package com.chronie.homemoney.core.common

import android.util.Log
import com.chronie.homemoney.data.local.dao.ExpenseDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地ID生成器
 * 使用负数ID来避免与服务端ID冲突
 * 服务端使用正数ID，本地使用负数ID
 */
@Singleton
class LocalIdGenerator @Inject constructor(
    private val expenseDao: ExpenseDao
) {
    
    companion object {
        private const val TAG = "LocalIdGenerator"
        private const val START_LOCAL_ID = -1
    }
    
    /**
     * 生成下一个本地ID
     * 使用负数递减，从-1开始
     */
    suspend fun generateNextLocalId(): String {
        return try {
            val maxLocalId = expenseDao.getMaxLocalId()
            val nextId = if (maxLocalId == null) {
                START_LOCAL_ID
            } else {
                maxLocalId - 1
            }
            nextId.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate local ID, using fallback", e)
            START_LOCAL_ID.toString()
        }
    }
    
    /**
     * 判断ID是否为本地ID
     */
    fun isLocalId(id: String): Boolean {
        return try {
            val idValue = id.toIntOrNull()
            idValue != null && idValue < 0
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 判断ID是否为服务端ID
     */
    fun isServerId(id: String): Boolean {
        return try {
            val idValue = id.toIntOrNull()
            idValue != null && idValue >= 0
        } catch (e: Exception) {
            false
        }
    }
}
