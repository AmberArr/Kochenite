package moe.frost.kochenite.persistence.ledger

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind

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

@Entity(
    tableName = "receipt_lines",
    primaryKeys = ["entryId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = AllocationRow::class,
            parentColumns = ["entryId", "id"],
            childColumns = ["entryId", "id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class ReceiptLineRow(
    val entryId: String,
    val id: String,
    val position: Int,
    val description: String,
    val merchantMinor: Long,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
internal data class TagRow(
    @PrimaryKey val id: String,
    val name: String,
    val archived: Boolean,
)

@Entity(
    tableName = "entry_tags",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = EntryRow::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagRow::class, parentColumns = ["id"], childColumns = ["tagId"]),
    ],
    indices = [Index("tagId")],
)
internal data class EntryTagRow(
    val entryId: String,
    val tagId: String,
    val position: Int,
)

@Entity(
    tableName = "entry_references",
    primaryKeys = ["entryId", "relatedId"],
    foreignKeys = [
        ForeignKey(entity = EntryRow::class, parentColumns = ["id"], childColumns = ["entryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EntryRow::class, parentColumns = ["id"], childColumns = ["relatedId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("relatedId")],
)
internal data class EntryReferenceRow(
    val entryId: String,
    val relatedId: String,
    val position: Int,
)

@Entity(tableName = "templates")
internal data class TemplateRow(
    @PrimaryKey val id: String,
    val name: String,
    val payload: String,
)

// EditorInput account/category references deliberately remain free-form: unfinished input must survive edits.
@Entity(tableName = "drafts", indices = [Index("entryId")])
internal data class DraftRow(
    @PrimaryKey val id: String,
    val entryId: String,
    val token: String,
    val originalToken: String?,
    val incomplete: Boolean,
    val payload: String,
)

internal data class BalanceRow(
    val accountId: String,
    val balanceMinor: Long,
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
