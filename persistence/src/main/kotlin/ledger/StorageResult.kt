package moe.frost.kochenite.persistence.ledger

import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteFullException
import kotlinx.coroutines.CancellationException
import moe.frost.kochenite.domain.ledger.InvalidReason
import moe.frost.kochenite.domain.ledger.LedgerResult
import java.io.IOException

/** Used inside a transaction to reject after a write while still rolling everything back. */
internal class Rejected(
    val result: LedgerResult<Nothing>,
) : RuntimeException()

/** Receives storage faults that become [LedgerResult.RecoveryRequired] or [LedgerResult.UnexpectedFailure]. */
object StorageDiagnostics {
    @Volatile var reporter: (Throwable) -> Unit = {}

    internal fun report(failure: Throwable) {
        // Diagnostics must never change a ledger result.
        runCatching { reporter(failure) }
    }
}

internal suspend fun <T> storageResult(block: suspend () -> T): LedgerResult<T> =
    try {
        LedgerResult.Success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (rejected: Rejected) {
        rejected.result
    } catch (_: ArithmeticException) {
        LedgerResult.Invalid(InvalidReason.OVERFLOW)
    } catch (_: IOException) {
        LedgerResult.TemporarilyUnavailable
    } catch (_: SQLiteFullException) {
        LedgerResult.TemporarilyUnavailable
    } catch (_: SQLiteCantOpenDatabaseException) {
        LedgerResult.TemporarilyUnavailable
    } catch (_: SQLiteDatabaseLockedException) {
        LedgerResult.TemporarilyUnavailable
    } catch (_: SQLiteDiskIOException) {
        LedgerResult.TemporarilyUnavailable
    } catch (failure: SQLiteException) {
        if (failure.message?.contains("integer overflow", ignoreCase = true) == true) {
            LedgerResult.Invalid(InvalidReason.OVERFLOW)
        } else {
            StorageDiagnostics.report(failure)
            LedgerResult.RecoveryRequired
        }
    } catch (failure: Exception) {
        android.util.Log.e("KocheniteStorage", "Unexpected storage operation failure: ${failure.javaClass.name}")
        StorageDiagnostics.report(failure)
        LedgerResult.UnexpectedFailure
    }
