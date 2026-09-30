package moe.frost.kochenite.transactions

import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.editor.IncompleteRecord
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.CategoryTotal
import moe.frost.kochenite.domain.ledger.CurrencyTotals
import moe.frost.kochenite.domain.ledger.EditorRepository
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.LedgerCursor
import moe.frost.kochenite.domain.ledger.LedgerFilter
import moe.frost.kochenite.domain.ledger.LedgerPage
import moe.frost.kochenite.domain.ledger.LedgerReader
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.RunningBalance
import moe.frost.kochenite.domain.ledger.SavedEntry
import java.time.LocalDate
import java.time.LocalTime

internal open class FakeLedger :
    LedgerReader,
    EditorRepository {
    val account = Account("cash", "Cash", BookCurrency("USD", 2))
    var searchCall: suspend (
        LedgerFilter,
        LedgerCursor?,
    ) -> LedgerResult<LedgerPage> = { _, _ -> LedgerResult.Success(LedgerPage(emptyList(), null)) }
    var postCall: suspend (
        EditorInput,
    ) -> LedgerResult<SavedEntry> = { LedgerResult.Success(SavedEntry(it.entryId, "posted", EntryStatus.POSTED, it.input)) }

    override suspend fun search(
        filter: LedgerFilter,
        after: LedgerCursor?,
        limit: Int,
    ) = searchCall(filter, after)

    override suspend fun post(
        editor: EditorInput,
        expectedIncompleteToken: String?,
    ) = postCall(editor)

    override suspend fun entry(id: String): LedgerResult<SavedEntry> = LedgerResult.Missing

    override suspend fun incomplete(id: String): LedgerResult<IncompleteRecord> = LedgerResult.Missing

    override suspend fun incompleteRecords(): LedgerResult<List<IncompleteRecord>> = LedgerResult.Success(emptyList())

    override suspend fun balance(accountId: String): LedgerResult<Long> = LedgerResult.Success(0)

    override suspend fun totals(
        start: LocalDate,
        end: LocalDate,
    ): LedgerResult<List<CurrencyTotals>> = LedgerResult.Success(emptyList())

    override suspend fun categoryTotals(
        start: LocalDate,
        end: LocalDate,
    ): LedgerResult<List<CategoryTotal>> = LedgerResult.Success(emptyList())

    override suspend fun runningBalances(entryIds: List<String>): LedgerResult<List<RunningBalance>> = LedgerResult.Success(emptyList())

    override suspend fun page(
        status: EntryStatus,
        after: LedgerCursor?,
        limit: Int,
    ) = search(LedgerFilter(status), after, limit)

    override suspend fun beginEdit(entryId: String): LedgerResult<EditorInput> = LedgerResult.Missing

    override suspend fun checkIncomplete(editor: EditorInput): LedgerResult<Unit> = LedgerResult.Success(Unit)

    override suspend fun saveIncomplete(
        editor: EditorInput,
        expectedToken: String?,
    ): LedgerResult<IncompleteRecord> = LedgerResult.Success(IncompleteRecord(editor))

    override suspend fun deleteIncomplete(
        id: String,
        expectedToken: String,
    ): LedgerResult<Unit> = LedgerResult.Success(Unit)

    override suspend fun trash(
        entryId: String,
        expectedToken: String,
    ): LedgerResult<SavedEntry> = LedgerResult.Missing

    override suspend fun restore(
        entryId: String,
        expectedToken: String,
    ): LedgerResult<SavedEntry> = LedgerResult.Missing

    override suspend fun purge(
        entryId: String,
        expectedToken: String,
    ): LedgerResult<Unit> = LedgerResult.Missing

    override suspend fun copyEntry(id: String): LedgerResult<EditorInput> = LedgerResult.Missing

    override suspend fun applyTemplate(id: String): LedgerResult<EditorInput> = LedgerResult.Missing
}

internal fun fixtureEntry(id: String) =
    SavedEntry(
        id,
        "token-$id",
        EntryStatus.POSTED,
        EntryInput(
            EntryKind.EXPENSE,
            LocalDate.of(2026, 9, 27),
            LocalTime.NOON,
            "cash",
            -100,
            allocations = listOf(Allocation("allocation", 100)),
        ),
    )
