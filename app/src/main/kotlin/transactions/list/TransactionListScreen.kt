package moe.frost.kochenite.transactions.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.update
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.LocalFinanceColors
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.editor.IncompleteRecord
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.failure
import moe.frost.kochenite.transactions.categorySummary
import moe.frost.kochenite.transactions.entryIcon
import moe.frost.kochenite.transactions.entryTitle
import moe.frost.kochenite.transactions.entryTone
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.AppScaffold
import moe.frost.kochenite.ui.components.AppTopBar
import moe.frost.kochenite.ui.components.EmptyState
import moe.frost.kochenite.ui.components.IconAction
import moe.frost.kochenite.ui.components.Notice
import moe.frost.kochenite.ui.components.NoticeTone
import moe.frost.kochenite.ui.components.SectionHeader
import moe.frost.kochenite.ui.components.SignDisplay
import moe.frost.kochenite.ui.components.Tone
import moe.frost.kochenite.ui.components.display
import moe.frost.kochenite.ui.components.shortDate
import moe.frost.kochenite.ui.components.symbol
import moe.frost.kochenite.ui.components.weekdayDate
import java.time.LocalDate

@Composable
internal fun TransactionsScreen(
    state: BookState,
    model: BookViewModel,
    listState: LazyListState,
    selectedEntryId: String?,
    openIncomplete: (String) -> Unit,
    openEntry: (String) -> Unit,
    openTemplates: () -> Unit,
    addTransaction: () -> Unit,
) {
    val query by model.transactionQuery.collectAsState()
    val list by model.transactionList.state.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val currencies = state.accounts.map { it.account.currency }.distinct()
    val filter = query.filter(currencies)

    fun change(update: (TransactionQuery) -> TransactionQuery) = model.transactionQuery.update(update)
    // The list owner keeps loaded rows across screens. A changed filter starts a new search; returning
    // with the same filter reloads in place, so rows stay visible and still pick up outside changes.
    LaunchedEffect(filter, query.tab) {
        if (filter == null || query.tab == LedgerTab.INCOMPLETE) return@LaunchedEffect
        if (filter != model.transactionList.state.value.filter) model.transactionList.search(filter) else model.transactionList.refresh()
    }
    if (query.bulk) {
        BackHandler { change { it.copy(bulk = false) } }
        BulkCategorizeScreen(state, model, list) {
            model.transactionList.clearSelection()
            change { it.copy(bulk = false) }
        }
        return
    }

    fun startBulk() {
        model.transactionList.clearSelection()
        change { it.copy(tab = LedgerTab.POSTED, bulk = true) }
    }
    AppScaffold(
        topBar = {
            AppTopBar(stringResource(R.string.transactions)) {
                IconAction(
                    R.drawable.ic_checklist,
                    stringResource(R.string.bulk_category),
                    Modifier.testTag("bulk-categorize"),
                    onClick = ::startBulk,
                )
                Box {
                    IconAction(R.drawable.ic_more_vert, stringResource(R.string.more), Modifier.testTag("transactions-menu")) {
                        keyboard?.hide()
                        showMenu = true
                    }
                    DropdownMenu(showMenu, { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.templates)) },
                            leadingIcon = { AppIcon(R.drawable.ic_bookmark, null) },
                            onClick = {
                                showMenu = false
                                openTemplates()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.bulk_categorize)) },
                            leadingIcon = { AppIcon(R.drawable.ic_checklist, null) },
                            onClick = {
                                showMenu = false
                                startBulk()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.load_more_history)) },
                            leadingIcon = { AppIcon(R.drawable.ic_restore, null) },
                            enabled = query.tab != LedgerTab.INCOMPLETE && list.next != null && !list.loading,
                            onClick = {
                                showMenu = false
                                model.transactionList.loadMore()
                            },
                        )
                    }
                }
            }
        },
    ) {
        SearchBar(query.text, { text -> change { it.copy(text = text) } }) {
            keyboard?.hide()
            showFilters = true
        }
        if (query.active) {
            ActiveFilters(state, query, currencies, ::change)
        } else {
            StatusChips(query.tab, state.incompleteCount) { tab ->
                model.transactionList.clearSelection()
                change { it.copy(tab = tab) }
            }
        }
        if (list.loading && query.tab != LedgerTab.INCOMPLETE) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.error?.let { error ->
                Notice(
                    stringResource(error),
                    tone = NoticeTone.ERROR,
                    action = stringResource(R.string.retry),
                    onAction = model::refresh,
                )
            }
            list.error?.let { Notice(stringResource(failure(it)), tone = NoticeTone.ERROR) }
        }
        if (query.tab == LedgerTab.INCOMPLETE) {
            IncompleteList(state, model, query, listState, openIncomplete) { change { it.cleared() } }
        } else {
            Ledger(
                state,
                list,
                query,
                listState,
                selectedEntryId,
                openEntry,
                loadMore = model.transactionList::loadMore,
                clear = { change { it.cleared() } },
                addTransaction = addTransaction,
            )
        }
    }
    if (showFilters) {
        FilterSheet(state, query, { showFilters = false }) { applied ->
            change { applied }
            showFilters = false
        }
    }
}

