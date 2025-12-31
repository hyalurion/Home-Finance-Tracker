package com.chronie.homemoney.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 数据库迁移策略
 * 用于处理数据库版本升级
 */
object DatabaseMigrations {
    
    /**
     * 从版本1到版本2的迁移
     * 添加预算表
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 创建预算表
            database.execSQL("""
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
    
    /**
     * 从版本2到版本3的迁移
     * 向expenses表添加date字段
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 添加date字段到expenses表
            // 使用默认值确保现有记录不会出错
            database.execSQL("ALTER TABLE expenses ADD COLUMN date TEXT NOT NULL DEFAULT CURRENT_DATE")
            
            // 更新现有记录的date字段值，从time字段转换
            // 这里使用SQLite的date函数将时间戳转换为日期字符串
            database.execSQL("UPDATE expenses SET date = date(time / 1000, 'unixepoch')")
        }
    }
    
    /**
     * 从版本3到版本4的迁移
     * 为server_id字段添加唯一索引
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // 为expenses表的server_id字段添加唯一索引
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_expenses_server_id` ON `expenses` (`server_id`) WHERE `server_id` IS NOT NULL")
        }
    }
    
    /**
     * 获取所有迁移策略
     */
    fun getAllMigrations(): Array<Migration> {
        return arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4
        )
    }
}
