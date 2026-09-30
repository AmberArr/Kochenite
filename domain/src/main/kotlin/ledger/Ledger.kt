package moe.frost.kochenite.domain.ledger

import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.editor.IncompleteRecord
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/** Currency scale is part of a book's authoritative currency definition. */
data class BookCurrency(
    val code: String,
    val scale: Int,
) {
    init {
        require(code.matches(Regex("[A-Z]{3}")) && scale in 0..3)
    }

    fun parseMinor(text: String): Long {
        val amount = BigDecimal(text)
        if (amount.scale() > scale) throw ArithmeticException("Amount exceeds $code precision")
        return amount.movePointRight(scale).longValueExact()
    }

    fun formatMinor(minor: Long): String = BigDecimal.valueOf(minor, scale).toPlainString()
}

data class Money(
    val minor: Long,
    val currency: BookCurrency,
)

enum class EntryKind { EXPENSE, INCOME, TRANSFER, OPENING, ADJUSTMENT }

enum class EntryStatus { POSTED, TRASH }

enum class CategoryKind { EXPENSE, INCOME }

data class Account(
    val id: String,
    val name: String,
    val currency: BookCurrency,
    val archived: Boolean = false,
    val includeInNetWorth: Boolean = true,
    val currencyLocked: Boolean = false,
    val groupId: String? = null,
    val type: String = "",
    val position: Int = 0,
)

data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val parentId: String? = null,
    val archived: Boolean = false,
)

data class Allocation(
    val id: String,
    val amountMinor: Long,
    val categoryId: String? = null,
)

/** A final, inclusive receipt total. Its ID matches exactly one account-currency allocation. */
data class ReceiptLine(
    val id: String,
    val description: String,
    val merchantMinor: Long,
)

/** Proposes exact account-currency shares; equal remainders follow receipt order. */
fun proportionalAllocations(
    accountMinor: Long,
    lineTotals: List<Long>,
): LedgerResult<List<Long>> =
    try {
        if (accountMinor <= 0 || lineTotals.isEmpty() || lineTotals.size > 1000 || lineTotals.any { it <= 0 }) {
            LedgerResult.Invalid(InvalidReason.ALLOCATIONS)
        } else {
            val denominator = lineTotals.fold(0L, Math::addExact)
            val divisor = BigDecimal.valueOf(denominator)
            val shares =
                lineTotals.map { line ->
                    val product = BigDecimal.valueOf(accountMinor).multiply(BigDecimal.valueOf(line))
                    product.divideToIntegralValue(divisor).longValueExact() to product.remainder(divisor)
                }
            val result = shares.map { it.first }.toMutableList()
            var remaining = Math.subtractExact(accountMinor, result.fold(0L, Math::addExact))
            for (index in shares.indices.sortedWith(compareByDescending<Int> { shares[it].second }.thenBy { it })) {
                if (remaining == 0L) break
                result[index] = Math.addExact(result[index], 1L)
                remaining--
            }
            if (remaining != 0L) LedgerResult.Invalid(InvalidReason.OVERFLOW) else LedgerResult.Success(result)
        }
    } catch (_: ArithmeticException) {
        LedgerResult.Invalid(InvalidReason.OVERFLOW)
    }

/** Transient editor input; only an explicit incomplete save serializes this payload. */
data class EntryInput(
    val kind: EntryKind,
    val date: LocalDate,
    val time: LocalTime,
    val accountId: String,
    val amountMinor: Long?,
    val destinationAccountId: String? = null,
    val receivedMinor: Long? = null,
    val allocations: List<Allocation> = emptyList(),
    val merchant: String = "",
    val note: String = "",
    val merchantAmount: Money? = null,
    val relatedEntryIds: List<String> = emptyList(),
    val receiptLines: List<ReceiptLine> = emptyList(),
    val tagIds: List<String> = emptyList(),
)

data class Posting(
    val accountId: String,
    val amountMinor: Long,
)

data class ValidatedEntry(
    val input: EntryInput,
    val postings: List<Posting>,
    val currency: BookCurrency,
)

enum class InvalidReason {
    IDENTITY,
    MISSING_AMOUNT,
    AMOUNT,
    OVERFLOW,
    FUTURE_DATE,
    DATE,
    ACCOUNT,
    CATEGORY,
    TAG,
    CURRENCY,
    TRANSFER,
    ALLOCATIONS,
    TEXT_TOO_LONG,
    RELATED_ENTRY,
    STATE,
}

