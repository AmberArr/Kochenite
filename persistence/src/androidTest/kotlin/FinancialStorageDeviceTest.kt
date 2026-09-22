package moe.frost.kochenite.persistence.ledger

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.Draft
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.InvalidReason
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.Money
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.persistence.PreservingSQLiteFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class FinancialStorageDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "financial-test-${UUID.randomUUID()}.db"
    private val usd = BookCurrency("USD", 2)
    private val today = LocalDate.of(2026, 9, 22)
    private var db: LedgerDatabase? = null

    @get:Rule val migration =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            LedgerDatabase::class.java,
            emptyList(),
            PreservingSQLiteFactory(),
        )

    @After fun cleanup() {
        db?.close()
        context.deleteDatabase(name)
    }

    private fun open(afterWrite: () -> Unit = {}): RoomLedger {
        db?.close()
        val opened = ledgerDatabase(context, name)
        db = opened
        return RoomLedger(opened, { today }, afterWrite)
    }

    private fun <T> success(result: LedgerResult<T>): T {
        assertTrue("Expected successful operation", result is LedgerResult.Success)
        return (result as LedgerResult.Success).value
    }

    private suspend fun seed(ledger: RoomLedger) {
        success(ledger.createAccount(Account("cash", "Cash", usd)))
        success(ledger.createAccount(Account("card", "Card", usd)))
        success(ledger.createAccount(Account("yen", "Yen", BookCurrency("JPY", 0))))
    }

    private fun expense(amount: Long = 1234) =
        EntryInput(
            EntryKind.EXPENSE,
            today,
            LocalTime.NOON,
            "cash",
            amount,
            allocations = listOf(Allocation("a", amount)),
            merchant = "Synthetic 合成",
            note = "日本語 • café",
        )

    private suspend fun draft(
        ledger: RoomLedger,
        input: EntryInput = expense(),
        incomplete: Boolean = false,
    ): Draft =
        success(
            ledger.saveDraft(Draft(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", input, incomplete = incomplete), null),
        )

    private suspend fun post(
        ledger: RoomLedger,
        input: EntryInput,
    ): SavedEntry {
        val saved = draft(ledger, input)
        return success(ledger.confirm(saved.id, saved.token))
    }

    @Test fun exactReopenDraftConsumptionAndReports() =
        runBlocking {
            var ledger = open()
            seed(ledger)
            val saved = draft(ledger, expense(9007199254740993L))
            ledger = open()
            assertEquals(saved, success(ledger.draft(saved.id)))
            val entry = success(ledger.confirm(saved.id, saved.token))
            assertEquals(LedgerResult.AlreadyConfirmed, ledger.confirm(saved.id, saved.token))
            assertEquals(LedgerResult.Missing, ledger.draft(saved.id))
            ledger = open()
            assertEquals(entry, success(ledger.entry(entry.id)))
            assertEquals(-9007199254740993L, success(ledger.balance("cash")))
            val totals = success(ledger.totals(today, today)).single()
            assertEquals(9007199254740993L, totals.spendingMinor)
            assertEquals(0L, totals.incomeMinor)
            db!!.openHelper.writableDatabase.query("PRAGMA integrity_check").use {
                assertTrue(it.moveToFirst())
                assertEquals("ok", it.getString(0))
            }
        }

    @Test fun staleEditsAndDraftSavesPreserveCurrentRecords() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val original = post(ledger, expense())
            val first = success(ledger.beginEdit(original.id))
            val second = success(ledger.beginEdit(original.id))
            val updated = success(ledger.saveDraft(first.copy(input = expense(1500)), first.token))
            assertEquals(LedgerResult.Conflict, ledger.saveDraft(first.copy(input = expense(2000)), first.token))
            assertEquals(LedgerResult.Conflict, ledger.confirm(first.id, first.token))
            val replacement = success(ledger.confirm(updated.id, updated.token))
            assertEquals(original.id, replacement.id)
            assertEquals(LedgerResult.Conflict, ledger.confirm(second.id, second.token))
            assertEquals(second, success(ledger.draft(second.id)))
            assertEquals(-1500L, success(ledger.balance("cash")))
            assertEquals(1500L, success(ledger.totals(today, today)).single().spendingMinor)
        }

    @Test fun injectedFailureAndCancellationRollBackAllWrites() =
        runBlocking {
            var ledger = open()
            seed(ledger)
            val original = post(ledger, expense())
            val editing = success(ledger.beginEdit(original.id))
            val changed = success(ledger.saveDraft(editing.copy(input = expense(1500)), editing.token))
            ledger = open { throw IOException("Synthetic unavailable storage") }
            assertEquals(LedgerResult.TemporarilyUnavailable, ledger.confirm(changed.id, changed.token))
            assertEquals(original, success(ledger.entry(original.id)))
            assertEquals(changed, success(ledger.draft(changed.id)))
            ledger = open { throw CancellationException("Synthetic interruption") }
            try {
                ledger.confirm(changed.id, changed.token)
                fail("Expected cancellation")
            } catch (_: CancellationException) {
            }
            ledger = open()
            assertEquals(original, success(ledger.entry(original.id)))
            assertEquals(-1234L, success(ledger.balance("cash")))
            assertEquals(changed, success(ledger.draft(changed.id)))
            success(ledger.confirm(changed.id, changed.token))
            assertEquals(-1500L, success(ledger.balance("cash")))
        }

    @Test fun balanceOverflowRollsBackEntryAndCurrencyLock() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            post(ledger, expense().copy(kind = EntryKind.OPENING, amountMinor = Long.MAX_VALUE, allocations = emptyList()))
            val saved =
                draft(
                    ledger,
                    expense(
                        1,
                    ).copy(
                        kind = EntryKind.TRANSFER,
                        accountId = "yen",
                        destinationAccountId = "cash",
                        receivedMinor = 1,
                        allocations = emptyList(),
                    ),
                )
            assertEquals(LedgerResult.Invalid(InvalidReason.OVERFLOW), ledger.confirm(saved.id, saved.token))
            assertEquals(saved, success(ledger.draft(saved.id)))
            assertEquals(Long.MAX_VALUE, success(ledger.balance("cash")))
            assertEquals(0L, success(ledger.balance("yen")))
            assertFalse(success(ledger.accounts()).single { it.id == "yen" }.currencyLocked)
            assertEquals(1, success(ledger.page()).entries.size)
        }

    @Test fun trashRestoreAndPurgeDetachRefundWithoutChangingItsAmount() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val purchase = post(ledger, expense(1500))
            val refund = post(ledger, expense(400).copy(kind = EntryKind.REFUND, accountId = "card", purchaseId = purchase.id))
            assertEquals(1100L, success(ledger.totals(today, today)).single().spendingMinor)
            var trashed = success(ledger.trash(purchase.id, purchase.token))
            assertEquals(0L, success(ledger.balance("cash")))
            assertEquals(-400L, success(ledger.totals(today, today)).single().spendingMinor)
            val restored = success(ledger.restore(purchase.id, trashed.token))
            assertEquals(-1500L, success(ledger.balance("cash")))
            assertEquals(restored, success(ledger.restore(purchase.id, restored.token)))
            val refundEdit = success(ledger.beginEdit(refund.id))
            trashed = success(ledger.trash(purchase.id, restored.token))
            success(ledger.purge(purchase.id, trashed.token))
            assertEquals(LedgerResult.Missing, ledger.entry(purchase.id))
            val surviving = success(ledger.entry(refund.id))
            assertEquals(null, surviving.input.purchaseId)
            assertEquals(400L, surviving.input.amountMinor)
            assertEquals(400L, success(ledger.balance("card")))
            assertEquals(LedgerResult.Conflict, ledger.confirm(refundEdit.id, refundEdit.token))
            assertTrue(success(ledger.accounts()).single { it.id == "cash" }.currencyLocked)
        }

    @Test fun incompleteAndFutureInputsHaveNoFinancialEffect() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val incomplete =
                draft(
                    ledger,
                    expense().copy(
                        kind = EntryKind.TRANSFER,
                        destinationAccountId = "yen",
                        receivedMinor = null,
                        allocations = emptyList(),
                        merchantAmount = Money(1000, BookCurrency("JPY", 0)),
                    ),
                    true,
                )
            assertEquals(LedgerResult.Invalid(InvalidReason.MISSING_AMOUNT), ledger.confirm(incomplete.id, incomplete.token))
            assertEquals(0L, success(ledger.balance("cash")))
            assertEquals(0L, success(ledger.balance("yen")))
            assertTrue(success(ledger.totals(today, today)).isEmpty())
            val completeInput = incomplete.copy(input = incomplete.input.copy(receivedMinor = 1800), incomplete = false)
            val completed = success(ledger.saveDraft(completeInput, incomplete.token))
            success(ledger.confirm(completed.id, completed.token))
            assertEquals(-1234L, success(ledger.balance("cash")))
            assertEquals(1800L, success(ledger.balance("yen")))
            assertTrue(success(ledger.totals(today, today)).isEmpty())
            val future = draft(ledger, expense().copy(date = today.plusDays(1)))
            assertEquals(LedgerResult.Invalid(InvalidReason.FUTURE_DATE), ledger.confirm(future.id, future.token))
            assertEquals(future, success(ledger.draft(future.id)))
        }

    @Test fun boundedKeysetPagesIncludeTiesExactlyOnce() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val expected = (1..7).map { post(ledger, expense(it.toLong())).id }.toSet()
            val first = success(ledger.page(limit = 3))
            val second = success(ledger.page(after = first.next, limit = 3))
            val third = success(ledger.page(after = second.next, limit = 3))
            assertEquals(expected, (first.entries + second.entries + third.entries).map { it.id }.toSet())
            assertEquals(3, first.entries.size)
            assertEquals(3, second.entries.size)
            assertEquals(1, third.entries.size)
            assertEquals(null, third.next)
            assertEquals(LedgerResult.Invalid(InvalidReason.STATE), ledger.page(limit = 201))
            val row = first.entries.first()
            success(ledger.trash(row.id, row.token))
            assertEquals(1, success(ledger.page(EntryStatus.TRASH)).entries.size)
        }

    private fun version1Fixture() {
        migration.createDatabase(name, 1).use { old ->
            old.execSQL("INSERT INTO accounts VALUES ('cash','Synthetic','USD',2,0,1,1)")
            old.execSQL(
                """
                INSERT INTO entries VALUES ('entry','token','draft','POSTED','EXPENSE','2026-09-22','12:00','cash',
                9007199254740993,NULL,NULL,'Synthetic',NULL,NULL,NULL,NULL)
                """.trimIndent(),
            )
            old.execSQL("INSERT INTO postings VALUES ('entry','cash',-9007199254740993)")
            old.execSQL("INSERT INTO allocations VALUES ('entry','a',0,9007199254740993,NULL)")
            val saved = Draft("saved-draft", "next-entry", "saved-token", expense()).row()
            old.execSQL(
                "INSERT INTO drafts VALUES (?,?,?,?,?,?)",
                arrayOf<Any?>(saved.id, saved.entryId, saved.token, null, 0, saved.payload),
            )
        }
    }

    @Test fun version1MigrationPreservesLedgerAndDraftsAcrossReopens() =
        runBlocking {
            version1Fixture()
            migration.runMigrationsAndValidate(name, 2, true, ledgerMigration1To2).close()
            repeat(2) {
                val ledger = open()
                assertEquals(-9007199254740993L, success(ledger.balance("cash")))
                assertEquals("", success(ledger.entry("entry")).input.note)
                assertEquals(expense(), success(ledger.draft("saved-draft")).input)
                assertTrue(success(ledger.accounts()).single().currencyLocked)
            }
        }

    @Test fun interruptedMigrationPreservesOldRowsAndRetries() =
        runBlocking {
            version1Fixture()
            val broken =
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        ledgerMigration1To2.migrate(db)
                        throw IOException("Synthetic migration interruption")
                    }
                }
            val candidate =
                Room
                    .databaseBuilder(
                        context,
                        LedgerDatabase::class.java,
                        name,
                    ).openHelperFactory(PreservingSQLiteFactory())
                    .addMigrations(broken)
                    .build()
            try {
                candidate.openHelper.writableDatabase
                fail("Expected interrupted migration")
            } catch (_: Exception) {
                assertTrue(context.getDatabasePath(name).exists())
            } finally {
                candidate.close()
            }
            assertEquals(-9007199254740993L, success(open().balance("cash")))
        }

    @Test fun lockedTemporaryAndCorruptStoragePreserveFiles() =
        runBlocking {
            FinancialStorage(context, name, { false }).use { assertEquals(LedgerResult.TemporarilyUnavailable, it.open()) }
            assertFalse(context.getDatabasePath(name).exists())
            val ledger = open()
            seed(ledger)
            post(ledger, expense())
            db!!.close()
            db = null
            val file = context.getDatabasePath(name)
            val before = file.readBytes()
            FinancialStorage(context, name, { true }) { _, _ -> throw IOException("Synthetic unavailable storage") }.use {
                assertEquals(LedgerResult.TemporarilyUnavailable, it.open())
            }
            assertArrayEquals(before, file.readBytes())
            val damaged = before.also { it[0] = (it[0].toInt() xor 1).toByte() }
            file.writeBytes(damaged)
            repeat(2) {
                FinancialStorage(context, name, { true }).use { assertEquals(LedgerResult.RecoveryRequired, it.open()) }
                assertArrayEquals(damaged, file.readBytes())
            }
        }

    @Test fun currencyDefinitionsAndCategoryReferencesCannotBeInvented() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            assertEquals(
                LedgerResult.Invalid(InvalidReason.CURRENCY),
                ledger.createAccount(Account("wrong-scale", "Synthetic", BookCurrency("USD", 3))),
            )
            val saved = draft(ledger, expense().copy(allocations = listOf(Allocation("a", 1234, "missing"))))
            assertEquals(LedgerResult.Invalid(InvalidReason.CATEGORY), ledger.confirm(saved.id, saved.token))
            assertEquals(saved, success(ledger.draft(saved.id)))
            assertEquals(0L, success(ledger.balance("cash")))
        }

    @Test fun concurrentConfirmationPostsOnlyOnce() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val saved = draft(ledger)
            val results = List(8) { async { ledger.confirm(saved.id, saved.token) } }.awaitAll()
            assertEquals(1, results.count { it is LedgerResult.Success })
            assertEquals(7, results.count { it == LedgerResult.AlreadyConfirmed })
            assertEquals(-1234L, success(ledger.balance("cash")))
            assertEquals(1, success(ledger.page()).entries.size)
        }
}
