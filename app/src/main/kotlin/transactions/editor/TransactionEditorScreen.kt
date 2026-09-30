package moe.frost.kochenite.transactions.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.editor.mainText
import moe.frost.kochenite.domain.editor.withAccount
import moe.frost.kochenite.domain.editor.withCurrency
import moe.frost.kochenite.domain.editor.withKind
import moe.frost.kochenite.domain.editor.withMainText
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.transactions.accountIcon
import moe.frost.kochenite.transactions.categoryIcon
import moe.frost.kochenite.transactions.categoryPath
import moe.frost.kochenite.ui.components.AmountInput
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.AppScaffold
import moe.frost.kochenite.ui.components.AppTopBar
import moe.frost.kochenite.ui.components.ContentMaxWidth
import moe.frost.kochenite.ui.components.CurrencySheet
import moe.frost.kochenite.ui.components.Field
import moe.frost.kochenite.ui.components.GroupCard
import moe.frost.kochenite.ui.components.IconAction
import moe.frost.kochenite.ui.components.ListRow
import moe.frost.kochenite.ui.components.Notice
import moe.frost.kochenite.ui.components.NoticeTone
import moe.frost.kochenite.ui.components.OptionSheet
import moe.frost.kochenite.ui.components.Segmented
import moe.frost.kochenite.ui.components.SheetOption
import moe.frost.kochenite.ui.components.clockTime
import moe.frost.kochenite.ui.components.display
import moe.frost.kochenite.ui.components.weekdayDate
import java.time.LocalDate

private enum class Picker { ACCOUNT, DESTINATION, CATEGORY, CURRENCY, TAGS, RELATED, DATE_TIME }

