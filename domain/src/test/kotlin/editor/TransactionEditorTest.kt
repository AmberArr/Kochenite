package moe.frost.kochenite.domain.editor

import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.ReceiptLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TransactionEditorTest {
    private val usd = BookCurrency("USD", 2)
    private val eur = BookCurrency("EUR", 2)
    private val jpy = BookCurrency("JPY", 0)
    private val accounts =
        listOf(
            Account("cash", "Cash", usd),
            Account("euro", "Euro", eur),
            Account("yen", "Yen", jpy),
        )

    private fun editor() =
        EditorInput(
            "session",
            "entry",
            "submission",
            EntryInput(
                EntryKind.EXPENSE,
                LocalDate.of(2026, 9, 25),
                LocalTime.NOON,
                "cash",
                -12500,
                allocations = listOf(Allocation("category", 12500, "food")),
            ),
            amountText = "-125.00",
        ).prepared(accounts)

    @Test fun newExpenseDirectionAndKindSelectionSetSignedAmount() {
        val start = editor()
        assertTrue(start.negative)
        assertEquals(-12500L, start.input.amountMinor)
        val income = start.withKind(EntryKind.INCOME, accounts)
        assertEquals(false, income.negative)
        assertEquals(12500L, income.input.amountMinor)
        val expense = income.withKind(EntryKind.EXPENSE, accounts)
        assertEquals(true, expense.negative)
        assertEquals(-12500L, expense.input.amountMinor)
    }

    @Test fun changingTransactionCurrencyRetainsItsNumberAndClearsAccountAmount() {
        val separated = editor().withCurrency(eur, accounts)
        assertEquals("125.00", separated.mainText(accounts))
        assertEquals("", separated.amountText)
        assertNull(separated.input.amountMinor)
        assertEquals(12500L, separated.input.merchantAmount?.minor)

        val merged = separated.withCurrency(usd, accounts)
        assertEquals("125.00", merged.mainText(accounts))
        assertEquals(-12500L, merged.input.amountMinor)
        assertNull(merged.input.merchantAmount)
    }

    @Test fun changingAccountPreservesTransactionAmountAndClearsAccountAmount() {
        val separated = editor().withCurrency(eur, accounts)
        val changed = separated.withAccount("yen", accounts)
        assertEquals("125.00", changed.mainText(accounts))
        assertEquals("", changed.amountText)
        assertEquals(eur, changed.transactionCurrency)
        assertNull(changed.input.amountMinor)
    }

    @Test fun changingCurrencyWithTwoValuesMergesUsingTransactionAmount() {
        val split =
            editor()
                .withCurrency(eur, accounts)
                .copy(amountText = "-100.00")
                .recalculate(accounts)
        assertEquals(-10000L, split.input.amountMinor)
        assertEquals(12500L, split.input.merchantAmount?.minor)

        val merged = split.withCurrency(usd, accounts)

        assertEquals("125.00", merged.mainText(accounts))
        assertEquals(-12500L, merged.input.amountMinor)
        assertNull(merged.input.merchantAmount)
    }

    @Test fun changingPrecisionKeepsTextAndExposesInvalidAmountWithoutRounding() {
        val changed = editor().withCurrency(jpy, accounts)

        assertEquals("125.00", changed.mainText(accounts))
        assertNull(changed.input.merchantAmount)
        assertNull(changed.input.amountMinor)
    }

    @Test fun transferAmountsFollowAccountsAndSynchronizeOnlyForMatchingCurrencies() {
        val transfer =
            editor()
                .withKind(EntryKind.TRANSFER, accounts)
                .copy(
                    input =
                        editor().input.copy(
                            kind = EntryKind.TRANSFER,
                            amountMinor = 12500,
                            destinationAccountId = "yen",
                            receivedMinor = null,
                            allocations = emptyList(),
                            receiptLines = emptyList(),
                        ),
                    amountText = "125.00",
                    receivedText = "1500",
                ).recalculate(accounts)

        assertEquals(12500L, transfer.input.amountMinor)
        assertEquals(1500L, transfer.input.receivedMinor)

        val sameCurrency =
            transfer
                .withAccount("euro", accounts)
                .withAccount("euro", accounts, destination = true)
                .copy(amountText = "80.00")
                .recalculate(accounts)
        assertEquals(8000L, sameCurrency.input.amountMinor)
        assertEquals(8000L, sameCurrency.input.receivedMinor)
    }

    @Test fun explicitItemsDoNotChangeTransactionTotalAndKeepStableOrderAndIdentity() {
        val original = editor()
        val first = original.addItem(accounts)
        val id =
            first.input.receiptLines
                .single()
                .id
        assertEquals("", first.lineTexts[id])
        assertEquals(
            0L,
            first.input.receiptLines
                .single()
                .merchantMinor,
        )
        assertEquals(-12500L, first.input.amountMinor)
        assertEquals(
            "food",
            first.input.allocations
                .single()
                .categoryId,
        )

        val second = first.addItem(accounts)
        val secondId =
            second.input.receiptLines
                .last()
                .id
        val reordered = second.moveItem(secondId, -1, accounts)
        assertEquals(listOf(secondId, id), reordered.input.receiptLines.map { it.id })
        assertEquals(-12500L, reordered.input.amountMinor)

        val oneLeft = reordered.removeItem(secondId, accounts)
        assertEquals(listOf(id), oneLeft.input.receiptLines.map { it.id })
        val ordinary = oneLeft.removeItem(id, accounts)
        assertTrue(ordinary.input.receiptLines.isEmpty())
        assertEquals(
            "food",
            ordinary.input.allocations
                .single()
                .categoryId,
        )
        assertEquals(-12500L, ordinary.input.amountMinor)
    }

    @Test fun persistedForeignAllocationsSurviveOpeningAndMetadataEdits() {
        val original =
            editor().input.copy(
                amountMinor = -1000,
                merchantAmount =
                    moe.frost.kochenite.domain.ledger
                        .Money(1000, eur),
                allocations = listOf(Allocation("a", 700, "food"), Allocation("b", 300, "other")),
                receiptLines = listOf(ReceiptLine("a", "One", 500), ReceiptLine("b", "Two", 500)),
            )
        val opened = EditorInput("edit", "entry", "", original, originalToken = "token").prepared(accounts)
        assertEquals(original, opened.input)
        val changed = opened.copy(input = opened.input.copy(note = "Only a note")).recalculate(accounts)
        assertEquals(original.allocations, changed.input.allocations)
        val signed = changed.copy(negative = false).recalculate(accounts)
        assertEquals(original.allocations, signed.input.allocations)
        val newCharge = changed.copy(amountText = "-12.00").recalculate(accounts)
        assertEquals(listOf(600L, 600L), newCharge.input.allocations.map { it.amountMinor })
    }

    @Test fun leavingTransferClearsBothRawAndParsedReceivedAmount() {
        for (destination in listOf("cash", "euro")) {
            val transfer =
                EditorInput(
                    "s",
                    "e",
                    "",
                    EntryInput(
                        EntryKind.TRANSFER,
                        LocalDate.now(),
                        LocalTime.NOON,
                        "cash",
                        1000,
                        destination,
                        1000,
                    ),
                ).prepared(accounts)
            for (kind in listOf(EntryKind.EXPENSE, EntryKind.INCOME)) {
                val changed = transfer.withKind(kind, accounts)
                assertNull(changed.receivedText)
                assertNull(changed.input.receivedMinor)
                assertNull(changed.input.destinationAccountId)
            }
        }
    }

    @Test fun reselectingKindKeepsCategoriesButChangingKindClearsThem() {
        val original = editor()
        assertEquals(original.input.allocations, original.withKind(EntryKind.EXPENSE, accounts).input.allocations)
        assertNull(
            original
                .withKind(EntryKind.INCOME, accounts)
                .input.allocations
                .single()
                .categoryId,
        )
    }

    @Test fun reorderingForeignItemsReassignsRemainderByNewOrder() {
        val original =
            EditorInput(
                "s",
                "e",
                "",
                editor().input.copy(
                    amountMinor = -101,
                    merchantAmount =
                        moe.frost.kochenite.domain.ledger
                            .Money(200, eur),
                    allocations = listOf(Allocation("a", 51), Allocation("b", 50)),
                    receiptLines = listOf(ReceiptLine("a", "", 100), ReceiptLine("b", "", 100)),
                ),
            ).prepared(accounts)
        val moved = original.moveItem("b", -1, accounts)
        assertEquals(listOf("b", "a"), moved.input.allocations.map { it.id })
        assertEquals(listOf(51L, 50L), moved.input.allocations.map { it.amountMinor })
    }
}
