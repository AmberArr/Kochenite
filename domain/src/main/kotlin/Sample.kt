package moe.frost.kochenite.domain

import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

data class SampleRecord(
    val id: String,
    val amountMinor: Long,
    val currency: String,
    val currencyScale: Int,
    val occurredAtEpochMillis: Long,
    val note: String? = null,
) {
    init {
        require(id.isNotBlank() && id.length <= 128)
        require(amountMinor > 0)
        require(currency.matches(Regex("[A-Z]{3}")))
        require(currencyScale in 0..3)
        require(occurredAtEpochMillis >= 0)
        require(note == null || note.length <= 500)
    }
}

fun minorUnits(value: String, scale: Int): Long {
    require(scale in 0..3)
    return BigDecimal(value).movePointRight(scale).longValueExact()
}

interface SampleRepository {
    fun observe(): Flow<List<SampleRecord>>
    suspend fun insertAtomically(records: List<SampleRecord>)
}

sealed interface StorageState {
    data class Ready(val repository: SampleRepository) : StorageState
    data object TemporarilyUnavailable : StorageState
    data object RecoveryRequired : StorageState
}

fun interface StorageInitializer {
    suspend fun open(): StorageState
}