@Composable
private fun SearchBar(
    text: String,
    onChange: (String) -> Unit,
    openFilters: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val label = stringResource(R.string.search)
    Surface(
        shape = CircleShape,
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 56.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_search, null, tint = scheme.onSurface)
            BasicTextField(
                text,
                onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .testTag("transaction-search")
                        .semantics { contentDescription = label },
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (text.isEmpty()) {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        field()
                    }
                },
            )
            if (text.isNotEmpty()) {
                IconAction(R.drawable.ic_close, stringResource(R.string.clear_search)) { onChange("") }
            }
            IconAction(R.drawable.ic_tune, stringResource(R.string.filter), Modifier.testTag("open-filters"), onClick = openFilters)
        }
    }
}

@Composable
private fun StatusChips(
    tab: LedgerTab,
    incompleteCount: Int,
    select: (LedgerTab) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            LedgerTab.POSTED to stringResource(R.string.posted),
            LedgerTab.INCOMPLETE to
                if (incompleteCount > 0) stringResource(R.string.incomplete_tab, incompleteCount) else stringResource(R.string.incomplete),
            LedgerTab.TRASH to stringResource(R.string.trash),
        ).forEach { (key, label) ->
            FilterChip(
                selected = tab == key,
                onClick = { select(key) },
                label = { Text(label, maxLines = 1) },
                leadingIcon = if (tab == key) ({ AppIcon(R.drawable.ic_check, null, Modifier.size(18.dp)) }) else null,
                modifier = Modifier.testTag("tab-${key.name.lowercase()}"),
            )
        }
    }
}

