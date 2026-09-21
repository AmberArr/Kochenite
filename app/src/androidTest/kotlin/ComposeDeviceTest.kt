package moe.frost.kochenite.proof.test

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class ComposeDeviceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun targetAppDisplaysComposeContent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent().setClassName(
            "moe.frost.kochenite.debug",
            "moe.frost.kochenite.proof.ui.MainActivity",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        try {
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("ready").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Kochenite").assertIsDisplayed()
            val countText = compose.onNodeWithTag("record-count").fetchSemanticsNode()
                .config[SemanticsProperties.Text].single().text
            val count = countText.removePrefix("Records: ").toInt()
            compose.onNodeWithText("Add sample").performClick()
            compose.waitUntil(15_000) {
                compose.onAllNodesWithText("Records: ${count + 1}").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Records: ${count + 1}").assertIsDisplayed()
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}
