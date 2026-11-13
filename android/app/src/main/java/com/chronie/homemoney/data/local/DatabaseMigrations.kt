package com.chronie.homemoney.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Defines all Room database migration steps from version 1 to version 6.
 *
 * Each migration handles schema changes incrementally:
 * - 1→2: Creates the budgets table
 * - 2→3: Adds a date column to expenses and populates it from the timestamp
 * - 3→4: Creates a unique index on server_id for server-side deduplication
 * - 4→5: Adds version, updated_at, and deleted_at columns for soft-delete sync
 * - 5→6: Rebuilds the expenses table with id as TEXT PRIMARY KEY, dropping server_id
 * - 6→7: Creates local AI chat conversation/message tables
 *
 * Use [getAllMigrations] to retrieve the full migration array for Room's database builder.
 */
object DatabaseMigrations {
    
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS budgets (
                    id INTEGER PRIMARY KEY NOT NULL,
                    monthly_limit REAL NOT NULL,
                    warning_threshold REAL NOT NULL DEFAULT 0.8,
                    is_enabled INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }
    
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN date TEXT NOT NULL DEFAULT CURRENT_DATE")
            db.execSQL("UPDATE expenses SET date = date(time / 1000, 'unixepoch')")
        }
    }
    
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_expenses_server_id` ON `expenses` (`server_id`) WHERE `server_id` IS NOT NULL")
        }
    }
    
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE expenses ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE expenses ADD COLUMN updated_at INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
            db.execSQL("ALTER TABLE expenses ADD COLUMN deleted_at INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_updated_at ON expenses(updated_at)")
            db.execSQL("DROP INDEX IF EXISTS index_expenses_server_id")
        }
    }
    
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP INDEX IF EXISTS index_expenses_date")
            db.execSQL("DROP INDEX IF EXISTS index_expenses_is_synced")
            db.execSQL("DROP INDEX IF EXISTS index_expenses_type")
            db.execSQL("DROP INDEX IF EXISTS index_expenses_updated_at")
            
            db.execSQL("""
                CREATE TABLE expenses_new (
                    id TEXT NOT NULL PRIMARY KEY,
                    type TEXT NOT NULL,
                    remark TEXT,
                    amount REAL NOT NULL,
                    date TEXT NOT NULL,
                    version INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    deleted_at INTEGER,
                    is_synced INTEGER NOT NULL
                )
            """)
            
            db.execSQL("""
                INSERT INTO expenses_new (id, type, remark, amount, date, version, updated_at, deleted_at, is_synced)
                SELECT id, type, remark, amount, date, 
                       COALESCE(version, 1), 
                       COALESCE(updated_at, ${System.currentTimeMillis()}), 
                       deleted_at, 
                       is_synced
                FROM expenses
            """)
            
            db.execSQL("DROP TABLE expenses")
            db.execSQL("ALTER TABLE expenses_new RENAME TO expenses")
            
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_date ON expenses(date)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_is_synced ON expenses(is_synced)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_type ON expenses(type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_expenses_updated_at ON expenses(updated_at)")
        }
    }
    
    /**
     * Adds the two AI chat tables. Both are local-only: chat history contains
     * personal financial context and is intentionally excluded from sync.
     * Column names must stay in sync with the @ColumnInfo declarations.
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chat_conversations (
                    id TEXT NOT NULL PRIMARY KEY,
                    title TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    provider_id TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id TEXT NOT NULL PRIMARY KEY,
                    conversation_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    attachments_json TEXT NOT NULL,
                    tool_calls_json TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    provider_label TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_chat_messages_conversation_id " +
                    "ON chat_messages(conversation_id)"
            )
        }
    }

    fun getAllMigrations(): Array<Migration> {
        return arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7
        )
    }
}
