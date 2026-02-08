package com.chronie.homemoney.data.sync

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.util.Log
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.local.dao.SyncQueueDao
import com.chronie.homemoney.data.local.entity.SyncQueueEntity
import com.chronie.homemoney.data.mapper.ExpenseMapper
import com.chronie.homemoney.data.remote.api.ExpenseApi
import com.chronie.homemoney.data.remote.dto.ExpenseDto
import com.chronie.homemoney.domain.model.*
import com.chronie.homemoney.domain.sync.DeviceSyncManager
import com.chronie.homemoney.domain.sync.SyncManager
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据同步管理器实现
 */
@Singleton
class SyncManagerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val expenseDao: ExpenseDao,
    private val syncQueueDao: SyncQueueDao,
    private val expenseApi: ExpenseApi,
    private val gson: Gson,
    private val deviceSyncManagerFactory: DeviceSyncManagerFactory
) : SyncManager {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "sync_prefs",
        Context.MODE_PRIVATE
    )
    
    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    
    companion object {
        private const val TAG = "SyncManager"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
        private const val MAX_RETRY_COUNT = 3
    }
    
    override suspend fun performFullSync(): Result<SyncResult> {
        return try {
            _syncStatus.value = SyncStatus.SYNCING
            Log.d(TAG, "Starting full sync")
            
            // 1. 上传本地更改
            val uploadResult = uploadLocalChanges().getOrThrow()
            
            // 2. 下载服务器更新
            val downloadResult = downloadServerUpdates().getOrThrow()
            
            // 3. 更新最后同步时间
            setLastSyncTime(System.currentTimeMillis())
            
            val syncResult = SyncResult(
                success = true,
                uploadResult = uploadResult,
                downloadResult = downloadResult,
                conflicts = downloadResult.conflicts
            )
            
            _syncStatus.value = if (downloadResult.conflicts.isNotEmpty()) {
                SyncStatus.CONFLICT
            } else {
                SyncStatus.SUCCESS
            }
            
            Log.d(TAG, "Full sync completed successfully")
            Result.success(syncResult)
        } catch (e: Exception) {
            Log.e(TAG, "Full sync failed", e)
            _syncStatus.value = SyncStatus.FAILED
            Result.failure(e)
        }
    }
    
    override suspend fun uploadLocalChanges(): Result<UploadResult> {
        return try {
            Log.d(TAG, "Starting upload of local changes")
            
            // 获取待同步的项
            val syncItems = syncQueueDao.getNextSyncItems(100)
            if (syncItems.isEmpty()) {
                Log.d(TAG, "No items to upload")
                return Result.success(
                    UploadResult(
                        totalItems = 0,
                        successCount = 0,
                        failedCount = 0
                    )
                )
            }
            
            var successCount = 0
            var failedCount = 0
            val failedItems = mutableListOf<FailedSyncItem>()
            
            for (item in syncItems) {
                try {
                    when (item.entityType) {
                        "expense" -> uploadExpenseItem(item)
                        // 可以添加其他实体类型的处理
                        else -> {
                            Log.w(TAG, "Unknown entity type: ${item.entityType}")
                            continue
                        }
                    }
                    
                    // 上传成功，从队列中删除
                    syncQueueDao.deleteSyncItemById(item.id)
                    successCount++
                    Log.d(TAG, "Successfully uploaded ${item.entityType} ${item.entityId}")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to upload ${item.entityType} ${item.entityId}", e)
                    failedCount++
                    failedItems.add(
                        FailedSyncItem(
                            entityType = item.entityType,
                            entityId = item.entityId,
                            operation = item.operation,
                            error = e.message ?: "Unknown error"
                        )
                    )
                    
                    // 增加重试次数
                    if (item.retryCount < MAX_RETRY_COUNT) {
                        syncQueueDao.updateSyncItem(
                            item.copy(retryCount = item.retryCount + 1)
                        )
                    } else {
                        // 超过最大重试次数，从队列中删除
                        Log.w(TAG, "Max retry count reached for ${item.entityType} ${item.entityId}")
                        syncQueueDao.deleteSyncItemById(item.id)
                    }
                }
            }
            
            Log.d(TAG, "Upload completed: $successCount success, $failedCount failed")
            Result.success(
                UploadResult(
                    totalItems = syncItems.size,
                    successCount = successCount,
                    failedCount = failedCount,
                    failedItems = failedItems
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            Result.failure(e)
        }
    }
    
    private suspend fun uploadExpenseItem(item: SyncQueueEntity) {
        when (item.operation) {
            "CREATE", "UPDATE" -> {
                val expenseDto = gson.fromJson(item.data, ExpenseDto::class.java)
                
                // 对于本地创建的记录（负数ID），上传时使用null作为ID，让服务器生成新ID
                val uploadDto = if (item.operation == "CREATE") {
                    expenseDto.copy(id = null)
                } else {
                    // UPDATE操作时，如果ID是负数（本地ID），说明还未同步，改为CREATE操作
                    if (expenseDto.id != null && expenseDto.id < 0) {
                        expenseDto.copy(id = null)
                    } else {
                        expenseDto
                    }
                }
                
                val response = if (item.operation == "CREATE" || (item.operation == "UPDATE" && uploadDto.id == null)) {
                    expenseApi.createExpense(uploadDto)
                } else {
                    val expenseId = uploadDto.id ?: throw Exception("Expense ID is null for UPDATE operation")
                    expenseApi.updateExpense(expenseId, uploadDto)
                }
                
                if (!response.isSuccessful) {
                    throw Exception("Server returned error: ${response.code()}")
                }
                
                // 更新本地实体的 serverId 和 isSynced 标志
                val entity = expenseDao.getExpenseById(item.entityId)
                if (entity != null) {
                    val serverExpense = response.body()?.data
                    // 保留本地ID，更新serverId和isSynced标志
                    expenseDao.updateExpense(
                        entity.copy(
                            isSynced = true,
                            serverId = serverExpense?.id.toString()
                        )
                    )
                }
            }
            "DELETE" -> {
                val entity = expenseDao.getExpenseById(item.entityId)
                if (entity?.serverId != null) {
                    val serverIdInt = entity.serverId?.toIntOrNull()
                    if (serverIdInt != null) {
                        val response = expenseApi.deleteExpense(serverIdInt)
                        if (!response.isSuccessful) {
                            throw Exception("Server returned error: ${response.code()}")
                        }
                    }
                }
                // 删除本地记录
                expenseDao.deleteExpenseById(item.entityId)
            }
        }
    }
    
    override suspend fun downloadServerUpdates(): Result<DownloadResult> {
        return try {
            Log.d(TAG, "Starting download of server updates")
            
            val lastSyncTime = getLastSyncTime() ?: 0L
            var newItems = 0
            var updatedItems = 0
            var deletedItems = 0
            val conflicts = mutableListOf<SyncConflict>()
            
            // 收集服务器上所有的 serverId
            val serverIds = mutableSetOf<String>()
            
            // 分页获取服务器上的所有支出记录
            var currentPage = 1
            val pageSize = 100
            var totalItems = 0
            var hasMorePages = true
            
            while (hasMorePages) {
                Log.d(TAG, "Fetching page $currentPage with limit $pageSize")
                
                val response = expenseApi.getExpenses(
                    page = currentPage,
                    limit = pageSize
                )
                
                if (!response.isSuccessful) {
                    throw Exception("Server returned error: ${response.code()}")
                }
                
                val apiResponse = response.body()
                val serverExpenses = apiResponse?.data ?: emptyList()
                val total = apiResponse?.total ?: 0
                
                if (currentPage == 1) {
                    totalItems = total
                    Log.d(TAG, "Total items on server: $totalItems")
                }
                
                if (serverExpenses.isEmpty()) {
                    hasMorePages = false
                    break
                }
                
                Log.d(TAG, "Processing ${serverExpenses.size} expenses from page $currentPage")
                
                for (serverExpense in serverExpenses) {
                    try {
                        val serverId = serverExpense.id?.toString() ?: continue
                        serverIds.add(serverId)
                        
                        // 从服务器下载的记录使用服务器的ID（正数）
                        val serverExpenseEntity = ExpenseMapper.toEntity(ExpenseMapper.toDomain(serverExpense)).copy(
                            id = serverId,
                            serverId = serverId,
                            isSynced = true
                        )
                        
                        // 查找本地是否存在该记录（通过 serverId）
                        val localExpenseByServerId = expenseDao.getExpenseByServerId(serverId)
                        
                        // 查找本地是否存在重复记录（通过内容：日期、金额、类型、备注）
                        val duplicateExpense = expenseDao.getExpenseByContent(
                            date = serverExpenseEntity.date,
                            amount = serverExpenseEntity.amount,
                            type = serverExpenseEntity.type,
                            remark = serverExpenseEntity.remark
                        )
                        
                        if (localExpenseByServerId != null) {
                            // 已存在记录（通过 serverId 匹配），使用 replace 策略更新
                            val entityToUpdate = serverExpenseEntity.copy(id = localExpenseByServerId.id)
                            expenseDao.insertExpense(entityToUpdate)
                            updatedItems++
                            Log.d(TAG, "Updated expense: $serverId")
                        } else if (duplicateExpense != null) {
                            // 发现重复记录（通过内容匹配），删除本地记录并使用服务器记录
                            Log.d(TAG, "Found duplicate expense, replacing with server record: $serverId (local id: ${duplicateExpense.id})")
                            expenseDao.deleteExpenseById(duplicateExpense.id)
                            expenseDao.insertExpense(serverExpenseEntity)
                            updatedItems++
                        } else {
                            // 新记录，直接插入
                            expenseDao.insertExpense(serverExpenseEntity)
                            newItems++
                            Log.d(TAG, "Downloaded new expense: $serverId")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to process server expense", e)
                    }
                }
                
                // 检查是否还有更多页
                val processedCount = currentPage * pageSize
                hasMorePages = processedCount < totalItems && serverExpenses.size == pageSize
                currentPage++
            }
            
            // 清理本地存在但服务器上不存在的记录
            try {
                val allLocalExpenses = expenseDao.getAllExpenses().first()
                for (localExpense in allLocalExpenses) {
                    val serverId = localExpense.serverId
                    
                    if (serverId != null) {
                        if (!serverIds.contains(serverId)) {
                            // 有serverId但不在服务器上，删除
                            Log.d(TAG, "Deleting local expense not found on server: $serverId")
                            expenseDao.deleteExpenseById(localExpense.id)
                            deletedItems++
                        }
                    } else {
                        // 没有serverId的情况
                        if (localExpense.isSynced) {
                            // 已同步但没有serverId，可能是重复记录，删除
                            Log.d(TAG, "Deleting synced expense without serverId: ${localExpense.id}")
                            expenseDao.deleteExpenseById(localExpense.id)
                            deletedItems++
                        }
                        // 未同步且没有serverId的记录保留（本地新创建的记录）
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clean up local expenses", e)
            }
            
            Log.d(TAG, "Download completed: $newItems new, $updatedItems updated, $deletedItems deleted, ${conflicts.size} conflicts")
            Result.success(
                DownloadResult(
                    totalItems = totalItems,
                    newItems = newItems,
                    updatedItems = updatedItems,
                    conflicts = conflicts
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Result.failure(e)
        }
    }
    
    override suspend fun resolveConflicts(conflicts: List<SyncConflict>): Result<Unit> {
        return try {
            for (conflict in conflicts) {
                when (conflict.resolution) {
                    ConflictResolution.USE_LOCAL -> {
                        // 本地版本已经在数据库中，只需添加到同步队列
                        val entity = when (conflict.entityType) {
                            "expense" -> expenseDao.getExpenseById(conflict.entityId)
                            else -> null
                        }
                        if (entity != null) {
                            addToSyncQueue(conflict.entityType, conflict.entityId, "UPDATE", entity)
                        }
                    }
                    ConflictResolution.USE_SERVER -> {
                        // 服务器版本已经在下载时更新到数据库
                        // 不需要额外操作
                    }
                    ConflictResolution.MERGE -> {
                        // 合并逻辑（如果需要）
                        Log.w(TAG, "Merge resolution not implemented")
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve conflicts", e)
            Result.failure(e)
        }
    }
    
    override fun getLastSyncTime(): Long? {
        val time = prefs.getLong(KEY_LAST_SYNC_TIME, -1L)
        return if (time == -1L) null else time
    }
    
    override suspend fun setLastSyncTime(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, timestamp).apply()
    }
    
    override suspend fun getPendingSyncCount(): Int {
        return syncQueueDao.getSyncQueueCount()
    }
    
    override fun observeSyncStatus(): Flow<SyncStatus> {
        return _syncStatus.asStateFlow()
    }
    
    private suspend fun addToSyncQueue(
        entityType: String,
        entityId: String,
        operation: String,
        data: Any
    ) {
        val jsonData = gson.toJson(data)
        val syncItem = SyncQueueEntity(
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            data = jsonData
        )
        syncQueueDao.insertSyncItem(syncItem)
    }
    
    private fun parseTimestamp(timestamp: String?): Long {
        if (timestamp == null) return 0L
        return try {
            // 假设时间戳格式为 ISO 8601
            java.time.Instant.parse(timestamp).toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse timestamp: $timestamp", e)
            0L
        }
    }
    
    override fun getDeviceSyncManager(connectionType: String): com.chronie.homemoney.domain.sync.DeviceSyncManager {
        return deviceSyncManagerFactory.createDeviceSyncManager(connectionType)
            ?: throw IllegalStateException("No device sync manager available for $connectionType connection")
    }
}
