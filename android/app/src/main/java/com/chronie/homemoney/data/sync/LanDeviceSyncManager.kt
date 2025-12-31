package com.chronie.homemoney.data.sync

import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.domain.sync.DeviceInfo
import com.chronie.homemoney.domain.sync.DeviceSyncData
import com.chronie.homemoney.domain.sync.DeviceSyncManager
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.net.*
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 局域网设备间同步管理器
 * 基于TCP/IP实现设备间同步
 */
class LanDeviceSyncManager(
    expenseDao: ExpenseDao,
    gson: Gson,
    private val wifiManager: WifiManager
) : BaseDeviceSyncManager(expenseDao, gson) {
    
    private val BROADCAST_PORT = 12345
    private val SYNC_PORT = 12346
    private val BROADCAST_INTERVAL = 1000L
    private val BROADCAST_COUNT = 5
    private val DISCOVERY_TIMEOUT = 15000L
    private val TCP_CONNECTION_TIMEOUT = 5000
    private val TCP_CONNECTION_RETRIES = 3
    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var broadcastThread: Thread? = null
    
    override fun searchDevices(): Flow<DeviceInfo> = flow {
        Log.d(TAG, "Searching for devices on LAN")
        
        val discoveredDevices = mutableSetOf<String>()
        val startTime = System.currentTimeMillis()
        var socket: DatagramSocket? = null
        
        try {
            socket = DatagramSocket(BROADCAST_PORT)
            socket.soTimeout = DISCOVERY_TIMEOUT.toInt()
            
            // 发送多次广播以提高发现概率
            for (i in 0 until BROADCAST_COUNT) {
                sendBroadcast()
                if (i < BROADCAST_COUNT - 1) {
                    delay(BROADCAST_INTERVAL)
                }
            }
            
            val buffer = ByteArray(1024)
            val packet = DatagramPacket(buffer, buffer.size)
            
            while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT) {
                try {
                    // 设置超时时间
                    socket.soTimeout = (DISCOVERY_TIMEOUT - (System.currentTimeMillis() - startTime)).toInt()
                    socket.receive(packet)
                    val message = String(packet.data, 0, packet.length)
                    val deviceInfo = parseDeviceInfo(message, packet.address.hostAddress)
                    
                    if (deviceInfo != null && discoveredDevices.add(deviceInfo.deviceId)) {
                        Log.d(TAG, "Discovered device: ${deviceInfo.deviceName} (${deviceInfo.address})")
                        emit(deviceInfo)
                    }
                } catch (e: SocketTimeoutException) {
                    // Timeout is expected
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error receiving broadcast", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in broadcast receiver", e)
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing broadcast socket", e)
            }
        }
        
        Log.d(TAG, "LAN device search completed")
    }
    
    private fun sendBroadcast() {
        Log.d(TAG, "Sending broadcast on LAN")
        
        val deviceName = Build.MODEL
        val deviceId = "android_${Build.SERIAL ?: UUID.randomUUID().toString()}"
        val message = "DEVICE_DISCOVERY,$deviceId,$deviceName,ANDROID,LAN"
        
        try {
            val socket = DatagramSocket()
            socket.broadcast = true
            
            val data = message.toByteArray()
            
            // 尝试获取实际的广播地址
            val broadcastAddress = getBroadcastAddress()
            Log.d(TAG, "Broadcasting to: $broadcastAddress")
            
            // 发送到实际广播地址
            val packet = DatagramPacket(
                data, 
                data.size, 
                broadcastAddress, 
                BROADCAST_PORT
            )
            
            socket.send(packet)
            
            // 同时发送到255.255.255.255作为后备
            if (broadcastAddress.hostAddress != "255.255.255.255") {
                val backupPacket = DatagramPacket(
                    data, 
                    data.size, 
                    InetAddress.getByName("255.255.255.255"), 
                    BROADCAST_PORT
                )
                socket.send(backupPacket)
            }
            
            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending broadcast", e)
        }
    }
    
    /**
     * 获取当前WiFi网络的广播地址
     */
    private fun getBroadcastAddress(): InetAddress {
        return try {
            val dhcpInfo = wifiManager.dhcpInfo
            if (dhcpInfo == null) {
                return InetAddress.getByName("255.255.255.255")
            }
            
            val ip = dhcpInfo.ipAddress
            val mask = dhcpInfo.netmask
            val broadcast = ip or (mask.inv())
            
            val quads = ByteArray(4)
            for (k in 0..3) {
                quads[k] = (broadcast shr (k * 8)).toByte()
            }
            
            InetAddress.getByAddress(quads)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting broadcast address, using default", e)
            InetAddress.getByName("255.255.255.255")
        }
    }
    
    private fun parseDeviceInfo(message: String, ipAddress: String): DeviceInfo? {
        Log.d(TAG, "Received broadcast from $ipAddress: $message")
        
        val parts = message.split(",")
        if (parts.size >= 5 && parts[0] == "DEVICE_DISCOVERY") {
            return DeviceInfo(
                deviceId = parts[1],
                deviceName = parts[2],
                deviceType = parts[3],
                connectionType = parts[4],
                address = ipAddress,
                signalStrength = 80 // 默认信号强度
            )
        }
        
        return null
    }
    
    override suspend fun connect(device: DeviceInfo): Boolean {
        Log.d(TAG, "Connecting to LAN device: ${device.deviceName} at ${device.address}")
        
        var lastException: Exception? = null
        
        // 多次尝试连接
        for (attempt in 1..TCP_CONNECTION_RETRIES) {
            try {
                Log.d(TAG, "Connection attempt $attempt/$TCP_CONNECTION_RETRIES")
                val socket = Socket()
                socket.soTimeout = TCP_CONNECTION_TIMEOUT
                socket.connect(InetSocketAddress(device.address, SYNC_PORT), TCP_CONNECTION_TIMEOUT)
                
                this.socket = socket
                isConnected = true
                currentDevice = device
                Log.d(TAG, "Successfully connected to LAN device")
                return true
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Connection attempt $attempt failed", e)
                
                // 如果不是最后一次尝试，等待一段时间后重试
                if (attempt < TCP_CONNECTION_RETRIES) {
                    delay(1000L * attempt) // 指数退避
                }
            }
        }
        
        Log.e(TAG, "Failed to connect to LAN device after $TCP_CONNECTION_RETRIES attempts", lastException)
        return false
    }
    
    override suspend fun disconnect(): Boolean {
        Log.d(TAG, "Disconnecting from LAN device")
        
        try {
            socket?.close()
            socket = null
            serverSocket?.close()
            serverSocket = null
            broadcastThread?.interrupt()
            broadcastThread = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting from LAN device", e)
        }
        
        isConnected = false
        currentDevice = null
        return true
    }
    
    override suspend fun sendData(data: DeviceSyncData): Boolean {
        Log.d(TAG, "Sending data to LAN device")
        
        return try {
            val outputStream = socket?.getOutputStream() ?: return false
            val dataJson = gson.toJson(data)
            val checksum = calculateChecksum(dataJson)
            
            // 发送数据格式: CHECKSUM:DATA
            val writer = ObjectOutputStream(outputStream)
            writer.writeObject("$checksum:$dataJson")
            writer.flush()
            
            Log.d(TAG, "Data sent successfully with checksum: $checksum")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send data to LAN device", e)
            false
        }
    }
    
    override suspend fun receiveData(): DeviceSyncData? {
        Log.d(TAG, "Receiving data from LAN device")
        
        return try {
            val inputStream = socket?.getInputStream() ?: return null
            val reader = ObjectInputStream(inputStream)
            val rawData = reader.readObject() as String
            
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive data from LAN device", e)
            null
        }
    }
    
    /**
     * 计算数据的简单校验和
     */
    private fun calculateChecksum(data: String): String {
        return data.hashCode().toString()
    }
    
    /**
     * 启动同步服务器
     */
    fun startSyncServer() {
        Log.d(TAG, "Starting sync server on port $SYNC_PORT")
        
        broadcastThread = Thread {
            try {
                serverSocket = ServerSocket(SYNC_PORT)
                
                while (!Thread.currentThread().isInterrupted) {
                    val clientSocket = serverSocket?.accept() ?: continue
                    Log.d(TAG, "Client connected: ${clientSocket.inetAddress.hostAddress}")
                    
                    // 处理客户端连接
                    GlobalScope.launch {
                        handleClientConnection(clientSocket)
                    }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Sync server interrupted")
            } catch (e: Exception) {
                Log.e(TAG, "Error in sync server", e)
            }
        }
        
        broadcastThread?.start()
    }
    
    private suspend fun handleClientConnection(clientSocket: Socket) = withContext(Dispatchers.IO) {
        try {
            val inputStream = clientSocket.getInputStream()
            val outputStream = clientSocket.getOutputStream()
            
            val reader = ObjectInputStream(inputStream)
            val writer = ObjectOutputStream(outputStream)
            
            // 接收客户端数据
            val rawData = reader.readObject() as String
            
            // 解析数据格式: CHECKSUM:DATA
            val separatorIndex = rawData.indexOf(":")
            if (separatorIndex == -1) {
                Log.e(TAG, "Invalid data format from client: $rawData")
                return@withContext
            }
            
            val receivedChecksum = rawData.substring(0, separatorIndex)
            val dataJson = rawData.substring(separatorIndex + 1)
            val calculatedChecksum = calculateChecksum(dataJson)
            
            // 验证数据完整性
            if (receivedChecksum != calculatedChecksum) {
                Log.e(TAG, "Data integrity check failed from client! Received: $receivedChecksum, Calculated: $calculatedChecksum")
                return@withContext
            }
            
            val clientData = gson.fromJson(dataJson, DeviceSyncData::class.java)
            Log.d(TAG, "Received client data successfully with valid checksum")
            
            // 处理客户端数据
            val downloadResult = processDeviceData(clientData)
            
            // 发送本地数据给客户端
            val localData = prepareLocalData()
            val localDataJson = gson.toJson(localData)
            val localChecksum = calculateChecksum(localDataJson)
            writer.writeObject("$localChecksum:$localDataJson")
            writer.flush()
            
            Log.d(TAG, "Sent local data to client with checksum: $localChecksum")
            
            clientSocket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client connection", e)
        }
    }
}