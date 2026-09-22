package moe.frost.kochenite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.AccountBalance
import moe.frost.kochenite.domain.ledger.AccountGroup
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.BookSettings
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CurrencyTotals
import moe.frost.kochenite.domain.ledger.Draft
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.persistence.ledger.FinancialStorage
import moe.frost.kochenite.persistence.ledger.RoomLedger
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class EntryEditorState(
    val draft: Draft,
    val saving: Boolean = false,
    val saved: Boolean = true,
    val error: Int? = null,
)

data class BookState(
    val loading: Boolean = true,
    val storageReady: Boolean = false,
    val saving: Boolean = false,
    val settings: BookSettings? = null,
    val accounts: List<AccountBalance> = emptyList(),
    val groups: List<AccountGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val entries: List<SavedEntry> = emptyList(),
    val trashed: List<SavedEntry> = emptyList(),
    val drafts: List<Draft> = emptyList(),
    val totals: List<CurrencyTotals> = emptyList(),
    val error: Int? = null,
)

internal class BookViewModel(
    private val storage: FinancialStorage,
) : ViewModel() {
    private val mutable = MutableStateFlow(BookState())
    val state = mutable.asStateFlow()
    private val mutableEditor = MutableStateFlow<EntryEditorState?>(null)
    val editor = mutableEditor.asStateFlow()
    private val mutableDetail = MutableStateFlow<SavedEntry?>(null)
    val detail = mutableDetail.asStateFlow()
    private val editorMutex = Mutex()
    private var editorSaveJob: Job? = null
    private var persistedDraft: Draft? = null
    private val completed = Channel<Unit>(Channel.BUFFERED)
    val saved = completed.receiveAsFlow()
    private var ledger: RoomLedger? = null

    init {
        refresh()
    }

    private fun failure(result: LedgerResult<*>): Int =
        when (result) {
            LedgerResult.Conflict -> {
                R.string.conflict_message
            }

            LedgerResult.TemporarilyUnavailable -> {
                R.string.unavailable
            }

            LedgerResult.RecoveryRequired -> {
                R.string.recovery
            }

            is LedgerResult.Invalid -> {
                when (result.reason) {
                    moe.frost.kochenite.domain.ledger.InvalidReason.CURRENCY -> R.string.currency_locked_error
                    moe.frost.kochenite.domain.ledger.InvalidReason.CATEGORY -> R.string.category_error
                    moe.frost.kochenite.domain.ledger.InvalidReason.OVERFLOW -> R.string.overflow_error
                    else -> R.string.correct_fields
                }
            }

            else -> {
                R.string.correct_fields
            }
        }

    private suspend fun reload() {
        val opened = ledger?.let { LedgerResult.Success(it) } ?: storage.open()
        if (opened !is LedgerResult.Success) {
            mutable.value = mutable.value.copy(loading = false, error = failure(opened))
            return
        }
        val book = opened.value.also { ledger = it }
        val accounts = book.accountRepository.list()
        val groups = book.accountRepository.groups()
        val categories = book.categoryRepository.list()
        val settings = book.settingsRepository.get()
        val entries = book.page()
        val trashed = book.page(EntryStatus.TRASH)
        val drafts = book.drafts()
        val today = LocalDate.now()
        val totals = book.totals(today.withDayOfMonth(1), today)
        val failed =
            listOf(
                accounts,
                groups,
                categories,
                settings,
                entries,
                trashed,
                drafts,
                totals,
            ).firstOrNull { it !is LedgerResult.Success }
        if (failed != null) {
            mutable.value = mutable.value.copy(loading = false, error = failure(failed))
        } else {
            mutable.value =
                BookState(
                    loading = false,
                    storageReady = true,
                    saving = mutable.value.saving,
                    settings = (settings as LedgerResult.Success).value,
                    accounts = (accounts as LedgerResult.Success).value,
                    groups = (groups as LedgerResult.Success).value,
                    categories = (categories as LedgerResult.Success).value,
                    entries = (entries as LedgerResult.Success).value.entries,
                    trashed = (trashed as LedgerResult.Success).value.entries,
                    drafts = (drafts as LedgerResult.Success).value,
                    totals = (totals as LedgerResult.Success).value,
                )
        }
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    fun error(resource: Int) {
        mutable.value = mutable.value.copy(error = resource)
    }

    fun clearError() {
        mutable.value = mutable.value.copy(error = null)
    }

    private fun mutate(block: suspend (RoomLedger) -> LedgerResult<*>) {
        if (mutable.value.saving) return
        val book = ledger ?: return
        mutable.value = mutable.value.copy(saving = true, error = null)
        viewModelScope.launch {
            val result = block(book)
            if (result is LedgerResult.Success) {
                reload()
                completed.send(Unit)
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
    ) = mutate {
        it.settingsRepository.finishSetup(account, opening, display, starter)
    }

    fun saveAccount(
        account: Account,
        old: Account?,
        opening: Long?,
    ) = mutate { it.accountRepository.save(account, old, opening) }

    fun saveCategory(
        category: Category,
        old: Category?,
    ) = mutate { it.categoryRepository.save(category, old) }

    fun saveGroup(
        group: AccountGroup,
        old: AccountGroup?,
    ) = mutate { it.accountRepository.saveGroup(group, old) }

    fun display(currency: BookCurrency) = mutate { it.settingsRepository.setDisplayCurrency(currency) }

    fun adjust(
        accountId: String,
        amount: Long,
        note: String,
    ) = mutate {
        it.accountRepository.adjust(accountId, amount, LocalDate.now(), LocalTime.now(), note)
    }

    fun openDraft(id: String) {
        if (mutableEditor.value?.draft?.id == id) return
        viewModelScope.launch {
            val book = ledger ?: return@launch
            val existing = book.draft(id)
            val result =
                if (existing == LedgerResult.Missing) {
                    val accountId =
                        mutable.value.accounts
                            .firstOrNull { !it.account.archived }
                            ?.account
                            ?.id ?: return@launch
                    val input = EntryInput(EntryKind.EXPENSE, LocalDate.now(), LocalTime.now().withNano(0), accountId, null)
                    book.saveDraft(Draft(id, UUID.randomUUID().toString(), "", input, amountText = ""), null)
                } else {
                    existing
                }
            if (result is LedgerResult.Success) {
                persistedDraft = result.value
                mutableEditor.value = EntryEditorState(result.value)
                reload()
            } else {
                mutable.value = mutable.value.copy(error = failure(result))
            }
        }
    }

    fun startEdit(
        entryId: String,
        navigate: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val result = ledger?.beginEdit(entryId) ?: return@launch
            if (result is LedgerResult.Success) {
                persistedDraft = result.value
                mutableEditor.value = EntryEditorState(result.value)
                reload()
                navigate(result.value.id)
            } else {
                mutable.value = mutable.value.copy(error = failure(result))
            }
        }
    }

    fun openDetail(id: String) {
        mutableDetail.value = null
        viewModelScope.launch {
            val result = ledger?.entry(id) ?: return@launch
            if (result is LedgerResult.Success) {
                mutableDetail.value = result.value
            } else {
                mutable.value =
                    mutable.value.copy(error = failure(result))
            }
        }
    }

    fun trashEntry(entry: SavedEntry) = mutate { it.trash(entry.id, entry.token) }

    fun restoreEntry(entry: SavedEntry) = mutate { it.restore(entry.id, entry.token) }

    fun purgeEntry(entry: SavedEntry) = mutate { it.purge(entry.id, entry.token) }

    fun updateEditor(change: (Draft) -> Draft) {
        val old = mutableEditor.value ?: return
        mutableEditor.value = old.copy(draft = change(old.draft), saved = false, error = null)
        editorSaveJob?.cancel()
        editorSaveJob =
            viewModelScope.launch {
                delay(300)
                persistEditor()
            }
    }

    private suspend fun persistEditor(): Boolean =
        editorMutex.withLock {
            val book = ledger ?: return@withLock false
            val current = mutableEditor.value ?: return@withLock false
            val previous = persistedDraft ?: return@withLock false
            if (current.draft.copy(token = previous.token) == previous) {
                mutableEditor.value = current.copy(saved = true, saving = false)
                return@withLock true
            }
            mutableEditor.value = current.copy(saving = true, error = null)
            val result = book.saveDraft(current.draft.copy(token = previous.token), previous.token)
            if (result is LedgerResult.Success) {
                persistedDraft = result.value
                val latest = mutableEditor.value ?: return@withLock true
                val updated = latest.draft.copy(token = result.value.token)
                val clean = updated == result.value
                mutableEditor.value = latest.copy(draft = updated, saved = clean, saving = false)
                reload()
                true
            } else {
                mutableEditor.value = mutableEditor.value?.copy(saving = false, saved = false, error = failure(result))
                false
            }
        }

    fun closeEditor(onSaved: () -> Unit) {
        viewModelScope.launch {
            editorSaveJob?.cancelAndJoin()
            if (persistEditor()) {
                mutableEditor.value = null
                persistedDraft = null
                onSaved()
            }
        }
    }

    fun discardEditor(onDiscarded: () -> Unit) {
        viewModelScope.launch {
            editorSaveJob?.cancelAndJoin()
            val current = persistedDraft ?: return@launch
            val result = ledger?.discardDraft(current.id, current.token) ?: return@launch
            if (result is LedgerResult.Success) {
                mutableEditor.value = null
                persistedDraft = null
                reload()
                onDiscarded()
            } else {
                mutableEditor.value = mutableEditor.value?.copy(error = failure(result))
            }
        }
    }

    fun confirmEditor() {
        viewModelScope.launch {
            editorSaveJob?.cancelAndJoin()
            if (!persistEditor()) return@launch
            val current = persistedDraft ?: return@launch
            val badDate = current.dateText?.let { runCatching { LocalDate.parse(it) }.isFailure } ?: false
            val badTime = current.timeText?.let { runCatching { LocalTime.parse(it) }.isFailure } ?: false
            if (badDate || badTime) {
                mutableEditor.value = mutableEditor.value?.copy(error = R.string.correct_fields)
                return@launch
            }
            val result = ledger?.confirm(current.id, current.token) ?: return@launch
            if (result is LedgerResult.Success) {
                mutableEditor.value = null
                persistedDraft = null
                mutableDetail.value = result.value
                reload()
                completed.send(Unit)
            } else {
                mutableEditor.value = mutableEditor.value?.copy(error = failure(result))
            }
        }
    }
}
