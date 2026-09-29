package moe.frost.kochenite

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.LedgerResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class BookUiDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun ready() {
        compose.activityRule.scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("create-book").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("nav-0").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithTag("create-book").fetchSemanticsNodes().isNotEmpty()) {
            type("field-Account name", "Synthetic initial cash")
            type("field-Account currency", "USD")
            type("field-Opening balance (+ owned / − owed)", "100.00")
            type("field-Dashboard currency", "USD")
            compose
                .onNodeWithTag("create-book")
                .performScrollTo()
                .assertIsDisplayed()
                .performClick()
        }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("nav-0").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun type(
        tag: String,
        value: String,
    ) {
        compose.waitUntil(15000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(tag).performTextReplacement(value)
    }

    private fun book() = (context.applicationContext as KocheniteApplication).financialStorage

    @Test fun editorUsesCompactRowsAndDoesNotWriteBeforeSave() =
        runBlocking {
            ready()
            val ledger = (book().open() as LedgerResult.Success).value
            val accounts = (ledger.accountRepository.list() as LedgerResult.Success).value
            val account = accounts.first { !it.account.archived }.account
            val before = (ledger.balance(account.id) as LedgerResult.Success).value
            val name = "Synthetic transient ${UUID.randomUUID().toString().take(8)}"
            compose.onNodeWithTag("nav-1").performClick()
            compose.onNodeWithText("Add").performClick()
            compose.onNodeWithText("Expense").assertIsDisplayed()
            compose.onNodeWithText("Income").assertIsDisplayed()
            compose.onNodeWithText("Transfer").assertIsDisplayed()
            compose.onNodeWithText("From receipt").assertIsDisplayed()
            type("field-Title", name)
            type("field-Amount", "12.34")
            assertEquals(before, (ledger.balance(account.id) as LedgerResult.Success).value)
            assertTrue((ledger.incompleteRecords() as LedgerResult.Success).value.none { it.editor.input.merchant == name })
            assertTrue((ledger.page() as LedgerResult.Success).value.entries.none { it.input.merchant == name })
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("field-Title").assertIsDisplayed()
            compose.onNodeWithText(name).assertIsDisplayed()
            compose.onNodeWithText("Save transaction").performClick()
            compose.waitUntil(15000) {
                runBlocking {
                    (ledger.page() as LedgerResult.Success).value.entries.any { it.input.merchant == name }
                }
            }
            val entry = (ledger.page() as LedgerResult.Success).value.entries.single { it.input.merchant == name }
            assertEquals(-1234L, entry.input.amountMinor)
            compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("nav-1") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav-1").assertIsSelected()
            Unit
        }

    @Test fun unchangedEditorClosesWithoutDiscardConfirmation() {
        ready()
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("Back").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("New transaction").fetchSemanticsNodes().isEmpty() }
        assertTrue(compose.onAllNodesWithText("Discard changes?").fetchSemanticsNodes().isEmpty())
    }

    @Test fun systemBackFromChangedEditorAsksBeforeLeavingAndKeepsNavigationWorking() {
        ready()
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithText("Add").performClick()
        type("field-Title", "Synthetic back ${UUID.randomUUID().toString().take(6)}")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("field-Title").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Discard").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("field-Title").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("nav-2").performClick()
        compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("nav-2") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun systemBackLeavesUnchangedEditorAndTopBackMatchesTheBackStack() {
        ready()
        compose.onNodeWithTag("nav-0").performClick()
        compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("nav-0") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        assertTrue(compose.onAllNodesWithText("Back").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithTag("field-Title").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("field-Title").fetchSemanticsNodes().isEmpty() }
        assertTrue(compose.onAllNodesWithText("Discard changes?").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("nav-0").assertIsSelected()
        assertTrue(compose.onAllNodesWithText("Back").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("nav-2").performClick()
        compose.waitUntil(15000) { compose.onAllNodes(hasTestTag("nav-2") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun discardRequiresConfirmationAndKeepsOriginalPostedEntry() =
        runBlocking {
            ready()
            val ledger = (book().open() as LedgerResult.Success).value
            val account =
                (ledger.accountRepository.list() as LedgerResult.Success).value.first { !it.account.archived }.account
            val source =
                EntryInput(
                    EntryKind.EXPENSE,
                    java.time.LocalDate.now(),
                    java.time.LocalTime.NOON,
                    account.id,
                    -250L,
                    allocations = listOf(Allocation("discard-check", 250)),
                    merchant = "Synthetic original ${UUID.randomUUID().toString().take(6)}",
                )
            val edit = EditorInput(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", source)
            val entry = (ledger.post(edit) as LedgerResult.Success).value
            val amount = entry.input.amountMinor
            val title = "Changed ${UUID.randomUUID().toString().take(6)}"
            compose.onNodeWithTag("nav-1").performClick()
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("posted-entry-${entry.id}").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("posted-entry-${entry.id}").performClick()
            compose.onNodeWithText("Edit transaction").performClick()
            type("field-Title", title)
            compose.onNodeWithText("Back").performClick()
            compose.onNodeWithText("Discard changes?").assertIsDisplayed()
            compose.onNodeWithText("Keep editing").performClick()
            compose.onNodeWithText("Back").performClick()
            compose.onNodeWithText("Discard").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("field-Title").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("Edit transaction").assertIsDisplayed()
            assertEquals(amount, (ledger.entry(entry.id) as LedgerResult.Success).value.input.amountMinor)
            assertTrue((ledger.page() as LedgerResult.Success).value.entries.none { it.input.merchant == title })
        }

    @Test fun itemChangesRemainInMemoryUntilParentTransactionIsSaved() =
        runBlocking {
            ready()
            val ledger = (book().open() as LedgerResult.Success).value
            val account =
                (ledger.accountRepository.list() as LedgerResult.Success).value.first { !it.account.archived }.account
            val before = (ledger.balance(account.id) as LedgerResult.Success).value
            val title = "Synthetic item transaction ${UUID.randomUUID().toString().take(6)}"
            compose.onNodeWithTag("nav-1").performClick()
            compose.onNodeWithText("Add").performClick()
            type("field-Title", title)
            type("field-Amount", "10.00")
            compose.onNodeWithText("+ Split transaction").performClick()
            compose.onNodeWithText("Transaction items").assertIsDisplayed()
            assertTrue(compose.onAllNodesWithTag("transaction-item-done").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithText("+ Add item").performClick()
            compose.onNodeWithText("Item").assertIsDisplayed()
            compose.onAllNodesWithTag("field-Title")[0].performTextReplacement("Synthetic line")
            compose.onAllNodesWithTag("field-Amount")[0].performTextReplacement("10.00")
            compose.onNodeWithTag("transaction-item-done").performClick()
            assertTrue(compose.onAllNodesWithText("Matched", substring = true).fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText("Done").performClick()
            assertEquals(before, (ledger.balance(account.id) as LedgerResult.Success).value)
            assertTrue((ledger.page() as LedgerResult.Success).value.entries.none { it.input.merchant == title })

            compose.onNodeWithText("Save transaction").performClick()
            compose.waitUntil(15000) {
                runBlocking { (ledger.page() as LedgerResult.Success).value.entries.any { it.input.merchant == title } }
            }
            val saved = (ledger.page() as LedgerResult.Success).value.entries.single { it.input.merchant == title }
            assertEquals(
                "Synthetic line",
                saved.input.receiptLines
                    .single()
                    .description,
            )
            assertEquals(
                1000L,
                saved.input.receiptLines
                    .single()
                    .merchantMinor,
            )
            assertEquals(
                saved.input.receiptLines
                    .single()
                    .id,
                saved.input.allocations
                    .single()
                    .id,
            )
        }

    @Test fun incompleteSaveIsExplicitAndDoesNotChangeAccountBalance() =
        runBlocking {
            ready()
            val ledger = (book().open() as LedgerResult.Success).value
            val account =
                (ledger.accountRepository.list() as LedgerResult.Success).value.first { !it.account.archived }.account
            val before = (ledger.balance(account.id) as LedgerResult.Success).value
            val today = java.time.LocalDate.now()
            val totalsBefore = (ledger.totals(today.withDayOfMonth(1), today) as LedgerResult.Success).value
            val name = "Synthetic incomplete ${UUID.randomUUID().toString().take(8)}"
            compose.onNodeWithTag("nav-1").performClick()
            compose.onNodeWithText("Add").performClick()
            type("field-Title", name)
            compose.onNodeWithText("Save transaction").performClick()
            compose.onNodeWithText("Save incomplete").performClick()
            compose.waitUntil(15000) {
                runBlocking {
                    (ledger.incompleteRecords() as LedgerResult.Success).value.any { it.editor.input.merchant == name }
                }
            }
            assertEquals(before, (ledger.balance(account.id) as LedgerResult.Success).value)
            assertTrue((ledger.page() as LedgerResult.Success).value.entries.none { it.input.merchant == name })
            assertEquals(totalsBefore, (ledger.totals(today.withDayOfMonth(1), today) as LedgerResult.Success).value)
        }

    @Test fun rotationRetainsEditorInputInViewModel() {
        ready()
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithText("Add").performClick()
        type("field-Title", "Synthetic process local ${UUID.randomUUID().toString().take(6)}")
        lateinit var model: BookViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[BookViewModel::class.java] }
        assertTrue(model.transactionEditor.editor.value != null)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { model.transactionEditor.editor.value != null }
        assertTrue(
            model.transactionEditor.editor.value!!
                .input.input.merchant
                .startsWith("Synthetic process local"),
        )
    }

    @Test fun restoredEditorRouteWithFreshViewModelReturnsToItsParent() {
        ready()
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithText("Add").performClick()
        type("field-Title", "Synthetic abandoned editor")
        // Restore navigation saved state with no retained ViewModel, as after process loss.
        // The separate process suite checks actual PID replacement and persistence absence.
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("nav-1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav-1").assertIsSelected()
        assertTrue(compose.onAllNodesWithTag("field-Title").fetchSemanticsNodes().isEmpty())
    }
}
