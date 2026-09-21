package moe.frost.kochenite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.frost.kochenite.domain.SampleRecord
import moe.frost.kochenite.domain.SampleRepository
import moe.frost.kochenite.domain.StorageInitializer
import moe.frost.kochenite.domain.StorageState

sealed interface FoundationState {
    data object Loading : FoundationState
    data class Ready(
        val records: List<SampleRecord>,
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
    ) : FoundationState
    data object TemporarilyUnavailable : FoundationState
    data object RecoveryRequired : FoundationState
}

class FoundationViewModel(
    private val storage: StorageInitializer,
    private val sample: () -> SampleRecord,
) : ViewModel() {
    private val mutableState = MutableStateFlow<FoundationState>(FoundationState.Loading)
    val state = mutableState.asStateFlow()
    private var repository: SampleRepository? = null
    private var observation: Job? = null

    init { retry() }

    fun retry() {
        if ((state.value as? FoundationState.Ready)?.saving == true) return
        observation?.cancel()
        repository = null
        mutableState.value = FoundationState.Loading
        observation = viewModelScope.launch {
            try {
                when (val opened = storage.open()) {
                    StorageState.TemporarilyUnavailable -> mutableState.value = FoundationState.TemporarilyUnavailable
                    StorageState.RecoveryRequired -> mutableState.value = FoundationState.RecoveryRequired
                    is StorageState.Ready -> {
                        repository = opened.repository
                        opened.repository.observe().collect { records ->
                            mutableState.update { previous ->
                                val ready = previous as? FoundationState.Ready
                                FoundationState.Ready(records, ready?.saving ?: false, ready?.saveFailed ?: false)
                            }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                repository = null
                mutableState.value = FoundationState.RecoveryRequired
            }
        }
    }

    fun addSample() {
        val current = state.value as? FoundationState.Ready ?: return
        val destination = repository ?: return
        if (current.saving) return
        mutableState.value = current.copy(saving = true, saveFailed = false)
        viewModelScope.launch {
            var failed = false
            try {
                destination.insertAtomically(listOf(sample()))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            } finally {
                mutableState.update { if (it is FoundationState.Ready) it.copy(saving = false, saveFailed = failed) else it }
            }
        }
    }
}
