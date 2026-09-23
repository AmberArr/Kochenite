package moe.frost.kochenite.persistence.ledger

import androidx.room.withTransaction
import moe.frost.kochenite.domain.ledger.InvalidReason
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.Tag
import moe.frost.kochenite.domain.ledger.TagRepository
import moe.frost.kochenite.domain.ledger.validId

internal class RoomTags(
    private val db: LedgerDatabase,
) : TagRepository {
    private val dao = db.ledger()

    override suspend fun list(): LedgerResult<List<Tag>> = storageResult { dao.tags().map { Tag(it.id, it.name, it.archived) } }

    override suspend fun save(
        tag: Tag,
        expected: Tag?,
    ): LedgerResult<Tag> =
        storageResult {
            db.withTransaction {
                if (!validId(tag.id) || tag.name.isBlank() || tag.name.length > 100) {
                    throw Rejected(LedgerResult.Invalid(InvalidReason.IDENTITY))
                }
                val old = dao.tag(tag.id)
                if (expected == null && old != null) throw Rejected(LedgerResult.Conflict)
                if (expected != null && old?.let { Tag(it.id, it.name, it.archived) } != expected) {
                    throw Rejected(LedgerResult.Conflict)
                }
                val name = tag.name.trim()
                if (dao.tags().any { it.id != tag.id && it.name.equals(name, ignoreCase = true) }) {
                    throw Rejected(LedgerResult.Invalid(InvalidReason.IDENTITY))
                }
                val row = TagRow(tag.id, name, tag.archived)
                if (old == null) dao.insertTag(row) else dao.updateTag(row)
                tag.copy(name = row.name)
            }
        }
}
