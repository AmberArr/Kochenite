package moe.frost.kochenite.domain.editor

import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind

data class EditorInput(
    val id: String,
    val entryId: String,
    val token: String,
    val input: EntryInput,
    val originalToken: String? = null,
    /** Preserves intermediate editor input in the live ViewModel, such as "12.". */
    val amountText: String? = null,
    val dateText: String? = null,
    val timeText: String? = null,
    val receivedText: String? = null,
    val merchantAmountText: String? = null,
    val merchantCurrencyText: String? = null,
    val allocationTexts: Map<String, String> = emptyMap(),
    val lineTexts: Map<String, String> = emptyMap(),
    val transactionCurrency: BookCurrency? = null,
    val negative: Boolean = input.amountMinor?.let { it < 0 } ?: (input.kind == EntryKind.EXPENSE),
)

/** Only explicitly saved incomplete records are durable; ordinary editor input is transient. */
data class IncompleteRecord(
    val editor: EditorInput,
) {
    val id get() = editor.id
    val token get() = editor.token
}
