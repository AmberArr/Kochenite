package moe.frost.kochenite.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.R
import moe.frost.kochenite.accounts.AccountFormScreen
import moe.frost.kochenite.accounts.AccountsScreen
import moe.frost.kochenite.accounts.GroupsScreen
import moe.frost.kochenite.overview.OverviewScreen
import moe.frost.kochenite.reports.ReportsScreen
import moe.frost.kochenite.settings.CategoriesScreen
import moe.frost.kochenite.settings.CategoryFormScreen
import moe.frost.kochenite.settings.SettingsScreen
import moe.frost.kochenite.settings.TagsScreen
import moe.frost.kochenite.setup.SetupScreen
import moe.frost.kochenite.templates.TemplateSaveScreen
import moe.frost.kochenite.templates.TemplatesScreen
import moe.frost.kochenite.transactions.detail.EntryDetailScreen
import moe.frost.kochenite.transactions.editor.TransactionEditorScreen
import moe.frost.kochenite.transactions.list.LedgerTab
import moe.frost.kochenite.transactions.list.TransactionQuery
import moe.frost.kochenite.transactions.list.TransactionsScreen
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.EmptyState
import moe.frost.kochenite.ui.components.LocalInPane
import moe.frost.kochenite.ui.components.labelAutoSize
import java.util.UUID
import kotlin.reflect.KClass

private data class Tab(
    val route: Any,
    val label: Int,
    val icon: Int,
)

private val tabs =
    listOf(
        Tab(OverviewRoute, R.string.overview, R.drawable.ic_dashboard),
        Tab(TransactionsRoute, R.string.transactions, R.drawable.ic_receipt),
        Tab(AccountsRoute, R.string.accounts, R.drawable.ic_wallet),
        Tab(ReportsRoute, R.string.reports, R.drawable.ic_pie_chart),
    )

