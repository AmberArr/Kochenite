package moe.frost.kochenite

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import moe.frost.kochenite.domain.ledger.LedgerResult
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class BookUiDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun field(
        label: String,
        value: String,
    ) {
        compose.waitUntil(15000) { compose.onAllNodesWithTag("field-$label").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("field-$label").performScrollTo().performTextReplacement(value)
    }

    private fun ready() {
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("create-book").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("nav-0").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithTag("create-book").fetchSemanticsNodes().isNotEmpty()) {
            field("Account name", "Synthetic initial cash")
            field("Account currency", "USD")
            field("Opening balance (+ owned / − owed)", "100.00")
            field("Dashboard currency", "JPY")
            compose.onNodeWithTag("create-book").performScrollTo().performClick()
        }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("nav-0").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun newAccount(
        name: String,
        amount: String,
    ) {
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("Add account").performClick()
        field("Account name", name)
        field("Account currency", "USD")
        field("Opening balance (+ owned / − owed)", amount)
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("New account").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test fun signedAccountsAndNavigationSurviveRecreation() {
        ready()
        val suffix = UUID.randomUUID().toString().take(8)
        val negativeName = "Synthetic card $suffix"
        val zeroName = "Synthetic empty $suffix"
        newAccount(negativeName, "-20.00")
        compose.onNodeWithTag("nav-3").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("nav-3").assertIsSelected()
        compose.onNodeWithText("Month to date").assertIsDisplayed()
        newAccount(zeroName, "0")
        runBlocking {
            val app = context.applicationContext as KocheniteApplication
            val book = (app.financialStorage.open() as LedgerResult.Success).value
            val rows = (book.accountRepository.list() as LedgerResult.Success).value
            assertEquals(-2000L, rows.single { it.account.name == negativeName }.balanceMinor)
            assertEquals(0L, rows.single { it.account.name == zeroName }.balanceMinor)
        }
    }

    @Test fun categoryCreationSurvivesRecreation() {
        ready()
        val name = "Synthetic category ${UUID.randomUUID().toString().take(8)}"
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Categories").performClick()
        compose.onNodeWithText("New category").performClick()
        field("Category name", name)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(name).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText("Category name").fetchSemanticsNodes().isEmpty()
        }
        runBlocking {
            val app = context.applicationContext as KocheniteApplication
            val book = (app.financialStorage.open() as LedgerResult.Success).value
            val rows = (book.categoryRepository.list() as LedgerResult.Success).value
            assertEquals(1, rows.count { it.name == name })
        }
    }

    @Test fun draftInputSurvivesRecreationAndPostsOnce() {
        ready()
        val merchant = "Synthetic expense ${UUID.randomUUID().toString().take(8)}"
        val app = context.applicationContext as KocheniteApplication
        val book = (runBlocking { app.financialStorage.open() } as LedgerResult.Success).value
        val firstAccount =
            (runBlocking { book.accountRepository.list() } as LedgerResult.Success)
                .value
                .first { !it.account.archived }
                .account
        val before = (runBlocking { book.balance(firstAccount.id) } as LedgerResult.Success).value
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithText("New transaction").assertIsDisplayed()
        field("Amount", "12.34")
        field("Merchant or payee", merchant)
        compose.waitUntil(15000) {
            runBlocking {
                (book.drafts() as LedgerResult.Success).value.any { it.input.merchant == merchant && it.amountText == "12.34" }
            }
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(merchant).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Post transaction").performScrollTo().performClick()
        compose.waitUntil(15000) {
            runBlocking {
                (book.page() as LedgerResult.Success).value.entries.any { it.input.merchant == merchant }
            }
        }
        val after = (runBlocking { book.balance(firstAccount.id) } as LedgerResult.Success).value
        assertEquals(Math.subtractExact(before, 1234L), after)
        assertEquals(0, (runBlocking { book.drafts() } as LedgerResult.Success).value.count { it.input.merchant == merchant })
        compose.waitUntil(15000) { compose.onAllNodesWithText("New transaction").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithText(merchant, substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Edit transaction").performClick()
        field("Amount", "20.00")
        compose.onNodeWithText("Post transaction").performScrollTo().performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.balance(firstAccount.id) as LedgerResult.Success).value == Math.subtractExact(before, 2000L) }
        }
        compose.onNodeWithText("Move to trash").assertIsDisplayed()
        compose.onNodeWithText("Move to trash").performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.balance(firstAccount.id) as LedgerResult.Success).value == before }
        }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Move to trash").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Trash").performClick()
        compose.onNodeWithText(merchant, substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Restore").performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.balance(firstAccount.id) as LedgerResult.Success).value == Math.subtractExact(before, 2000L) }
        }
    }
}
