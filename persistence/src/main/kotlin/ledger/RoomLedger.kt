package moe.frost.kochenite.persistence.ledger

import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.CategoryTotal
import moe.frost.kochenite.domain.ledger.CurrencyTotals
import moe.frost.kochenite.domain.ledger.Draft
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.InvalidReason
import moe.frost.kochenite.domain.ledger.LedgerCursor
import moe.frost.kochenite.domain.ledger.LedgerPage
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.Money
import moe.frost.kochenite.domain.ledger.PostingService
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.domain.ledger.ValidatedEntry
import moe.frost.kochenite.domain.ledger.validId
import moe.frost.kochenite.domain.ledger.validateEntry
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** Used inside a transaction to reject after a write while still rolling everything back. */
internal class Rejected(
    val result: LedgerResult<Nothing>,
) : RuntimeException()

internal suspend fun <T> storageResult(block: suspend () -> T): LedgerResult<T> =
    try {
        LedgerResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (rejected: Rejected) {
        rejected.result
    } catch (_: ArithmeticException) {
        LedgerResult.Invalid(InvalidReason.OVERFLOW)
    } catch (_: IOException) {
        LedgerResult.TemporarilyUnavailable
    } catch (_: SQLiteFullException) {
        LedgerResult.TemporarilyUnavailable
    } catch (_: SQLiteCantOpenDatabaseException) {
        LedgerResult.TemporarilyUnavailable
    } catch (_: SQLiteDatabaseLockedException) {
        LedgerResult.TemporarilyUnavailable
    } catch (_: SQLiteDiskIOException) {
        LedgerResult.TemporarilyUnavailable
    } catch (failure: SQLiteException) {
        if (failure.message?.contains("integer overflow", ignoreCase = true) == true) {
            LedgerResult.Invalid(InvalidReason.OVERFLOW)
        } else {
            LedgerResult.RecoveryRequired
        }
    } catch (_: Exception) {
        LedgerResult.RecoveryRequired
    }

/** All aggregate mutations go through this boundary. Database contents never enter diagnostics. */
class RoomLedger internal constructor(
    private val db: LedgerDatabase,
    private val today: () -> LocalDate = LocalDate::now,
    private val afterWrite: () -> Unit = {},
) : PostingService {
    private val dao = db.ledger()
    val accountRepository: moe.frost.kochenite.domain.ledger.AccountRepository = RoomAccounts(db, this)
    val categoryRepository: moe.frost.kochenite.domain.ledger.CategoryRepository = RoomCategories(db)
    val settingsRepository: moe.frost.kochenite.domain.ledger.SettingsRepository = RoomSettings(db, this)

    private fun token() = UUID.randomUUID().toString()

    private fun invalid(reason: InvalidReason): Nothing = throw Rejected(LedgerResult.Invalid(reason))

    private fun conflict(): Nothing = throw Rejected(LedgerResult.Conflict)

    private fun missing(): Nothing = throw Rejected(LedgerResult.Missing)

    suspend fun createAccount(account: Account): LedgerResult<Account> =
        storageResult {
            db.withTransaction {
                if (!validId(account.id) || account.name.isBlank() || account.name.length > 200 || account.currencyLocked) {
                    invalid(InvalidReason.ACCOUNT)
                }
                val existing = dao.accounts()
                if (existing.any { it.id == account.id }) conflict()
                if (existing.any { it.currency == account.currency.code && it.scale != account.currency.scale }) {
                    invalid(InvalidReason.CURRENCY)
                }
                dao.insertAccount(
                    AccountRow(
                        account.id,
                        account.name,
                        account.currency.code,
                        account.currency.scale,
                        account.archived,
                        account.includeInNetWorth,
                        false,
                        account.groupId,
                        account.type,
                        account.position,
                    ),
                )
                account
            }
        }

    suspend fun accounts(): LedgerResult<List<Account>> = storageResult { dao.accounts().map { it.model() } }

    suspend fun draft(id: String): LedgerResult<Draft> = storageResult { dao.draft(id)?.model() ?: missing() }

    suspend fun entry(id: String): LedgerResult<SavedEntry> = storageResult { db.withTransaction { model(dao.entry(id) ?: missing()) } }

    suspend fun balance(accountId: String): LedgerResult<Long> = storageResult { dao.balance(accountId) }

    suspend fun totals(
        start: LocalDate,
        end: LocalDate,
    ): LedgerResult<List<CurrencyTotals>> =
        storageResult {
            if (start > end) invalid(InvalidReason.STATE)
            dao.totals(start.toString(), end.toString()).map {
                val spending = Math.subtractExact(it.expenseMinor, it.refundMinor)
                val total = CurrencyTotals(BookCurrency(it.currency, it.scale), it.incomeMinor, spending)
                total.netFlowMinor // Validate the representability of the derived total.
                total
            }
        }

    suspend fun categoryTotals(
        start: LocalDate,
        end: LocalDate,
    ): LedgerResult<List<CategoryTotal>> =
        storageResult {
            if (start > end) invalid(InvalidReason.STATE)
            dao.categoryTotals(start.toString(), end.toString()).map {
                CategoryTotal(it.rootId, CategoryKind.valueOf(it.kind), BookCurrency(it.currency, it.scale), it.amountMinor)
            }
        }

    suspend fun page(
        status: EntryStatus = EntryStatus.POSTED,
        after: LedgerCursor? = null,
        limit: Int = 50,
    ): LedgerResult<LedgerPage> =
        storageResult {
            if (limit !in 1..200) invalid(InvalidReason.STATE)
            db.withTransaction {
                val rows = dao.page(status.name, after?.date?.toString(), after?.time?.toString(), after?.id, limit + 1)
                val entries = rows.take(limit).map { model(it) }
                val next =
                    if (rows.size > limit) {
                        entries.last().let { LedgerCursor(it.input.date, it.input.time, it.id) }
                    } else {
                        null
                    }
                LedgerPage(entries, next)
            }
        }

    override suspend fun saveDraft(
        draft: Draft,
        expectedToken: String?,
    ): LedgerResult<Draft> =
        storageResult {
            db.withTransaction {
                if (!validId(draft.id) || !validId(draft.entryId)) invalid(InvalidReason.IDENTITY)
                if (draft.input.merchant.length > 500 || draft.input.note.length > 10000 || draft.input.allocations.size > 1000) {
                    invalid(InvalidReason.TEXT_TOO_LONG)
                }
                val old = dao.draft(draft.id)
                if (expectedToken == null) {
                    if (old != null || dao.confirmed(draft.id) != 0) conflict()
                    if (draft.originalToken == null && dao.entry(draft.entryId) != null) conflict()
                    if (draft.originalToken != null && dao.entry(draft.entryId)?.token != draft.originalToken) conflict()
                } else if (
                    old == null || old.token != expectedToken ||
                    old.entryId != draft.entryId || old.originalToken != draft.originalToken
                ) {
                    conflict()
                }
                val saved = draft.copy(token = token())
                val row = saved.row()
                if (row.payload.length > 500000) invalid(InvalidReason.TEXT_TOO_LONG)
                if (old == null) dao.insertDraft(row) else dao.updateDraft(row)
                saved
            }
        }

    override suspend fun beginEdit(entryId: String): LedgerResult<Draft> =
        storageResult {
            db.withTransaction {
                val row = dao.entry(entryId) ?: missing()
                if (row.status != EntryStatus.POSTED.name) invalid(InvalidReason.STATE)
                val draft = Draft(token(), row.id, token(), model(row).input, row.token)
                dao.insertDraft(draft.row())
                draft
            }
        }

    private suspend fun validate(
        input: EntryInput,
        restoring: Boolean = false,
    ): ValidatedEntry {
        val accounts =
            dao
                .accounts()
                .map {
                    val account = it.model()
                    if (restoring) account.copy(archived = false) else account
                }.associateBy { it.id }
        val categories =
            dao
                .categories()
                .map {
                    val category = it.model()
                    if (restoring) category.copy(archived = false) else category
                }.associateBy { it.id }
        val result = validateEntry(input, accounts, categories, today())
        if (result is LedgerResult.Success) return result.value
        throw Rejected(result as LedgerResult.Invalid)
    }

    override suspend fun confirm(
        draftId: String,
        expectedToken: String,
    ): LedgerResult<SavedEntry> =
        storageResult {
            db.withTransaction {
                val draft =
                    dao.draft(draftId)?.model() ?: run {
                        if (dao.confirmed(draftId) > 0) throw Rejected(LedgerResult.AlreadyConfirmed)
                        missing()
                    }
                if (draft.token != expectedToken) conflict()
                val original = dao.entry(draft.entryId)
                if (draft.originalToken == null) {
                    if (original != null) conflict()
                } else if (
                    original == null || original.token != draft.originalToken ||
                    original.status != EntryStatus.POSTED.name
                ) {
                    conflict()
                }
                val valid = validate(draft.input)
                draft.input.purchaseId?.let {
                    if (it == draft.entryId || dao.entry(it)?.kind != EntryKind.EXPENSE.name) invalid(InvalidReason.REFUND_LINK)
                }
                if (
                    original?.kind == EntryKind.EXPENSE.name && draft.input.kind != EntryKind.EXPENSE &&
                    dao.refunds(original.id).isNotEmpty()
                ) {
                    invalid(InvalidReason.REFUND_LINK)
                }
                val input = valid.input
                val row =
                    EntryRow(
                        draft.entryId,
                        token(),
                        draft.id,
                        EntryStatus.POSTED.name,
                        input.kind.name,
                        input.date.toString(),
                        input.time.toString(),
                        input.accountId,
                        input.amountMinor!!,
                        input.destinationAccountId,
                        input.receivedMinor,
                        input.merchant,
                        input.note,
                        input.merchantAmount?.minor,
                        input.merchantAmount?.currency?.code,
                        input.merchantAmount?.currency?.scale,
                        input.purchaseId,
                    )
                if (original == null) {
                    dao.insertEntry(row)
                } else {
                    dao.deletePostings(row.id)
                    dao.deleteAllocations(row.id)
                    dao.updateEntry(row)
                }
                valid.postings.forEach {
                    dao.insertPosting(PostingRow(row.id, it.accountId, it.amountMinor))
                    dao.lockCurrency(it.accountId)
                }
                input.allocations.forEachIndexed { position, allocation ->
                    dao.insertAllocation(AllocationRow(row.id, allocation.id, position, allocation.amountMinor, allocation.categoryId))
                }
                afterWrite()
                // SQLite SUM detects overflow. Check both removed and newly affected accounts before commit.
                val affected = valid.postings.map { it.accountId } + listOfNotNull(original?.accountId, original?.destinationAccountId)
                affected.distinct().forEach { dao.balance(it) }
                dao.deleteDraft(draftId)
                model(row)
            }
        }

    override suspend fun trash(
        entryId: String,
        expectedToken: String,
    ): LedgerResult<SavedEntry> = setStatus(entryId, expectedToken, EntryStatus.TRASH)

    override suspend fun restore(
        entryId: String,
        expectedToken: String,
    ): LedgerResult<SavedEntry> = setStatus(entryId, expectedToken, EntryStatus.POSTED)

    private suspend fun setStatus(
        entryId: String,
        expectedToken: String,
        status: EntryStatus,
    ): LedgerResult<SavedEntry> =
        storageResult {
            db.withTransaction {
                val old = dao.entry(entryId) ?: missing()
                if (old.token != expectedToken) conflict()
                if (old.status == status.name) return@withTransaction model(old)
                if (status == EntryStatus.POSTED) validate(model(old).input, restoring = true)
                val updated = old.copy(token = token(), status = status.name)
                dao.updateEntry(updated)
                listOfNotNull(old.accountId, old.destinationAccountId).forEach { dao.balance(it) }
                afterWrite()
                model(updated)
            }
        }

    override suspend fun purge(
        entryId: String,
        expectedToken: String,
    ): LedgerResult<Unit> =
        storageResult {
            db.withTransaction {
                val old = dao.entry(entryId) ?: missing()
                if (old.token != expectedToken) conflict()
                if (old.status != EntryStatus.TRASH.name) invalid(InvalidReason.STATE)
                dao.refunds(entryId).forEach { dao.updateEntry(it.copy(purchaseId = null, token = token())) }
                dao.deleteEntry(entryId)
                afterWrite()
            }
        }

    private suspend fun model(row: EntryRow): SavedEntry {
        val allocations = dao.allocations(row.id).map { Allocation(it.id, it.amountMinor, it.categoryId) }
        val money = row.merchantMinor?.let { Money(it, BookCurrency(row.merchantCurrency!!, row.merchantScale!!)) }
        return SavedEntry(
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
                allocations,
                row.merchant,
                row.note,
                money,
                row.purchaseId,
            ),
        )
    }
}
