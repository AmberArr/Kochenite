package moe.frost.kochenite.transactions.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.LedgerFilter
import moe.frost.kochenite.failure
import moe.frost.kochenite.ui.components.Choice
import moe.frost.kochenite.ui.components.DatePickerField
import moe.frost.kochenite.ui.components.Field
import moe.frost.kochenite.ui.components.currency
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionsPanel(
    state: BookState,
    model: BookViewModel,
    isActive: Boolean,
    openIncomplete: (String) -> Unit,
    openEntry: (String) -> Unit,
    openTemplates: () -> Unit,
    addTransaction: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var filter by rememberSaveable { mutableStateOf("posted") }
    var query by rememberSaveable { mutableStateOf("") }
    var fromText by rememberSaveable { mutableStateOf("") }
    var throughText by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableStateOf<String?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var tagId by rememberSaveable { mutableStateOf<String?>(null) }
    var kind by rememberSaveable { mutableStateOf<String?>(null) }
    var amountCode by rememberSaveable { mutableStateOf<String?>(null) }
    var minimumText by rememberSaveable { mutableStateOf("") }
    var maximumText by rememberSaveable { mutableStateOf("") }
    var bulk by rememberSaveable { mutableStateOf(false) }
    val list by model.transactionList.state.collectAsState()
    val selected = list.selection
    var targetCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showMenu by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val amountCurrency =
        state.accounts
            .map { it.account.currency }
            .distinct()
            .find { it.code == amountCode }
    val from = if (fromText.isBlank()) null else runCatching { LocalDate.parse(fromText) }.getOrNull()
    val through = if (throughText.isBlank()) null else runCatching { LocalDate.parse(throughText) }.getOrNull()
    val minimum = if (minimumText.isBlank()) null else runCatching { amountCurrency?.parseMinor(minimumText) }.getOrNull()
    val maximum = if (maximumText.isBlank()) null else runCatching { amountCurrency?.parseMinor(maximumText) }.getOrNull()
    val valid =
        (fromText.isBlank() || from != null) && (throughText.isBlank() || through != null) &&
            (minimumText.isBlank() || minimum != null) && (maximumText.isBlank() || maximum != null) &&
            ((minimumText.isBlank() && maximumText.isBlank()) || amountCurrency != null)
    val searchKey =
        listOf(filter, query, fromText, throughText, accountId, categoryId, tagId, kind, amountCode, minimumText, maximumText, valid)
    LaunchedEffect(searchKey, isActive) {
        if (isActive && valid && filter in setOf("posted", "trash")) {
            model.transactionList.search(
                LedgerFilter(
                    status = if (filter == "trash") EntryStatus.TRASH else EntryStatus.POSTED,
                    text = query,
                    from = from,
                    through = through,
                    accountId = accountId,
                    categoryId = categoryId,
                    tagId = tagId,
                    kind = kind?.let(EntryKind::valueOf),
                    amountCurrency = amountCurrency,
                    minimumMinor = minimum,
                    maximumMinor = maximum,
                ),
            )
        }
    }
    val isLedger = filter == "posted" || filter == "trash"
    val entries =
        if (valid &&
            isLedger
        ) {
            list.entries.filter {
                it.status ==
                    if (filter ==
                        "trash"
                    ) {
                        EntryStatus.TRASH
                    } else {
                        EntryStatus.POSTED
                    }
            }
        } else {
            emptyList()
        }
    val incompleteRecords = state.incompleteRecords
    val activeFilters =
        fromText.isNotBlank() || throughText.isNotBlank() || accountId != null || categoryId != null || tagId != null || kind != null ||
            amountCode != null ||
            minimumText.isNotBlank() ||
            maximumText.isNotBlank()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            stringResource(R.string.transactions),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "posted" to R.string.posted,
                "incomplete" to R.string.incomplete,
                "trash" to R.string.trash,
            ).forEach { (key, label) ->
                FilterChip(selected = filter == key, onClick = {
                    filter = key
                    model.transactionList.clearSelection()
                }, label = { Text(stringResource(label), maxLines = 1) })
            }
        }
        state.error?.let { error ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(error), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = model::refresh, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
            }
        }
        list.error?.let { Text(stringResource(failure(it)), color = MaterialTheme.colorScheme.error) }
        if (list.loading) CircularProgressIndicator()
        if (isLedger) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text(stringResource(R.string.search)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("field-${stringResource(R.string.search)}"),
                )
                TextButton(
                    onClick = {
                        keyboardController?.hide()
                        showFilters = true
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.filter)) }
                Box {
                    TextButton(
                        onClick = {
                            keyboardController?.hide()
                            showMenu = true
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text(stringResource(R.string.more)) }
                    DropdownMenu(showMenu, { showMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.templates)) }, onClick = {
                            showMenu = false
                            openTemplates()
                        })
                        if (filter ==
                            "posted"
                        ) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.bulk_category)) }, onClick = {
                                bulk = !bulk
                                model.transactionList.clearSelection()
                                showMenu = false
                            })
                        }
                    }
                }
            }
            if (!valid) Text(stringResource(R.string.correct_fields), color = MaterialTheme.colorScheme.error)
            if (activeFilters || query.isNotBlank()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(if (query.isNotBlank()) stringResource(R.string.searching, query) else stringResource(R.string.filters_active))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        query = ""
                        fromText = ""
                        throughText = ""
                        accountId = null
                        categoryId = null
                        tagId = null
                        kind = null
                        amountCode =
                            null
                        minimumText = ""
                        maximumText = ""
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reset)) }
                }
            }
        }
        if (filter == "incomplete") {
            if (incompleteRecords.isNotEmpty()) {
                Text(
                    stringResource(R.string.incomplete_not_posted),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                items(incompleteRecords, key = { it.id }) { record ->
                    val input = record.editor.input
                    val account = state.accounts.find { it.account.id == input.accountId }?.account
                    val rawAmountText = record.editor.amountText
                    val amountMinor = input.amountMinor
                    val amount =
                        when {
                            !rawAmountText.isNullOrBlank() && runCatching {
                                account?.currency?.parseMinor(rawAmountText)
                            }.getOrNull() == null -> stringResource(R.string.check_amount)

                            !rawAmountText.isNullOrBlank() -> "${account?.currency?.code.orEmpty()} $rawAmountText"

                            amountMinor != null -> "${account?.currency?.code.orEmpty()} ${account?.currency?.formatMinor(
                                amountMinor,
                            ).orEmpty()}"

                            else -> stringResource(R.string.amount_needed)
                        }
                    val receivedUnknown = input.kind == EntryKind.TRANSFER && input.receivedMinor == null
                    LedgerLine(
                        title =
                            input.merchant.ifBlank {
                                input.allocations
                                    .firstOrNull()
                                    ?.categoryId
                                    ?.let { id ->
                                        state.categories
                                            .find {
                                                it.id ==
                                                    id
                                            }?.name
                                    }.orEmpty()
                                    .ifBlank { account?.name ?: stringResource(R.string.new_transaction) }
                            },
                        amount = amount,
                        metadata = "${input.date} · ${account?.name.orEmpty()} · ${stringResource(R.string.incomplete)}",
                        detail =
                            if (receivedUnknown) {
                                stringResource(R.string.received_unknown)
                            } else {
                                input.merchantAmount?.let {
                                    "${stringResource(R.string.merchant_amount)} ${it.currency.code} ${it.currency.formatMinor(it.minor)}"
                                }
                            },
                        onClick = { openIncomplete(record.id) },
                    )
                    TextButton(
                        onClick = { model.deleteIncomplete(record.id, record.token) },
                    ) { Text(stringResource(R.string.delete_incomplete)) }
                }
                if (incompleteRecords.isEmpty()) {
                    item { EmptyLedger(stringResource(R.string.no_incomplete)) }
                }
            }
        } else {
            val selectedRows =
                entries
                    .flatMap { entry ->
                        entry.input.allocations.map { allocation ->
                            entry to allocation
                        }
                    }.filter { (entry, allocation) ->
                        selected.any { it.entryId == entry.id && it.allocationId == allocation.id }
                    }
            if (bulk && filter == "posted") {
                Text(stringResource(R.string.selected_rows, selectedRows.size))
                val currencies =
                    selectedRows.groupBy { (entry, _) ->
                        state.accounts
                            .find { it.account.id == entry.input.accountId }
                            ?.account
                            ?.currency
                    }
                currencies.forEach { (currency, rows) ->
                    val amount = runCatching { rows.fold(0L) { sum, (_, row) -> Math.addExact(sum, row.amountMinor) } }.getOrNull()
                    Text("${currency?.code.orEmpty()} ${amount?.let { currency?.formatMinor(it) } ?: "?"}")
                }
                val kinds =
                    selectedRows
                        .map {
                            if (it.first.input.kind ==
                                EntryKind.INCOME
                            ) {
                                CategoryKind.INCOME
                            } else {
                                CategoryKind.EXPENSE
                            }
                        }.distinct()
                val choices = state.categories.filter { !it.archived && (kinds.isEmpty() || it.kind in kinds) }
                Choice(
                    R.string.category,
                    state.categories.find { it.id == targetCategory }?.name ?: stringResource(R.string.uncategorized_expense),
                    listOf(null to stringResource(R.string.uncategorized_expense)) + choices.map { it.id to it.name },
                ) {
                    targetCategory =
                        it
                }
                Button(
                    onClick = {
                        model.bulkCategorize(
                            selectedRows.map { (entry, allocation) ->
                                selected.single { it.entryId == entry.id && it.allocationId == allocation.id }
                            },
                            targetCategory,
                        )
                    },
                    enabled = selectedRows.isNotEmpty() && kinds.size == 1 && !state.saving && !list.loading,
                    modifier =
                        Modifier.heightIn(
                            min = 48.dp,
                        ),
                ) {
                    Text(stringResource(R.string.apply_bulk_category))
                }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (!valid) {
                    item(key = "invalid-filter") { EmptyLedger(stringResource(R.string.invalid_filter)) }
                } else if (entries.isEmpty() && !list.loading) {
                    item(key = "empty-ledger") {
                        EmptyLedger(
                            when {
                                query.isNotBlank() || activeFilters -> stringResource(R.string.no_matches)
                                filter == "trash" -> stringResource(R.string.empty_trash)
                                else -> stringResource(R.string.no_posted)
                            },
                            if (query.isNotBlank() ||
                                activeFilters
                            ) {
                                stringResource(R.string.clear_filters)
                            } else if (filter ==
                                "posted"
                            ) {
                                stringResource(R.string.add)
                            } else {
                                null
                            },
                        ) {
                            if (query.isNotBlank() ||
                                activeFilters
                            ) {
                                query = ""
                                fromText = ""
                                throughText = ""
                                accountId = null
                                categoryId = null
                                tagId =
                                    null
                                kind = null
                                amountCode = null
                                minimumText = ""
                                maximumText = ""
                            } else {
                                addTransaction()
                            }
                        }
                    }
                } else {
                    val grouped = entries.groupBy { it.input.date }
                    grouped.forEach { (date, group) ->
                        item(key = "date-$date") {
                            Text(
                                dateHeading(date),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp),
                            )
                        }
                        items(group, key = { it.id }) { entry ->
                            val account = state.accounts.find { it.account.id == entry.input.accountId }?.account
                            val destination = state.accounts.find { it.account.id == entry.input.destinationAccountId }?.account
                            val uncategorizedName =
                                stringResource(
                                    if (entry.input.kind ==
                                        EntryKind.INCOME
                                    ) {
                                        R.string.uncategorized_income
                                    } else {
                                        R.string.uncategorized_expense
                                    },
                                )
                            val categoryNames =
                                entry.input.allocations
                                    .map { row ->
                                        val category = state.categories.find { it.id == row.categoryId }
                                        if (category == null) {
                                            uncategorizedName
                                        } else {
                                            val parent = state.categories.find { it.id == category.parentId }
                                            if (parent == null) category.name else "${parent.name} · ${category.name}"
                                        }
                                    }.distinct()
                            val mainAmount =
                                when (entry.input.kind) {
                                    EntryKind.TRANSFER -> "${account?.currency?.code.orEmpty()} −${account?.currency?.formatMinor(
                                        entry.input.amountMinor ?: 0,
                                    ).orEmpty()}"

                                    else -> "${account?.currency?.code.orEmpty()} ${entry.input.amountMinor?.let {
                                        if (it > 0) {
                                            "+${account?.currency?.formatMinor(
                                                it,
                                            )}"
                                        } else {
                                            account?.currency?.formatMinor(it)
                                        }
                                    } ?: stringResource(R.string.amount_needed)}"
                                }
                            val title =
                                when (entry.input.kind) {
                                    EntryKind.OPENING -> {
                                        stringResource(R.string.opening_balance_title)
                                    }

                                    EntryKind.ADJUSTMENT -> {
                                        stringResource(R.string.adjustment_title)
                                    }

                                    else -> {
                                        entry.input.merchant.ifBlank {
                                            categoryNames.firstOrNull() ?: account?.name ?: entry.input.kind.name
                                        }
                                    }
                                }
                            val metadata =
                                if (entry.input.kind == EntryKind.TRANSFER) {
                                    "${account?.name.orEmpty()} → ${destination?.name.orEmpty()} · ${stringResource(
                                        R.string.sent_received,
                                        mainAmount,
                                        "${destination?.currency?.code.orEmpty()} +${destination?.currency?.formatMinor(
                                            entry.input.receivedMinor ?: 0,
                                        ).orEmpty()}",
                                    )} · ${entry.input.time.toString().take(5)}"
                                } else {
                                    "${account?.name.orEmpty()} · ${categoryNames.joinToString().ifBlank {
                                        stringResource(
                                            R.string.category,
                                        )
                                    }} · ${entry.input.time.toString().take(5)}"
                                }
                            val extra =
                                entry.input.merchantAmount
                                    ?.takeIf {
                                        it.minor !=
                                            entry.input.amountMinor?.let { amount -> runCatching { Math.absExact(amount) }.getOrNull() }
                                    }?.let {
                                        "${stringResource(
                                            R.string.merchant_amount,
                                        )} ${it.currency.code} ${it.currency.formatMinor(it.minor)}"
                                    }
                                    ?: if (entry.input.allocations.size >
                                        1
                                    ) {
                                        stringResource(R.string.allocation_count, entry.input.allocations.size)
                                    } else {
                                        null
                                    }
                            Column {
                                LedgerLine(
                                    title,
                                    mainAmount,
                                    metadata,
                                    extra,
                                    rowTag = "posted-entry-${entry.id}",
                                ) { openEntry(entry.id) }
                                if (bulk && filter == "posted") {
                                    entry.input.allocations.forEach { allocation ->
                                        val checked = selected.any { it.entryId == entry.id && it.allocationId == allocation.id }
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("bulk-row-${entry.id}-${allocation.id}")
                                                .selectable(checked, role = Role.Checkbox) {
                                                    model.transactionList.toggle(entry, allocation.id)
                                                },
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Checkbox(checked, onCheckedChange = null)
                                            Text(
                                                "${categoryNames.firstOrNull() ?: stringResource(
                                                    R.string.uncategorized_expense,
                                                )} · ${account?.currency?.formatMinor(allocation.amountMinor).orEmpty()}",
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (list.next !=
                        null
                    ) {
                        item(key = "load-more") {
                            TextButton(
                                onClick = model.transactionList::loadMore,
                                enabled = !list.loading,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.load_more)) }
                        }
                    }
                }
            }
        }
    }
    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.filter_transactions), style = MaterialTheme.typography.titleLarge)
                DatePickerField(R.string.from_date, fromText) { fromText = it }
                DatePickerField(R.string.through_date, throughText) { throughText = it }
                Choice(
                    R.string.account_name,
                    state.accounts
                        .find { it.account.id == accountId }
                        ?.account
                        ?.name ?: stringResource(R.string.any_account),
                    listOf(null to stringResource(R.string.any_account)) + state.accounts.map { it.account.id to it.account.name },
                    useSheet = false,
                ) {
                    accountId =
                        it
                }
                Choice(
                    R.string.category,
                    state.categories.find { it.id == categoryId }?.name ?: stringResource(R.string.any_category),
                    listOf(null to stringResource(R.string.any_category)) + state.categories.map { it.id to it.name },
                    useSheet = false,
                ) { categoryId = it }
                Choice(
                    R.string.tags,
                    state.tags.find { it.id == tagId }?.name ?: stringResource(R.string.any_tag),
                    listOf(null to stringResource(R.string.any_tag)) + state.tags.map { it.id to it.name },
                    useSheet = false,
                ) { tagId = it }
                Choice(
                    R.string.transaction_kind,
                    kind ?: stringResource(R.string.any_kind),
                    listOf(null to stringResource(R.string.any_kind)) + EntryKind.entries.map { it.name to it.name },
                    useSheet = false,
                ) { kind = it }
                Choice(
                    R.string.amount_currency,
                    amountCurrency?.code ?: stringResource(R.string.any_currency),
                    listOf(null to stringResource(R.string.any_currency)) +
                        state.accounts
                            .map { it.account.currency }
                            .distinct()
                            .map { it.code to it.code },
                    useSheet = false,
                ) {
                    amountCode = it
                    minimumText =
                        ""
                    maximumText = ""
                }
                Field(R.string.minimum_amount, minimumText, { minimumText = it }, number = true)
                Field(R.string.maximum_amount, maximumText, { maximumText = it }, number = true)
                if (!valid) Text(stringResource(R.string.correct_fields), color = MaterialTheme.colorScheme.error)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        fromText = ""
                        throughText = ""
                        accountId = null
                        categoryId = null
                        tagId = null
                        kind = null
                        amountCode =
                            null
                        minimumText = ""
                        maximumText = ""
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.reset)) }
                    TextButton(onClick = {
                        showFilters = false
                    }, enabled = valid, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable internal fun LedgerLine(
    title: String,
    amount: String,
    metadata: String,
    detail: String?,
    rowTag: String? = null,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .let { modifier -> if (rowTag == null) modifier else modifier.testTag(rowTag) }
            .semantics(mergeDescendants = true) {
            }.padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(amount, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"))
        }
        Text(metadata, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable internal fun EmptyLedger(
    message: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        if (action != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

internal fun dateHeading(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> {
            "Today · ${date.format(
                java.time.format.DateTimeFormatter
                    .ofPattern("EEE, d MMM", java.util.Locale.getDefault()),
            )}"
        }

        today.minusDays(1) -> {
            "Yesterday"
        }

        else -> {
            date.format(
                java.time.format.DateTimeFormatter
                    .ofPattern("EEE, d MMM uuuu", java.util.Locale.getDefault()),
            )
        }
    }
}
