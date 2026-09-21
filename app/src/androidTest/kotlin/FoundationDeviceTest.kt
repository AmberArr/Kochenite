package moe.frost.kochenite

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import moe.frost.kochenite.domain.StorageState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class FoundationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val checkpoint get() = File(context.noBackupFilesDir, "instrumentation-snapshot.json")

    private fun ready() {
        compose.activityRule.scenario.onActivity {
            it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("record-count").fetchSemanticsNodes().size == 1 }
    }

    private fun snapshot(): String = runBlocking {
        val repository = ((context.applicationContext as KocheniteApplication).storage.open() as StorageState.Ready).repository
        JSONArray().apply {
            repository.observe().first().sortedBy { it.id }.forEach {
                put(JSONObject().put("id", it.id).put("amountMinor", it.amountMinor)
                    .put("currency", it.currency).put("currencyScale", it.currencyScale)
                    .put("occurredAtEpochMillis", it.occurredAtEpochMillis).put("note", it.note ?: JSONObject.NULL))
            }
        }.toString()
    }

    @Test fun addRecreateAndCheckpoint() {
        ready()
        val before = JSONArray(snapshot()).length()
        compose.onNodeWithText("Add sample").assertHasClickAction().assertIsEnabled().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Records: ${before + 1}").fetchSemanticsNodes().size == 1
        }
        val saved = snapshot()
        compose.activityRule.scenario.recreate()
        ready()
        compose.onNodeWithText("Records: ${before + 1}").assertIsDisplayed()
        assertEquals(saved, snapshot())
        checkpoint.writeText(saved)
    }

    @Test fun verifyCheckpointAfterProcessRestart() {
        ready()
        assertTrue("Run checkpoint test first", checkpoint.isFile)
        assertEquals(checkpoint.readText(), snapshot())
        compose.onNodeWithText("Records: ${JSONArray(checkpoint.readText()).length()}").assertIsDisplayed()
    }
}
