package moe.frost.kochenite.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.BookCurrency
import java.util.Currency

/** Outlined text field. Its test tag is "field-" followed by the label. */
@Composable
internal fun Field(
    label: Int,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    number: Boolean = false,
    focusRequester: FocusRequester? = null,
    multiline: Boolean = false,
    supporting: String? = null,
    error: Boolean = false,
    clearable: Boolean = false,
    prefix: String? = null,
) {
    val labelText = stringResource(label)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(labelText, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        enabled = enabled,
        isError = error,
        singleLine = !multiline,
        prefix = prefix?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        trailingIcon =
            if (clearable && value.isNotEmpty() && enabled) {
                { IconAction(R.drawable.ic_close, stringResource(R.string.clear_field, labelText)) { onChange("") } }
            } else {
                null
            },
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .testTag("field-$labelText"),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Decimal else KeyboardType.Text),
    )
}

/** Exposed dropdown menu for a short, fixed list of options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DropdownField(
    label: String,
    value: String,
    options: List<Pair<String?, String>>,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    tag: String = "choice-$label",
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingText = supporting?.let { { Text(it) } },
            trailingIcon = { AppIcon(R.drawable.ic_arrow_drop_down, null) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).testTag(tag),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    modifier = Modifier.testTag("$tag-option-${id ?: "none"}"),
                    onClick = {
                        onSelect(id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = { if (value == selected) AppIcon(R.drawable.ic_check, null, Modifier.size(18.dp)) },
            ) {
                // Segments share a fixed width, so very large text is limited rather than clipped mid-word.
                Text(
                    label,
                    fontSize = cappedFontSize(MaterialTheme.typography.labelLarge, 1.2f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

internal data class SheetOption(
    val id: String?,
    val title: String,
    val supporting: String? = null,
    @DrawableRes val icon: Int? = null,
)

/** Single-choice bottom sheet; searchable once the list is long. Option tags are "$tag-option-$id". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionSheet(
    title: String,
    options: List<SheetOption>,
    selectedId: String?,
    tag: String,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val visible = options.filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Text(
                title,
                Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            if (options.size > 6) {
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text(stringResource(R.string.search_options)) },
                    leadingIcon = { AppIcon(R.drawable.ic_search, null) },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 8.dp)
                            .testTag("$tag-search"),
                )
            }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(visible, key = { it.id ?: "none" }) { option ->
                    val checked = option.id == selectedId
                    val scheme = MaterialTheme.colorScheme
                    ListRow(
                        option.title,
                        Modifier
                            .testTag("$tag-option-${option.id ?: "none"}")
                            .background(if (checked) scheme.secondaryContainer else scheme.surfaceContainerLow)
                            .selectable(checked, role = Role.RadioButton) { onSelect(option.id) },
                        supporting = option.supporting,
                        leading = option.icon?.let { { AppIcon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } },
                        trailing = { if (checked) AppIcon(R.drawable.ic_check, null) },
                    )
                }
            }
        }
    }
}

/**
 * Signed amount entry used by the editor, setup and account forms. The leading button toggles the
 * sign and the currency pill opens the currency sheet. The field's test tag is "field-" followed by the label.
 */
