package moe.frost.kochenite

import android.content.Intent
import android.os.Process
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import moe.frost.kochenite.domain.ledger.LedgerResult
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Run the two methods separately, with an external force-stop between them. */
class BookEditorProcessDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("synthetic-unsaved-process-test", 0)

    private fun launch(): MainActivity {
        val activity =
            instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            ) as MainActivity
        compose.waitUntil(15000) { compose.onAllNodesWithTag("nav-1").fetchSemanticsNodes().isNotEmpty() }
        return activity
    }

    @Test fun leaveUnsavedEditorInProcess() {
        val activity = launch()
        compose.onNodeWithTag("nav-1").performClick()
        compose.onNodeWithTag("add-transaction").performClick()
        compose.onNodeWithTag("field-Title").performTextReplacement("Synthetic unsaved process test")
        compose.onNodeWithTag("field-Amount").performTextReplacement("42.19")
        compose.runOnIdle {
            val editor = ViewModelProvider(activity)[BookViewModel::class.java].transactionEditor.editor.value!!
            assertTrue(
                prefs
                    .edit()
                    .putString("id", editor.input.id)
                    .putInt("pid", Process.myPid())
                    .commit(),
            )
        }
        // No ActivityScenario rule closes this activity before the runner kills its process.
    }

    @Test fun verifyUnsavedEditorLostAfterRestart() =
        runBlocking {
            val id = prefs.getString("id", null) ?: error("Create step did not run")
            assertNotEquals(prefs.getInt("pid", -1), Process.myPid())
            val activity = launch()
            compose.runOnIdle { assertNull(ViewModelProvider(activity)[BookViewModel::class.java].transactionEditor.editor.value) }
            val ledger = ((context.applicationContext as KocheniteApplication).financialStorage.open() as LedgerResult.Success).value
            assertTrue(ledger.entry(id) is LedgerResult.Missing)
            assertTrue(ledger.incomplete(id) is LedgerResult.Missing)
            assertTrue(prefs.edit().clear().commit())
            compose.runOnIdle { activity.finish() }
        }
}
