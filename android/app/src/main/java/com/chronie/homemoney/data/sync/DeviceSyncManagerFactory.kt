package com.chronie.homemoney.data.sync

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.domain.sync.DeviceSyncManager
import com.google.gson.Gson

/**
 * 设备同步管理器工厂类
 * 根据连接类型创建对应的设备同步管理器实例
 */
class DeviceSyncManagerFactory(
    private val context: Context,
    private val expenseDao: ExpenseDao,
    private val gson: Gson,
    private val wifiManager: WifiManager,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val nfcAdapter: NfcAdapter?
) {
    
    /**
     * 创建设备同步管理器
     * @param connectionType 连接类型："LAN", "BLUETOOTH", "NFC"
     */
    fun createDeviceSyncManager(connectionType: String): DeviceSyncManager? {
        return when (connectionType.uppercase()) {
            "LAN" -> LanDeviceSyncManager(expenseDao, gson, wifiManager)
            "BLUETOOTH" -> {
                if (bluetoothAdapter != null) {
                    BluetoothDeviceSyncManager(expenseDao, gson, context, bluetoothAdapter)
                } else {
                    null
                }
            }
            "NFC" -> {
                if (nfcAdapter != null) {
                    NfcDeviceSyncManager(expenseDao, gson, context, nfcAdapter)
                } else {
                    null
                }
            }
            else -> null
        }
    }
}