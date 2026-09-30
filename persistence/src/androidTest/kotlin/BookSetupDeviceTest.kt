package moe.frost.kochenite.persistence.ledger

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.AccountGroup
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.BalanceAdjustment
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.InvalidReason
import moe.frost.kochenite.domain.ledger.LedgerFilter
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
            assertEquals(5, success(ledger.categoryRepository.list()).size)
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

    @Test fun reorderStoresGroupAndAccountOrderAndRejectsIncompleteLists() =
        runBlocking {
            val first = success(ledger.accountRepository.saveGroup(AccountGroup("first", "First", 0)))
            val second = success(ledger.accountRepository.saveGroup(AccountGroup("second", "Second", 1)))
            listOf("a", "b", "c").forEachIndexed { position, id ->
                success(
                    ledger.accountRepository.save(
                        Account(id, id.uppercase(), usd, position = position),
                        openingMinor =
                            100L * (position + 1),
                    ),
                )
            }
            success(ledger.accountRepository.reorder(listOf(second.id, first.id), listOf("c", "a", "b")))
            assertEquals(listOf(second.id, first.id), success(ledger.accountRepository.groups()).map { it.id })
            val listed = success(ledger.accountRepository.list())
            assertEquals(listOf("c", "a", "b"), listed.map { it.account.id })
            // Order is presentation only: balances stay with their accounts.
            assertEquals(listOf(300L, 100L, 200L), listed.map { it.balanceMinor })
            assertEquals(LedgerResult.Conflict, ledger.accountRepository.reorder(listOf(first.id), listOf("c", "a", "b")))
            assertEquals(LedgerResult.Conflict, ledger.accountRepository.reorder(listOf(second.id, first.id), listOf("a", "a", "b")))
            assertEquals(
                LedgerResult.Conflict,
                ledger.accountRepository.reorder(listOf(second.id, first.id), listOf("a", "b", "c", "gone")),
            )
            assertEquals(listOf("c", "a", "b"), success(ledger.accountRepository.list()).map { it.account.id })
        }

    @Test fun saveWithAdjustmentPostsTheDifferenceAtomicallyAndRejectsAStaleBalance() =
        runBlocking {
            val cash = success(ledger.accountRepository.save(Account("cash", "Cash", usd), openingMinor = 42180))
            val locked = success(ledger.accounts()).single()
            assertTrue(locked.currencyLocked)
            val renamed =
                success(
                    ledger.accountRepository.saveWithAdjustment(
                        locked.copy(name = "Travel card"),
                        locked,
                        BalanceAdjustment(42180, 40980, "Reconciled with statement"),
                    ),
                )
            assertEquals("Travel card", renamed.name)
            assertEquals(40980L, success(ledger.balance("cash")))
            val adjustment = success(ledger.page()).entries.first()
            assertEquals(EntryKind.ADJUSTMENT, adjustment.input.kind)
            assertEquals(-1200L, adjustment.input.amountMinor)
            assertEquals("Reconciled with statement", adjustment.input.note)
            assertTrue(success(ledger.totals(LocalDate.now(), LocalDate.now())).isEmpty())
            // A balance that moved since the form was opened must not be overwritten.
            val stale = BalanceAdjustment(42180, 50000)
            assertEquals(LedgerResult.Conflict, ledger.accountRepository.saveWithAdjustment(renamed.copy(name = "Stale"), renamed, stale))
            assertEquals(LedgerResult.Conflict, ledger.accountRepository.saveWithAdjustment(cash.copy(name = "Stale"), cash, stale))
            assertEquals(
                LedgerResult.Invalid(InvalidReason.AMOUNT),
                ledger.accountRepository.saveWithAdjustment(renamed, renamed, BalanceAdjustment(40980, 40980)),
            )
            // Archiving and adjusting in one save posts first, then archives.
            val archived =
                success(ledger.accountRepository.saveWithAdjustment(renamed.copy(archived = true), renamed, BalanceAdjustment(40980, 0)))
            assertTrue(archived.archived)
            assertEquals(0L, success(ledger.balance("cash")))
            assertEquals(
                LedgerResult.Invalid(InvalidReason.ACCOUNT),
                ledger.accountRepository.saveWithAdjustment(archived, archived, BalanceAdjustment(0, 100)),
            )
            assertEquals("Travel card", success(ledger.accounts()).single().name)
            assertEquals(3, success(ledger.page()).entries.size)
        }

    @Test fun runningBalancesFollowPostedOrderPerAccountAndAdjustmentFilterIncludesOpenings() =
        runBlocking {
            // Entries cannot be dated after today, so the fixture ends today.
            val day = LocalDate.now().minusDays(2)
            val jpy = BookCurrency("JPY", 0)
            success(ledger.accountRepository.save(Account("bank", "Bank", jpy)))
            success(ledger.accountRepository.save(Account("wise", "Wise", usd)))

            suspend fun post(
                id: String,
                input: EntryInput,
            ) = success(ledger.post(EditorInput("submission-$id", id, "", input)))
            post("opening", EntryInput(EntryKind.OPENING, day, LocalTime.of(8, 0), "bank", 300000))
            post(
                "purchase",
                EntryInput(EntryKind.EXPENSE, day, LocalTime.of(9, 0), "bank", -640, allocations = listOf(Allocation("a", 640))),
            )
            post("transfer", EntryInput(EntryKind.TRANSFER, day.plusDays(1), LocalTime.of(9, 0), "bank", 50000, "wise", 33120))
            val trashed =
                post(
                    "trashed",
                    EntryInput(
                        EntryKind.EXPENSE,
                        day.plusDays(1),
                        LocalTime.of(10, 0),
                        "bank",
                        -999,
                        allocations = listOf(Allocation("b", 999)),
                    ),
                )
            post("adjustment", EntryInput(EntryKind.ADJUSTMENT, day.plusDays(2), LocalTime.of(9, 0), "wise", -120))
            success(ledger.trash(trashed.id, trashed.token))

            val all = success(ledger.runningBalances(listOf("opening", "purchase", "transfer", "trashed", "adjustment", "missing")))
            assertEquals(
                mapOf(
                    ("adjustment" to "wise") to 33000L,
                    ("transfer" to "bank") to 249360L,
                    ("transfer" to "wise") to 33120L,
                    ("purchase" to "bank") to 299360L,
                    ("opening" to "bank") to 300000L,
                ),
                all.associate { (it.entryId to it.accountId) to it.balanceMinor },
            )
            // A filtered list still reports each row's true balance, counting the rows it does not show.
            assertEquals(
                listOf(299360L),
                success(ledger.runningBalances(listOf("purchase"))).map { it.balanceMinor },
            )
            assertTrue(success(ledger.runningBalances(listOf("trashed"))).isEmpty())
            assertTrue(success(ledger.runningBalances(emptyList())).isEmpty())
            assertEquals(249360L, success(ledger.balance("bank")))

            val adjustments = success(ledger.search(LedgerFilter(kind = EntryKind.ADJUSTMENT))).entries.map { it.id }
            assertEquals(listOf("adjustment", "opening"), adjustments)
            assertEquals(listOf("opening"), success(ledger.search(LedgerFilter(kind = EntryKind.OPENING))).entries.map { it.id })
            assertEquals(listOf("purchase"), success(ledger.search(LedgerFilter(kind = EntryKind.EXPENSE))).entries.map { it.id })
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
