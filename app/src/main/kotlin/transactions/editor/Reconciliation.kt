package moe.frost.kochenite.transactions.editor

import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.editor.mainText
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.EntryKind

/** How the items of an editor compare with its amount. Nothing here balances a mismatch. */
internal data class Reconciliation(
    val currency: BookCurrency?,
    val count: Int,
    /** Null while any item amount is missing or invalid. */
    val sum: Long?,
    /** Null while the transaction amount is missing or invalid. */
    val target: Long?,
    val invalidCategoryItems: Set<String>,
    /** A foreign receipt whose actual account charge is still unknown. */
    val allocationPending: Boolean,
) {
    val difference: Long? = sum?.let { items -> target?.let { amount -> runCatching { Math.subtractExact(items, amount) }.getOrNull() } }
    val pending: Boolean get() = sum == null || target == null
    val matched: Boolean get() = !pending && sum == target && invalidCategoryItems.isEmpty()
}

internal fun reconcile(
    editor: EditorInput,
    accounts: List<Account>,
    categories: List<Category>,
): Reconciliation {
    val currency = editor.transactionCurrency
    val values =
        editor.input.receiptLines.map { line ->
            runCatching { currency?.parseMinor(editor.lineTexts[line.id].orEmpty()) }.getOrNull()?.takeIf { it > 0 }
        }
    val sum =
        if (values.any {
                it == null
            }
        ) {
            null
        } else {
            runCatching { values.fold(0L) { total, value -> Math.addExact(total, value!!) } }.getOrNull()
        }
    val target =
        runCatching { currency?.parseMinor(editor.mainText(accounts))?.let(Math::absExact) }
            .getOrNull()
            ?.takeIf { it > 0 }
    val expected = if (editor.input.kind == EntryKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val allocations = editor.input.allocations.associateBy { it.id }
    val invalid =
        editor.input.receiptLines
            .filter { line ->
                val allocation = allocations[line.id] ?: return@filter true
                val categoryId = allocation.categoryId
                categoryId != null && categories.none { it.id == categoryId && !it.archived && it.kind == expected }
            }.map { it.id }
            .toSet()
    val account = accounts.find { it.id == editor.input.accountId }
    return Reconciliation(
        currency,
        editor.input.receiptLines.size,
        sum,
        target,
        invalid,
        allocationPending = editor.input.amountMinor == null && currency != account?.currency,
    )
}