/** One flat surface with no hidden section. Nothing is written before Save. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TransactionEditorScreen(
    state: BookState,
    bookModel: BookViewModel,
    model: TransactionEditorStateOwner,
    editor: EntryEditorState,
    onClose: () -> Unit,
) {
    val current = editor.input
    val input = current.input
    val accounts = state.accounts.map { it.account }
    val account = accounts.find { it.id == input.accountId }
    val destination = accounts.find { it.id == input.destinationAccountId }
    val currency = current.transactionCurrency ?: account?.currency
    val transfer = input.kind == EntryKind.TRANSFER
    val foreign = !transfer && currency != account?.currency
    val focus = remember(current.id) { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var picker by remember(current.id) { mutableStateOf<Picker?>(null) }
    var pendingKind by remember(current.id) { mutableStateOf<EntryKind?>(null) }
    var confirmDelete by remember(current.id) { mutableStateOf(false) }
    val incompleteToken = editor.incompleteToken

    fun open(next: Picker) {
        keyboard?.hide()
        picker = next
    }
    EditorDialogs(editor, model, pendingKind, accounts) { pendingKind = null }
    if (confirmDelete && incompleteToken != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_incomplete_question)) },
            text = { Text(stringResource(R.string.delete_incomplete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    bookModel.deleteIncomplete(current.id, incompleteToken)
                    model.abandonEditor()
                }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (editor.itemsOpen) {
        TransactionItemsScreen(state, model, editor, onClose)
        return
    }
    LaunchedEffect(current.id) {
        if (editor.focusTitle) {
            focus.requestFocus()
            model.titleFocused()
        }
    }
    val unknownAmounts = input.amountMinor == null || (transfer && input.receivedMinor == null)
    AppScaffold(
        topBar = {
            AppTopBar(
                stringResource(
                    if (current.originalToken == null &&
                        incompleteToken == null
                    ) {
                        R.string.new_transaction
                    } else {
                        R.string.edit_transaction
                    },
                ),
                onBack = onClose,
                close = true,
                backEnabled = !editor.saving,
            ) {
                if (incompleteToken != null) {
                    IconAction(
                        R.drawable.ic_delete,
                        stringResource(R.string.delete_incomplete),
                        Modifier.testTag("delete-incomplete"),
                        enabled = !editor.saving,
                    ) { confirmDelete = true }
                }
            }
        },
        maxWidth = ContentMaxWidth,
        bottomBar =
            {
                if (incompleteToken != null) {
                    // A record with every amount entered can only be posted, so keeping it incomplete is no longer offered.
                    TextButton(
                        onClick = { model.confirmEditor(allowIncomplete = true) },
                        enabled = !editor.saving && unknownAmounts,
                        modifier = Modifier.testTag("save-incomplete"),
                    ) { Text(stringResource(R.string.save_incomplete)) }
                }
                Button(
                    onClick = { model.confirmEditor() },
                    enabled = !editor.saving,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("save-transaction"),
                ) {
                    if (editor.saving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.save_transaction))
                    }
                }
            },
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (incompleteToken != null) Notice(stringResource(R.string.incomplete_editor_notice), tone = NoticeTone.CAUTION)
            Segmented(
                listOf(
                    EntryKind.EXPENSE to stringResource(R.string.expense),
                    EntryKind.INCOME to stringResource(R.string.income),
                    EntryKind.TRANSFER to stringResource(R.string.transfer),
                ),
                input.kind,
                enabled = !editor.saving,
            ) { kind ->
                val losesDetails = input.receiptLines.isNotEmpty() || input.allocations.any { it.categoryId != null }
                if (kind != input.kind && losesDetails) pendingKind = kind else model.updateEditor { it.withKind(kind, accounts) }
            }
            val title: @Composable () -> Unit = {
                Field(
                    R.string.transaction_title,
                    input.merchant,
                    { text -> model.updateEditor { it.copy(input = it.input.copy(merchant = text)) } },
                    enabled = !editor.saving,
                    focusRequester = focus,
                    clearable = true,
                )
            }
            val amountPlaceholder = if (incompleteToken != null) stringResource(R.string.amount_needed) else null
            if (transfer) {
                title()
                AmountInput(
                    stringResource(R.string.sent_amount),
                    account?.currency,
                    current.mainText(accounts),
                    { text -> model.updateEditor { it.withMainText(text, accounts) } },
                    enabled = !editor.saving,
                    error = amountError(current.mainText(accounts), account?.currency),
                    placeholder = amountPlaceholder,
                )
                GroupCard { AccountRow(account, R.string.from_account, "choice-From account") { open(Picker.ACCOUNT) } }
                AmountInput(
                    stringResource(R.string.received_amount),
                    destination?.currency,
                    current.receivedText.orEmpty(),
                    { text ->
                        model.updateEditor {
                            // Same-currency transfers move one amount, so both sides stay equal.
                            if (account?.currency ==
                                destination?.currency
                            ) {
                                it.copy(amountText = text, receivedText = text)
                            } else {
                                it.copy(receivedText = text)
                            }
                        }
                    },
                    enabled = !editor.saving && destination != null,
                    error = amountError(current.receivedText.orEmpty(), destination?.currency),
                    placeholder = amountPlaceholder,
                )
                GroupCard { AccountRow(destination, R.string.destination_account, "choice-To account") { open(Picker.DESTINATION) } }
            } else {
                AmountInput(
                    stringResource(R.string.amount),
                    currency,
                    current.mainText(accounts),
                    { text -> model.updateEditor { it.withMainText(text, accounts) } },
                    negative = current.negative,
                    onToggleSign = { model.updateEditor { it.copy(negative = !it.negative) } },
                    enabled = !editor.saving,
                    onCurrencyClick = { open(Picker.CURRENCY) },
                    error = amountError(current.mainText(accounts), currency),
                    placeholder = amountPlaceholder,
                )
                if (foreign) {
                    // A foreign purchase also needs the actual charge in the account's own currency.
                    val charged =
                        current.amountText
                            .orEmpty()
                            .removePrefix("-")
                            .removePrefix("+")
                    AmountInput(
                        stringResource(R.string.charged_to, account?.name.orEmpty()),
                        account?.currency,
                        charged,
                        { text -> model.updateEditor { it.copy(amountText = text) } },
                        enabled = !editor.saving,
                        showCurrency = false,
                        error = amountError(charged, account?.currency),
                        placeholder = amountPlaceholder,
                    )
                }
                title()
            }
            val futureDate = input.date > LocalDate.now()
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    val today = LocalDate.now()
                    val day = if (input.date == today) stringResource(R.string.today) else weekdayDate(input.date, today)
                    AssistChip(
                        onClick = { open(Picker.DATE_TIME) },
                        enabled = !editor.saving,
                        label = {
                            Text(
                                "$day · ${clockTime(input.time)}",
                                color = if (futureDate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        leadingIcon = {
                            AppIcon(
                                R.drawable.ic_schedule,
                                null,
                                Modifier.size(18.dp),
                                tint = if (futureDate) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        },
                        border =
                            if (futureDate) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            } else {
                                AssistChipDefaults.assistChipBorder(true)
                            },
                        modifier = Modifier.testTag("transaction-date-time"),
                    )
                    // Receipt capture arrives with recognition; until then the entry point stays visibly inert.
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(stringResource(R.string.from_receipt)) },
                        leadingIcon = { AppIcon(R.drawable.ic_camera, null, Modifier.size(18.dp)) },
                    )
                }
                if (futureDate) {
                    Text(
                        stringResource(R.string.future_date_short),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            GroupCard {
                val muted = MaterialTheme.colorScheme.onSurfaceVariant
                val add: @Composable () -> Unit = { AppIcon(R.drawable.ic_add, null, tint = muted) }
                val chevron: @Composable () -> Unit = { AppIcon(R.drawable.ic_chevron_right, null, tint = muted) }
                if (!transfer) {
                    AccountRow(account, R.string.transaction_account, "choice-Account") { open(Picker.ACCOUNT) }
                    if (input.receiptLines.isEmpty()) {
                        val category = state.categories.find { it.id == input.allocations.firstOrNull()?.categoryId }
                        ListRow(
                            category?.let { categoryPath(it, state.categories) } ?: stringResource(R.string.uncategorized),
                            Modifier.testTag("choice-Category"),
                            supporting = stringResource(R.string.category),
                            leading = { AppIcon(R.drawable.ic_category, null, tint = muted) },
                            trailing = { chevron() },
                            enabled = !editor.saving,
                        ) { open(Picker.CATEGORY) }
                        ListRow(
                            stringResource(R.string.split_transaction),
                            Modifier.testTag("transaction-items"),
                            headlineColor = muted,
                            leading = { AppIcon(R.drawable.ic_split, null, tint = muted) },
                            trailing = { add() },
                            enabled = !editor.saving,
                        ) { model.showItems(true) }
                    } else {
                        ListRow(
                            stringResource(R.string.transaction_items),
                            Modifier.testTag("transaction-items"),
                            supporting = itemsSummary(reconcile(current, accounts, state.categories)),
                            leading = { AppIcon(R.drawable.ic_split, null, tint = muted) },
                            trailing = { chevron() },
                            enabled = !editor.saving,
                        ) { model.showItems(true) }
                    }
                }
                val tags = input.tagIds.mapNotNull { id -> state.tags.find { it.id == id }?.name }
                ListRow(
                    tags.joinToString().ifEmpty { stringResource(R.string.add_tags) },
                    Modifier.testTag("editor-tags"),
                    headlineColor = if (tags.isEmpty()) muted else MaterialTheme.colorScheme.onSurface,
                    leading = { AppIcon(R.drawable.ic_label, null, tint = muted) },
                    trailing = { if (tags.isEmpty()) add() else chevron() },
                    enabled = !editor.saving,
                ) { open(Picker.TAGS) }
                val related = input.relatedEntryIds
                ListRow(
                    if (related.isEmpty()) {
                        stringResource(R.string.link_related)
                    } else {
                        pluralStringResource(R.plurals.related_count, related.size, related.size)
                    },
                    Modifier.testTag("editor-related"),
                    headlineColor = if (related.isEmpty()) muted else MaterialTheme.colorScheme.onSurface,
                    leading = { AppIcon(R.drawable.ic_link, null, tint = muted) },
                    trailing = { if (related.isEmpty()) add() else chevron() },
                    enabled = !editor.saving,
                ) { open(Picker.RELATED) }
                // Attachments arrive with receipt capture.
                ListRow(
                    stringResource(R.string.attachment),
                    leading = { AppIcon(R.drawable.ic_attach, null) },
                    enabled = false,
                )
            }
            Field(
                R.string.note,
                input.note,
                { text -> model.updateEditor { it.copy(input = it.input.copy(note = text)) } },
                enabled = !editor.saving,
                multiline = true,
                clearable = true,
            )
        }
        editor.error?.takeIf { it != R.string.conflict_message }?.let {
            Notice(stringResource(it), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), tone = NoticeTone.ERROR)
        }
    }
    when (picker) {
        Picker.ACCOUNT, Picker.DESTINATION -> {
            val toDestination = picker == Picker.DESTINATION
            OptionSheet(
                stringResource(
                    if (toDestination) {
                        R.string.destination_account
                    } else if (transfer) {
                        R.string.from_account
                    } else {
                        R.string.transaction_account
                    },
                ),
                accounts
                    .filter { !it.archived && (!toDestination || it.id != account?.id) }
                    .map {
                        SheetOption(
                            it.id,
                            it.name,
                            listOfNotNull(it.type.ifBlank { null }, it.currency.code).joinToString(" · "),
                            accountIcon(it.type),
                        )
                    },
                if (toDestination) destination?.id else account?.id,
                tag =
                    if (toDestination) {
                        "choice-To account"
                    } else if (transfer) {
                        "choice-From account"
                    } else {
                        "choice-Account"
                    },
                onDismiss = { picker = null },
            ) { id ->
                if (id != null) model.updateEditor { it.withAccount(id, accounts, destination = toDestination) }
                picker = null
            }
        }

        Picker.CATEGORY -> {
            CategorySheet(state, current, input.allocations.firstOrNull()?.categoryId, { picker = null }) { id ->
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
                picker = null
            }
        }

        Picker.CURRENCY -> {
            CurrencySheet(currency, accounts.map { it.currency }, { picker = null }) { chosen ->
                model.updateEditor { it.withCurrency(chosen, accounts) }
                picker = null
            }
        }

        Picker.TAGS -> {
            TagsSheet(state, bookModel, model, current) { picker = null }
        }

        Picker.RELATED -> {
            RelatedSheet(state, bookModel, model, current) { picker = null }
        }

        Picker.DATE_TIME -> {
            DateTimeDialog(input.date, input.time, { picker = null }) { date, time ->
                model.updateEditor { it.copy(dateText = null, timeText = null, input = it.input.copy(date = date, time = time)) }
                picker = null
            }
        }

        null -> {}
    }
}

@Composable
private fun amountError(
    text: String,
    currency: BookCurrency?,
): String? {
    val invalid = text.isNotBlank() && (currency == null || runCatching { currency.parseMinor(text) }.getOrNull()?.let { it <= 0 } != false)
    return if (invalid) stringResource(R.string.amount_error) else null
}

@Composable
private fun AccountRow(
    account: Account?,
    label: Int,
    tag: String,
    onClick: () -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    ListRow(
        account?.name ?: stringResource(R.string.choose_account),
        Modifier.testTag(tag),
        supporting = stringResource(label),
        leading = { AppIcon(accountIcon(account?.type.orEmpty()), null, tint = muted) },
        trailing = { AppIcon(R.drawable.ic_chevron_right, null, tint = muted) },
        onClick = onClick,
    )
}

@Composable
internal fun CategorySheet(
    state: BookState,
    editor: EditorInput,
    selected: String?,
    onDismiss: () -> Unit,
    choose: (String?) -> Unit,
) {
    val kind = if (editor.input.kind == EntryKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
    val categories = state.categories.filter { !it.archived && it.kind == kind }.sortedBy { categoryPath(it, state.categories).lowercase() }
    OptionSheet(
        stringResource(R.string.category),
        listOf(SheetOption(null, stringResource(R.string.uncategorized), icon = R.drawable.ic_category)) +
            categories.map { SheetOption(it.id, categoryPath(it, state.categories), icon = categoryIcon(it, state.categories)) },
        selected,
        tag = "choice-Category",
        onDismiss = onDismiss,
        onSelect = choose,
    )
}

/** "3 items · ¥8,760 of ¥8,760 · Matched" for the editor's items row. */
@Composable
internal fun itemsSummary(reconciliation: Reconciliation): String {
    val currency = reconciliation.currency
    val status =
        stringResource(
            when {
                reconciliation.pending -> R.string.amounts_pending
                reconciliation.difference != 0L -> R.string.totals_mismatch
                reconciliation.invalidCategoryItems.isNotEmpty() -> R.string.item_categories_error
                else -> R.string.totals_match
            },
        )
    return listOfNotNull(
        pluralStringResource(R.plurals.item_count, reconciliation.count, reconciliation.count),
        stringResource(
            R.string.sum_of_target,
            reconciliation.sum?.let { currency?.display(it) } ?: "?",
            reconciliation.target?.let { currency?.display(it) } ?: "?",
        ),
        status,
        stringResource(R.string.account_allocation_pending).takeIf { reconciliation.allocationPending },
    ).joinToString(" · ")
}