@Composable
internal fun BookScreen(
    state: BookState,
    model: BookViewModel,
) {
    // Composed only while saving: the newest back callback outranks NavHost and destination handlers.
    if (state.saving) BackHandler {}
    val nav = rememberNavController()
    val editor by model.transactionEditor.editor.collectAsState()
    val detail by model.detail.collectAsState()
    val query by model.transactionQuery.collectAsState()
    val current by nav.currentBackStackEntryAsState()
    val ledgerScroll = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    SideEffect { model.activeRouteId = current?.id }
    LaunchedEffect(model) {
        model.saved.collect { origin ->
            if (nav.currentBackStackEntry?.id == origin && nav.previousBackStackEntry != null) nav.popBackStack()
        }
    }
    if (state.loading) {
        LoadingScreen()
        return
    }
    if (!state.storageReady) {
        if (state.error == R.string.recovery) RecoveryScreen(model::refresh) else StorageUnavailableScreen(model::refresh)
        return
    }
    if (state.settings?.setupComplete != true) {
        SetupScreen(state, model)
        return
    }
    val movedToTrash = stringResource(R.string.moved_to_trash)
    val undo = stringResource(R.string.undo)
    LaunchedEffect(model) {
        model.trashed.collect { entry ->
            launch {
                val result = snackbar.showSnackbar(movedToTrash, undo, duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) model.restoreEntry(entry, close = false)
            }
        }
    }

    fun onRoute(type: KClass<*>) = current?.destination?.hasRoute(type) == true

    /** [replaceDetail] is set by list panes, where a newly chosen record replaces the open one. */
    fun navigate(
        route: Any,
        replaceDetail: Boolean = false,
    ) {
        if (state.saving) return

        fun push() {
            model.clearError()
            if (route is EntryEditorRoute) model.transactionEditor.newEditor(route.id)
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
                if (tabs.any { it.route == route }) {
                    popUpTo<OverviewRoute> { saveState = true }
                    restoreState = true
                }
                if (replaceDetail && route is EntryDetailRoute && onRoute(EntryDetailRoute::class)) {
                    popUpTo<EntryDetailRoute> { inclusive = true }
                }
                if (replaceDetail && route is AccountRoute && onRoute(AccountRoute::class)) {
                    popUpTo<AccountRoute> { inclusive = true }
                }
            }
        }
        if (editor != null) model.transactionEditor.closeEditor { push() } else push()
    }

    fun back() {
        if (state.saving) return
        if (editor != null) model.transactionEditor.closeEditor { nav.popBackStack() } else nav.popBackStack()
        model.clearError()
    }

    fun addTransaction() = navigate(EntryEditorRoute(UUID.randomUUID().toString()))

    fun showTransactions(change: (TransactionQuery) -> TransactionQuery) {
        model.transactionQuery.update { change(TransactionQuery()) }
        navigate(TransactionsRoute)
    }

    /** Opens a loaded editor only if [origin] is still the current screen and nothing is saving. */
    fun openEditor(origin: String): (String) -> Boolean =
        { editorId ->
            if (model.state.value.saving || nav.currentBackStackEntry?.id != origin) {
                false
            } else {
                model.clearError()
                nav.navigate(EntryEditorRoute(editorId)) { launchSingleTop = true }
                true
            }
        }

    // Editor dialogs render only on the editor route. If editor state ever outlives its route, show it again
    // rather than leaving every navigation action waiting on an invisible discard confirmation.
    LaunchedEffect(editor?.input?.id, current) {
        val id = editor?.input?.id ?: return@LaunchedEffect
        if (current != null && runCatching { nav.getBackStackEntry(EntryEditorRoute(id)) }.isFailure) {
            nav.navigate(EntryEditorRoute(id)) { launchSingleTop = true }
        }
    }
    BoxWithConstraints {
        val wide = maxWidth >= 840.dp
        val editorActive = editor != null && onRoute(EntryEditorRoute::class)
        val onTab = tabs.any { onRoute(it.route::class) }
        val bulk = query.bulk && onRoute(TransactionsRoute::class)
        // Tabs other than Overview sit directly above it, so the deepest one present is the selected tab.
        val selectedTab =
            remember(current) {
                (tabs.size - 1 downTo 1).firstOrNull { index ->
                    runCatching {
                        when (index) {
                            1 -> nav.getBackStackEntry<TransactionsRoute>()
                            2 -> nav.getBackStackEntry<AccountsRoute>()
                            else -> nav.getBackStackEntry<ReportsRoute>()
                        }
                    }.isSuccess
                } ?: 0
            }
        val showAdd = onTab && !bulk && !(onRoute(TransactionsRoute::class) && query.tab == LedgerTab.TRASH)
        val bottomBar = !wide && onTab && !bulk
        val labelWidth = maxWidth / tabs.size - 8.dp
        Scaffold(
            bottomBar = {
                if (bottomBar) {
                    NavigationBar {
                        tabs.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                modifier = Modifier.testTag("nav-$index"),
                                selected = index == selectedTab,
                                onClick = { navigate(tab.route) },
                                icon = { AppIcon(tab.icon, null) },
                                // A label is not bounded by its item, so bound it here for auto-sizing to take effect.
                                label = {
                                    Text(
                                        stringResource(tab.label),
                                        Modifier.widthIn(max = labelWidth),
                                        maxLines = 1,
                                        autoSize = labelAutoSize(MaterialTheme.typography.labelMedium),
                                    )
                                },
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (!wide && showAdd) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.add)) },
                        icon = { AppIcon(R.drawable.ic_add, null) },
                        onClick = ::addTransaction,
                        // Search results need the room; the icon alone still says Add.
                        expanded = !(onRoute(TransactionsRoute::class) && query.active),
                        modifier = Modifier.testTag("add-transaction"),
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar, if (bottomBar) Modifier else Modifier.navigationBarsPadding()) },
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (wide && !editorActive && !bulk) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            FloatingActionButton(onClick = ::addTransaction, modifier = Modifier.testTag("add-transaction")) {
                                AppIcon(R.drawable.ic_add, stringResource(R.string.add))
                            }
                            Spacer(Modifier.height(24.dp))
                        },
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            NavigationRailItem(
                                modifier = Modifier.testTag("nav-$index"),
                                selected = index == selectedTab,
                                onClick = { navigate(tab.route) },
                                icon = { AppIcon(tab.icon, null) },
                                label = {
                                    Text(
                                        stringResource(tab.label),
                                        Modifier.widthIn(max = 72.dp),
                                        maxLines = 1,
                                        autoSize = labelAutoSize(MaterialTheme.typography.labelMedium),
                                    )
                                },
                            )
                        }
                    }
                }
                NavHost(nav, startDestination = OverviewRoute, modifier = Modifier.weight(1f)) {
                    composable<OverviewRoute> {
                        OverviewScreen(
                            state,
                            wide,
                            openSettings = { navigate(SettingsRoute) },
                            reviewIncomplete = { showTransactions { it.copy(tab = LedgerTab.INCOMPLETE) } },
                            addTransaction = ::addTransaction,
                            seeAccounts = { navigate(AccountsRoute) },
                            openAccount = { navigate(AccountRoute(it)) },
                        )
                    }
                    composable<TransactionsRoute> { entry ->
                        val transactions: @Composable (String?) -> Unit = { selected ->
                            TransactionsScreen(
                                state,
                                model,
                                ledgerScroll,
                                selectedEntryId = selected,
                                openIncomplete = { id -> model.transactionEditor.openIncomplete(id, openEditor(entry.id)) },
                                openEntry = { navigate(EntryDetailRoute(it)) },
                                openTemplates = { navigate(TemplatesRoute) },
                                addTransaction = ::addTransaction,
                            )
                        }
                        if (wide && !bulk) {
                            ListDetail({ transactions(null) }) {
                                PanePlaceholder(R.drawable.ic_receipt, R.string.select_transaction)
                            }
                        } else {
                            transactions(null)
                        }
                    }
                    composable<EntryDetailRoute> { entry ->
                        val id = entry.toRoute<EntryDetailRoute>().id
                        LaunchedEffect(id) { model.openDetail(id) }
                        val content: @Composable () -> Unit = {
                            EntryDetailScreen(
                                id,
                                detail,
                                state,
                                model,
                                onBack = if (wide) null else ::back,
                                edit = { entryId -> model.transactionEditor.startEdit(entryId, openEditor(entry.id)) },
                                refundAllocation = { allocationId ->
                                    detail?.let { purchase ->
                                        model.transactionEditor.startLinkedRefund(purchase, allocationId, openEditor(entry.id))
                                    }
                                },
                                copy = { model.transactionEditor.copyEntry(id, openEditor(entry.id)) },
                                saveTemplate = { navigate(TemplateSaveRoute(id)) },
                                openRelated = { navigate(EntryDetailRoute(it)) },
                            )
                        }
                        if (wide) {
                            ListDetail(
                                {
                                    TransactionsScreen(
                                        state,
                                        model,
                                        ledgerScroll,
                                        selectedEntryId = id,
                                        openIncomplete = { record ->
                                            model.transactionEditor.openIncomplete(record, openEditor(entry.id))
                                        },
                                        openEntry = { navigate(EntryDetailRoute(it), replaceDetail = true) },
                                        openTemplates = { navigate(TemplatesRoute) },
                                        addTransaction = ::addTransaction,
                                    )
                                },
                                content,
                            )
                        } else {
                            content()
                        }
                    }
                    composable<ReportsRoute> {
                        ReportsScreen(state, model.reports) { month, currency, categoryId, kind ->
                            showTransactions {
                                it.copy(
                                    from = month.atDay(1),
                                    through = month.atEndOfMonth(),
                                    categoryId = categoryId,
                                    kind = kind,
                                    amountCode = currency.code,
                                )
                            }
                        }
                    }
                    composable<AccountsRoute> {
                        val list: @Composable () -> Unit = {
                            AccountsScreen(
                                state,
                                selectedId = null,
                                edit = { navigate(AccountRoute(it)) },
                                add = { navigate(AccountRoute()) },
                                groups = { navigate(GroupsRoute) },
                            )
                        }
                        if (wide) ListDetail(list) { PanePlaceholder(R.drawable.ic_wallet, R.string.select_account) } else list()
                    }
                    composable<AccountRoute> { entry ->
                        val id = entry.toRoute<AccountRoute>().id
                        if (wide) {
                            ListDetail(
                                {
                                    AccountsScreen(
                                        state,
                                        selectedId = id,
                                        edit = { navigate(AccountRoute(it), replaceDetail = true) },
                                        add = { navigate(AccountRoute(), replaceDetail = true) },
                                        groups = { navigate(GroupsRoute) },
                                    )
                                },
                            ) { AccountFormScreen(id, state, model.accountManagement, onBack = null) }
                        } else {
                            AccountFormScreen(id, state, model.accountManagement, onBack = ::back)
                        }
                    }
                    composable<GroupsRoute> { GroupsScreen(state, model.accountManagement, ::back) }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            state,
                            model,
                            ::back,
                            categories = { navigate(CategoriesRoute) },
                            tags = { navigate(TagsRoute) },
                            templates = { navigate(TemplatesRoute) },
                            groups = { navigate(GroupsRoute) },
                        )
                    }
                    composable<CategoriesRoute> { CategoriesScreen(state, ::back) { navigate(CategoryRoute(it)) } }
                    composable<CategoryRoute> { CategoryFormScreen(it.toRoute<CategoryRoute>().id, state, model, ::back) }
                    composable<TagsRoute> { TagsScreen(state, model, ::back) }
                    composable<TemplatesRoute> { entry ->
                        TemplatesScreen(state, model, ::back) { id -> model.transactionEditor.applyTemplate(id, openEditor(entry.id)) }
                    }
                    composable<TemplateSaveRoute> {
                        val entryId = it.toRoute<TemplateSaveRoute>().entryId
                        // A restored back stack has no detail loaded for this route yet.
                        LaunchedEffect(entryId) { if (model.detail.value?.id != entryId) model.openDetail(entryId) }
                        TemplateSaveScreen(entryId, detail, state, model, ::back)
                    }
                    composable<EntryEditorRoute> {
                        val route = it.toRoute<EntryEditorRoute>()
                        val id = route.id
                        LaunchedEffect(id, editor) {
                            if (editor?.input?.id != id && nav.currentBackStackEntry?.id == it.id) nav.popBackStack()
                        }
                        // Registered inside the destination so it outranks NavHost's own back callback. Otherwise system
                        // Back pops this route without closeEditor, stranding changed input whose dialogs never show.
                        BackHandler(enabled = editor != null && editor?.confirmDiscard != true && editor?.confirmIncomplete != true) {
                            model.transactionEditor.closeEditor { nav.popBackStack() }
                        }
                        editor?.takeIf { it.input.id == id }?.let { open ->
                            TransactionEditorScreen(state, model, model.transactionEditor, open) {
                                model.transactionEditor.closeEditor { nav.popBackStack() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Expanded windows: a fixed list pane beside a rounded detail pane. */
@Composable
private fun ListDetail(
    list: @Composable () -> Unit,
    detail: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(412.dp).fillMaxHeight()) { list() }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = 8.dp, end = 24.dp, top = 8.dp, bottom = 24.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .consumeWindowInsets(WindowInsets.safeDrawing),
        ) {
            CompositionLocalProvider(LocalInPane provides true) { detail() }
        }
    }
}

@Composable
private fun PanePlaceholder(
    icon: Int,
    message: Int,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyState(icon, stringResource(message)) }
}
