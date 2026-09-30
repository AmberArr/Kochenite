package moe.frost.kochenite.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryTemplate
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.transactions.categoryPath
import moe.frost.kochenite.transactions.entryIcon
import moe.frost.kochenite.transactions.entryTone
import moe.frost.kochenite.transactions.kindLabel
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.AppScaffold
import moe.frost.kochenite.ui.components.AppTopBar
import moe.frost.kochenite.ui.components.Avatar
import moe.frost.kochenite.ui.components.ContentMaxWidth
import moe.frost.kochenite.ui.components.EmptyState
import moe.frost.kochenite.ui.components.Field
import moe.frost.kochenite.ui.components.GroupCard
import moe.frost.kochenite.ui.components.IconAction
import moe.frost.kochenite.ui.components.Notice
import moe.frost.kochenite.ui.components.NoticeTone
import moe.frost.kochenite.ui.components.SwitchRow
import moe.frost.kochenite.ui.components.cardColor
import moe.frost.kochenite.ui.components.display
import java.util.UUID

/** What a template keeps from [source]. Dates, related transactions and attachments are never copied. */
internal fun templateInput(
    source: EntryInput,
    includeAmounts: Boolean,
    includeSplits: Boolean,
): EntryInput {
    val sourceAmount = source.amountMinor
    val allocations =
        when {
            source.kind == EntryKind.TRANSFER -> {
                emptyList()
            }

            includeSplits -> {
                source.allocations.map { if (includeAmounts) it else it.copy(amountMinor = 0) }
            }

            includeAmounts && sourceAmount != null -> {
                listOf(
                    Allocation(
                        UUID.randomUUID().toString(),
                        runCatching { Math.absExact(sourceAmount) }.getOrDefault(0L),
                        source.allocations.firstOrNull()?.categoryId,
                    ),
                )
            }

            else -> {
                emptyList()
            }
        }
    return source.copy(
        amountMinor = if (includeAmounts) source.amountMinor else null,
        receivedMinor = if (includeAmounts) source.receivedMinor else null,
        merchantAmount = if (includeAmounts) source.merchantAmount else null,
        allocations = allocations,
        receiptLines =
            if (includeSplits) {
                source.receiptLines.map { if (includeAmounts) it else it.copy(merchantMinor = 0) }
            } else {
                emptyList()
            },
        relatedEntryIds = emptyList(),
    )
}

/** "Expense · Aeon Mall · Everyday bank", or the two accounts of a transfer. */
@Composable
private fun templateSummary(
    input: EntryInput,
    state: BookState,
): String {
    val accounts = state.accounts.map { it.account }
    val account = accounts.find { it.id == input.accountId }?.name
    val category = state.categories.find { it.id == input.allocations.firstOrNull()?.categoryId }
    return if (input.kind == EntryKind.TRANSFER) {
        val destination = accounts.find { it.id == input.destinationAccountId }?.name
        "${kindLabel(input)} · ${account.orEmpty()} → ${destination.orEmpty()}"
    } else {
        listOfNotNull(
            kindLabel(input),
            input.merchant.ifBlank { null } ?: category?.let { categoryPath(it, state.categories) },
            account,
        ).joinToString(" · ")
    }
}

/** "3 lines · amounts blank" or "Amount ¥85,000". */
@Composable
private fun templateAmounts(
    input: EntryInput,
    state: BookState,
): String {
    val currency =
        state.accounts
            .find { it.account.id == input.accountId }
            ?.account
            ?.currency
    val amount = input.amountMinor
    val amounts =
        if (amount != null && currency != null) {
            stringResource(
                R.string.template_amount,
                currency.display(
                    if (input.kind ==
                        EntryKind.TRANSFER
                    ) {
                        amount
                    } else {
                        runCatching { Math.absExact(amount) }.getOrDefault(0L)
                    },
                ),
            )
        } else {
            stringResource(R.string.amounts_blank)
        }
    val lines = input.allocations.size
    return if (lines > 1) "${pluralStringResource(R.plurals.line_count, lines, lines)} · $amounts" else amounts
}

