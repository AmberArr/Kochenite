package moe.frost.kochenite.transactions

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import moe.frost.kochenite.domain.ledger.LedgerCursor
import moe.frost.kochenite.domain.ledger.LedgerFilter
import moe.frost.kochenite.domain.ledger.LedgerPage
import moe.frost.kochenite.domain.ledger.LedgerResult
import moe.frost.kochenite.transactions.list.TransactionListStateOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionListStateOwnerTest {
    @Test fun repeatedLoadMoreRequestsUseOneCursorAndMergeUniqueEntries() =
        runTest {
            val a = fixtureEntry("a")
            val b = fixtureEntry("b")
            val cursor = LedgerCursor(a.input.date, a.input.time, a.id)
            val release = CompletableDeferred<Unit>()
            var pageCalls = 0
            val book =
                FakeLedger().apply {
                    searchCall = { _, after ->
                        if (after == null) {
                            LedgerResult.Success(LedgerPage(listOf(a), cursor))
                        } else {
                            pageCalls++
                            release.await()
                            LedgerResult.Success(LedgerPage(listOf(a, b), null))
                        }
                    }
                }
            val owner = TransactionListStateOwner(this) { book }
            owner.refresh()
            runCurrent()
            repeat(5) { owner.loadMore() }
            runCurrent()
            assertEquals(1, pageCalls)
            assertTrue(owner.state.value.loading)
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                listOf("a", "b"),
                owner.state.value.entries
                    .map { it.id },
            )
            assertNull(owner.state.value.next)
        }

    @Test fun refreshRejectsAnOldPageEvenWhenItsQueryIsUnchanged() =
        runTest {
            val a = fixtureEntry("a")
            val cursor = LedgerCursor(a.input.date, a.input.time, a.id)
            val release = CompletableDeferred<Unit>()
            val book =
                FakeLedger().apply {
                    searchCall = { _, after ->
                        if (after == null) {
                            LedgerResult.Success(LedgerPage(listOf(a), cursor))
                        } else {
                            withContext(NonCancellable) { release.await() }
                            LedgerResult.Success(LedgerPage(listOf(fixtureEntry("obsolete")), null))
                        }
                    }
                }
            val owner = TransactionListStateOwner(this) { book }
            owner.refresh()
            runCurrent()
            owner.loadMore()
            runCurrent()
            owner.refresh()
            runCurrent()
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(
                listOf("a"),
                owner.state.value.entries
                    .map { it.id },
            )
            assertEquals(cursor, owner.state.value.next)
        }

    @Test fun changingFiltersClearsSelectionBeforeNewResultsArrive() =
        runTest {
            val a = fixtureEntry("a")
            val b = fixtureEntry("b")
            val book =
                FakeLedger().apply {
                    searchCall = { filter, _ ->
                        LedgerResult.Success(LedgerPage(if (filter.text.isEmpty()) listOf(a, b) else listOf(b), null))
                    }
                }
            val owner = TransactionListStateOwner(this) { book }
            owner.refresh()
            advanceUntilIdle()
            owner.toggle(a, "allocation")
            owner.toggle(b, "allocation")
            assertEquals(2, owner.state.value.selection.size)
            owner.search(LedgerFilter(text = "b"))
            assertTrue(
                owner.state.value.selection
                    .isEmpty(),
            )
            advanceUntilIdle()
            owner.toggle(b, "allocation")
            assertEquals(
                listOf("b"),
                owner.state.value.selection
                    .map { it.entryId },
            )
            assertEquals(
                listOf("b"),
                owner.state.value.entries
                    .map { it.id },
            )
        }

    @Test fun textChangesDebounceAndOnlyLatestQueryRuns() =
        runTest {
            val queries = mutableListOf<String>()
            val book =
                FakeLedger().apply {
                    searchCall = { filter, _ ->
                        queries += filter.text
                        LedgerResult.Success(LedgerPage(emptyList(), null))
                    }
                }
            val owner = TransactionListStateOwner(this) { book }
            owner.search(LedgerFilter(text = "o"))
            runCurrent()
            owner.search(LedgerFilter(text = "on"))
            runCurrent()
            owner.search(LedgerFilter(text = "one"))
            advanceUntilIdle()
            assertEquals(listOf("one"), queries)
        }

    @Test fun refreshReloadsTheLoadedDepthWhileKeepingRowsVisible() =
        runTest {
            val ledger = (1..120).map { fixtureEntry("e%03d".format(it)) }
            var gate: CompletableDeferred<Unit>? = null
            val limits = mutableListOf<Int>()
            val paged =
                object : FakeLedger() {
                    override suspend fun search(
                        filter: LedgerFilter,
                        after: LedgerCursor?,
                        limit: Int,
                    ): LedgerResult<LedgerPage> {
                        limits += limit
                        gate?.await()
                        val start = after?.let { cursor -> ledger.indexOfFirst { it.id == cursor.id } + 1 } ?: 0
                        val page = ledger.drop(start).take(limit)
                        val next =
                            if (start + limit < ledger.size) page.last().let { LedgerCursor(it.input.date, it.input.time, it.id) } else null
                        return LedgerResult.Success(LedgerPage(page, next))
                    }
                }
            val owner = TransactionListStateOwner(this) { paged }
            owner.refresh()
            advanceUntilIdle()
            owner.loadMore()
            advanceUntilIdle()
            assertEquals(100, owner.state.value.entries.size)
            limits.clear()
            val release = CompletableDeferred<Unit>()
            gate = release
            owner.refresh()
            runCurrent()
            assertTrue(owner.state.value.loading)
            assertEquals(100, owner.state.value.entries.size)
            release.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf(100), limits)
            assertEquals(ledger.take(100), owner.state.value.entries)
            assertEquals(
                "e100",
                owner.state.value.next
                    ?.id,
            )
        }

    @Test fun refreshKeepsOnlySelectionsWhoseEntryIsUnchanged() =
        runTest {
            val a = fixtureEntry("a")
            val b = fixtureEntry("b")
            var entries = listOf(a, b)
            val book =
                FakeLedger().apply {
                    searchCall = { _, _ -> LedgerResult.Success(LedgerPage(entries, null)) }
                }
            val owner = TransactionListStateOwner(this) { book }
            owner.refresh()
            advanceUntilIdle()
            owner.toggle(a, "allocation")
            owner.toggle(b, "allocation")
            entries = listOf(a, b.copy(token = "recategorized"))
            owner.refresh()
            advanceUntilIdle()
            assertEquals(
                listOf("a"),
                owner.state.value.selection
                    .map { it.entryId },
            )
        }

    @Test fun failedRefreshKeepsRowsAndReportsTheError() =
        runTest {
            val a = fixtureEntry("a")
            var fail = false
            val book =
                FakeLedger().apply {
                    searchCall =
                        { _, _ -> if (fail) LedgerResult.TemporarilyUnavailable else LedgerResult.Success(LedgerPage(listOf(a), null)) }
                }
            val owner = TransactionListStateOwner(this) { book }
            owner.refresh()
            advanceUntilIdle()
            fail = true
            owner.refresh()
            advanceUntilIdle()
            assertEquals(listOf(a), owner.state.value.entries)
            assertEquals(LedgerResult.TemporarilyUnavailable, owner.state.value.error)
            assertTrue(!owner.state.value.loading)
        }
}
