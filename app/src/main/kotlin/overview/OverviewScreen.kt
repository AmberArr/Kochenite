package moe.frost.kochenite.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.LocalFinanceColors
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.AccountBalance
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.CurrencyTotals
import moe.frost.kochenite.transactions.accountIcon
import moe.frost.kochenite.transactions.accountTone
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.AppScaffold
import moe.frost.kochenite.ui.components.Avatar
import moe.frost.kochenite.ui.components.EmptyState
import moe.frost.kochenite.ui.components.GroupCard
import moe.frost.kochenite.ui.components.IconAction
import moe.frost.kochenite.ui.components.LargeTopBar
import moe.frost.kochenite.ui.components.ListRow
import moe.frost.kochenite.ui.components.Notice
import moe.frost.kochenite.ui.components.NoticeTone
import moe.frost.kochenite.ui.components.SignDisplay
import moe.frost.kochenite.ui.components.cardColor
import moe.frost.kochenite.ui.components.display
import moe.frost.kochenite.ui.components.monthName
import moe.frost.kochenite.ui.components.shortDate
import moe.frost.kochenite.ui.components.symbol
import moe.frost.kochenite.ui.components.tabular
import java.time.LocalDate
import java.time.YearMonth

/** Net worth per currency, in the order the dashboard currency first. Overflowing sums are reported as null. */
internal fun netWorth(
    accounts: List<AccountBalance>,
    first: BookCurrency?,
): List<Pair<BookCurrency, Long?>> =
    accounts
        .filter { it.account.includeInNetWorth }
        .groupBy { it.account.currency }
        .map { (currency, balances) ->
            currency to runCatching { balances.fold(0L) { total, row -> Math.addExact(total, row.balanceMinor) } }.getOrNull()
        }.sortedBy { (currency, _) -> if (currency == first) 0 else 1 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverviewScreen(
    state: BookState,
    wide: Boolean,
    openSettings: () -> Unit,
    reviewIncomplete: () -> Unit,
    addTransaction: () -> Unit,
    seeAccounts: () -> Unit,
    openAccount: (String) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    AppScaffold(
        topBar = {
            LargeTopBar(stringResource(R.string.overview), scroll) {
                IconAction(
                    R.drawable.ic_settings,
                    stringResource(R.string.settings),
                    Modifier.testTag("open-settings"),
                    onClick = openSettings,
                )
            }
        },
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
    ) {
        val summary: @Composable () -> Unit = {
            state.error?.let { Notice(stringResource(it), tone = NoticeTone.ERROR) }
            NetWorthCard(state)
            if (state.incompleteCount > 0) {
                Notice(
                    pluralStringResource(R.plurals.incomplete_count, state.incompleteCount, state.incompleteCount),
                    tone = NoticeTone.CAUTION,
                    action = stringResource(R.string.review_incomplete),
                    onAction = reviewIncomplete,
                    modifier = Modifier.testTag("review-incomplete"),
                )
            }
            MonthCard(state, addTransaction)
        }
        val accounts: @Composable () -> Unit = { AccountsCard(state, seeAccounts, openAccount) }
        if (wide) {
            Row(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) { summary() }
                Column(Modifier.weight(1f)) { accounts() }
            }
        } else {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                summary()
                accounts()
                // Keeps the last row clear of the Add button.
                Spacer(Modifier.height(88.dp))
            }
        }
    }
}

@Composable
private fun NetWorthCard(state: BookState) {
    val scheme = MaterialTheme.colorScheme
    val worth = netWorth(state.accounts, state.settings?.displayCurrency)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(scheme.primaryContainer)
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(stringResource(R.string.net_worth), style = MaterialTheme.typography.titleSmall, color = scheme.onPrimaryContainer)
        if (worth.isEmpty()) {
            Text("—", style = MaterialTheme.typography.displaySmall, color = scheme.onPrimaryContainer)
        }
        worth.forEachIndexed { index, (currency, total) ->
            Text(
                total?.let { currency.display(it) } ?: stringResource(R.string.overflow_error),
                style =
                    when {
                        total == null -> MaterialTheme.typography.bodyMedium
                        index == 0 -> MaterialTheme.typography.displaySmall.tabular
                        else -> MaterialTheme.typography.headlineSmall.tabular
                    },
                color = scheme.onPrimaryContainer,
            )
        }
        if (worth.size > 1) {
            Text(
                stringResource(R.string.currency_totals_hint),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onPrimaryContainer,
            )
        }
    }
}

/** A chip that opens a menu of the currencies which have data. */
@Composable
internal fun CurrencyChip(
    selected: BookCurrency,
    currencies: List<BookCurrency>,
    onSelect: (BookCurrency) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = false,
            onClick = { open = true },
            label = { Text("${selected.symbol().trim()} ${selected.code}") },
            trailingIcon = { AppIcon(R.drawable.ic_arrow_drop_down, null, Modifier.size(18.dp)) },
            modifier = Modifier.testTag("currency-chip"),
        )
        DropdownMenu(open, { open = false }) {
            currencies.forEach { currency ->
                DropdownMenuItem(
                    text = { Text("${currency.symbol().trim()} ${currency.code}") },
                    onClick = {
                        onSelect(currency)
                        open = false
                    },
                )
            }
        }
    }
}