/** The query and each filter stay visible and individually removable. */
@Composable
private fun ActiveFilters(
    state: BookState,
    query: TransactionQuery,
    currencies: List<BookCurrency>,
    change: ((TransactionQuery) -> TransactionQuery) -> Unit,
) {
    val from = query.from
    val through = query.through
    val currency = currencies.find { it.code == query.amountCode }
    val chips =
        buildList<Pair<String, (TransactionQuery) -> TransactionQuery>> {
            if (query.tab == LedgerTab.TRASH) add(stringResource(R.string.trash) to { it.copy(tab = LedgerTab.POSTED) })
            if (query.tab == LedgerTab.INCOMPLETE) add(stringResource(R.string.incomplete) to { it.copy(tab = LedgerTab.POSTED) })
            when {
                from != null && through != null -> "${shortDate(from)} – ${shortDate(through)}"
                from != null -> stringResource(R.string.from_value, shortDate(from))
                through != null -> stringResource(R.string.until_value, shortDate(through))
                else -> null
            }?.let { add(it to { q -> q.copy(from = null, through = null) }) }
            state.accounts.find { it.account.id == query.accountId }?.let { add(it.account.name to { q -> q.copy(accountId = null) }) }
            state.categories.find { it.id == query.categoryId }?.let { add(it.name to { q -> q.copy(categoryId = null) }) }
            state.tags.find { it.id == query.tagId }?.let { add(it.name to { q -> q.copy(tagId = null) }) }
            query.kind?.let { kind ->
                add(
                    stringResource(
                        when (kind) {
                            EntryKind.EXPENSE -> R.string.expense
                            EntryKind.INCOME -> R.string.income
                            EntryKind.TRANSFER -> R.string.transfer
                            else -> R.string.adjustment
                        },
                    ) to { q -> q.copy(kind = null) },
                )
            }
            if (currency != null) {
                val minimum = query.minimum(currency)
                val maximum = query.maximum(currency)
                val label =
                    when {
                        minimum != null && maximum != null -> "${currency.display(minimum)} – ${currency.display(maximum)}"
                        minimum != null -> "≥ ${currency.display(minimum)}"
                        maximum != null -> "≤ ${currency.display(maximum)}"
                        else -> "${currency.symbol().trim()} ${currency.code}"
                    }
                add(label to { q -> q.copy(amountCode = null, minimumText = "", maximumText = "") })
            }
        }
    if (chips.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { (label, remove) ->
            val description = stringResource(R.string.remove_filter, label)
            InputChip(
                selected = false,
                onClick = { change(remove) },
                label = { Text(label, maxLines = 1) },
                trailingIcon = { AppIcon(R.drawable.ic_close, null, Modifier.size(18.dp)) },
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
}

@Composable
internal fun dateHeading(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.today_date, weekdayDate(date, today))
        today.minusDays(1) -> stringResource(R.string.yesterday_date, weekdayDate(date, today))
        else -> weekdayDate(date, today)
    }
}

/** Net of income and expenses for the listed entries of one day, per account currency. Transfers and adjustments are flows of neither. */
internal fun dayNet(
    entries: List<SavedEntry>,
    currencyOf: (String) -> BookCurrency?,
): List<Pair<BookCurrency, Long>> =
    entries
        .filter { it.input.kind == EntryKind.EXPENSE || it.input.kind == EntryKind.INCOME }
        .mapNotNull { entry -> currencyOf(entry.input.accountId)?.let { it to (entry.input.amountMinor ?: 0L) } }
        .groupBy({ it.first }, { it.second })
        .mapNotNull { (currency, amounts) ->
            runCatching { amounts.fold(0L, Math::addExact) }.getOrNull()?.let { currency to it }
        }

@Composable
private fun Ledger(
    state: BookState,
    list: TransactionListState,
    query: TransactionQuery,
    listState: LazyListState,
    selectedEntryId: String?,
    openEntry: (String) -> Unit,
    loadMore: () -> Unit,
    clear: () -> Unit,
    addTransaction: () -> Unit,
) {
    val trash = query.tab == LedgerTab.TRASH
    val entries = list.entries
    val accounts = state.accounts.associate { it.account.id to it.account }
    val grouped = entries.groupBy { it.input.date }.toList()
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (query.active && entries.isNotEmpty()) {
            item(key = "result-meta") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val count = if (list.next != null) "${entries.size}+" else entries.size.toString()
                    Text(
                        pluralStringResource(if (trash) R.plurals.trashed_results else R.plurals.posted_results, entries.size, count),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = clear) { Text(stringResource(R.string.clear_all)) }
                }
            }
        }
        if (trash && entries.isNotEmpty()) {
            item(key = "trash-notice") {
                Notice(stringResource(R.string.trash_notice), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        if (entries.isEmpty() && !list.loading) {
            item(key = "empty-ledger") {
                when {
                    query.active -> {
                        EmptyState(
                            R.drawable.ic_search,
                            stringResource(R.string.no_matches_title),
                            body = stringResource(R.string.no_matches),
                            action = stringResource(R.string.clear_filters),
                            onAction = clear,
                        )
                    }

                    trash -> {
                        EmptyState(R.drawable.ic_delete, stringResource(R.string.empty_trash), body = stringResource(R.string.trash_notice))
                    }

                    else -> {
                        EmptyState(
                            R.drawable.ic_receipt,
                            stringResource(R.string.no_posted),
                            body = stringResource(R.string.no_month_activity_body),
                            action = stringResource(R.string.add_transaction),
                            actionIcon = R.drawable.ic_add,
                            onAction = addTransaction,
                        )
                    }
                }
            }
        }
        grouped.forEachIndexed { index, (date, group) ->
            item(key = "date-$date") {
                // The last loaded day may continue on the next page, so its total would be partial.
                val complete = list.next == null || index < grouped.lastIndex
                val net =
                    if (trash || !complete) {
                        emptyList()
                    } else {
                        dayNet(group) { accounts[it]?.currency }.filter { it.second != 0L }
                    }
                SectionHeader(
                    dateHeading(date),
                    trailing =
                        net
                            .joinToString(
                                " · ",
                            ) { (currency, amount) -> currency.display(amount, SignDisplay.ALWAYS) }
                            .ifEmpty { null },
                )
            }
            items(group, key = { it.id }) { entry ->
                EntryRows(state, entry, list.balances, query.text, trash, selectedEntryId == entry.id) { openEntry(entry.id) }
            }
        }
        if (list.next != null) {
            item(key = "load-more") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(
                        onClick = loadMore,
                        enabled = !list.loading,
                        modifier = Modifier.testTag("load-more"),
                    ) {
                        Text(stringResource(R.string.load_more))
                    }
                }
            }
        }
    }
}