@Composable
internal fun AmountInput(
    label: String,
    currency: BookCurrency?,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    negative: Boolean? = null,
    onToggleSign: () -> Unit = {},
    enabled: Boolean = true,
    showCurrency: Boolean = true,
    onCurrencyClick: (() -> Unit)? = null,
    error: String? = null,
    placeholder: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val signAction = stringResource(if (negative == true) R.string.make_amount_positive else R.string.make_amount_negative)
    val currencyName = stringResource(R.string.currency_name)
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(cardColor())
                .then(if (error != null) Modifier.border(2.dp, scheme.error, MaterialTheme.shapes.extraLarge) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (error != null) scheme.error else scheme.onSurfaceVariant,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (negative != null) {
                    OutlinedIconButton(
                        onClick = onToggleSign,
                        enabled = enabled,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, scheme.outlineVariant),
                        modifier = Modifier.semantics { contentDescription = signAction },
                    ) {
                        AppIcon(if (negative) R.drawable.ic_remove else R.drawable.ic_add, null)
                    }
                }
                val style = MaterialTheme.typography.displaySmall.tabular
                val symbol = currency?.symbol().orEmpty()
                BasicTextField(
                    text,
                    onTextChange,
                    enabled = enabled,
                    singleLine = true,
                    textStyle = style.copy(color = scheme.onSurface, textAlign = TextAlign.End),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    visualTransformation = remember(symbol) { PrefixTransformation(symbol) },
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("field-$label")
                            .semantics { contentDescription = label },
                    decorationBox = { field ->
                        Box(contentAlignment = Alignment.CenterEnd) {
                            if (text.isEmpty()) {
                                Text(
                                    placeholder ?: "${symbol}0",
                                    style = MaterialTheme.typography.headlineSmall.tabular,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                            field()
                        }
                    },
                )
                if (showCurrency) {
                    val pillEnabled = enabled && onCurrencyClick != null
                    Surface(
                        onClick = { onCurrencyClick?.invoke() },
                        enabled = pillEnabled,
                        shape = CircleShape,
                        color = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer,
                        modifier =
                            Modifier
                                .height(40.dp)
                                .alpha(if (pillEnabled) 1f else 0.38f)
                                .testTag("amount-currency")
                                .semantics { contentDescription = "$currencyName: ${currency?.code.orEmpty()}" },
                    ) {
                        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text(
                                currency?.code.orEmpty(),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }
                }
            }
        }
        if (error != null) {
            Text(
                error,
                color = scheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Shows the currency symbol before typed digits without changing the edited text. */
private class PrefixTransformation(
    private val prefix: String,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (text.isEmpty() || prefix.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val mapping =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = offset + prefix.length

                override fun transformedToOriginal(offset: Int) = (offset - prefix.length).coerceIn(0, text.length)
            }
        return TransformedText(AnnotatedString(prefix) + text, mapping)
    }
}

/** Whether non-blank amount text is not an exact, unsigned amount in [currency]. The sign is entered separately. */
internal fun invalidAmount(
    text: String,
    currency: BookCurrency?,
): Boolean = text.isNotBlank() && (currency == null || runCatching { currency.parseMinor(text) }.getOrNull()?.let { it < 0 } != false)

/** Searchable ISO 4217 list with the currencies already in use first. Option tags are "currency-option-CODE". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CurrencySheet(
    selected: BookCurrency?,
    recent: List<BookCurrency>,
    onDismiss: () -> Unit,
    onChoose: (BookCurrency) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val all =
        remember {
            Currency
                .getAvailableCurrencies()
                .filter { it.defaultFractionDigits in 0..3 }
                .sortedBy { it.currencyCode }
        }
    val matches = all.filter { query.isBlank() || it.currencyCode.contains(query, true) || it.displayName.contains(query, true) }
    val recentCodes = recent.map { it.code }.distinct()
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Text(
                stringResource(R.string.currency_name),
                Modifier.padding(horizontal = 24.dp).semantics { heading() },
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                query,
                { query = it },
                label = { Text(stringResource(R.string.search_currency)) },
                placeholder = { Text(stringResource(R.string.code_or_name)) },
                leadingIcon = { AppIcon(R.drawable.ic_search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).testTag("currency-search"),
            )
            LazyColumn(Modifier.heightIn(max = 440.dp)) {
                if (query.isBlank() && recentCodes.isNotEmpty()) {
                    item(key = "recent-header") { SectionHeader(stringResource(R.string.recent)) }
                    items(all.filter { it.currencyCode in recentCodes }, key = { "recent-${it.currencyCode}" }) { item ->
                        CurrencyRow(item, selected, "currency-recent-${item.currencyCode}", onChoose)
                    }
                    item(key = "all-header") { SectionHeader(stringResource(R.string.all_currencies)) }
                }
                items(matches, key = { it.currencyCode }) { item ->
                    CurrencyRow(item, selected, "currency-option-${item.currencyCode}", onChoose)
                }
            }
        }
    }
}

@Composable
private fun CurrencyRow(
    item: Currency,
    selected: BookCurrency?,
    tag: String,
    onChoose: (BookCurrency) -> Unit,
) {
    val checked = item.currencyCode == selected?.code
    ListRow(
        "${item.currencyCode} · ${item.displayName}",
        Modifier
            .testTag(tag)
            .background(if (checked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .selectable(checked, role = Role.RadioButton) { onChoose(BookCurrency(item.currencyCode, item.defaultFractionDigits)) },
        trailing = { if (checked) AppIcon(R.drawable.ic_check, null) },
    )
}
