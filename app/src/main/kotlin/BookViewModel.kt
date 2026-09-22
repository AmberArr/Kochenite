package moe.frost.kochenite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.AccountBalance
import moe.frost.kochenite.domain.ledger.AccountGroup
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.BookSettings
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CurrencyTotals
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.persistence.ledger.FinancialStorage
import moe.frost.kochenite.persistence.ledger.RoomLedger
import java.time.LocalDate
import java.time.LocalTime

data class BookState(
    val loading: Boolean = true,
    val storageReady: Boolean = false,
    val saving: Boolean = false,
    val settings: BookSettings? = null,
    val accounts: List<AccountBalance> = emptyList(),
    val groups: List<AccountGroup> = emptyList(),
    val categories: List<Category> = emptyList(),
    val entries: List<SavedEntry> = emptyList(),
    val totals: List<CurrencyTotals> = emptyList(),
    val error: Int? = null,
)

internal class BookViewModel(
    private val storage: FinancialStorage,
) : ViewModel() {
    private val mutable = MutableStateFlow(BookState())
    val state = mutable.asStateFlow()
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
        val today = LocalDate.now()
        val totals = book.totals(today.withDayOfMonth(1), today)
        val failed = listOf(accounts, groups, categories, settings, entries, totals).firstOrNull { it !is LedgerResult.Success }
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
}