/** One row per account side: a transfer shows its sending row and its receiving row. */
@Composable
private fun EntryRows(
    state: BookState,
    entry: SavedEntry,
    balances: Map<Pair<String, String>, Long>,
    searchText: String,
    trash: Boolean,
    selected: Boolean,
    open: () -> Unit,
) {
    val input = entry.input
    val accounts = state.accounts.map { it.account }
    val account = accounts.find { it.id == input.accountId }
    val destination = accounts.find { it.id == input.destinationAccountId }
    val currency = account?.currency
    val scheme = MaterialTheme.colorScheme
    val title = entryTitle(input, accounts, state.categories)
    val category = categorySummary(input, state.categories)
    val icon = entryIcon(input, state.categories)
    val tone = entryTone(input.kind)
    val amount = input.amountMinor ?: 0L

    fun balance(accountId: String?): String? {
        val owner = accounts.find { it.id == accountId } ?: return null
        return balances[entry.id to owner.id]?.takeIf { !trash }?.let { owner.currency.display(it) }
    }
    val matchedLine =
        input.receiptLines.firstOrNull {
            searchText.isNotBlank() && it.description.contains(searchText.trim(), ignoreCase = true)
        }
    val note =
        input.note.lineSequence().firstOrNull { it.isNotBlank() }
            ?: matchedLine?.let { stringResource(R.string.receipt_line_match, it.description) }
    if (input.kind == EntryKind.TRANSFER) {
        val crossCurrency = destination != null && destination.currency != currency
        Column {
            LedgerRow(
                icon,
                tone,
                title,
                currency?.display(runCatching { Math.negateExact(amount) }.getOrDefault(0L), SignDisplay.ALWAYS).orEmpty(),
                scheme.onSurface,
                category,
                account?.name.orEmpty(),
                Modifier.testTag("posted-entry-${entry.id}"),
                balance = balance(account?.id),
                note = note,
                selected = selected,
                dimmed = trash,
                onClick = open,
            )
            LedgerRow(
                icon,
                tone,
                title,
                destination?.currency?.display(input.receivedMinor ?: 0L, SignDisplay.ALWAYS).orEmpty(),
                scheme.onSurface,
                category,
                destination?.name.orEmpty(),
                Modifier.testTag("posted-entry-${entry.id}-in"),
                balance = balance(destination?.id),
                otherAmount = if (crossCurrency) currency?.display(amount, SignDisplay.NONE) else null,
                selected = selected,
                dimmed = trash,
                onClick = open,
            )
        }
        return
    }
    val merchant = input.merchantAmount?.takeIf { it.currency != currency }
    LedgerRow(
        icon,
        tone,
        title,
        currency?.display(amount, SignDisplay.ALWAYS).orEmpty(),
        when (input.kind) {
            EntryKind.INCOME -> LocalFinanceColors.current.income
            EntryKind.OPENING, EntryKind.ADJUSTMENT -> scheme.onSurfaceVariant
            else -> scheme.onSurface
        },
        category,
        account?.name.orEmpty(),
        Modifier.testTag("posted-entry-${entry.id}"),
        balance = balance(account?.id),
        otherAmount = merchant?.let { (if (amount < 0) "−" else "") + it.currency.display(it.minor, SignDisplay.NONE) },
        note = note,
        selected = selected,
        dimmed = trash,
        onClick = open,
    )
}

