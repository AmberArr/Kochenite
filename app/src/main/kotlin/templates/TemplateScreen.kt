package moe.frost.kochenite.templates

import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryTemplate
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.ui.components.Field
import moe.frost.kochenite.ui.components.Form
import moe.frost.kochenite.ui.components.Toggle
import java.util.UUID

@Composable internal fun TemplateSaveForm(
    entryId: String,
    entry: SavedEntry?,
    state: BookState,
    model: BookViewModel,
) {
    var name by rememberSaveable(entryId) { mutableStateOf("") }
    var includeAmounts by rememberSaveable(entryId) { mutableStateOf(false) }
    var includeSplits by rememberSaveable(entryId) { mutableStateOf(false) }
    val id = rememberSaveable(entryId) { UUID.randomUUID().toString() }
    Form(R.string.save_as_template, state) {
        if (entry?.id != entryId) {
            CircularProgressIndicator()
            return@Form
        }
        Field(R.string.template_name, name, { name = it })
        Toggle(R.string.include_template_amounts, includeAmounts) { includeAmounts = it }
        Toggle(R.string.include_template_splits, includeSplits) { includeSplits = it }
        Button(onClick = {
            val source = entry.input
            val sourceAmount = source.amountMinor
            val allocations =
                if (source.kind == EntryKind.TRANSFER) {
                    emptyList()
                } else if (includeSplits) {
                    source.allocations.map { if (includeAmounts) it else it.copy(amountMinor = 0) }
                } else if (includeAmounts && sourceAmount != null) {
                    listOf(
                        Allocation(
                            UUID.randomUUID().toString(),
                            runCatching {
                                Math.absExact(sourceAmount)
                            }.getOrDefault(0L),
                            source.allocations.firstOrNull()?.categoryId,
                        ),
                    )
                } else {
                    emptyList()
                }
            val input =
                source.copy(
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
            model.saveTemplate(EntryTemplate(id, name.trim(), input))
        }, enabled = !state.saving && name.isNotBlank()) { Text(stringResource(R.string.save)) }
    }
}

@Composable internal fun TemplatesPanel(
    state: BookState,
    model: BookViewModel,
    apply: (String) -> Unit,
) {
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    Form(R.string.templates, state) {
        if (state.templates.isEmpty()) Text(stringResource(R.string.no_templates))
        state.templates.forEach { template ->
            Text(template.name, style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { apply(template.id) }, modifier = Modifier.heightIn(min = 48.dp).testTag("template-use-${template.id}")) {
                Text(stringResource(R.string.use_template))
            }
            if (confirmDelete == template.id) {
                Button(onClick = {
                    model.deleteTemplate(template.id)
                    confirmDelete = null
                }) {
                    Text(stringResource(R.string.confirm_delete))
                }
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) }
            } else {
                TextButton(onClick = { confirmDelete = template.id }) { Text(stringResource(R.string.delete_template)) }
            }
        }
    }
}
