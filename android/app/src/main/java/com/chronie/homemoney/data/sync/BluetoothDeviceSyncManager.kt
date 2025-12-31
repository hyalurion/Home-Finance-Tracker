package com.chronie.homemoney.data.sync

import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import android.util.Log
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.domain.sync.DeviceInfo
import com.chronie.homemoney.domain.sync.DeviceSyncData
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

/**
 * 蓝牙设备间同步管理器
 */
class BluetoothDeviceSyncManager(
    expenseDao: ExpenseDao,
    gson: Gson,
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter
) : BaseDeviceSyncManager(expenseDao, gson) {
    
    companion object {
        private val SYNC_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val DEVICE_NAME = "HomeMoney"
    }
    
    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var discoveryReceiver: BluetoothDiscoveryReceiver? = null
    
    /**
     * 检查是否有必要的权限
     */
    private fun hasRequiredPermissions(): Boolean {
        val locationPermission = android.Manifest.permission.ACCESS_FINE_LOCATION
        val bluetoothScanPermission = android.Manifest.permission.BLUETOOTH_SCAN
        val bluetoothConnectPermission = android.Manifest.permission.BLUETOOTH_CONNECT
        
        val hasLocationPermission = context.checkSelfPermission(locationPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasScanPermission = context.checkSelfPermission(bluetoothScanPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasConnectPermission = context.checkSelfPermission(bluetoothConnectPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
        
        return hasLocationPermission && hasScanPermission && hasConnectPermission
    }
    
    override fun searchDevices(): Flow<DeviceInfo> {
        Log.d(TAG, "Searching for Bluetooth devices")
        
        _devices.value = emptyList()
        
        // 检查蓝牙是否可用
        if (!bluetoothAdapter.isEnabled) {
            Log.e(TAG, "Bluetooth is not enabled")
            return emptyFlow()
        }
        
        // 检查权限
        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions for Bluetooth scanning")
            return emptyFlow()
        }
        
        // 检查是否正在扫描
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        
        // 注册广播接收器
        discoveryReceiver = BluetoothDiscoveryReceiver(_devices)
        context.registerReceiver(
            discoveryReceiver,
            BluetoothDiscoveryReceiver.getIntentFilter()
        )
        
        // 开始扫描
        bluetoothAdapter.startDiscovery()
        
        // 将StateFlow<List<DeviceInfo>>转换为Flow<DeviceInfo>
        return _devices.asStateFlow().flatMapLatest { devices ->
            if (devices.isEmpty()) {
                emptyFlow()
            } else {
                flowOf(*devices.toTypedArray())
            }
        }
    }
    
    override suspend fun connect(device: DeviceInfo): Boolean {
        Log.d(TAG, "Connecting to Bluetooth device: ${device.deviceName}")
        
        if (!bluetoothAdapter.isEnabled) {
            return false
        }
        
        // 查找蓝牙设备
        val bluetoothDevice = bluetoothAdapter.bondedDevices.find { it.address == device.address }
            ?: bluetoothAdapter.getRemoteDevice(device.address)
        
        try {
            // 创建RFCOMM套接字
            bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(SYNC_UUID)
            
            // 取消扫描以提高连接速度
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            
            // 连接设备
            bluetoothSocket?.connect()
            
            // 获取输入输出流
            outputStream = bluetoothSocket?.outputStream
            inputStream = bluetoothSocket?.inputStream
            
            isConnected = true
            currentDevice = device
            
            // 注销广播接收器
            if (discoveryReceiver != null) {
                context.unregisterReceiver(discoveryReceiver)
                discoveryReceiver = null
            }
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to Bluetooth device", e)
            
            // 关闭套接字
            try {
                bluetoothSocket?.close()
            } catch (e2: IOException) {
                Log.e(TAG, "Error closing socket", e2)
            }
            
            return false
        }
    }
    
    override suspend fun disconnect(): Boolean {
        Log.d(TAG, "Disconnecting from Bluetooth device")
        
        try {
            bluetoothSocket?.close()
            outputStream?.close()
            inputStream?.close()
            
            bluetoothSocket = null
            outputStream = null
            inputStream = null
            
            // 注销广播接收器
            if (discoveryReceiver != null) {
                context.unregisterReceiver(discoveryReceiver)
                discoveryReceiver = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from Bluetooth device", e)
        }
        
        isConnected = false
        currentDevice = null
        return true
    }
    
    override suspend fun sendData(data: DeviceSyncData): Boolean {
        Log.d(TAG, "Sending data to Bluetooth device")
        
        return try {
            val jsonData = gson.toJson(data)
            val bytes = jsonData.toByteArray()
            outputStream?.write(bytes)
            outputStream?.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send data to Bluetooth device", e)
            false
        }
    }
    
    override suspend fun receiveData(): DeviceSyncData? {
        Log.d(TAG, "Receiving data from Bluetooth device")
        
        return try {
            val buffer = ByteArray(1024)
            val bytesRead = inputStream?.read(buffer)
            
            if (bytesRead != null && bytesRead > 0) {
                val jsonData = String(buffer, 0, bytesRead)
                gson.fromJson(jsonData, DeviceSyncData::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive data from Bluetooth device", e)
            null
        }
    }
    
    /**
     * 蓝牙发现广播接收器
     */
    private class BluetoothDiscoveryReceiver(
        private val devicesFlow: MutableStateFlow<List<DeviceInfo>>
    ) : BroadcastReceiver() {
        
        companion object {
            fun getIntentFilter(): IntentFilter {
                val filter = IntentFilter()
                filter.addAction(BluetoothDevice.ACTION_FOUND)
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                return filter
            }
        }
        
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // 发现设备
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    
                    device?.let {
                        val deviceInfo = DeviceInfo(
                            deviceId = it.address,
                            deviceName = it.name ?: "Unknown Device",
                            deviceType = "ANDROID",
                            connectionType = "BLUETOOTH",
                            address = it.address,
                            signalStrength = calculateSignalStrength(rssi)
                        )
                        
                        devicesFlow.value = devicesFlow.value + deviceInfo
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // 扫描完成
                    Log.d("BluetoothDiscovery", "Discovery finished")
                }
            }
        }
        
        private fun calculateSignalStrength(rssi: Int): Int {
            return when (rssi) {
                in -40..0 -> 100
                in -50..-41 -> 90
                in -60..-51 -> 80
                in -70..-61 -> 70
                in -80..-71 -> 60
                in -90..-81 -> 50
                in -100..-91 -> 40
                else -> 30
            }
        }
    }
}