@Composable
private fun IncompleteList(
    state: BookState,
    model: BookViewModel,
    query: TransactionQuery,
    listState: LazyListState,
    open: (String) -> Unit,
    clear: () -> Unit,
) {
    val text = query.text.trim()
    val records =
        state.incompleteRecords.filter { record ->
            text.isBlank() ||
                record.editor.input.merchant
                    .contains(text, true) ||
                record.editor.input.note
                    .contains(text, true)
        }
    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (state.incompleteRecords.isNotEmpty()) {
            item(key = "incomplete-notice") {
                Notice(stringResource(R.string.incomplete_not_posted), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        items(records, key = {
            it.id
        }) { record -> IncompleteRow(state, record, { open(record.id) }) { model.deleteIncomplete(record.id, record.token) } }
        if (records.isEmpty()) {
            item(key = "empty-incomplete") {
                if (state.incompleteRecords.isEmpty()) {
                    EmptyState(
                        R.drawable.ic_check,
                        stringResource(R.string.no_incomplete),
                        body = stringResource(R.string.incomplete_not_posted),
                    )
                } else {
                    EmptyState(
                        R.drawable.ic_search,
                        stringResource(R.string.no_matches_title),
                        body = stringResource(R.string.no_matches),
                        action = stringResource(R.string.clear_filters),
                        onAction = clear,
                    )
                }
            }
        }
    }
}

/** Caution tone plus "Amount needed" marks a row that has not posted; it has no running balance. */
@Composable
private fun IncompleteRow(
    state: BookState,
    record: IncompleteRecord,
    open: () -> Unit,
    delete: () -> Unit,
) {
    val input = record.editor.input
    val accounts = state.accounts.map { it.account }
    val account = accounts.find { it.id == input.accountId }
    var menu by remember { mutableStateOf(false) }
    val raw = record.editor.amountText
    val known = input.amountMinor
    val amount =
        when {
            !raw.isNullOrBlank() && runCatching { account?.currency?.parseMinor(raw) }.getOrNull() == null -> {
                stringResource(R.string.check_amount)
            }

            known != null -> {
                val signed = if (input.kind == EntryKind.TRANSFER) runCatching { Math.negateExact(known) }.getOrDefault(0L) else known
                account?.currency?.display(signed, SignDisplay.ALWAYS).orEmpty()
            }

            else -> {
                stringResource(R.string.amount_needed)
            }
        }
    val category =
        if (input.kind == EntryKind.TRANSFER && input.receivedMinor == null) {
            stringResource(R.string.transfer_received_unknown)
        } else {
            categorySummary(input, state.categories)
        }
    val complete = stringResource(R.string.complete_entry)
    val deleteLabel = stringResource(R.string.delete_incomplete)
    Box {
        LedgerRow(
            entryIcon(input, state.categories),
            Tone.CAUTION,
            entryTitle(input, accounts, state.categories),
            amount,
            LocalFinanceColors.current.caution,
            category,
            account?.name.orEmpty(),
            Modifier.testTag("incomplete-entry-${record.id}").semantics {
                customActions =
                    listOf(
                        CustomAccessibilityAction(deleteLabel) {
                            menu = true
                            true
                        },
                    )
            },
            note = input.note.lineSequence().firstOrNull { it.isNotBlank() },
            onLongClick = { menu = true },
            onClick = open,
        )
        Box(Modifier.align(Alignment.TopEnd)) {
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(
                    text = { Text(complete) },
                    leadingIcon = { AppIcon(R.drawable.ic_edit, null) },
                    onClick = {
                        menu = false
                        open()
                    },
                )
                DropdownMenuItem(
                    text = { Text(deleteLabel, color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { AppIcon(R.drawable.ic_delete, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.testTag("delete-incomplete-${record.id}"),
                    onClick = {
                        menu = false
                        delete()
                    },
                )
            }
        }
    }
}