sealed interface LedgerResult<out T> {
    data class Success<T>(
        val value: T,
    ) : LedgerResult<T>

    data class Invalid(
        val reason: InvalidReason,
    ) : LedgerResult<Nothing>

    data object Conflict : LedgerResult<Nothing>

    data object Missing : LedgerResult<Nothing>

    data object AlreadyConfirmed : LedgerResult<Nothing>

    data object TemporarilyUnavailable : LedgerResult<Nothing>

    data object RecoveryRequired : LedgerResult<Nothing>

    data object UnexpectedFailure : LedgerResult<Nothing>
}

fun validId(id: String): Boolean = id.isNotBlank() && id.length <= 128

/** Pure validation, independent of storage and Android. No rate can supply actual amounts. */
fun validateEntry(
    input: EntryInput,
    accounts: Map<String, Account>,
    categories: Map<String, Category>,
    today: LocalDate,
    allowMissingAmounts: Boolean = false,
): LedgerResult<ValidatedEntry> {
    fun invalid(reason: InvalidReason) = LedgerResult.Invalid(reason)
    if (input.date.year !in 1..9999) return invalid(InvalidReason.DATE)
    if (input.date > today) return invalid(InvalidReason.FUTURE_DATE)
    if (input.merchant.length > 500 || input.note.length > 10000) return invalid(InvalidReason.TEXT_TOO_LONG)
    if (input.receiptLines.size > 1000 || input.receiptLines.any { it.description.length > 500 }) {
        return invalid(InvalidReason.TEXT_TOO_LONG)
    }
    if (input.tagIds.size > 100 || input.tagIds.distinct().size != input.tagIds.size || input.tagIds.any { !validId(it) }) {
        return invalid(InvalidReason.IDENTITY)
    }
    val account = accounts[input.accountId]?.takeUnless { it.archived } ?: return invalid(InvalidReason.ACCOUNT)
    val amount = input.amountMinor
    val missing = amount == null || (input.kind == EntryKind.TRANSFER && input.receivedMinor == null)
    if (input.merchantAmount?.minor?.let { it <= 0 } == true) return invalid(InvalidReason.AMOUNT)
    if (input.relatedEntryIds.size > 100 || input.relatedEntryIds.distinct().size != input.relatedEntryIds.size ||
        input.relatedEntryIds.any { !validId(it) }
    ) {
        return invalid(InvalidReason.RELATED_ENTRY)
    }
    val balanceOnly = input.kind in setOf(EntryKind.OPENING, EntryKind.ADJUSTMENT)
    if (!balanceOnly &&
        (amount == 0L || (input.kind == EntryKind.TRANSFER && amount != null && amount < 0))
    ) {
        return invalid(InvalidReason.AMOUNT)
    }
    if (input.kind != EntryKind.TRANSFER && (input.destinationAccountId != null || input.receivedMinor != null)) {
        return invalid(InvalidReason.TRANSFER)
    }
    return try {
        val postings =
            when (input.kind) {
                EntryKind.TRANSFER -> {
                    val destination =
                        accounts[input.destinationAccountId]?.takeUnless { it.archived }
                            ?: return invalid(InvalidReason.ACCOUNT)
                    val received = input.receivedMinor
                    if (destination.id == account.id || (received != null && received <= 0)) return invalid(InvalidReason.TRANSFER)
                    if (destination.currency.code == account.currency.code && destination.currency != account.currency) {
                        return invalid(InvalidReason.CURRENCY)
                    }
                    if (destination.currency == account.currency && received != null && amount != null &&
                        received != amount
                    ) {
                        return invalid(InvalidReason.TRANSFER)
                    }
                    if (amount == null ||
                        received == null
                    ) {
                        emptyList()
                    } else {
                        listOf(Posting(account.id, Math.negateExact(amount)), Posting(destination.id, received))
                    }
                }

                EntryKind.EXPENSE, EntryKind.INCOME -> {
                    if (amount == null) emptyList() else listOf(Posting(account.id, amount))
                }

                else -> {
                    if (amount == null) emptyList() else listOf(Posting(account.id, amount))
                }
            }
        if (balanceOnly || input.kind == EntryKind.TRANSFER) {
            if (input.allocations.isNotEmpty() || input.receiptLines.isNotEmpty()) return invalid(InvalidReason.ALLOCATIONS)
        } else {
            if (input.allocations.isEmpty() || input.allocations.size > 1000 ||
                input.allocations
                    .map { it.id }
                    .distinct()
                    .size != input.allocations.size
            ) {
                return invalid(InvalidReason.ALLOCATIONS)
            }
            val expected = if (input.kind == EntryKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            var total = 0L
            for (allocation in input.allocations) {
                if (!validId(allocation.id) || allocation.amountMinor < 0 ||
                    (allocation.amountMinor == 0L && input.receiptLines.isEmpty() && amount != null)
                ) {
                    return invalid(InvalidReason.ALLOCATIONS)
                }
                if (allocation.categoryId != null) {
                    val category = categories[allocation.categoryId] ?: return invalid(InvalidReason.CATEGORY)
                    if (category.archived || category.kind != expected) return invalid(InvalidReason.CATEGORY)
                }
                total = Math.addExact(total, allocation.amountMinor)
            }
            if (amount != null && total != Math.absExact(amount)) return invalid(InvalidReason.ALLOCATIONS)
        }
        if (input.receiptLines.isNotEmpty()) {
            if (input.receiptLines
                    .map { it.id }
                    .distinct()
                    .size != input.receiptLines.size ||
                input.receiptLines.map { it.id }.toSet() != input.allocations.map { it.id }.toSet() ||
                input.receiptLines.any { !validId(it.id) || it.merchantMinor <= 0 }
            ) {
                return invalid(InvalidReason.ALLOCATIONS)
            }
            val receiptTotal = input.receiptLines.fold(0L) { total, line -> Math.addExact(total, line.merchantMinor) }
            val target = input.merchantAmount?.minor ?: amount?.let { Math.absExact(it) }
            if (target != null && receiptTotal != target) return invalid(InvalidReason.ALLOCATIONS)
        }
        if (missing && !allowMissingAmounts) return invalid(InvalidReason.MISSING_AMOUNT)
        LedgerResult.Success(ValidatedEntry(input, postings, account.currency))
    } catch (_: ArithmeticException) {
        invalid(InvalidReason.OVERFLOW)
    }
}

/** Tokens describe current state only; no historical versions are retained. */
data class SavedEntry(
    val id: String,
    val token: String,
    val status: EntryStatus,
    val input: EntryInput,
)

data class LedgerCursor(
    val date: LocalDate,
    val time: LocalTime,
    val id: String,
)

data class LedgerPage(
    val entries: List<SavedEntry>,
    val next: LedgerCursor?,
)

/** One account-side amount of a posted entry. */
data class LedgerPosting(
    val entryId: String,
    val accountId: String,
    val amountMinor: Long,
)

/** The balance of [accountId] immediately after [entryId] posted. */
data class RunningBalance(
    val entryId: String,
    val accountId: String,
    val balanceMinor: Long,
)

/**
 * Walks back from each account's current balance. [newestFirst] must hold every posted posting of
 * the listed accounts from the newest entry down to the oldest one of interest.
 */
fun runningBalances(
    currentBalances: Map<String, Long>,
    newestFirst: List<LedgerPosting>,
): List<RunningBalance> {
    val running = currentBalances.toMutableMap()
    return newestFirst.map { posting ->
        val after = running[posting.accountId] ?: 0L
        running[posting.accountId] = Math.subtractExact(after, posting.amountMinor)
        RunningBalance(posting.entryId, posting.accountId, after)
    }
}

data class CategoryTotal(
    val rootCategoryId: String?,
    val kind: CategoryKind,
    val currency: BookCurrency,
    val amountMinor: Long,
)

data class CurrencyTotals(
    val currency: BookCurrency,
    val incomeMinor: Long,
    val spendingMinor: Long,
) {
    val netFlowMinor: Long get() = Math.subtractExact(incomeMinor, spendingMinor)
}

interface PostingService {
    suspend fun post(
        editor: EditorInput,
        expectedIncompleteToken: String? = null,
    ): LedgerResult<SavedEntry>

    suspend fun saveIncomplete(
        editor: EditorInput,
        expectedToken: String?,
    ): LedgerResult<IncompleteRecord>

    suspend fun deleteIncomplete(
        id: String,
        expectedToken: String,
    ): LedgerResult<Unit>

    suspend fun beginEdit(entryId: String): LedgerResult<EditorInput>

    suspend fun trash(
        entryId: String,
        expectedToken: String,
    ): LedgerResult<SavedEntry>

    suspend fun restore(
        entryId: String,
        expectedToken: String,
    ): LedgerResult<SavedEntry>

    suspend fun purge(
        entryId: String,
        expectedToken: String,
    ): LedgerResult<Unit>
}
