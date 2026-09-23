package moe.frost.kochenite

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
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

    private fun fieldAt(
        label: String,
        index: Int,
        value: String,
    ) {
        compose.waitUntil(15000) { compose.onAllNodesWithTag("field-$label").fetchSemanticsNodes().size > index }
        compose.onAllNodesWithTag("field-$label")[index].performScrollTo().performTextReplacement(value)
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
        code: String = "USD",
    ) {
        compose.onNodeWithTag("nav-2").performClick()
        compose.onNodeWithText("Add account").performClick()
        field("Account name", name)
        field("Account currency", code)
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
        compose.waitUntil(15000) { compose.onAllNodesWithText("Move to trash").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Move to trash").performScrollTo().assertIsDisplayed()
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

    @Test fun incompleteCrossCurrencyTransferPostsOnlyAfterBothActualAmountsAreKnown() {
        ready()
        val suffix = UUID.randomUUID().toString().take(8)
        val sourceName = "Synthetic source $suffix"
        val destinationName = "Synthetic yen $suffix"
        val merchant = "Synthetic transfer $suffix"
        newAccount(sourceName, "0", "USD")
        newAccount(destinationName, "0", "JPY")
        val app = context.applicationContext as KocheniteApplication
        val book = (runBlocking { app.financialStorage.open() } as LedgerResult.Success).value
        val accounts = (runBlocking { book.accounts() } as LedgerResult.Success).value
        val source = accounts.single { it.name == sourceName }
        val destination = accounts.single { it.name == destinationName }
        val today = java.time.LocalDate.now()
        val beforeTotals = (runBlocking { book.totals(today.withDayOfMonth(1), today) } as LedgerResult.Success).value
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithTag("choice-Transaction kind").performClick()
        compose.onNodeWithTag("choice-Transaction kind-option-TRANSFER").performClick()
        compose.onNodeWithTag("choice-Account name").performClick()
        compose.onNodeWithTag("choice-Account name-option-${source.id}").performScrollTo().performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithText(sourceName, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("choice-Account name").assertTextContains(sourceName)
        compose.waitUntil(15000) {
            runBlocking {
                (book.drafts() as LedgerResult.Success).value.any {
                    it.input.accountId == source.id &&
                        it.input.kind.name == "TRANSFER"
                }
            }
        }
        field("Amount", "10.00")
        compose.onNodeWithTag("choice-To account").performClick()
        compose.onNodeWithTag("choice-To account-option-${destination.id}").performScrollTo().performClick()
        field("Merchant or payee", merchant)
        field("Merchant currency (optional context)", "JPY")
        field("Merchant amount (optional context)", "1550")
        compose.onNodeWithText("Save incomplete").performScrollTo().performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.incompleteDrafts() as LedgerResult.Success).value.any { it.input.merchant == merchant } }
        }
        val incompleteDraft =
            (
                runBlocking {
                    book.incompleteDrafts()
                } as LedgerResult.Success
            ).value.single { it.input.merchant == merchant }
        assertEquals(source.id, incompleteDraft.input.accountId)
        assertEquals(destination.id, incompleteDraft.input.destinationAccountId)
        assertEquals(1000L, incompleteDraft.input.amountMinor)
        assertEquals(1550L, incompleteDraft.input.merchantAmount?.minor)
        assertEquals(0L, (runBlocking { book.balance(source.id) } as LedgerResult.Success).value)
        assertEquals(0L, (runBlocking { book.balance(destination.id) } as LedgerResult.Success).value)
        compose.waitUntil(15000) { compose.onAllNodesWithText("New transaction").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("nav-1").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Incomplete").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Incomplete").performScrollTo().performClick()
        compose.onNodeWithText(merchant, substring = true).performScrollTo().performClick()
        field("Actual amount received", "1510")
        compose.waitUntil(15000) {
            runBlocking {
                (book.drafts() as LedgerResult.Success).value.any {
                    it.input.merchant == merchant && it.input.receivedMinor == 1510L && !it.incomplete
                }
            }
        }
        val completeDraft = (runBlocking { book.drafts() } as LedgerResult.Success).value.single { it.input.merchant == merchant }
        assertEquals(1000L, completeDraft.input.amountMinor)
        assertEquals(1550L, completeDraft.input.merchantAmount?.minor)
        assertEquals(0, completeDraft.input.allocations.size)
        compose.onNodeWithText("Post transaction").performScrollTo().performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.page() as LedgerResult.Success).value.entries.any { it.input.merchant == merchant } }
        }
        val entry = (runBlocking { book.page() } as LedgerResult.Success).value.entries.single { it.input.merchant == merchant }
        assertEquals(1000L, entry.input.amountMinor)
        assertEquals(1510L, entry.input.receivedMinor)
        assertEquals(1550L, entry.input.merchantAmount?.minor)
        assertEquals(
            "JPY",
            entry.input.merchantAmount
                ?.currency
                ?.code,
        )
        assertEquals(-1000L, (runBlocking { book.balance(source.id) } as LedgerResult.Success).value)
        assertEquals(1510L, (runBlocking { book.balance(destination.id) } as LedgerResult.Success).value)
        assertEquals(beforeTotals, (runBlocking { book.totals(today.withDayOfMonth(1), today) } as LedgerResult.Success).value)
    }

    @Test fun linkedPartialAndStandaloneRefundsUseSelectedReceivingAccount() {
        ready()
        val suffix = UUID.randomUUID().toString().take(8)
        val sourceName = "Synthetic purchase source $suffix"
        val targetName = "Synthetic refund target $suffix"
        val purchaseMerchant = "Synthetic purchase $suffix"
        val standaloneMerchant = "Synthetic standalone refund $suffix"
        newAccount(sourceName, "0")
        newAccount(targetName, "0")
        val app = context.applicationContext as KocheniteApplication
        val book = (runBlocking { app.financialStorage.open() } as LedgerResult.Success).value
        val accounts = (runBlocking { book.accounts() } as LedgerResult.Success).value
        val source = accounts.single { it.name == sourceName }
        val target = accounts.single { it.name == targetName }
        compose.onNodeWithText("Add").performClick()
        compose.onNodeWithTag("choice-Account name").performClick()
        compose.onNodeWithTag("choice-Account name-option-${source.id}").performScrollTo().performClick()
        field("Amount", "10.00")
        field("Merchant or payee", purchaseMerchant)
        compose.onNodeWithText("Post transaction").performScrollTo().performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.page() as LedgerResult.Success).value.entries.any { it.input.merchant == purchaseMerchant } }
        }
        val purchase = (runBlocking { book.page() } as LedgerResult.Success).value.entries.single { it.input.merchant == purchaseMerchant }
        compose.waitUntil(15000) { compose.onAllNodesWithText("New transaction").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithText(purchaseMerchant, substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Refund this allocation").performClick()
        field("Amount", "3.00")
        compose.onNodeWithTag("choice-Account name").performClick()
        compose.onNodeWithTag("choice-Account name-option-${target.id}").performScrollTo().performClick()
        compose.onNodeWithText("Post transaction").performScrollTo().performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.page() as LedgerResult.Success).value.entries.any { it.input.purchaseId == purchase.id } }
        }
        val linked = (runBlocking { book.page() } as LedgerResult.Success).value.entries.single { it.input.purchaseId == purchase.id }
        assertEquals(300L, linked.input.amountMinor)
        assertEquals(target.id, linked.input.accountId)
        assertEquals(-1000L, (runBlocking { book.balance(source.id) } as LedgerResult.Success).value)
        assertEquals(300L, (runBlocking { book.balance(target.id) } as LedgerResult.Success).value)

        compose.waitUntil(15000) { compose.onAllNodesWithText("New transaction").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Add").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("choice-Transaction kind").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("choice-Transaction kind").performClick()
        compose.onNodeWithTag("choice-Transaction kind-option-REFUND").performClick()
        compose.onNodeWithTag("choice-Account name").performClick()
        compose.onNodeWithTag("choice-Account name-option-${target.id}").performScrollTo().performClick()
        field("Amount", "2.00")
        field("Merchant or payee", standaloneMerchant)
        compose.onNodeWithText("Post transaction").performScrollTo().performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.page() as LedgerResult.Success).value.entries.any { it.input.merchant == standaloneMerchant } }
        }
        val standalone =
            (runBlocking { book.page() } as LedgerResult.Success).value.entries.single {
                it.input.merchant ==
                    standaloneMerchant
            }
        assertEquals(null, standalone.input.purchaseId)
        assertEquals(500L, (runBlocking { book.balance(target.id) } as LedgerResult.Success).value)
    }

    @Test fun receiptLinesAndReviewedForeignAllocationsPostExactCharge() {
        ready()
        val merchant = "Synthetic itemized ${UUID.randomUUID().toString().take(8)}"
        val app = context.applicationContext as KocheniteApplication
        val book = (runBlocking { app.financialStorage.open() } as LedgerResult.Success).value
        val account = (runBlocking { book.accountRepository.list() } as LedgerResult.Success).value.first { !it.account.archived }.account
        val before = (runBlocking { book.balance(account.id) } as LedgerResult.Success).value
        compose.onNodeWithText("Add").performClick()
        field("Amount", "10.01")
        field("Merchant or payee", merchant)
        field("Merchant amount (optional context)", "300")
        field("Merchant currency (optional context)", "JPY")
        compose.onNodeWithText("Add split").performScrollTo().performClick()
        fieldAt("Account-currency allocation", 0, "5.00")
        fieldAt("Account-currency allocation", 1, "5.01")
        compose.onNodeWithText("Itemize receipt").performScrollTo().performClick()
        fieldAt("Line description", 0, "Synthetic first")
        fieldAt("Line description", 1, "Synthetic second")
        fieldAt("Final inclusive line total", 0, "100")
        fieldAt("Final inclusive line total", 1, "200")
        fieldAt("Account-currency allocation", 0, "3.35")
        fieldAt("Account-currency allocation", 1, "6.66")
        compose.onNodeWithText("Post transaction").performScrollTo().performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.page() as LedgerResult.Success).value.entries.any { it.input.merchant == merchant } }
        }
        val posted = (runBlocking { book.page() } as LedgerResult.Success).value.entries.single { it.input.merchant == merchant }
        assertEquals(listOf(335L, 666L), posted.input.allocations.map { it.amountMinor })
        assertEquals(listOf(100L, 200L), posted.input.receiptLines.map { it.merchantMinor })
        assertEquals(Math.subtractExact(before, 1001L), (runBlocking { book.balance(account.id) } as LedgerResult.Success).value)
    }
}
