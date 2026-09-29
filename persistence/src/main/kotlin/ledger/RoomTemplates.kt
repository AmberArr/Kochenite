package moe.frost.kochenite.persistence.ledger

import androidx.room.withTransaction
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryTemplate
import moe.frost.kochenite.domain.ledger.InvalidReason
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.validId

internal class RoomTemplates(
    private val db: LedgerDatabase,
) : moe.frost.kochenite.domain.ledger.TemplateRepository {
    private val dao = db.ledger()

    private fun invalid(reason: InvalidReason): Nothing = throw Rejected(LedgerResult.Invalid(reason))

    private fun conflict(): Nothing = throw Rejected(LedgerResult.Conflict)

    private fun missing(): Nothing = throw Rejected(LedgerResult.Missing)

    override suspend fun templates(): LedgerResult<List<EntryTemplate>> =
        storageResult {
            dao.templates().map { EntryTemplate(it.id, it.name, DraftRow(it.id, it.id, "", null, false, it.payload).model().input) }
        }

    override suspend fun saveTemplate(template: EntryTemplate): LedgerResult<EntryTemplate> =
        storageResult {
            db.withTransaction {
                if (!validId(template.id) || template.name.isBlank() || template.name.length > 100 ||
                    template.input.kind in setOf(EntryKind.OPENING, EntryKind.ADJUSTMENT) ||
                    template.input.relatedEntryIds.isNotEmpty()
                ) {
                    invalid(InvalidReason.STATE)
                }
                if (dao.template(template.id) != null) conflict()
                val row = EditorInput(template.id, template.id, "", template.input).row()
                if (row.payload.length > 500000) invalid(InvalidReason.TEXT_TOO_LONG)
                dao.insertTemplate(TemplateRow(template.id, template.name.trim(), row.payload))
                template.copy(name = template.name.trim())
            }
        }

    override suspend fun deleteTemplate(id: String): LedgerResult<Unit> =
        storageResult {
            db.withTransaction {
                if (dao.template(id) == null) missing()
                dao.deleteTemplate(id)
            }
        }
}
