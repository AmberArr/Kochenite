package moe.frost.kochenite

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.AccountGroup
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.Draft
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.Money
import moe.frost.kochenite.domain.ledger.SavedEntry
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency
import java.util.UUID

@Serializable internal data object OverviewRoute

@Serializable internal data object TransactionsRoute

@Serializable internal data object AccountsRoute

@Serializable internal data object ReportsRoute

@Serializable internal data object SettingsRoute

@Serializable internal data object CategoriesRoute

@Serializable internal data object GroupsRoute

@Serializable internal data class AccountRoute(
    val id: String? = null,
)

@Serializable internal data class CategoryRoute(
    val id: String? = null,
)

@Serializable internal data class GroupRoute(
    val id: String? = null,
)

@Serializable internal data class AdjustmentRoute(
    val id: String,
)

@Serializable internal data class EntryDraftRoute(
    val id: String,
)

@Serializable internal data class EntryDetailRoute(
    val id: String,
)

private fun currency(code: String): BookCurrency? =
    try {
        Currency.getInstance(code.trim().uppercase()).let { BookCurrency(it.currencyCode, it.defaultFractionDigits) }
    } catch (_: IllegalArgumentException) {
        null
    }

@Composable private fun Field(
    label: Int,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
    number: Boolean = false,
) {
    val labelText = stringResource(label)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(labelText) },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("field-$labelText"),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Ascii else KeyboardType.Text),
    )
}

@Composable private fun Toggle(
    label: Int,
    checked: Boolean,
    change: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { change(!checked) }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Checkbox(checked, change)
        Text(stringResource(label), Modifier.padding(top = 12.dp))
    }
}

@Composable private fun Choice(
    label: Int,
    selected: String,
    choices: List<Pair<String?, String>>,
    change: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val labelText = stringResource(label)
    Column {
        Text(labelText, style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp).testTag("choice-$labelText")) { Text(selected) }
        DropdownMenu(expanded, { expanded = false }) {
            choices.forEach { (id, title) ->
                DropdownMenuItem(text = {
                    Text(
                        title,
                    )
                }, modifier = Modifier.testTag("choice-$labelText-option-${id ?: "none"}"), onClick = {
                    change(id)
                    expanded = false
                })
            }
        }
    }
}

