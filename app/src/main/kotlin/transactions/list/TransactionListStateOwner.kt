package moe.frost.kochenite.transactions.list

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.frost.kochenite.domain.ledger.AllocationSelection
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.LedgerCursor
import moe.frost.kochenite.domain.ledger.LedgerFilter
import moe.frost.kochenite.domain.ledger.LedgerReader
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.SavedEntry

internal data class TransactionListState(
    val filter: LedgerFilter = LedgerFilter(),
    val entries: List<SavedEntry> = emptyList(),
    val next: LedgerCursor? = null,
    val loading: Boolean = false,
    val error: LedgerResult<Nothing>? = null,
    val selection: List<AllocationSelection> = emptyList(),
    /** Account balance after each posted entry, keyed by entry and account. */
    val balances: Map<Pair<String, String>, Long> = emptyMap(),
)

/** Owns the query generation, page cursor and selection as one consistent state. */
internal class TransactionListStateOwner(
    private val scope: CoroutineScope,
    private val reader: () -> LedgerReader?,
) {
    private val mutable = MutableStateFlow(TransactionListState())
    val state = mutable.asStateFlow()
    private var generation = 0L
    private var request: Job? = null

    fun search(filter: LedgerFilter) {
        val book = reader() ?: return
        val debounce = filter.text != mutable.value.filter.text
        val version = ++generation
        request?.cancel()
        mutable.value = TransactionListState(filter = filter, loading = true)
        request =
            scope.launch {
                if (debounce) delay(200)
                val result = book.search(filter, limit = PAGE_SIZE)
                if (version != generation) return@launch
                when (result) {
                    is LedgerResult.Success -> {
                        mutable.value =
                            mutable.value.copy(
                                entries = result.value.entries.distinctBy { it.id },
                                next = result.value.next,
                                loading = false,
                            )
                        loadBalances(book, version)
                    }

                    else -> {
                        mutable.value = mutable.value.copy(error = result.failure(), loading = false)
                    }
                }
            }
    }

    /** Rows are already visible; their running balances follow in a second query. */
    private suspend fun loadBalances(
        book: LedgerReader,
        version: Long,
    ) {
        val current = mutable.value
        if (current.filter.status != EntryStatus.POSTED || current.entries.isEmpty()) {
            mutable.value = current.copy(balances = emptyMap())
            return
        }
        val result = book.runningBalances(current.entries.map { it.id })
        if (version != generation || result !is LedgerResult.Success) return
        mutable.value = mutable.value.copy(balances = result.value.associate { (it.entryId to it.accountId) to it.balanceMinor })
    }

    /**
     * Reloads the current filter to the depth already loaded. Visible rows stay until the reload
     * completes; selections survive only while their entry token and allocation are unchanged.
     */
    fun refresh() {
        val book = reader() ?: return
        val current = mutable.value
        val version = ++generation
        request?.cancel()
        val wanted = maxOf(PAGE_SIZE, (current.entries.size + PAGE_SIZE - 1) / PAGE_SIZE * PAGE_SIZE)
        mutable.value = current.copy(loading = true, error = null)
        request =
            scope.launch {
                val entries = mutableListOf<SavedEntry>()
                var next: LedgerCursor? = null
                do {
                    val limit = minOf(MAX_PAGE_SIZE, wanted - entries.size)
                    val result = book.search(current.filter, next, limit)
                    if (version != generation) return@launch
                    if (result !is LedgerResult.Success) {
                        mutable.value = mutable.value.copy(error = result.failure(), loading = false)
                        return@launch
                    }
                    entries += result.value.entries
                    next = result.value.next
                    // A short page is final; this also bounds the loop if a reader returns an empty page.
                } while (next != null && entries.size < wanted && result.value.entries.size >= limit)
                val refreshed = entries.distinctBy { it.id }
                val byId = refreshed.associateBy { it.id }
                mutable.value =
                    mutable.value.copy(
                        entries = refreshed,
                        next = next,
                        loading = false,
                        selection =
                            mutable.value.selection.filter { selected ->
                                val entry = byId[selected.entryId]
                                entry != null && entry.token == selected.expectedToken &&
                                    entry.input.allocations.any { it.id == selected.allocationId }
                            },
                    )
                loadBalances(book, version)
            }
    }

    fun loadMore() {
        val current = mutable.value
        val cursor = current.next ?: return
        if (current.loading) return
        val book = reader() ?: return
        val version = generation
        // Reserve synchronously so repeated taps cannot request the same cursor.
        mutable.value = current.copy(loading = true, error = null)
        request =
            scope.launch {
                val result = book.search(current.filter, cursor, PAGE_SIZE)
                if (version != generation || mutable.value.next != cursor) return@launch
                when (result) {
                    is LedgerResult.Success -> {
                        mutable.value =
                            mutable.value.copy(
                                entries = (mutable.value.entries + result.value.entries).distinctBy { it.id },
                                next = result.value.next,
                                loading = false,
                            )
                        loadBalances(book, version)
                    }

                    else -> {
                        mutable.value = mutable.value.copy(error = result.failure(), loading = false)
                    }
                }
            }
    }

    fun clearSelection() {
        mutable.value = mutable.value.copy(selection = emptyList())
    }

    fun toggle(
        entry: SavedEntry,
        allocationId: String,
    ) {
        if (mutable.value.loading || mutable.value.entries.none { it.id == entry.id && it.token == entry.token }) return
        val selected = mutable.value.selection
        val old = selected.find { it.entryId == entry.id && it.allocationId == allocationId }
        mutable.value =
            mutable.value.copy(
                selection =
                    if (old != null) {
                        selected - old
                    } else {
                        selected + AllocationSelection(entry.id, allocationId, entry.token)
                    },
            )
    }

    private companion object {
        const val PAGE_SIZE = 50

        // LedgerReader rejects larger pages.
        const val MAX_PAGE_SIZE = 200
    }

    private fun LedgerResult<*>.failure(): LedgerResult<Nothing> =
        when (this) {
            is LedgerResult.Success -> error("A successful query is not a failure")
            is LedgerResult.Invalid -> this
            LedgerResult.Conflict -> LedgerResult.Conflict
            LedgerResult.Missing -> LedgerResult.Missing
            LedgerResult.AlreadyConfirmed -> LedgerResult.AlreadyConfirmed
            LedgerResult.TemporarilyUnavailable -> LedgerResult.TemporarilyUnavailable
            LedgerResult.RecoveryRequired -> LedgerResult.RecoveryRequired
            LedgerResult.UnexpectedFailure -> LedgerResult.UnexpectedFailure
        }
}
