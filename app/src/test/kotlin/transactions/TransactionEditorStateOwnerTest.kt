package moe.frost.kochenite.transactions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import moe.frost.kochenite.domain.editor.EditorInput
import moe.frost.kochenite.domain.editor.withMainText
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.transactions.editor.TransactionEditorStateOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionEditorStateOwnerTest {
    @Test fun saveClosesOnceBeforeDelayedRefreshAndDoesNotCloseANewEditor() =
        runTest {
            val book = FakeLedger()
            val refresh = CompletableDeferred<Unit>()
            var saved = 0
            var posts = 0
            val post = book.postCall
            book.postCall = {
                posts++
                post(it)
            }
            val owner =
                TransactionEditorStateOwner(this, { book }, { book }, { listOf(book.account) }, { error("Unexpected error") }) {
                    saved++
                    refresh.await()
                }
            owner.newEditor("first")
            owner.updateEditor { it.withMainText("10", listOf(book.account)) }
            owner.confirmEditor()
            owner.confirmEditor()
            runCurrent()
            assertEquals(1, posts)
            assertEquals(1, saved)
            assertNull(owner.editor.value)
            owner.newEditor("second")
            refresh.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                "second",
                owner.editor.value
                    ?.input
                    ?.id,
            )
            assertEquals(1, saved)
        }

    @Test fun failedSubmissionKeepsInputAndAllowsRetry() =
        runTest {
            val book = FakeLedger().apply { postCall = { LedgerResult.Conflict } }
            val owner = TransactionEditorStateOwner(this, { book }, { book }, { listOf(book.account) }, {}) { error("Must not complete") }
            owner.newEditor("first")
            owner.updateEditor { it.withMainText("12.34", listOf(book.account)) }
            val before = owner.editor.value!!.input
            owner.confirmEditor()
            advanceUntilIdle()
            assertEquals(before, owner.editor.value!!.input)
            assertFalse(owner.editor.value!!.saving)
            assertNotNull(owner.editor.value!!.error)
        }

    @Test fun discardAndUnchangedBackCompleteOnlyOnce() =
        runTest {
            val book = FakeLedger()
            val owner = TransactionEditorStateOwner(this, { book }, { book }, { listOf(book.account) }, {}) {}
            var closed = 0
            owner.newEditor("first")
            owner.closeEditor { closed++ }
            assertEquals(1, closed)
            assertNull(owner.editor.value)
            owner.newEditor("second")
            owner.updateEditor { it.withMainText("10", listOf(book.account)) }
            owner.closeEditor { closed++ }
            assertTrue(owner.editor.value!!.confirmDiscard)
            assertEquals(1, closed)
            owner.discardEditor()
            owner.discardEditor()
            assertEquals(2, closed)
            assertNull(owner.editor.value)
        }

    private class EditableLedger(
        val gate: CompletableDeferred<Unit>? = null,
    ) : FakeLedger() {
        var begins = 0

        override suspend fun beginEdit(entryId: String): LedgerResult<EditorInput> {
            begins++
            gate?.await()
            val entry = fixtureEntry(entryId)
            return LedgerResult.Success(EditorInput("editor-$entryId-$begins", entry.id, "draft", entry.input, entry.token))
        }
    }

    @Test fun repeatedOpenRequestsLoadAndNavigateOnce() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val book = EditableLedger(gate)
            val owner = TransactionEditorStateOwner(this, { book }, { book }, { listOf(book.account) }, {}) {}
            val navigated = mutableListOf<String>()
            repeat(3) {
                owner.startEdit("a") {
                    navigated += it
                    true
                }
            }
            owner.copyEntry("a") {
                navigated += it
                true
            }
            runCurrent()
            assertEquals(1, book.begins)
            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("editor-a-1"), navigated)
            assertEquals(
                "editor-a-1",
                owner.editor.value
                    ?.input
                    ?.id,
            )
        }

    @Test fun rejectedNavigationLeavesNoEditorBehind() =
        runTest {
            val book = EditableLedger()
            val owner = TransactionEditorStateOwner(this, { book }, { book }, { listOf(book.account) }, {}) {}
            owner.startEdit("a") { false }
            advanceUntilIdle()
            assertNull(owner.editor.value)
            owner.startLinkedRefund(fixtureEntry("a"), "allocation") { false }
            assertNull(owner.editor.value)
            owner.startEdit("a") { true }
            advanceUntilIdle()
            assertNotNull(owner.editor.value)
        }
}
