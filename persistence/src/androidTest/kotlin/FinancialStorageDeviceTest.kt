package moe.frost.kochenite.persistence.ledger

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.ledger.Account
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.BookCurrency
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.InvalidReason
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.domain.ledger.ReceiptLine
import moe.frost.kochenite.domain.ledger.SavedEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class FinancialStorageDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "financial-test-${UUID.randomUUID()}.db"
    private val usd = BookCurrency("USD", 2)
    private val today = LocalDate.now()
    private var db: LedgerDatabase? = null

    @After fun cleanup() {
        db?.close()
        context.deleteDatabase(name)
    }

    private fun open(afterWrite: () -> Unit = {}): RoomLedger =
        RoomLedger(
            ledgerDatabase(context, name).also {
                db?.close()
                db = it
            },
            { today },
            afterWrite,
        )

    private fun <T> success(result: LedgerResult<T>): T {
        assertTrue("Expected success", result is LedgerResult.Success)
        return (result as LedgerResult.Success).value
    }

    private suspend fun seed(ledger: RoomLedger) {
        success(ledger.createAccount(Account("cash", "Cash", usd)))
        success(ledger.createAccount(Account("card", "Card", usd)))
        success(ledger.createAccount(Account("yen", "Yen", BookCurrency("JPY", 0))))
    }

    private fun expense(
        amount: Long? = -1234L,
        id: String = UUID.randomUUID().toString(),
    ) = EditorInput(
        id,
        UUID.randomUUID().toString(),
        "submission-${UUID.randomUUID()}",
        EntryInput(
            EntryKind.EXPENSE,
            today,
            LocalTime.NOON,
            "cash",
            amount,
            allocations = listOf(Allocation("food", amount?.let { Math.absExact(it) } ?: 0)),
        ),
        amountText = amount?.let(usd::formatMinor) ?: "",
    )

    private suspend fun post(
        ledger: RoomLedger,
        editor: EditorInput = expense(),
    ): SavedEntry = success(ledger.post(editor))

    @Test fun openingCopyAndTemplateApplicationAreReadOnly() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val source = post(ledger)
            val before = success(ledger.balance("cash"))
            val edit = success(ledger.beginEdit(source.id))
            val copy = success(ledger.copyEntry(source.id))
            success(
                ledger.saveTemplate(
                    moe.frost.kochenite.domain.ledger
                        .EntryTemplate("template", "Reusable", source.input),
                ),
            )
            val applied = success(ledger.applyTemplate("template"))
            assertEquals(before, success(ledger.balance("cash")))
            assertTrue(success(ledger.incompleteRecords()).isEmpty())
            assertEquals(source.input, edit.input)
            assertEquals(source.input.amountMinor, copy.input.amountMinor)
            assertEquals(source.input.amountMinor, applied.input.amountMinor)
        }

    @Test fun postingAndPostedReplacementAreAtomicAndRejectStaleEdits() =
        runBlocking {
            var ledger = open()
            seed(ledger)
            val original = post(ledger)
            val first =
                success(ledger.beginEdit(original.id)).copy(
                    input =
                        original.input.copy(amountMinor = -1500, allocations = listOf(Allocation("food", 1500))),
                )
            val stale = success(ledger.beginEdit(original.id))
            val fail = open { throw IOException("Synthetic storage failure") }
            assertEquals(LedgerResult.TemporarilyUnavailable, fail.post(first))
            assertEquals(original, success(fail.entry(original.id)))
            ledger = open()
            val replacement = success(ledger.post(first))
            assertEquals(-1500L, success(ledger.balance("cash")))
            assertEquals(LedgerResult.Conflict, ledger.post(stale))
            assertEquals(LedgerResult.AlreadyConfirmed, ledger.post(first))
            assertEquals(replacement.id, original.id)
            assertEquals(1500L, success(ledger.totals(today, today)).single().spendingMinor)
        }

    @Test fun explicitIncompleteSaveUpdateDeleteAndCompletionAreTokenChecked() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val id = UUID.randomUUID().toString()
            val editor = expense(null, id).copy(input = expense(null, id).input.copy(merchant = "Unknown charge"))
            val record = success(ledger.saveIncomplete(editor, null))
            assertEquals(listOf(record), success(ledger.incompleteRecords()))
            assertEquals(0L, success(ledger.balance("cash")))
            assertTrue(success(ledger.totals(today, today)).isEmpty())
            assertEquals(LedgerResult.Conflict, ledger.deleteIncomplete(record.id, "stale-token"))
            val updated = record.editor.copy(input = record.editor.input.copy(note = "Synthetic update"))
            val saved = success(ledger.saveIncomplete(updated, record.token))
            assertEquals(saved, success(ledger.incomplete(record.id)))
            assertEquals(LedgerResult.Conflict, ledger.saveIncomplete(updated, record.token))
            val complete =
                saved.editor.copy(
                    input = saved.editor.input.copy(amountMinor = -975, allocations = listOf(Allocation("food", 975))),
                    amountText = "-9.75",
                )
            val posted = success(ledger.post(complete, saved.token))
            assertEquals(-975L, success(ledger.balance("cash")))
            assertTrue(success(ledger.incompleteRecords()).isEmpty())
            assertEquals("Synthetic update", posted.input.note)
            assertEquals(LedgerResult.AlreadyConfirmed, ledger.post(complete, saved.token))
        }

    @Test fun incompleteSavesRejectFutureDatesAndPostedEditsCannotBecomeIncomplete() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val future = expense(null).copy(input = expense(null).input.copy(date = today.plusDays(1)))
            assertEquals(LedgerResult.Invalid(InvalidReason.FUTURE_DATE), ledger.saveIncomplete(future, null))

            val original = post(ledger)
            val edit =
                success(ledger.beginEdit(original.id)).copy(
                    input = original.input.copy(amountMinor = null, allocations = listOf(Allocation("food", 0))),
                    amountText = "",
                )
            assertEquals(LedgerResult.Invalid(InvalidReason.MISSING_AMOUNT), ledger.post(edit))
            assertEquals(0L, success(ledger.incompleteRecords()).size.toLong())
            assertEquals(original, success(ledger.entry(original.id)))
        }

    @Test fun validationRejectsFutureDatesMalformedCurrencyPrecisionAndItemMismatch() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            assertEquals(
                LedgerResult.Invalid(InvalidReason.FUTURE_DATE),
                ledger.post(
                    expense().copy(input = expense().input.copy(date = today.plusDays(1))),
                ),
            )
            val badPrecision =
                expense().copy(
                    amountText = "1.001",
                    input =
                        expense().input.copy(amountMinor = -100, allocations = listOf(Allocation("food", 100))),
                )
            assertEquals(LedgerResult.Invalid(InvalidReason.AMOUNT), ledger.post(badPrecision))
            val mismatch =
                expense().copy(
                    input =
                        expense().input.copy(
                            amountMinor = -1000,
                            allocations = listOf(Allocation("item", 1000)),
                            receiptLines = listOf(ReceiptLine("item", "", 999)),
                        ),
                    amountText = "-10.00",
                    lineTexts = mapOf("item" to "9.99"),
                )
            assertEquals(LedgerResult.Invalid(InvalidReason.ALLOCATIONS), ledger.post(mismatch))
            assertEquals(0L, success(ledger.balance("cash")))
        }

    @Test fun incompleteTransferPostsNeitherSideAndCompletesAsOneAtomicTransfer() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val id = UUID.randomUUID().toString()
            val input = EntryInput(EntryKind.TRANSFER, today, LocalTime.NOON, "cash", 1000, "yen", null)
            val editor =
                EditorInput(
                    id,
                    UUID.randomUUID().toString(),
                    "submission",
                    input,
                    amountText = "10.00",
                    receivedText = "",
                )
            val saved = success(ledger.saveIncomplete(editor, null))
            assertEquals(0L, success(ledger.balance("cash")))
            assertEquals(0L, success(ledger.balance("yen")))
            val complete = saved.editor.copy(input = saved.editor.input.copy(receivedMinor = 1500), receivedText = "1500")
            success(ledger.post(complete, saved.token))
            assertEquals(-1000L, success(ledger.balance("cash")))
            assertEquals(1500L, success(ledger.balance("yen")))
            assertTrue(success(ledger.totals(today, today)).isEmpty())
        }

    @Test fun repeatedConcurrentSubmissionPostsOnce() =
        runBlocking {
            val ledger = open()
            seed(ledger)
            val editor = expense()
            val results = List(8) { async { ledger.post(editor) } }.awaitAll()
            assertEquals(1, results.count { it is LedgerResult.Success })
            assertEquals(7, results.count { it == LedgerResult.AlreadyConfirmed })
            assertEquals(-1234L, success(ledger.balance("cash")))
        }
}
