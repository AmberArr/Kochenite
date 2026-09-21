package moe.frost.kochenite.persistence

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.map
import moe.frost.kochenite.domain.SampleRecord
import moe.frost.kochenite.domain.SampleRepository

@Entity(tableName = "samples")
internal data class SampleEntity(
    @PrimaryKey val id: String,
    val amountMinor: Long,
    val currency: String,
    val currencyScale: Int,
    val occurredAtEpochMillis: Long,
    val note: String?,
) {
    fun record() = SampleRecord(id, amountMinor, currency, currencyScale, occurredAtEpochMillis, note)
}

@Dao
internal interface SampleDao {
    @Query("SELECT * FROM samples ORDER BY occurredAtEpochMillis DESC, id")
    fun observe(): kotlinx.coroutines.flow.Flow<List<SampleEntity>>

    @Insert suspend fun insert(record: SampleEntity)
}

@Database(entities = [SampleEntity::class], version = 2, exportSchema = true)
internal abstract class FoundationDatabase : RoomDatabase() {
    abstract fun samples(): SampleDao
}

internal val migration1To2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE samples ADD COLUMN note TEXT")
        }
    }

internal fun database(
    context: Context,
    name: String,
): FoundationDatabase =
    Room
        .databaseBuilder(context, FoundationDatabase::class.java, name)
        .openHelperFactory(PreservingSQLiteFactory())
        .addMigrations(migration1To2)
        .build()

internal class RoomSampleRepository(
    private val db: FoundationDatabase,
) : SampleRepository {
    override fun observe() = db.samples().observe().map { rows -> rows.map { it.record() } }

    override suspend fun insertAtomically(records: List<SampleRecord>) {
        db.withTransaction {
            records.forEach { record ->
                db.samples().insert(
                    SampleEntity(
                        record.id,
                        record.amountMinor,
                        record.currency,
                        record.currencyScale,
                        record.occurredAtEpochMillis,
                        record.note,
                    ),
                )
            }
        }
    }
}
