package moe.frost.kochenite.persistence.ledger

import androidx.room.withTransaction
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.editor.IncompleteRecord
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.AllocationSelection
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.InvalidReason
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.domain.ledger.ValidatedEntry
import moe.frost.kochenite.domain.ledger.validId
import moe.frost.kochenite.domain.ledger.validateEntry
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** All aggregate mutations go through this boundary. Database contents never enter diagnostics. */
class RoomLedger internal constructor(
    private val db: LedgerDatabase,
    private val today: () -> LocalDate = LocalDate::now,
    private val afterWrite: () -> Unit = {},
) : moe.frost.kochenite.domain.ledger.EditorRepository,
    moe.frost.kochenite.domain.ledger.LedgerReader by RoomLedgerReader(db),
    moe.frost.kochenite.domain.ledger.TemplateRepository by RoomTemplates(db),
    moe.frost.kochenite.domain.ledger.BulkCategoryRepository {
    private val reader = RoomLedgerReader(db)
    val services get() =
        moe.frost.kochenite.domain.ledger.BookServices(
            accountRepository,
            categoryRepository,
            settingsRepository,
            tagRepository,
            this,
            this,
            this,
            this,
        )

    private suspend fun model(row: EntryRow) = reader.model(row)

    private val dao = db.ledger()
    val accountRepository: moe.frost.kochenite.domain.ledger.AccountRepository = RoomAccounts(db, this)
    val categoryRepository: moe.frost.kochenite.domain.ledger.CategoryRepository = RoomCategories(db)
    val tagRepository: moe.frost.kochenite.domain.ledger.TagRepository = RoomTags(db)
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

    private fun freshInput(input: EntryInput): EntryInput {
        val ids = input.allocations.associate { it.id to token() }
        return input.copy(
            date = today(),
            time = LocalTime.now().withNano(0),
            relatedEntryIds = emptyList(),
            allocations = input.allocations.map { it.copy(id = ids.getValue(it.id)) },
            receiptLines = input.receiptLines.map { it.copy(id = ids[it.id] ?: token()) },
        )
    }

    private suspend fun newEditorInput(input: EntryInput): EditorInput {
        val fresh = freshInput(input)
        val account = dao.accounts().find { it.id == fresh.accountId }?.model()
        val draft =
            EditorInput(token(), token(), token(), fresh, amountText = fresh.amountMinor?.let { account?.currency?.formatMinor(it) } ?: "")
        return draft
    }

    override suspend fun copyEntry(id: String): LedgerResult<EditorInput> =
        storageResult {
            db.withTransaction {
                val source = dao.entry(id) ?: missing()
                newEditorInput(model(source).input)
            }
        }

    override suspend fun applyTemplate(id: String): LedgerResult<EditorInput> =
        storageResult {
            db.withTransaction {
                val row = dao.template(id) ?: missing()
                val input = DraftRow(id, id, "", null, false, row.payload).model().input
                newEditorInput(input)
            }
        }

    override suspend fun bulkCategorize(
        selections: List<AllocationSelection>,
        categoryId: String?,
    ): LedgerResult<Int> =
        storageResult {
            db.withTransaction {
                if (selections.isEmpty() || selections.size > 1000 ||
                    selections.map { it.entryId to it.allocationId }.distinct().size != selections.size
                ) {
                    invalid(InvalidReason.STATE)
                }
                val category = categoryId?.let { id -> dao.categories().find { it.id == id } ?: invalid(InvalidReason.CATEGORY) }
                if (category?.archived == true) invalid(InvalidReason.CATEGORY)
                val entries = mutableMapOf<String, EntryRow>()
                for (selection in selections) {
                    val row = dao.entry(selection.entryId) ?: conflict()
                    if (row.status != EntryStatus.POSTED.name || row.token != selection.expectedToken) conflict()
                    if (dao.allocation(selection.entryId, selection.allocationId) == null) conflict()
                    val kind = if (row.kind == EntryKind.INCOME.name) CategoryKind.INCOME else CategoryKind.EXPENSE
                    if (category != null && CategoryKind.valueOf(category.kind) != kind) invalid(InvalidReason.CATEGORY)
                    entries[row.id] = row
                }
                selections.forEach { dao.setAllocationCategory(it.entryId, it.allocationId, categoryId) }
                entries.values.forEach { dao.updateEntry(it.copy(token = token())) }
                afterWrite()
                selections.size
            }
        }

    override suspend fun deleteIncomplete(
        id: String,
        expectedToken: String,
    ): LedgerResult<Unit> =
        storageResult {
            db.withTransaction {
                val current = dao.incomplete(id) ?: missing()
                if (current.token != expectedToken) conflict()
                dao.deleteIncomplete(id)
                afterWrite()
            }
        }

    override suspend fun checkIncomplete(editor: EditorInput): LedgerResult<Unit> =
        storageResult {
            db.withTransaction {
                if (!validId(editor.id) || !validId(editor.entryId)) invalid(InvalidReason.IDENTITY)
                if (editor.originalToken != null || editor.input.kind !in setOf(EntryKind.EXPENSE, EntryKind.INCOME, EntryKind.TRANSFER)) {
                    invalid(InvalidReason.MISSING_AMOUNT)
                }
                if (editor.input.amountMinor != null &&
                    (editor.input.kind != EntryKind.TRANSFER || editor.input.receivedMinor != null)
                ) {
                    invalid(InvalidReason.STATE)
                }
                validateEditorText(editor)
                validate(editor.input, allowMissingAmounts = true)
                validateReferences(editor, null)
            }
        }

    override suspend fun saveIncomplete(
        editor: EditorInput,
        expectedToken: String?,
    ): LedgerResult<IncompleteRecord> =
        storageResult {
            db.withTransaction {
                if (!validId(editor.id) || !validId(editor.entryId)) invalid(InvalidReason.IDENTITY)
                if (editor.originalToken != null || dao.entry(editor.entryId) != null) invalid(InvalidReason.STATE)
                val input = editor.input
                if (input.kind !in setOf(EntryKind.EXPENSE, EntryKind.INCOME, EntryKind.TRANSFER)) {
                    invalid(InvalidReason.STATE)
                }
                if (input.amountMinor != null && (input.kind != EntryKind.TRANSFER || input.receivedMinor != null)) {
                    invalid(InvalidReason.STATE)
                }
                validateEditorText(editor)
                validate(input, allowMissingAmounts = true)
                validateReferences(editor, null)
                val old = dao.incomplete(editor.id)
                if (dao.confirmed(editor.id) != 0) throw Rejected(LedgerResult.AlreadyConfirmed)
                if (expectedToken == null) {
                    if (old != null) conflict()
                } else if (old == null || !old.incomplete || old.token != expectedToken || old.entryId != editor.entryId) {
                    conflict()
                }
                val saved = editor.copy(token = token())
                val row = saved.row(incomplete = true)
                if (row.payload.length > 500000) invalid(InvalidReason.TEXT_TOO_LONG)
                if (old == null) dao.insertIncomplete(row) else dao.updateIncomplete(row)
                afterWrite()
                IncompleteRecord(saved)
            }
        }

    override suspend fun beginEdit(entryId: String): LedgerResult<EditorInput> =
        storageResult {
            db.withTransaction {
                val row = dao.entry(entryId) ?: missing()
                if (row.status != EntryStatus.POSTED.name) invalid(InvalidReason.STATE)
                val draft = EditorInput(token(), row.id, token(), model(row).input, row.token)
                draft
            }
        }

    private suspend fun validate(
        input: EntryInput,
        restoring: Boolean = false,
        allowMissingAmounts: Boolean = false,
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
        val result = validateEntry(input, accounts, categories, today(), allowMissingAmounts)
        if (result is LedgerResult.Success) return result.value
        throw Rejected(result as LedgerResult.Invalid)
    }

    override suspend fun post(
        editor: EditorInput,
        expectedIncompleteToken: String?,
    ): LedgerResult<SavedEntry> =
        storageResult {
            db.withTransaction {
                if (!validId(editor.id) || !validId(editor.entryId)) invalid(InvalidReason.IDENTITY)
                if (dao.confirmed(editor.id) > 0) throw Rejected(LedgerResult.AlreadyConfirmed)
                val incomplete = dao.incomplete(editor.id)
                if (expectedIncompleteToken != null) {
                    if (incomplete == null || !incomplete.incomplete || incomplete.token != expectedIncompleteToken ||
                        incomplete.entryId != editor.entryId
                    ) {
                        conflict()
                    }
                } else if (incomplete != null) {
                    conflict()
                }
                val original = dao.entry(editor.entryId)
                if (editor.originalToken == null) {
                    if (original != null) conflict()
                } else if (original == null || original.token != editor.originalToken || original.status != EntryStatus.POSTED.name) {
                    conflict()
                }
                validateEditorText(editor)
                val valid = validate(editor.input)
                validateReferences(editor, original)
                val input = valid.input
                val row =
                    EntryRow(
                        editor.entryId,
                        token(),
                        editor.id,
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
                        null,
                    )
                if (original == null) {
                    dao.insertEntry(row)
                } else {
                    dao.deleteReferences(row.id)
                    dao.deletePostings(row.id)
                    dao.deleteReceiptLines(row.id)
                    dao.deleteEntryTags(row.id)
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
                input.receiptLines.forEachIndexed { position, line ->
                    dao.insertReceiptLine(ReceiptLineRow(row.id, line.id, position, line.description, line.merchantMinor))
                }
                input.relatedEntryIds.forEachIndexed { position, id -> dao.insertReference(EntryReferenceRow(row.id, id, position)) }
                input.tagIds.forEachIndexed { position, id -> dao.insertEntryTag(EntryTagRow(row.id, id, position)) }
                afterWrite()
                // SQLite SUM detects overflow. Check both removed and newly affected accounts before commit.
                val affected = valid.postings.map { it.accountId } + listOfNotNull(original?.accountId, original?.destinationAccountId)
                affected.distinct().forEach { dao.balance(it) }
                if (expectedIncompleteToken != null) dao.deleteIncomplete(editor.id)
                model(row)
            }
        }

    private suspend fun validateEditorText(editor: EditorInput) {
        if ((editor.amountText?.length ?: 0) > 100 || (editor.merchantAmountText?.length ?: 0) > 100 ||
            (editor.receivedText?.length ?: 0) > 100 || (editor.merchantCurrencyText?.length ?: 0) > 10 ||
            (editor.dateText?.length ?: 0) > 40 || (editor.timeText?.length ?: 0) > 40 ||
            editor.lineTexts.size > 1000 || editor.allocationTexts.size > 1000 ||
            (editor.lineTexts.values + editor.allocationTexts.values).any { it.length > 100 }
        ) {
            invalid(InvalidReason.TEXT_TOO_LONG)
        }
        editor.dateText?.let { if (runCatching { LocalDate.parse(it) }.getOrNull() != editor.input.date) invalid(InvalidReason.DATE) }
        editor.timeText?.let { if (runCatching { LocalTime.parse(it) }.getOrNull() != editor.input.time) invalid(InvalidReason.DATE) }
        editor.amountText?.takeIf { it.isNotBlank() }?.let { text ->
            val account = dao.accounts().find { it.id == editor.input.accountId } ?: invalid(InvalidReason.ACCOUNT)
            if (runCatching { account.model().currency.parseMinor(text) }.getOrNull() != editor.input.amountMinor) {
                invalid(InvalidReason.AMOUNT)
            }
        }
        editor.receivedText?.takeIf { it.isNotBlank() }?.let { text ->
            val account = dao.accounts().find { it.id == editor.input.destinationAccountId } ?: invalid(InvalidReason.ACCOUNT)
            if (runCatching { account.model().currency.parseMinor(text) }.getOrNull() != editor.input.receivedMinor) {
                invalid(InvalidReason.AMOUNT)
            }
        }
        if (!editor.merchantAmountText.isNullOrBlank() || !editor.merchantCurrencyText.isNullOrBlank()) {
            val merchant = editor.input.merchantAmount ?: invalid(InvalidReason.AMOUNT)
            if (editor.merchantCurrencyText?.trim()?.uppercase() != merchant.currency.code ||
                runCatching { merchant.currency.parseMinor(editor.merchantAmountText.orEmpty()) }.getOrNull() != merchant.minor
            ) {
                invalid(InvalidReason.AMOUNT)
            }
        }
        val currency =
            dao
                .accounts()
                .find { it.id == editor.input.accountId }
                ?.model()
                ?.currency ?: invalid(InvalidReason.ACCOUNT)
        for ((id, text) in editor.allocationTexts) {
            val allocation = editor.input.allocations.find { it.id == id } ?: invalid(InvalidReason.ALLOCATIONS)
            if (runCatching { currency.parseMinor(text) }.getOrNull() != allocation.amountMinor) invalid(InvalidReason.ALLOCATIONS)
        }
        val lineCurrency = editor.input.merchantAmount?.currency ?: currency
        for ((id, text) in editor.lineTexts) {
            val line = editor.input.receiptLines.find { it.id == id } ?: invalid(InvalidReason.ALLOCATIONS)
            if (runCatching { lineCurrency.parseMinor(text) }.getOrNull() != line.merchantMinor) invalid(InvalidReason.ALLOCATIONS)
        }
    }

    private suspend fun validateReferences(
        editor: EditorInput,
        original: EntryRow?,
    ) {
        val priorTagIds = if (original == null) emptyList() else dao.entryTags(original.id)
        for (id in editor.input.tagIds) {
            val tag = dao.tag(id) ?: invalid(InvalidReason.TAG)
            if (tag.archived && id !in priorTagIds) invalid(InvalidReason.TAG)
        }
        val priorReferences = if (original == null) emptyList() else dao.references(original.id)
        editor.input.relatedEntryIds.forEach { id ->
            val target = dao.entry(id)
            if (id == editor.entryId || target == null ||
                (target.status != EntryStatus.POSTED.name && id !in priorReferences)
            ) {
                invalid(InvalidReason.RELATED_ENTRY)
            }
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
                dao.referencing(entryId).forEach { referencing ->
                    val remaining = dao.references(referencing.id).filterNot { it == entryId }
                    dao.deleteReferences(referencing.id)
                    remaining.forEachIndexed { position, related ->
                        dao.insertReference(EntryReferenceRow(referencing.id, related, position))
                    }
                    dao.updateEntry(referencing.copy(token = token()))
                }
                dao.deleteEntry(entryId)
                afterWrite()
            }
        }
}
