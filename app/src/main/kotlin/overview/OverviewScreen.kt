package moe.frost.kochenite.overview

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import moe.frost.kochenite.BookState
import moe.frost.kochenite.R
import moe.frost.kochenite.ui.components.Form
import moe.frost.kochenite.ui.components.currency

@Composable internal fun OverviewPanel(state: BookState) {
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

@Composable internal fun ReportPanel(state: BookState) {
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
