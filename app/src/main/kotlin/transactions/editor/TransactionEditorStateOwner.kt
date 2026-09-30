package moe.frost.kochenite.transactions.editor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.editor.prepared
import moe.frost.kochenite.domain.editor.recalculate
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.EditorRepository
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.LedgerReader
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.SavedEntry
import moe.frost.kochenite.domain.ledger.map
import moe.frost.kochenite.failure
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class EntryEditorState(
    val input: EditorInput,
    val initial: EditorInput = input,
    val incompleteToken: String? = null,
    val saving: Boolean = false,
    val error: Int? = null,
    val confirmIncomplete: Boolean = false,
    val confirmDiscard: Boolean = false,
    val itemsOpen: Boolean = false,
    val itemId: String? = null,
    val focusTitle: Boolean = false,
) {
    val changed get() = input != initial
}

/** In-memory editor lifecycle; no saved-state or database autosave. */
internal class TransactionEditorStateOwner(
    private val scope: CoroutineScope,
    private val posting: () -> EditorRepository?,
    private val reading: () -> LedgerReader?,
    private val accounts: () -> List<Account>,
    private val error: (Int) -> Unit,
    private val onSaved: suspend (SavedEntry?) -> Unit,
) {
    private val mutableEditor = MutableStateFlow<EntryEditorState?>(null)
    val editor = mutableEditor.asStateFlow()
    private var leaveEditor: (() -> Unit)? = null

    private fun activate(
        input: EditorInput,
        incompleteToken: String? = null,
        focusTitle: Boolean = false,
    ) {
        val prepared = input.prepared(accounts())
        mutableEditor.value = EntryEditorState(prepared, incompleteToken = incompleteToken, focusTitle = focusTitle)
    }

    fun newEditor(id: String) {
        if (mutableEditor.value != null) return
        val accountId =
            accounts().firstOrNull { !it.archived }?.id ?: return
        activate(
            EditorInput(
                id,
                UUID.randomUUID().toString(),
                "",
                EntryInput(EntryKind.EXPENSE, LocalDate.now(), LocalTime.now().withNano(0), accountId, null),
                amountText = "",
            ),
            focusTitle = true,
        )
    }

    private var opening: Job? = null

    /**
     * Loads and shows one editor. [navigate] returns false when its origin screen is no longer current;
     * the loaded editor is then dropped so no editor state exists without its route.
     */
    private fun open(
        navigate: (String) -> Boolean,
        load: suspend () -> LedgerResult<Pair<EditorInput, String?>>?,
    ) {
        if (mutableEditor.value != null || opening?.isActive == true) return
        opening =
            scope.launch {
                val result = load() ?: return@launch
                if (result !is LedgerResult.Success) {
                    error(failure(result))
                    return@launch
                }
                if (mutableEditor.value != null) return@launch
                val (input, incompleteToken) = result.value
                activate(input, incompleteToken)
                if (!navigate(input.id)) mutableEditor.value = null
            }
    }

    fun openIncomplete(
        id: String,
        navigate: (String) -> Boolean,
    ) = open(navigate) { reading()?.incomplete(id)?.map { it.editor to it.token } }

    fun startEdit(
        entryId: String,
        navigate: (String) -> Boolean,
    ) = open(navigate) { posting()?.beginEdit(entryId)?.map { it to null } }

    fun copyEntry(
        entryId: String,
        navigate: (String) -> Boolean,
    ) = open(navigate) { posting()?.copyEntry(entryId)?.map { it to null } }

    fun applyTemplate(
        id: String,
        navigate: (String) -> Boolean,
    ) = open(navigate) { posting()?.applyTemplate(id)?.map { it to null } }

    fun startLinkedRefund(
        purchase: SavedEntry,
        allocationId: String,
        navigate: (String) -> Boolean,
    ) {
        if (mutableEditor.value != null || opening?.isActive == true) return
        val allocation = purchase.input.allocations.find { it.id == allocationId } ?: return
        val input =
            EntryInput(
                EntryKind.EXPENSE,
                LocalDate.now(),
                LocalTime.now().withNano(0),
                purchase.input.accountId,
                allocation.amountMinor,
                allocations = listOf(Allocation(UUID.randomUUID().toString(), allocation.amountMinor, allocation.categoryId)),
                merchant = purchase.input.merchant,
                relatedEntryIds = listOf(purchase.id),
            )
        val editor = EditorInput(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", input)
        activate(editor)
        if (!navigate(editor.id)) mutableEditor.value = null
    }

    fun updateEditor(change: (EditorInput) -> EditorInput) {
        val old = mutableEditor.value ?: return
        if (old.saving) return
        val updated = change(old.input)
        val changed =
            updated.recalculate(
                accounts(),
                forceAllocations =
                    updated.input.receiptLines.map { it.id } !=
                        old.input.input.receiptLines
                            .map { it.id },
            )
        mutableEditor.value = old.copy(input = changed, error = null, confirmIncomplete = false)
    }

    fun titleFocused() {
        mutableEditor.value = mutableEditor.value?.copy(focusTitle = false)
    }

    fun showItems(
        open: Boolean,
        itemId: String? = null,
    ) {
        mutableEditor.value = mutableEditor.value?.copy(itemsOpen = open, itemId = itemId)
    }

    fun showItem(id: String?) {
        mutableEditor.value = mutableEditor.value?.copy(itemId = id)
    }

    fun closeEditor(onClosed: () -> Unit) {
        val current = mutableEditor.value ?: return onClosed()
        if (current.saving) return
        if (current.itemId != null) {
            showItem(null)
            return
        }
        if (current.itemsOpen) {
            showItems(false)
            return
        }
        if (current.changed) {
            leaveEditor = onClosed
            mutableEditor.value = current.copy(confirmDiscard = true)
        } else {
            mutableEditor.value = null
            onClosed()
        }
    }

    fun keepEditing() {
        leaveEditor = null
        mutableEditor.value = mutableEditor.value?.copy(confirmDiscard = false, confirmIncomplete = false)
    }

    fun discardEditor() {
        mutableEditor.value = null
        val action = leaveEditor
        leaveEditor = null
        action?.invoke()
    }

    fun dismissError() {
        mutableEditor.value = mutableEditor.value?.copy(error = null)
    }

    /** Drops the editor without confirmation; its route closes once it observes the missing state. */
    fun abandonEditor() {
        if (mutableEditor.value?.saving == true) return
        leaveEditor = null
        mutableEditor.value = null
    }

    fun confirmEditor(allowIncomplete: Boolean = false) {
        val old = mutableEditor.value ?: return
        if (old.saving) return
        val book = posting() ?: return
        // Mark busy synchronously: repeated taps cannot enqueue multiple submissions.
        mutableEditor.value = old.copy(saving = true, error = null, confirmIncomplete = false)
        scope.launch {
            val input = old.input
            val unknown =
                input.input.amountMinor == null ||
                    (input.input.kind == EntryKind.TRANSFER && input.input.receivedMinor == null)
            val result =
                if (unknown && input.originalToken == null) {
                    val validation = book.checkIncomplete(input)
                    if (validation !is LedgerResult.Success) {
                        validation
                    } else if (!allowIncomplete) {
                        mutableEditor.value = old.copy(confirmIncomplete = true)
                        return@launch
                    } else {
                        book.saveIncomplete(input, old.incompleteToken)
                    }
                } else {
                    book.post(input, old.incompleteToken)
                }
            if (result is LedgerResult.Success) {
                mutableEditor.value = null
                onSaved(result.value as? SavedEntry)
            } else {
                mutableEditor.value = old.copy(saving = false, error = failure(result))
            }
        }
    }
}
