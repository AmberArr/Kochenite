package moe.frost.kochenite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.frost.kochenite.accounts.AccountStateOwner
import moe.frost.kochenite.domain.editor.IncompleteRecord
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.AccountBalance
import moe.frost.kochenite.domain.ledger.AccountGroup
import moe.frost.kochenite.domain.ledger.AllocationSelection
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.BookSettings
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CurrencyTotals
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.EntryTemplate
import moe.frost.kochenite.domain.ledger.LedgerCursor
import moe.frost.kochenite.domain.ledger.LedgerFilter
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.domain.ledger.Tag
import java.time.LocalDate

data class BookState(
    val loading: Boolean = true,
    val storageReady: Boolean = false,
    val saving: Boolean = false,
    val settings: BookSettings? = null,
    val accounts: List<AccountBalance> = emptyList(),
    val groups: List<AccountGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val templates: List<EntryTemplate> = emptyList(),
    val relatedEntries: List<SavedEntry> = emptyList(),
    val relatedNext: LedgerCursor? = null,
    val relatedQuery: String = "",
    val relatedLoading: Boolean = false,
    val relatedError: Int? = null,
    val incompleteRecords: List<IncompleteRecord> = emptyList(),
    val incompleteCount: Int = 0,
    val affectedIncomplete: Map<String, Int> = emptyMap(),
    val totals: List<CurrencyTotals> = emptyList(),
    val error: Int? = null,
)

