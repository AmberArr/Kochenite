package moe.frost.kochenite.domain.editor

import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.Money
import moe.frost.kochenite.domain.ledger.ReceiptLine
import moe.frost.kochenite.domain.ledger.proportionalAllocations
import java.util.UUID

/** Transient editor operations. These functions never perform storage or rate lookup. */
fun EditorInput.prepared(accounts: List<Account>): EditorInput {
    val account = accounts.find { it.id == input.accountId } ?: return this
    val destination = accounts.find { it.id == input.destinationAccountId }
    val currency = transactionCurrency ?: input.merchantAmount?.currency ?: account.currency
    val rows =
        input.allocations.ifEmpty {
            if (input.kind == EntryKind.TRANSFER) emptyList() else listOf(Allocation(UUID.randomUUID().toString(), 0))
        }
    val lines =
        if (input.receiptLines.isEmpty() && rows.size > 1) {
            rows.map { ReceiptLine(it.id, "", it.amountMinor) }
        } else {
            input.receiptLines
        }
    return copy(
        transactionCurrency = currency,
        amountText = amountText ?: input.amountMinor?.let(account.currency::formatMinor).orEmpty(),
        receivedText = receivedText ?: input.receivedMinor?.let { destination?.currency?.formatMinor(it) }.orEmpty(),
        merchantAmountText = merchantAmountText ?: input.merchantAmount?.let { it.currency.formatMinor(it.minor) },
        merchantCurrencyText = if (currency != account.currency) currency.code else null,
        lineTexts = lines.associate { it.id to (lineTexts[it.id] ?: currency.formatMinor(it.merchantMinor)) },
        input = input.copy(allocations = rows, receiptLines = lines),
    ).recalculate(accounts, forceAllocations = input.allocations.isEmpty())
}

fun EditorInput.mainText(accounts: List<Account>): String =
    if (transactionCurrency != accounts.find { it.id == input.accountId }?.currency && input.kind != EntryKind.TRANSFER) {
        merchantAmountText.orEmpty()
    } else {
        amountText.orEmpty().removePrefix("-").removePrefix("+")
    }

fun EditorInput.withMainText(
    text: String,
    accounts: List<Account>,
): EditorInput {
    val unsigned = text.removePrefix("-").removePrefix("+")
    val foreign = transactionCurrency != accounts.find { it.id == input.accountId }?.currency && input.kind != EntryKind.TRANSFER
    return (if (foreign) copy(merchantAmountText = unsigned) else copy(amountText = unsigned)).recalculate(accounts)
}

fun EditorInput.withCurrency(
    currency: BookCurrency,
    accounts: List<Account>,
): EditorInput {
    if (currency == transactionCurrency) return this
    val text = mainText(accounts)
    val accountCurrency = accounts.find { it.id == input.accountId }?.currency
    return if (currency == accountCurrency) {
        copy(transactionCurrency = currency, amountText = text, merchantAmountText = null, merchantCurrencyText = null)
    } else {
        copy(
            transactionCurrency = currency,
            merchantAmountText = text,
            merchantCurrencyText = currency.code,
            amountText = if (transactionCurrency == accountCurrency) "" else amountText,
        )
    }.recalculate(accounts)
}

fun EditorInput.withAccount(
    id: String,
    accounts: List<Account>,
    destination: Boolean = false,
): EditorInput {
    val chosen = accounts.find { it.id == id } ?: return this
    if (destination) {
        val oldCurrency = accounts.find { it.id == input.destinationAccountId }?.currency
        return copy(
            input = input.copy(destinationAccountId = id),
            receivedText = if (oldCurrency == chosen.currency) receivedText else "",
        ).recalculate(accounts)
    }
    val oldCurrency = accounts.find { it.id == input.accountId }?.currency
    if (oldCurrency == chosen.currency) return copy(input = input.copy(accountId = id)).recalculate(accounts)
    if (input.kind == EntryKind.TRANSFER) {
        val toCurrency = accounts.find { it.id == input.destinationAccountId }?.currency
        return copy(
            input = input.copy(accountId = id),
            transactionCurrency = chosen.currency,
            amountText = "",
            receivedText = if (toCurrency == chosen.currency) "" else receivedText,
        ).recalculate(accounts)
    }
    val foreign = transactionCurrency != oldCurrency && input.kind != EntryKind.TRANSFER
    val text = mainText(accounts)
    return copy(
        input = input.copy(accountId = id),
        transactionCurrency = if (foreign) transactionCurrency else chosen.currency,
        amountText = if (foreign && transactionCurrency == chosen.currency) text else "",
        merchantAmountText = if (foreign && transactionCurrency != chosen.currency) text else null,
    ).recalculate(accounts)
}

