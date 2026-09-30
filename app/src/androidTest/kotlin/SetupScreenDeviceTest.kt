package moe.frost.kochenite

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.navigation.RecoveryScreen
import moe.frost.kochenite.navigation.StorageUnavailableScreen
import moe.frost.kochenite.setup.SetupScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * First-run and storage-fault screens, rendered on their own so they can be checked on a phone whose
 * book already exists. Nothing here opens or changes the app's database.
 */
class SetupScreenDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun setupChoosesTheAccountCurrencyFromTheOpeningBalancePill() {
        val model = BookViewModel { LedgerResult.TemporarilyUnavailable }
        compose.setContent { KocheniteTheme { SetupScreen(BookState(loading = false, storageReady = true), model) } }
        compose.onNodeWithText("Your local book").assertIsDisplayed()
        compose.onNodeWithTag("field-Account name").performTextReplacement("Synthetic everyday bank")
        compose.onNodeWithTag("amount-currency").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("currency-search").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("currency-search").performTextReplacement("JPY")
        compose.onNodeWithTag("currency-option-JPY").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithTag("currency-search").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Currency: JPY").assertIsDisplayed()
        compose.onNodeWithTag("field-Opening balance").performTextReplacement("250000")
        compose.onNodeWithContentDescription("Make amount negative").assertIsDisplayed()
        compose.onNodeWithTag("create-book").assertIsDisplayed().assertIsEnabled()
        // Yen has no minor unit, so a decimal opening balance is rejected before anything is saved.
        compose.onNodeWithTag("field-Opening balance").performTextReplacement("250000.5")
        compose.onNodeWithText("Enter an exact amount", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun temporaryStorageFaultExplainsTheImpactAndOffersOneRetry() {
        var retries = 0
        compose.setContent { KocheniteTheme { StorageUnavailableScreen { retries++ } } }
        compose.onNodeWithText("Storage temporarily unavailable").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test fun recoveryRequiredStatesThatTheFileIsPreserved() {
        var retries = 0
        compose.setContent { KocheniteTheme { RecoveryScreen { retries++ } } }
        compose.onNodeWithText("Storage needs recovery").assertIsDisplayed()
        compose.onNodeWithText("preserved as-is", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
}
