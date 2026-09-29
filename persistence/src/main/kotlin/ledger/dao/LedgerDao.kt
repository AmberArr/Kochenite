package moe.frost.kochenite.persistence.ledger

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface LedgerDao {
    @Query("SELECT * FROM allocations WHERE entryId IN (:ids) ORDER BY entryId, position")
    suspend fun allocationsForEntries(ids: List<String>): List<AllocationRow>

    @Query("SELECT * FROM receipt_lines WHERE entryId IN (:ids) ORDER BY entryId, position")
    suspend fun receiptLinesForEntries(ids: List<String>): List<ReceiptLineRow>

    @Query("SELECT * FROM entry_tags WHERE entryId IN (:ids) ORDER BY entryId, position")
    suspend fun tagsForEntries(ids: List<String>): List<EntryTagRow>

    @Query("SELECT * FROM entry_references WHERE entryId IN (:ids) ORDER BY entryId, position")
    suspend fun referencesForEntries(ids: List<String>): List<EntryReferenceRow>

    @Query("SELECT relatedId FROM entry_references WHERE entryId = :id ORDER BY position")
    suspend fun references(id: String): List<String>

    @Query("SELECT e.* FROM entries e JOIN entry_references r ON e.id = r.entryId WHERE r.relatedId = :id")
    suspend fun referencing(id: String): List<EntryRow>

    @Insert suspend fun insertReference(row: EntryReferenceRow)

    @Query("DELETE FROM entry_references WHERE entryId = :id")
    suspend fun deleteReferences(id: String)

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE, id")
    suspend fun tags(): List<TagRow>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun tag(id: String): TagRow?

    @Insert suspend fun insertTag(row: TagRow)

    @androidx.room.Update suspend fun updateTag(row: TagRow)

    @Insert suspend fun insertEntryTag(row: EntryTagRow)

    @Query("DELETE FROM entry_tags WHERE entryId = :id")
    suspend fun deleteEntryTags(id: String)

    @Query("SELECT tagId FROM entry_tags WHERE entryId = :id ORDER BY position")
    suspend fun entryTags(id: String): List<String>

    @Query("SELECT * FROM templates ORDER BY name COLLATE NOCASE, id")
    suspend fun templates(): List<TemplateRow>

    @Query("SELECT * FROM templates WHERE id = :id")
    suspend fun template(id: String): TemplateRow?

    @Insert suspend fun insertTemplate(row: TemplateRow)

    @Query("DELETE FROM templates WHERE id = :id")
    suspend fun deleteTemplate(id: String)

    @androidx.room.Update suspend fun updateAccount(row: AccountRow)

    @Query("SELECT * FROM account_groups ORDER BY position, id")
    suspend fun groups(): List<AccountGroupRow>

    @Insert suspend fun insertGroup(row: AccountGroupRow)

    @androidx.room.Update suspend fun updateGroup(row: AccountGroupRow)

    @androidx.room.Update suspend fun updateCategory(row: CategoryRow)

    @Query("SELECT count(*) FROM allocations WHERE categoryId = :id")
    suspend fun categoryUses(id: String): Int

    @Query("SELECT * FROM book_settings WHERE id = 1")
    suspend fun settings(): BookSettingsRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(row: BookSettingsRow)

    @Insert suspend fun insertAccount(row: AccountRow)

    @Query("SELECT * FROM accounts ORDER BY position, id")
    suspend fun accounts(): List<AccountRow>

    @Query("UPDATE accounts SET currencyLocked = 1 WHERE id = :id")
    suspend fun lockCurrency(id: String)

    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun categories(): List<CategoryRow>

    @Insert suspend fun insertCategory(row: CategoryRow)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(row: EntryRow)

    @androidx.room.Update suspend fun updateEntry(row: EntryRow)

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun entry(id: String): EntryRow?

    @Query("SELECT count(*) FROM entries WHERE sourceDraftId = :id")
    suspend fun confirmed(id: String): Int

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: String)

    @Query("SELECT * FROM entries WHERE purchaseId = :id")
    suspend fun refunds(id: String): List<EntryRow>

    @Insert suspend fun insertPosting(row: PostingRow)

    @Insert suspend fun insertAllocation(row: AllocationRow)

    @Insert suspend fun insertReceiptLine(row: ReceiptLineRow)

    @Query("DELETE FROM postings WHERE entryId = :id")
    suspend fun deletePostings(id: String)

    @Query("DELETE FROM allocations WHERE entryId = :id")
    suspend fun deleteAllocations(id: String)

    @Query("DELETE FROM receipt_lines WHERE entryId = :id")
    suspend fun deleteReceiptLines(id: String)

    @Query("SELECT * FROM allocations WHERE entryId = :id ORDER BY position")
    suspend fun allocations(id: String): List<AllocationRow>

    @Query("SELECT * FROM allocations WHERE entryId = :entryId AND id = :id")
    suspend fun allocation(
        entryId: String,
        id: String,
    ): AllocationRow?

    @Query("UPDATE allocations SET categoryId = :categoryId WHERE entryId = :entryId AND id = :id")
    suspend fun setAllocationCategory(
        entryId: String,
        id: String,
        categoryId: String?,
    )

    @Query("SELECT * FROM receipt_lines WHERE entryId = :id ORDER BY position")
    suspend fun receiptLines(id: String): List<ReceiptLineRow>

    @Query(
        "SELECT COALESCE(SUM(p.amountMinor), 0) FROM postings p JOIN entries e ON p.entryId = e.id WHERE p.accountId = :accountId AND e.status = 'POSTED'",
    )
    suspend fun balance(accountId: String): Long

    @Query(
        "SELECT p.accountId, SUM(p.amountMinor) AS balanceMinor FROM postings p JOIN entries e ON p.entryId = e.id WHERE e.status = 'POSTED' GROUP BY p.accountId",
    )
    suspend fun balances(): List<BalanceRow>

    @Query(
        "SELECT a.currency, a.scale, SUM(CASE WHEN e.kind = 'INCOME' THEN CASE WHEN e.amountMinor < 0 THEN -c.amountMinor ELSE c.amountMinor END ELSE 0 END) AS incomeMinor, SUM(CASE WHEN e.kind = 'EXPENSE' THEN CASE WHEN e.amountMinor > 0 THEN -c.amountMinor ELSE c.amountMinor END ELSE 0 END) AS expenseMinor, 0 AS refundMinor FROM allocations c JOIN entries e ON c.entryId = e.id JOIN accounts a ON a.id = e.accountId WHERE e.status = 'POSTED' AND e.date BETWEEN :start AND :end GROUP BY a.currency, a.scale",
    )
    suspend fun totals(
        start: String,
        end: String,
    ): List<TotalRow>

    @Query(
        """SELECT COALESCE(c.parentId, c.id) AS rootId,
        CASE WHEN e.kind = 'INCOME' THEN 'INCOME' ELSE 'EXPENSE' END AS kind,
        a.currency, a.scale,
        SUM(CASE WHEN (e.kind = 'EXPENSE' AND e.amountMinor > 0) OR (e.kind = 'INCOME' AND e.amountMinor < 0) THEN -al.amountMinor ELSE al.amountMinor END) AS amountMinor
        FROM allocations al JOIN entries e ON e.id = al.entryId
        JOIN accounts a ON a.id = e.accountId LEFT JOIN categories c ON c.id = al.categoryId
        WHERE e.status = 'POSTED' AND e.date BETWEEN :start AND :end
        GROUP BY rootId, CASE WHEN e.kind = 'INCOME' THEN 'INCOME' ELSE 'EXPENSE' END, a.currency, a.scale""",
    )
    suspend fun categoryTotals(
        start: String,
        end: String,
    ): List<CategoryTotalRow>

    @Insert suspend fun insertIncomplete(row: DraftRow)

    @androidx.room.Update suspend fun updateIncomplete(row: DraftRow)

    @Query("SELECT * FROM drafts WHERE id = :id AND incomplete = 1")
    suspend fun incomplete(id: String): DraftRow?

    @Query("SELECT * FROM drafts WHERE incomplete = 1")
    suspend fun incompleteRecords(): List<DraftRow>

    @Query("DELETE FROM drafts WHERE id = :id AND incomplete = 1")
    suspend fun deleteIncomplete(id: String)

    @Query(
        "SELECT * FROM entries WHERE status = :status AND (:date IS NULL OR date < :date OR (date = :date AND time < :time) OR (date = :date AND time = :time AND id < :id)) ORDER BY date DESC, time DESC, id DESC LIMIT :limit",
    )
    suspend fun page(
        status: String,
        date: String?,
        time: String?,
        id: String?,
        limit: Int,
    ): List<EntryRow>

    @Query(
        """SELECT e.* FROM entries e WHERE e.status = :status
        AND (:from IS NULL OR e.date >= :from) AND (:through IS NULL OR e.date <= :through)
        AND (:kind IS NULL OR e.kind = :kind)
        AND (:accountId IS NULL OR EXISTS (SELECT 1 FROM postings p WHERE p.entryId = e.id AND p.accountId = :accountId))
        AND (:categoryId IS NULL OR EXISTS (
            SELECT 1 FROM allocations al LEFT JOIN categories c ON c.id = al.categoryId
            WHERE al.entryId = e.id AND (al.categoryId = :categoryId OR c.parentId = :categoryId)))
        AND (:tagId IS NULL OR EXISTS (SELECT 1 FROM entry_tags et WHERE et.entryId = e.id AND et.tagId = :tagId))
        AND (:text = '' OR instr(lower(e.merchant), lower(:text)) > 0 OR instr(lower(e.note), lower(:text)) > 0
            OR EXISTS (SELECT 1 FROM receipt_lines r WHERE r.entryId = e.id AND instr(lower(r.description), lower(:text)) > 0))
        AND (:amountCurrency IS NULL OR EXISTS (
            SELECT 1 FROM postings p JOIN accounts a ON a.id = p.accountId
            WHERE p.entryId = e.id AND a.currency = :amountCurrency AND a.scale = :amountScale
            AND ((p.amountMinor BETWEEN :minimum AND :maximum) OR (p.amountMinor BETWEEN -:maximum AND -:minimum))))
        AND (:date IS NULL OR e.date < :date OR (e.date = :date AND e.time < :time)
            OR (e.date = :date AND e.time = :time AND e.id < :id))
        ORDER BY e.date DESC, e.time DESC, e.id DESC LIMIT :limit""",
    )
    suspend fun search(
        status: String,
        from: String?,
        through: String?,
        kind: String?,
        accountId: String?,
        categoryId: String?,
        tagId: String?,
        text: String,
        amountCurrency: String?,
        amountScale: Int?,
        minimum: Long,
        maximum: Long,
        date: String?,
        time: String?,
        id: String?,
        limit: Int,
    ): List<EntryRow>
}
