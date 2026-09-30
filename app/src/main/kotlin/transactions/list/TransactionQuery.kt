package moe.frost.kochenite.transactions.list

import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.LedgerFilter
import java.time.LocalDate

internal enum class LedgerTab { POSTED, INCOMPLETE, TRASH }

/** What the Transactions destination shows. Other destinations change it to drill down. */
internal data class TransactionQuery(
    val tab: LedgerTab = LedgerTab.POSTED,
    val text: String = "",
    val from: LocalDate? = null,
    val through: LocalDate? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val tagId: String? = null,
    val kind: EntryKind? = null,
    val amountCode: String? = null,
    val minimumText: String = "",
    val maximumText: String = "",
    val bulk: Boolean = false,
) {
    val hasFilters
        get() =
            from != null || through != null || accountId != null || categoryId != null || tagId != null || kind != null ||
                amountCode != null || minimumText.isNotBlank() || maximumText.isNotBlank()

    val active get() = hasFilters || text.isNotBlank()

    fun cleared() = TransactionQuery(tab = tab, bulk = bulk)

    val rangeValid get() = from == null || through == null || from <= through

    fun minimum(currency: BookCurrency?): Long? = amount(minimumText, currency)

    fun maximum(currency: BookCurrency?): Long? = amount(maximumText, currency)

    private fun amount(
        text: String,
        currency: BookCurrency?,
    ): Long? = if (text.isBlank()) null else runCatching { currency?.parseMinor(text) }.getOrNull()?.takeIf { it >= 0 }

    fun minimumValid(currency: BookCurrency?) = minimumText.isBlank() || minimum(currency) != null

    fun maximumValid(currency: BookCurrency?): Boolean {
        if (maximumText.isBlank()) return true
        val maximum = maximum(currency) ?: return false
        return maximum >= (minimum(currency) ?: 0L)
    }

    /** The ledger filter for the posted or trash tab, or null while a filter value is invalid. */
    fun filter(currencies: List<BookCurrency>): LedgerFilter? {
        val currency = currencies.find { it.code == amountCode }
        if (!rangeValid || !minimumValid(currency) || !maximumValid(currency)) return null
        return LedgerFilter(
            status = if (tab == LedgerTab.TRASH) EntryStatus.TRASH else EntryStatus.POSTED,
            text = text,
            from = from,
            through = through,
            accountId = accountId,
            categoryId = categoryId,
            tagId = tagId,
            kind = kind,
            amountCurrency = currency,
            minimumMinor = minimum(currency),
            maximumMinor = maximum(currency),
        )
    }
}
