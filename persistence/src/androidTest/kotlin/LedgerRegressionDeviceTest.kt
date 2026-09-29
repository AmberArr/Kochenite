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
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.editor.prepared
import moe.frost.kochenite.domain.editor.recalculate
import moe.frost.kochenite.domain.editor.withKind
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.AllocationSelection
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.EntryTemplate
import moe.frost.kochenite.domain.ledger.InvalidReason
import moe.frost.kochenite.domain.ledger.LedgerFilter
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.Money
import moe.frost.kochenite.domain.ledger.ReceiptLine
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.domain.ledger.Tag
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

class LedgerRegressionDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "ledger-regression-${UUID.randomUUID()}.db"
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
        assertTrue("Expected success: ${result::class.simpleName}", result is LedgerResult.Success)
        return (result as LedgerResult.Success).value
    }

    private suspend fun seed(book: RoomLedger) {
        success(book.createAccount(Account("cash", "Cash", usd)))
        success(book.createAccount(Account("card", "Card", usd)))
        success(book.createAccount(Account("yen", "Yen", BookCurrency("JPY", 0))))
    }

    private fun expense(amount: Long = 1234) =
        EntryInput(
            EntryKind.EXPENSE,
            today,
            LocalTime.NOON,
            "cash",
            -amount,
            allocations = listOf(Allocation("a", amount)),
            merchant = "Synthetic 合成",
            note = "日本語 • café",
        )

    private fun editor(input: EntryInput = expense()) = EditorInput(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", input)

    private suspend fun post(
        book: RoomLedger,
        input: EntryInput = expense(),
    ) = success(book.post(editor(input)))

    @Test fun signedExpenseCreditsUseTheirOwnPeriodAndRelatedRecords() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val purchaseDate = today.withDayOfMonth(1)
            val purchase = post(ledger, expense(1000).copy(date = purchaseDate))
            val first = post(ledger, expense(300).copy(amountMinor = 300, accountId = "card", relatedEntryIds = listOf(purchase.id)))
            val second = post(ledger, expense(100).copy(amountMinor = 100, accountId = "card", relatedEntryIds = listOf(purchase.id)))
            val standalone = post(ledger, expense(200).copy(amountMinor = 200))
            assertEquals(purchase.id, first.input.relatedEntryIds.single())
            assertEquals(purchase.id, second.input.relatedEntryIds.single())
            assertEquals(emptyList<String>(), standalone.input.relatedEntryIds)
            val edit = success(ledger.beginEdit(first.id))
            success(ledger.post(edit))
            assertEquals(listOf(purchase.id), success(ledger.entry(first.id)).input.relatedEntryIds)
            val copied = success(ledger.copyEntry(first.id))
            assertEquals(emptyList<String>(), copied.input.relatedEntryIds)
            assertEquals(-800L, success(ledger.balance("cash")))
            assertEquals(400L, success(ledger.balance("card")))
            assertEquals(1000L, success(ledger.totals(purchaseDate, purchaseDate)).single().spendingMinor)
            assertEquals(-600L, success(ledger.totals(today, today)).single().spendingMinor)
        }

    @Test fun trashRestoreAndPurgeDetachRefundWithoutChangingItsAmount() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val purchase = post(ledger, expense(1500))
            val refund = post(ledger, expense(400).copy(amountMinor = 400, accountId = "card", relatedEntryIds = listOf(purchase.id)))
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
            assertEquals(emptyList<String>(), surviving.input.relatedEntryIds)
            assertEquals(400L, surviving.input.amountMinor)
            assertEquals(400L, success(ledger.balance("card")))
            assertEquals(LedgerResult.Conflict, ledger.post(refundEdit))
            assertTrue(success(ledger.accounts()).single { it.id == "cash" }.currencyLocked)
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

    @Test fun bulkCategorizationRejectsStaleRowsAndRollsBackWholeBatch() =
        runBlocking {
            var ledger = open()
            seed(ledger)
            success(ledger.categoryRepository.save(Category("food", "Food", CategoryKind.EXPENSE)))
            val first = post(ledger, expense(600).copy(merchant = "First"))
            val second = post(ledger, expense(400).copy(merchant = "Second"))
            val selections =
                listOf(
                    AllocationSelection(first.id, "a", first.token),
                    AllocationSelection(second.id, "a", second.token),
                )
            assertEquals(
                LedgerResult.Conflict,
                ledger.bulkCategorize(
                    selections.mapIndexed { index, row ->
                        if (index == 1) row.copy(expectedToken = "stale") else row
                    },
                    "food",
                ),
            )
            assertEquals(
                null,
                success(ledger.entry(first.id))
                    .input.allocations
                    .single()
                    .categoryId,
            )
            ledger = open { throw IOException("Synthetic interrupted bulk write") }
            assertEquals(LedgerResult.TemporarilyUnavailable, ledger.bulkCategorize(selections, "food"))
            ledger = open()
            assertEquals(
                null,
                success(ledger.entry(first.id))
                    .input.allocations
                    .single()
                    .categoryId,
            )
            assertEquals(2, success(ledger.bulkCategorize(selections, "food")))
            assertEquals(
                "food",
                success(ledger.entry(first.id))
                    .input.allocations
                    .single()
                    .categoryId,
            )
            assertEquals(
                "food",
                success(ledger.entry(second.id))
                    .input.allocations
                    .single()
                    .categoryId,
            )
            assertEquals(-1000L, success(ledger.balance("cash")))
            assertEquals(1000L, success(ledger.totals(today, today)).single().spendingMinor)
            assertEquals(LedgerResult.Conflict, ledger.bulkCategorize(selections, null))
        }

    @Test fun legacyDraftCodecMigratesExpenseSignsAndRelatedReferences() {
        val payload =
            """
            {
              "version": 1,
              "kind": "EXPENSE",
              "date": "2026-09-22",
              "time": "12:00",
              "accountId": "cash",
              "amount": "1234",
              "destination": null,
              "received": null,
              "merchant": "Legacy",
              "note": "",
              "purchase": "purchase",
              "amountText": "12.34",
              "allocations": [{"id": "a", "amount": "1234", "category": null}],
              "receiptLines": [],
              "tagIds": []
            }
            """.trimIndent()
        val draft = DraftRow("draft", "entry", "token", null, false, payload).model()
        assertEquals(EntryKind.EXPENSE, draft.input.kind)
        assertEquals(-1234L, draft.input.amountMinor)
        assertEquals("-12.34", draft.amountText)
        assertEquals(listOf("purchase"), draft.input.relatedEntryIds)
    }

    @Test fun signedEntryMigrationPreservesPostingsReportsAndOldPurchaseLinks() =
        runBlocking {
            migration.createDatabase(name, 5).use { old ->
                old.execSQL(
                    "INSERT INTO accounts (id,name,currency,scale,archived,includeInNetWorth,currencyLocked,groupId,type,position) VALUES ('cash','Cash','USD',2,0,1,1,NULL,'',0)",
                )
                old.execSQL(
                    "INSERT INTO accounts (id,name,currency,scale,archived,includeInNetWorth,currencyLocked,groupId,type,position) VALUES ('card','Card','USD',2,0,1,1,NULL,'',1)",
                )
                old.execSQL(
                    "INSERT INTO entries (id,token,sourceDraftId,status,kind,date,time,accountId,amountMinor,destinationAccountId,receivedMinor,merchant,note,merchantMinor,merchantCurrency,merchantScale,purchaseId) VALUES ('purchase','p','pd','POSTED','EXPENSE','2026-09-01','12:00','cash',1000,NULL,NULL,'Shop','',NULL,NULL,NULL,NULL)",
                )
                old.execSQL(
                    "INSERT INTO entries (id,token,sourceDraftId,status,kind,date,time,accountId,amountMinor,destinationAccountId,receivedMinor,merchant,note,merchantMinor,merchantCurrency,merchantScale,purchaseId) VALUES ('credit','c','cd','POSTED','REFUND','2026-09-22','12:00','card',300,NULL,NULL,'Shop','',NULL,NULL,NULL,'purchase')",
                )
                old.execSQL("INSERT INTO postings VALUES ('purchase','cash',-1000)")
                old.execSQL("INSERT INTO postings VALUES ('credit','card',300)")
                old.execSQL("INSERT INTO allocations (entryId,id,position,amountMinor,categoryId) VALUES ('purchase','p-line',0,1000,NULL)")
                old.execSQL("INSERT INTO allocations (entryId,id,position,amountMinor,categoryId) VALUES ('credit','c-line',0,300,NULL)")
            }
            migration.runMigrationsAndValidate(name, 6, true, ledgerMigration5To6).close()
            val ledger = open()
            val purchase = success(ledger.entry("purchase"))
            val credit = success(ledger.entry("credit"))
            assertEquals(-1000L, purchase.input.amountMinor)
            assertEquals(EntryKind.EXPENSE, credit.input.kind)
            assertEquals(300L, credit.input.amountMinor)
            assertEquals(listOf("purchase"), credit.input.relatedEntryIds)
            assertEquals(-1000L, success(ledger.balance("cash")))
            assertEquals(300L, success(ledger.balance("card")))
            assertEquals(1000L, success(ledger.totals(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-01"))).single().spendingMinor)
            assertEquals(-300L, success(ledger.totals(today, today)).single().spendingMinor)
            val edit = success(ledger.beginEdit(credit.id))
            success(ledger.post(edit))
            assertEquals(300L, success(ledger.balance("card")))
            val trashed = success(ledger.trash(purchase.id, purchase.token))
            assertEquals(listOf("purchase"), success(ledger.entry(credit.id)).input.relatedEntryIds)
            success(ledger.purge(purchase.id, trashed.token))
            assertEquals(emptyList<String>(), success(ledger.entry(credit.id)).input.relatedEntryIds)
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

    @Test fun boundedSearchFindsUnicodeReceiptTextAndExactTagCategoryCurrencyFilters() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            success(ledger.categoryRepository.save(Category("food", "Food", CategoryKind.EXPENSE)))
            success(ledger.categoryRepository.save(Category("fruit", "Fruit", CategoryKind.EXPENSE, parentId = "food")))
            success(ledger.tagRepository.save(Tag("trip", "Trip")))
            val first =
                post(
                    ledger,
                    expense(1200).copy(
                        merchant = "Café 東京",
                        allocations = listOf(Allocation("line", 1200, "fruit")),
                        receiptLines = listOf(ReceiptLine("line", "りんご Apple", 1200)),
                        tagIds = listOf("trip"),
                    ),
                )
            val second = post(ledger, expense(500).copy(merchant = "Other", note = "Unicode résumé"))
            assertEquals(listOf(first.id), success(ledger.search(LedgerFilter(text = "東京"))).entries.map { it.id })
            assertEquals(listOf(first.id), success(ledger.search(LedgerFilter(text = "りんご"))).entries.map { it.id })
            assertEquals(listOf(second.id), success(ledger.search(LedgerFilter(text = "résumé"))).entries.map { it.id })
            assertEquals(listOf(first.id), success(ledger.search(LedgerFilter(categoryId = "food"))).entries.map { it.id })
            assertEquals(listOf(first.id), success(ledger.search(LedgerFilter(tagId = "trip"))).entries.map { it.id })
            assertEquals(
                listOf(
                    first.id,
                ),
                success(
                    ledger.search(LedgerFilter(accountId = "cash", amountCurrency = usd, minimumMinor = 1200, maximumMinor = 1200)),
                ).entries.map {
                    it.id
                },
            )
            assertTrue(success(ledger.search(LedgerFilter(amountCurrency = BookCurrency("JPY", 0), minimumMinor = 1200))).entries.isEmpty())
            val page = success(ledger.search(LedgerFilter(), limit = 1))
            assertEquals(1, page.entries.size)
            assertEquals(1, success(ledger.search(LedgerFilter(), page.next, 1)).entries.size)
            success(ledger.tagRepository.save(Tag("trip", "Journey", archived = true), Tag("trip", "Trip")))
            val editing = success(ledger.beginEdit(first.id))
            success(ledger.post(editing))
            val fresh = editor(expense(100).copy(tagIds = listOf("trip")))
            assertEquals(LedgerResult.Invalid(InvalidReason.TAG), ledger.post(fresh))
            val current = success(ledger.entry(first.id))
            success(ledger.trash(first.id, current.token))
            assertEquals(listOf(first.id), success(ledger.search(LedgerFilter(status = EntryStatus.TRASH))).entries.map { it.id })
        }

    @Test fun cancellationAndUnexpectedFailureRollBackPostedReplacement() =
        runBlocking {
            var book = open()
            seed(book)
            val original = post(book)
            val edit = success(book.beginEdit(original.id)).copy(input = expense(1500))
            book = open { throw CancellationException("Synthetic cancellation") }
            try {
                book.post(edit)
                fail("Cancellation must propagate")
            } catch (_: CancellationException) {
            }
            book = open()
            assertEquals(original, success(book.entry(original.id)))
            book = open { throw IllegalStateException("Synthetic implementation failure") }
            assertEquals(LedgerResult.UnexpectedFailure, book.post(edit))
            book = open()
            assertEquals(original, success(book.entry(original.id)))
            assertEquals(-1234L, success(book.balance("cash")))
        }

    @Test fun overflowRollsBackBothTransferLegsAndCurrencyLock() =
        runBlocking {
            val book = open()
            seed(book)
            post(book, expense().copy(kind = EntryKind.OPENING, amountMinor = Long.MAX_VALUE, allocations = emptyList()))
            val input = EntryInput(EntryKind.TRANSFER, today, LocalTime.NOON, "yen", 1, "cash", 1)
            assertEquals(LedgerResult.Invalid(InvalidReason.OVERFLOW), book.post(editor(input)))
            assertEquals(Long.MAX_VALUE, success(book.balance("cash")))
            assertEquals(0L, success(book.balance("yen")))
            assertFalse(success(book.accounts()).single { it.id == "yen" }.currencyLocked)
            assertEquals(1, success(book.page()).entries.size)
        }

    @Test fun foreignMetadataEditPreservesCorrectedAllocationsAcrossReopen() =
        runBlocking {
            var book = open()
            seed(book)
            val input =
                expense(1000).copy(
                    merchantAmount = Money(200, BookCurrency("JPY", 0)),
                    allocations = listOf(Allocation("a", 700), Allocation("b", 300)),
                    receiptLines = listOf(ReceiptLine("a", "One", 100), ReceiptLine("b", "Two", 100)),
                )
            val original = post(book, input)
            val accounts = success(book.accounts())
            val opened = success(book.beginEdit(original.id)).prepared(accounts)
            val edited = opened.copy(input = opened.input.copy(note = "Metadata only")).recalculate(accounts)
            success(book.post(edited))
            book = open()
            assertEquals(input.allocations, success(book.entry(original.id)).input.allocations)
            assertEquals(-1000L, success(book.balance("cash")))
            val malformed = success(book.beginEdit(original.id)).copy(input = input.copy(receiptLines = input.receiptLines.dropLast(1)))
            assertEquals(LedgerResult.Invalid(InvalidReason.ALLOCATIONS), book.post(malformed))
            assertEquals(input.allocations, success(book.entry(original.id)).input.allocations)
        }

    @Test fun leavingTransferCanPostExpenseAndIncomeWithoutHiddenTextErrors() =
        runBlocking {
            val book = open()
            seed(book)
            val accounts = success(book.accounts())
            for (destination in listOf("card", "yen")) {
                for (kind in listOf(EntryKind.EXPENSE, EntryKind.INCOME)) {
                    val input = EntryInput(EntryKind.TRANSFER, today, LocalTime.NOON, "cash", 1000, destination, 1000)
                    val changed = editor(input).prepared(accounts).withKind(kind, accounts)
                    success(book.post(changed))
                }
            }
            assertEquals(0L, success(book.balance("cash")))
            assertEquals(0L, success(book.balance("yen")))
            assertEquals(4, success(book.page()).entries.size)
        }

    private fun seedLegacy(
        version: Int,
        databaseName: String = name,
    ) {
        migration.createDatabase(databaseName, version).use { old ->
            old.execSQL(
                "INSERT INTO accounts (id,name,currency,scale,archived,includeInNetWorth,currencyLocked) VALUES ('cash','Cash','USD',2,0,1,1)",
            )
            val amount = if (version < 6) 9007199254740993L else -9007199254740993L
            old.execSQL(
                "INSERT INTO entries (id,token,sourceDraftId,status,kind,date,time,accountId,amountMinor,merchant) VALUES ('entry','token','source','POSTED','EXPENSE','2026-09-22','12:00','cash',?,'Synthetic')",
                arrayOf<Any>(amount),
            )
            old.execSQL("INSERT INTO postings VALUES ('entry','cash',-9007199254740993)")
            old.execSQL("INSERT INTO allocations VALUES ('entry','a',0,9007199254740993,NULL)")
            val saved =
                editor(
                    expense().copy(amountMinor = null, allocations = listOf(Allocation("a", 0))),
                ).copy(id = "incomplete").row(incomplete = true)
            old.execSQL(
                "INSERT INTO drafts VALUES (?,?,?,?,?,?)",
                arrayOf<Any?>(saved.id, saved.entryId, saved.token, null, 1, saved.payload),
            )
        }
    }

    @Test fun everyReleasedSchemaMigratesPopulatedFinancialAndIncompleteData() =
        runBlocking {
            for (version in 1..6) {
                val fixture = "$name-$version"
                try {
                    seedLegacy(version, fixture)
                    migration
                        .runMigrationsAndValidate(
                            fixture,
                            7,
                            true,
                            ledgerMigration1To2,
                            ledgerMigration2To3,
                            ledgerMigration3To4,
                            ledgerMigration4To5,
                            ledgerMigration5To6,
                            ledgerMigration6To7,
                        ).close()
                    repeat(2) {
                        val database = ledgerDatabase(context, fixture)
                        try {
                            val book = RoomLedger(database, { today })
                            assertEquals(-9007199254740993L, success(book.balance("cash")))
                            assertEquals(-9007199254740993L, success(book.entry("entry")).input.amountMinor)
                            assertEquals(9007199254740993L, success(book.totals(today, today)).single().spendingMinor)
                            assertEquals(null, success(book.incomplete("incomplete")).editor.input.amountMinor)
                        } finally {
                            database.close()
                        }
                    }
                } finally {
                    context.deleteDatabase(fixture)
                }
            }
        }

    @Test fun interruptedMigrationRollsBackAndRetriesWithOriginalData() =
        runBlocking {
            seedLegacy(1)
            var invoked = false
            val broken =
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        invoked = true
                        ledgerMigration1To2.migrate(db)
                        throw IOException("Synthetic migration interruption")
                    }
                }
            val candidate =
                Room
                    .databaseBuilder(context, LedgerDatabase::class.java, name)
                    .openHelperFactory(PreservingSQLiteFactory())
                    .addMigrations(
                        broken,
                        ledgerMigration2To3,
                        ledgerMigration3To4,
                        ledgerMigration4To5,
                        ledgerMigration5To6,
                        ledgerMigration6To7,
                    ).build()
            try {
                candidate.openHelper.writableDatabase
                fail("Expected failure")
            } catch (
                _: Exception,
            ) {
                assertTrue(invoked)
                assertTrue(context.getDatabasePath(name).exists())
            } finally {
                candidate.close()
            }
            assertEquals(-9007199254740993L, success(open().balance("cash")))
            assertEquals(null, success(open().incomplete("incomplete")).editor.input.amountMinor)
        }
}
