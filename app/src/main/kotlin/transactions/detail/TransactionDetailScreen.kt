package moe.frost.kochenite.transactions.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.LocalFinanceColors
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.transactions.accountIcon
import moe.frost.kochenite.transactions.categoryPath
import moe.frost.kochenite.transactions.entryIcon
import moe.frost.kochenite.transactions.entryTitle
import moe.frost.kochenite.transactions.entryTone
import moe.frost.kochenite.transactions.kindLabel
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.AppScaffold
import moe.frost.kochenite.ui.components.AppTopBar
import moe.frost.kochenite.ui.components.Avatar
import moe.frost.kochenite.ui.components.ContentMaxWidth
import moe.frost.kochenite.ui.components.GroupCard
import moe.frost.kochenite.ui.components.IconAction
import moe.frost.kochenite.ui.components.ListRow
import moe.frost.kochenite.ui.components.Notice
import moe.frost.kochenite.ui.components.NoticeTone
import moe.frost.kochenite.ui.components.SignDisplay
import moe.frost.kochenite.ui.components.StatusBadge
import moe.frost.kochenite.ui.components.clockTime
import moe.frost.kochenite.ui.components.display
import moe.frost.kochenite.ui.components.fullDate
import moe.frost.kochenite.ui.components.shortDate
import moe.frost.kochenite.ui.components.tabular

