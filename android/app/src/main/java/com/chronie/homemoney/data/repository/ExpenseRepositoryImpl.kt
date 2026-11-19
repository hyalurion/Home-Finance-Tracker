package com.chronie.homemoney.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.mapper.ExpenseMapper
import com.chronie.homemoney.data.remote.api.ExpenseApi
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseFilters
import com.chronie.homemoney.domain.model.ExpenseStatistics
import com.chronie.homemoney.domain.model.SortOption
import com.chronie.homemoney.domain.repository.ExpenseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Implementation of [ExpenseRepository] with a network-first, local-fallback strategy.
 *
 * For list queries:
 * 1. Attempts a network request with a 3-second timeout.
 * 2. On success, caches results locally and returns them.
 * 3. On timeout/error, falls back to the local Room database.
 *
 * Write operations (add, update, delete) always go to the local database first
 * and are marked as unsynced, waiting for the sync engine to push them.
 *
 * Deletion uses soft-delete (sets [deletedAt]) rather than hard-delete to
 * support reliable sync conflict resolution.
 */
@Singleton
class ExpenseRepositoryImpl @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val expenseApi: ExpenseApi
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
                throw NotImplementedError("PagingSource not implemented yet")
            }
        ).flow
    }
    
    /**
     * Network-first expense list query with local fallback.
     *
     * Strategy:
     * 1. Attempt network fetch with 3s timeout.
     * 2. If network succeeds: cache results locally, return network data.
     * 3. If network fails/times out: load from local DB, apply filters + sort
     *    in memory, and paginate manually.
     */
    override suspend fun getExpensesList(
        page: Int,
        limit: Int,
        filters: ExpenseFilters
    ): Result<List<Expense>> {
        return try {
            val networkTimeoutMillis = 3000L
            
            val expenses = try {
                withTimeout(networkTimeoutMillis.milliseconds) {
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
                        
                        expenses.forEach { expense ->
                            expenseDao.upsertExpense(ExpenseMapper.toEntity(expense.copy(isSynced = true)))
                        }
                        
                        Result.success(expenses)
                    } else {
                        Result.failure(Exception("Server returned error: ${response.code()}"))
                    }
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                null
            } catch (_: Exception) {
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
    
    /**
     * Adds a new expense to the local database.
     * Generates a UUID if no ID is provided, increments version,
     * and marks the record as unsynced so the sync engine can push it.
     */
    override suspend fun addExpense(expense: Expense): Result<Expense> {
        return try {
            val id = expense.id.ifEmpty { UUID.randomUUID().toString() }
            val now = System.currentTimeMillis()
            
            val newExpense = expense.copy(
                id = id,
                version = 1,
                updatedAt = now,
                isSynced = false
            )
            
            val entity = ExpenseMapper.toEntity(newExpense)
            expenseDao.insertExpense(entity)
            
            Result.success(newExpense)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateExpense(expense: Expense): Result<Expense> {
        return try {
            val existing = expenseDao.getExpenseById(expense.id)
            val newVersion = (existing?.version ?: 0) + 1
            val now = System.currentTimeMillis()
            
            val updatedExpense = expense.copy(
                version = newVersion,
                updatedAt = now,
                isSynced = false
            )
            
            val entity = ExpenseMapper.toEntity(updatedExpense)
            expenseDao.updateExpense(entity)
            
            Result.success(updatedExpense)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Soft-deletes an expense by setting [deletedAt] and bumping the version.
     * The record remains in the database for sync propagation; UI queries
     * filter it out via the `deleted_at IS NULL` SQL condition.
     */
    override suspend fun deleteExpense(id: String): Result<Unit> {
        return try {
            val entity = expenseDao.getExpenseById(id)
            if (entity != null) {
                val now = System.currentTimeMillis()
                val deletedEntity = entity.copy(
                    deletedAt = now,
                    updatedAt = now,
                    version = entity.version + 1,
                    isSynced = false
                )
                expenseDao.updateExpense(deletedEntity)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exposes soft-deleted expenses for the Recycle Bin screen.
     * The DAO query already filters to `deleted_at IS NOT NULL`.
     */
    override fun getDeletedExpenses(): Flow<List<Expense>> {
        return expenseDao.getDeletedExpenses().map { entities ->
            entities.map { ExpenseMapper.toDomain(it) }
        }
    }

    /**
     * Restores a soft-deleted expense by clearing its tombstone.
     * Marked unsynced so the restore reaches the server on the next sync.
     */
    override suspend fun restoreExpense(id: String): Result<Unit> {
        return try {
            expenseDao.restoreExpense(id, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Permanently removes a single soft-deleted expense from the recycle bin.
     *
     * The server is asked to hard-delete too (best-effort — network/HTTP errors
     * are swallowed) so a synced tombstone does not reappear on the next pull.
     * The local row is always removed regardless of the server outcome.
     */
    override suspend fun permanentDeleteExpense(id: String): Result<Unit> {
        return try {
            try {
                expenseApi.hardDeleteExpense(id)
            } catch (_: Exception) {
                // Offline or server unreachable — keep the local deletion; it will
                // reconcile on a later sync attempt.
            }
            expenseDao.permanentDeleteExpense(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreExpenses(ids: List<String>): Result<Unit> {
        return try {
            expenseDao.restoreExpenses(ids, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun permanentDeleteExpenses(ids: List<String>): Result<Unit> {
        return try {
            ids.forEach { id ->
                try {
                    expenseApi.hardDeleteExpense(id)
                } catch (_: Exception) {
                    // Best-effort server deletion
                }
            }
            expenseDao.permanentDeleteExpenses(ids)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreAllExpenses(): Result<Unit> {
        return try {
            expenseDao.restoreAllExpenses(System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun permanentDeleteAllExpenses(): Result<Unit> {
        return try {
            expenseDao.permanentDeleteAllExpenses()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Computes aggregate statistics from local expenses matching the given filters.
     * Calculates count, total, average, median, min, and max amounts.
     */
    override suspend fun getStatistics(filters: ExpenseFilters): Result<ExpenseStatistics> {
        return try {
            val allExpenses = expenseDao.getAllExpenses().first()
            var expenses = allExpenses.map { ExpenseMapper.toDomain(it) }
            
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
                        val expenseDate = LocalDate.parse(expense.date)
                        expenseDate >= filters.startDate
                    } catch (_: Exception) {
                        false
                    }
                }
            }
            
            if (filters.endDate != null) {
                expenses = expenses.filter { expense ->
                    try {
                        val expenseDate = LocalDate.parse(expense.date)
                        expenseDate <= filters.endDate
                    } catch (_: Exception) {
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
        return Result.success(Unit)
    }
    
    /** Canonical storage name owned by the enum, shared with ExpenseMapper. */
    private fun getChineseTypeName(type: com.chronie.homemoney.domain.model.ExpenseType): String =
        type.chineseName
    
    private fun getSortString(sortOption: SortOption): String {
        return when (sortOption) {
            SortOption.DATE_ASC -> "dateAsc"
            SortOption.DATE_DESC -> "dateDesc"
            SortOption.AMOUNT_ASC -> "amountAsc"
            SortOption.AMOUNT_DESC -> "amountDesc"
        }
    }
}