internal class BookViewModel(
    private val openBook: suspend () -> moe.frost.kochenite.domain.ledger.LedgerResult<moe.frost.kochenite.domain.ledger.BookServices>,
) : ViewModel() {
    private val mutable = MutableStateFlow(BookState())
    private val mutableDetail = MutableStateFlow<SavedEntry?>(null)
    val detail = mutableDetail.asStateFlow()
    private var relatedSearchJob: Job? = null
    private var detailJob: Job? = null
    private val completed = Channel<String>(Channel.BUFFERED)
    val saved = completed.receiveAsFlow()
    private var bookServices: moe.frost.kochenite.domain.ledger.BookServices? = null
    val accountManagement =
        AccountStateOwner(
            viewModelScope,
            { bookServices?.accounts },
            { mutable.value.saving },
            { activeRouteId },
        ) { origin, postedEntry ->
            reload(if (postedEntry) ENTRY_PARTS else setOf(Part.ACCOUNTS))
            if (origin != null) completed.send(origin)
        }
    val state =
        combine(mutable, accountManagement.state) { book, accounts ->
            book.copy(
                accounts = accounts.accounts,
                groups = accounts.groups,
                saving = book.saving || accounts.saving,
                error = accounts.error ?: book.error,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, BookState())
    val transactionList =
        moe.frost.kochenite.transactions.list
            .TransactionListStateOwner(viewModelScope, { bookServices?.reader })
    val transactionEditor =
        moe.frost.kochenite.transactions.editor.TransactionEditorStateOwner(
            viewModelScope,
            { bookServices?.editor },
            { bookServices?.reader },
            {
                accountManagement.state.value.accounts
                    .map { it.account }
            },
            ::error,
        ) { saved ->
            if (saved != null && mutableDetail.value?.id == saved.id) mutableDetail.value = saved
            // A posted save may complete an incomplete record; saving an incomplete record posts nothing.
            reload(if (saved != null) ENTRY_PARTS + Part.INCOMPLETE else setOf(Part.INCOMPLETE))
        }
    var activeRouteId: String? = null

    init {
        refresh()
    }

    /** Book state a mutation can change; [reload] queries only the requested parts. */
    internal enum class Part { SETTINGS, ACCOUNTS, CATEGORIES, TAGS, TEMPLATES, INCOMPLETE, TOTALS, LEDGER }

    private suspend fun reload(parts: Set<Part> = ALL_PARTS) {
        val opened = bookServices?.let { LedgerResult.Success(it) } ?: openBook()
        if (opened !is LedgerResult.Success) {
            mutable.value = mutable.value.copy(loading = false, error = failure(opened))
            return
        }
        val book = opened.value.also { bookServices = it }
        // Partial reloads apply only to a book that has loaded completely once.
        val load = if (mutable.value.storageReady) parts else ALL_PARTS

        suspend fun <T> part(
            part: Part,
            query: suspend () -> LedgerResult<T>,
        ): LedgerResult<T>? = if (part in load) query() else null
        val accounts = part(Part.ACCOUNTS) { book.accounts.list() }
        val groups = part(Part.ACCOUNTS) { book.accounts.groups() }
        val categories = part(Part.CATEGORIES) { book.categories.list() }
        val tags = part(Part.TAGS) { book.tags.list() }
        val templates = part(Part.TEMPLATES) { book.templates.templates() }
        val settings = part(Part.SETTINGS) { book.settings.get() }
        val incomplete = part(Part.INCOMPLETE) { book.reader.incompleteRecords() }
        val totals =
            part(Part.TOTALS) {
                val today = LocalDate.now()
                book.reader.totals(today.withDayOfMonth(1), today)
            }
        val failed =
            listOfNotNull(
                accounts,
                groups,
                categories,
                tags,
                templates,
                settings,
                incomplete,
                totals,
            ).firstOrNull { it !is LedgerResult.Success }
        if (failed != null) {
            mutable.value = mutable.value.copy(loading = false, error = failure(failed))
            return
        }
        if (accounts != null && groups != null) {
            accountManagement.replace((accounts as LedgerResult.Success).value, (groups as LedgerResult.Success).value)
        }
        var next = mutable.value.copy(loading = false, storageReady = true)
        settings?.let { next = next.copy(settings = (it as LedgerResult.Success).value) }
        categories?.let { next = next.copy(categories = (it as LedgerResult.Success).value) }
        tags?.let { next = next.copy(tags = (it as LedgerResult.Success).value) }
        templates?.let { next = next.copy(templates = (it as LedgerResult.Success).value) }
        totals?.let { next = next.copy(totals = (it as LedgerResult.Success).value) }
        incomplete?.let {
            val records = (it as LedgerResult.Success).value
            next =
                next.copy(
                    incompleteCount = records.size,
                    incompleteRecords = records,
                    affectedIncomplete =
                        records
                            .flatMap { record ->
                                listOfNotNull(record.editor.input.accountId, record.editor.input.destinationAccountId).distinct()
                            }.groupingBy { id -> id }
                            .eachCount(),
                )
        }
        mutable.value = next.copy(error = null)
        if (Part.LEDGER in load) transactionList.refresh()
    }

    private companion object {
        val ALL_PARTS = Part.entries.toSet()

        /** Posting, trashing, restoring or purging an entry changes balances, month totals and ledger rows. */
        val ENTRY_PARTS = setOf(Part.ACCOUNTS, Part.TOTALS, Part.LEDGER)
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    fun error(resource: Int) {
        mutable.value = mutable.value.copy(error = resource)
    }

    fun clearError() {
        accountManagement.clearError()
        mutable.value = mutable.value.copy(error = null)
    }

    private fun mutate(
        parts: Set<Part>,
        closeOnSuccess: Boolean = true,
        block: suspend (moe.frost.kochenite.domain.ledger.BookServices) -> LedgerResult<*>,
    ) {
        if (mutable.value.saving || accountManagement.state.value.saving) return
        val book = bookServices ?: return
        mutable.value = mutable.value.copy(saving = true, error = null)
        val origin = activeRouteId
        viewModelScope.launch {
            val result = block(book)
            if (result is LedgerResult.Success) {
                reload(parts)
                if (closeOnSuccess && origin != null) completed.send(origin)
            } else {
                mutable.value = mutable.value.copy(error = failure(result))
            }
            mutable.value = mutable.value.copy(saving = false)
        }
    }

    fun setup(
        account: Account,
        opening: Long,
        display: BookCurrency,
        starter: Boolean,
    ) = mutate(ALL_PARTS) {
        it.settings.finishSetup(account, opening, display, starter)
    }

    // Reparenting changes which entries a parent-category filter matches.
    fun saveCategory(
        category: Category,
        old: Category?,
    ) = mutate(setOf(Part.CATEGORIES, Part.LEDGER)) { it.categories.save(category, old) }

    fun saveTag(
        tag: Tag,
        old: Tag?,
    ) = mutate(setOf(Part.TAGS)) { it.tags.save(tag, old) }

    fun saveTemplate(template: EntryTemplate) = mutate(setOf(Part.TEMPLATES)) { it.templates.saveTemplate(template) }

    fun deleteTemplate(id: String) = mutate(setOf(Part.TEMPLATES)) { it.templates.deleteTemplate(id) }

    fun bulkCategorize(
        selections: List<AllocationSelection>,
        categoryId: String?,
    ) {
        if (mutable.value.saving || accountManagement.state.value.saving) return
        val book = bookServices ?: return
        mutable.value = mutable.value.copy(saving = true, error = null)
        viewModelScope.launch {
            val result = book.bulkCategories.bulkCategorize(selections, categoryId)
            // Bulk categorization changes allocation categories and entry tokens, not balances or currency totals.
            if (result is LedgerResult.Success) reload(setOf(Part.LEDGER)) else mutable.value = mutable.value.copy(error = failure(result))
            mutable.value = mutable.value.copy(saving = false)
        }
    }

    fun searchRelatedEntries(
        text: String,
        referencedIds: List<String>,
    ) {
        val book = bookServices ?: return
        relatedSearchJob?.cancel()
        mutable.value = mutable.value.copy(relatedQuery = text, relatedLoading = true, relatedError = null)
        relatedSearchJob =
            viewModelScope.launch {
                val result = book.reader.search(LedgerFilter(status = EntryStatus.POSTED, text = text))
                val referenced =
                    referencedIds.mapNotNull { id ->
                        (book.reader.entry(id) as? LedgerResult.Success)?.value
                    }
                if (mutable.value.relatedQuery != text) return@launch
                if (result is LedgerResult.Success) {
                    mutable.value =
                        mutable.value.copy(
                            relatedEntries = (result.value.entries + referenced).distinctBy { it.id },
                            relatedNext = result.value.next,
                            relatedLoading = false,
                        )
                } else {
                    mutable.value = mutable.value.copy(relatedLoading = false, relatedError = failure(result))
                }
            }
    }

    fun loadMoreRelatedEntries() {
        val book = bookServices ?: return
        val cursor = mutable.value.relatedNext ?: return
        val query = mutable.value.relatedQuery
        mutable.value = mutable.value.copy(relatedLoading = true, relatedError = null)
        relatedSearchJob?.cancel()
        relatedSearchJob =
            viewModelScope.launch {
                val result = book.reader.search(LedgerFilter(status = EntryStatus.POSTED, text = query), cursor)
                if (mutable.value.relatedQuery != query) return@launch
                if (result is LedgerResult.Success) {
                    mutable.value =
                        mutable.value.copy(
                            relatedEntries = (mutable.value.relatedEntries + result.value.entries).distinctBy { it.id },
                            relatedNext = result.value.next,
                            relatedLoading = false,
                        )
                } else {
                    mutable.value = mutable.value.copy(relatedLoading = false, relatedError = failure(result))
                }
            }
    }

    fun display(currency: BookCurrency) = mutate(setOf(Part.SETTINGS)) { it.settings.setDisplayCurrency(currency) }

    fun deleteIncomplete(
        id: String,
        token: String,
    ) = mutate(setOf(Part.INCOMPLETE), closeOnSuccess = false) { it.editor.deleteIncomplete(id, token) }

    fun openDetail(id: String) {
        mutableDetail.value = null
        detailJob?.cancel()
        detailJob =
            viewModelScope.launch {
                val result = bookServices?.reader?.entry(id) ?: return@launch
                if (result is LedgerResult.Success) {
                    mutableDetail.value = result.value
                    val reader = bookServices?.reader ?: return@launch
                    val related =
                        result.value.input.relatedEntryIds
                            .mapNotNull { (reader.entry(it) as? LedgerResult.Success)?.value }
                    mutable.value = mutable.value.copy(relatedEntries = related)
                } else {
                    mutable.value =
                        mutable.value.copy(error = failure(result))
                }
            }
    }

    fun trashEntry(entry: SavedEntry) = mutate(ENTRY_PARTS) { it.editor.trash(entry.id, entry.token) }

    fun restoreEntry(entry: SavedEntry) = mutate(ENTRY_PARTS) { it.editor.restore(entry.id, entry.token) }

    fun purgeEntry(entry: SavedEntry) = mutate(ENTRY_PARTS) { it.editor.purge(entry.id, entry.token) }
}
