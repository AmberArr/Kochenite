package moe.frost.kochenite.transactions

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import moe.frost.kochenite.accounts.moved
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.editor.prepared
import moe.frost.kochenite.domain.editor.withMainText
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.LedgerFilter
import moe.frost.kochenite.domain.ledger.LedgerPage
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.ReceiptLine
import moe.frost.kochenite.domain.ledger.RunningBalance
import moe.frost.kochenite.reports.categoryShares
import moe.frost.kochenite.templates.templateInput
import moe.frost.kochenite.transactions.editor.reconcile
import moe.frost.kochenite.transactions.list.LedgerTab
import moe.frost.kochenite.transactions.list.TransactionListStateOwner
import moe.frost.kochenite.transactions.list.TransactionQuery
import moe.frost.kochenite.transactions.list.dayNet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionPresentationTest {
    private val usd = BookCurrency("USD", 2)
    private val jpy = BookCurrency("JPY", 0)

    @Test fun queryBecomesALedgerFilterOnlyWhileEveryValueIsValid() {
        val currencies = listOf(usd, jpy)
        assertEquals(LedgerFilter(), TransactionQuery().filter(currencies))
        assertEquals(EntryStatus.TRASH, TransactionQuery(tab = LedgerTab.TRASH).filter(currencies)!!.status)
        val ranged = TransactionQuery(amountCode = "USD", minimumText = "5.00", maximumText = "10")
        assertEquals(LedgerFilter(amountCurrency = usd, minimumMinor = 500, maximumMinor = 1000), ranged.filter(currencies))
        assertNull(ranged.copy(maximumText = "4.99").filter(currencies))
        assertNull(ranged.copy(minimumText = "5.001").filter(currencies))
        assertNull(ranged.copy(minimumText = "-1").filter(currencies))
        // Amounts need a currency the book actually holds.
        assertNull(ranged.copy(amountCode = "EUR").filter(currencies))
        val day = LocalDate.of(2026, 9, 30)
        assertNull(TransactionQuery(from = day, through = day.minusDays(1)).filter(currencies))
        assertNotNull(TransactionQuery(from = day, through = day).filter(currencies))
    }

    @Test fun clearingAQueryKeepsItsTabAndModeButDropsSearchAndFilters() {
        val query = TransactionQuery(tab = LedgerTab.TRASH, text = "coffee", accountId = "cash", bulk = true)
        assertTrue(query.active)
        assertEquals(TransactionQuery(tab = LedgerTab.TRASH, bulk = true), query.cleared())
        assertFalse(query.cleared().active)
    }

    @Test fun dayNetCountsIncomeAndExpensesPerCurrencyAndNothingElse() {
        fun entry(
            id: String,
            kind: EntryKind,
            account: String,
            amount: Long,
        ) = fixtureEntry(id).let { it.copy(input = it.input.copy(kind = kind, accountId = account, amountMinor = amount)) }
        val entries =
            listOf(
                entry("a", EntryKind.EXPENSE, "cash", -640),
                entry("b", EntryKind.INCOME, "cash", 31000),
                entry("c", EntryKind.TRANSFER, "cash", 5000),
                entry("d", EntryKind.ADJUSTMENT, "cash", 99),
                entry("e", EntryKind.EXPENSE, "yen", -2480),
                entry("f", EntryKind.EXPENSE, "unknown", -1),
            )
        val currencies = mapOf("cash" to usd, "yen" to jpy)
        assertEquals(listOf(usd to 30360L, jpy to -2480L), dayNet(entries) { currencies[it] })
    }

    @Test fun postedListsLoadRunningBalancesAfterTheirRows() =
        runTest {
            val a = fixtureEntry("a")
            var asked = emptyList<String>()
            val book =
                object : FakeLedger() {
                    override suspend fun runningBalances(entryIds: List<String>): LedgerResult<List<RunningBalance>> {
                        asked = entryIds
                        return LedgerResult.Success(listOf(RunningBalance("a", "cash", 4200)))
                    }
                }
            book.searchCall = { _, _ -> LedgerResult.Success(LedgerPage(listOf(a), null)) }
            val owner = TransactionListStateOwner(this) { book }
            owner.refresh()
            advanceUntilIdle()
            assertEquals(listOf("a"), asked)
            assertEquals(mapOf(("a" to "cash") to 4200L), owner.state.value.balances)
            // Trashed entries have no balance to show.
            asked = emptyList()
            owner.search(LedgerFilter(status = EntryStatus.TRASH))
            advanceUntilIdle()
            assertTrue(asked.isEmpty())
            assertTrue(
                owner.state.value.balances
                    .isEmpty(),
            )
        }

    @Test fun itemsReconcileAgainstTheAmountWithoutBalancingAnything() {
        val account = FakeLedger().account
        val categories = listOf(Category("food", "Food", CategoryKind.EXPENSE), Category("pay", "Pay", CategoryKind.INCOME))
        val input =
            EntryInput(
                EntryKind.EXPENSE,
                LocalDate.of(2026, 9, 29),
                LocalTime.NOON,
                account.id,
                null,
                allocations = listOf(Allocation("one", 0, "food"), Allocation("two", 0, "pay")),
                receiptLines = listOf(ReceiptLine("one", "Rice", 0), ReceiptLine("two", "Towels", 0)),
            )
        val editor =
            EditorInput("editor", "entry", "", input, lineTexts = mapOf("one" to "52.00", "two" to "23.00"))
                .prepared(listOf(account))
                .withMainText("87.60", listOf(account))
        val mismatch = reconcile(editor, listOf(account), categories)
        assertEquals(7500L, mismatch.sum)
        assertEquals(8760L, mismatch.target)
        assertEquals(-1260L, mismatch.difference)
        assertFalse(mismatch.matched)
        // An income category on an expense item is flagged rather than silently kept.
        assertEquals(setOf("two"), mismatch.invalidCategoryItems)
        val pending = reconcile(editor.copy(lineTexts = editor.lineTexts + ("two" to "")), listOf(account), categories)
        assertTrue(pending.pending)
        assertNull(pending.difference)
        val matched =
            reconcile(
                editor
                    .copy(input = editor.input.copy(allocations = listOf(Allocation("one", 0, "food"), Allocation("two", 0, null))))
                    .withMainText("75.00", listOf(account)),
                listOf(account),
                categories,
            )
        assertTrue(matched.matched)
        assertEquals(0L, matched.difference)
    }

    @Test fun categorySharesRoundHalfUpAndLeaveNetCreditsWithoutAShare() {
        val shares =
            categoryShares(listOf("a" to 62300L, "b" to 38120L, null to 24800L, "credit" to -500L, "zero" to 0L)) {
                it
                    ?: "Uncategorized"
            }
        assertEquals(listOf("a", "b", "Uncategorized", "credit"), shares.map { it.name })
        assertEquals(listOf(50, 30, 20, null), shares.map { it.percent })
        assertEquals(
            listOf(1),
            categoryShares(listOf("a" to 1L, "b" to 199L)) { it.orEmpty() }.filter { it.name == "a" }.map { it.percent },
        )
    }

    @Test fun templatesKeepOnlyWhatWasAsked() {
        val source =
            EntryInput(
                EntryKind.EXPENSE,
                LocalDate.of(2026, 9, 29),
                LocalTime.NOON,
                "cash",
                -8760,
                allocations = listOf(Allocation("one", 5200, "food"), Allocation("two", 3560, "home")),
                merchant = "Aeon Mall",
                relatedEntryIds = listOf("refund"),
                receiptLines = listOf(ReceiptLine("one", "Rice", 5200), ReceiptLine("two", "Towels", 3560)),
            )
        val bare = templateInput(source, includeAmounts = false, includeSplits = false)
        assertNull(bare.amountMinor)
        assertTrue(bare.allocations.isEmpty() && bare.receiptLines.isEmpty() && bare.relatedEntryIds.isEmpty())
        val splits = templateInput(source, includeAmounts = false, includeSplits = true)
        assertEquals(listOf(0L, 0L), splits.allocations.map { it.amountMinor })
        assertEquals(listOf("food", "home"), splits.allocations.map { it.categoryId })
        assertEquals(listOf("Rice", "Towels"), splits.receiptLines.map { it.description })
        val amounts = templateInput(source, includeAmounts = true, includeSplits = false)
        assertEquals(-8760L, amounts.amountMinor)
        assertEquals(listOf(8760L), amounts.allocations.map { it.amountMinor })
        assertEquals("food", amounts.allocations.single().categoryId)
        assertEquals(source.copy(relatedEntryIds = emptyList()), templateInput(source, includeAmounts = true, includeSplits = true))
    }

    @Test fun movingARowStopsAtEitherEnd() {
        val rows = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), rows.moved(0, 1))
        assertEquals(listOf("a", "c", "b"), rows.moved(2, -1))
        assertEquals(rows, rows.moved(0, -1))
        assertEquals(rows, rows.moved(2, 1))
    }
}
