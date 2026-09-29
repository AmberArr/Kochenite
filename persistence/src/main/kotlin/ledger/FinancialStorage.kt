package moe.frost.kochenite.persistence.ledger

import android.content.Context
import android.os.UserManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.frost.kochenite.domain.ledger.LedgerResult
import java.io.Closeable

/** Opens financial data without deleting or recreating files after a failure. */
class FinancialStorage internal constructor(
    private val context: Context,
    private val name: String,
    private val unlocked: () -> Boolean,
    private val create: (Context, String) -> LedgerDatabase = ::ledgerDatabase,
) : Closeable {
    constructor(context: Context) : this(context.applicationContext, "bookkeeping.db", {
        context.getSystemService(UserManager::class.java).isUserUnlocked
    })

    private val lock = Any()
    private var db: LedgerDatabase? = null
    private var ledger: RoomLedger? = null

    suspend fun open(): LedgerResult<RoomLedger> =
        withContext(Dispatchers.IO) {
            storageResult {
                synchronized(lock) {
                    if (!unlocked()) throw java.io.IOException("Storage temporarily unavailable")
                    ledger ?: run {
                        val candidate = create(context, name)
                        try {
                            candidate.openHelper.writableDatabase
                                .query("SELECT count(*) FROM accounts")
                                .use { it.moveToFirst() }
                            RoomLedger(candidate).also {
                                db = candidate
                                ledger = it
                            }
                        } catch (failure: Exception) {
                            candidate.close()
                            throw failure
                        }
                    }
                }
            }
        }

    override fun close() =
        synchronized(lock) {
            db?.close()
            db = null
            ledger = null
        }
}