/** Full-screen form; the preview makes included and excluded fields explicit. */
@Composable
internal fun TemplateSaveScreen(
    entryId: String,
    entry: SavedEntry?,
    state: BookState,
    model: BookViewModel,
    onBack: () -> Unit,
) {
    var name by rememberSaveable(entryId) { mutableStateOf("") }
    var includeAmounts by rememberSaveable(entryId) { mutableStateOf(false) }
    var includeSplits by rememberSaveable(entryId) { mutableStateOf(false) }
    val id = rememberSaveable(entryId) { UUID.randomUUID().toString() }
    val source = entry?.takeIf { it.id == entryId }?.input
    AppScaffold(
        topBar = {
            AppTopBar(stringResource(R.string.save_as_template), onBack = onBack, close = true, backEnabled = !state.saving) {
                TextButton(
                    onClick = {
                        source?.let {
                            model.saveTemplate(
                                EntryTemplate(id, name.trim(), templateInput(it, includeAmounts, includeSplits)),
                            )
                        }
                    },
                    enabled = !state.saving && name.isNotBlank() && source != null,
                    modifier = Modifier.testTag("save-template"),
                ) { Text(stringResource(R.string.save)) }
            }
        },
        maxWidth = ContentMaxWidth,
    ) {
        if (source == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@AppScaffold
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let { Notice(stringResource(it), tone = NoticeTone.ERROR) }
            Field(
                R.string.template_name,
                name,
                { name = it },
                enabled = !state.saving,
                supporting = stringResource(R.string.template_name_hint),
            )
            GroupCard {
                SwitchRow(
                    stringResource(R.string.include_template_amounts),
                    includeAmounts,
                    supporting = stringResource(R.string.include_amounts_hint),
                ) {
                    includeAmounts = it
                }
                if (source.kind != EntryKind.TRANSFER) {
                    SwitchRow(
                        stringResource(R.string.include_template_splits),
                        includeSplits,
                        supporting = stringResource(R.string.include_splits_hint),
                    ) {
                        includeSplits = it
                    }
                }
            }
            val preview = templateInput(source, includeAmounts, includeSplits)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(
                        MaterialTheme.shapes.large,
                    ).background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.template_prefill),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.semantics { heading() },
                )
                listOf(
                    templateSummary(preview, state),
                    templateAmounts(preview, state),
                    stringResource(R.string.template_not_copied),
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Notice(stringResource(R.string.templates_never_post))
        }
    }
}

/** A card per template with one clear Use action; deleting asks first. */
@Composable
internal fun TemplatesScreen(
    state: BookState,
    model: BookViewModel,
    onBack: () -> Unit,
    apply: (String) -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    AppScaffold(topBar = {
        AppTopBar(stringResource(R.string.templates), onBack = onBack, backEnabled = !state.saving)
    }, maxWidth = ContentMaxWidth) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { Notice(stringResource(it), tone = NoticeTone.ERROR) }
            if (state.templates.isEmpty()) {
                EmptyState(R.drawable.ic_bookmark, stringResource(R.string.no_templates), body = stringResource(R.string.no_templates_body))
            } else {
                Text(
                    stringResource(R.string.templates_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.templates.forEach { template ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(cardColor())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(entryIcon(template.input, state.categories), entryTone(template.input.kind))
                        Column(Modifier.weight(1f)) {
                            Text(template.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            Text(
                                templateSummary(template.input, state),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconAction(
                            R.drawable.ic_delete,
                            stringResource(R.string.delete_template_named, template.name),
                            Modifier.testTag("template-delete-${template.id}"),
                            enabled = !state.saving,
                        ) { confirmDelete = template.id }
                    }
                    Text(
                        templateAmounts(template.input, state),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { apply(template.id) },
                        enabled = !state.saving,
                        modifier = Modifier.testTag("template-use-${template.id}"),
                    ) {
                        AppIcon(R.drawable.ic_add, null, Modifier.padding(end = 8.dp).size(18.dp))
                        Text(stringResource(R.string.use_template))
                    }
                }
            }
        }
    }
    state.templates.find { it.id == confirmDelete }?.let { template ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete_template_question)) },
            text = { Text(stringResource(R.string.delete_template_body, template.name)) },
            confirmButton = {
                TextButton(onClick = {
                    model.deleteTemplate(template.id)
                    confirmDelete = null
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
