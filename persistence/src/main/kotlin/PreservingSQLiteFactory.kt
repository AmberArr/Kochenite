package moe.frost.kochenite.persistence

import android.database.sqlite.SQLiteDatabaseCorruptException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

// The default SQLite corruption callback deletes files. Preserve them for recovery.
internal class PreservingSQLiteFactory : SupportSQLiteOpenHelper.Factory {
    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val delegate = configuration.callback
        val callback =
            object : SupportSQLiteOpenHelper.Callback(delegate.version) {
                override fun onCreate(db: SupportSQLiteDatabase) = delegate.onCreate(db)

                override fun onUpgrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) {
                    delegate.onUpgrade(db, oldVersion, newVersion)
                }

                override fun onDowngrade(
                    db: SupportSQLiteDatabase,
                    oldVersion: Int,
                    newVersion: Int,
                ) {
                    delegate.onDowngrade(db, oldVersion, newVersion)
                }

                override fun onConfigure(db: SupportSQLiteDatabase) {
                    delegate.onConfigure(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    delegate.onOpen(db)
                }

                override fun onCorruption(db: SupportSQLiteDatabase): Unit =
                    throw SQLiteDatabaseCorruptException("Storage requires recovery")
            }
        return FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration
                .builder(configuration.context)
                .name(configuration.name)
                .callback(callback)
                .noBackupDirectory(configuration.useNoBackupDirectory)
                .allowDataLossOnRecovery(false)
                .build(),
        )
    }
}
