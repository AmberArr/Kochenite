package moe.frost.kochenite.persistence.ledger

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.AccountGroup
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.InvalidReason
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.persistence.PreservingSQLiteFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class BookSetupDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "setup-test-${UUID.randomUUID()}.db"
    private val db = ledgerDatabase(context, name)
    private val ledger = RoomLedger(db)
    private val usd = BookCurrency("USD", 2)

    @get:Rule val migration =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            LedgerDatabase::class.java,
            emptyList(),
            PreservingSQLiteFactory(),
        )

    @After fun cleanup() {
        db.close()
        context.deleteDatabase(name)
    }

    private fun <T> success(result: LedgerResult<T>): T {
        assertTrue("Expected success", result is LedgerResult.Success)
        return (result as LedgerResult.Success).value
    }

    @Test fun setupSignedBalancesGroupsOrderingAndSettingsPersist() =
        runBlocking {
            success(ledger.settingsRepository.finishSetup(Account("cash", "Cash", usd), 10000, usd, true))
            success(ledger.accountRepository.save(Account("zero", "Zero", usd, position = 2), openingMinor = 0))
            val card = success(ledger.accountRepository.save(Account("card", "Card", usd, position = 1), openingMinor = -20000))
            success(ledger.accountRepository.adjust("cash", -250, LocalDate.now(), LocalTime.NOON, "Synthetic correction"))
            assertEquals(listOf(9750L, -20000L, 0L), success(ledger.accountRepository.list()).map { it.balanceMinor })
            assertTrue(success(ledger.totals(LocalDate.now(), LocalDate.now())).isEmpty())
            val group = success(ledger.accountRepository.saveGroup(AccountGroup("group", "Bank")))
            val firstGroup = success(ledger.accountRepository.saveGroup(AccountGroup("first", "First", -1)))
            assertEquals(listOf(firstGroup.id, group.id), success(ledger.accountRepository.groups()).map { it.id })
            val grouped = success(ledger.accountRepository.save(card.copy(groupId = group.id, type = "Credit card"), card))
            val archived = success(ledger.accountRepository.save(grouped.copy(archived = true), grouped))
            assertEquals(-20000L, success(ledger.balance("card")))
            assertTrue(archived.includeInNetWorth)
            success(ledger.settingsRepository.setDisplayCurrency(BookCurrency("JPY", 0)))
            assertEquals(usd, success(ledger.accounts()).first().currency)
            assertEquals(3, success(ledger.categoryRepository.list()).size)
            db.close()
            val reopened = ledgerDatabase(context, name)
            try {
                val book = RoomLedger(reopened)
                assertEquals("JPY", success(book.settingsRepository.get())!!.displayCurrency.code)
                assertEquals(listOf(9750L, -20000L, 0L), success(book.accountRepository.list()).map { it.balanceMinor })
                assertEquals(listOf(firstGroup, group), success(book.accountRepository.groups()))
            } finally {
                reopened.close()
            }
        }

    @Test fun accountListBalancesMatchPerAccountBalancesAndExcludeTrash() =
        runBlocking {
            success(ledger.settingsRepository.finishSetup(Account("cash", "Cash", usd), 10000, usd, false))
            success(ledger.accountRepository.save(Account("bank", "Bank", usd, position = 1), openingMinor = 5000))
            success(ledger.accountRepository.save(Account("unused", "Unused", usd, position = 2)))
            val transfer =
                EntryInput(EntryKind.TRANSFER, LocalDate.now(), LocalTime.NOON, "cash", 1500, "bank", 1500)
            success(ledger.post(EditorInput(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", transfer)))
            val trashed = success(ledger.accountRepository.adjust("bank", -700, LocalDate.now(), LocalTime.NOON, "Synthetic"))
            success(ledger.trash(trashed.id, trashed.token))
            val listed = success(ledger.accountRepository.list()).associate { it.account.id to it.balanceMinor }
            assertEquals(mapOf("cash" to 8500L, "bank" to 6500L, "unused" to 0L), listed)
            listed.forEach { (id, balance) -> assertEquals(success(ledger.balance(id)), balance) }
        }

    @Test fun failedOpeningRollsBackEntireSetupAndRetryDoesNotDuplicate() =
        runBlocking {
            val broken = RoomLedger(db, afterWrite = { throw IOException("Synthetic interruption") })
            val account = Account("cash", "Cash", usd)
            assertEquals(LedgerResult.TemporarilyUnavailable, broken.settingsRepository.finishSetup(account, 1000, usd, true))
            assertTrue(success(ledger.accounts()).isEmpty())
            assertTrue(success(ledger.categoryRepository.list()).isEmpty())
            assertEquals(null, success(ledger.settingsRepository.get()))
            success(ledger.settingsRepository.finishSetup(account, 1000, usd, true))
            assertEquals(LedgerResult.Conflict, ledger.settingsRepository.finishSetup(account, 1000, usd, true))
            assertEquals(1000L, success(ledger.balance("cash")))
        }

    @Test fun postedCurrencyRemainsLockedAfterPurgeAndStaleMetadataIsRejected() =
        runBlocking {
            val empty = success(ledger.accountRepository.save(Account("empty", "Empty", usd)))
            assertFalse(empty.currencyLocked)
            val yen = success(ledger.accountRepository.save(empty.copy(currency = BookCurrency("JPY", 0)), empty))
            assertEquals("JPY", yen.currency.code)
            val cash = success(ledger.accountRepository.save(Account("cash", "Cash", usd), openingMinor = 1000))
            val opening = success(ledger.page()).entries.single()
            val trashed = success(ledger.trash(opening.id, opening.token))
            success(ledger.purge(opening.id, trashed.token))
            val changedCurrency = cash.copy(currency = BookCurrency("JPY", 0))
            assertEquals(LedgerResult.Invalid(InvalidReason.CURRENCY), ledger.accountRepository.save(changedCurrency, cash))
            val renamed = success(ledger.accountRepository.save(cash.copy(name = "Renamed"), cash))
            assertEquals(LedgerResult.Conflict, ledger.accountRepository.save(cash.copy(name = "Stale"), cash))
            assertTrue(renamed.currencyLocked)
        }

    @Test fun categoryEditsPreserveReferencesAndTwoLevelHierarchy() =
        runBlocking {
            success(ledger.accountRepository.save(Account("cash", "Cash", usd)))
            val parent = success(ledger.categoryRepository.save(Category("food", "Food", CategoryKind.EXPENSE)))
            val child = success(ledger.categoryRepository.save(Category("groceries", "Groceries", CategoryKind.EXPENSE, parent.id)))
            val third = Category("third", "Third", CategoryKind.EXPENSE, child.id)
            assertEquals(LedgerResult.Invalid(InvalidReason.CATEGORY), ledger.categoryRepository.save(third))
            val cycle = parent.copy(parentId = child.id)
            assertEquals(LedgerResult.Invalid(InvalidReason.CATEGORY), ledger.categoryRepository.save(cycle, parent))
            val input =
                EntryInput(
                    EntryKind.EXPENSE,
                    LocalDate.now(),
                    LocalTime.NOON,
                    "cash",
                    -1500,
                    allocations = listOf(Allocation("a", 500, parent.id), Allocation("b", 1000, child.id)),
                )
            success(ledger.post(EditorInput("submission", "entry", "", input)))
            val changedKind = child.copy(kind = CategoryKind.INCOME, parentId = null)
            assertEquals(LedgerResult.Invalid(InvalidReason.CATEGORY), ledger.categoryRepository.save(changedKind, child))
            val refundInput =
                input.copy(
                    kind = EntryKind.EXPENSE,
                    amountMinor = 200,
                    allocations = listOf(Allocation("refund", 200, child.id)),
                    relatedEntryIds = listOf("entry"),
                )
            success(ledger.post(EditorInput("refund-submission", "refund-entry", "", refundInput)))
            val archivedParent = parent.copy(archived = true)
            assertEquals(LedgerResult.Invalid(InvalidReason.CATEGORY), ledger.categoryRepository.save(archivedParent, parent))
            success(ledger.categoryRepository.save(child.copy(name = "Groceries renamed", archived = true), child))
            success(ledger.categoryRepository.save(parent.copy(archived = true), parent))
            assertEquals(1300L, success(ledger.totals(LocalDate.now(), LocalDate.now())).single().spendingMinor)
            val categoryTotal = success(ledger.categoryTotals(LocalDate.now(), LocalDate.now())).single()
            assertEquals(parent.id, categoryTotal.rootCategoryId)
            assertEquals(1300L, categoryTotal.amountMinor)
            assertEquals(listOf(parent.id, child.id), success(ledger.entry("entry")).input.allocations.map { it.categoryId })
        }

    @Test fun schema2MigrationPreservesAccountsAndAddsDefaultMetadata() =
        runBlocking {
            migration.createDatabase(name, 2).use { old ->
                old.execSQL("INSERT INTO accounts VALUES ('cash','Cash','USD',2,0,1,1)")
                old.execSQL(
                    """
                    INSERT INTO entries (id, token, sourceDraftId, status, kind, date, time, accountId, amountMinor, merchant, note)
                    VALUES ('entry','token','draft','POSTED','OPENING','2026-09-22','12:00','cash',1000,'','kept')
                    """.trimIndent(),
                )
                old.execSQL("INSERT INTO postings VALUES ('entry','cash',1000)")
            }
            migration.runMigrationsAndValidate(name, 3, true, ledgerMigration2To3).close()
            val account = success(ledger.accountRepository.list()).single()
            assertEquals(1000L, account.balanceMinor)
            assertEquals(null, account.account.groupId)
            assertEquals("", account.account.type)
            assertEquals(0, account.account.position)
            assertTrue(account.account.currencyLocked)
            assertEquals("kept", success(ledger.entry("entry")).input.note)
        }

    @Test fun schema6MigrationRetiresOrdinaryEditorRowsAndKeepsIncompleteRecords() =
        runBlocking {
            fun editor(
                id: String,
                originalToken: String? = null,
            ) = EditorInput(
                id,
                "entry-$id",
                "token-$id",
                EntryInput(
                    EntryKind.EXPENSE,
                    LocalDate.now(),
                    LocalTime.NOON,
                    "cash",
                    null,
                    allocations = listOf(Allocation("allocation-$id", 0)),
                ),
                originalToken = originalToken,
                amountText = "",
            )

            val newEditor = editor("ordinary-new").row()
            val editEditor = editor("ordinary-edit", "posted-token").row()
            val incomplete = editor("saved-incomplete").row(incomplete = true)
            migration.createDatabase(name, 6).use { old ->
                listOf(newEditor, editEditor, incomplete).forEach { row ->
                    old.execSQL(
                        "INSERT INTO drafts (id, entryId, token, originalToken, incomplete, payload) VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf<Any?>(row.id, row.entryId, row.token, row.originalToken, if (row.incomplete) 1 else 0, row.payload),
                    )
                }
            }

            migration.runMigrationsAndValidate(name, 7, true, ledgerMigration6To7).use { migrated ->
                migrated.query("SELECT id, incomplete FROM drafts ORDER BY id").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("saved-incomplete", cursor.getString(0))
                    assertEquals(1, cursor.getInt(1))
                    assertFalse(cursor.moveToNext())
                }
            }
        }
}
