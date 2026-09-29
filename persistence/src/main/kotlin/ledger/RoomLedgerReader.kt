package moe.frost.kochenite.persistence.ledger

import androidx.room.withTransaction
import moe.frost.kochenite.domain.editor.IncompleteRecord
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.CategoryTotal
import moe.frost.kochenite.domain.ledger.CurrencyTotals
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.InvalidReason
import moe.frost.kochenite.domain.ledger.LedgerCursor
import moe.frost.kochenite.domain.ledger.LedgerFilter
import moe.frost.kochenite.domain.ledger.LedgerPage
import moe.frost.kochenite.domain.ledger.LedgerReader
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.Money
import moe.frost.kochenite.domain.ledger.ReceiptLine
import moe.frost.kochenite.domain.ledger.SavedEntry
import java.time.LocalDate
import java.time.LocalTime

internal class RoomLedgerReader(
    private val db: LedgerDatabase,
) : LedgerReader {
    private val dao = db.ledger()

    private fun invalid(reason: InvalidReason): Nothing = throw Rejected(LedgerResult.Invalid(reason))

    private fun missing(): Nothing = throw Rejected(LedgerResult.Missing)

    override suspend fun incomplete(id: String): LedgerResult<IncompleteRecord> =
        storageResult {
            val row = dao.incomplete(id) ?: missing()
            IncompleteRecord(row.model())
        }

    override suspend fun incompleteRecords(): LedgerResult<List<IncompleteRecord>> =
        storageResult { dao.incompleteRecords().map { IncompleteRecord(it.model()) } }

    override suspend fun entry(id: String): LedgerResult<SavedEntry> =
        storageResult {
            db.withTransaction { model(dao.entry(id) ?: missing()) }
        }

    override suspend fun balance(accountId: String): LedgerResult<Long> = storageResult { dao.balance(accountId) }

    override suspend fun totals(
        start: LocalDate,
        end: LocalDate,
    ): LedgerResult<List<CurrencyTotals>> =
        storageResult {
            if (start > end) invalid(InvalidReason.STATE)
            dao.totals(start.toString(), end.toString()).map {
                val spending = it.expenseMinor
                val total = CurrencyTotals(BookCurrency(it.currency, it.scale), it.incomeMinor, spending)
                total.netFlowMinor // Validate the representability of the derived total.
                total
            }
        }

    override suspend fun categoryTotals(
        start: LocalDate,
        end: LocalDate,
    ): LedgerResult<List<CategoryTotal>> =
        storageResult {
            if (start > end) invalid(InvalidReason.STATE)
            dao.categoryTotals(start.toString(), end.toString()).map {
                CategoryTotal(it.rootId, CategoryKind.valueOf(it.kind), BookCurrency(it.currency, it.scale), it.amountMinor)
            }
        }

    override suspend fun page(
        status: EntryStatus,
        after: LedgerCursor?,
        limit: Int,
    ): LedgerResult<LedgerPage> =
        storageResult {
            if (limit !in 1..200) invalid(InvalidReason.STATE)
            db.withTransaction {
                val rows = dao.page(status.name, after?.date?.toString(), after?.time?.toString(), after?.id, limit + 1)
                val entries = models(rows.take(limit))
                val next =
                    if (rows.size > limit) {
                        entries.last().let { LedgerCursor(it.input.date, it.input.time, it.id) }
                    } else {
                        null
                    }
                LedgerPage(entries, next)
            }
        }

    override suspend fun search(
        filter: LedgerFilter,
        after: LedgerCursor?,
        limit: Int,
    ): LedgerResult<LedgerPage> =
        storageResult {
            val from = filter.from
            val through = filter.through
            if (limit !in 1..200 || filter.text.length > 200 ||
                (from != null && through != null && from > through) ||
                ((filter.minimumMinor != null || filter.maximumMinor != null) && filter.amountCurrency == null) ||
                (filter.minimumMinor ?: 0L) < 0 || (filter.maximumMinor ?: Long.MAX_VALUE) < (filter.minimumMinor ?: 0L)
            ) {
                invalid(InvalidReason.STATE)
            }
            db.withTransaction {
                val rows =
                    dao.search(
                        filter.status.name,
                        from?.toString(),
                        through?.toString(),
                        filter.kind?.name,
                        filter.accountId,
                        filter.categoryId,
                        filter.tagId,
                        filter.text.trim(),
                        filter.amountCurrency?.code,
                        filter.amountCurrency?.scale,
                        filter.minimumMinor ?: 0L,
                        filter.maximumMinor ?: Long.MAX_VALUE,
                        after?.date?.toString(),
                        after?.time?.toString(),
                        after?.id,
                        limit + 1,
                    )
                val entries = models(rows.take(limit))
                val next = if (rows.size > limit) entries.last().let { LedgerCursor(it.input.date, it.input.time, it.id) } else null
                LedgerPage(entries, next)
            }
        }

    internal suspend fun model(row: EntryRow): SavedEntry = models(listOf(row)).single()

    // Callers hold a Room transaction so all four relation batches share the entry snapshot.
    internal suspend fun models(rows: List<EntryRow>): List<SavedEntry> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it.id }
        val allocations = dao.allocationsForEntries(ids).groupBy { it.entryId }
        val lines = dao.receiptLinesForEntries(ids).groupBy { it.entryId }
        val tags = dao.tagsForEntries(ids).groupBy { it.entryId }
        val references = dao.referencesForEntries(ids).groupBy { it.entryId }
        return rows.map { row ->
            SavedEntry(
                row.id,
                row.token,
                EntryStatus.valueOf(row.status),
                EntryInput(
                    EntryKind.valueOf(row.kind),
                    LocalDate.parse(row.date),
                    LocalTime.parse(row.time),
                    row.accountId,
                    row.amountMinor,
                    row.destinationAccountId,
                    row.receivedMinor,
                    allocations[row.id].orEmpty().map { Allocation(it.id, it.amountMinor, it.categoryId) },
                    row.merchant,
                    row.note,
                    row.merchantMinor?.let { Money(it, BookCurrency(row.merchantCurrency!!, row.merchantScale!!)) },
                    references[row.id].orEmpty().map { it.relatedId },
                    lines[row.id].orEmpty().map { ReceiptLine(it.id, it.description, it.merchantMinor) },
                    tags[row.id].orEmpty().map { it.tagId },
                ),
            )
        }
    }
}
