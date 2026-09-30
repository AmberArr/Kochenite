package moe.frost.kochenite.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.AccountBalance
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.overview.AccountRow
import moe.frost.kochenite.overview.netWorth
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.AppScaffold
import moe.frost.kochenite.ui.components.GroupCard
import moe.frost.kochenite.ui.components.IconAction
import moe.frost.kochenite.ui.components.LargeTopBar
import moe.frost.kochenite.ui.components.ListRow
import moe.frost.kochenite.ui.components.Notice
import moe.frost.kochenite.ui.components.NoticeTone
import moe.frost.kochenite.ui.components.SectionHeader
import moe.frost.kochenite.ui.components.cardColor
import moe.frost.kochenite.ui.components.display
import moe.frost.kochenite.ui.components.tabular

private fun subtotals(rows: List<AccountBalance>): String =
    rows
        .groupBy { it.account.currency }
        .mapNotNull { (currency, balances) ->
            runCatching {
                balances.fold(
                    0L,
                ) { total, row -> Math.addExact(total, row.balanceMinor) }
            }.getOrNull()?.let { currency.display(it) }
        }.joinToString(" · ")

/** Accounts by group, with excluded and archived accounts labelled at the end rather than hidden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AccountsScreen(
    state: BookState,
    selectedId: String?,
    edit: (String) -> Unit,
    add: () -> Unit,
    groups: () -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var menu by remember { mutableStateOf(false) }
    var showArchived by rememberSaveable { mutableStateOf(false) }
    val active = state.accounts.filter { !it.account.archived }
    val included = active.filter { it.account.includeInNetWorth }
    val excluded = active.filter { !it.account.includeInNetWorth }
    val archived = state.accounts.filter { it.account.archived }
    AppScaffold(
        topBar = {
            LargeTopBar(stringResource(R.string.accounts), scroll) {
                IconAction(
                    R.drawable.ic_folder,
                    stringResource(R.string.groups_and_order),
                    Modifier.testTag("groups-and-order"),
                    onClick = groups,
                )
                Box {
                    IconAction(R.drawable.ic_more_vert, stringResource(R.string.more), Modifier.testTag("accounts-menu")) { menu = true }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.new_account)) },
                            leadingIcon = { AppIcon(R.drawable.ic_add, null) },
                            modifier = Modifier.testTag("new-account"),
                            onClick = {
                                menu = false
                                add()
                            },
                        )
                    }
                }
            }
        },
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
    ) {
        @Composable
        fun rows(list: List<AccountBalance>) {
            GroupCard {
                list.forEach { row ->
                    AccountRow(
                        row,
                        state.affectedIncomplete[row.account.id] ?: 0,
                        showCode = true,
                        modifier =
                            Modifier
                                .testTag("account-${row.account.id}")
                                .then(
                                    if (row.account.id == selectedId) {
                                        Modifier.clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.secondaryContainer)
                                    } else {
                                        Modifier
                                    },
                                ),
                    ) { edit(row.account.id) }
                }
            }
        }
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
            state.error?.let { error -> item(key = "error") { Notice(stringResource(error), tone = NoticeTone.ERROR) } }
            item(key = "net-worth") { NetWorthSummary(netWorth(state.accounts, state.settings?.displayCurrency)) }
            state.groups.forEach { group ->
                val members = included.filter { it.account.groupId == group.id }
                if (members.isNotEmpty()) {
                    item(key = "group-${group.id}") {
                        SectionHeader(group.name, trailing = subtotals(members))
                        rows(members)
                    }
                }
            }
            val ungrouped = included.filter { row -> state.groups.none { it.id == row.account.groupId } }
            if (ungrouped.isNotEmpty()) {
                item(key = "ungrouped") {
                    SectionHeader(
                        stringResource(if (state.groups.isEmpty()) R.string.accounts else R.string.no_group),
                        trailing = subtotals(ungrouped),
                    )
                    rows(ungrouped)
                }
            }
            if (excluded.isNotEmpty()) {
                item(key = "excluded") {
                    SectionHeader(stringResource(R.string.not_in_net_worth))
                    rows(excluded)
                }
            }
            if (archived.isNotEmpty()) {
                item(key = "archived") {
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    ListRow(
                        stringResource(R.string.archived_count, archived.size),
                        Modifier.padding(top = 8.dp).testTag("toggle-archived"),
                        headlineColor = muted,
                        leading = { AppIcon(R.drawable.ic_archive, null, tint = muted) },
                        trailing = {
                            AppIcon(
                                if (showArchived) R.drawable.ic_expand_less else R.drawable.ic_expand_more,
                                null,
                                tint = muted,
                            )
                        },
                    ) { showArchived = !showArchived }
                    if (showArchived) rows(archived)
                }
            }
        }
    }
}

@Composable
private fun NetWorthSummary(worth: List<Pair<BookCurrency, Long?>>) {
    if (worth.isEmpty()) return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(cardColor())
            .horizontalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        worth.forEach { (currency, total) ->
            Column(Modifier.semantics(mergeDescendants = true) {}) {
                Text(
                    stringResource(R.string.currency_net_worth, currency.code),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    total?.let { currency.display(it) } ?: stringResource(R.string.overflow_error),
                    style = if (total == null) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleLarge.tabular,
                )
            }
        }
    }
}
