package moe.frost.kochenite.proof.test

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import moe.frost.kochenite.proof.storage.ProofRecord
import moe.frost.kochenite.proof.storage.openProofDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.UUID
import java.io.DataInputStream

class EncryptedStorageDeviceTest {
    @Test
    fun encryptedRecordsSurviveReopen() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "isolated-probe-${UUID.randomUUID()}.db"
        val record = ProofRecord("synthetic-stable-id", 9007199254740993L, "USD", 2, 1700000000000L)
        try {
            val first = openProofDatabase(context, name)
            try {
                first.samples().insert(record)
                assertEquals(listOf(record), first.samples().all())
            } finally {
                first.close()
            }
            val second = openProofDatabase(context, name)
            try {
                assertEquals(listOf(record), second.samples().all())
                second.openHelper.readableDatabase.query("PRAGMA cipher_integrity_check").use {
                    assertFalse("Encrypted integrity check must return no errors", it.moveToFirst())
                }
            } finally {
                second.close()
            }
            val header = ByteArray(16)
            DataInputStream(context.getDatabasePath(name).inputStream()).use { it.readFully(header) }
            assertFalse(header.contentEquals("SQLite format 3\u0000".toByteArray()))
        } finally {
            context.deleteDatabase(name)
        }
    }
}
