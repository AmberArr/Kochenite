package moe.frost.kochenite.domain.ledger

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

    fun parseMinor(text: String): Long = BigDecimal(text).movePointRight(scale).longValueExact()

    fun formatMinor(minor: Long): String = BigDecimal.valueOf(minor, scale).toPlainString()
}

data class Money(
    val minor: Long,
    val currency: BookCurrency,
)

enum class EntryKind { EXPENSE, INCOME, REFUND, TRANSFER, OPENING, ADJUSTMENT }

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

/** Null actual amounts remain draft input, never authoritative zeroes. */
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
    val purchaseId: String? = null,
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
    CURRENCY,
    TRANSFER,
    ALLOCATIONS,
    TEXT_TOO_LONG,
    REFUND_LINK,
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
}

fun validId(id: String): Boolean = id.isNotBlank() && id.length <= 128

/** Pure validation, independent of storage and Android. No rate can supply actual amounts. */
fun validateEntry(
    input: EntryInput,
    accounts: Map<String, Account>,
    categories: Map<String, Category>,
    today: LocalDate,
): LedgerResult<ValidatedEntry> {
    fun invalid(reason: InvalidReason) = LedgerResult.Invalid(reason)
    if (input.date.year !in 1..9999) return invalid(InvalidReason.DATE)
    if (input.date > today) return invalid(InvalidReason.FUTURE_DATE)
    if (input.merchant.length > 500 || input.note.length > 10000) return invalid(InvalidReason.TEXT_TOO_LONG)
    val account = accounts[input.accountId]?.takeUnless { it.archived } ?: return invalid(InvalidReason.ACCOUNT)
    val amount = input.amountMinor ?: return invalid(InvalidReason.MISSING_AMOUNT)
    if (input.merchantAmount?.minor?.let { it <= 0 } == true) return invalid(InvalidReason.AMOUNT)
    if (input.purchaseId != null && (input.kind != EntryKind.REFUND || !validId(input.purchaseId))) {
        return invalid(InvalidReason.REFUND_LINK)
    }
    val signed = input.kind in setOf(EntryKind.OPENING, EntryKind.ADJUSTMENT)
    if (!signed && amount <= 0) return invalid(InvalidReason.AMOUNT)
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
                    val received = input.receivedMinor ?: return invalid(InvalidReason.MISSING_AMOUNT)
                    if (destination.id == account.id || received <= 0) return invalid(InvalidReason.TRANSFER)
                    if (destination.currency.code == account.currency.code && destination.currency != account.currency) {
                        return invalid(InvalidReason.CURRENCY)
                    }
                    if (destination.currency == account.currency && received != amount) return invalid(InvalidReason.TRANSFER)
                    listOf(Posting(account.id, Math.negateExact(amount)), Posting(destination.id, received))
                }

                EntryKind.EXPENSE -> {
                    listOf(Posting(account.id, Math.negateExact(amount)))
                }

                else -> {
                    listOf(Posting(account.id, amount))
                }
            }
        if (signed || input.kind == EntryKind.TRANSFER) {
            if (input.allocations.isNotEmpty()) return invalid(InvalidReason.ALLOCATIONS)
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
                if (!validId(allocation.id) || allocation.amountMinor <= 0) return invalid(InvalidReason.ALLOCATIONS)
                if (allocation.categoryId != null) {
                    val category = categories[allocation.categoryId] ?: return invalid(InvalidReason.CATEGORY)
                    if (category.archived || category.kind != expected) return invalid(InvalidReason.CATEGORY)
                }
                total = Math.addExact(total, allocation.amountMinor)
            }
            if (total != amount) return invalid(InvalidReason.ALLOCATIONS)
        }
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

data class Draft(
    val id: String,
    val entryId: String,
    val token: String,
    val input: EntryInput,
    val originalToken: String? = null,
    val incomplete: Boolean = false,
    /** Preserves intermediate editor input such as "12." or an invalid amount. */
    val amountText: String? = null,
    val dateText: String? = null,
    val timeText: String? = null,
    val receivedText: String? = null,
    val merchantAmountText: String? = null,
    val merchantCurrencyText: String? = null,
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
    /** expectedToken = null creates only. A save never overwrites another editor's draft. */
    suspend fun saveDraft(
        draft: Draft,
        expectedToken: String?,
    ): LedgerResult<Draft>

    suspend fun confirm(
        draftId: String,
        expectedToken: String,
    ): LedgerResult<SavedEntry>

    suspend fun beginEdit(entryId: String): LedgerResult<Draft>

    suspend fun discardDraft(
        draftId: String,
        expectedToken: String,
    ): LedgerResult<Unit>

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
