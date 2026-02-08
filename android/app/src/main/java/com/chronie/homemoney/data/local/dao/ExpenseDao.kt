package com.chronie.homemoney.data.local.dao

import androidx.room.*
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

/**
 * 支出记录数据访问对象
 */
@Dao
interface ExpenseDao {
    
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>
    
    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getExpenseById(id: String): ExpenseEntity?
    
    @Query("SELECT * FROM expenses WHERE server_id = :serverId")
    suspend fun getExpenseByServerId(serverId: String): ExpenseEntity?
    
    @Query("SELECT * FROM expenses WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    fun getExpensesByDateRange(startDate: String, endDate: String): Flow<List<ExpenseEntity>>
    
    @Query("SELECT * FROM expenses WHERE type = :type ORDER BY date DESC")
    fun getExpensesByType(type: String): Flow<List<ExpenseEntity>>
    
    @Query("SELECT * FROM expenses WHERE is_synced = 0")
    suspend fun getUnsyncedExpenses(): List<ExpenseEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpenses(expenses: List<ExpenseEntity>)
    
    @Update
    suspend fun updateExpense(expense: ExpenseEntity)
    
    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)
    
    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteExpenseById(id: String)
    
    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()
    
    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun getExpenseCount(): Int
    
    @Query("SELECT SUM(amount) FROM expenses WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getTotalAmountByDateRange(startDate: String, endDate: String): Double?
    
    @Query("SELECT * FROM expenses WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getExpensesByDateRangeSync(startDate: String, endDate: String): List<ExpenseEntity>
    
    @Query("SELECT * FROM expenses WHERE date = :date AND amount = :amount AND type = :type AND (remark = :remark OR (remark IS NULL AND :remark IS NULL))")
    suspend fun getExpenseByContent(date: String, amount: Double, type: String, remark: String?): ExpenseEntity?
    
    @Query("SELECT MIN(CAST(id AS INTEGER)) FROM expenses WHERE CAST(id AS INTEGER) < 0")
    suspend fun getMinLocalId(): Int?
    
    @Query("SELECT MAX(CAST(id AS INTEGER)) FROM expenses WHERE CAST(id AS INTEGER) < 0")
    suspend fun getMaxLocalId(): Int?
}