/** Income, spending and net flow, spread across the row with the last value right-aligned. Large text wraps whole values. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FlowSummary(totals: CurrencyTotals) {
    val finance = LocalFinanceColors.current
    val scheme = MaterialTheme.colorScheme
    val net = totals.netFlowMinor
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowValue(
            R.drawable.ic_arrow_downward,
            stringResource(R.string.income),
            totals.currency.display(totals.incomeMinor, SignDisplay.ALWAYS),
            finance.income,
            Alignment.Start,
        )
        FlowValue(
            R.drawable.ic_arrow_upward,
            stringResource(R.string.spending),
            // Spending is stored as a positive magnitude and shown as an outflow.
            totals.currency.display(runCatching { Math.negateExact(totals.spendingMinor) }.getOrDefault(0L), SignDisplay.ALWAYS),
            scheme.onSurface,
            Alignment.CenterHorizontally,
        )
        FlowValue(
            if (net < 0) R.drawable.ic_trending_down else R.drawable.ic_trending_up,
            stringResource(R.string.net_flow),
            totals.currency.display(net, SignDisplay.ALWAYS),
            if (net < 0) scheme.error else scheme.primary,
            Alignment.End,
        )
    }
}

@Composable
private fun FlowValue(
    icon: Int,
    label: String,
    value: String,
    color: Color,
    alignment: Alignment.Horizontal,
) {
    Column(horizontalAlignment = alignment, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            AppIcon(icon, null, Modifier.size(16.dp), tint = color)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value, style = MaterialTheme.typography.titleMedium.tabular, color = color, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun MonthCard(
    state: BookState,
    addTransaction: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val month = YearMonth.now()
    if (state.totals.isEmpty()) {
        Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(cardColor())) {
            EmptyState(
                R.drawable.ic_receipt,
                stringResource(R.string.no_month_activity),
                body = stringResource(R.string.no_month_activity_body),
                action = stringResource(R.string.add_transaction),
                actionIcon = R.drawable.ic_add,
                onAction = addTransaction,
            )
        }
        return
    }
    var code by rememberSaveable { mutableStateOf<String?>(null) }
    val totals =
        state.totals.find { it.currency.code == code }
            ?: state.totals.find { it.currency == state.settings?.displayCurrency }
            ?: state.totals.first()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(cardColor())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.month_so_far, monthName(month)),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            CurrencyChip(totals.currency, state.totals.map { it.currency }) { code = it.code }
        }
        FlowSummary(totals)
        // Whole percent of income spent, computed exactly; the bar is only an illustration of it.
        val percent =
            if (totals.incomeMinor > 0 && totals.spendingMinor >= 0) {
                totals.spendingMinor
                    .toBigInteger()
                    .multiply(100.toBigInteger())
                    .divide(totals.incomeMinor.toBigInteger())
                    .min(999.toBigInteger())
                    .toInt()
            } else {
                null
            }
        if (percent != null) {
            LinearProgressIndicator(
                progress = { percent.coerceIn(0, 100) / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = scheme.primary,
                trackColor = scheme.primary.copy(alpha = 0.3f),
                gapSize = 2.dp,
                drawStopIndicator = {},
            )
        }
        val today = LocalDate.now()
        val range = "${shortDate(month.atDay(1), today)} – ${shortDate(today, today)}"
        Text(
            if (percent != null) stringResource(R.string.income_spent, percent, range) else range,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AccountsCard(
    state: BookState,
    seeAll: () -> Unit,
    openAccount: (String) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.accounts),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = seeAll) { Text(stringResource(R.string.see_all)) }
        }
        GroupCard {
            state.accounts.filter { !it.account.archived }.forEach { row ->
                AccountRow(row, state.affectedIncomplete[row.account.id] ?: 0, showCode = false) { openAccount(row.account.id) }
            }
        }
    }
}

@Composable
internal fun AccountRow(
    row: AccountBalance,
    affected: Int,
    showCode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val account = row.account
    val details =
        listOfNotNull(
            account.type.takeIf { it.isNotBlank() },
            account.currency.code.takeIf { showCode || account.type.isBlank() },
            stringResource(R.string.excluded).takeIf { showCode && !account.includeInNetWorth },
            pluralStringResource(R.plurals.incomplete_may_affect, affected, affected).takeIf { affected > 0 },
        ).joinToString(" · ")
    ListRow(
        account.name,
        modifier.heightIn(min = 60.dp),
        supporting = details,
        supportingColor = if (affected > 0) LocalFinanceColors.current.caution else MaterialTheme.colorScheme.onSurfaceVariant,
        leading = { Avatar(accountIcon(account.type), accountTone(account)) },
        trailing = {
            Text(
                account.currency.display(row.balanceMinor),
                style = MaterialTheme.typography.titleMedium.tabular,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            )
        },
        onClick = onClick,
    )
}
