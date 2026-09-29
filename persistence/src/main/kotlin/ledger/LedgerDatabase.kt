package moe.frost.kochenite.persistence.ledger

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import moe.frost.kochenite.persistence.PreservingSQLiteFactory

@Database(
    entities = [
        AccountGroupRow::class, BookSettingsRow::class, AccountRow::class, CategoryRow::class,
        EntryRow::class, PostingRow::class, AllocationRow::class, ReceiptLineRow::class,
        TagRow::class, EntryTagRow::class, TemplateRow::class, DraftRow::class, EntryReferenceRow::class,
    ],
    version = 7,
    exportSchema = true,
)
internal abstract class LedgerDatabase : RoomDatabase() {
    abstract fun ledger(): LedgerDao
}

internal fun ledgerDatabase(
    context: Context,
    name: String,
): LedgerDatabase =
    Room
        .databaseBuilder(context, LedgerDatabase::class.java, name)
        .openHelperFactory(PreservingSQLiteFactory())
        .addMigrations(
            ledgerMigration1To2,
            ledgerMigration2To3,
            ledgerMigration3To4,
            ledgerMigration4To5,
            ledgerMigration5To6,
            ledgerMigration6To7,
        ).build()
