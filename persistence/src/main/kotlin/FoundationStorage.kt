package moe.frost.kochenite.persistence

import android.content.Context
import android.database.sqlite.SQLiteFullException
import android.database.sqlite.SQLiteCantOpenDatabaseException
import android.os.UserManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.frost.kochenite.domain.StorageInitializer
import moe.frost.kochenite.domain.StorageState
import java.io.Closeable
import java.io.IOException

class FoundationStorage internal constructor(
    private val context: Context,
    private val name: String,
    private val unlocked: () -> Boolean,
    private val create: (Context, String) -> FoundationDatabase = ::database,
) : StorageInitializer, Closeable {
    constructor(context: Context) : this(
        context.applicationContext,
        "foundation-proof.db",
        { context.getSystemService(UserManager::class.java).isUserUnlocked },
    )

    private val lock = Any()
    private var db: FoundationDatabase? = null
    private var ready: StorageState.Ready? = null

    override suspend fun open(): StorageState = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!unlocked()) return@synchronized StorageState.TemporarilyUnavailable
            ready?.let { return@synchronized it }
            var candidate: FoundationDatabase? = null
            try {
                candidate = create(context, name)
                candidate.openHelper.writableDatabase.query("SELECT count(*) FROM samples").use { it.moveToFirst() }
                db = candidate
                StorageState.Ready(RoomSampleRepository(candidate)).also { ready = it }
            } catch (cancelled: CancellationException) {
                candidate?.close()
                throw cancelled
            } catch (_: IOException) {
                candidate?.close()
                StorageState.TemporarilyUnavailable
            } catch (_: SQLiteFullException) {
                candidate?.close()
                StorageState.TemporarilyUnavailable
            } catch (_: SQLiteCantOpenDatabaseException) {
                candidate?.close()
                StorageState.TemporarilyUnavailable
            } catch (_: Exception) {
                candidate?.close()
                StorageState.RecoveryRequired
            }
        }
    }

    override fun close() = synchronized(lock) {
        db?.close()
        db = null
        ready = null
    }
}