@Composable private fun Form(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookScreen(
    state: BookState,
    model: BookViewModel,
) {
    BackHandler(enabled = state.saving) {}
    val nav = rememberNavController()
    val editor by model.editor.collectAsState()
    val detail by model.detail.collectAsState()
    BackHandler(enabled = editor != null) { model.closeEditor { nav.popBackStack() } }
    val current by nav.currentBackStackEntryAsState()
    LaunchedEffect(model) { model.saved.collect { if (nav.previousBackStackEntry != null) nav.popBackStack() } }
    if (state.loading) {
        Column(Modifier.fillMaxSize().padding(32.dp)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.loading))
        }
        return
    }
    if (!state.storageReady) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
            Column(Modifier.padding(padding).padding(24.dp)) {
                Text(stringResource(state.error ?: R.string.unavailable))
                TextButton(onClick = model::refresh) { Text(stringResource(R.string.retry)) }
            }
        }
        return
    }
    if (state.settings?.setupComplete != true) {
        Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
            Column(Modifier.padding(padding).consumeWindowInsets(padding).imePadding()) { SetupForm(state, model) }
        }
        return
    }
    val tabs =
        listOf(
            OverviewRoute to R.string.overview,
            TransactionsRoute to R.string.transactions,
            AccountsRoute to R.string.accounts,
            ReportsRoute to R.string.reports,
        )

    fun navigate(route: Any) {
        if (state.saving) return

        fun push() {
            model.clearError()
            nav.navigate(route) {
                launchSingleTop = true
                if (tabs.any { it.first == route }) {
                    popUpTo<OverviewRoute> { saveState = true }
                    restoreState = true
                }
            }
        }
        if (editor != null) model.closeEditor { push() } else push()
    }
    BoxWithConstraints {
        val wide = maxWidth >= 840.dp
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(stringResource(R.string.app_name)) }, navigationIcon = {
                    if (nav.previousBackStackEntry !=
                        null
                    ) {
                        TextButton(enabled = !state.saving, onClick = {
                            if (editor != null) model.closeEditor { nav.popBackStack() } else nav.popBackStack()
                            model.clearError()
                        }) { Text(stringResource(R.string.back)) }
                    }
                }, actions = { TextButton(onClick = { navigate(SettingsRoute) }) { Text(stringResource(R.string.settings)) } })
            },
            bottomBar = {
                if (!wide) {
                    NavigationBar {
                        tabs.forEachIndexed { index, (route, label) ->
                            NavigationBarItem(
                                modifier = Modifier.testTag("nav-$index"),
                                selected = current?.destination?.hasRoute(route::class) == true,
                                onClick = { navigate(route) },
                                icon = { TabIcon(index) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(onClick = {
                    navigate(EntryDraftRoute(UUID.randomUUID().toString()))
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add)) }
            },
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { padding ->
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    .imePadding(),
            ) {
                if (wide) {
                    NavigationRail {
                        tabs.forEachIndexed { index, (route, label) ->
                            NavigationRailItem(
                                modifier = Modifier.testTag("nav-$index"),
                                selected = current?.destination?.hasRoute(route::class) == true,
                                onClick = { navigate(route) },
                                icon = { TabIcon(index) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                }
                NavHost(nav, startDestination = OverviewRoute, modifier = Modifier.weight(1f)) {
                    composable<OverviewRoute> { OverviewPanel(state) }
                    composable<TransactionsRoute> {
                        TransactionsPanel(
                            state,
                            { navigate(EntryDraftRoute(it)) },
                            { navigate(EntryDetailRoute(it)) },
                        )
                    }
                    composable<ReportsRoute> { ReportPanel(state) }
                    composable<AccountsRoute> {
                        AccountsPanel(
                            state,
                            { navigate(AccountRoute(it)) },
                            { navigate(AccountRoute()) },
                            { navigate(GroupsRoute) },
                        )
                    }
                    composable<SettingsRoute> { SettingsForm(state, model, { navigate(CategoriesRoute) }) }
                    composable<AccountRoute> { entry ->
                        val id = entry.toRoute<AccountRoute>().id
                        Row {
                            if (wide) {
                                Column(Modifier.width(280.dp)) {
                                    AccountsPanel(
                                        state,
                                        { navigate(AccountRoute(it)) },
                                        { navigate(AccountRoute()) },
                                        { navigate(GroupsRoute) },
                                    )
                                }
                            }
                            Column(Modifier.weight(1f)) { AccountForm(id, state, model) { navigate(AdjustmentRoute(it)) } }
                        }
                    }
                    composable<CategoriesRoute> { CategoriesPanel(state) { navigate(CategoryRoute(it)) } }
                    composable<CategoryRoute> { CategoryForm(it.toRoute<CategoryRoute>().id, state, model) }
                    composable<GroupsRoute> { GroupsPanel(state) { navigate(GroupRoute(it)) } }
                    composable<GroupRoute> { GroupForm(it.toRoute<GroupRoute>().id, state, model) }
                    composable<AdjustmentRoute> { AdjustmentForm(it.toRoute<AdjustmentRoute>().id, state, model) }
                    composable<EntryDraftRoute> {
                        val id = it.toRoute<EntryDraftRoute>().id
                        LaunchedEffect(id) { model.openDraft(id) }
                        EntryEditorForm(
                            id,
                            state,
                            editor,
                            model,
                            { model.discardEditor { nav.popBackStack() } },
                            { model.saveIncompleteEditor { nav.popBackStack() } },
                        )
                    }
                    composable<EntryDetailRoute> {
                        val id = it.toRoute<EntryDetailRoute>().id
                        LaunchedEffect(id) { model.openDetail(id) }
                        EntryDetailForm(
                            id,
                            detail,
                            state,
                            model,
                            { draftId -> nav.navigate(EntryDraftRoute(draftId)) },
                            { allocationId ->
                                detail?.let { purchase ->
                                    model.startLinkedRefund(purchase, allocationId) { draftId -> nav.navigate(EntryDraftRoute(draftId)) }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable private fun TabIcon(index: Int) {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        when (index) {
            0 -> {
                drawRect(
                    color,
                    Offset(size.width * .2f, size.height * .4f),
                    androidx.compose.ui.geometry.Size(
                        size.width * .6f,
                        size.height * .5f,
                    ),
                    style = Stroke(stroke),
                )
                drawLine(color, Offset(size.width * .1f, size.height * .45f), Offset(size.width * .5f, size.height * .1f), stroke)
                drawLine(color, Offset(size.width * .5f, size.height * .1f), Offset(size.width * .9f, size.height * .45f), stroke)
            }

            1 -> {
                repeat(3) {
                    val y = size.height * (.25f + it * .25f)
                    drawLine(color, Offset(2f, y), Offset(size.width - 2f, y), stroke)
                }
            }

            2 -> {
                drawRect(
                    color,
                    Offset(2f, 5f),
                    androidx.compose.ui.geometry
                        .Size(size.width - 4f, size.height - 10f),
                    style = Stroke(stroke),
                )
            }

            else -> {
                repeat(3) {
                    val x =
                        size.width * (.25f + it * .25f)
                    drawLine(
                        color,
                        Offset(x, size.height * .9f),
                        Offset(x, size.height * (.6f - it * .2f)),
                        stroke * 2,
                    )
                }
            }
        }
    }
}

@Composable private fun SetupForm(
    state: BookState,
    model: BookViewModel,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var accountCode by rememberSaveable { mutableStateOf("") }
    var displayCode by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("0") }
    var starter by rememberSaveable { mutableStateOf(true) }
    val id = rememberSaveable { UUID.randomUUID().toString() }
    Form(R.string.setup_title, state) {
        Text(stringResource(R.string.setup_intro))
        Field(R.string.account_name, name, { name = it })
        Field(R.string.account_currency, accountCode, { accountCode = it })
        Text(stringResource(R.string.currency_hint), style = MaterialTheme.typography.bodySmall)
        Field(R.string.opening_balance, amount, { amount = it }, number = true)
        Field(R.string.display_currency, displayCode, { displayCode = it })
        Toggle(R.string.starter_categories, starter) { starter = it }
        Button(onClick = {
            val accountCurrency = currency(accountCode)
            val display = currency(displayCode)
            if (accountCurrency == null || display == null) {
                model.error(R.string.currency_error)
            } else {
                try {
                    model.setup(Account(id, name.trim(), accountCurrency), accountCurrency.parseMinor(amount), display, starter)
                } catch (
                    _: IllegalArgumentException,
                ) {
                    model.error(R.string.amount_error)
                } catch (_: ArithmeticException) {
                    model.error(R.string.amount_error)
                }
            }
        }, enabled = !state.saving, modifier = Modifier.heightIn(min = 48.dp).testTag("create-book")) {
            Text(stringResource(if (state.saving) R.string.saving else R.string.create_book))
        }
        if (state.error == R.string.unavailable ||
            state.error == R.string.recovery
        ) {
            TextButton(onClick = model::refresh) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable private fun AccountForm(
    id: String?,
    state: BookState,
    model: BookViewModel,
    adjust: (String) -> Unit,
) {
    val old = remember(id) { state.accounts.find { it.account.id == id }?.account }
    val newId = rememberSaveable { UUID.randomUUID().toString() }
    var name by rememberSaveable(id) { mutableStateOf(old?.name ?: "") }
    var code by rememberSaveable(id) { mutableStateOf(old?.currency?.code ?: "") }
    var opening by rememberSaveable(id) { mutableStateOf("0") }
    var type by rememberSaveable(id) { mutableStateOf(old?.type ?: "") }
    var order by rememberSaveable(id) { mutableStateOf((old?.position ?: 0).toString()) }
    var group by rememberSaveable(id) { mutableStateOf(old?.groupId) }
    var include by rememberSaveable(id) { mutableStateOf(old?.includeInNetWorth ?: true) }
    var archived by rememberSaveable(id) { mutableStateOf(old?.archived ?: false) }
    Form(if (old == null) R.string.new_account else R.string.edit_account, state) {
        Field(R.string.account_name, name, { name = it })
        Field(R.string.account_currency, code, { code = it }, enabled = old?.currencyLocked != true)
        if (old?.currencyLocked == true) Text(stringResource(R.string.currency_locked))
        if (old == null) Field(R.string.opening_balance, opening, { opening = it }, number = true)
        Field(R.string.account_type, type, { type = it })
        Field(R.string.position, order, { order = it }, number = true)
        Choice(
            R.string.group,
            state.groups.find { it.id == group }?.name ?: stringResource(R.string.no_group),
            listOf(null to stringResource(R.string.no_group)) + state.groups.map { it.id to it.name },
        ) { group = it }
        Toggle(R.string.include_net_worth, include) { include = it }
        if (old != null) Toggle(R.string.archived, archived) { archived = it }
        Button(onClick = {
            val curr = currency(code)
            if (curr == null) {
                model.error(R.string.currency_error)
            } else {
                try {
                    val candidate =
                        Account(
                            old?.id ?: newId,
                            name.trim(),
                            curr,
                            archived,
                            include,
                            old?.currencyLocked ?: false,
                            group,
                            type.trim(),
                            order.toInt(),
                        )
                    model.saveAccount(candidate, old, if (old == null) curr.parseMinor(opening) else null)
                } catch (
                    _: IllegalArgumentException,
                ) {
                    model.error(R.string.correct_fields)
                } catch (_: ArithmeticException) {
                    model.error(R.string.amount_error)
                }
            }
        }, enabled = !state.saving, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(if (state.saving) R.string.saving else R.string.save))
        }
        if (old != null && !old.archived) TextButton(onClick = { adjust(old.id) }) { Text(stringResource(R.string.adjust_balance)) }
    }
}

@Composable private fun OverviewPanel(state: BookState) {
    Form(R.string.overview, state) {
        Text(stringResource(R.string.net_worth), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.currency_totals_hint))
        val incompleteCount = state.incompleteCount
        if (incompleteCount > 0) Text(stringResource(R.string.incomplete_count, incompleteCount))
        state.accounts.filter { it.account.includeInNetWorth }.groupBy { it.account.currency }.forEach { (currency, balances) ->
            val total = runCatching { balances.fold(0L) { total, row -> Math.addExact(total, row.balanceMinor) } }.getOrNull()
            Text(
                if (total ==
                    null
                ) {
                    stringResource(R.string.overflow_error)
                } else {
                    "${currency.code} ${currency.formatMinor(total)}"
                },
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        HorizontalDivider()
        Text(stringResource(R.string.accounts), style = MaterialTheme.typography.titleLarge)
        state.accounts.forEach {
            Text(
                "${it.account.name}  ${it.account.currency.code} ${it.account.currency.formatMinor(it.balanceMinor)}",
            )
            val affected = state.affectedIncomplete[it.account.id] ?: 0
            if (affected > 0) Text(stringResource(R.string.incomplete_account_count, affected), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun AccountsPanel(
    state: BookState,
    edit: (String) -> Unit,
    add: () -> Unit,
    groups: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(stringResource(R.string.accounts), style = MaterialTheme.typography.headlineSmall) }
        item {
            TextButton(onClick = add) { Text(stringResource(R.string.add_account)) }
            TextButton(onClick = groups) { Text(stringResource(R.string.groups)) }
        }
        items(state.accounts, key = { it.account.id }) { row ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clickable { edit(row.account.id) }
                    .padding(8.dp),
            ) {
                Text(row.account.name, style = MaterialTheme.typography.titleMedium)
                Text("${row.account.currency.code} ${row.account.currency.formatMinor(row.balanceMinor)}")
                val affected = state.affectedIncomplete[row.account.id] ?: 0
                if (affected >
                    0
                ) {
                    Text(stringResource(R.string.incomplete_account_count, affected), style = MaterialTheme.typography.bodySmall)
                }
                state.groups.find { it.id == row.account.groupId }?.let { Text(it.name) }
                if (row.account.archived) Text(stringResource(R.string.archived))
            }
            HorizontalDivider()
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable private fun TransactionsPanel(
    state: BookState,
    openDraft: (String) -> Unit,
    openEntry: (String) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf("posted") }
    Form(R.string.transactions, state) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "posted" to R.string.posted,
                "draft" to R.string.drafts,
                "incomplete" to R.string.incomplete,
                "trash" to R.string.trash,
            ).forEach { (key, label) ->
                TextButton(onClick = { filter = key }) { Text(stringResource(label)) }
            }
        }
        if (filter == "draft" || filter == "incomplete") {
            val drafts = if (filter == "incomplete") state.incompleteDrafts else state.drafts.filter { !it.incomplete }
            drafts.forEach { draft ->
                TextButton(onClick = { openDraft(draft.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("${draft.input.date} • ${draft.input.merchant.ifBlank { draft.input.kind.name }}")
                }
            }
        } else {
            val entries = if (filter == "trash") state.trashed else state.entries
            if (entries.isEmpty()) Text(stringResource(R.string.no_entries))
            entries.forEach { entry ->
                val account = state.accounts.find { it.account.id == entry.input.accountId }?.account
                TextButton(onClick = { openEntry(entry.id) }, modifier = Modifier.heightIn(min = 64.dp)) {
                    Text(
                        "${entry.input.date} • ${entry.input.merchant.ifBlank {
                            account?.name.orEmpty()
                        }} • ${account?.currency?.code.orEmpty()} ${account?.currency?.formatMinor(
                            entry.input.amountMinor ?: 0,
                        ).orEmpty()}",
                    )
                }
            }
        }
    }
}

@Composable private fun EntryDetailForm(
    id: String,
    entry: SavedEntry?,
    state: BookState,
    model: BookViewModel,
    edit: (String) -> Unit,
    refundAllocation: (String) -> Unit,
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
        entry.input.purchaseId?.let { Text(stringResource(R.string.linked_refund)) }
        entry.input.allocations.forEach { allocation ->
            Text(
                "${state.categories.find { it.id == allocation.categoryId }?.name ?: stringResource(
                    R.string.category,
                )} · ${account?.currency?.formatMinor(allocation.amountMinor).orEmpty()}",
            )
            if (entry.status == EntryStatus.POSTED && entry.input.kind == EntryKind.EXPENSE) {
                TextButton(onClick = { refundAllocation(allocation.id) }) { Text(stringResource(R.string.refund_allocation)) }
            }
        }
        if (entry.input.note.isNotBlank()) Text(entry.input.note)
        if (entry.status == EntryStatus.POSTED) {
            if (entry.input.kind !in setOf(EntryKind.OPENING, EntryKind.ADJUSTMENT)) {
                TextButton(onClick = { model.startEdit(entry.id, edit) }) { Text(stringResource(R.string.edit_transaction)) }
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

@Composable private fun EntryEditorForm(
    id: String,
    state: BookState,
    editor: EntryEditorState?,
    model: BookViewModel,
    discard: () -> Unit,
    saveIncomplete: () -> Unit,
) {
    val current = editor?.draft?.takeIf { it.id == id }
    if (current == null) {
        Form(R.string.new_transaction, state) { CircularProgressIndicator() }
        return
    }
    val account = state.accounts.find { it.account.id == current.input.accountId }?.account
    val amountText = current.amountText ?: account?.currency?.let { current.input.amountMinor?.let(it::formatMinor) }.orEmpty()
    val dateText = current.dateText ?: current.input.date.toString()
    val timeText =
        current.timeText ?: current.input.time
            .toString()
            .take(5)

    fun changeAmount(
        draft: Draft,
        text: String,
        accountId: String = draft.input.accountId,
    ): Draft {
        val money =
            state.accounts
                .find { it.account.id == accountId }
                ?.account
                ?.currency
        val minor = runCatching { money?.parseMinor(text) }.getOrNull()?.takeIf { it > 0 }
        val first = draft.input.allocations.firstOrNull()
        val allocations =
            if (draft.input.kind == EntryKind.TRANSFER) {
                emptyList()
            } else {
                if (minor ==
                    null
                ) {
                    emptyList()
                } else {
                    listOf(Allocation(first?.id ?: UUID.randomUUID().toString(), minor, first?.categoryId))
                }
            }
        return draft.copy(
            amountText = text,
            receivedText =
                if (draft.input.kind == EntryKind.TRANSFER &&
                    state.accounts
                        .find { it.account.id == draft.input.destinationAccountId }
                        ?.account
                        ?.currency == money
                ) {
                    text
                } else {
                    draft.receivedText
                },
            input =
                draft.input.copy(
                    accountId = accountId,
                    amountMinor = minor,
                    allocations = allocations,
                    receivedMinor =
                        if (draft.input.kind == EntryKind.TRANSFER &&
                            state.accounts
                                .find { it.account.id == draft.input.destinationAccountId }
                                ?.account
                                ?.currency == money
                        ) {
                            minor
                        } else {
                            draft.input.receivedMinor
                        },
                ),
        )
    }

    Form(R.string.new_transaction, state) {
        Text(
            stringResource(
                if (editor.error != null && !editor.saved) {
                    R.string.draft_save_failed
                } else if (editor.saving || !editor.saved) {
                    R.string.saving
                } else {
                    R.string.saved
                },
            ),
        )
        editor.error?.let {
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
        Choice(
            R.string.transaction_kind,
            stringResource(
                when (current.input.kind) {
                    EntryKind.INCOME -> R.string.income
                    EntryKind.REFUND -> R.string.refund
                    EntryKind.TRANSFER -> R.string.transfer
                    else -> R.string.expense
                },
            ),
            listOf(
                EntryKind.EXPENSE.name to stringResource(R.string.expense),
                EntryKind.INCOME.name to stringResource(R.string.income),
                EntryKind.REFUND.name to stringResource(R.string.refund),
                EntryKind.TRANSFER.name to stringResource(R.string.transfer),
            ),
        ) { selected ->
            val kind = EntryKind.valueOf(selected ?: EntryKind.EXPENSE.name)
            model.updateEditor { draft ->
                val minor = draft.input.amountMinor
                val allocations =
                    if (kind == EntryKind.TRANSFER || minor == null) {
                        emptyList()
                    } else {
                        listOf(
                            Allocation(
                                draft.input.allocations
                                    .firstOrNull()
                                    ?.id ?: UUID.randomUUID().toString(),
                                minor,
                            ),
                        )
                    }
                draft.copy(
                    receivedText = if (kind == EntryKind.TRANSFER) draft.receivedText else null,
                    input =
                        draft.input.copy(
                            kind = kind,
                            destinationAccountId = if (kind == EntryKind.TRANSFER) draft.input.destinationAccountId else null,
                            receivedMinor = if (kind == EntryKind.TRANSFER) draft.input.receivedMinor else null,
                            purchaseId = if (kind == EntryKind.REFUND) draft.input.purchaseId else null,
                            allocations = allocations,
                        ),
                )
            }
        }
        Field(R.string.amount, amountText, { text -> model.updateEditor { changeAmount(it, text) } }, number = true)
        Choice(
            R.string.account_name,
            account?.name ?: stringResource(R.string.select_account),
            state.accounts.filter { !it.account.archived }.map { it.account.id to "${it.account.name} · ${it.account.currency.code}" },
        ) { selected -> if (selected != null) model.updateEditor { changeAmount(it, amountText, selected) } }
        if (current.input.kind == EntryKind.TRANSFER) {
            val destination = state.accounts.find { it.account.id == current.input.destinationAccountId }?.account
            val receivedText =
                current.receivedText ?: destination?.currency?.let { current.input.receivedMinor?.let(it::formatMinor) }.orEmpty()
            Choice(
                R.string.destination_account,
                destination?.name ?: stringResource(R.string.select_account),
                state.accounts
                    .filter { !it.account.archived && it.account.id != current.input.accountId }
                    .map { it.account.id to "${it.account.name} · ${it.account.currency.code}" },
            ) { selected ->
                model.updateEditor { draft ->
                    val chosen = state.accounts.find { it.account.id == selected }?.account
                    val text = if (chosen?.currency == account?.currency) amountText else draft.receivedText.orEmpty()
                    val minor = runCatching { chosen?.currency?.parseMinor(text) }.getOrNull()?.takeIf { it > 0 }
                    draft.copy(receivedText = text, input = draft.input.copy(destinationAccountId = selected, receivedMinor = minor))
                }
            }
            Field(R.string.received_amount, receivedText, { text ->
                model.updateEditor { draft ->
                    val minor = runCatching { destination?.currency?.parseMinor(text) }.getOrNull()?.takeIf { it > 0 }
                    draft.copy(receivedText = text, input = draft.input.copy(receivedMinor = minor))
                }
            }, number = true)
        }
        Field(
            R.string.merchant_payee,
            current.input.merchant,
            { text -> model.updateEditor { it.copy(input = it.input.copy(merchant = text)) } },
        )
        val merchantCodeText =
            current.merchantCurrencyText ?: current.input.merchantAmount
                ?.currency
                ?.code
                .orEmpty()
        val merchantAmountText =
            current.merchantAmountText ?: current.input.merchantAmount
                ?.let { it.currency.formatMinor(it.minor) }
                .orEmpty()

        fun changeMerchant(
            draft: Draft,
            text: String,
            code: String,
        ): Draft {
            val merchantCurrency = currency(code)
            val minor = runCatching { merchantCurrency?.parseMinor(text) }.getOrNull()?.takeIf { it > 0 }
            val money = if (merchantCurrency != null && minor != null) Money(minor, merchantCurrency) else null
            return draft.copy(merchantAmountText = text, merchantCurrencyText = code, input = draft.input.copy(merchantAmount = money))
        }
        Field(R.string.merchant_amount, merchantAmountText, { text ->
            model.updateEditor {
                changeMerchant(it, text, merchantCodeText)
            }
        }, number = true)
        Field(R.string.merchant_currency, merchantCodeText, { code -> model.updateEditor { changeMerchant(it, merchantAmountText, code) } })
        Field(R.string.transaction_date, dateText, { text ->
            model.updateEditor { draft ->
                val parsed = runCatching { LocalDate.parse(text) }.getOrNull()
                draft.copy(dateText = text, input = draft.input.copy(date = parsed ?: draft.input.date))
            }
        })
        Field(R.string.transaction_time, timeText, { text ->
            model.updateEditor { draft ->
                val parsed = runCatching { LocalTime.parse(text) }.getOrNull()
                draft.copy(timeText = text, input = draft.input.copy(time = parsed ?: draft.input.time))
            }
        })
        if (current.input.kind != EntryKind.TRANSFER) {
            val categoryKind = if (current.input.kind == EntryKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            val uncategorized = if (categoryKind == CategoryKind.INCOME) R.string.uncategorized_income else R.string.uncategorized_expense
            val categories = state.categories.filter { !it.archived && it.kind == categoryKind }
            val categoryId =
                current.input.allocations
                    .firstOrNull()
                    ?.categoryId
            Choice(
                R.string.category,
                categories.find { it.id == categoryId }?.name ?: stringResource(uncategorized),
                listOf(null to stringResource(uncategorized)) + categories.map { it.id to it.name },
            ) { selected ->
                model.updateEditor { draft ->
                    draft.copy(input = draft.input.copy(allocations = draft.input.allocations.map { it.copy(categoryId = selected) }))
                }
            }
        }
        Field(R.string.notes, current.input.note, { text -> model.updateEditor { it.copy(input = it.input.copy(note = text)) } })
        val unknown = current.input.amountMinor == null || (current.input.kind == EntryKind.TRANSFER && current.input.receivedMinor == null)
        if (unknown) {
            TextButton(
                onClick = saveIncomplete,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.save_incomplete)) }
        }
        Button(onClick = model::confirmEditor, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.post)) }
        TextButton(onClick = discard, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.discard_draft)) }
    }
}

@Composable private fun ReportPanel(state: BookState) {
    Form(R.string.reports, state) {
        Text(stringResource(R.string.month_to_date), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.currency_totals_hint))
        val incompleteCount = state.incompleteCount
        if (incompleteCount > 0) Text(stringResource(R.string.incomplete_count, incompleteCount))
        if (state.totals.isEmpty()) Text(stringResource(R.string.no_report_activity))
        state.totals.forEach { total ->
            Text(total.currency.code, style = MaterialTheme.typography.titleLarge)
            Text("${stringResource(R.string.income)}: ${total.currency.formatMinor(total.incomeMinor)}")
            Text("${stringResource(R.string.spending)}: ${total.currency.formatMinor(total.spendingMinor)}")
            Text("${stringResource(R.string.net_flow)}: ${total.currency.formatMinor(total.netFlowMinor)}")
        }
    }
}

@Composable private fun SettingsForm(
    state: BookState,
    model: BookViewModel,
    categories: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf(state.settings?.displayCurrency?.code ?: "") }
    Form(R.string.settings, state) {
        Field(R.string.display_currency, code, { code = it })
        Button(onClick = {
            currency(code)?.let(model::display) ?: model.error(R.string.currency_error)
        }, enabled = !state.saving) { Text(stringResource(R.string.save)) }
        TextButton(onClick = categories) { Text(stringResource(R.string.categories)) }
    }
}

@Composable private fun CategoriesPanel(
    state: BookState,
    edit: (String?) -> Unit,
) {
    Form(R.string.categories, state) {
        TextButton(onClick = { edit(null) }) { Text(stringResource(R.string.new_category)) }
        Text(stringResource(R.string.uncategorized_income))
        Text(stringResource(R.string.uncategorized_expense))
        Text(stringResource(R.string.uncategorized_hint), style = MaterialTheme.typography.bodySmall)
        state.categories.sortedBy { it.parentId ?: it.id }.forEach { category ->
            TextButton(onClick = { edit(category.id) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(listOfNotNull(state.categories.find { it.id == category.parentId }?.name, category.name).joinToString(" / "))
            }
            if (category.archived) Text(stringResource(R.string.archived))
        }
    }
}

@Composable private fun CategoryForm(
    id: String?,
    state: BookState,
    model: BookViewModel,
) {
    val old = remember(id) { state.categories.find { it.id == id } }
    val newId = rememberSaveable { UUID.randomUUID().toString() }
    var name by rememberSaveable(id) { mutableStateOf(old?.name ?: "") }
    var kind by rememberSaveable(id) { mutableStateOf(old?.kind?.name ?: CategoryKind.EXPENSE.name) }
    var parent by rememberSaveable(id) { mutableStateOf(old?.parentId) }
    var archived by rememberSaveable(id) { mutableStateOf(old?.archived ?: false) }
    Form(R.string.categories, state) {
        Field(R.string.category_name, name, { name = it })
        Choice(
            R.string.category_kind,
            stringResource(if (kind == CategoryKind.INCOME.name) R.string.income else R.string.expense),
            listOf(
                CategoryKind.EXPENSE.name to stringResource(R.string.expense),
                CategoryKind.INCOME.name to stringResource(R.string.income),
            ),
        ) {
            kind = it!!
            parent = null
        }
        Choice(
            R.string.category_parent,
            state.categories.find { it.id == parent }?.name ?: stringResource(R.string.no_parent),
            listOf(null to stringResource(R.string.no_parent)) +
                state.categories.filter { it.parentId == null && it.id != id && it.kind.name == kind && !it.archived }.map {
                    it.id to
                        it.name
                },
        ) { parent = it }
        if (old != null) Toggle(R.string.archived, archived) { archived = it }
        Button(onClick = {
            model.saveCategory(Category(old?.id ?: newId, name.trim(), CategoryKind.valueOf(kind), parent, archived), old)
        }, enabled = !state.saving) { Text(stringResource(R.string.save)) }
    }
}

@Composable private fun GroupsPanel(
    state: BookState,
    edit: (String?) -> Unit,
) {
    Form(R.string.groups, state) {
        TextButton(onClick = { edit(null) }) { Text(stringResource(R.string.new_group)) }
        state.groups.forEach { group -> TextButton(onClick = { edit(group.id) }) { Text(group.name) } }
    }
}

@Composable private fun GroupForm(
    id: String?,
    state: BookState,
    model: BookViewModel,
) {
    val old = remember(id) { state.groups.find { it.id == id } }
    val newId = rememberSaveable { UUID.randomUUID().toString() }
    var name by rememberSaveable(id) { mutableStateOf(old?.name ?: "") }
    var order by rememberSaveable(id) { mutableStateOf((old?.position ?: 0).toString()) }
    Form(R.string.groups, state) {
        Field(R.string.group_name, name, { name = it })
        Field(R.string.position, order, { order = it }, number = true)
        Button(onClick = {
            order.toIntOrNull()?.let { model.saveGroup(AccountGroup(old?.id ?: newId, name.trim(), it), old) }
                ?: model.error(R.string.correct_fields)
        }, enabled = !state.saving) { Text(stringResource(R.string.save)) }
    }
}

@Composable private fun AdjustmentForm(
    id: String,
    state: BookState,
    model: BookViewModel,
) {
    val account = state.accounts.find { it.account.id == id }?.account ?: return
    var amount by rememberSaveable(id) { mutableStateOf("") }
    var note by rememberSaveable(id) { mutableStateOf("") }
    Form(R.string.adjust_balance, state) {
        Text(account.name, style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.adjustment_hint))
        Field(R.string.adjustment_amount, amount, { amount = it }, number = true)
        Field(R.string.notes, note, { note = it })
        Button(onClick = {
            try {
                model.adjust(id, account.currency.parseMinor(amount), note)
            } catch (
                _: IllegalArgumentException,
            ) {
                model.error(R.string.amount_error)
            } catch (_: ArithmeticException) {
                model.error(R.string.amount_error)
            }
        }, enabled = !state.saving) { Text(stringResource(R.string.save)) }
    }
}