/** Read-only posted or trashed record. Editing, copying and templates open an in-memory editor elsewhere. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EntryDetailScreen(
    id: String,
    entry: SavedEntry?,
    state: BookState,
    model: BookViewModel,
    onBack: (() -> Unit)?,
    edit: (String) -> Unit,
    refundAllocation: (String) -> Unit,
    copy: () -> Unit,
    saveTemplate: () -> Unit,
    openRelated: (String) -> Unit,
) {
    var confirmPurge by rememberSaveable(id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val loaded = entry?.takeIf { it.id == id }
    val posted = loaded?.status == EntryStatus.POSTED
    // Balance adjustments, including opening balances, are corrected by another adjustment rather than edited.
    val editable = posted && loaded.input.kind !in setOf(EntryKind.OPENING, EntryKind.ADJUSTMENT)
    AppScaffold(
        topBar = {
            AppTopBar("", onBack = onBack, backEnabled = !state.saving) {
                if (loaded != null && editable) {
                    IconAction(R.drawable.ic_edit, stringResource(R.string.edit_transaction), Modifier.testTag("edit-transaction")) {
                        edit(loaded.id)
                    }
                }
                if (loaded != null && posted) {
                    Box {
                        IconAction(R.drawable.ic_more_vert, stringResource(R.string.more), Modifier.testTag("detail-menu")) { menu = true }
                        DropdownMenu(menu, { menu = false }) {
                            if (editable) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy_transaction)) },
                                    leadingIcon = { AppIcon(R.drawable.ic_copy, null) },
                                    onClick = {
                                        menu = false
                                        copy()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.save_as_template)) },
                                    leadingIcon = { AppIcon(R.drawable.ic_bookmark, null) },
                                    onClick = {
                                        menu = false
                                        saveTemplate()
                                    },
                                )
                                HorizontalDivider()
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.move_to_trash), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { AppIcon(R.drawable.ic_delete, null, tint = MaterialTheme.colorScheme.error) },
                                modifier = Modifier.testTag("move-to-trash"),
                                onClick = {
                                    menu = false
                                    model.trashEntry(loaded)
                                },
                            )
                        }
                    }
                }
            }
        },
        maxWidth = if (onBack == null) ContentMaxWidth * 2 else ContentMaxWidth,
        bottomBar =
            if (loaded != null && !posted) {
                {
                    TextButton(
                        onClick = { confirmPurge = true },
                        enabled = !state.saving,
                        modifier = Modifier.testTag("delete-permanently"),
                    ) {
                        Text(stringResource(R.string.delete_permanently), color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = { model.restoreEntry(loaded) },
                        enabled = !state.saving,
                        modifier = Modifier.testTag("restore-entry"),
                    ) {
                        AppIcon(R.drawable.ic_restore, null, Modifier.padding(end = 8.dp).size(18.dp))
                        Text(stringResource(R.string.restore))
                    }
                }
            } else {
                null
            },
    ) {
        if (loaded == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                state.error?.let { Notice(stringResource(it), Modifier.padding(16.dp), tone = NoticeTone.ERROR) }
                    ?: CircularProgressIndicator()
            }
            return@AppScaffold
        }
        val input = loaded.input
        val accounts = state.accounts.map { it.account }
        val account = accounts.find { it.id == input.accountId }
        val destination = accounts.find { it.id == input.destinationAccountId }
        val currency = account?.currency
        val amount = input.amountMinor ?: 0L
        val scheme = MaterialTheme.colorScheme
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Avatar(entryIcon(input, state.categories), entryTone(input.kind), size = 56.dp)
                Text(
                    entryTitle(input, accounts, state.categories),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
                Text(
                    currency
                        ?.display(
                            if (input.kind == EntryKind.TRANSFER) runCatching { Math.negateExact(amount) }.getOrDefault(0L) else amount,
                            SignDisplay.ALWAYS,
                        ).orEmpty(),
                    style = MaterialTheme.typography.displaySmall.tabular,
                    color = if (input.kind == EntryKind.INCOME) LocalFinanceColors.current.income else scheme.onSurface,
                )
                Text(
                    "${kindLabel(input)} · ${fullDate(input.date)} · ${clockTime(input.time)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            state.error?.let { Notice(stringResource(it), tone = NoticeTone.ERROR) }
            if (!posted) Notice(stringResource(R.string.in_trash_notice), tone = NoticeTone.CAUTION)
            val creditable = posted && input.kind == EntryKind.EXPENSE && amount < 0
            if (input.allocations.size > 1 || input.receiptLines.isNotEmpty()) {
                GroupCard {
                    val lineCurrency = input.merchantAmount?.currency ?: currency
                    val lines = input.receiptLines.isNotEmpty()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(
                                if (lines) R.string.receipt_lines_count else R.string.category_splits_count,
                                input.allocations.size,
                            ),
                            Modifier.weight(1f).semantics { heading() },
                            style = MaterialTheme.typography.titleSmall,
                        )
                        StatusBadge(stringResource(R.string.totals_match), ok = true)
                    }
                    input.allocations.forEach { allocation ->
                        val line = input.receiptLines.find { it.id == allocation.id }
                        val category =
                            state.categories.find { it.id == allocation.categoryId }?.let { categoryPath(it, state.categories) }
                                ?: stringResource(R.string.uncategorized)
                        val signed = if (amount < 0) -allocation.amountMinor else allocation.amountMinor
                        ListRow(
                            line?.description?.ifBlank { null } ?: category,
                            supporting = if (line?.description.isNullOrBlank()) null else category,
                            trailing = {
                                Text(
                                    currency?.display(signed, SignDisplay.ALWAYS).orEmpty(),
                                    style = MaterialTheme.typography.titleMedium.tabular,
                                )
                                if (creditable && allocation.amountMinor > 0) {
                                    IconAction(
                                        R.drawable.ic_undo,
                                        stringResource(R.string.refund_allocation),
                                        Modifier.testTag("refund-${allocation.id}"),
                                    ) { refundAllocation(allocation.id) }
                                }
                            },
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            stringResource(
                                when {
                                    lines && input.merchantAmount != null -> R.string.receipt_total
                                    lines -> R.string.lines_total_charge
                                    else -> R.string.allocated_total
                                },
                            ),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurfaceVariant,
                        )
                        val total =
                            runCatching {
                                input.allocations.fold(
                                    0L,
                                ) { sum, row -> Math.addExact(sum, row.amountMinor) }
                            }.getOrNull()
                        val lineTotal =
                            runCatching {
                                input.receiptLines.fold(
                                    0L,
                                ) { sum, row -> Math.addExact(sum, row.merchantMinor) }
                            }.getOrNull()
                        Text(
                            if (lines && input.merchantAmount != null) {
                                "${lineTotal?.let {
                                    lineCurrency?.display(
                                        it,
                                    )
                                }.orEmpty()} / ${lineCurrency?.display(input.merchantAmount!!.minor).orEmpty()}"
                            } else {
                                "${total?.let {
                                    currency?.display(
                                        it,
                                    )
                                }.orEmpty()} / ${currency?.display(amount, SignDisplay.NONE).orEmpty()}"
                            },
                            style = MaterialTheme.typography.bodyMedium.tabular,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            GroupCard {
                ListRow(
                    account?.name.orEmpty(),
                    supporting =
                        stringResource(
                            if (input.kind ==
                                EntryKind.TRANSFER
                            ) {
                                R.string.from_account
                            } else {
                                R.string.transaction_account
                            },
                        ),
                    leading = leadingIcon(accountIcon(account?.type.orEmpty())),
                )
                if (input.kind == EntryKind.TRANSFER) {
                    ListRow(
                        destination?.name.orEmpty(),
                        supporting = stringResource(R.string.destination_account),
                        leading = leadingIcon(accountIcon(destination?.type.orEmpty())),
                        trailing = {
                            Text(
                                destination?.currency?.display(input.receivedMinor ?: 0L, SignDisplay.ALWAYS).orEmpty(),
                                style = MaterialTheme.typography.titleMedium.tabular,
                            )
                        },
                    )
                }
                if (input.allocations.size == 1 && input.receiptLines.isEmpty()) {
                    val allocation = input.allocations.single()
                    val category = state.categories.find { it.id == allocation.categoryId }
                    ListRow(
                        category?.let { categoryPath(it, state.categories) } ?: stringResource(R.string.uncategorized),
                        supporting = stringResource(R.string.category),
                        leading = leadingIcon(R.drawable.ic_category),
                        trailing = {
                            if (creditable && allocation.amountMinor > 0) {
                                IconAction(
                                    R.drawable.ic_undo,
                                    stringResource(R.string.refund_allocation),
                                    Modifier.testTag("refund-${allocation.id}"),
                                ) { refundAllocation(allocation.id) }
                            }
                        },
                    )
                }
                input.merchantAmount?.takeIf { it.currency != currency }?.let { merchant ->
                    ListRow(
                        merchant.currency.display(merchant.minor),
                        supporting = stringResource(R.string.merchant_amount),
                        leading = leadingIcon(R.drawable.ic_currency),
                    )
                }
                ListRow(
                    "${fullDate(input.date)} · ${clockTime(input.time)}",
                    supporting = stringResource(R.string.date_time_local),
                    leading = leadingIcon(R.drawable.ic_schedule),
                )
                if (input.note.isNotBlank()) {
                    ListRow(input.note, supporting = stringResource(R.string.notes), leading = leadingIcon(R.drawable.ic_notes))
                }
                val tags = input.tagIds.mapNotNull { tagId -> state.tags.find { it.id == tagId } }
                if (tags.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AppIcon(R.drawable.ic_label, stringResource(R.string.tags), tint = scheme.onSurfaceVariant)
                        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            tags.forEach { tag ->
                                AssistChip(
                                    onClick = {},
                                    enabled = false,
                                    label = { Text(tag.name, color = scheme.onSurface) },
                                    leadingIcon = { AppIcon(R.drawable.ic_label, null, Modifier.size(18.dp), tint = scheme.primary) },
                                )
                            }
                        }
                    }
                }
                input.relatedEntryIds.forEach { relatedId ->
                    val related = state.relatedEntries.find { it.id == relatedId }
                    val relatedAccount = accounts.find { it.id == related?.input?.accountId }
                    val title =
                        related?.let {
                            val name = entryTitle(it.input, accounts, state.categories)
                            val value = relatedAccount?.currency?.display(it.input.amountMinor ?: 0L, SignDisplay.ALWAYS)
                            listOfNotNull(name, value).joinToString(" · ")
                        } ?: stringResource(R.string.related_transaction)
                    ListRow(
                        title,
                        supporting =
                            related?.let { stringResource(R.string.related_on, shortDate(it.input.date)) }
                                ?: stringResource(R.string.related_transaction),
                        leading = leadingIcon(R.drawable.ic_link),
                        trailing = { if (related != null) AppIcon(R.drawable.ic_chevron_right, null, tint = scheme.onSurfaceVariant) },
                        onClick = related?.let { { openRelated(it.id) } },
                    )
                }
            }
        }
    }
    if (confirmPurge && loaded != null) {
        AlertDialog(
            onDismissRequest = { confirmPurge = false },
            title = { Text(stringResource(R.string.delete_permanently_question)) },
            text = { Text(stringResource(R.string.purge_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmPurge = false
                    model.purgeEntry(loaded)
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmPurge = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private fun leadingIcon(icon: Int): @Composable () -> Unit = { AppIcon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
