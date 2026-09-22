package moe.frost.kochenite.persistence.ledger

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.persistence.PreservingSQLiteFactory

@Entity(tableName = "account_groups")
internal data class AccountGroupRow(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int,
)

@Entity(tableName = "book_settings")
internal data class BookSettingsRow(
    @PrimaryKey val id: Int = 1,
    val displayCurrency: String,
    val displayScale: Int,
    val setupComplete: Boolean,
)

@Entity(
    tableName = "accounts",
    foreignKeys = [ForeignKey(entity = AccountGroupRow::class, parentColumns = ["id"], childColumns = ["groupId"])],
    indices = [Index("currency"), Index("groupId")],
)
internal data class AccountRow(
    @PrimaryKey val id: String,
    val name: String,
    val currency: String,
    val scale: Int,
    val archived: Boolean,
    val includeInNetWorth: Boolean,
    val currencyLocked: Boolean,
    val groupId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "''") val type: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val position: Int = 0,
) {
    fun model() = Account(id, name, BookCurrency(currency, scale), archived, includeInNetWorth, currencyLocked, groupId, type, position)
}

@Entity(
    tableName = "categories",
    foreignKeys = [ForeignKey(entity = CategoryRow::class, parentColumns = ["id"], childColumns = ["parentId"])],
    indices = [Index("parentId")],
)
internal data class CategoryRow(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val parentId: String?,
    val archived: Boolean,
) {
    fun model() = Category(id, name, CategoryKind.valueOf(kind), parentId, archived)
}

@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = EntryRow::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["status", "date", "time", "id"]), Index("purchaseId"), Index(value = ["sourceDraftId"], unique = true)],
)
internal data class EntryRow(
    @PrimaryKey val id: String,
    val token: String,
    val sourceDraftId: String,
    val status: String,
    val kind: String,
    val date: String,
    val time: String,
    val accountId: String,
    val amountMinor: Long,
    val destinationAccountId: String?,
    val receivedMinor: Long?,
    val merchant: String,
    @androidx.room.ColumnInfo(defaultValue = "''") val note: String,
    val merchantMinor: Long?,
    val merchantCurrency: String?,
    val merchantScale: Int?,
    val purchaseId: String?,
)

@Entity(
    tableName = "postings",
    primaryKeys = ["entryId", "accountId"],
    foreignKeys = [
        ForeignKey(entity = EntryRow::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = AccountRow::class, parentColumns = ["id"], childColumns = ["accountId"]),
    ],
    indices = [Index("accountId")],
)
internal data class PostingRow(
    val entryId: String,
    val accountId: String,
    val amountMinor: Long,
)

@Entity(
    tableName = "allocations",
    primaryKeys = ["entryId", "id"],
    foreignKeys = [
        ForeignKey(entity = EntryRow::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = CategoryRow::class, parentColumns = ["id"], childColumns = ["categoryId"]),
    ],
    indices = [Index("categoryId")],
)
internal data class AllocationRow(
    val entryId: String,
    val id: String,
    val position: Int,
    val amountMinor: Long,
    val categoryId: String?,
)

// Draft account/category references deliberately remain free-form: unfinished input must survive edits.
@Entity(tableName = "drafts", indices = [Index("entryId")])
internal data class DraftRow(
    @PrimaryKey val id: String,
    val entryId: String,
    val token: String,
    val originalToken: String?,
    val incomplete: Boolean,
    val payload: String,
)

internal data class TotalRow(
    val currency: String,
    val scale: Int,
    val incomeMinor: Long,
    val expenseMinor: Long,
    val refundMinor: Long,
)

internal data class CategoryTotalRow(
    val rootId: String?,
    val kind: String,
    val currency: String,
    val scale: Int,
    val amountMinor: Long,
)

@Dao
internal interface LedgerDao {
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

    @Query("DELETE FROM postings WHERE entryId = :id")
    suspend fun deletePostings(id: String)

