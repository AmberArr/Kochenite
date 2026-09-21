package moe.frost.kochenite

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moe.frost.kochenite.domain.SampleRecord
import moe.frost.kochenite.domain.SampleRepository
import moe.frost.kochenite.domain.StorageInitializer
import moe.frost.kochenite.domain.StorageState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoundationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val record = SampleRecord("synthetic-id", 123, "USD", 2, 1700000000000)

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    private class FakeRepository : SampleRepository {
        val records = MutableStateFlow<List<SampleRecord>>(emptyList())
        var fail = false

        override fun observe() = records

        override suspend fun insertAtomically(records: List<SampleRecord>) {
            if (fail) error("Synthetic failure")
            this.records.value += records
        }
    }

    private fun model(storage: StorageInitializer) =
        FoundationViewModel(storage) { record }
            .also { store.put("foundation", it) }

    @Test fun loadingEmptyInsertionAndFailure() =
        runTest(dispatcher) {
            val repository = FakeRepository()
            val model = model { StorageState.Ready(repository) }
            assertEquals(FoundationState.Loading, model.state.value)
            testScheduler.runCurrent()
            assertEquals(FoundationState.Ready(emptyList()), model.state.value)
            model.addSample()
            model.addSample()
            assertTrue((model.state.value as FoundationState.Ready).saving)
            testScheduler.runCurrent()
            assertEquals(FoundationState.Ready(listOf(record)), model.state.value)
            repository.fail = true
            model.addSample()
            testScheduler.runCurrent()
            assertEquals(FoundationState.Ready(listOf(record), saveFailed = true), model.state.value)
        }

    @Test fun unavailableCanRetry() =
        runTest(dispatcher) {
            var available = false
            val repository = FakeRepository()
            val model = model { if (available) StorageState.Ready(repository) else StorageState.TemporarilyUnavailable }
            testScheduler.runCurrent()
            assertEquals(FoundationState.TemporarilyUnavailable, model.state.value)
            model.addSample()
            assertTrue(repository.records.value.isEmpty())
            available = true
            model.retry()
            testScheduler.runCurrent()
            assertEquals(FoundationState.Ready(emptyList()), model.state.value)
        }

    @Test fun keyFailureIsRecoveryRequired() =
        runTest(dispatcher) {
            val model = model { StorageState.RecoveryRequired }
            testScheduler.runCurrent()
            assertEquals(FoundationState.RecoveryRequired, model.state.value)
        }

    @Test fun observationFailurePropagates() =
        runTest(dispatcher) {
            val repository =
                object : SampleRepository {
                    override fun observe() = flow<List<SampleRecord>> { error("Synthetic failure") }

                    override suspend fun insertAtomically(records: List<SampleRecord>) = Unit
                }
            val model = model { StorageState.Ready(repository) }
            testScheduler.runCurrent()
            assertEquals(FoundationState.RecoveryRequired, model.state.value)
        }
}
