package moe.frost.kochenite.persistence.ledger

import androidx.room.withTransaction
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.AccountBalance
import moe.frost.kochenite.domain.ledger.AccountGroup
import moe.frost.kochenite.domain.ledger.AccountRepository
import moe.frost.kochenite.domain.ledger.BalanceAdjustment
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.BookSettings
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.CategoryRepository
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.InvalidReason
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.domain.ledger.SettingsRepository
import moe.frost.kochenite.domain.ledger.validId
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

internal fun <T> LedgerResult<T>.required(): T =
    when (this) {
        is LedgerResult.Success -> value
        is LedgerResult.Invalid -> throw Rejected(this)
        LedgerResult.Conflict -> throw Rejected(LedgerResult.Conflict)
        LedgerResult.Missing -> throw Rejected(LedgerResult.Missing)
        LedgerResult.AlreadyConfirmed -> throw Rejected(LedgerResult.AlreadyConfirmed)
        LedgerResult.TemporarilyUnavailable -> throw Rejected(LedgerResult.TemporarilyUnavailable)
        LedgerResult.RecoveryRequired -> throw Rejected(LedgerResult.RecoveryRequired)
        LedgerResult.UnexpectedFailure -> throw Rejected(LedgerResult.UnexpectedFailure)
    }

private fun reject(reason: InvalidReason): Nothing = throw Rejected(LedgerResult.Invalid(reason))

private fun conflict(): Nothing = throw Rejected(LedgerResult.Conflict)

private fun Account.row() =
    AccountRow(id, name, currency.code, currency.scale, archived, includeInNetWorth, currencyLocked, groupId, type, position)

