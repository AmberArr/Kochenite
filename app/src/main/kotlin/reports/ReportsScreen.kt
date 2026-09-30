package moe.frost.kochenite.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.frost.kochenite.BookState
import moe.frost.kochenite.LocalFinanceColors
import moe.frost.kochenite.R
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.CurrencyTotals
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.overview.CurrencyChip
import moe.frost.kochenite.ui.components.AppIcon
import moe.frost.kochenite.ui.components.AppScaffold
import moe.frost.kochenite.ui.components.EmptyState
import moe.frost.kochenite.ui.components.IconAction
import moe.frost.kochenite.ui.components.LargeTopBar
import moe.frost.kochenite.ui.components.Notice
import moe.frost.kochenite.ui.components.NoticeTone
import moe.frost.kochenite.ui.components.SignDisplay
import moe.frost.kochenite.ui.components.cardColor
import moe.frost.kochenite.ui.components.display
import moe.frost.kochenite.ui.components.monthTitle
import moe.frost.kochenite.ui.components.tabular
import java.math.BigInteger
import java.time.YearMonth

internal data class CategoryShare(
    val categoryId: String?,
    val name: String,
    val amountMinor: Long,
    /** Whole percent of the positive total, rounded half up; null for a net credit. */
    val percent: Int?,
)

/** Spending per top-level category, largest first. Categories whose credits exceed their spending have no share. */
internal fun categoryShares(
    totals: List<Pair<String?, Long>>,
    name: (String?) -> String,
): List<CategoryShare> {
    val spent = totals.filter { it.second > 0 }.fold(BigInteger.ZERO) { sum, row -> sum + row.second.toBigInteger() }
    return totals
        .filter { it.second != 0L }
        .sortedByDescending { it.second }
        .map { (id, amount) ->
            val percent =
                if (amount > 0 && spent.signum() > 0) {
                    ((amount.toBigInteger() * BigInteger.valueOf(200) + spent) / (spent * BigInteger.valueOf(2))).toInt()
                } else {
                    null
                }
            CategoryShare(id, name(id), amount, percent)
        }
}

