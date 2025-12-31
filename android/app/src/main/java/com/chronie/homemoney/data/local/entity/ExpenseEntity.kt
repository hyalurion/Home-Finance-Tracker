package com.chronie.homemoney.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 支出记录实体
 * 对应后端的 Expense 表
 */
@Entity(
    tableName = "expenses",
    indices = [
        Index(value = ["date"]),
        Index(value = ["type"]),
        Index(value = ["is_synced"]),
        Index(value = ["server_id"], unique = true)
    ]
)
data class ExpenseEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "type")
    val type: String,
    
    @ColumnInfo(name = "remark")
    val remark: String?,
    
    @ColumnInfo(name = "amount")
    val amount: Double,
    
    @ColumnInfo(name = "date")
    val date: String,
    
    @ColumnInfo(name = "is_synced")
    val isSynced: Boolean = false,
    
    @ColumnInfo(name = "server_id")
    val serverId: String? = null
)
