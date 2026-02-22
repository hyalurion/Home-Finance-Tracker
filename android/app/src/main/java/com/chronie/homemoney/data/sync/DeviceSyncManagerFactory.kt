package com.chronie.homemoney.data.sync

import android.content.Context
import android.net.wifi.WifiManager
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.domain.sync.DeviceSyncManager
import com.google.gson.Gson

/**
 * 设备同步管理器工厂类
 * 仅支持局域网同步
 */
class DeviceSyncManagerFactory(
    private val context: Context,
    private val expenseDao: ExpenseDao,
    private val gson: Gson,
    private val wifiManager: WifiManager
) {
    
    /**
     * 创建设备同步管理器
     * 仅支持局域网(LAN)同步
     */
    fun createDeviceSyncManager(): DeviceSyncManager {
        return LanDeviceSyncManager(context, expenseDao, gson, wifiManager)
    }
}
