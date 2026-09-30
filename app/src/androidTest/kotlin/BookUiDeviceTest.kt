package moe.frost.kochenite

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
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
            // The account currency is chosen from the opening balance's currency pill.
            compose.onNodeWithTag("amount-currency").performClick()
            type("currency-search", "USD")
            compose.onNodeWithTag("currency-option-USD").performClick()
            type("field-Opening balance", "100.00")
            compose.onNodeWithTag("create-book").assertIsDisplayed().performClick()
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
            compose.onNodeWithTag("add-transaction").performClick()
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
        compose.onNodeWithTag("add-transaction").performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("New transaction").fetchSemanticsNodes().isEmpty() }
        assertTrue(compose.onAllNodesWithText("Discard changes?").fetchSemanticsNodes().isEmpty())
    }

    @Test fun systemBackFromChangedEditorAsksBeforeLeavingAndKeepsNavigationWorking() {
        ready()
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithTag("add-transaction").performClick()
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
        assertTrue(compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("add-transaction").performClick()
        compose.onNodeWithTag("field-Title").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("field-Title").fetchSemanticsNodes().isEmpty() }
        assertTrue(compose.onAllNodesWithText("Discard changes?").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("nav-0").assertIsSelected()
        assertTrue(compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().isEmpty())
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
            // The list composes only visible rows, so search for this entry instead of relying on its position.
            type("transaction-search", source.merchant)
            compose.waitUntil(15000) {
                compose.onAllNodesWithTag("posted-entry-${entry.id}").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("posted-entry-${entry.id}").performClick()
            // Edit is the detail screen's primary app-bar action and appears once the record has loaded.
            compose.waitUntil(15000) { compose.onAllNodesWithTag("edit-transaction").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("edit-transaction").performClick()
            type("field-Title", title)
            compose.onNodeWithContentDescription("Close").performClick()
            compose.onNodeWithText("Discard changes?").assertIsDisplayed()
            compose.onNodeWithText("Keep editing").performClick()
            compose.onNodeWithContentDescription("Close").performClick()
            compose.onNodeWithText("Discard").performClick()
            compose.waitUntil(15000) { compose.onAllNodesWithTag("field-Title").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("edit-transaction").assertIsDisplayed()
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
            compose.onNodeWithTag("add-transaction").performClick()
            type("field-Title", title)
            type("field-Amount", "10.00")
            compose.onNodeWithText("Split transaction").performScrollTo().performClick()
            compose.onNodeWithText("Transaction items").assertIsDisplayed()
            assertTrue(compose.onAllNodesWithTag("transaction-item-done").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithText("Add item").performClick()
            compose.onNodeWithText("Item").assertIsDisplayed()
            compose.onAllNodesWithTag("field-Title")[0].performTextReplacement("Synthetic line")
            compose.onAllNodesWithTag("field-Amount")[0].performTextReplacement("10.00")
            compose.onNodeWithTag("transaction-item-done").performClick()
            assertTrue(compose.onAllNodesWithText("Matched", substring = true).fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithTag("items-done").performClick()
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
            compose.onNodeWithTag("add-transaction").performClick()
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

    @Test fun accountBalanceChangeIsSavedAsOneAdjustmentAndGroupsAreCreatedFromTheSheet() =
        runBlocking {
            ready()
            val ledger = (book().open() as LedgerResult.Success).value
            val name = "Synthetic account ${UUID.randomUUID().toString().take(8)}"
            val group = "Synthetic group ${UUID.randomUUID().toString().take(8)}"
            compose.onNodeWithTag("nav-2").performClick()
            compose.onNodeWithTag("accounts-menu").performClick()
            compose.onNodeWithTag("new-account").performClick()
            type("field-Account name", name)
            type("field-Opening balance", "421.80")
            compose.onNodeWithTag("save-account").performClick()
            compose.waitUntil(15000) {
                runBlocking { (ledger.accountRepository.list() as LedgerResult.Success).value.any { it.account.name == name } }
            }
            val created = (ledger.accountRepository.list() as LedgerResult.Success).value.single { it.account.name == name }
            assertEquals(42180L, created.balanceMinor)
            val postedBefore = (ledger.page() as LedgerResult.Success).value.entries.count { it.input.accountId == created.account.id }

            compose.waitUntil(15000) { compose.onAllNodesWithTag("account-${created.account.id}").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("account-${created.account.id}").performScrollTo().performClick()
            // The currency is locked by the opening posting, so only the amount can change.
            type("field-Balance", "409.80")
            compose.onNodeWithTag("balance-adjustment-notice").assertIsDisplayed()
            type("field-Adjustment note", "Synthetic statement")
            compose.onNodeWithTag("save-account").performClick()
            compose.waitUntil(15000) { runBlocking { (ledger.balance(created.account.id) as LedgerResult.Success).value == 40980L } }
            val entries = (ledger.page() as LedgerResult.Success).value.entries.filter { it.input.accountId == created.account.id }
            assertEquals(postedBefore + 1, entries.size)
            val adjustment = entries.first()
            assertEquals(EntryKind.ADJUSTMENT, adjustment.input.kind)
            assertEquals(-1200L, adjustment.input.amountMinor)
            assertEquals("Synthetic statement", adjustment.input.note)

            compose.waitUntil(15000) { compose.onAllNodesWithTag("groups-and-order").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("groups-and-order").performClick()
            compose.onNodeWithTag("new-group").performClick()
            type("field-Group name", group)
            compose.onNodeWithTag("save-group").performClick()
            compose.waitUntil(15000) {
                runBlocking { (ledger.accountRepository.groups() as LedgerResult.Success).value.any { it.name == group } }
            }
            compose.waitUntil(15000) { compose.onAllNodesWithText(group).fetchSemanticsNodes().isNotEmpty() }
        }

    @Test fun reviewIncompleteOpensTheIncompleteTabAndFiltersAreRemovable() {
        ready()
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithTag("transaction-search").performTextReplacement("synthetic-no-such-entry")
        compose.waitUntil(15000) { compose.onAllNodesWithText("No transactions match").fetchSemanticsNodes().isNotEmpty() }
        // Searching replaces the status chips with the result count and removable filters.
        assertTrue(compose.onAllNodesWithTag("tab-posted").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Clear search and filters").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("tab-posted").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab-trash").performClick()
        compose.onNodeWithTag("tab-trash").assertIsSelected()
        // Trash offers no Add action.
        assertTrue(compose.onAllNodesWithTag("add-transaction").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("tab-incomplete").performClick()
        compose.onNodeWithTag("tab-incomplete").assertIsSelected()
        compose.onNodeWithTag("tab-posted").performClick()
        compose.onNodeWithTag("bulk-categorize").performClick()
        // Bulk categorize is a contextual mode without the navigation bar.
        compose.waitUntil(15000) { compose.onAllNodesWithTag("nav-1").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("bulk-apply").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("nav-1").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun rotationRetainsEditorInputInViewModel() {
        ready()
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithTag("add-transaction").performClick()
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
        compose.onNodeWithTag("add-transaction").performClick()
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
