package com.chronie.homemoney.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.local.dao.SyncQueueDao
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.local.entity.SyncQueueEntity
import com.chronie.homemoney.data.mapper.ExpenseMapper
import com.chronie.homemoney.data.remote.api.ExpenseApi
import com.chronie.homemoney.data.remote.dto.ExpenseDto
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseFilters
import com.chronie.homemoney.domain.model.ExpenseStatistics
import com.chronie.homemoney.domain.model.SortOption
import com.chronie.homemoney.domain.repository.ExpenseRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import java.time.LocalDate

/**
 * 支出记录 Repository 实现
 */
@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val expenseApi: ExpenseApi,
    private val syncQueueDao: SyncQueueDao,
    private val gson: Gson
) : ExpenseRepository {
    
    override fun getExpenses(
        page: Int,
        limit: Int,
        filters: ExpenseFilters
    ): Flow<PagingData<Expense>> {
        return Pager<Int, Expense>(
            config = PagingConfig(
                pageSize = limit,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                // TODO: 实现 PagingSource
                throw NotImplementedError("PagingSource not implemented yet")
            }
        ).flow
    }
    
    override suspend fun getExpensesList(
        page: Int,
        limit: Int,
        filters: ExpenseFilters
    ): Result<List<Expense>> {
        return try {
            val networkTimeoutMillis = 3000L
            
            val expenses = try {
                withTimeout(networkTimeoutMillis) {
                    val response = expenseApi.getExpenses(
                        page = page,
                        limit = limit,
                        keyword = filters.keyword,
                        type = filters.type?.let { getChineseTypeName(it) },
                        month = filters.month,
                        minAmount = filters.minAmount,
                        maxAmount = filters.maxAmount,
                        sort = getSortString(filters.sortBy)
                    )
                    
                    if (response.isSuccessful && response.body() != null) {
                        val apiResponse = response.body()!!
                        var expenses = apiResponse.data.map { ExpenseMapper.toDomain(it) }
                        
                        if (filters.startDate != null) {
                            expenses = expenses.filter { LocalDate.parse(it.date) >= filters.startDate }
                        }
                        if (filters.endDate != null) {
                            expenses = expenses.filter { LocalDate.parse(it.date) <= filters.endDate }
                        }
                        
                        expenses = when (filters.sortBy) {
                            SortOption.DATE_ASC -> expenses.sortedBy { it.date }
                            SortOption.DATE_DESC -> expenses.sortedByDescending { it.date }
                            SortOption.AMOUNT_ASC -> expenses.sortedBy { it.amount }
                            SortOption.AMOUNT_DESC -> expenses.sortedByDescending { it.amount }
                        }
                        
                        if (page == 1) {
                            expenses.forEach { expense ->
                                expenseDao.insertExpense(ExpenseMapper.toEntity(expense))
                            }
                        }
                        
                        android.util.Log.d("ExpenseRepository", "Filtered expenses from server: ${expenses.size} (startDate=${filters.startDate}, endDate=${filters.endDate})")
                        Result.success(expenses)
                    } else {
                        Result.failure(Exception("Server returned error: ${response.code()}"))
                    }
                }
            } catch (timeoutException: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.w("ExpenseRepository", "Network request timeout after ${networkTimeoutMillis}ms, falling back to local data")
                null
            } catch (networkError: Exception) {
                android.util.Log.w("ExpenseRepository", "Network error, falling back to local data", networkError)
                null
            }
            
            if (expenses != null) {
                return expenses
            }
            
            val allExpenses = expenseDao.getAllExpenses().first()
            var filteredExpenses = allExpenses.map { ExpenseMapper.toDomain(it) }
            
            if (filters.keyword != null) {
                filteredExpenses = filteredExpenses.filter { expense ->
                    expense.remark?.contains(filters.keyword, ignoreCase = true) == true ||
                    getChineseTypeName(expense.type).contains(filters.keyword, ignoreCase = true)
                }
            }
            
            if (filters.type != null) {
                filteredExpenses = filteredExpenses.filter { it.type == filters.type }
            }
            
            if (filters.minAmount != null) {
                filteredExpenses = filteredExpenses.filter { it.amount >= filters.minAmount }
            }
            
            if (filters.maxAmount != null) {
                filteredExpenses = filteredExpenses.filter { it.amount <= filters.maxAmount }
            }
            
            if (filters.startDate != null) {
                filteredExpenses = filteredExpenses.filter { 
                    LocalDate.parse(it.date) >= filters.startDate 
                }
            }
            
            if (filters.endDate != null) {
                filteredExpenses = filteredExpenses.filter { 
                    LocalDate.parse(it.date) <= filters.endDate 
                }
            }
            
            filteredExpenses = when (filters.sortBy) {
                SortOption.DATE_ASC -> filteredExpenses.sortedBy { it.date }
                SortOption.DATE_DESC -> filteredExpenses.sortedByDescending { it.date }
                SortOption.AMOUNT_ASC -> filteredExpenses.sortedBy { it.amount }
                SortOption.AMOUNT_DESC -> filteredExpenses.sortedByDescending { it.amount }
            }
            
            val startIndex = (page - 1) * limit
            val endIndex = minOf(startIndex + limit, filteredExpenses.size)
            val localExpenses = if (startIndex < filteredExpenses.size) {
                filteredExpenses.subList(startIndex, endIndex)
            } else {
                emptyList()
            }
            
            Result.success(localExpenses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getExpenseById(id: String): Result<Expense> {
        return try {
            val entity = expenseDao.getExpenseById(id)
            if (entity != null) {
                Result.success(ExpenseMapper.toDomain(entity))
            } else {
                Result.failure(Exception("Expense not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun addExpense(expense: Expense): Result<Expense> {
        return try {
            // 保存到本地数据库
            val entity = ExpenseMapper.toEntity(expense).copy(isSynced = false)
            expenseDao.insertExpense(entity)
            
            // 添加到同步队列
            addToSyncQueue("expense", expense.id, "CREATE", entity)
            
            Result.success(expense)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateExpense(expense: Expense): Result<Expense> {
        return try {
            // 先尝试同步到服务器
            try {
                // 注意：后端API接受Int类型的ID，但我们使用String类型
                val expenseId = expense.id.toIntOrNull()
                if (expenseId != null) {
                    // 调用后端API更新支出记录 - 转换为正确的类型
                    expenseApi.updateExpense(expenseId.toLong(), ExpenseMapper.toDto(expense))
                    // 如果API调用成功，则标记为已同步
                    val entity = ExpenseMapper.toEntity(expense).copy(
                        isSynced = true
                    )
                    expenseDao.updateExpense(entity)
                    return Result.success(expense)
                } else {
                    android.util.Log.w("ExpenseRepository", "Invalid expense ID format for server sync: ${expense.id}")
                }
            } catch (apiError: Exception) {
                // API调用失败，继续本地更新和同步队列处理
                android.util.Log.w("ExpenseRepository", "Failed to update expense on server, will retry later", apiError)
            }
            
            // 本地更新逻辑（服务器同步失败时）
            val entity = ExpenseMapper.toEntity(expense).copy(
                isSynced = false
            )
            expenseDao.updateExpense(entity)
            
            // 添加到同步队列
            addToSyncQueue("expense", expense.id, "UPDATE", entity)
            
            Result.success(expense)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun deleteExpense(id: String): Result<Unit> {
        return try {
            val entity = expenseDao.getExpenseById(id)
            if (entity != null) {
                // 添加到同步队列（在删除之前）
                addToSyncQueue("expense", id, "DELETE", entity)
                
                // 尝试先同步到服务器
                try {
                    // 注意：后端API接受Int类型的ID，但我们使用String类型
                    // 这里尝试转换，如果失败则跳过服务器同步，但仍保留本地删除和同步队列
                    val expenseId = id.toIntOrNull()
                    if (expenseId != null) {
                        expenseApi.deleteExpense(expenseId)
                        // 如果API调用成功，不需要做特殊处理，因为我们即将删除该记录
                    }
                } catch (apiError: Exception) {
                    // API调用失败，不影响本地操作，继续保留在同步队列中
                    android.util.Log.w("ExpenseRepository", "Failed to delete expense on server, will retry later", apiError)
                }
                
                // 从本地数据库删除
                expenseDao.deleteExpenseById(id)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getStatistics(filters: ExpenseFilters): Result<ExpenseStatistics> {
        return try {
            val networkTimeoutMillis = 3000L
            
            try {
                withTimeout(networkTimeoutMillis) {
                    val response = expenseApi.getStatistics(
                        keyword = filters.keyword,
                        type = filters.type?.let { getChineseTypeName(it) },
                        month = filters.month,
                        minAmount = filters.minAmount,
                        maxAmount = filters.maxAmount
                    )
                    
                    if (response.isSuccessful && response.body() != null) {
                        android.util.Log.d("ExpenseRepository", "Server stats available but using local calculation for date filtering")
                    } else {
                        android.util.Log.w("ExpenseRepository", "Server stats returned error: ${response.code()}")
                    }
                }
            } catch (timeoutException: kotlinx.coroutines.TimeoutCancellationException) {
                android.util.Log.w("ExpenseRepository", "Network request timeout after ${networkTimeoutMillis}ms, using local statistics")
            } catch (networkError: Exception) {
                android.util.Log.w("ExpenseRepository", "Network error, using local statistics", networkError)
            }
            
            // 从本地数据库计算统计数据
            val allExpenses = expenseDao.getAllExpenses().first()
            var expenses = allExpenses.map { ExpenseMapper.toDomain(it) }
            
            // 应用筛选条件
            if (filters.keyword != null) {
                expenses = expenses.filter { expense ->
                    expense.remark?.contains(filters.keyword, ignoreCase = true) == true ||
                    getChineseTypeName(expense.type).contains(filters.keyword, ignoreCase = true)
                }
            }
            
            if (filters.type != null) {
                expenses = expenses.filter { it.type == filters.type }
            }
            
            if (filters.minAmount != null) {
                expenses = expenses.filter { it.amount >= filters.minAmount }
            }
            
            if (filters.maxAmount != null) {
                expenses = expenses.filter { it.amount <= filters.maxAmount }
            }
            
            if (filters.startDate != null) {
                expenses = expenses.filter { expense ->
                    try {
                        val expenseDate = java.time.LocalDate.parse(expense.date)
                        expenseDate >= filters.startDate
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            
            if (filters.endDate != null) {
                expenses = expenses.filter { expense ->
                    try {
                        val expenseDate = java.time.LocalDate.parse(expense.date)
                        expenseDate <= filters.endDate
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            
            if (expenses.isEmpty()) {
                return Result.success(
                    ExpenseStatistics(
                        count = 0,
                        totalAmount = 0.0,
                        averageAmount = 0.0,
                        medianAmount = 0.0,
                        minAmount = 0.0,
                        maxAmount = 0.0
                    )
                )
            }
            
            val amounts = expenses.map { it.amount }.sorted()
            val total = amounts.sum()
            val average = total / amounts.size
            val median = if (amounts.size % 2 == 0) {
                (amounts[amounts.size / 2 - 1] + amounts[amounts.size / 2]) / 2
            } else {
                amounts[amounts.size / 2]
            }
            
            Result.success(
                ExpenseStatistics(
                    count = expenses.size,
                    totalAmount = total,
                    averageAmount = average,
                    medianAmount = median,
                    minAmount = amounts.first(),
                    maxAmount = amounts.last()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun syncWithServer(): Result<Unit> {
        // TODO: 实现同步逻辑
        return Result.success(Unit)
    }
    
    private fun getChineseTypeName(type: com.chronie.homemoney.domain.model.ExpenseType): String {
        return when (type) {
            com.chronie.homemoney.domain.model.ExpenseType.DAILY_GOODS -> "日常用品"
            com.chronie.homemoney.domain.model.ExpenseType.LUXURY -> "奢侈品"
            com.chronie.homemoney.domain.model.ExpenseType.COMMUNICATION -> "通讯费用"
            com.chronie.homemoney.domain.model.ExpenseType.FOOD -> "食品"
            com.chronie.homemoney.domain.model.ExpenseType.SNACKS -> "零食糖果"
            com.chronie.homemoney.domain.model.ExpenseType.COLD_DRINKS -> "冷饮"
            com.chronie.homemoney.domain.model.ExpenseType.CONVENIENCE_FOOD -> "方便食品"
            com.chronie.homemoney.domain.model.ExpenseType.TEXTILES -> "纺织品"
            com.chronie.homemoney.domain.model.ExpenseType.BEVERAGES -> "饮品"
            com.chronie.homemoney.domain.model.ExpenseType.CONDIMENTS -> "调味品"
            com.chronie.homemoney.domain.model.ExpenseType.TRANSPORTATION -> "交通出行"
            com.chronie.homemoney.domain.model.ExpenseType.DINING -> "餐饮"
            com.chronie.homemoney.domain.model.ExpenseType.MEDICAL -> "医疗费用"
            com.chronie.homemoney.domain.model.ExpenseType.FRUITS -> "水果"
            com.chronie.homemoney.domain.model.ExpenseType.OTHER -> "其他"
            com.chronie.homemoney.domain.model.ExpenseType.SEAFOOD -> "水产品"
            com.chronie.homemoney.domain.model.ExpenseType.DAIRY -> "乳制品"
            com.chronie.homemoney.domain.model.ExpenseType.GIFTS -> "礼物人情"
            com.chronie.homemoney.domain.model.ExpenseType.TRAVEL -> "旅行度假"
            com.chronie.homemoney.domain.model.ExpenseType.GOVERNMENT -> "政务"
            com.chronie.homemoney.domain.model.ExpenseType.UTILITIES -> "水电煤气"
        }
    }
    
    private fun getSortString(sortOption: SortOption): String {
        return when (sortOption) {
            SortOption.DATE_ASC -> "dateAsc"
            SortOption.DATE_DESC -> "dateDesc"
            SortOption.AMOUNT_ASC -> "amountAsc"
            SortOption.AMOUNT_DESC -> "amountDesc"
        }
    }
    
    private suspend fun addToSyncQueue(
        entityType: String,
        entityId: String,
        operation: String,
        data: Any
    ) {
        // 先删除该实体的旧同步项
        syncQueueDao.deleteSyncItemsByEntity(entityId, entityType)
        
        // 转换为 DTO 格式
        val dto = when (data) {
            is ExpenseEntity -> ExpenseMapper.toDto(ExpenseMapper.toDomain(data))
            else -> data
        }
        
        val jsonData = gson.toJson(dto)
        val syncItem = SyncQueueEntity(
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            data = jsonData
        )
        syncQueueDao.insertSyncItem(syncItem)
    }
}
