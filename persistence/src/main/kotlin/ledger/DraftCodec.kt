package moe.frost.kochenite.persistence.ledger

import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.Draft
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.Money
import moe.frost.kochenite.domain.ledger.ReceiptLine
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** Explicit schema; monetary JSON values are decimal strings, never floating point. */
internal fun Draft.row(): DraftRow {
    val json =
        JSONObject()
            .put("version", 2)
            .put("kind", input.kind.name)
            .put("date", input.date.toString())
            .put("time", input.time.toString())
            .put("accountId", input.accountId)
            .put("amount", input.amountMinor?.toString())
            .put("destination", input.destinationAccountId)
            .put("received", input.receivedMinor?.toString())
            .put("merchant", input.merchant)
            .put("note", input.note)
            .put("relatedEntryIds", JSONArray(input.relatedEntryIds))
            .put("amountText", amountText)
            .put("dateText", dateText)
            .put("timeText", timeText)
            .put("receivedText", receivedText)
            .put("merchantAmountText", merchantAmountText)
            .put("merchantCurrencyText", merchantCurrencyText)
            .put("allocationTexts", JSONObject(allocationTexts))
            .put("lineTexts", JSONObject(lineTexts))
            .put("tagIds", JSONArray(input.tagIds))
            .put(
                "allocations",
                JSONArray().apply {
                    input.allocations.forEach {
                        put(
                            JSONObject().put("id", it.id).put("amount", it.amountMinor.toString()).put("category", it.categoryId),
                        )
                    }
                },
            ).put(
                "receiptLines",
                JSONArray().apply {
                    input.receiptLines.forEach {
                        put(
                            JSONObject()
                                .put(
                                    "id",
                                    it.id,
                                ).put("description", it.description)
                                .put("merchantMinor", it.merchantMinor.toString()),
                        )
                    }
                },
            )
    input.merchantAmount?.let {
        json.put(
            "merchantAmount",
            JSONObject().put("amount", it.minor.toString()).put("currency", it.currency.code).put("scale", it.currency.scale),
        )
    }
    return DraftRow(id, entryId, token, originalToken, incomplete, json.toString())
}

internal fun DraftRow.model(): Draft {
    val json = JSONObject(payload)
    check(json.getInt("version") in 1..2)
    val legacy = json.getInt("version") == 1
    val legacyExpense = legacy && json.getString("kind") == "EXPENSE"

    fun optional(key: String): String? = if (json.has(key) && !json.isNull(key)) json.getString(key) else null
    val money =
        json.optJSONObject("merchantAmount")?.let {
            Money(it.getString("amount").toLong(), BookCurrency(it.getString("currency"), it.getInt("scale")))
        }
    val rows = json.getJSONArray("allocations")
    val allocations =
        (0 until rows.length()).map {
            val row = rows.getJSONObject(it)
            Allocation(row.getString("id"), row.getString("amount").toLong(), if (row.has("category")) row.getString("category") else null)
        }
    val receiptRows = json.optJSONArray("receiptLines") ?: JSONArray()
    val receiptLines =
        (0 until receiptRows.length()).map {
            val row = receiptRows.getJSONObject(it)
            ReceiptLine(row.getString("id"), row.getString("description"), row.getString("merchantMinor").toLong())
        }
    val tagRows = json.optJSONArray("tagIds") ?: JSONArray()
    val tagIds = (0 until tagRows.length()).map { tagRows.getString(it) }

    fun textMap(key: String): Map<String, String> =
        json.optJSONObject(key)?.let { objectJson ->
            objectJson.keys().asSequence().associateWith { objectJson.getString(it) }
        } ?: emptyMap()
    return Draft(
        id,
        entryId,
        token,
        EntryInput(
            EntryKind.valueOf(if (json.getString("kind") == "REFUND") "EXPENSE" else json.getString("kind")),
            LocalDate.parse(json.getString("date")),
            LocalTime.parse(json.getString("time")),
            json.getString("accountId"),
            optional("amount")?.toLong()?.let { if (legacyExpense) Math.negateExact(it) else it },
            optional("destination"),
            optional("received")?.toLong(),
            allocations,
            json.getString("merchant"),
            json.getString("note"),
            money,
            if (legacy) {
                listOfNotNull(optional("purchase"))
            } else {
                json.optJSONArray("relatedEntryIds").let { refs ->
                    if (refs == null) emptyList() else (0 until refs.length()).map { refs.getString(it) }
                }
            },
            receiptLines,
            tagIds,
        ),
        originalToken,
        incomplete,
        optional("amountText")?.let {
            if (legacyExpense && it.isNotBlank()) {
                if (it.startsWith("-")) it else "-" + it.removePrefix("+")
            } else {
                it
            }
        },
        optional("dateText"),
        optional("timeText"),
        optional("receivedText"),
        optional("merchantAmountText"),
        optional("merchantCurrencyText"),
        textMap("allocationTexts"),
        textMap("lineTexts"),
    )
}