    @Query("DELETE FROM allocations WHERE entryId = :id")
    suspend fun deleteAllocations(id: String)

    @Query("SELECT * FROM allocations WHERE entryId = :id ORDER BY position")
    suspend fun allocations(id: String): List<AllocationRow>

    @Query(
        "SELECT COALESCE(SUM(p.amountMinor), 0) FROM postings p JOIN entries e ON p.entryId = e.id WHERE p.accountId = :accountId AND e.status = 'POSTED'",
    )
    suspend fun balance(accountId: String): Long

    @Query(
        "SELECT a.currency, a.scale, SUM(CASE WHEN e.kind = 'INCOME' THEN c.amountMinor ELSE 0 END) AS incomeMinor, SUM(CASE WHEN e.kind = 'EXPENSE' THEN c.amountMinor ELSE 0 END) AS expenseMinor, SUM(CASE WHEN e.kind = 'REFUND' THEN c.amountMinor ELSE 0 END) AS refundMinor FROM allocations c JOIN entries e ON c.entryId = e.id JOIN accounts a ON a.id = e.accountId WHERE e.status = 'POSTED' AND e.date BETWEEN :start AND :end GROUP BY a.currency, a.scale",
    )
    suspend fun totals(
        start: String,
        end: String,
    ): List<TotalRow>

    @Query(
        """SELECT COALESCE(c.parentId, c.id) AS rootId,
        CASE WHEN e.kind = 'INCOME' THEN 'INCOME' ELSE 'EXPENSE' END AS kind,
        a.currency, a.scale,
        SUM(CASE WHEN e.kind = 'REFUND' THEN -al.amountMinor ELSE al.amountMinor END) AS amountMinor
        FROM allocations al JOIN entries e ON e.id = al.entryId
        JOIN accounts a ON a.id = e.accountId LEFT JOIN categories c ON c.id = al.categoryId
        WHERE e.status = 'POSTED' AND e.date BETWEEN :start AND :end
        GROUP BY rootId, CASE WHEN e.kind = 'INCOME' THEN 'INCOME' ELSE 'EXPENSE' END, a.currency, a.scale""",
    )
    suspend fun categoryTotals(
        start: String,
        end: String,
    ): List<CategoryTotalRow>

    @Insert suspend fun insertDraft(row: DraftRow)

    @androidx.room.Update suspend fun updateDraft(row: DraftRow)

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun draft(id: String): DraftRow?

    @Query("SELECT * FROM drafts ORDER BY rowid DESC LIMIT :limit")
    suspend fun drafts(limit: Int): List<DraftRow>

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun deleteDraft(id: String)

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
}

@Database(
    entities = [
        AccountGroupRow::class, BookSettingsRow::class, AccountRow::class, CategoryRow::class,
        EntryRow::class, PostingRow::class, AllocationRow::class, DraftRow::class,
    ],
    version = 3,
    exportSchema = true,
)
internal abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledger(): LedgerDao
}

internal fun ledgerDatabase(
    context: Context,
    name: String,
): LedgerDatabase =
    Room
        .databaseBuilder(context, LedgerDatabase::class.java, name)
        .openHelperFactory(PreservingSQLiteFactory())
        .addMigrations(ledgerMigration1To2, ledgerMigration2To3)
        .build()

internal val ledgerMigration1To2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE entries ADD COLUMN note TEXT NOT NULL DEFAULT ''")
        }
    }

internal val ledgerMigration2To3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS account_groups (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, position INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS book_settings (id INTEGER NOT NULL PRIMARY KEY, displayCurrency TEXT NOT NULL, displayScale INTEGER NOT NULL, setupComplete INTEGER NOT NULL)",
            )
            db.execSQL("ALTER TABLE accounts ADD COLUMN groupId TEXT REFERENCES account_groups(id)")
            db.execSQL("ALTER TABLE accounts ADD COLUMN type TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE accounts ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_accounts_groupId ON accounts(groupId)")
        }
    }
