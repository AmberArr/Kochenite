package moe.frost.kochenite.domain.ledger

import java.time.LocalDate

data class Tag(
    val id: String,
    val name: String,
    val archived: Boolean = false,
)

data class LedgerFilter(
    val status: EntryStatus = EntryStatus.POSTED,
    val text: String = "",
    val from: LocalDate? = null,
    val through: LocalDate? = null,
    val accountId: String? = null,
    val categoryId: String? = null,
    val tagId: String? = null,
    val kind: EntryKind? = null,
    val amountCurrency: BookCurrency? = null,
    val minimumMinor: Long? = null,
    val maximumMinor: Long? = null,
)

interface TagRepository {
    suspend fun list(): LedgerResult<List<Tag>>

    suspend fun save(
        tag: Tag,
        expected: Tag? = null,
    ): LedgerResult<Tag>
}

data class EntryTemplate(
    val id: String,
    val name: String,
    val input: EntryInput,
)

data class AllocationSelection(
    val entryId: String,
    val allocationId: String,
    val expectedToken: String,
)
