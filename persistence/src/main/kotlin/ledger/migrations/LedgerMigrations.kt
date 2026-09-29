package moe.frost.kochenite.persistence.ledger

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val ledgerMigration1To2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE entries ADD COLUMN note TEXT NOT NULL DEFAULT ''")
        }
    }

internal val ledgerMigration2To3 =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS account_groups (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, position INTEGER NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS book_settings (id INTEGER NOT NULL PRIMARY KEY, displayCurrency TEXT NOT NULL, displayScale INTEGER NOT NULL, setupComplete INTEGER NOT NULL)",
            )
            db.execSQL("ALTER TABLE accounts ADD COLUMN groupId TEXT REFERENCES account_groups(id)")
            db.execSQL("ALTER TABLE accounts ADD COLUMN type TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE accounts ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_accounts_groupId ON accounts(groupId)")
        }
    }

internal val ledgerMigration3To4 =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS receipt_lines (entryId TEXT NOT NULL, id TEXT NOT NULL, position INTEGER NOT NULL, description TEXT NOT NULL, merchantMinor INTEGER NOT NULL, PRIMARY KEY(entryId, id), FOREIGN KEY(entryId, id) REFERENCES allocations(entryId, id) ON DELETE CASCADE)",
            )
        }
    }

internal val ledgerMigration4To5 =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS tags (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, archived INTEGER NOT NULL)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_tags_name ON tags(name)")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS entry_tags (entryId TEXT NOT NULL, tagId TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(entryId, tagId), FOREIGN KEY(entryId) REFERENCES entries(id) ON DELETE CASCADE, FOREIGN KEY(tagId) REFERENCES tags(id) ON DELETE NO ACTION)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_entry_tags_tagId ON entry_tags(tagId)")
            db.execSQL("CREATE TABLE IF NOT EXISTS templates (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, payload TEXT NOT NULL)")
        }
    }

internal val ledgerMigration5To6 =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS entry_references (entryId TEXT NOT NULL, relatedId TEXT NOT NULL, position INTEGER NOT NULL, PRIMARY KEY(entryId, relatedId), FOREIGN KEY(entryId) REFERENCES entries(id) ON DELETE CASCADE, FOREIGN KEY(relatedId) REFERENCES entries(id) ON DELETE CASCADE)",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_entry_references_relatedId ON entry_references(relatedId)")
            db.execSQL(
                "INSERT INTO entry_references(entryId, relatedId, position) SELECT id, purchaseId, 0 FROM entries WHERE purchaseId IS NOT NULL",
            )
            db.execSQL("UPDATE entries SET amountMinor = -amountMinor WHERE kind = 'EXPENSE'")
            db.execSQL("UPDATE entries SET kind = 'EXPENSE' WHERE kind = 'REFUND'")
            db.execSQL("UPDATE entries SET purchaseId = NULL")
            // EditorInput/template JSON v1 is upgraded losslessly by the version-aware codec when read.
        }
    }

/** Retire legacy ordinary editor rows; only explicitly saved incomplete records remain. */
internal val ledgerMigration6To7 =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DELETE FROM drafts WHERE incomplete = 0")
        }
    }
