package moe.frost.kochenite.transactions.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.failure
import moe.frost.kochenite.transactions.categoryPath
import moe.frost.kochenite.transactions.entryTitle
import moe.frost.kochenite.ui.components.AppScaffold
import moe.frost.kochenite.ui.components.AppTopBar
import moe.frost.kochenite.ui.components.DropdownField
import moe.frost.kochenite.ui.components.EmptyState
import moe.frost.kochenite.ui.components.IconAction
import moe.frost.kochenite.ui.components.Notice
import moe.frost.kochenite.ui.components.NoticeTone
import moe.frost.kochenite.ui.components.SectionHeader
import moe.frost.kochenite.ui.components.SignDisplay
import moe.frost.kochenite.ui.components.display
import moe.frost.kochenite.ui.components.tabular

private fun SavedEntry.categoryKind() = if (input.kind == EntryKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE

/**
 * Contextual selection mode over the loaded posted rows. Each allocation row is selectable; applying
 * changes only categories, atomically, and a mixed or stale selection is an error rather than a partial write.
 */
@Composable
internal fun BulkCategorizeScreen(
    state: BookState,
    model: BookViewModel,
    list: TransactionListState,
    exit: () -> Unit,
) {
    var targetCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val entries =
        list.entries.filter { it.status == EntryStatus.POSTED && (it.input.kind == EntryKind.EXPENSE || it.input.kind == EntryKind.INCOME) }
    val rows = entries.flatMap { entry -> entry.input.allocations.map { entry to it } }
    val selection = list.selection

    fun selected(
        entry: SavedEntry,
        allocation: Allocation,
    ) = selection.any { it.entryId == entry.id && it.allocationId == allocation.id }
    val selectedRows = rows.filter { (entry, allocation) -> selected(entry, allocation) }
    val kinds = selectedRows.map { it.first.categoryKind() }.distinct()
    val accounts = state.accounts.map { it.account }
    val choices = state.categories.filter { !it.archived && (kinds.isEmpty() || it.kind in kinds) }
    val uncategorized =
        stringResource(
            if (kinds.singleOrNull() ==
                CategoryKind.INCOME
            ) {
                R.string.uncategorized_income
            } else {
                R.string.uncategorized_expense
            },
        )
    AppScaffold(
        topBar = {
            AppTopBar(
                pluralStringResource(R.plurals.rows_selected, selectedRows.size, selectedRows.size),
                onBack = exit,
                close = true,
            ) {
                val all = rows.isNotEmpty() && selectedRows.size == rows.size
                IconAction(
                    R.drawable.ic_checklist,
                    stringResource(if (all) R.string.clear_selection else R.string.select_all),
                    Modifier.testTag("bulk-select-all"),
                    enabled = !list.loading,
                ) {
                    if (all) {
                        model.transactionList.clearSelection()
                    } else {
                        rows.filterNot { (entry, allocation) -> selected(entry, allocation) }.forEach { (entry, allocation) ->
                            model.transactionList.toggle(entry, allocation.id)
                        }
                    }
                }
            }
        },
        bottomBar =
            {
                DropdownField(
                    stringResource(R.string.category),
                    state.categories.find { it.id == targetCategory }?.let { categoryPath(it, state.categories) } ?: uncategorized,
                    listOf(null to uncategorized) +
                        choices.sortedBy { categoryPath(it, state.categories).lowercase() }.map {
                            it.id to categoryPath(it, state.categories)
                        },
                    Modifier.weight(1f),
                    tag = "bulk-category",
                ) { targetCategory = it }
                Button(
                    onClick = {
                        model.bulkCategorize(
                            selectedRows.map { (entry, allocation) ->
                                selection.single { it.entryId == entry.id && it.allocationId == allocation.id }
                            },
                            targetCategory,
                        )
                    },
                    enabled = selectedRows.isNotEmpty() && kinds.size == 1 && !state.saving && !list.loading,
                    modifier = Modifier.testTag("bulk-apply"),
                ) {
                    Text(stringResource(R.string.apply_to_count, selectedRows.size))
                }
            },
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Notice(stringResource(R.string.bulk_hint))
            state.error?.let { Notice(stringResource(it), tone = NoticeTone.ERROR) }
            list.error?.let { Notice(stringResource(failure(it)), tone = NoticeTone.ERROR) }
            if (kinds.size > 1) Notice(stringResource(R.string.select_one_category_kind), tone = NoticeTone.ERROR)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (rows.isEmpty()) {
                item(key = "empty") { EmptyState(R.drawable.ic_checklist, stringResource(R.string.no_posted)) }
            }
            entries.groupBy { it.input.date }.forEach { (date, group) ->
                item(key = "date-$date") { SectionHeader(dateHeading(date)) }
                group.forEach { entry ->
                    val account = accounts.find { it.id == entry.input.accountId }
                    items(entry.input.allocations, key = { "${entry.id}-${it.id}" }) { allocation ->
                        val index = entry.input.allocations.indexOf(allocation)
                        val checked = selected(entry, allocation)
                        val title = entryTitle(entry.input, accounts, state.categories)
                        val category =
                            state.categories.find { it.id == allocation.categoryId }?.name
                                ?: stringResource(
                                    if (entry.input.kind ==
                                        EntryKind.INCOME
                                    ) {
                                        R.string.uncategorized_income
                                    } else {
                                        R.string.uncategorized_expense
                                    },
                                )
                        // Allocations are magnitudes; the entry's sign says whether they spend, earn or reverse.
                        val signed = if ((entry.input.amountMinor ?: 0L) < 0) -allocation.amountMinor else allocation.amountMinor
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 64.dp)
                                .testTag("bulk-row-${entry.id}-${allocation.id}")
                                .toggleable(checked, role = Role.Checkbox) { model.transactionList.toggle(entry, allocation.id) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked, onCheckedChange = null, modifier = Modifier.padding(horizontal = 8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (entry.input.allocations.size >
                                        1
                                    ) {
                                        stringResource(R.string.allocation_row_title, title, index + 1)
                                    } else {
                                        title
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "$category · ${account?.name.orEmpty()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                account?.currency?.display(signed, SignDisplay.ALWAYS).orEmpty(),
                                style = MaterialTheme.typography.titleMedium.tabular,
                            )
                        }
                    }
                }
            }
        }
    }
}
