package moe.frost.kochenite.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import moe.frost.kochenite.BookState
import moe.frost.kochenite.BookViewModel
import moe.frost.kochenite.R
import moe.frost.kochenite.accounts.AccountForm
import moe.frost.kochenite.accounts.AccountsPanel
import moe.frost.kochenite.accounts.AdjustmentForm
import moe.frost.kochenite.accounts.GroupForm
import moe.frost.kochenite.accounts.GroupsPanel
import moe.frost.kochenite.overview.OverviewPanel
import moe.frost.kochenite.overview.ReportPanel
import moe.frost.kochenite.settings.CategoriesPanel
import moe.frost.kochenite.settings.CategoryForm
import moe.frost.kochenite.settings.SettingsForm
import moe.frost.kochenite.settings.TagForm
import moe.frost.kochenite.settings.TagsPanel
import moe.frost.kochenite.setup.SetupForm
import moe.frost.kochenite.templates.TemplateSaveForm
import moe.frost.kochenite.templates.TemplatesPanel
import moe.frost.kochenite.transactions.detail.EntryDetailForm
import moe.frost.kochenite.transactions.editor.TransactionEditorScreen
import moe.frost.kochenite.transactions.list.TransactionsPanel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
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
    val current by nav.currentBackStackEntryAsState()
    // Read here, not inside the top bar lambda: a skipped lambda would keep showing a stale Back button.
    val canGoBack = current != null && nav.previousBackStackEntry != null
    SideEffect { model.activeRouteId = current?.id }
    LaunchedEffect(model) {
        model.saved.collect { origin ->
            if (nav.currentBackStackEntry?.id == origin && nav.previousBackStackEntry != null) nav.popBackStack()
        }
    }
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
                if (tabs.any { it.first == route }) {
                    popUpTo<OverviewRoute> { saveState = true }
                    restoreState = true
                }
            }
        }
        if (editor != null) model.transactionEditor.closeEditor { push() } else push()
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
        val editorActive = editor != null && current?.destination?.hasRoute(EntryEditorRoute::class) == true
        Scaffold(
            topBar = {
                if (editorActive) {
                    TopAppBar(title = {
                        Text(
                            if (editor?.itemsOpen == true) {
                                stringResource(R.string.transaction_items)
                            } else if (editor?.input?.originalToken == null && editor?.incompleteToken == null) {
                                stringResource(R.string.new_transaction)
                            } else {
                                stringResource(R.string.edit_transaction)
                            },
                        )
                    }, navigationIcon = {
                        TextButton(
                            onClick = { model.transactionEditor.closeEditor { nav.popBackStack() } },
                        ) { Text(stringResource(R.string.back)) }
                    })
                } else {
                    TopAppBar(title = { Text(stringResource(R.string.app_name)) }, navigationIcon = {
                        if (canGoBack) {
                            TextButton(enabled = !state.saving, onClick = {
                                if (editor != null) model.transactionEditor.closeEditor { nav.popBackStack() } else nav.popBackStack()
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
                        navigate(EntryEditorRoute(UUID.randomUUID().toString()))
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
                    composable<TransactionsRoute> { entry ->
                        TransactionsPanel(
                            state,
                            model,
                            current?.destination?.hasRoute(TransactionsRoute::class) == true,
                            { id -> model.transactionEditor.openIncomplete(id, openEditor(entry.id)) },
                            { navigate(EntryDetailRoute(it)) },
                            { navigate(TemplatesRoute) },
                            { navigate(EntryEditorRoute(UUID.randomUUID().toString())) },
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
                            Column(
                                Modifier.weight(1f),
                            ) { AccountForm(id, state, model.accountManagement) { navigate(AdjustmentRoute(it)) } }
                        }
                    }
                    composable<CategoriesRoute> { CategoriesPanel(state) { navigate(CategoryRoute(it)) } }
                    composable<CategoryRoute> { CategoryForm(it.toRoute<CategoryRoute>().id, state, model) }
                    composable<TagsRoute> { TagsPanel(state) { navigate(TagRoute(it)) } }
                    composable<TagRoute> { TagForm(it.toRoute<TagRoute>().id, state, model) }
                    composable<TemplatesRoute> { entry ->
                        TemplatesPanel(state, model) { id -> model.transactionEditor.applyTemplate(id, openEditor(entry.id)) }
                    }
                    composable<TemplateSaveRoute> {
                        val entryId = it.toRoute<TemplateSaveRoute>().entryId
                        // A restored back stack has no detail loaded for this route yet.
                        LaunchedEffect(entryId) { if (model.detail.value?.id != entryId) model.openDetail(entryId) }
                        TemplateSaveForm(entryId, detail, state, model)
                    }
                    composable<GroupsRoute> { GroupsPanel(state) { navigate(GroupRoute(it)) } }
                    composable<GroupRoute> { GroupForm(it.toRoute<GroupRoute>().id, state, model.accountManagement) }
                    composable<AdjustmentRoute> { AdjustmentForm(it.toRoute<AdjustmentRoute>().id, state, model.accountManagement) }
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
                        editor?.takeIf { it.input.id == id }?.let { TransactionEditorScreen(state, model, model.transactionEditor, it) }
                    }
                    composable<EntryDetailRoute> { entry ->
                        val id = entry.toRoute<EntryDetailRoute>().id
                        LaunchedEffect(id) { model.openDetail(id) }
                        EntryDetailForm(
                            id,
                            detail,
                            state,
                            model,
                            { entryId -> model.transactionEditor.startEdit(entryId, openEditor(entry.id)) },
                            { allocationId ->
                                detail?.let { purchase ->
                                    model.transactionEditor.startLinkedRefund(purchase, allocationId, openEditor(entry.id))
                                }
                            },
                            { model.transactionEditor.copyEntry(id, openEditor(entry.id)) },
                            { navigate(TemplateSaveRoute(id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable internal fun TabIcon(index: Int) {
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
