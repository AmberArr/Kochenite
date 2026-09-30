package moe.frost.kochenite.transactions.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.transactions.categoryPath
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.DropdownField
import moe.frost.kochenite.ui.components.Field
import moe.frost.kochenite.ui.components.mediumDate
import moe.frost.kochenite.ui.components.symbol
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Read-only date field that opens a date picker. A null date reads as "Any date". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(
    label: String,
    date: LocalDate?,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    onChange: (LocalDate?) -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val value = date?.let(::mediumDate) ?: stringResource(R.string.any_date)
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            isError = error,
            label = { Text(label) },
            trailingIcon = { AppIcon(R.drawable.ic_calendar, null) },
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        )
        // The overlay makes the whole field one button instead of a text field that cannot be typed into.
        Box(
            Modifier
                .matchParentSize()
                .testTag("date-$label")
                .clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = "$label: $value" },
        )
    }
    if (open) {
        val picker =
            rememberDatePickerState(
                initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        onChange(null)
                        open = false
                    }) { Text(stringResource(R.string.any_date)) }
                    TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
                }
            },
        ) {
            DatePicker(picker)
        }
    }
}

/** Edits a copy of the query; nothing is applied until Done, which stays disabled while a value is invalid. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun FilterSheet(
    state: BookState,
    applied: TransactionQuery,
    onDismiss: () -> Unit,
    onApply: (TransactionQuery) -> Unit,
) {
    var draft by remember { mutableStateOf(applied) }
    val accounts = state.accounts.map { it.account }
    val currencies = accounts.map { it.currency }.distinct()
    val currency = currencies.find { it.code == draft.amountCode }
    val any = stringResource(R.string.any)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.filter_transactions),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(stringResource(R.string.from_date), draft.from, Modifier.weight(1f), error = !draft.rangeValid) {
                    draft = draft.copy(from = it)
                }
                DateField(stringResource(R.string.through_date), draft.through, Modifier.weight(1f), error = !draft.rangeValid) {
                    draft = draft.copy(through = it)
                }
            }
            if (!draft.rangeValid) {
                Text(
                    stringResource(R.string.date_range_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            DropdownField(
                stringResource(R.string.transaction_account),
                accounts.find { it.id == draft.accountId }?.name ?: stringResource(R.string.any_account),
                listOf(null to stringResource(R.string.any_account)) + accounts.map { it.id to it.name },
                tag = "filter-account",
            ) { draft = draft.copy(accountId = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField(
                    stringResource(R.string.category),
                    state.categories.find { it.id == draft.categoryId }?.name ?: stringResource(R.string.any_category),
                    listOf(null to stringResource(R.string.any_category)) +
                        state.categories.sortedBy { categoryPath(it, state.categories).lowercase() }.map {
                            it.id to categoryPath(it, state.categories)
                        },
                    Modifier.weight(1f),
                    tag = "filter-category",
                ) { draft = draft.copy(categoryId = it) }
                DropdownField(
                    stringResource(R.string.tag),
                    state.tags.find { it.id == draft.tagId }?.name ?: stringResource(R.string.any_tag),
                    listOf(null to stringResource(R.string.any_tag)) + state.tags.map { it.id to it.name },
                    Modifier.weight(1f),
                    tag = "filter-tag",
                ) { draft = draft.copy(tagId = it) }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.kind),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Opening balances are adjustments, so there is no separate Opening kind to filter by.
                    listOf(
                        null to any,
                        EntryKind.EXPENSE to stringResource(R.string.expense),
                        EntryKind.INCOME to stringResource(R.string.income),
                        EntryKind.TRANSFER to stringResource(R.string.transfer),
                        EntryKind.ADJUSTMENT to stringResource(R.string.adjustment),
                    ).forEach { (kind, label) ->
                        FilterChip(
                            selected = draft.kind == kind,
                            onClick = { draft = draft.copy(kind = kind) },
                            label = { Text(label) },
                            leadingIcon =
                                if (draft.kind == kind) {
                                    { AppIcon(R.drawable.ic_check, null, Modifier.size(18.dp)) }
                                } else {
                                    null
                                },
                            modifier = Modifier.testTag("filter-kind-${kind?.name ?: "any"}"),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DropdownField(
                    stringResource(R.string.currency_name),
                    currency?.let { "${it.symbol().trim()} ${it.code}" } ?: any,
                    listOf(null to any) + currencies.map { it.code to "${it.symbol().trim()} ${it.code}" },
                    Modifier.weight(1f),
                    tag = "filter-currency",
                ) { draft = draft.copy(amountCode = it, minimumText = "", maximumText = "") }
                Field(
                    R.string.minimum_amount,
                    draft.minimumText,
                    { draft = draft.copy(minimumText = it) },
                    Modifier.weight(1f),
                    enabled = currency != null,
                    number = true,
                    error = !draft.minimumValid(currency),
                    prefix = currency?.symbol(),
                )
                Field(
                    R.string.maximum_amount,
                    draft.maximumText,
                    { draft = draft.copy(maximumText = it) },
                    Modifier.weight(1f),
                    enabled = currency != null,
                    number = true,
                    error = !draft.maximumValid(currency),
                    supporting = if (draft.maximumValid(currency)) null else stringResource(R.string.maximum_error),
                    prefix = currency?.symbol(),
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { draft = draft.cleared().copy(text = draft.text) }) {
                    Text(stringResource(R.string.reset))
                }
                Button(
                    onClick = { onApply(draft) },
                    enabled = draft.filter(currencies) != null,
                    modifier = Modifier.testTag("filter-done"),
                ) { Text(stringResource(R.string.done)) }
            }
        }
    }
}
