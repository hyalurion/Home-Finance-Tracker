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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        private const val CONNECTION_TIMEOUT = 10000 // 10秒连接超时
        private const val CONNECTION_RETRIES = 3 // 连接重试次数
    }
    
    private val _devices = MutableStateFlow<List<DeviceInfo>>(emptyList())
    private val discoveredDeviceIds = mutableSetOf<String>() // 用于避免重复设备
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
    
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun searchDevices(): Flow<DeviceInfo> {
        Log.d(TAG, "Searching for Bluetooth devices")
        
        // 重置设备列表和已发现设备ID集合
        _devices.value = emptyList()
        discoveredDeviceIds.clear()
        
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
        discoveryReceiver = BluetoothDiscoveryReceiver(_devices, discoveredDeviceIds)
        context.registerReceiver(
            discoveryReceiver,
            BluetoothDiscoveryReceiver.getIntentFilter()
        )
        
        // 开始扫描
        bluetoothAdapter.startDiscovery()
        Log.d(TAG, "Bluetooth device discovery started")
        
        // 创建Flow，确保在取消时清理资源
        return _devices.asStateFlow().flatMapLatest { devices ->
            if (devices.isEmpty()) {
                emptyFlow()
            } else {
                flowOf(*devices.toTypedArray())
            }
        }.onCompletion { 
            // 在Flow取消时清理资源
            Log.d(TAG, "Bluetooth device search completed/cancelled, cleaning up")
            if (bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.cancelDiscovery()
            }
            if (discoveryReceiver != null) {
                try {
                    context.unregisterReceiver(discoveryReceiver)
                    discoveryReceiver = null
                } catch (e: Exception) {
                    Log.e(TAG, "Error unregistering broadcast receiver", e)
                }
            }
            // 清空已发现设备集合
            discoveredDeviceIds.clear()
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
        
        var lastException: Exception? = null
        
        // 多次尝试连接
        for (attempt in 1..CONNECTION_RETRIES) {
            try {
                Log.d(TAG, "Connection attempt $attempt/$CONNECTION_RETRIES")
                
                // 创建RFCOMM套接字
                bluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(SYNC_UUID)
                
                // 取消扫描以提高连接速度
                if (bluetoothAdapter.isDiscovering) {
                    bluetoothAdapter.cancelDiscovery()
                }
                
                // 使用超时的方式连接
                val connectSuccess = bluetoothSocket?.let { socket ->
                    withTimeoutOrNull(CONNECTION_TIMEOUT.toLong()) {
                        withContext(Dispatchers.IO) {
                            try {
                                socket.connect()
                                true
                            } catch (e: Exception) {
                                false
                            }
                        }
                    } ?: false
                } ?: false
                
                if (!connectSuccess) {
                    throw IOException("Connection timed out")
                }
                
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
                
                Log.d(TAG, "Successfully connected to Bluetooth device")
                return true
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Connection attempt $attempt failed", e)
                
                // 关闭套接字
                try {
                    bluetoothSocket?.close()
                } catch (e2: IOException) {
                    Log.e(TAG, "Error closing socket", e2)
                }
                bluetoothSocket = null
                
                // 如果不是最后一次尝试，等待一段时间后重试
                if (attempt < CONNECTION_RETRIES) {
                    delay(1000L * attempt) // 指数退避
                }
            }
        }
        
        Log.e(TAG, "Failed to connect to Bluetooth device after $CONNECTION_RETRIES attempts", lastException)
        return false
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
    
    /**
     * 计算数据的校验和
     */
    private fun calculateChecksum(data: String): String {
        return data.hashCode().toString()
    }
    
    override suspend fun sendData(data: DeviceSyncData): Boolean {
        Log.d(TAG, "Sending data to Bluetooth device")
        
        return try {
            val jsonData = gson.toJson(data)
            val checksum = calculateChecksum(jsonData)
            val dataToSend = "$checksum:$jsonData"
            
            outputStream?.write(dataToSend.toByteArray())
            outputStream?.flush()
            
            Log.d(TAG, "Data sent successfully with checksum: $checksum")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send data to Bluetooth device", e)
            false
        }
    }
    
    override suspend fun receiveData(): DeviceSyncData? {
        Log.d(TAG, "Receiving data from Bluetooth device")
        
        return try {
            val buffer = ByteArray(4096) // 增大缓冲区以处理较大的数据
            val bytesRead = inputStream?.read(buffer)
            
            if (bytesRead != null && bytesRead > 0) {
                val rawData = String(buffer, 0, bytesRead)
                
                // 解析数据格式: CHECKSUM:DATA
                val separatorIndex = rawData.indexOf(":")
                if (separatorIndex == -1) {
                    Log.e(TAG, "Invalid data format: $rawData")
                    return null
                }
                
                val receivedChecksum = rawData.substring(0, separatorIndex)
                val dataJson = rawData.substring(separatorIndex + 1)
                val calculatedChecksum = calculateChecksum(dataJson)
                
                // 验证数据完整性
                if (receivedChecksum != calculatedChecksum) {
                    Log.e(TAG, "Data integrity check failed! Received: $receivedChecksum, Calculated: $calculatedChecksum")
                    return null
                }
                
                Log.d(TAG, "Data received successfully with valid checksum")
                gson.fromJson(dataJson, DeviceSyncData::class.java)
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
        private val devicesFlow: MutableStateFlow<List<DeviceInfo>>,
        private val discoveredDeviceIds: MutableSet<String>
    ) : BroadcastReceiver() {
        
        companion object {
            fun getIntentFilter(): IntentFilter {
                val filter = IntentFilter()
                filter.addAction(BluetoothDevice.ACTION_FOUND)
                filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                return filter
            }
        }
        
        private val TAG = this::class.java.simpleName
        
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // 发现设备
                    val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    
                    device?.let {
                        // 检查设备是否支持我们的服务UUID
                        if (supportsServiceUUID(it)) {
                            // 检查是否为重复设备
                            if (!discoveredDeviceIds.contains(it.address)) {
                                val deviceName = it.name ?: "Unknown Device"
                                Log.d(TAG, "Found supported Bluetooth device: $deviceName (${it.address})")
                                
                                val deviceInfo = DeviceInfo(
                                    deviceId = it.address,
                                    deviceName = deviceName,
                                    deviceType = "ANDROID",
                                    connectionType = "BLUETOOTH",
                                    address = it.address,
                                    signalStrength = calculateSignalStrength(rssi)
                                )
                                
                                discoveredDeviceIds.add(it.address)
                                devicesFlow.value = devicesFlow.value + deviceInfo
                            } else {
                                Log.d(TAG, "Ignoring duplicate Bluetooth device: ${it.address}")
                            }
                        } else {
                            Log.d(TAG, "Ignoring Bluetooth device without required service: ${it.address}")
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // 扫描完成
                    Log.d(TAG, "Bluetooth discovery finished")
                }
            }
        }
        
        /**
         * 检查设备是否支持我们的服务UUID
         */
        private fun supportsServiceUUID(device: BluetoothDevice): Boolean {
            // 检查设备名称是否包含我们的应用标识
            if (device.name?.contains(DEVICE_NAME, ignoreCase = true) == true) {
                return true
            }
            
            // 检查设备的UUIDs是否包含我们的服务UUID
            try {
                // 对于已配对的设备，可以获取其UUIDs
                if (device.bondState == BluetoothDevice.BOND_BONDED) {
                    val uuids = device.uuids
                    if (uuids != null) {
                        for (uuid in uuids) {
                            if (SYNC_UUID == uuid.uuid) {
                                return true
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking device UUIDs", e)
            }
            
            // 对于未配对设备，默认认为可能支持（将在连接时验证）
            return true
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