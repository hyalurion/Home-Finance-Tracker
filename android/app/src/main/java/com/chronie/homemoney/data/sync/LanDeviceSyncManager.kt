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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.net.*
import java.util.*

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
    private val BROADCAST_INTERVAL = 2000L
    private val DISCOVERY_TIMEOUT = 10000L
    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var broadcastThread: Thread? = null
    
    override fun searchDevices(): Flow<DeviceInfo> = flow {
        Log.d(TAG, "Searching for devices on LAN")
        
        val discoveredDevices = mutableSetOf<String>()
        val startTime = System.currentTimeMillis()
        
        try {
            val socket = DatagramSocket(BROADCAST_PORT)
            socket.soTimeout = DISCOVERY_TIMEOUT.toInt()
            
            // 发送广播
            sendBroadcast()
            delay(2000L)
            sendBroadcast()
            
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
                        emit(deviceInfo)
                    }
                } catch (e: SocketTimeoutException) {
                    // Timeout is expected
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "Error receiving broadcast", e)
                }
            }
            
            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error in broadcast receiver", e)
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
            val packet = DatagramPacket(
                data, 
                data.size, 
                InetAddress.getByName("255.255.255.255"), 
                BROADCAST_PORT
            )
            
            socket.send(packet)
            socket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending broadcast", e)
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
        
        return try {
            socket = Socket(device.address, SYNC_PORT)
            isConnected = true
            currentDevice = device
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to LAN device", e)
            false
        }
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
            val writer = ObjectOutputStream(outputStream)
            writer.writeObject(gson.toJson(data))
            writer.flush()
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
            val jsonData = reader.readObject() as String
            gson.fromJson(jsonData, DeviceSyncData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive data from LAN device", e)
            null
        }
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
            val jsonData = reader.readObject() as String
            val clientData = gson.fromJson(jsonData, DeviceSyncData::class.java)
            
            // 处理客户端数据
            val downloadResult = processDeviceData(clientData)
            
            // 发送本地数据给客户端
            val localData = prepareLocalData()
            writer.writeObject(gson.toJson(localData))
            writer.flush()
            
            clientSocket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client connection", e)
        }
    }
}