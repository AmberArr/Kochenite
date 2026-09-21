package moe.frost.kochenite

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import moe.frost.kochenite.domain.SampleRecord
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class FoundationPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun themesLargeTextAndAccessibleControls() {
        val variant = mutableStateOf(false to 1f)
        compose.setContent {
            val (dark, scale) = variant.value
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                FoundationTheme(dark) {
                    FoundationScreen(
                        FoundationState.Ready(
                            listOf(
                                SampleRecord("presentation", 12345, "JPY", 0, 1700000000000),
                            ),
                        ),
                        {},
                        {},
                    )
                }
            }
        }
        listOf(false to 1f, true to 1f, false to 2f).forEach { configuration ->
            compose.runOnIdle { variant.value = configuration }
            compose
                .onNodeWithText("Add sample")
                .assertIsDisplayed()
                .assertHasClickAction()
                .assertHeightIsAtLeast(48.dp)
            compose.onNodeWithText("Synthetic records").assertIsDisplayed()
            compose.onNodeWithTag("record-presentation").assertIsDisplayed()
            val button = compose.onNodeWithText("Add sample").fetchSemanticsNode().boundsInRoot
            val record = compose.onNodeWithTag("record-presentation").fetchSemanticsNode().boundsInRoot
            assertTrue("Record must not overlap add control", record.top >= button.bottom)
            val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "test-evidence")
            directory.mkdirs()
            val label =
                if (configuration.second > 1) {
                    "large-text"
                } else if (configuration.first) {
                    "dark"
                } else {
                    "light"
                }
            File(directory, "$label.png").outputStream().use {
                compose
                    .onRoot()
                    .captureToImage()
                    .asAndroidBitmap()
                    .compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun loadingEmptyAndFailureStates() {
        val state = mutableStateOf<FoundationState>(FoundationState.Loading)
        var retried = false
        compose.setContent { FoundationTheme { FoundationScreen(state.value, {}, { retried = true }) } }
        compose.onNodeWithText("Loading").assertIsDisplayed()
        compose.runOnIdle { state.value = FoundationState.Ready(emptyList()) }
        compose.onNodeWithText("No samples").assertIsDisplayed()
        compose.runOnIdle { state.value = FoundationState.TemporarilyUnavailable }
        compose.onNodeWithText("Storage temporarily unavailable").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertTrue(retried)
        compose.runOnIdle { state.value = FoundationState.RecoveryRequired }
        compose.onNodeWithText("Storage needs recovery. Existing data has been retained.").assertIsDisplayed()
    }
}
