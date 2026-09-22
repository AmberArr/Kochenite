package moe.frost.kochenite.persistence.ledger

import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.Draft
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.Money
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

/** Explicit schema; monetary JSON values are decimal strings, never floating point. */
internal fun Draft.row(): DraftRow {
    val json =
        JSONObject()
            .put("version", 1)
            .put("kind", input.kind.name)
            .put("date", input.date.toString())
            .put("time", input.time.toString())
            .put("accountId", input.accountId)
            .put("amount", input.amountMinor?.toString())
            .put("destination", input.destinationAccountId)
            .put("received", input.receivedMinor?.toString())
            .put("merchant", input.merchant)
            .put("note", input.note)
            .put("purchase", input.purchaseId)
            .put("amountText", amountText)
            .put("dateText", dateText)
            .put("timeText", timeText)
            .put(
                "allocations",
                JSONArray().apply {
                    input.allocations.forEach {
                        put(
                            JSONObject().put("id", it.id).put("amount", it.amountMinor.toString()).put("category", it.categoryId),
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
    check(json.getInt("version") == 1)

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
    return Draft(
        id,
        entryId,
        token,
        EntryInput(
            EntryKind.valueOf(json.getString("kind")),
            LocalDate.parse(json.getString("date")),
            LocalTime.parse(json.getString("time")),
            json.getString("accountId"),
            optional("amount")?.toLong(),
            optional("destination"),
            optional("received")?.toLong(),
            allocations,
            json.getString("merchant"),
            json.getString("note"),
            money,
            optional("purchase"),
        ),
        originalToken,
        incomplete,
        optional("amountText"),
        optional("dateText"),
        optional("timeText"),
    )
}
