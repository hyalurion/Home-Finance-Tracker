package com.chronie.homemoney.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.chronie.homemoney.data.local.dao.BudgetDao
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.local.dao.MemberDao
import com.chronie.homemoney.data.local.dao.SyncQueueDao
import com.chronie.homemoney.data.local.entity.BudgetEntity
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.local.entity.MemberEntity
import com.chronie.homemoney.data.local.entity.SyncQueueEntity

/**
 * 应用数据库
 * 版本 2: 添加 budgets 表
 * 版本 3: 向 expenses 表添加 date 字段
 * 版本 4: 为 expenses 表的 server_id 字段添加唯一索引
 */
@Database(
    entities = [
        ExpenseEntity::class,
        MemberEntity::class,
        SyncQueueEntity::class,
        BudgetEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun expenseDao(): ExpenseDao
    abstract fun memberDao(): MemberDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun budgetDao(): BudgetDao
    
    companion object {
        const val DATABASE_NAME = "homemoney.db"
    }
}