/** One calendar month and one currency at a time; nothing is converted. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportsScreen(
    state: BookState,
    owner: ReportStateOwner,
    drillDown: (YearMonth, BookCurrency, String?, EntryKind?) -> Unit,
) {
    val report by owner.state.collectAsState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var months by remember { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf<String?>(null) }
    var table by rememberSaveable { mutableStateOf(false) }
    val currencies = (report.totals.map { it.currency } + state.accounts.map { it.account.currency }).distinct()
    val currency =
        currencies.find { it.code == code }
            ?: report.totals.map { it.currency }.find { it == state.settings?.displayCurrency }
            ?: report.totals.firstOrNull()?.currency
            ?: state.settings?.displayCurrency
            ?: currencies.firstOrNull()
    val totals = report.totals.find { it.currency == currency }
    AppScaffold(
        topBar = {
            LargeTopBar(stringResource(R.string.reports), scroll) {
                IconAction(
                    R.drawable.ic_calendar,
                    stringResource(R.string.choose_month),
                    Modifier.testTag("choose-month"),
                ) { months = true }
            }
        },
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
    ) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    FilterChip(
                        selected = true,
                        onClick = { months = true },
                        label = { Text(monthTitle(report.month)) },
                        leadingIcon = { AppIcon(R.drawable.ic_check, null, Modifier.size(18.dp)) },
                        trailingIcon = { AppIcon(R.drawable.ic_arrow_drop_down, null, Modifier.size(18.dp)) },
                        modifier = Modifier.testTag("report-month"),
                    )
                    DropdownMenu(months, { months = false }) {
                        val current = YearMonth.now()
                        // Future months cannot hold entries, so the list runs back from the current month.
                        (0L until 24L).map { current.minusMonths(it) }.forEach { month ->
                            DropdownMenuItem(
                                text = { Text(monthTitle(month)) },
                                leadingIcon = { if (month == report.month) AppIcon(R.drawable.ic_check, null) },
                                onClick = {
                                    owner.show(month)
                                    months = false
                                },
                            )
                        }
                    }
                }
                if (currency != null) CurrencyChip(currency, currencies) { code = it.code }
            }
            (report.error ?: state.error)?.let { Notice(stringResource(it), tone = NoticeTone.ERROR) }
            if (currency == null || totals == null || (totals.incomeMinor == 0L && totals.spendingMinor == 0L)) {
                if (!report.loading) {
                    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(cardColor())) {
                        EmptyState(
                            R.drawable.ic_pie_chart,
                            stringResource(R.string.no_report_activity),
                            body = stringResource(R.string.no_report_activity_body),
                            action = stringResource(R.string.choose_another_month),
                            actionIcon = R.drawable.ic_calendar,
                            onAction = { months = true },
                        )
                    }
                }
            } else {
                NetFlowCard(totals)
                val names = state.categories.associate { it.id to it.name }
                val uncategorized = stringResource(R.string.uncategorized)
                val shares =
                    categoryShares(
                        report.categories.filter { it.currency == currency && it.kind == CategoryKind.EXPENSE }.map {
                            it.rootCategoryId to
                                it.amountMinor
                        },
                    ) { id -> names[id] ?: uncategorized }
                if (shares.isNotEmpty()) {
                    CategoryCard(currency, shares, table, { table = !table }) { share ->
                        drillDown(report.month, currency, share.categoryId, EntryKind.EXPENSE)
                    }
                }
            }
            // Keeps the last row clear of the Add button.
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun NetFlowCard(totals: CurrencyTotals) {
    val scheme = MaterialTheme.colorScheme
    val finance = LocalFinanceColors.current
    val currency = totals.currency
    val net = totals.netFlowMinor
    val largest = maxOf(totals.incomeMinor, totals.spendingMinor, 1L)

    fun fraction(value: Long) =
        (value.coerceAtLeast(0L).toBigDecimal().divide(largest.toBigDecimal(), 4, java.math.RoundingMode.HALF_UP)).toFloat()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(cardColor())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) {}) {
            Text(
                stringResource(R.string.net_flow),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                currency.display(net, SignDisplay.ALWAYS),
                style = MaterialTheme.typography.headlineLarge.tabular,
                color = if (net < 0) scheme.error else scheme.primary,
            )
        }
        FlowBar(
            stringResource(R.string.income),
            currency.display(totals.incomeMinor, SignDisplay.ALWAYS),
            fraction(totals.incomeMinor),
            finance.income,
        )
        FlowBar(
            stringResource(R.string.spending),
            currency.display(runCatching { Math.negateExact(totals.spendingMinor) }.getOrDefault(0L), SignDisplay.ALWAYS),
            fraction(totals.spendingMinor),
            scheme.primary,
        )
        Text(stringResource(R.string.report_exclusions), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

/** The value is always printed; the bar only illustrates it. */
@Composable
private fun FlowBar(
    label: String,
    value: String,
    fraction: Float,
    color: Color,
) {
    Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(
                label,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(value, style = MaterialTheme.typography.titleMedium.tabular)
        }
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(12.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
private fun CategoryCard(
    currency: BookCurrency,
    shares: List<CategoryShare>,
    table: Boolean,
    toggleTable: () -> Unit,
    open: (CategoryShare) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val chart = LocalFinanceColors.current.chart
    // The chart names the largest categories and gathers the rest; the table lists every category.
    val top = shares.filter { it.percent != null }.take(chart.size - 2)
    val rest = shares.filter { it.percent != null }.drop(top.size)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(cardColor())
            .padding(vertical = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.spending_by_category),
                Modifier.weight(1f).semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton(onClick = toggleTable, modifier = Modifier.testTag("report-table")) {
                Text(stringResource(if (table) R.string.chart else R.string.table))
            }
        }
        if (table) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    stringResource(R.string.category),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.share), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                Text(
                    stringResource(R.string.amount),
                    Modifier.padding(start = 16.dp).heightIn(min = 16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                )
            }
            shares.forEach { share -> ShareRow(currency, share, null) { open(share) } }
            return@Column
        }
        if (top.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(16.dp)
                    .clip(CircleShape),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                top.forEachIndexed { index, share ->
                    Box(Modifier.weight(share.amountMinor.toFloat().coerceAtLeast(1f)).height(16.dp).background(chart[index]))
                }
                val other = rest.sumOf { it.amountMinor.toBigInteger() }
                if (other.signum() > 0) Box(Modifier.weight(other.toFloat()).height(16.dp).background(chart.last()))
            }
        }
        top.forEachIndexed { index, share -> ShareRow(currency, share, chart[index]) { open(share) } }
        if (rest.isNotEmpty()) {
            val amount = runCatching { rest.fold(0L) { sum, share -> Math.addExact(sum, share.amountMinor) } }.getOrNull()
            ShareRow(
                currency,
                CategoryShare(null, stringResource(R.string.other_categories, rest.size), amount ?: 0L, rest.sumOf { it.percent ?: 0 }),
                chart.last(),
                onClick = toggleTable,
            )
        }
    }
}

/** The whole row is the drill-down target; there is no separate chevron. */
@Composable
private fun ShareRow(
    currency: BookCurrency,
    share: CategoryShare,
    color: Color?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (color != null) Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Text(share.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            share.percent?.let { stringResource(R.string.percent, it) } ?: "—",
            style = MaterialTheme.typography.bodyMedium.tabular,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(currency.display(share.amountMinor), style = MaterialTheme.typography.titleMedium.tabular)
    }
}
