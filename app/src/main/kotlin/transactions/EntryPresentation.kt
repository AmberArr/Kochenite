package moe.frost.kochenite.transactions

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.ui.components.Tone

/** Descriptive account types offered by the account form. The type never changes how balances work. */
internal val AccountTypes = listOf("Bank", "Cash", "Savings", "Credit card", "E-wallet", "Asset", "Loan", "Receivable")

@DrawableRes
internal fun accountIcon(type: String): Int =
    when (type.trim().lowercase()) {
        "bank" -> R.drawable.ic_bank
        "cash" -> R.drawable.ic_cash
        "credit card" -> R.drawable.ic_credit_card
        "savings" -> R.drawable.ic_savings
        else -> R.drawable.ic_wallet
    }

internal fun accountTone(account: Account): Tone =
    when {
        account.archived || !account.includeInNetWorth -> Tone.NEUTRAL
        account.type.trim().lowercase() in setOf("credit card", "loan") -> Tone.TERTIARY
        else -> Tone.SECONDARY
    }

private val categoryIcons =
    listOf(
        listOf("coffee", "cafe", "café", "dining") to R.drawable.ic_cafe,
        listOf("restaurant", "food", "meal", "lunch", "dinner") to R.drawable.ic_restaurant,
        listOf("grocer", "supermarket", "shopping", "household") to R.drawable.ic_cart,
        listOf("transport", "train", "transit", "bus", "commute", "travel", "taxi") to R.drawable.ic_train,
        listOf("housing", "rent", "home", "utilit", "hotel") to R.drawable.ic_home,
        listOf("salary", "payroll", "wage", "work", "job") to R.drawable.ic_work,
        listOf("saving", "interest", "cashback", "income") to R.drawable.ic_savings,
    )

/** Categories store no icon, so one is chosen from the category's name, then its parent's. */
@DrawableRes
internal fun categoryIcon(
    category: Category?,
    categories: List<Category>,
): Int {
    val names = listOfNotNull(category?.name, categories.find { it.id == category?.parentId }?.name).map { it.lowercase() }
    for (name in names) {
        categoryIcons.firstOrNull { (keywords, _) -> keywords.any { it in name } }?.let { return it.second }
    }
    return R.drawable.ic_category
}

internal fun categoryPath(
    category: Category,
    categories: List<Category>,
): String = categories.find { it.id == category.parentId }?.let { "${it.name} › ${category.name}" } ?: category.name

@DrawableRes
internal fun entryIcon(
    input: EntryInput,
    categories: List<Category>,
): Int =
    when (input.kind) {
        EntryKind.TRANSFER -> {
            R.drawable.ic_swap
        }

        EntryKind.OPENING, EntryKind.ADJUSTMENT -> {
            R.drawable.ic_tune
        }

        else -> {
            if (input.allocations.size > 1) {
                R.drawable.ic_split
            } else {
                val category = categories.find { it.id == input.allocations.firstOrNull()?.categoryId }
                if (category == null && input.kind == EntryKind.INCOME) R.drawable.ic_savings else categoryIcon(category, categories)
            }
        }
    }

internal fun entryTone(kind: EntryKind): Tone =
    when (kind) {
        EntryKind.TRANSFER -> Tone.TERTIARY
        EntryKind.OPENING, EntryKind.ADJUSTMENT -> Tone.NEUTRAL
        EntryKind.INCOME -> Tone.INCOME
        EntryKind.EXPENSE -> Tone.SECONDARY
    }

@Composable
internal fun kindLabel(input: EntryInput): String =
    stringResource(
        when (input.kind) {
            EntryKind.EXPENSE -> if ((input.amountMinor ?: -1) > 0) R.string.expense_credit else R.string.expense
            EntryKind.INCOME -> R.string.income
            EntryKind.TRANSFER -> R.string.transfer
            EntryKind.OPENING, EntryKind.ADJUSTMENT -> R.string.adjustment
        },
    )

/** The category line of a row: one category, a count of several, or the kind for entries without categories. */
@Composable
internal fun categorySummary(
    input: EntryInput,
    categories: List<Category>,
): String =
    when (input.kind) {
        EntryKind.TRANSFER -> {
            stringResource(R.string.transfer)
        }

        EntryKind.OPENING, EntryKind.ADJUSTMENT -> {
            stringResource(R.string.adjustment)
        }

        else -> {
            val ids = input.allocations.map { it.categoryId }.distinct()
            if (input.allocations.size > 1 && ids.size > 1) {
                pluralStringResource(R.plurals.category_count, ids.size, ids.size)
            } else {
                categories.find { it.id == ids.firstOrNull() }?.name ?: stringResource(R.string.uncategorized)
            }
        }
    }

@Composable
internal fun entryTitle(
    input: EntryInput,
    accounts: List<Account>,
    categories: List<Category>,
): String {
    val account = accounts.find { it.id == input.accountId }
    return when (input.kind) {
        EntryKind.OPENING -> {
            stringResource(R.string.opening_balance_title)
        }

        EntryKind.ADJUSTMENT -> {
            stringResource(R.string.adjustment_title)
        }

        EntryKind.TRANSFER -> {
            input.merchant.ifBlank {
                "${account?.name.orEmpty()} → ${accounts.find { it.id == input.destinationAccountId }?.name.orEmpty()}"
            }
        }

        else -> {
            input.merchant.ifBlank { categorySummary(input, categories) }
        }
    }
}
