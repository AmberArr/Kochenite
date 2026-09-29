package moe.frost.kochenite

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.LedgerResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** The runner invokes explicit incomplete-record checks after force-stopping the process. */
class BookProcessDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("synthetic-process-incomplete-test", 0)

    @Test fun createExplicitIncompleteRecord() =
        runBlocking {
            val book = ((context.applicationContext as KocheniteApplication).financialStorage.open() as LedgerResult.Success).value
            val account = (book.accounts() as LedgerResult.Success).value.firstOrNull()?.id ?: error("No synthetic account")
            val id = UUID.randomUUID().toString()
            val input =
                EntryInput(
                    EntryKind.EXPENSE,
                    LocalDate.now(),
                    LocalTime.now(),
                    account,
                    null,
                    allocations = listOf(Allocation(UUID.randomUUID().toString(), 0)),
                    merchant = "Synthetic incomplete",
                )
            val saved = book.saveIncomplete(EditorInput(id, UUID.randomUUID().toString(), "", input), null) as LedgerResult.Success
            assertTrue(prefs.edit().putString("id", saved.value.id).commit())
        }

    @Test fun verifyExplicitIncompleteAfterProcessRestart() =
        runBlocking {
            val id = prefs.getString("id", null) ?: error("Create step did not save an incomplete transaction")
            val book = ((context.applicationContext as KocheniteApplication).financialStorage.open() as LedgerResult.Success).value
            val record = (book.incomplete(id) as LedgerResult.Success).value
            assertEquals("Synthetic incomplete", record.editor.input.merchant)
            assertEquals(null, record.editor.input.amountMinor)
            assertTrue(book.deleteIncomplete(id, record.token) is LedgerResult.Success)
            assertTrue(prefs.edit().remove("id").commit())
        }
}
