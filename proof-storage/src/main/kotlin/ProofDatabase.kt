package moe.frost.kochenite.proof.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Entity(tableName = "samples")
data class ProofRecord(
    @PrimaryKey val id: String,
    val amountMinor: Long,
    val currency: String,
    val currencyScale: Int,
    val occurredAtEpochMillis: Long,
)

@Dao
interface ProofDao {
    @Insert
    suspend fun insert(record: ProofRecord)

    @Query("SELECT * FROM samples ORDER BY id")
    suspend fun all(): List<ProofRecord>
}

@Database(entities = [ProofRecord::class], version = 1, exportSchema = true)
abstract class ProofDatabase : RoomDatabase() {
    abstract fun samples(): ProofDao
}

// Disposable build-system probe only. This public test key must never protect real data.
fun openProofDatabase(context: Context, name: String = "toolchain-probe.db"): ProofDatabase {
    System.loadLibrary("sqlcipher")
    Logger.setTarget(NoopTarget())
    return Room.databaseBuilder(context, ProofDatabase::class.java, name)
        .openHelperFactory(SupportOpenHelperFactory("synthetic-proof-key-only".toByteArray()))
        .build()
}