@Composable
private fun EditorDialogs(
    editor: EntryEditorState,
    model: TransactionEditorStateOwner,
    pendingKind: EntryKind?,
    accounts: List<Account>,
    clearPendingKind: () -> Unit,
) {
    if (editor.confirmDiscard) {
        AlertDialog(
            onDismissRequest = model::keepEditing,
            title = { Text(stringResource(R.string.discard_changes)) },
            text = { Text(stringResource(R.string.discard_changes_body)) },
            confirmButton = {
                TextButton(
                    onClick = model::discardEditor,
                ) { Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error) }
            },
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
    if (pendingKind != null) {
        AlertDialog(
            onDismissRequest = clearPendingKind,
            title = { Text(stringResource(R.string.change_transaction_kind)) },
            text = { Text(stringResource(R.string.change_kind_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    model.updateEditor { it.withKind(pendingKind, accounts) }
                    clearPendingKind()
                }) { Text(stringResource(R.string.continue_label)) }
            },
            dismissButton = { TextButton(onClick = clearPendingKind) { Text(stringResource(R.string.cancel)) } },
        )
    }
    // A stale edit cannot be merged; the only way forward is to reopen the current record.
    if (editor.error == R.string.conflict_message) {
        AlertDialog(
            // Dismissing keeps the form so its values can still be read before going back.
            onDismissRequest = model::dismissError,
            title = { Text(stringResource(R.string.record_changed)) },
            text = { Text(stringResource(R.string.conflict_message)) },
            confirmButton = { TextButton(onClick = model::abandonEditor) { Text(stringResource(R.string.go_back)) } },
        )
    }
}
