package moe.frost.kochenite.domain.ledger

import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.editor.IncompleteRecord
import java.time.LocalDate

interface LedgerReader {
    suspend fun entry(id: String): LedgerResult<SavedEntry>

    suspend fun incomplete(id: String): LedgerResult<IncompleteRecord>

    suspend fun incompleteRecords(): LedgerResult<List<IncompleteRecord>>

    suspend fun balance(accountId: String): LedgerResult<Long>

    suspend fun totals(
        start: LocalDate,
        end: LocalDate,
    ): LedgerResult<List<CurrencyTotals>>

    suspend fun categoryTotals(
        start: LocalDate,
        end: LocalDate,
    ): LedgerResult<List<CategoryTotal>>

    /** Balances after each posted entry in [entryIds]; trashed or unknown entries are omitted. */
    suspend fun runningBalances(entryIds: List<String>): LedgerResult<List<RunningBalance>>

    suspend fun page(
        status: EntryStatus = EntryStatus.POSTED,
        after: LedgerCursor? = null,
        limit: Int = 50,
    ): LedgerResult<LedgerPage>

    suspend fun search(
        filter: LedgerFilter,
        after: LedgerCursor? = null,
        limit: Int = 50,
    ): LedgerResult<LedgerPage>
}

interface EditorRepository : PostingService {
    suspend fun checkIncomplete(editor: EditorInput): LedgerResult<Unit>

    suspend fun copyEntry(id: String): LedgerResult<EditorInput>

    suspend fun applyTemplate(id: String): LedgerResult<EditorInput>
}

interface TemplateRepository {
    suspend fun templates(): LedgerResult<List<EntryTemplate>>

    suspend fun saveTemplate(template: EntryTemplate): LedgerResult<EntryTemplate>

    suspend fun deleteTemplate(id: String): LedgerResult<Unit>
}

interface BulkCategoryRepository {
    suspend fun bulkCategorize(
        selections: List<AllocationSelection>,
        categoryId: String?,
    ): LedgerResult<Int>
}

/** Composition boundary; feature state owners receive only the interfaces they use. */
data class BookServices(
    val accounts: AccountRepository,
    val categories: CategoryRepository,
    val settings: SettingsRepository,
    val tags: TagRepository,
    val reader: LedgerReader,
    val editor: EditorRepository,
    val templates: TemplateRepository,
    val bulkCategories: BulkCategoryRepository,
)

inline fun <T, R> LedgerResult<T>.map(transform: (T) -> R): LedgerResult<R> =
    when (this) {
        is LedgerResult.Success -> LedgerResult.Success(transform(value))
        is LedgerResult.Invalid -> this
        LedgerResult.Conflict -> LedgerResult.Conflict
        LedgerResult.Missing -> LedgerResult.Missing
        LedgerResult.AlreadyConfirmed -> LedgerResult.AlreadyConfirmed
        LedgerResult.TemporarilyUnavailable -> LedgerResult.TemporarilyUnavailable
        LedgerResult.RecoveryRequired -> LedgerResult.RecoveryRequired
        LedgerResult.UnexpectedFailure -> LedgerResult.UnexpectedFailure
    }
