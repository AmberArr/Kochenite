package moe.frost.kochenite

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import moe.frost.kochenite.domain.ledger.Allocation
import moe.frost.kochenite.domain.ledger.Category
import moe.frost.kochenite.domain.ledger.CategoryKind
import moe.frost.kochenite.domain.ledger.Draft
import moe.frost.kochenite.domain.ledger.EntryInput
import moe.frost.kochenite.domain.ledger.EntryKind
import moe.frost.kochenite.domain.ledger.EntryStatus
import moe.frost.kochenite.domain.ledger.LedgerFilter
import moe.frost.kochenite.domain.ledger.LedgerResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class BookUiDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun field(
        label: String,
        value: String,
    ) {
        compose.waitUntil(15000) { compose.onAllNodesWithTag("field-$label").fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNodeWithTag("field-$label")
        runCatching { node.performScrollTo() }
        node.performTextReplacement(value)
    }

    private fun fieldAt(
        label: String,
        index: Int,
        value: String,
    ) {
        compose.waitUntil(15000) { compose.onAllNodesWithTag("field-$label").fetchSemanticsNodes().size > index }
        compose.onAllNodesWithTag("field-$label")[index].performScrollTo().performTextReplacement(value)
    }

    private fun selectChoice(
        label: String,
        id: String,
        searchText: String? = null,
        index: Int = 0,
    ) {
        val choice = compose.onAllNodesWithTag("choice-$label")[index]
        runCatching { choice.performScrollTo() }
        choice.performClick()
        val searchTag = "choice-search-$label"
        if (searchText != null && compose.onAllNodesWithTag(searchTag).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag(searchTag).performTextReplacement(searchText)
        }
        val optionTag = "choice-$label-option-$id"
        compose.waitUntil(15000) { compose.onAllNodesWithTag(optionTag).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(optionTag).performScrollTo().performClick()
    }

    private fun waitForPostEnabled() {
        compose.waitUntil(15000) {
            runCatching { compose.onNodeWithText("Post transaction").assertIsEnabled() }.isSuccess
        }
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
        compose.onNodeWithTag("field-Category name").assertTextContains(name)
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
        compose.waitUntil(15000) { compose.onAllNodesWithTag("field-Amount").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("New transaction").assertIsDisplayed()
        field("Amount", "-12.34")
        field("Merchant or payee", merchant)
        compose.waitUntil(15000) {
            runBlocking {
                (book.drafts() as LedgerResult.Success).value.any { it.input.merchant == merchant && it.amountText == "-12.34" }
            }
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(merchant).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Post transaction").performClick()
        compose.waitUntil(15000) {
            runBlocking {
                (book.page() as LedgerResult.Success).value.entries.any { it.input.merchant == merchant }
            }
        }
        val posted = (runBlocking { book.page() } as LedgerResult.Success).value.entries.single { it.input.merchant == merchant }
        val after = (runBlocking { book.balance(firstAccount.id) } as LedgerResult.Success).value
        assertEquals(Math.subtractExact(before, 1234L), after)
        assertEquals(0, (runBlocking { book.drafts() } as LedgerResult.Success).value.count { it.input.merchant == merchant })
        lateinit var model: BookViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[BookViewModel::class.java] }
        compose.waitUntil(15000) { !model.state.value.saving }
        compose.waitUntil(15000) { compose.onAllNodesWithText("New transaction").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(15000) {
            runCatching { compose.onNodeWithTag("nav-0").assertIsSelected() }.isSuccess
        }
        compose.onNodeWithTag("nav-1").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Posted").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Posted").performClick()
        field("Search merchant, notes, or receipt lines", merchant)
        val postedRow = "posted-entry-${posted.id}"
        compose.waitUntil(15000) { compose.onAllNodesWithTag(postedRow).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(postedRow).performScrollTo().performClick()
        compose.onNodeWithText("Edit transaction").performClick()
        field("Amount", "-20.00")
        compose.onNodeWithText("Post transaction").performClick()
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
        compose.waitUntil(15000) { compose.onAllNodesWithText("Restore").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("nav-1").assertIsSelected()
        compose.onAllNodesWithText("Transactions")[0].assertIsDisplayed()
        compose.onNodeWithText("Trash").performScrollTo().assertIsDisplayed()
        compose.waitForIdle()
        compose.onNodeWithText("Trash").performClick()
        assertTrue(
            (runBlocking { book.search(LedgerFilter(status = EntryStatus.TRASH)) } as LedgerResult.Success).value.entries.any {
                it.id ==
                    posted.id
            },
        )
        val filterChanged = runCatching { compose.waitUntil(5000) { model.searchFilter.status == EntryStatus.TRASH } }.isSuccess
        assertTrue("Trash filter did not activate: ${model.searchFilter}", filterChanged)
        val foundInSearch =
            runCatching {
                compose.waitUntil(5000) {
                    model.state.value.searchEntries
                        .any { it.id == posted.id && it.status == EntryStatus.TRASH }
                }
            }.isSuccess
        assertTrue(
            "Trash search status=${model.searchFilter.status}, error=${model.state.value.error}, " +
                "saving=${model.state.value.saving}, results=${model.state.value.searchEntries.map { it.id to it.status }}",
            foundInSearch,
        )
        compose.waitUntil(15000) { compose.onAllNodesWithText(merchant, substring = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(postedRow).performScrollTo().performClick()
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
        selectChoice("Account name", source.id, sourceName)
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
        field("Amount sent", "10.00")
        compose.onNodeWithTag("choice-To account").performScrollTo()
        selectChoice("To account", destination.id, destinationName)
        field("Merchant or payee", merchant)
        compose.onNodeWithText("More details").performScrollTo().performClick()
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
        compose.onNodeWithText("Post transaction").performClick()
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
        selectChoice("Account name", source.id, sourceName)
        field("Amount", "-10.00")
        field("Merchant or payee", purchaseMerchant)
        compose.onNodeWithText("Post transaction").performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.page() as LedgerResult.Success).value.entries.any { it.input.merchant == purchaseMerchant } }
        }
        val purchase = (runBlocking { book.page() } as LedgerResult.Success).value.entries.single { it.input.merchant == purchaseMerchant }
        compose.waitUntil(15000) { compose.onAllNodesWithText("New transaction").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(15000) {
            runCatching { compose.onNodeWithTag("nav-2").assertIsSelected() }.isSuccess
        }
        compose.onNodeWithTag("nav-1").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Posted").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Posted").performClick()
        field("Search merchant, notes, or receipt lines", purchaseMerchant)
        val purchaseRow = "posted-entry-${purchase.id}"
        compose.waitUntil(15000) { compose.onAllNodesWithTag(purchaseRow).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(purchaseRow).performScrollTo().performClick()
        compose.onNodeWithText("Create expense credit for this allocation").performClick()
        field("Amount", "3.00")
        selectChoice("Account name", target.id, targetName)
        compose.onNodeWithTag("choice-Account name").assertTextContains(targetName)
        waitForPostEnabled()
        compose.onNodeWithText("Post transaction").performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.page() as LedgerResult.Success).value.entries.any { purchase.id in it.input.relatedEntryIds } }
        }
        val linked = (runBlocking { book.page() } as LedgerResult.Success).value.entries.single { purchase.id in it.input.relatedEntryIds }
        assertEquals(300L, linked.input.amountMinor)
        assertEquals(target.id, linked.input.accountId)
        assertEquals(-1000L, (runBlocking { book.balance(source.id) } as LedgerResult.Success).value)
        assertEquals(300L, (runBlocking { book.balance(target.id) } as LedgerResult.Success).value)

        compose.waitUntil(15000) { compose.onAllNodesWithText("New transaction").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Add").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("choice-Transaction kind").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("choice-Transaction kind").performClick()
        compose.onNodeWithTag("choice-Transaction kind-option-EXPENSE").performClick()
        selectChoice("Account name", target.id, targetName)
        compose.onNodeWithTag("choice-Account name").assertTextContains(targetName)
        field("Amount", "2.00")
        field("Merchant or payee", standaloneMerchant)
        compose.onNodeWithText("Post transaction").performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.page() as LedgerResult.Success).value.entries.any { it.input.merchant == standaloneMerchant } }
        }
        val standalone =
            (runBlocking { book.page() } as LedgerResult.Success).value.entries.single {
                it.input.merchant ==
                    standaloneMerchant
            }
        assertEquals(emptyList<String>(), standalone.input.relatedEntryIds)
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
        field("Amount", "-10.01")
        field("Merchant or payee", merchant)
        compose.onNodeWithText("More details").performScrollTo().performClick()
        field("Merchant amount (optional context)", "300")
        field("Merchant currency (optional context)", "JPY")
        compose.onNodeWithText("Allocation details").performScrollTo().performClick()
        compose.onNodeWithText("Add row").performScrollTo().performClick()
        fieldAt("Account-currency allocation", 0, "5.00")
        fieldAt("Account-currency allocation", 1, "5.01")
        compose.onNodeWithText("Receipt details").performScrollTo().performClick()
        fieldAt("Line description", 0, "Synthetic first")
        fieldAt("Line description", 1, "Synthetic second")
        fieldAt("Final inclusive line total", 0, "100")
        fieldAt("Final inclusive line total", 1, "200")
        fieldAt("Account-currency allocation", 0, "3.35")
        fieldAt("Account-currency allocation", 1, "6.66")
        compose.onNodeWithText("Post transaction").performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.page() as LedgerResult.Success).value.entries.any { it.input.merchant == merchant } }
        }
        val posted = (runBlocking { book.page() } as LedgerResult.Success).value.entries.single { it.input.merchant == merchant }
        assertEquals(listOf(335L, 666L), posted.input.allocations.map { it.amountMinor })
        assertEquals(listOf(100L, 200L), posted.input.receiptLines.map { it.merchantMinor })
        assertEquals(Math.subtractExact(before, 1001L), (runBlocking { book.balance(account.id) } as LedgerResult.Success).value)
    }

    @Test fun tagSearchCopyAndTemplateKeepNewWorkAsDrafts() {
        ready()
        val suffix = UUID.randomUUID().toString().take(8)
        val tagName = "Synthetic trip $suffix"
        val merchant = "Synthetic 東京 $suffix"
        val templateName = "Synthetic routine $suffix"
        val app = context.applicationContext as KocheniteApplication
        val book = (runBlocking { app.financialStorage.open() } as LedgerResult.Success).value
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Tags").performClick()
        compose.onNodeWithText("New tag").performClick()
        field("Tag name", tagName)
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.tagRepository.list() as LedgerResult.Success).value.any { it.name == tagName } }
        }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("field-Tag name").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Add").performClick()
        field("Amount", "-4.00")
        field("Merchant or payee", merchant)
        compose.onNodeWithText("More details").performScrollTo().performClick()
        compose.onNodeWithText("Tags").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("entry-tag-picker-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("entry-tag-picker-list").performScrollToNode(hasText(tagName))
        compose.onNodeWithText(tagName).performClick()
        compose.waitUntil(15000) {
            runCatching { compose.onNodeWithTag("entry-tag-picker-done").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("entry-tag-picker-done").performClick()
        compose.onNodeWithText("Post transaction").performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.page() as LedgerResult.Success).value.entries.any { it.input.merchant == merchant } }
        }
        val posted = (runBlocking { book.page() } as LedgerResult.Success).value.entries.single { it.input.merchant == merchant }
        assertEquals(1, posted.input.tagIds.size)
        compose.waitUntil(15000) { compose.onAllNodesWithText("New transaction").fetchSemanticsNodes().isEmpty() }
        compose.waitUntil(15000) { compose.onAllNodesWithText("New tag").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav-1").performClick()
        compose.waitUntil(15000) {
            runCatching { compose.onNodeWithTag("nav-1").assertIsSelected() }.isSuccess
        }
        compose.waitUntil(15000) { compose.onAllNodesWithText("Posted").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Posted").performScrollTo().performClick()
        field("Search merchant, notes, or receipt lines", "東京")
        val postedRow = "posted-entry-${posted.id}"
        compose.waitUntil(15000) { compose.onAllNodesWithTag(postedRow).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Filter").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("choice-Tags").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("choice-Tags").performClick()
        compose.waitUntil(15000) {
            compose.onAllNodesWithTag("choice-Tags-option-${posted.input.tagIds.single()}").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("choice-Tags-option-${posted.input.tagIds.single()}").performClick()
        if (compose.onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Done").performScrollTo().performClick()
        }
        compose.onNodeWithTag(postedRow).performScrollTo().performClick()
        compose.onNodeWithText("Copy as new draft").performScrollTo().performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.drafts() as LedgerResult.Success).value.any { it.input.merchant == merchant && it.entryId != posted.id } }
        }
        compose.waitUntil(15000) { compose.onAllNodesWithText("New transaction").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Discard draft").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Copy as new draft").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Save as template").performScrollTo().performClick()
        field("Template name", templateName)
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(15000) {
            runBlocking { (book.templates() as LedgerResult.Success).value.any { it.name == templateName } }
        }
        compose.waitUntil(15000) { compose.onAllNodesWithTag("field-Template name").fetchSemanticsNodes().isEmpty() }
        lateinit var model: BookViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[BookViewModel::class.java] }
        compose.waitUntil(15000) { !model.state.value.saving }
        compose.onNodeWithTag("nav-1").performClick()
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("nav-1").assertIsSelected() }.isSuccess }
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Templates").performClick()
        compose.onNodeWithText(templateName).performScrollTo().assertIsDisplayed()
        val template = (runBlocking { book.templates() } as LedgerResult.Success).value.single { it.name == templateName }
        compose.onNodeWithTag("template-use-${template.id}").performScrollTo().performClick()
        compose.waitUntil(15000) {
            runBlocking {
                (book.drafts() as LedgerResult.Success).value.any { it.input.merchant == merchant && it.input.amountMinor == null }
            }
        }
        assertEquals(1, (runBlocking { book.page() } as LedgerResult.Success).value.entries.count { it.input.merchant == merchant })
    }

    @Test fun bulkCategoryPreviewChangesOnlySelectedAllocation() {
        ready()
        val suffix = UUID.randomUUID().toString().take(8)
        val merchant = "Synthetic bulk $suffix"
        val category = Category(UUID.randomUUID().toString(), "Synthetic bulk category $suffix", CategoryKind.EXPENSE)
        val app = context.applicationContext as KocheniteApplication
        val book = (runBlocking { app.financialStorage.open() } as LedgerResult.Success).value
        val account = (runBlocking { book.accountRepository.list() } as LedgerResult.Success).value.first { !it.account.archived }.account
        val beforeBalance = (runBlocking { book.balance(account.id) } as LedgerResult.Success).value
        val date = LocalDate.now()
        val beforeTotals = (runBlocking { book.totals(date.withDayOfMonth(1), date) } as LedgerResult.Success).value
        assertTrue(runBlocking { book.categoryRepository.save(category) } is LedgerResult.Success)
        val allocationId = UUID.randomUUID().toString()
        val untouchedId = UUID.randomUUID().toString()
        val input =
            EntryInput(
                EntryKind.EXPENSE,
                date,
                LocalTime.NOON,
                account.id,
                -789L,
                allocations = listOf(Allocation(allocationId, 300L), Allocation(untouchedId, 489L)),
                merchant = merchant,
            )
        val saved =
            runBlocking {
                book.saveDraft(Draft(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "", input), null)
            } as LedgerResult.Success
        val posted = (runBlocking { book.confirm(saved.value.id, saved.value.token) } as LedgerResult.Success).value
        lateinit var model: BookViewModel
        compose.activityRule.scenario.onActivity { model = ViewModelProvider(it)[BookViewModel::class.java] }
        model.refresh()
        compose.onNodeWithTag("nav-1").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Posted").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Posted").performScrollTo().performClick()
        field("Search merchant, notes, or receipt lines", merchant)
        val postedRow = "posted-entry-${posted.id}"
        compose.waitUntil(15000) { compose.onAllNodesWithTag(postedRow).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("More").performClick()
        compose.onNodeWithText("Bulk categorize allocations").performClick()
        val bulkRow = "bulk-row-${posted.id}-$allocationId"
        compose.waitUntil(15000) { compose.onAllNodesWithTag(bulkRow).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(bulkRow).performScrollTo().performClick()
        compose.waitUntil(15000) {
            runCatching { compose.onNodeWithText("Selected allocation rows: 1").assertIsDisplayed() }.isSuccess
        }
        selectChoice("Category", category.id, category.name)
        compose.onNodeWithText("Apply category to selected rows").performClick()
        compose.waitUntil(15000) {
            runBlocking {
                (book.entry(posted.id) as LedgerResult.Success)
                    .value.input.allocations
                    .find { it.id == allocationId }
                    ?.categoryId == category.id
            }
        }
        assertEquals(
            null,
            (
                runBlocking {
                    book.entry(
                        posted.id,
                    )
                } as LedgerResult.Success
            ).value.input.allocations.find { it.id == untouchedId }?.categoryId,
        )
        assertEquals(Math.subtractExact(beforeBalance, 789L), (runBlocking { book.balance(account.id) } as LedgerResult.Success).value)
        val afterTotals = (runBlocking { book.totals(date.withDayOfMonth(1), date) } as LedgerResult.Success).value
        val oldUsd = beforeTotals.find { it.currency == account.currency }
        val newUsd = afterTotals.single { it.currency == account.currency }
        assertEquals(Math.addExact(oldUsd?.spendingMinor ?: 0L, 789L), newUsd.spendingMinor)
    }
}
