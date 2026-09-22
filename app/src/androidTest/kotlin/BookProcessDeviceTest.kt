package moe.frost.kochenite

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import moe.frost.kochenite.domain.ledger.Draft
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.LedgerResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

/** The host runner invokes these methods in separate instrumentation processes. */
class BookProcessDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("synthetic-process-draft-test", 0)

    @Test fun createAcknowledgedDraft() =
        runBlocking {
            val book = ((context.applicationContext as KocheniteApplication).financialStorage.open() as LedgerResult.Success).value
            val account = (book.accounts() as LedgerResult.Success).value.firstOrNull()?.id ?: "synthetic-process-account"
            val draft =
                Draft(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    "",
                    EntryInput(EntryKind.EXPENSE, LocalDate.now(), LocalTime.now(), account, null, merchant = "Synthetic process draft"),
                    amountText = "12.",
                    dateText = "2026-09-",
                )
            val saved = book.saveDraft(draft, null) as LedgerResult.Success
            assertTrue(prefs.edit().putString("id", saved.value.id).commit())
        }

    @Test fun verifyAfterProcessRestart() =
        runBlocking {
            val id = prefs.getString("id", null) ?: error("Create step did not record a draft")
            val book = ((context.applicationContext as KocheniteApplication).financialStorage.open() as LedgerResult.Success).value
            val saved = (book.draft(id) as LedgerResult.Success).value
            assertEquals("12.", saved.amountText)
            assertEquals("2026-09-", saved.dateText)
            assertEquals("Synthetic process draft", saved.input.merchant)
            assertTrue(book.discardDraft(id, saved.token) is LedgerResult.Success)
            assertTrue(prefs.edit().remove("id").commit())
        }
}
