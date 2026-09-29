package moe.frost.kochenite.domain.ledger

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LedgerTest {
    private val usd = BookCurrency("USD", 2)
    private val today = LocalDate.of(2026, 9, 22)
    private val accounts =
        listOf(
            Account("cash", "Cash", usd),
            Account("card", "Card", usd),
            Account("yen", "Yen", BookCurrency("JPY", 0)),
        ).associateBy { it.id }

    private fun expense(amount: Long = 1234) =
        EntryInput(
            EntryKind.EXPENSE,
            today,
            LocalTime.NOON,
            "cash",
            if (amount > 0) -amount else amount,
            allocations = listOf(Allocation("food", runCatching { Math.absExact(amount) }.getOrDefault(Long.MAX_VALUE))),
        )

    private fun check(input: EntryInput) = validateEntry(input, accounts, emptyMap(), today)

    @Test fun currencyParsingIsExactForAllSupportedScales() {
        assertEquals(9007199254740993L, usd.parseMinor("90071992547409.93"))
        assertEquals(1234, BookCurrency("JPY", 0).parseMinor("1234"))
        assertEquals(1234, BookCurrency("KWD", 3).parseMinor("1.234"))
        assertEquals("-1.23", usd.formatMinor(-123))
        assertEquals(Long.MIN_VALUE, usd.parseMinor("-92233720368547758.08"))
        assertFailsWith<ArithmeticException> { usd.parseMinor("1.001") }
        assertFailsWith<ArithmeticException> { BookCurrency("JPY", 0).parseMinor("125.00") }
        assertFailsWith<ArithmeticException> { usd.parseMinor("92233720368547758.08") }
        assertFailsWith<IllegalArgumentException> { BookCurrency("usd", 2) }
    }

    @Test fun expenseAndIncomeSignsAndSignedAdjustmentsProduceExpectedPostings() {
        for ((kind, amount, expected) in listOf(
            Triple(EntryKind.EXPENSE, -1234L, -1234L),
            Triple(EntryKind.EXPENSE, 1234L, 1234L),
            Triple(EntryKind.INCOME, 1234L, 1234L),
            Triple(EntryKind.OPENING, -20000L, -20000L),
            Triple(EntryKind.ADJUSTMENT, Long.MIN_VALUE, Long.MIN_VALUE),
            Triple(EntryKind.OPENING, 0L, 0L),
        )) {
            val allocations =
                if (kind in
                    setOf(EntryKind.OPENING, EntryKind.ADJUSTMENT)
                ) {
                    emptyList()
                } else {
                    listOf(Allocation("a", runCatching { Math.absExact(amount) }.getOrDefault(Long.MAX_VALUE)))
                }
            val result =
                assertIs<LedgerResult.Success<ValidatedEntry>>(
                    check(expense().copy(kind = kind, amountMinor = amount, allocations = allocations)),
                )
            assertEquals(listOf(Posting("cash", expected)), result.value.postings)
        }
    }

    @Test fun sameCurrencyTransfersMustMatchWhileCrossCurrencyAmountsAreIndependent() {
        val transfer =
            expense(
                1000,
            ).copy(
                kind = EntryKind.TRANSFER,
                amountMinor = 1000,
                destinationAccountId = "card",
                receivedMinor = 1000,
                allocations = emptyList(),
            )
        assertEquals(
            listOf(Posting("cash", -1000), Posting("card", 1000)),
            assertIs<LedgerResult.Success<ValidatedEntry>>(check(transfer)).value.postings,
        )
        assertEquals(LedgerResult.Invalid(InvalidReason.TRANSFER), check(transfer.copy(receivedMinor = 999)))
        assertIs<LedgerResult.Success<ValidatedEntry>>(check(transfer.copy(destinationAccountId = "yen", receivedMinor = 1450)))
        assertEquals(LedgerResult.Invalid(InvalidReason.MISSING_AMOUNT), check(transfer.copy(receivedMinor = null)))
        assertEquals(LedgerResult.Invalid(InvalidReason.TRANSFER), check(transfer.copy(destinationAccountId = "cash")))
    }

    @Test fun allocationsMustReconcileAndOverflowIsRejected() {
        assertIs<LedgerResult.Success<ValidatedEntry>>(
            check(expense(1500).copy(allocations = listOf(Allocation("a", 1000), Allocation("b", 500)))),
        )
        assertEquals(LedgerResult.Invalid(InvalidReason.ALLOCATIONS), check(expense().copy(allocations = listOf(Allocation("a", 1200)))))
        assertEquals(
            LedgerResult.Invalid(InvalidReason.OVERFLOW),
            check(expense(Long.MAX_VALUE).copy(allocations = listOf(Allocation("a", Long.MAX_VALUE), Allocation("b", 1)))),
        )
        assertEquals(
            LedgerResult.Invalid(InvalidReason.ALLOCATIONS),
            check(expense().copy(allocations = listOf(Allocation("a", 617), Allocation("a", 617)))),
        )
        assertEquals(LedgerResult.Invalid(InvalidReason.OVERFLOW), check(expense(Long.MIN_VALUE)))
    }

    @Test fun missingAndFutureActualAmountsNeverBecomePostings() {
        assertEquals(
            LedgerResult.Invalid(InvalidReason.MISSING_AMOUNT),
            check(expense().copy(amountMinor = null, merchantAmount = Money(1000, BookCurrency("JPY", 0)))),
        )
        assertEquals(LedgerResult.Invalid(InvalidReason.FUTURE_DATE), check(expense().copy(date = today.plusDays(1))))
        assertIs<LedgerResult.Success<ValidatedEntry>>(check(expense().copy(date = today.minusYears(20), time = LocalTime.of(23, 59, 59))))
    }

    @Test fun calendarDatesOutsideTheIndexedIsoRangeAreRejected() {
        assertEquals(LedgerResult.Invalid(InvalidReason.DATE), check(expense().copy(date = LocalDate.of(0, 1, 1))))
        assertEquals(LedgerResult.Invalid(InvalidReason.DATE), check(expense().copy(date = LocalDate.of(10000, 1, 1))))
    }

    @Test fun categoryKindsAndArchivedReferencesAreValidated() {
        val food = Category("food", "Food", CategoryKind.EXPENSE)
        val input = expense().copy(allocations = listOf(Allocation("a", 1234, "food")))
        assertIs<LedgerResult.Success<ValidatedEntry>>(validateEntry(input, accounts, mapOf("food" to food), today))
        assertEquals(
            LedgerResult.Invalid(InvalidReason.CATEGORY),
            validateEntry(
                input.copy(kind = EntryKind.INCOME, amountMinor = 1234, allocations = listOf(Allocation("a", 1234, "food"))),
                accounts,
                mapOf("food" to food),
                today,
            ),
        )
        assertEquals(
            LedgerResult.Invalid(InvalidReason.CATEGORY),
            validateEntry(input, accounts, mapOf("food" to food.copy(archived = true)), today),
        )
        assertEquals(
            LedgerResult.Invalid(InvalidReason.ACCOUNT),
            validateEntry(
                input,
                accounts.mapValues {
                    it.value.copy(archived = true)
                },
                emptyMap(),
                today,
            ),
        )
    }

    @Test fun receiptLinesAndSplitsMustBothReconcile() {
        val input =
            expense(1001).copy(
                merchantAmount = Money(300, BookCurrency("JPY", 0)),
                allocations = listOf(Allocation("a", 334), Allocation("b", 334), Allocation("c", 333)),
                receiptLines = listOf(ReceiptLine("a", "One", 100), ReceiptLine("b", "Two", 100), ReceiptLine("c", "Three", 100)),
            )
        assertIs<LedgerResult.Success<ValidatedEntry>>(check(input))
        assertEquals(LedgerResult.Invalid(InvalidReason.ALLOCATIONS), check(input.copy(receiptLines = input.receiptLines.dropLast(1))))
        assertEquals(
            LedgerResult.Invalid(InvalidReason.ALLOCATIONS),
            check(
                input.copy(
                    receiptLines =
                        input.receiptLines.mapIndexed { index, line ->
                            if (index == 0) line.copy(merchantMinor = 99) else line
                        },
                ),
            ),
        )
        assertEquals(LedgerResult.Invalid(InvalidReason.ALLOCATIONS), check(input.copy(allocations = input.allocations.dropLast(1))))
        assertIs<LedgerResult.Success<ValidatedEntry>>(
            check(input.copy(receiptLines = input.receiptLines.map { it.copy(description = "") })),
        )
    }

    @Test fun largestRemainderUsesStableLineOrderAndExactIntegerMoney() {
        assertEquals(
            listOf(334L, 334L, 333L),
            assertIs<LedgerResult.Success<List<Long>>>(proportionalAllocations(1001, listOf(100, 100, 100))).value,
        )
        assertEquals(listOf(1L, 0L, 0L), assertIs<LedgerResult.Success<List<Long>>>(proportionalAllocations(1, listOf(1, 1, 1))).value)
        assertIs<LedgerResult.Success<ValidatedEntry>>(
            check(
                expense(1).copy(
                    allocations = listOf(Allocation("a", 1), Allocation("b", 0)),
                    receiptLines = listOf(ReceiptLine("a", "First", 1), ReceiptLine("b", "Second", 1)),
                    merchantAmount = Money(2, BookCurrency("JPY", 0)),
                ),
            ),
        )
        assertEquals(
            listOf(9007199254740993L),
            assertIs<LedgerResult.Success<List<Long>>>(proportionalAllocations(9007199254740993L, listOf(9007199254740993L))).value,
        )
        assertEquals(LedgerResult.Invalid(InvalidReason.OVERFLOW), proportionalAllocations(1, listOf(Long.MAX_VALUE, 1)))
        assertEquals(LedgerResult.Invalid(InvalidReason.ALLOCATIONS), proportionalAllocations(10, listOf(0, 10)))
    }
}
