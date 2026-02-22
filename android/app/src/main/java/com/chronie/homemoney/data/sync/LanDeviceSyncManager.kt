package com.chronie.homemoney.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Collections
import java.util.Enumeration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 局域网设备间同步管理器
 * 基于TCP/IP实现设备间同步，支持Android 8-16
 */
class LanDeviceSyncManager(
    private val context: Context,
    expenseDao: ExpenseDao,
    gson: Gson,
    private val wifiManager: WifiManager
) : BaseDeviceSyncManager(expenseDao, gson) {
    
    companion object {
        private const val BROADCAST_PORT = 12345
        private const val SYNC_PORT = 12346
        private const val BROADCAST_INTERVAL = 500L
        private const val BROADCAST_COUNT = 10
        private const val DISCOVERY_TIMEOUT = 30000L
        private const val TCP_CONNECTION_TIMEOUT = 8000
        private const val TCP_CONNECTION_RETRIES = 3
        private const val TAG = "LanDeviceSyncManager"
        private const val SOCKET_TIMEOUT = 30000
        private const val MULTICAST_GROUP = "239.255.255.250"
    }
    
    private var socket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var isServerRunning = AtomicBoolean(false)
    private val discoveredDevices = ConcurrentHashMap<String, DeviceInfo>()
    private var broadcastThread: Thread? = null
    private var serverThread: Thread? = null
    private val handler = Handler(Looper.getMainLooper())
    
    // 设备唯一标识
    private val deviceId: String by lazy {
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        prefs.getString("device_sync_id", null) ?: run {
            val newId = "android_${UUID.randomUUID().toString().substring(0, 8)}"
            prefs.edit().putString("device_sync_id", newId).apply()
            newId
        }
    }
    
    // 设备名称（使用用户自定义名称或设备型号）
    private val deviceName: String by lazy {
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        prefs.getString("device_custom_name", null) ?: Build.MODEL ?: "Android Device"
    }
    
    /**
     * 设置设备自定义名称
     */
    fun setDeviceCustomName(name: String) {
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("device_custom_name", name).apply()
    }
    
    /**
     * 获取设备自定义名称
     */
    fun getDeviceCustomName(): String? {
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        return prefs.getString("device_custom_name", null)
    }
    
    /**
     * 检查WiFi是否连接
     */
    private fun isWifiConnected(): Boolean {
        val connectivityManager = ContextCompat.getSystemService(context, ConnectivityManager::class.java)
            ?: return false
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            val wifiInfo = wifiManager.connectionInfo
            wifiInfo != null && wifiInfo.networkId != -1
        }
    }
    
    /**
     * 获取本地IP地址
     */
    private fun getLocalIpAddress(): String? {
        return try {
            val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in Collections.list(interfaces)) {
                val addresses = networkInterface.inetAddresses
                for (address in Collections.list(addresses)) {
                    if (!address.isLoopbackAddress && address is InetAddress && address.hostAddress.indexOf(':') < 0) {
                        val ip = address.hostAddress
                        // 优先返回WiFi地址（通常是192.168.x.x或10.x.x.x）
                        if (ip.startsWith("192.168.") || ip.startsWith("10.")) {
                            return ip
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting local IP address", e)
            null
        }
    }
    
    /**
     * 获取广播地址列表
     */
    private fun getBroadcastAddresses(): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()
        
        try {
            // 添加标准广播地址
            addresses.add(InetAddress.getByName("255.255.255.255"))
            addresses.add(InetAddress.getByName(MULTICAST_GROUP))
            
            // 获取WiFi子网的广播地址
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                @Suppress("DEPRECATION")
                val dhcpInfo = wifiManager.dhcpInfo
                if (dhcpInfo != null) {
                    val ip = dhcpInfo.ipAddress
                    val mask = dhcpInfo.netmask
                    val broadcast = ip or (mask.inv())
                    
                    val quads = ByteArray(4)
                    for (k in 0..3) {
                        quads[k] = (broadcast shr (k * 8)).toByte()
                    }
                    
                    try {
                        val subnetBroadcast = InetAddress.getByAddress(quads)
                        if (!addresses.contains(subnetBroadcast)) {
                            addresses.add(subnetBroadcast)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to create subnet broadcast address", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting broadcast addresses", e)
        }
        
        return addresses
    }
    
    override fun searchDevices(): Flow<DeviceInfo> = flow {
        Log.d(TAG, "Starting device search on LAN")
        discoveredDevices.clear()
        
        if (!isWifiConnected()) {
            Log.w(TAG, "WiFi not connected, cannot search devices")
            return@flow
        }
        
        val localIp = getLocalIpAddress()
        if (localIp == null) {
            Log.w(TAG, "Cannot get local IP address")
            return@flow
        }
        
        Log.d(TAG, "Local IP: $localIp")
        
        // 启动服务器以接收其他设备的连接
        startSyncServer()
        
        // 发送广播发现其他设备
        sendDiscoveryBroadcast(localIp)
        
        // 监听其他设备的响应
        val startTime = System.currentTimeMillis()
        val discoverySocket = java.net.DatagramSocket(BROADCAST_PORT)
        
        try {
            discoverySocket.soTimeout = 1000
            val buffer = ByteArray(1024)
            
            while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT) {
                try {
                    val packet = java.net.DatagramPacket(buffer, buffer.size)
                    discoverySocket.receive(packet)
                    
                    val message = String(packet.data, 0, packet.length)
                    val senderAddress = packet.address.hostAddress
                    
                    // 忽略自己的广播
                    if (senderAddress == localIp) {
                        continue
                    }
                    
                    val deviceInfo = parseDiscoveryResponse(message, senderAddress)
                    if (deviceInfo != null && !discoveredDevices.containsKey(deviceInfo.deviceId)) {
                        discoveredDevices[deviceInfo.deviceId] = deviceInfo
                        Log.d(TAG, "Discovered device: ${deviceInfo.deviceName} at ${deviceInfo.address}")
                        emit(deviceInfo)
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // 超时继续
                } catch (e: Exception) {
                    Log.e(TAG, "Error receiving discovery response", e)
                }
            }
        } finally {
            discoverySocket.close()
        }
        
        Log.d(TAG, "Device search completed, found ${discoveredDevices.size} devices")
    }
    
    /**
     * 发送发现广播
     */
    private fun sendDiscoveryBroadcast(localIp: String) {
        Thread {
            try {
                val broadcastAddresses = getBroadcastAddresses()
                val message = createDiscoveryMessage(localIp)
                val data = message.toByteArray()
                
                for (i in 0 until BROADCAST_COUNT) {
                    try {
                        val socket = java.net.DatagramSocket()
                        socket.broadcast = true
                        
                        for (address in broadcastAddresses) {
                            try {
                                val packet = java.net.DatagramPacket(
                                    data,
                                    data.size,
                                    address,
                                    BROADCAST_PORT
                                )
                                socket.send(packet)
                                Log.d(TAG, "Sent discovery broadcast to ${address.hostAddress}")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send to ${address.hostAddress}", e)
                            }
                        }
                        
                        socket.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error sending discovery broadcast", e)
                    }
                    
                    if (i < BROADCAST_COUNT - 1) {
                        Thread.sleep(BROADCAST_INTERVAL)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in discovery broadcast thread", e)
            }
        }.start()
    }
    
    /**
     * 创建发现消息
     */
    private fun createDiscoveryMessage(localIp: String): String {
        return "DISCOVERY|$deviceId|$deviceName|$localIp|${System.currentTimeMillis()}"
    }
    
    /**
     * 解析发现响应
     */
    private fun parseDiscoveryResponse(message: String, address: String): DeviceInfo? {
        return try {
            val parts = message.split("|")
            if (parts.size >= 4 && parts[0] == "DISCOVERY") {
                DeviceInfo(
                    deviceId = parts[1],
                    deviceName = parts[2],
                    deviceType = "ANDROID",
                    connectionType = "LAN",
                    address = parts[3],
                    signalStrength = 80
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing discovery response", e)
            null
        }
    }
    
    /**
     * 启动同步服务器
     */
    fun startSyncServer() {
        if (isServerRunning.get()) {
            return
        }
        
        isServerRunning.set(true)
        serverThread = Thread {
            try {
                serverSocket = ServerSocket(SYNC_PORT)
                Log.d(TAG, "Sync server started on port $SYNC_PORT")
                
                while (isServerRunning.get()) {
                    try {
                        serverSocket?.soTimeout = 5000
                        val clientSocket = serverSocket?.accept()
                        
                        if (clientSocket != null) {
                            GlobalScope.launch(Dispatchers.IO) {
                                handleClientConnection(clientSocket)
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // 超时继续循环
                    } catch (e: SocketException) {
                        if (isServerRunning.get()) {
                            Log.e(TAG, "Server socket error", e)
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in sync server", e)
            } finally {
                Log.d(TAG, "Sync server stopped")
            }
        }
        serverThread?.start()
    }
    
    /**
     * 停止同步服务器
     */
    fun stopSyncServer() {
        isServerRunning.set(false)
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping sync server", e)
        }
    }
    
    /**
     * 处理客户端连接
     */
    private suspend fun handleClientConnection(clientSocket: Socket) {
        try {
            Log.d(TAG, "Client connected: ${clientSocket.inetAddress.hostAddress}")
            clientSocket.soTimeout = SOCKET_TIMEOUT
            
            val reader = BufferedReader(InputStreamReader(clientSocket.getInputStream()))
            val writer = PrintWriter(clientSocket.getOutputStream(), true)
            
            // 读取客户端发送的数据
            val request = reader.readLine()
            if (request == null) {
                Log.w(TAG, "Empty request from client")
                return
            }
            
            Log.d(TAG, "Received request: $request")
            
            when {
                request.startsWith("SYNC|") -> {
                    // 处理同步请求
                    handleSyncRequest(request, reader, writer)
                }
                request.startsWith("PING|") -> {
                    // 处理心跳请求
                    writer.println("PONG|$deviceId|$deviceName")
                }
                else -> {
                    Log.w(TAG, "Unknown request type: $request")
                }
            }
            
            clientSocket.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client connection", e)
        }
    }
    
    /**
     * 处理同步请求
     */
    private suspend fun handleSyncRequest(
        request: String,
        reader: BufferedReader,
        writer: PrintWriter
    ) {
        try {
            // 读取客户端数据
            val clientDataJson = reader.readLine()
            if (clientDataJson == null) {
                Log.w(TAG, "No data received from client")
                writer.println("ERROR|No data received")
                return
            }
            
            val clientData = gson.fromJson(clientDataJson, DeviceSyncData::class.java)
            Log.d(TAG, "Received sync data from ${clientData.deviceName}")
            
            // 处理客户端数据
            val downloadResult = processDeviceData(clientData)
            
            // 准备本地数据
            val localData = prepareLocalData()
            val localDataJson = gson.toJson(localData)
            
            // 发送响应
            writer.println("OK")
            writer.println(localDataJson)
            
            Log.d(TAG, "Sync completed with client")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling sync request", e)
            writer.println("ERROR|${e.message}")
        }
    }
    
    override suspend fun connect(device: DeviceInfo): Boolean {
        Log.d(TAG, "Connecting to device: ${device.deviceName} at ${device.address}")
        
        var lastException: Exception? = null
        
        for (attempt in 1..TCP_CONNECTION_RETRIES) {
            try {
                val socket = Socket()
                socket.soTimeout = SOCKET_TIMEOUT
                
                val connected = withTimeoutOrNull(TCP_CONNECTION_TIMEOUT.toLong()) {
                    withContext(Dispatchers.IO) {
                        socket.connect(InetSocketAddress(device.address, SYNC_PORT), TCP_CONNECTION_TIMEOUT)
                        true
                    }
                } ?: false
                
                if (!connected) {
                    throw Exception("Connection timeout")
                }
                
                this.socket = socket
                isConnected = true
                currentDevice = device
                
                Log.d(TAG, "Successfully connected to device")
                return true
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Connection attempt $attempt failed", e)
                
                if (attempt < TCP_CONNECTION_RETRIES) {
                    delay(1000L * attempt)
                }
            }
        }
        
        Log.e(TAG, "Failed to connect after $TCP_CONNECTION_RETRIES attempts", lastException)
        return false
    }
    
    override suspend fun disconnect(): Boolean {
        Log.d(TAG, "Disconnecting from device")
        
        try {
            socket?.close()
            socket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
        }
        
        isConnected = false
        currentDevice = null
        return true
    }
    
    override suspend fun sendData(data: DeviceSyncData): Boolean {
        Log.d(TAG, "Sending data to device")
        
        return try {
            val socket = this.socket ?: return false
            val writer = PrintWriter(socket.getOutputStream(), true)
            
            // 发送同步请求头
            writer.println("SYNC|$deviceId|$deviceName")
            
            // 发送数据
            val dataJson = gson.toJson(data)
            writer.println(dataJson)
            
            Log.d(TAG, "Data sent successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send data", e)
            false
        }
    }
    
    override suspend fun receiveData(): DeviceSyncData? {
        Log.d(TAG, "Receiving data from device")
        
        return try {
            val socket = this.socket ?: return null
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            
            // 读取响应状态
            val response = reader.readLine()
            if (response != "OK") {
                Log.e(TAG, "Server returned error: $response")
                return null
            }
            
            // 读取数据
            val dataJson = reader.readLine()
            if (dataJson == null) {
                Log.e(TAG, "No data received from server")
                return null
            }
            
            gson.fromJson(dataJson, DeviceSyncData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive data", e)
            null
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        stopSyncServer()
        GlobalScope.launch {
            disconnect()
        }
    }
}
