package moe.frost.kochenite.transactions.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.editor.addItem
import moe.frost.kochenite.domain.editor.mainText
import moe.frost.kochenite.domain.editor.moveItem
import moe.frost.kochenite.domain.editor.removeItem
import moe.frost.kochenite.domain.editor.withAccount
import moe.frost.kochenite.domain.editor.withCurrency
import moe.frost.kochenite.domain.editor.withKind
import moe.frost.kochenite.domain.editor.withMainText
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.ui.components.Choice
import moe.frost.kochenite.ui.components.EditorActionRow
import moe.frost.kochenite.ui.components.Field
import moe.frost.kochenite.ui.components.currency
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Currency

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionEditorScreen(
    state: BookState,
    bookModel: BookViewModel,
    model: TransactionEditorStateOwner,
    editor: EntryEditorState,
) {
    val current = editor.input
    val accounts = state.accounts.map { it.account }
    val account = accounts.find { it.id == current.input.accountId }
    val destination = accounts.find { it.id == current.input.destinationAccountId }
    val currency = current.transactionCurrency ?: account?.currency
    val transfer = current.input.kind == EntryKind.TRANSFER
    val foreign = !transfer && currency != account?.currency
    val focus = remember(current.id) { FocusRequester() }
    val clearTitle = stringResource(R.string.clear_title)
    var noteOpen by remember { mutableStateOf(false) }
    var showDateTimePicker by remember { mutableStateOf(false) }
    var pickerShowsTime by remember { mutableStateOf(false) }
    if (editor.confirmDiscard) {
        AlertDialog(
            onDismissRequest = model::keepEditing,
            title = { Text(stringResource(R.string.discard_changes)) },
            confirmButton = { TextButton(onClick = model::discardEditor) { Text(stringResource(R.string.discard)) } },
            dismissButton = { TextButton(onClick = model::keepEditing) { Text(stringResource(R.string.keep_editing)) } },
        )
    }
    if (editor.confirmIncomplete) {
        AlertDialog(
            onDismissRequest = model::keepEditing,
            title = { Text(stringResource(R.string.save_incomplete_question)) },
            text = { Text(stringResource(R.string.incomplete_confirmation)) },
            confirmButton = { TextButton(onClick = { model.confirmEditor(true) }) { Text(stringResource(R.string.save_incomplete)) } },
            dismissButton = { TextButton(onClick = model::keepEditing) { Text(stringResource(R.string.keep_editing)) } },
        )
    }
    if (editor.itemsOpen) {
        TransactionItemsScreen(state, model, editor)
        return
    }
    LaunchedEffect(current.id) {
        if (editor.focusTitle) {
            focus.requestFocus()
            model.titleFocused()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            val kinds = listOf(EntryKind.EXPENSE, EntryKind.INCOME, EntryKind.TRANSFER)
            val labels = listOf(R.string.expense, R.string.income, R.string.transfer)
            PrimaryTabRow(selectedTabIndex = kinds.indexOf(current.input.kind).coerceAtLeast(0)) {
                kinds.forEachIndexed { index, kind ->
                    Tab(
                        selected = current.input.kind == kind,
                        enabled = !editor.saving,
                        onClick = { model.updateEditor { it.withKind(kind, accounts) } },
                        text = { Text(stringResource(labels[index])) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = {
                    showDateTimePicker = true
                }, modifier = Modifier.testTag("transaction-date-time")) {
                    Text("${current.input.date}  ·  ${current.input.time.toString().take(5)}")
                }
                TextButton(onClick = {}, enabled = false) { Text(stringResource(R.string.from_receipt)) }
            }
            TextField(
                value = current.input.merchant,
                onValueChange = { text -> model.updateEditor { it.copy(input = it.input.copy(merchant = text)) } },
                label = { Text(stringResource(R.string.transaction_title)) },
                singleLine = true,
                enabled = !editor.saving,
                trailingIcon = {
                    if (current.input.merchant.isNotEmpty()) {
                        IconButton(onClick = {
                            model.updateEditor { it.copy(input = it.input.copy(merchant = "")) }
                        }) { Text("×", modifier = Modifier.semantics { contentDescription = clearTitle }) }
                    }
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("field-Title"),
            )
            Spacer(Modifier.height(12.dp))
            AmountControl(
                stringResource(if (transfer) R.string.sent_amount else R.string.amount),
                currency,
                text = current.mainText(accounts),
                allowSign = !transfer,
                negative = current.negative,
                enabled = !editor.saving,
                change = { text -> model.updateEditor { it.withMainText(text, accounts) } },
                sign = { model.updateEditor { it.copy(negative = !it.negative) } },
                choose = if (transfer) null else { chosen -> model.updateEditor { it.withCurrency(chosen, accounts) } },
            )
            if (foreign) {
                AmountControl(
                    stringResource(R.string.account_amount),
                    account?.currency,
                    text =
                        current.amountText
                            .orEmpty()
                            .removePrefix("-")
                            .removePrefix("+"),
                    allowSign = false,
                    negative = current.negative,
                    enabled = !editor.saving,
                    change = { text -> model.updateEditor { it.copy(amountText = text) } },
                    sign = {},
                    choose = null,
                    small = true,
                )
            }
            if (transfer) {
                AmountControl(
                    stringResource(R.string.received_amount),
                    destination?.currency,
                    text = current.receivedText.orEmpty(),
                    allowSign = false,
                    negative = false,
                    enabled = !editor.saving,
                    change = { text ->
                        model.updateEditor {
                            if (account?.currency == destination?.currency) {
                                it.copy(amountText = text, receivedText = text)
                            } else {
                                it.copy(receivedText = text)
                            }
                        }
                    },
                    sign = {},
                    choose = null,
                )
            } else {
                EditorActionRow(
                    stringResource(if (current.input.receiptLines.isEmpty()) R.string.split_transaction else R.string.transaction_items),
                    if (current.input.receiptLines.isEmpty()) null else reconciliation(current, accounts, state.categories),
                    onClick = {
                        if (current.input.receiptLines.isEmpty()) {
                            model.showItems(true)
                        } else {
                            model.showItems(true)
                        }
                    },
                    testTag = "transaction-items",
                )
            }
            HorizontalDivider()
            Choice(
                if (transfer) R.string.from_account else R.string.transaction_account,
                account?.name ?: stringResource(R.string.select_account),
                accounts.filter { !it.archived }.map { it.id to "${it.name} · ${it.currency.code}" },
                editorRow = true,
            ) { id ->
                if (id != null) model.updateEditor { it.withAccount(id, accounts) }
            }
            HorizontalDivider()
            if (transfer) {
                Choice(
                    R.string.destination_account,
                    destination?.name ?: stringResource(R.string.select_account),
                    accounts.filter { !it.archived && it.id != account?.id }.map { it.id to "${it.name} · ${it.currency.code}" },
                    editorRow = true,
                ) { id ->
                    if (id != null) model.updateEditor { it.withAccount(id, accounts, destination = true) }
                }
                HorizontalDivider()
            } else if (current.input.receiptLines.isEmpty()) {
                ItemCategory(
                    state,
                    current,
                    current.input.allocations
                        .firstOrNull()
                        ?.categoryId,
                ) { id ->
                    model.updateEditor {
                        it.copy(
                            input =
                                it.input.copy(
                                    allocations =
                                        it.input.allocations.map { row ->
                                            row.copy(categoryId = id)
                                        },
                                ),
                        )
                    }
                }
                HorizontalDivider()
            }
            EditorActionRow(stringResource(R.string.notes), current.input.note, onClick = {
                noteOpen = true
            })
            HorizontalDivider()
            OptionalSelectionRows(state, bookModel, model, current)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.attachment), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            editor.error?.let {
                Text(
                    stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
        HorizontalDivider()
        Button(
            onClick = {
                model.confirmEditor()
            },
            enabled = !editor.saving,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 52.dp),
            shape =
                androidx.compose.foundation.shape
                    .RoundedCornerShape(12.dp),
        ) {
            if (editor.saving) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.save_transaction))
            }
        }
    }
    if (noteOpen) {
        ModalBottomSheet(onDismissRequest = { noteOpen = false }) {
            Column(Modifier.padding(16.dp).imePadding()) {
                Field(
                    R.string.notes,
                    current.input.note,
                    { text -> model.updateEditor { it.copy(input = it.input.copy(note = text)) } },
                    multiline = true,
                )
                TextButton(onClick = { noteOpen = false }) { Text(stringResource(R.string.done)) }
            }
        }
    }
    if (showDateTimePicker) {
        val dateState =
            rememberDatePickerState(
                initialSelectedDateMillis =
                    current.input.date
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                        .toEpochMilli(),
            )
        val timeState = rememberTimePickerState(current.input.time.hour, current.input.time.minute, true)
        Dialog(
            onDismissRequest = { showDateTimePicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).widthIn(max = 560.dp),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 24.dp, bottom = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.transaction_date_time),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    PrimaryTabRow(
                        selectedTabIndex = if (pickerShowsTime) 1 else 0,
                    ) {
                        Tab(
                            selected = !pickerShowsTime,
                            onClick = { pickerShowsTime = false },
                            text = { Text(stringResource(R.string.date)) },
                        )
                        Tab(
                            selected = pickerShowsTime,
                            onClick = { pickerShowsTime = true },
                            text = { Text(stringResource(R.string.time)) },
                        )
                    }
                    if (pickerShowsTime) {
                        TimePicker(
                            timeState,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        DatePicker(
                            dateState,
                            modifier = Modifier.fillMaxWidth(),
                            showModeToggle = true,
                        )
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showDateTimePicker = false }) { Text(stringResource(R.string.cancel)) }
                        TextButton(onClick = {
                            val date =
                                dateState.selectedDateMillis?.let {
                                    java.time.Instant
                                        .ofEpochMilli(it)
                                        .atZone(ZoneOffset.UTC)
                                        .toLocalDate()
                                }
                                    ?: current.input.date
                            model.updateEditor {
                                it.copy(
                                    dateText = null,
                                    timeText = null,
                                    input = it.input.copy(date = date, time = LocalTime.of(timeState.hour, timeState.minute)),
                                )
                            }
                            showDateTimePicker = false
                        }) { Text(stringResource(R.string.done)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountControl(
    label: String,
    currency: BookCurrency?,
    text: String,
    allowSign: Boolean,
    negative: Boolean,
    enabled: Boolean,
    change: (String) -> Unit,
    sign: () -> Unit,
    choose: ((BookCurrency) -> Unit)?,
    small: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    val signAction = stringResource(if (negative) R.string.make_amount_positive else R.string.make_amount_negative)
    val currencyName = stringResource(R.string.currency_name)
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (allowSign) {
            OutlinedIconButton(
                onClick = sign,
                enabled = enabled,
                shape = CircleShape,
                border = ButtonDefaults.outlinedButtonBorder(enabled),
            ) { Text(if (negative) "−" else "+", style = MaterialTheme.typography.headlineSmall) }
            // ){    Icon(painterResource(R.drawable.ic_launcher), contentDescription = signAction) }
        }
        BasicTextField(
            text,
            change,
            enabled = enabled,
            singleLine = true,
            textStyle =
                MaterialTheme.typography.headlineLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                            "0",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    field()
                }
            },
        )
        TextButton(
            onClick = { open = true },
            enabled = choose != null,
            modifier =
                Modifier.testTag("transaction-currency").semantics {
                    contentDescription = "$currencyName: ${currency?.code.orEmpty()}"
                },
            colors =
                ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                    disabledContentColor = MaterialTheme.colorScheme.secondary,
                ),
        ) { Text(currency?.code.orEmpty()) }
    }
    val invalid = text.isNotBlank() && (currency == null || runCatching { currency.parseMinor(text) }.getOrNull()?.let { it <= 0 } != false)
    if (invalid) {
        Text(
            stringResource(R.string.amount_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (open && choose != null) {
        CurrencySheet({ open = false }) {
            choose(it)
            open = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencySheet(
    close: () -> Unit,
    choose: (BookCurrency) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val currencies = remember { Currency.getAvailableCurrencies().filter { it.defaultFractionDigits in 0..3 }.sortedBy { it.currencyCode } }
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.padding(16.dp).imePadding()) {
            OutlinedTextField(
                query,
                { query = it },
                label = { Text(stringResource(R.string.currency_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                itemsIndexed(
                    currencies.filter { it.currencyCode.contains(query, true) || it.displayName.contains(query, true) },
                ) { _, item ->
                    TextButton(
                        onClick = { choose(BookCurrency(item.currencyCode, item.defaultFractionDigits)) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${item.currencyCode} · ${item.displayName}")
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCategory(
    state: BookState,
    editor: EditorInput,
    selected: String?,
    choose: (String?) -> Unit,
) {
    val kind = if (editor.input.kind == EntryKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = state.categories.filter { !it.archived && it.kind == kind }
    Choice(
        R.string.category,
        categories.find { it.id == selected }?.name ?: stringResource(R.string.uncategorized),
        listOf(null to stringResource(R.string.uncategorized)) + categories.map { it.id to it.name },
        editorRow = true,
        change = choose,
    )
}

@Composable
private fun reconciliation(
    editor: EditorInput,
    accounts: List<Account>,
    categories: List<Category>,
): String {
    val currency = editor.transactionCurrency ?: return stringResource(R.string.amounts_pending)
    val values =
        editor.input.receiptLines.map { line ->
            runCatching { currency.parseMinor(editor.lineTexts[line.id].orEmpty()) }.getOrNull()?.takeIf {
                it >
                    0
            }
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
        runCatching { Math.absExact(currency.parseMinor(editor.mainText(accounts))) }
            .getOrNull()
            ?.takeIf { it > 0 }
    val expectedCategoryKind = if (editor.input.kind == EntryKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val allocations = editor.input.allocations.associateBy { it.id }
    val categoriesValid =
        editor.input.receiptLines.size == allocations.size &&
            editor.input.receiptLines.all { line ->
                val allocation = allocations[line.id] ?: return@all false
                val categoryId = allocation.categoryId
                categoryId == null || categories.any { it.id == categoryId && !it.archived && it.kind == expectedCategoryKind }
            }
    val status =
        when {
            sum == null || target == null -> stringResource(R.string.amounts_pending)
            sum != target -> stringResource(R.string.totals_mismatch)
            !categoriesValid -> stringResource(R.string.item_categories_error)
            else -> stringResource(R.string.totals_match)
        }
    val account = accounts.find { it.id == editor.input.accountId }
    val allocation =
        if (editor.input.amountMinor == null && currency != account?.currency) {
            stringResource(R.string.account_allocation_pending)
        } else {
            ""
        }
    return stringResource(
        R.string.item_reconciliation,
        editor.input.receiptLines.size,
        currency.code,
        sum?.let(currency::formatMinor) ?: "?",
        target?.let(currency::formatMinor) ?: "?",
        status,
        allocation,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionItemsScreen(
    state: BookState,
    model: TransactionEditorStateOwner,
    editor: EntryEditorState,
) {
    val current = editor.input
    val accounts = state.accounts.map { it.account }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val moveUp = stringResource(R.string.move_up)
    val moveDown = stringResource(R.string.move_down)
    val itemDeleted = stringResource(R.string.item_deleted)
    val undo = stringResource(R.string.undo)
    val uncategorized = stringResource(R.string.uncategorized)
    val transactionTitle = if (current.input.merchant.isBlank()) stringResource(R.string.new_transaction) else current.input.merchant
    Column(Modifier.fillMaxSize()) {
        Text(
            transactionTitle,
            Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "${current.input.date} · ${current.input.time.toString().take(5)}",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(current.input.receiptLines, key = { _, line -> line.id }) { index, line ->
                val category = state.categories.find { it.id == current.input.allocations[index].categoryId }?.name ?: uncategorized
                val moveItemDescription = stringResource(R.string.move_item, line.description.ifBlank { category })
                val deleteItemDescription = stringResource(R.string.delete_item, index + 1)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = MaterialTheme.colorScheme.onSurfaceVariant
                    var drag by remember(line.id) { mutableStateOf(0f) }
                    Canvas(
                        Modifier
                            .size(48.dp)
                            .semantics {
                                contentDescription = moveItemDescription
                                customActions =
                                    listOf(
                                        CustomAccessibilityAction(moveUp) {
                                            model.updateEditor { it.moveItem(line.id, -1, accounts) }
                                            true
                                        },
                                        CustomAccessibilityAction(moveDown) {
                                            model.updateEditor { it.moveItem(line.id, 1, accounts) }
                                            true
                                        },
                                    )
                            }.pointerInput(line.id) {
                                detectDragGestures(
                                    onDragStart = { drag = 0f },
                                    onDragEnd = { drag = 0f },
                                    onDragCancel = { drag = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    drag += amount.y
                                    val threshold = 56.dp.toPx()
                                    if (kotlin.math.abs(drag) >=
                                        threshold
                                    ) {
                                        model.updateEditor { it.moveItem(line.id, if (drag > 0) 1 else -1, accounts) }
                                        drag = 0f
                                    }
                                }
                            },
                    ) {
                        for (offset in listOf(-6f, 0f, 6f)) {
                            drawLine(
                                color,
                                Offset(size.width * .32f, size.height / 2 + offset),
                                Offset(size.width * .68f, size.height / 2 + offset),
                                2f,
                            )
                        }
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 64.dp)
                            .clickable {
                                model.showItem(line.id)
                            }.semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(line.description.ifBlank { category }, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (line.description.isNotBlank()) {
                                Text(
                                    category,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text(
                            "${current.transactionCurrency?.code.orEmpty()} ${current.lineTexts[line.id].orEmpty().ifBlank { "—" }}",
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(max = 160.dp),
                        )
                    }
                    IconButton(onClick = {
                        val removedRow = current.input.allocations[index]
                        val text = current.lineTexts[line.id].orEmpty()
                        model.updateEditor { it.removeItem(line.id, accounts) }
                        scope.launch {
                            if (snackbar.showSnackbar(itemDeleted, undo, duration = SnackbarDuration.Short) ==
                                SnackbarResult.ActionPerformed
                            ) {
                                model.updateEditor { latest ->
                                    val lines = latest.input.receiptLines.toMutableList()
                                    val rows = if (lines.isEmpty()) mutableListOf() else latest.input.allocations.toMutableList()
                                    val at = index.coerceAtMost(lines.size)
                                    lines.add(at, line)
                                    rows.add(at, removedRow)
                                    latest.copy(
                                        input = latest.input.copy(receiptLines = lines, allocations = rows),
                                        lineTexts =
                                            latest.lineTexts + (line.id to text),
                                    )
                                }
                            }
                        }
                    }, modifier = Modifier.semantics { contentDescription = deleteItemDescription }) {
                        Text(
                            "×",
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
            item {
                TextButton(onClick = {
                    model.updateEditor { it.addItem(accounts) }
                    model.showItem(
                        model.editor.value
                            ?.input
                            ?.input
                            ?.receiptLines
                            ?.lastOrNull()
                            ?.id,
                    )
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(stringResource(R.string.add_item)) }
            }
        }
        SnackbarHost(snackbar)
        HorizontalDivider()
        Text(
            reconciliation(current, accounts, state.categories),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = {
            model.showItems(false)
        }, modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
    }
    val item = current.input.receiptLines.find { it.id == editor.itemId }
    if (item != null) {
        ModalBottomSheet(onDismissRequest = {
            model.showItem(null)
        }) {
            Column(Modifier.padding(16.dp).imePadding().verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.item), style = MaterialTheme.typography.titleLarge)
                Field(R.string.transaction_title, item.description, { text ->
                    model.updateEditor {
                        it.copy(
                            input =
                                it.input.copy(
                                    receiptLines =
                                        it.input.receiptLines.map { line ->
                                            if (line.id ==
                                                item.id
                                            ) {
                                                line.copy(description = text)
                                            } else {
                                                line
                                            }
                                        },
                                ),
                        )
                    }
                })
                ItemCategory(
                    state,
                    current,
                    current.input.allocations
                        .find { it.id == item.id }
                        ?.categoryId,
                ) { id ->
                    model.updateEditor {
                        it.copy(
                            input =
                                it.input.copy(
                                    allocations =
                                        it.input.allocations.map { row ->
                                            if (row.id ==
                                                item.id
                                            ) {
                                                row.copy(categoryId = id)
                                            } else {
                                                row
                                            }
                                        },
                                ),
                        )
                    }
                }
                AmountControl(
                    "Amount",
                    currency = current.transactionCurrency,
                    text = current.lineTexts[item.id].orEmpty(),
                    allowSign = false,
                    negative = false,
                    enabled = true,
                    change = { text -> model.updateEditor { it.copy(lineTexts = it.lineTexts + (item.id to text)) } },
                    sign = {},
                    choose = null,
                    small = true,
                )
                TextButton(onClick = {
                    model.showItem(null)
                }, modifier = Modifier.fillMaxWidth().testTag("transaction-item-done")) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionalSelectionRows(
    state: BookState,
    bookModel: BookViewModel,
    model: TransactionEditorStateOwner,
    current: EditorInput,
) {
    var showTagPicker by remember(current.id) { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val selectedTags = current.input.tagIds.mapNotNull { tagId -> state.tags.find { it.id == tagId }?.name }
    EditorActionRow(stringResource(R.string.tags), selectedTags.joinToString(), onClick = {
        keyboardController?.hide()
        showTagPicker =
            true
    })
    if (showTagPicker) {
        ModalBottomSheet(onDismissRequest = { showTagPicker = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .heightIn(max = 560.dp)
                    .padding(horizontal = 24.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.tags), style = MaterialTheme.typography.titleLarge)
                    TextButton(
                        onClick = { showTagPicker = false },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("entry-tag-picker-done"),
                    ) {
                        Text(stringResource(R.string.done))
                    }
                }
                val availableTags = state.tags.filter { !it.archived || it.id in current.input.tagIds }
                LazyColumn(
                    Modifier.weight(1f, fill = false).heightIn(max = 480.dp).testTag("entry-tag-picker-list"),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(availableTags, key = { it.id }) { tag ->
                        val checked = tag.id in current.input.tagIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("entry-tag-${tag.id}")
                                .selectable(checked, role = Role.Checkbox) {
                                    model.updateEditor { editor ->
                                        editor.copy(
                                            input =
                                                editor.input.copy(
                                                    tagIds =
                                                        if (checked) {
                                                            editor.input.tagIds - tag.id
                                                        } else {
                                                            editor.input.tagIds +
                                                                tag.id
                                                        },
                                                ),
                                        )
                                    }
                                },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Checkbox(checked, onCheckedChange = null)
                            Text(tag.name)
                        }
                    }
                }
            }
        }
    }

    var showRelatedPicker by remember(current.id) { mutableStateOf(false) }
    var relatedQuery by remember(current.id) { mutableStateOf("") }
    HorizontalDivider()
    val relatedRecords = (state.relatedEntries).distinctBy { it.id }
    val relatedNames =
        current.input.relatedEntryIds.mapNotNull { id ->
            relatedRecords.find { it.id == id }?.let { it.input.merchant.ifBlank { it.input.kind.name } }
        }
    val relatedSummary =
        when {
            current.input.relatedEntryIds.isEmpty() -> null
            relatedNames.size == current.input.relatedEntryIds.size -> relatedNames.joinToString()
            else -> stringResource(R.string.related_count, current.input.relatedEntryIds.size)
        }
    EditorActionRow(stringResource(R.string.related_transactions), relatedSummary, onClick = {
        keyboardController?.hide()
        showRelatedPicker =
            true
    })
    LaunchedEffect(showRelatedPicker, relatedQuery, current.input.relatedEntryIds) {
        if (showRelatedPicker) bookModel.searchRelatedEntries(relatedQuery, current.input.relatedEntryIds)
    }
    if (showRelatedPicker) {
        ModalBottomSheet(onDismissRequest = { showRelatedPicker = false }) {
            Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp)) {
                Text(stringResource(R.string.related_transactions), style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    relatedQuery,
                    { relatedQuery = it },
                    label = { Text(stringResource(R.string.search_related)) },
                    singleLine = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("field-${stringResource(R.string.search_related)}"),
                )
                state.relatedError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                val candidates =
                    (
                        state.relatedEntries
                    ).distinctBy { it.id }
                        .filter {
                            it.id != current.entryId &&
                                (it.status == EntryStatus.POSTED || it.id in current.input.relatedEntryIds)
                        }
                LazyColumn(Modifier.heightIn(max = 440.dp)) {
                    items(candidates, key = { it.id }) { related ->
                        val checked = related.id in current.input.relatedEntryIds
                        val enabled = related.status == EntryStatus.POSTED || checked
                        val relatedAccount = state.accounts.find { it.account.id == related.input.accountId }?.account
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp)
                                .testTag("related-entry-${related.id}")
                                .selectable(checked, enabled = enabled, role = Role.Checkbox) {
                                    model.updateEditor { editor ->
                                        editor.copy(
                                            input =
                                                editor.input.copy(
                                                    relatedEntryIds =
                                                        if (checked) {
                                                            editor.input.relatedEntryIds - related.id
                                                        } else {
                                                            editor.input.relatedEntryIds + related.id
                                                        },
                                                ),
                                        )
                                    }
                                },
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Checkbox(checked, onCheckedChange = null, enabled = enabled)
                            Column {
                                Text(
                                    related.input.merchant.ifBlank { related.input.kind.name },
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    "${related.input.date} · ${relatedAccount?.currency?.code.orEmpty()} " +
                                        relatedAccount?.currency?.formatMinor(related.input.amountMinor ?: 0L).orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    if (candidates.isEmpty() && !state.relatedLoading) {
                        item { Text(stringResource(R.string.no_related_transactions), Modifier.padding(vertical = 16.dp)) }
                    }
                    if (state.relatedLoading) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
                    if (state.relatedNext != null && !state.relatedLoading) {
                        item {
                            TextButton(onClick = bookModel::loadMoreRelatedEntries, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text(stringResource(R.string.load_more))
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        showRelatedPicker = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    }
}