internal class RoomAccounts(
    private val db: LedgerDatabase,
    private val posting: RoomLedger,
) : AccountRepository {
    private val dao = db.ledger()

    override suspend fun list(): LedgerResult<List<AccountBalance>> =
        storageResult {
            db.withTransaction {
                val balances = dao.balances().associate { it.accountId to it.balanceMinor }
                dao.accounts().map { AccountBalance(it.model(), balances[it.id] ?: 0L) }
            }
        }

    override suspend fun groups(): LedgerResult<List<AccountGroup>> =
        storageResult {
            dao.groups().map { AccountGroup(it.id, it.name, it.position) }
        }

    override suspend fun save(
        account: Account,
        expected: Account?,
        openingMinor: Long?,
    ): LedgerResult<Account> =
        storageResult {
            db.withTransaction {
                if (!validId(account.id) || account.name.isBlank() || account.name.length > 200 || account.type.length > 100) {
                    reject(InvalidReason.ACCOUNT)
                }
                val all = dao.accounts()
                val old = all.find { it.id == account.id }?.model()
                if (old != expected) conflict()
                if (account.groupId != null && dao.groups().none { it.id == account.groupId }) reject(InvalidReason.ACCOUNT)
                if (all.any { it.currency == account.currency.code && it.scale != account.currency.scale && it.id != account.id }) {
                    reject(InvalidReason.CURRENCY)
                }
                if (old == null) {
                    if (account.currencyLocked || account.archived) reject(InvalidReason.ACCOUNT)
                    posting.createAccount(account).required()
                    if (openingMinor != null) {
                        postAdjustment(account.id, openingMinor, EntryKind.OPENING, LocalDate.now(), LocalTime.now(), "")
                    }
                } else {
                    if (openingMinor != null) reject(InvalidReason.STATE)
                    if (old.currencyLocked && account.currency != old.currency) reject(InvalidReason.CURRENCY)
                    dao.updateAccount(account.copy(currencyLocked = old.currencyLocked).row())
                }
                dao.accounts().first { it.id == account.id }.model()
            }
        }

    override suspend fun saveWithAdjustment(
        account: Account,
        expected: Account,
        adjustment: BalanceAdjustment,
    ): LedgerResult<Account> =
        storageResult {
            db.withTransaction {
                val old = dao.accounts().find { it.id == account.id }?.model()
                if (old != expected) conflict()
                if (old.archived) reject(InvalidReason.ACCOUNT)
                if (dao.balance(account.id) != adjustment.expectedBalanceMinor) conflict()
                val delta = Math.subtractExact(adjustment.newBalanceMinor, adjustment.expectedBalanceMinor)
                if (delta == 0L) reject(InvalidReason.AMOUNT)
                // Post while the stored account is still active; posting locks its currency.
                postAdjustment(account.id, delta, EntryKind.ADJUSTMENT, LocalDate.now(), LocalTime.now(), adjustment.note)
                save(account, old.copy(currencyLocked = true), null).required()
            }
        }

    override suspend fun reorder(
        groupIds: List<String>,
        accountIds: List<String>,
    ): LedgerResult<Unit> =
        storageResult {
            db.withTransaction {
                val groups = dao.groups().map { it.id }
                val accounts = dao.accounts().map { it.id }
                if (groupIds.size != groups.size || groupIds.toSet() != groups.toSet() ||
                    accountIds.size != accounts.size || accountIds.toSet() != accounts.toSet()
                ) {
                    conflict()
                }
                groupIds.forEachIndexed { position, id -> dao.setGroupPosition(id, position) }
                accountIds.forEachIndexed { position, id -> dao.setAccountPosition(id, position) }
            }
        }

    override suspend fun saveGroup(
        group: AccountGroup,
        expected: AccountGroup?,
    ): LedgerResult<AccountGroup> =
        storageResult {
            db.withTransaction {
                if (!validId(group.id) || group.name.isBlank() || group.name.length > 200) reject(InvalidReason.ACCOUNT)
                val old = dao.groups().find { it.id == group.id }?.let { AccountGroup(it.id, it.name, it.position) }
                if (old != expected) conflict()
                val row = AccountGroupRow(group.id, group.name, group.position)
                if (old == null) dao.insertGroup(row) else dao.updateGroup(row)
                group
            }
        }

    override suspend fun adjust(
        accountId: String,
        deltaMinor: Long,
        date: LocalDate,
        time: LocalTime,
        note: String,
    ): LedgerResult<SavedEntry> =
        storageResult {
            db.withTransaction { postAdjustment(accountId, deltaMinor, EntryKind.ADJUSTMENT, date, time, note) }
        }

    private suspend fun postAdjustment(
        id: String,
        amount: Long,
        kind: EntryKind,
        date: LocalDate,
        time: LocalTime,
        note: String,
    ): SavedEntry {
        val input = EntryInput(kind, date, time, id, amount, note = note)
        return posting.post(EditorInput(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", input)).required()
    }
}

internal class RoomCategories(
    private val db: LedgerDatabase,
) : CategoryRepository {
    private val dao = db.ledger()

    override suspend fun list(): LedgerResult<List<Category>> = storageResult { dao.categories().map { it.model() } }

    override suspend fun save(
        category: Category,
        expected: Category?,
    ): LedgerResult<Category> =
        storageResult {
            db.withTransaction {
                if (!validId(category.id) || category.name.isBlank() || category.name.length > 200) reject(InvalidReason.CATEGORY)
                val all = dao.categories().map { it.model() }
                val old = all.find { it.id == category.id }
                if (old != expected) conflict()
                val children = all.filter { it.parentId == category.id }
                if (category.parentId != null) {
                    val parent = all.find { it.id == category.parentId } ?: reject(InvalidReason.CATEGORY)
                    if (
                        parent.id == category.id || parent.parentId != null || parent.kind != category.kind ||
                        parent.archived || children.isNotEmpty()
                    ) {
                        reject(InvalidReason.CATEGORY)
                    }
                }
                if (old != null && old.kind != category.kind && (dao.categoryUses(old.id) > 0 || children.isNotEmpty())) {
                    reject(InvalidReason.CATEGORY)
                }
                if (category.archived && children.any { !it.archived }) reject(InvalidReason.CATEGORY)
                val row = CategoryRow(category.id, category.name, category.kind.name, category.parentId, category.archived)
                if (old == null) dao.insertCategory(row) else dao.updateCategory(row)
                category
            }
        }
}

internal class RoomSettings(
    private val db: LedgerDatabase,
    private val posting: RoomLedger,
) : SettingsRepository {
    private val dao = db.ledger()

    override suspend fun get(): LedgerResult<BookSettings?> =
        storageResult {
            dao.settings()?.let { BookSettings(BookCurrency(it.displayCurrency, it.displayScale), it.setupComplete) }
        }

    override suspend fun setDisplayCurrency(currency: BookCurrency): LedgerResult<BookSettings> =
        storageResult {
            db.withTransaction {
                val old = dao.settings()
                dao.saveSettings(
                    BookSettingsRow(
                        displayCurrency = currency.code,
                        displayScale = currency.scale,
                        setupComplete =
                            old?.setupComplete ?: false,
                    ),
                )
                BookSettings(currency, old?.setupComplete ?: false)
            }
        }

    override suspend fun finishSetup(
        account: Account,
        openingMinor: Long,
        displayCurrency: BookCurrency,
        starterCategories: Boolean,
    ): LedgerResult<Unit> =
        storageResult {
            db.withTransaction {
                if (dao.settings()?.setupComplete == true) conflict()
                posting.accountRepository.save(account, openingMinor = openingMinor).required()
                if (starterCategories) {
                    listOf(
                        Category("starter-groceries", "Groceries", CategoryKind.EXPENSE),
                        Category("starter-dining", "Dining", CategoryKind.EXPENSE),
                        Category("starter-transport", "Transport", CategoryKind.EXPENSE),
                        Category("starter-household", "Household", CategoryKind.EXPENSE),
                        Category("starter-salary", "Salary", CategoryKind.INCOME),
                    ).forEach { posting.categoryRepository.save(it).required() }
                }
                dao.saveSettings(
                    BookSettingsRow(displayCurrency = displayCurrency.code, displayScale = displayCurrency.scale, setupComplete = true),
                )
            }
        }
}
