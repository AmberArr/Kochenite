package moe.frost.kochenite.persistence

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import moe.frost.kochenite.domain.SampleRecord
import moe.frost.kochenite.domain.StorageState
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.util.UUID

private inline fun <R> FoundationDatabase.use(action: (FoundationDatabase) -> R): R =
    try {
        action(this)
    } finally {
        close()
    }

class StorageDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "storage-test-${UUID.randomUUID()}.db"
    private val file = context.getDatabasePath(name)
    private val record = SampleRecord("stable-synthetic-id", 9007199254740993L, "USD", 2, 1700000000000)

    @get:Rule val migration =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            FoundationDatabase::class.java,
            emptyList(),
            PreservingSQLiteFactory(),
        )

    @After fun cleanup() {
        context.deleteDatabase(name)
    }

    private fun storage(unlocked: () -> Boolean = { true }) = FoundationStorage(context, name, unlocked)

    private suspend fun seed() {
        storage().use { opened ->
            (opened.open() as StorageState.Ready).repository.insertAtomically(listOf(record))
        }
    }

    private fun mustFail(action: () -> Unit) {
        try {
            action()
        } catch (_: Exception) {
            return
        }
        fail("Expected failure")
    }

    @Test fun roundTripIntegrityAndRollback() =
        runBlocking {
            seed()
            storage().use { reopened ->
                val repository = (reopened.open() as StorageState.Ready).repository
                assertEquals(listOf(record), repository.observe().first())
                try {
                    repository.insertAtomically(listOf(record.copy(id = "should-rollback"), record))
                    fail("Duplicate key must fail")
                } catch (_: android.database.sqlite.SQLiteConstraintException) {
                }
                assertEquals(listOf(record), repository.observe().first())
            }
            database(context, name).use { db ->
                db.openHelper.writableDatabase.query("PRAGMA integrity_check").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("ok", it.getString(0))
                }
            }
            assertArrayEquals("SQLite format 3\u0000".toByteArray(), file.readBytes().take(16).toByteArray())
        }

    private fun version1Fixture() {
        migration.createDatabase(name, 1).use { db ->
            db.execSQL(
                "INSERT INTO samples VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(record.id, record.amountMinor, record.currency, record.currencyScale, record.occurredAtEpochMillis),
            )
        }
    }

    @Test fun version1MigratesAndReopensTwice() =
        runBlocking {
            version1Fixture()
            migration.runMigrationsAndValidate(name, 2, true, migration1To2).close()
            repeat(2) {
                database(context, name).use { db ->
                    assertEquals(listOf(record), RoomSampleRepository(db).observe().first())
                }
            }
        }

    @Test fun failedMigrationRollsBackWithoutReset() =
        runBlocking {
            version1Fixture()
            val broken =
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE samples ADD COLUMN note TEXT")
                        error("Synthetic migration interruption")
                    }
                }
            mustFail {
                Room
                    .databaseBuilder(context, FoundationDatabase::class.java, name)
                    .openHelperFactory(PreservingSQLiteFactory())
                    .addMigrations(broken)
                    .build()
                    .use { it.openHelper.writableDatabase }
            }
            assertTrue(file.exists())
            database(context, name).use { db ->
                assertEquals(listOf(record), RoomSampleRepository(db).observe().first())
            }
        }

    @Test fun corruptionNeverDeletesOrReplacesDatabase() =
        runBlocking {
            seed()
            val damaged = file.readBytes().also { it[0] = (it[0].toInt() xor 1).toByte() }
            file.writeBytes(damaged)
            repeat(2) {
                storage().use { assertEquals(StorageState.RecoveryRequired, it.open()) }
                assertArrayEquals(damaged, file.readBytes())
            }
        }

    @Test fun lockedStorageDoesNotCreateDatabase() =
        runBlocking {
            storage { false }.use { assertEquals(StorageState.TemporarilyUnavailable, it.open()) }
            assertFalse(file.exists())
        }

    @Test fun temporaryFailureRetainsExistingDataAndRetries() =
        runBlocking {
            seed()
            var unavailable = true
            FoundationStorage(context, name, { true }) { context, name ->
                if (unavailable) throw IOException("Synthetic unavailable storage")
                database(context, name)
            }.use {
                val before = file.readBytes()
                assertEquals(StorageState.TemporarilyUnavailable, it.open())
                assertArrayEquals(before, file.readBytes())
                unavailable = false
                assertEquals(listOf(record), (it.open() as StorageState.Ready).repository.observe().first())
            }
        }

    @Test fun interruptedCreationRollsBackAndRetries() =
        runBlocking {
            mustFail {
                Room
                    .databaseBuilder(context, FoundationDatabase::class.java, name)
                    .openHelperFactory(PreservingSQLiteFactory())
                    .addCallback(
                        object : RoomDatabase.Callback() {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                db.execSQL("INSERT INTO samples VALUES ('interrupted', 1, 'JPY', 0, 0, NULL)")
                                error("Synthetic creation interruption")
                            }
                        },
                    ).build()
                    .use { it.openHelper.writableDatabase }
            }
            storage().use {
                val repository = (it.open() as StorageState.Ready).repository
                assertTrue(repository.observe().first().isEmpty())
                repository.insertAtomically(listOf(record))
            }
            storage().use { assertEquals(listOf(record), (it.open() as StorageState.Ready).repository.observe().first()) }
        }
}
