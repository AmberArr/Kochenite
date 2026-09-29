package moe.frost.kochenite.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.BookCurrency
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Currency

internal fun currency(code: String): BookCurrency? =
    try {
        Currency.getInstance(code.trim().uppercase()).let { BookCurrency(it.currencyCode, it.defaultFractionDigits) }
    } catch (_: IllegalArgumentException) {
        null
    }

@Composable internal fun Field(
    label: Int,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
    number: Boolean = false,
    focusRequester: FocusRequester? = null,
    multiline: Boolean = false,
) {
    val labelText = stringResource(label)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(labelText) },
        enabled = enabled,
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .then(
                    if (focusRequester !=
                        null
                    ) {
                        Modifier.focusRequester(focusRequester)
                    } else {
                        Modifier
                    },
                ).testTag("field-$labelText"),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
    )
}

@Composable internal fun Toggle(
    label: Int,
    checked: Boolean,
    change: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(checked, onClick = {
            change(!checked)
        }, role = Role.Checkbox),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked, onCheckedChange = null)
        Text(stringResource(label), Modifier.padding(top = 12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Choice(
    label: Int,
    selected: String,
    choices: List<Pair<String?, String>>,
    useSheet: Boolean = true,
    editorRow: Boolean = false,
    compactEditor: Boolean = false,
    rowLabel: Int? = null,
    change: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val labelText = stringResource(label)
    Column {
        val open = {
            query = ""
            expanded = true
        }
        when {
            editorRow -> {
                EditorSelectorRow(
                    label = stringResource(rowLabel ?: label),
                    value = selected,
                    onClick = open,
                    testTag = "choice-$labelText",
                )
            }

            compactEditor -> {
                FilterChip(
                    selected = false,
                    onClick = open,
                    label = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(selected)
                            Text("⌄", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("choice-$labelText"),
                )
            }

            else -> {
                Text(labelText, style = MaterialTheme.typography.labelLarge)
                TextButton(
                    onClick = open,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("choice-$labelText"),
                ) { Text(selected) }
            }
        }
        if (useSheet && expanded) {
            val selectedId = choices.firstOrNull { it.second == selected }?.first
            val visibleChoices = choices.filter { query.isBlank() || it.second.contains(query, ignoreCase = true) }
            ModalBottomSheet(onDismissRequest = { expanded = false }) {
                Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp)) {
                    Text(labelText, style = MaterialTheme.typography.titleLarge)
                    if (choices.size > 6) {
                        OutlinedTextField(
                            query,
                            { query = it },
                            label = { Text(stringResource(R.string.search_options)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("choice-search-$labelText"),
                        )
                    }
                    LazyColumn(Modifier.heightIn(max = 560.dp)) {
                        items(visibleChoices, key = { it.first ?: "none" }) { (id, title) ->
                            val checked = id == selectedId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .testTag("choice-$labelText-option-${id ?: "none"}")
                                    .selectable(checked, role = Role.RadioButton) {
                                        change(id)
                                        query = ""
                                        expanded = false
                                    },
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            ) {
                                RadioButton(checked, onClick = null)
                                Text(title)
                            }
                        }
                    }
                }
            }
        } else if (!useSheet) {
            DropdownMenu(expanded, { expanded = false }) {
                choices.forEach { (id, title) ->
                    DropdownMenuItem(
                        text = { Text(title) },
                        modifier = Modifier.testTag("choice-$labelText-option-${id ?: "none"}"),
                        onClick = {
                            change(id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun EditorSelectorRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@Composable
internal fun EditorActionRow(
    title: String,
    summary: String? = null,
    expanded: Boolean? = null,
    onClick: () -> Unit,
    testTag: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!summary.isNullOrBlank()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            when (expanded) {
                true -> "⌃"
                false -> "⌄"
                null -> "›"
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DatePickerField(
    label: Int,
    value: String,
    onChange: (String) -> Unit,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val labelText = stringResource(label)
    Column {
        Text(labelText, style = MaterialTheme.typography.labelLarge)
        TextButton(
            onClick = { pickerOpen = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("date-picker-$labelText"),
        ) {
            Text(value.ifBlank { stringResource(R.string.any_date) })
        }
    }
    if (pickerOpen) {
        val selectedDate = runCatching { LocalDate.parse(value) }.getOrNull() ?: LocalDate.now()
        val pickerState =
            rememberDatePickerState(
                initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                initialDisplayMode = DisplayMode.Picker,
                yearRange = 1..9999,
            )
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onChange(
                            java.time.Instant
                                .ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                                .toString(),
                        )
                    }
                    pickerOpen = false
                }) { Text(stringResource(R.string.done)) }
            },
            dismissButton = {
                TextButton(onClick = { pickerOpen = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(pickerState, showModeToggle = true)
        }
    }
}

@Composable internal fun Form(
    title: Int,
    state: BookState,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        state.error?.let {
            Text(
                stringResource(it),
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier.semantics {
                        liveRegion =
                            LiveRegionMode.Polite
                    },
            )
        }
        content()
        Spacer(Modifier.height(96.dp))
    }
}
