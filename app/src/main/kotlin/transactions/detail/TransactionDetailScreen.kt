package moe.frost.kochenite.transactions.detail

import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.ui.components.Form
import moe.frost.kochenite.ui.components.currency

@Composable internal fun EntryDetailForm(
    id: String,
    entry: SavedEntry?,
    state: BookState,
    model: BookViewModel,
    edit: (String) -> Unit,
    refundAllocation: (String) -> Unit,
    copy: () -> Unit,
    saveTemplate: () -> Unit,
) {
    var confirmPurge by rememberSaveable(id) { mutableStateOf(false) }
    if (entry?.id != id) {
        Form(R.string.transactions, state) { CircularProgressIndicator() }
        return
    }
    val account = state.accounts.find { it.account.id == entry.input.accountId }?.account
    Form(R.string.transactions, state) {
        Text(entry.input.merchant.ifBlank { entry.input.kind.name }, style = MaterialTheme.typography.headlineSmall)
        Text("${entry.input.date} ${entry.input.time}")
        Text(
            "${account?.name.orEmpty()} · ${account?.currency?.code.orEmpty()} ${account?.currency?.formatMinor(
                entry.input.amountMinor ?: 0,
            ).orEmpty()}",
        )
        entry.input.merchantAmount?.let {
            Text("${stringResource(R.string.merchant_amount)}: ${it.currency.code} ${it.currency.formatMinor(it.minor)}")
        }
        entry.input.relatedEntryIds.forEach { relatedId ->
            val related = state.relatedEntries.find { it.id == relatedId }
            Text(
                "${stringResource(
                    R.string.related_transactions,
                )} · ${related?.input?.merchant?.ifBlank { related.input.kind.name } ?: relatedId}",
            )
        }
        entry.input.allocations.forEach { allocation ->
            entry.input.receiptLines.find { it.id == allocation.id }?.let { line ->
                val lineCurrency = entry.input.merchantAmount?.currency ?: account?.currency
                Text("${line.description} · ${lineCurrency?.code.orEmpty()} ${lineCurrency?.formatMinor(line.merchantMinor).orEmpty()}")
            }
            Text(
                "${state.categories.find { it.id == allocation.categoryId }?.name ?: stringResource(
                    R.string.category,
                )} · ${account?.currency?.formatMinor(allocation.amountMinor).orEmpty()}",
            )
            if (entry.status == EntryStatus.POSTED && entry.input.kind == EntryKind.EXPENSE && allocation.amountMinor > 0 &&
                entry.input.amountMinor!! < 0
            ) {
                TextButton(onClick = { refundAllocation(allocation.id) }) { Text(stringResource(R.string.refund_allocation)) }
            }
        }
        if (entry.input.note.isNotBlank()) Text(entry.input.note)
        entry.input.tagIds.forEach { tagId -> state.tags.find { it.id == tagId }?.let { Text(it.name) } }
        if (entry.status == EntryStatus.POSTED && entry.input.kind !in setOf(EntryKind.OPENING, EntryKind.ADJUSTMENT)) {
            TextButton(onClick = copy) { Text(stringResource(R.string.copy_transaction)) }
            TextButton(onClick = saveTemplate) { Text(stringResource(R.string.save_as_template)) }
        }
        if (entry.status == EntryStatus.POSTED) {
            if (entry.input.kind !in setOf(EntryKind.OPENING, EntryKind.ADJUSTMENT)) {
                TextButton(
                    onClick = { edit(entry.id) },
                ) { Text(stringResource(R.string.edit_transaction)) }
            }
            TextButton(onClick = { model.trashEntry(entry) }) { Text(stringResource(R.string.move_to_trash)) }
        } else {
            TextButton(onClick = { model.restoreEntry(entry) }) { Text(stringResource(R.string.restore)) }
            if (!confirmPurge) {
                TextButton(onClick = { confirmPurge = true }) { Text(stringResource(R.string.delete_permanently)) }
            } else {
                Text(stringResource(R.string.purge_warning), color = MaterialTheme.colorScheme.error)
                Button(onClick = { model.purgeEntry(entry) }) { Text(stringResource(R.string.confirm_delete)) }
                TextButton(onClick = { confirmPurge = false }) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}
