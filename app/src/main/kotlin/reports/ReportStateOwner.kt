package moe.frost.kochenite.reports

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.frost.kochenite.domain.ledger.CategoryTotal
import moe.frost.kochenite.domain.ledger.CurrencyTotals
import moe.frost.kochenite.domain.ledger.LedgerReader
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.failure
import java.time.YearMonth

internal data class ReportState(
    val month: YearMonth = YearMonth.now(),
    val totals: List<CurrencyTotals> = emptyList(),
    val categories: List<CategoryTotal> = emptyList(),
    val loading: Boolean = false,
    val error: Int? = null,
)

/** Income, spending and category totals for one calendar month, separated by account currency. */
internal class ReportStateOwner(
    private val scope: CoroutineScope,
    private val reader: () -> LedgerReader?,
) {
    private val mutable = MutableStateFlow(ReportState())
    val state = mutable.asStateFlow()
    private var request: Job? = null

    fun show(month: YearMonth) {
        if (month == mutable.value.month) return
        mutable.value = ReportState(month = month)
        refresh()
    }

    fun refresh() {
        val book = reader() ?: return
        val month = mutable.value.month
        request?.cancel()
        mutable.value = mutable.value.copy(loading = true, error = null)
        request =
            scope.launch {
                val totals = book.totals(month.atDay(1), month.atEndOfMonth())
                val categories = book.categoryTotals(month.atDay(1), month.atEndOfMonth())
                if (mutable.value.month != month) return@launch
                mutable.value =
                    if (totals is LedgerResult.Success && categories is LedgerResult.Success) {
                        mutable.value.copy(totals = totals.value, categories = categories.value, loading = false)
                    } else {
                        mutable.value.copy(loading = false, error = failure(if (totals is LedgerResult.Success) categories else totals))
                    }
            }
    }
}