fun EditorInput.withKind(
    kind: EntryKind,
    accounts: List<Account>,
): EditorInput {
    val text = mainText(accounts)
    // Categories are reset when the kind changes; their old kind must never silently survive.
    return copy(
        negative = kind == EntryKind.EXPENSE,
        transactionCurrency = if (kind == EntryKind.TRANSFER) accounts.find { it.id == input.accountId }?.currency else transactionCurrency,
        amountText = if (kind == EntryKind.TRANSFER) text else amountText,
        receivedText = if (kind == EntryKind.TRANSFER) receivedText else null,
        input =
            input.copy(
                kind = kind,
                destinationAccountId = if (kind == EntryKind.TRANSFER) input.destinationAccountId else null,
                receivedMinor = null,
                allocations =
                    when {
                        kind == EntryKind.TRANSFER -> emptyList()
                        kind == input.kind -> input.allocations
                        else -> input.allocations.map { it.copy(categoryId = null) }
                    },
                receiptLines = if (kind == EntryKind.TRANSFER) emptyList() else input.receiptLines,
            ),
        lineTexts = if (kind == EntryKind.TRANSFER) emptyMap() else lineTexts,
    ).prepared(accounts)
}

fun EditorInput.recalculate(
    accounts: List<Account>,
    forceAllocations: Boolean = false,
): EditorInput {
    val account = accounts.find { it.id == input.accountId } ?: return this
    val currency = transactionCurrency ?: account.currency
    val transfer = input.kind == EntryKind.TRANSFER
    val foreign = !transfer && currency != account.currency

    fun parse(
        text: String?,
        c: BookCurrency,
    ): Long? = text?.takeIf { it.isNotBlank() }?.let { runCatching { c.parseMinor(it) }.getOrNull() }
    val unsignedText = amountText.orEmpty().removePrefix("-").removePrefix("+")
    val signedText = if (!transfer && negative && unsignedText.isNotEmpty()) "-$unsignedText" else unsignedText
    val actual = parse(signedText, account.currency)
    val merchant = if (foreign) parse(merchantAmountText, currency)?.let { Money(it, currency) } else null
    val destination = accounts.find { it.id == input.destinationAccountId }
    val received =
        when {
            !transfer -> null
            destination?.currency == account.currency -> unsignedText
            else -> receivedText
        }
    val lines = input.receiptLines.map { line -> line.copy(merchantMinor = parse(lineTexts[line.id], currency) ?: 0) }
    val magnitude = actual?.let { runCatching { Math.absExact(it) }.getOrNull() }
    val shares =
        if (foreign && magnitude != null && lines.isNotEmpty()) {
            (proportionalAllocations(magnitude, lines.map { it.merchantMinor }) as? LedgerResult.Success)?.value
        } else {
            null
        }
    val previousMagnitude = input.amountMinor?.let { runCatching { Math.absExact(it) }.getOrNull() }
    val amountsChanged = magnitude != previousMagnitude || lines != input.receiptLines
    val rows =
        if (!forceAllocations && !amountsChanged) {
            input.allocations
        } else if (lines.isEmpty()) {
            input.allocations.map { it.copy(amountMinor = magnitude ?: 0) }
        } else {
            input.allocations.mapIndexed { index, row ->
                row.copy(amountMinor = if (foreign) shares?.get(index) ?: 0 else lines[index].merchantMinor)
            }
        }
    return copy(
        amountText = signedText,
        receivedText = received,
        merchantCurrencyText = if (foreign) currency.code else null,
        merchantAmountText = if (foreign) merchantAmountText else null,
        allocationTexts = emptyMap(),
        input =
            input.copy(
                amountMinor = actual,
                merchantAmount = merchant,
                allocations = rows,
                receiptLines = lines,
                receivedMinor = if (transfer && destination != null) parse(received, destination.currency) else null,
            ),
    )
}

fun EditorInput.addItem(accounts: List<Account>): EditorInput {
    val id = UUID.randomUUID().toString()
    val first = input.receiptLines.isEmpty()
    val row = Allocation(id, 0, if (first) input.allocations.firstOrNull()?.categoryId else null)
    return copy(
        input =
            input.copy(
                allocations = (if (first) emptyList() else input.allocations) + row,
                receiptLines = input.receiptLines + ReceiptLine(id, "", 0),
            ),
        lineTexts = lineTexts + (id to ""),
    ).recalculate(accounts, forceAllocations = true)
}

fun EditorInput.removeItem(
    id: String,
    accounts: List<Account>,
): EditorInput {
    val row = input.allocations.find { it.id == id } ?: return this
    val remaining = input.receiptLines.filterNot { it.id == id }
    return copy(
        input =
            input.copy(
                receiptLines = remaining,
                allocations = if (remaining.isEmpty()) listOf(row) else input.allocations.filterNot { it.id == id },
            ),
        lineTexts = lineTexts - id,
    ).recalculate(accounts, forceAllocations = true)
}

fun EditorInput.moveItem(
    id: String,
    offset: Int,
    accounts: List<Account>,
): EditorInput {
    val lines = input.receiptLines.toMutableList()
    val index = lines.indexOfFirst { it.id == id }
    val target = index + offset
    if (index < 0 || target !in lines.indices) return this
    lines.add(target, lines.removeAt(index))
    return copy(
        input =
            input.copy(
                receiptLines = lines,
                allocations = lines.map { line -> input.allocations.single { it.id == line.id } },
            ),
    ).recalculate(accounts, forceAllocations = true)
}
