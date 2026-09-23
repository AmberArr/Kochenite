package moe.frost.kochenite

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
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
import moe.frost.kochenite.domain.ledger.AllocationSelection
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.Draft
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.EntryTemplate
import moe.frost.kochenite.domain.ledger.LedgerFilter
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.Money
import moe.frost.kochenite.domain.ledger.ReceiptLine
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.domain.ledger.Tag
import moe.frost.kochenite.domain.ledger.proportionalAllocations
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.Currency
import java.util.UUID

@Serializable internal data object OverviewRoute

@Serializable internal data object TransactionsRoute

@Serializable internal data object AccountsRoute

@Serializable internal data object ReportsRoute

@Serializable internal data object SettingsRoute

@Serializable internal data object CategoriesRoute

@Serializable internal data object TagsRoute

@Serializable internal data class TagRoute(
    val id: String? = null,
)

@Serializable internal data object TemplatesRoute

@Serializable internal data class TemplateSaveRoute(
    val entryId: String,
)

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
    val autofocusAmount: Boolean = false,
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

@Composable private fun Toggle(
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
private fun Choice(
    label: Int,
    selected: String,
    choices: List<Pair<String?, String>>,
    useSheet: Boolean = true,
    change: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val labelText = stringResource(label)
    Column {
        Text(labelText, style = MaterialTheme.typography.labelLarge)
        TextButton(
            onClick = {
                query = ""
                expanded = true
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("choice-$labelText"),
        ) { Text(selected) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
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

@Composable private fun EditorLayout(
    state: BookState,
    postEnabled: Boolean,
    onPost: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
            content()
            Spacer(Modifier.height(16.dp))
        }
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = onPost, enabled = postEnabled && !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.post))
            }
        }
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
            val returnedToTab =
                when (route) {
                    OverviewRoute -> nav.popBackStack<OverviewRoute>(false)
                    TransactionsRoute -> nav.popBackStack<TransactionsRoute>(false)
                    AccountsRoute -> nav.popBackStack<AccountsRoute>(false)
                    ReportsRoute -> nav.popBackStack<ReportsRoute>(false)
                    else -> false
                }
            if (returnedToTab) return
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
        val editorActive = editor != null && current?.destination?.hasRoute(EntryDraftRoute::class) == true
        var editorMenu by rememberSaveable { mutableStateOf(false) }
        Scaffold(
            topBar = {
                if (editorActive) {
                    TopAppBar(title = {
                        Text(
                            if (editor?.draft?.originalToken ==
                                null
                            ) {
                                stringResource(R.string.new_transaction)
                            } else {
                                stringResource(R.string.edit_transaction)
                            },
                        )
                    }, navigationIcon = {
                        TextButton(onClick = { model.closeEditor { nav.popBackStack() } }) { Text(stringResource(R.string.back)) }
                    }, actions = {
                        Box {
                            TextButton(
                                onClick = { editorMenu = true },
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) { Text(stringResource(R.string.more)) }
                            DropdownMenu(editorMenu, { editorMenu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.discard_draft)) }, onClick = {
                                    editorMenu = false
                                    model.discardEditor { nav.popBackStack() }
                                })
                            }
                        }
                    })
                } else {
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
                }
            },
            bottomBar = {
                if (!wide && !editorActive) {
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
                if (!editorActive) {
                    ExtendedFloatingActionButton(onClick = {
                        navigate(EntryDraftRoute(UUID.randomUUID().toString(), autofocusAmount = true))
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add)) }
                }
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
                if (wide && !editorActive) {
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
                            model,
                            current?.destination?.hasRoute(TransactionsRoute::class) == true,
                            { navigate(EntryDraftRoute(it)) },
                            { navigate(EntryDetailRoute(it)) },
                            { navigate(TemplatesRoute) },
                            { navigate(EntryDraftRoute(UUID.randomUUID().toString(), autofocusAmount = true)) },
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
                    composable<SettingsRoute> { SettingsForm(state, model, { navigate(CategoriesRoute) }, { navigate(TagsRoute) }) }
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
                    composable<TagsRoute> { TagsPanel(state) { navigate(TagRoute(it)) } }
                    composable<TagRoute> { TagForm(it.toRoute<TagRoute>().id, state, model) }
                    composable<TemplatesRoute> {
                        TemplatesPanel(state, model) { id ->
                            model.applyTemplate(id) { draftId -> navigate(EntryDraftRoute(draftId)) }
                        }
                    }
                    composable<TemplateSaveRoute> {
                        TemplateSaveForm(it.toRoute<TemplateSaveRoute>().entryId, detail, state, model)
                    }
                    composable<GroupsRoute> { GroupsPanel(state) { navigate(GroupRoute(it)) } }
                    composable<GroupRoute> { GroupForm(it.toRoute<GroupRoute>().id, state, model) }
                    composable<AdjustmentRoute> { AdjustmentForm(it.toRoute<AdjustmentRoute>().id, state, model) }
                    composable<EntryDraftRoute> {
                        val route = it.toRoute<EntryDraftRoute>()
                        val id = route.id
                        LaunchedEffect(id) { model.openDraft(id) }
                        EntryEditorForm(
                            id,
                            route.autofocusAmount,
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
                                    model.startLinkedRefund(purchase, allocationId) { draftId ->
                                        nav.navigate(EntryDraftRoute(draftId))
                                    }
                                }
                            },
                            { model.copyEntry(id) { draftId -> nav.navigate(EntryDraftRoute(draftId)) } },
                            { navigate(TemplateSaveRoute(id)) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionsPanel(
    state: BookState,
    model: BookViewModel,
    isActive: Boolean,
    openDraft: (String) -> Unit,
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
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
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
            model.search(
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
            state.searchEntries.filter {
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
    val drafts = if (filter == "incomplete") state.incompleteDrafts else state.drafts.filter { !it.incomplete }
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
                "draft" to R.string.drafts,
                "incomplete" to R.string.incomplete,
                "trash" to R.string.trash,
            ).forEach { (key, label) ->
                FilterChip(selected = filter == key, onClick = {
                    filter = key
                    selected = emptyList()
                }, label = { Text(stringResource(label), maxLines = 1) })
            }
        }
        state.error?.let { error ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(error), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = model::refresh, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.retry)) }
            }
        }
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
                                selected =
                                    emptyList()
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
        if (filter == "draft" || filter == "incomplete") {
            if (drafts.isNotEmpty()) {
                Text(
                    stringResource(
                        if (filter ==
                            "incomplete"
                        ) {
                            R.string.incomplete_not_posted
                        } else {
                            R.string.drafts_not_posted
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                items(drafts, key = { it.id }) { draft ->
                    val account = state.accounts.find { it.account.id == draft.input.accountId }?.account
                    val rawAmountText = draft.amountText
                    val amountMinor = draft.input.amountMinor
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
                    val receivedUnknown = draft.input.kind == EntryKind.TRANSFER && draft.input.receivedMinor == null
                    LedgerLine(
                        title =
                            draft.input.merchant.ifBlank {
                                draft.input.allocations
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
                        metadata = "${draft.input.date} · ${account?.name.orEmpty()} · ${if (draft.incomplete) {
                            stringResource(
                                R.string.incomplete,
                            )
                        } else {
                            stringResource(R.string.draft)
                        }}",
                        detail =
                            if (receivedUnknown) {
                                stringResource(R.string.received_unknown)
                            } else {
                                draft.input.merchantAmount?.let {
                                    "${stringResource(R.string.merchant_amount)} ${it.currency.code} ${it.currency.formatMinor(it.minor)}"
                                }
                            },
                        onClick = { openDraft(draft.id) },
                    )
                }
                if (drafts.isEmpty()) {
                    item {
                        EmptyLedger(
                            stringResource(
                                if (filter ==
                                    "incomplete"
                                ) {
                                    R.string.no_incomplete
                                } else {
                                    R.string.no_drafts
                                },
                            ),
                        )
                    }
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
                        selected.any { it.startsWith("${entry.id}|${allocation.id}|") }
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
                Button(onClick = {
                    model.bulkCategorize(
                        selected.map { row ->
                            val parts = row.split('|', limit = 3)
                            AllocationSelection(parts[0], parts[1], parts[2])
                        },
                        targetCategory,
                    )
                }, enabled = selected.isNotEmpty() && kinds.size == 1 && !state.saving, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.apply_bulk_category))
                }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (!valid) {
                    item(key = "invalid-filter") { EmptyLedger(stringResource(R.string.invalid_filter)) }
                } else if (entries.isEmpty()) {
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
                                        val key = "${entry.id}|${allocation.id}|${entry.token}"
                                        val checked = selected.any { it.startsWith("${entry.id}|${allocation.id}|") }
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("bulk-row-${entry.id}-${allocation.id}")
                                                .selectable(checked, role = Role.Checkbox) {
                                                    selected =
                                                        if (checked) {
                                                            selected.filterNot { it.startsWith("${entry.id}|${allocation.id}|") }
                                                        } else {
                                                            selected + key
                                                        }
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
                    if (state.searchNext !=
                        null
                    ) {
                        item(key = "load-more") {
                            TextButton(
                                onClick = model::loadMoreSearch,
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

@Composable private fun LedgerLine(
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

@Composable private fun EmptyLedger(
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

private fun dateHeading(date: LocalDate): String {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllocationDetailsDialog(
    draft: Draft,
    state: BookState,
    model: BookViewModel,
    close: () -> Unit,
) {
    var disableReceipt by rememberSaveable(draft.id) { mutableStateOf(false) }
    if (disableReceipt) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { disableReceipt = false },
            title = { Text(stringResource(R.string.remove_receipt_details)) },
            text = { Text(stringResource(R.string.remove_receipt_details_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    model.updateEditor { it.copy(lineTexts = emptyMap(), input = it.input.copy(receiptLines = emptyList())) }
                    disableReceipt = false
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = { TextButton(onClick = { disableReceipt = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    androidx.compose.ui.window.Dialog(
        onDismissRequest = close,
        properties =
            androidx.compose.ui.window
                .DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TextButton(onClick = close, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.done)) }
                    Text(stringResource(R.string.allocation_details), style = MaterialTheme.typography.titleLarge)
                }
                val account = state.accounts.find { it.account.id == draft.input.accountId }?.account
                val categories =
                    state.categories.filter {
                        !it.archived &&
                            it.kind == if (draft.input.kind == EntryKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
                    }
                val currency = draft.input.merchantAmount?.currency ?: account?.currency
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Toggle(R.string.receipt_details, draft.input.receiptLines.isNotEmpty()) { enabled ->
                        if (!enabled && draft.input.receiptLines.isNotEmpty()) {
                            disableReceipt = true
                        } else if (enabled) {
                            model.updateEditor { current ->
                                val expected =
                                    current.input.merchantAmount?.minor
                                        ?: current.input.amountMinor?.let { runCatching { Math.absExact(it) }.getOrNull() }
                                val suggested =
                                    expected?.let {
                                        proportionalAllocations(
                                            it,
                                            current.input.allocations.map { row ->
                                                row.amountMinor
                                            },
                                        )
                                    }
                                if (suggested !is LedgerResult.Success) {
                                    current
                                } else {
                                    current.copy(
                                        input =
                                            current.input.copy(
                                                receiptLines =
                                                    current.input.allocations.mapIndexed {
                                                        index,
                                                        row,
                                                        ->
                                                        ReceiptLine(row.id, "", suggested.value[index])
                                                    },
                                            ),
                                    )
                                }
                            }
                        }
                    }
                    draft.input.allocations.forEachIndexed { index, allocation ->
                        Text("${stringResource(R.string.allocation_row)} ${index + 1}", style = MaterialTheme.typography.titleMedium)
                        Choice(
                            R.string.category,
                            categories.find { it.id == allocation.categoryId }?.name
                                ?: stringResource(
                                    if (draft.input.kind ==
                                        EntryKind.INCOME
                                    ) {
                                        R.string.uncategorized_income
                                    } else {
                                        R.string.uncategorized_expense
                                    },
                                ),
                            listOf(
                                null to
                                    stringResource(
                                        if (draft.input.kind ==
                                            EntryKind.INCOME
                                        ) {
                                            R.string.uncategorized_income
                                        } else {
                                            R.string.uncategorized_expense
                                        },
                                    ),
                            ) +
                                categories.map { it.id to it.name },
                        ) { selected ->
                            model.updateEditor { current ->
                                current.copy(
                                    input =
                                        current.input.copy(
                                            allocations =
                                                current.input.allocations.map {
                                                    if (it.id ==
                                                        allocation.id
                                                    ) {
                                                        it.copy(categoryId = selected)
                                                    } else {
                                                        it
                                                    }
                                                },
                                        ),
                                )
                            }
                        }
                        Field(
                            R.string.allocation_amount,
                            draft.allocationTexts[allocation.id] ?: account?.currency?.formatMinor(allocation.amountMinor).orEmpty(),
                            { text ->
                                model.updateEditor { current ->
                                    val parsed = runCatching { account?.currency?.parseMinor(text) }.getOrNull() ?: 0L
                                    current.copy(
                                        allocationTexts = current.allocationTexts + (allocation.id to text),
                                        input =
                                            current.input.copy(
                                                allocations =
                                                    current.input.allocations.map {
                                                        if (it.id ==
                                                            allocation.id
                                                        ) {
                                                            it.copy(amountMinor = parsed)
                                                        } else {
                                                            it
                                                        }
                                                    },
                                            ),
                                    )
                                }
                            },
                            number = true,
                        )
                        val line = draft.input.receiptLines.find { it.id == allocation.id }
                        if (line != null) {
                            Field(R.string.receipt_description, line.description, { text ->
                                model.updateEditor { current ->
                                    current.copy(
                                        input =
                                            current.input.copy(
                                                receiptLines =
                                                    current.input.receiptLines.map {
                                                        if (it.id ==
                                                            line.id
                                                        ) {
                                                            it.copy(description = text)
                                                        } else {
                                                            it
                                                        }
                                                    },
                                            ),
                                    )
                                }
                            })
                            Field(
                                R.string.receipt_line_total,
                                draft.lineTexts[line.id] ?: currency?.formatMinor(line.merchantMinor).orEmpty(),
                                { text ->
                                    model.updateEditor { current ->
                                        val parsed = runCatching { currency?.parseMinor(text) }.getOrNull() ?: 0L
                                        val updated =
                                            current.copy(
                                                lineTexts = current.lineTexts + (line.id to text),
                                                input =
                                                    current.input.copy(
                                                        receiptLines =
                                                            current.input.receiptLines.map {
                                                                if (it.id ==
                                                                    line.id
                                                                ) {
                                                                    it.copy(merchantMinor = parsed)
                                                                } else {
                                                                    it
                                                                }
                                                            },
                                                    ),
                                            )
                                        val actual = updated.input.amountMinor?.let { runCatching { Math.absExact(it) }.getOrNull() }
                                        val shares =
                                            actual?.let {
                                                proportionalAllocations(
                                                    it,
                                                    updated.input.receiptLines.map { row ->
                                                        row.merchantMinor
                                                    },
                                                )
                                            }
                                        if (shares is LedgerResult.Success) {
                                            updated.copy(
                                                allocationTexts = emptyMap(),
                                                input =
                                                    updated.input.copy(
                                                        allocations =
                                                            updated.input.allocations.mapIndexed {
                                                                i,
                                                                row,
                                                                ->
                                                                row.copy(amountMinor = shares.value[i])
                                                            },
                                                    ),
                                            )
                                        } else {
                                            updated
                                        }
                                    }
                                },
                                number = true,
                            )
                        }
                        if (draft.input.allocations.size > 1) {
                            TextButton(onClick = {
                                model.updateEditor { current ->
                                    current.copy(
                                        allocationTexts = current.allocationTexts - allocation.id,
                                        lineTexts =
                                            current.lineTexts - allocation.id,
                                        input =
                                            current.input.copy(
                                                allocations =
                                                    current.input.allocations.filterNot {
                                                        it.id ==
                                                            allocation.id
                                                    },
                                                receiptLines = current.input.receiptLines.filterNot { it.id == allocation.id },
                                            ),
                                    )
                                }
                            }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.remove_row)) }
                        }
                    }
                    TextButton(onClick = {
                        model.updateEditor { current ->
                            val id = UUID.randomUUID().toString()
                            current.copy(
                                input =
                                    current.input.copy(
                                        allocations = current.input.allocations + Allocation(id, 0),
                                        receiptLines =
                                            if (current.input.receiptLines.isEmpty()) {
                                                emptyList()
                                            } else {
                                                current.input.receiptLines +
                                                    ReceiptLine(id, "", 0)
                                            },
                                    ),
                            )
                        }
                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.add_row)) }
                    val actual = draft.input.amountMinor?.let { runCatching { Math.absExact(it) }.getOrNull() }
                    val allocated =
                        runCatching {
                            draft.input.allocations.fold(
                                0L,
                            ) { total, row -> Math.addExact(total, row.amountMinor) }
                        }.getOrNull()
                    Text(
                        "${stringResource(
                            R.string.allocated_total,
                        )} · ${account?.currency?.code.orEmpty()} ${allocated?.let {
                            account?.currency?.formatMinor(
                                it,
                            )
                        } ?: "?"} / ${actual?.let { account?.currency?.formatMinor(it) } ?: "?"}",
                    )
                    if (draft.input.receiptLines.isNotEmpty()) {
                        val receiptTotal =
                            runCatching {
                                draft.input.receiptLines.fold(
                                    0L,
                                ) { total, line -> Math.addExact(total, line.merchantMinor) }
                            }.getOrNull()
                        val expected = draft.input.merchantAmount?.minor ?: actual
                        Text(
                            "${stringResource(
                                R.string.receipt_total,
                            )} · ${currency?.code.orEmpty()} ${receiptTotal?.let {
                                currency?.formatMinor(
                                    it,
                                )
                            } ?: "?"} / ${expected?.let {
                                currency?.formatMinor(
                                    it,
                                )
                            } ?: "?"} · ${if (receiptTotal == expected) {
                                stringResource(
                                    R.string.totals_match,
                                )
                            } else {
                                stringResource(R.string.totals_mismatch)
                            }}",
                        )
                    }
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
    copy: () -> Unit,
    saveTemplate: () -> Unit,
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
        entry.input.relatedEntryIds.forEach { relatedId ->
            val related = (state.entries + state.trashed).find { it.id == relatedId }
            Text(
                "${stringResource(
                    R.string.related_transactions,
                )} · ${related?.input?.merchant?.ifBlank { related.input.kind.name } ?: relatedId}",
            )
        }
        entry.input.allocations.forEach { allocation ->
            entry.input.receiptLines.find { it.id == allocation.id }?.let { line ->
                val lineCurrency = entry.input.merchantAmount?.currency ?: account?.currency
                Text("${line.description} · ${lineCurrency?.code.orEmpty()} ${lineCurrency?.formatMinor(line.merchantMinor).orEmpty()}")
            }
            Text(
                "${state.categories.find { it.id == allocation.categoryId }?.name ?: stringResource(
                    R.string.category,
                )} · ${account?.currency?.formatMinor(allocation.amountMinor).orEmpty()}",
            )
            if (entry.status == EntryStatus.POSTED && entry.input.kind == EntryKind.EXPENSE && allocation.amountMinor > 0 &&
                entry.input.amountMinor!! < 0
            ) {
                TextButton(onClick = { refundAllocation(allocation.id) }) { Text(stringResource(R.string.refund_allocation)) }
            }
        }
        if (entry.input.note.isNotBlank()) Text(entry.input.note)
        entry.input.tagIds.forEach { tagId -> state.tags.find { it.id == tagId }?.let { Text(it.name) } }
        if (entry.status == EntryStatus.POSTED && entry.input.kind !in setOf(EntryKind.OPENING, EntryKind.ADJUSTMENT)) {
            TextButton(onClick = copy) { Text(stringResource(R.string.copy_as_draft)) }
            TextButton(onClick = saveTemplate) { Text(stringResource(R.string.save_as_template)) }
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryEditorForm(
    id: String,
    autofocusAmount: Boolean,
    state: BookState,
    editor: EntryEditorState?,
    model: BookViewModel,
    discard: () -> Unit,
    saveIncomplete: () -> Unit,
) {
    val current = editor?.draft?.takeIf { it.id == id }
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val account = state.accounts.find { it.account.id == current.input.accountId }?.account
    val amountText = current.amountText ?: account?.currency?.let { current.input.amountMinor?.let(it::formatMinor) }.orEmpty()
    val dateText = current.dateText ?: current.input.date.toString()
    val timeText =
        current.timeText ?: current.input.time
            .toString()
            .take(5)
    var showDateTimePicker by rememberSaveable(id) { mutableStateOf(false) }
    var pickerShowsTime by rememberSaveable(id) { mutableStateOf(false) }

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
        val minor = runCatching { money?.parseMinor(text) }.getOrNull()?.takeIf { it != 0L }
        val allocatedMinor = minor?.let { runCatching { Math.absExact(it) }.getOrNull() }
        val first = draft.input.allocations.firstOrNull()
        val allocations =
            if (draft.input.kind == EntryKind.TRANSFER) {
                emptyList()
            } else {
                if (minor ==
                    null
                ) {
                    draft.input.allocations
                } else if (allocatedMinor == null) {
                    draft.input.allocations
                } else if (draft.input.receiptLines.isNotEmpty()) {
                    val proposed = proportionalAllocations(allocatedMinor, draft.input.receiptLines.map { it.merchantMinor })
                    if (proposed is LedgerResult.Success) {
                        draft.input.allocations.mapIndexed { index, row -> row.copy(amountMinor = proposed.value[index]) }
                    } else {
                        draft.input.allocations
                    }
                } else if (draft.input.allocations.size > 1) {
                    draft.input.allocations
                } else {
                    listOf(Allocation(first?.id ?: UUID.randomUUID().toString(), allocatedMinor, first?.categoryId))
                }
            }
        return draft.copy(
            amountText = text,
            allocationTexts =
                if (draft.input.receiptLines.isNotEmpty() || draft.input.allocations.size <= 1 ||
                    accountId != draft.input.accountId
                ) {
                    emptyMap()
                } else {
                    draft.allocationTexts
                },
            lineTexts = if (accountId != draft.input.accountId && draft.input.merchantAmount == null) emptyMap() else draft.lineTexts,
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
                    receiptLines = if (draft.input.kind == EntryKind.TRANSFER) emptyList() else draft.input.receiptLines,
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

    fun reallocateLines(draft: Draft): Draft {
        val charge = draft.input.amountMinor?.let { runCatching { Math.absExact(it) }.getOrNull() } ?: return draft
        val lines = draft.input.receiptLines
        if (lines.isEmpty()) return draft
        val proposed = proportionalAllocations(charge, lines.map { it.merchantMinor })
        if (proposed !is LedgerResult.Success) return draft
        val byId = lines.mapIndexed { index, line -> line.id to proposed.value[index] }.toMap()
        return draft.copy(
            allocationTexts = emptyMap(),
            input = draft.input.copy(allocations = draft.input.allocations.map { it.copy(amountMinor = byId[it.id] ?: it.amountMinor) }),
        )
    }

    var pendingKind by rememberSaveable(id) { mutableStateOf<String?>(null) }

    fun changeKind(kind: EntryKind) {
        model.updateEditor { draft ->
            val minor = draft.input.amountMinor
            val magnitude = minor?.let { runCatching { Math.absExact(it) }.getOrNull() }
            val allocations =
                if (kind == EntryKind.TRANSFER || magnitude == null) {
                    emptyList()
                } else {
                    listOf(
                        Allocation(
                            draft.input.allocations
                                .firstOrNull()
                                ?.id ?: UUID.randomUUID().toString(),
                            magnitude,
                        ),
                    )
                }
            val expectedCategory = if (kind == EntryKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            val oldCategory =
                draft.input.allocations
                    .singleOrNull()
                    ?.categoryId
            val preserveCategory = oldCategory?.takeIf { id -> state.categories.find { it.id == id }?.kind == expectedCategory }
            val normalizedAllocations = allocations.map { it.copy(categoryId = preserveCategory) }
            draft.copy(
                receivedText = if (kind == EntryKind.TRANSFER) draft.receivedText else null,
                allocationTexts = emptyMap(),
                lineTexts = emptyMap(),
                input =
                    draft.input.copy(
                        kind = kind,
                        destinationAccountId = if (kind == EntryKind.TRANSFER) draft.input.destinationAccountId else null,
                        receivedMinor = if (kind == EntryKind.TRANSFER) draft.input.receivedMinor else null,
                        allocations = normalizedAllocations,
                        receiptLines = emptyList(),
                    ),
            )
        }
        pendingKind = null
    }
    if (pendingKind != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingKind = null },
            title = { Text(stringResource(R.string.change_transaction_kind)) },
            text = { Text(stringResource(R.string.change_kind_warning)) },
            confirmButton = {
                TextButton(
                    onClick = { pendingKind?.let { changeKind(EntryKind.valueOf(it)) } },
                ) { Text(stringResource(R.string.continue_label)) }
            },
            dismissButton = { TextButton(onClick = { pendingKind = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    val amountFocus = remember(id) { FocusRequester() }
    LaunchedEffect(id, autofocusAmount) { if (autofocusAmount) amountFocus.requestFocus() }
    EditorLayout(
        state,
        current.input.amountMinor != null && (current.input.kind != EntryKind.TRANSFER || current.input.receivedMinor != null),
        model::confirmEditor,
    ) {
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
        Text(account?.currency?.code.orEmpty(), style = MaterialTheme.typography.labelLarge)
        Field(
            if (current.input.kind ==
                EntryKind.TRANSFER
            ) {
                R.string.sent_amount
            } else {
                R.string.amount
            },
            amountText,
            { text -> model.updateEditor { changeAmount(it, text) } },
            number = true,
            focusRequester = amountFocus,
        )
        Choice(
            R.string.transaction_kind,
            stringResource(
                if (current.input.kind ==
                    EntryKind.INCOME
                ) {
                    R.string.income
                } else if (current.input.kind ==
                    EntryKind.TRANSFER
                ) {
                    R.string.transfer
                } else {
                    R.string.expense
                },
            ),
            listOf(
                EntryKind.EXPENSE.name to stringResource(R.string.expense),
                EntryKind.INCOME.name to stringResource(R.string.income),
                EntryKind.TRANSFER.name to stringResource(R.string.transfer),
            ),
        ) { selected ->
            val kind = EntryKind.valueOf(selected ?: EntryKind.EXPENSE.name)
            if (kind != current.input.kind) {
                if (current.input.allocations.size > 1 ||
                    current.input.receiptLines.isNotEmpty()
                ) {
                    pendingKind = kind.name
                } else {
                    changeKind(kind)
                }
            }
        }
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
        var showAllocationDetails by rememberSaveable(id) { mutableStateOf(false) }
        if (current.input.kind != EntryKind.TRANSFER) {
            val categoryKind = if (current.input.kind == EntryKind.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            val uncategorized = if (categoryKind == CategoryKind.INCOME) R.string.uncategorized_income else R.string.uncategorized_expense
            val categories = state.categories.filter { !it.archived && it.kind == categoryKind }
            val allocation = current.input.allocations.singleOrNull()
            if (current.input.allocations.size <= 1 && current.input.receiptLines.isEmpty()) {
                Choice(
                    R.string.category,
                    categories.find { it.id == allocation?.categoryId }?.name ?: stringResource(uncategorized),
                    listOf(null to stringResource(uncategorized)) + categories.map { it.id to it.name },
                ) { selected ->
                    model.updateEditor { draft ->
                        val old = draft.input.allocations.singleOrNull()
                        val amount = draft.input.amountMinor?.let { runCatching { Math.absExact(it) }.getOrNull() } ?: 0L
                        val row = Allocation(old?.id ?: UUID.randomUUID().toString(), amount, selected)
                        draft.copy(input = draft.input.copy(allocations = listOf(row)))
                    }
                }
            } else {
                val currency = account?.currency
                val allocated =
                    runCatching {
                        current.input.allocations.fold(
                            0L,
                        ) { total, row -> Math.addExact(total, row.amountMinor) }
                    }.getOrNull()
                val actual = current.input.amountMinor?.let { runCatching { Math.absExact(it) }.getOrNull() }
                val receiptTotal =
                    runCatching {
                        current.input.receiptLines.fold(0L) { total, line -> Math.addExact(total, line.merchantMinor) }
                    }.getOrNull()
                val expectedReceipt = current.input.merchantAmount?.minor ?: actual
                val receiptStatus =
                    if (receiptTotal ==
                        expectedReceipt
                    ) {
                        stringResource(R.string.totals_match)
                    } else {
                        stringResource(R.string.totals_mismatch)
                    }
                TextButton(onClick = { showAllocationDetails = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                    Text(
                        "${stringResource(
                            R.string.allocation_details,
                        )} · ${current.input.allocations.size} · ${currency?.code.orEmpty()} ${allocated?.let {
                            currency?.formatMinor(
                                it,
                            )
                        } ?: "?"}/${actual?.let {
                            currency?.formatMinor(
                                it,
                            )
                        } ?: "?"}${if (current.input.receiptLines.isNotEmpty()) " · $receiptStatus" else ""}",
                    )
                }
            }
            if (current.input.allocations.size <= 1 && current.input.receiptLines.isEmpty()) {
                TextButton(onClick = {
                    showAllocationDetails = true
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.allocation_details)) }
            }
            if (showAllocationDetails) AllocationDetailsDialog(current, state, model) { showAllocationDetails = false }
        }

        val dateSummary =
            runCatching { LocalDate.parse(dateText) }.getOrNull()?.format(
                java.time.format.DateTimeFormatter
                    .ofPattern("EEE, d MMM uuuu", java.util.Locale.getDefault()),
            ) ?: dateText
        val timeSummary =
            runCatching { LocalTime.parse(timeText) }.getOrNull()?.format(
                java.time.format.DateTimeFormatter
                    .ofPattern("HH:mm", java.util.Locale.getDefault()),
            ) ?: timeText
        TextButton(
            onClick = { showDateTimePicker = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("transaction-date-time"),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.transaction_date_time), style = MaterialTheme.typography.labelLarge)
                Text("$dateSummary · $timeSummary", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (showDateTimePicker) {
            val initialDate = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: current.input.date
            val initialTime = runCatching { LocalTime.parse(timeText) }.getOrNull() ?: current.input.time
            val datePickerState =
                rememberDatePickerState(
                    initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    initialDisplayMode = DisplayMode.Picker,
                    yearRange = 1..9999,
                )
            val timePickerState = rememberTimePickerState(initialTime.hour, initialTime.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = {
                    showDateTimePicker = false
                    pickerShowsTime = false
                },
                title = { Text(stringResource(R.string.transaction_date_time)) },
                text = {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !pickerShowsTime,
                                onClick = { pickerShowsTime = false },
                                label = { Text(stringResource(R.string.date)) },
                            )
                            FilterChip(
                                selected = pickerShowsTime,
                                onClick = { pickerShowsTime = true },
                                label = { Text(stringResource(R.string.time)) },
                            )
                        }
                        if (pickerShowsTime) {
                            TimePicker(timePickerState)
                        } else {
                            DatePicker(datePickerState, showModeToggle = true)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val selectedDate =
                            datePickerState.selectedDateMillis?.let { millis ->
                                java.time.Instant
                                    .ofEpochMilli(millis)
                                    .atZone(ZoneOffset.UTC)
                                    .toLocalDate()
                            } ?: initialDate
                        val selectedTime = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        model.updateEditor { draft ->
                            draft.copy(
                                dateText = selectedDate.toString(),
                                timeText = selectedTime.toString().take(5),
                                input = draft.input.copy(date = selectedDate, time = selectedTime),
                            )
                        }
                        showDateTimePicker = false
                        pickerShowsTime = false
                    }) { Text(stringResource(R.string.done)) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showDateTimePicker = false
                        pickerShowsTime = false
                    }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }

        var moreOpen by rememberSaveable(id) {
            mutableStateOf(
                current.input.note.isNotBlank() || current.input.merchantAmount != null || current.input.tagIds.isNotEmpty() ||
                    current.input.relatedEntryIds.isNotEmpty(),
            )
        }
        TextButton(onClick = { moreOpen = !moreOpen }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            val selectedTagNames = current.input.tagIds.mapNotNull { id -> state.tags.find { it.id == id }?.name }
            val summary =
                listOfNotNull(
                    current.input.note
                        .takeIf { it.isNotBlank() }
                        ?.let { stringResource(R.string.notes) },
                    current.input.merchantAmount?.let { "${it.currency.code} ${it.currency.formatMinor(it.minor)}" },
                    selectedTagNames.takeIf { it.isNotEmpty() }?.joinToString(),
                    current.input.relatedEntryIds
                        .takeIf { it.isNotEmpty() }
                        ?.let { stringResource(R.string.related_count, it.size) },
                ).joinToString(" · ")
            Text(if (summary.isBlank()) stringResource(R.string.more_details) else "${stringResource(R.string.more_details)} · $summary")
        }
        if (moreOpen) {
            current.input.merchantAmount?.let {
                Text("${stringResource(R.string.merchant_amount)} context: ${it.currency.code} ${it.currency.formatMinor(it.minor)}")
            }
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
            Field(
                R.string.merchant_currency,
                merchantCodeText,
                { code -> model.updateEditor { changeMerchant(it, merchantAmountText, code) } },
            )
            var showTagPicker by rememberSaveable(id) { mutableStateOf(false) }
            val keyboardController = LocalSoftwareKeyboardController.current
            val selectedTags = current.input.tagIds.mapNotNull { tagId -> state.tags.find { it.id == tagId }?.name }
            TextButton(
                onClick = {
                    keyboardController?.hide()
                    showTagPicker = true
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(
                    if (selectedTags.isEmpty()) {
                        stringResource(R.string.tags)
                    } else {
                        "${stringResource(R.string.tags)} · ${selectedTags.joinToString()}"
                    },
                )
            }
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
                                            model.updateEditor { draft ->
                                                draft.copy(
                                                    input =
                                                        draft.input.copy(
                                                            tagIds =
                                                                if (checked) {
                                                                    draft.input.tagIds - tag.id
                                                                } else {
                                                                    draft.input.tagIds +
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

            var showRelatedPicker by rememberSaveable(id) { mutableStateOf(false) }
            var relatedQuery by rememberSaveable(id) { mutableStateOf("") }
            TextButton(onClick = { showRelatedPicker = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(
                    stringResource(R.string.related_transactions) +
                        if (current.input.relatedEntryIds.isEmpty()) "" else " · ${current.input.relatedEntryIds.size}",
                )
            }
            LaunchedEffect(showRelatedPicker, relatedQuery, current.input.relatedEntryIds) {
                if (showRelatedPicker) model.searchRelatedEntries(relatedQuery, current.input.relatedEntryIds)
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
                                state.relatedEntries + state.entries.filter { it.id in current.input.relatedEntryIds } +
                                    state.trashed.filter { it.id in current.input.relatedEntryIds }
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
                                            model.updateEditor { draft ->
                                                draft.copy(
                                                    input =
                                                        draft.input.copy(
                                                            relatedEntryIds =
                                                                if (checked) {
                                                                    draft.input.relatedEntryIds - related.id
                                                                } else {
                                                                    draft.input.relatedEntryIds + related.id
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
                                    TextButton(onClick = model::loadMoreRelatedEntries, modifier = Modifier.heightIn(min = 48.dp)) {
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
            Field(
                R.string.notes,
                current.input.note,
                { text -> model.updateEditor { it.copy(input = it.input.copy(note = text)) } },
                multiline = true,
            )
        }
        val unknown = current.input.amountMinor == null || (current.input.kind == EntryKind.TRANSFER && current.input.receivedMinor == null)
        if (unknown) {
            TextButton(onClick = saveIncomplete, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.save_incomplete))
            }
        }
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
    tags: () -> Unit,
) {
    var code by rememberSaveable { mutableStateOf(state.settings?.displayCurrency?.code ?: "") }
    Form(R.string.settings, state) {
        Field(R.string.display_currency, code, { code = it })
        Button(onClick = {
            currency(code)?.let(model::display) ?: model.error(R.string.currency_error)
        }, enabled = !state.saving) { Text(stringResource(R.string.save)) }
        TextButton(onClick = categories) { Text(stringResource(R.string.categories)) }
        TextButton(onClick = tags) { Text(stringResource(R.string.tags)) }
    }
}

@Composable private fun TagsPanel(
    state: BookState,
    edit: (String?) -> Unit,
) {
    Form(R.string.tags, state) {
        TextButton(onClick = { edit(null) }) { Text(stringResource(R.string.new_tag)) }
        state.tags.forEach { tag ->
            TextButton(onClick = { edit(tag.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text(tag.name) }
            if (tag.archived) Text(stringResource(R.string.archived))
        }
    }
}

@Composable private fun TagForm(
    id: String?,
    state: BookState,
    model: BookViewModel,
) {
    val old = remember(id) { state.tags.find { it.id == id } }
    val newId = rememberSaveable { UUID.randomUUID().toString() }
    var name by rememberSaveable(id) { mutableStateOf(old?.name ?: "") }
    var archived by rememberSaveable(id) { mutableStateOf(old?.archived ?: false) }
    Form(R.string.tags, state) {
        Field(R.string.tag_name, name, { name = it })
        if (old != null) Toggle(R.string.archived, archived) { archived = it }
        Button(onClick = { model.saveTag(Tag(old?.id ?: newId, name.trim(), archived), old) }, enabled = !state.saving) {
            Text(stringResource(R.string.save))
        }
    }
}

@Composable private fun TemplatesPanel(
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

@Composable private fun TemplateSaveForm(
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
