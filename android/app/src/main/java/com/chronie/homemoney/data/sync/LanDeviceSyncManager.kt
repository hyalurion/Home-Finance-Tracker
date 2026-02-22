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
import com.chronie.homemoney.domain.sync.SyncProgressInfo
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
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
        private const val DISCOVERY_BROADCAST_PORT = 12345  // 发现广播端口（发送和监听发现请求）
        private const val DISCOVERY_RESPONSE_PORT = 12347   // 发现响应端口（接收响应）
        private const val SYNC_PORT = 12346                 // TCP同步端口
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
    
    // 同步锁，防止同时作为客户端和服务器进行同步
    private val syncLock = Object()
    @Volatile
    private var isSyncing = false

    // 同步进度状态流
    private val _syncProgress = kotlinx.coroutines.flow.MutableStateFlow(SyncProgressInfo())
    override val syncProgress: kotlinx.coroutines.flow.StateFlow<SyncProgressInfo> = _syncProgress.asStateFlow()

    // 同步请求回调
    private var syncRequestCallback: com.chronie.homemoney.domain.sync.SyncRequestCallback? = null
    private var pendingSyncResponse: kotlin.coroutines.Continuation<Boolean>? = null

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
     * 更新同步进度（用于服务器端通知UI）
     */
    override fun updateSyncProgress(progress: Float, message: String, isActive: Boolean) {
        _syncProgress.value = SyncProgressInfo(
            progress = progress,
            message = message,
            isActive = isActive,
            deviceName = deviceName
        )
    }

    /**
     * 清除同步进度
     */
    override fun clearSyncProgress() {
        _syncProgress.value = SyncProgressInfo()
    }

    /**
     * 设置同步请求回调
     */
    override fun setSyncRequestCallback(callback: com.chronie.homemoney.domain.sync.SyncRequestCallback?) {
        syncRequestCallback = callback
    }

    /**
     * 响应同步请求
     */
    override fun respondToSyncRequest(accepted: Boolean) {
        pendingSyncResponse?.resume(accepted)
        pendingSyncResponse = null
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

        // 确保服务器已启动（如果尚未启动）
        startSyncServer()

        // 创建用于发送广播和接收响应的 socket，绑定到固定端口
        val discoverySocket = java.net.DatagramSocket(DISCOVERY_RESPONSE_PORT)

        // 发送广播发现其他设备
        sendDiscoveryBroadcast(localIp, discoverySocket)

        // 监听其他设备的响应
        val startTime = System.currentTimeMillis()

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
                        // 使用数据包的源地址作为连接地址（更可靠）
                        val deviceWithCorrectAddress = deviceInfo.copy(address = senderAddress)
                        discoveredDevices[deviceInfo.deviceId] = deviceWithCorrectAddress
                        Log.d(TAG, "Discovered device: ${deviceWithCorrectAddress.deviceName} at ${deviceWithCorrectAddress.address} (from packet)")
                        emit(deviceWithCorrectAddress)
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
    }.flowOn(Dispatchers.IO)
    
    /**
     * 发送发现广播
     * @param localIp 本地IP地址
     * @param socket 用于发送广播的socket（已绑定到特定端口）
     */
    private fun sendDiscoveryBroadcast(localIp: String, socket: java.net.DatagramSocket) {
        Thread {
            try {
                val broadcastAddresses = getBroadcastAddresses()
                val message = createDiscoveryMessage(localIp)
                val data = message.toByteArray()

                // 设置广播权限
                socket.broadcast = true

                for (i in 0 until BROADCAST_COUNT) {
                    try {
                        for (address in broadcastAddresses) {
                            try {
                                val packet = java.net.DatagramPacket(
                                    data,
                                    data.size,
                                    address,
                                    DISCOVERY_BROADCAST_PORT
                                )
                                socket.send(packet)
                                Log.d(TAG, "Sent discovery broadcast to ${address.hostAddress}:$DISCOVERY_BROADCAST_PORT from port ${socket.localPort}")
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to send to ${address.hostAddress}", e)
                            }
                        }
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
    
    private var discoveryResponseSocket: java.net.DatagramSocket? = null
    private var discoveryResponseThread: Thread? = null

    /**
     * 启动同步服务器
     */
    fun startSyncServer() {
        if (isServerRunning.get()) {
            return
        }

        isServerRunning.set(true)

        // 启动 TCP 服务器处理同步请求
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

        // 启动 UDP 发现响应服务器
        startDiscoveryResponseServer()
    }

    /**
     * 启动发现响应服务器（监听其他设备的发现请求并响应）
     */
    private fun startDiscoveryResponseServer() {
        discoveryResponseThread = Thread {
            try {
                // 监听发现广播端口
                discoveryResponseSocket = java.net.DatagramSocket(DISCOVERY_BROADCAST_PORT)
                discoveryResponseSocket?.soTimeout = 1000
                val buffer = ByteArray(1024)

                Log.d(TAG, "Discovery response server started on port $DISCOVERY_BROADCAST_PORT")

                while (isServerRunning.get()) {
                    try {
                        val packet = java.net.DatagramPacket(buffer, buffer.size)
                        discoveryResponseSocket?.receive(packet)

                        val message = String(packet.data, 0, packet.length)
                        val senderAddress = packet.address.hostAddress
                        val localIp = getLocalIpAddress()

                        // 忽略自己的广播
                        if (senderAddress == localIp) {
                            continue
                        }

                        // 解析发现请求
                        val parts = message.split("|")
                        if (parts.size >= 2 && parts[0] == "DISCOVERY") {
                            Log.d(TAG, "Received discovery request from $senderAddress:${packet.port}")

                            // 发送响应到请求的来源端口（搜索方监听的端口）
                            val responseMessage = createDiscoveryMessage(localIp ?: "")
                            val responseData = responseMessage.toByteArray()
                            val responsePacket = java.net.DatagramPacket(
                                responseData,
                                responseData.size,
                                packet.address,
                                packet.port
                            )
                            discoveryResponseSocket?.send(responsePacket)
                            Log.d(TAG, "Sent discovery response to $senderAddress:${packet.port}")
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // 超时继续循环
                    } catch (e: Exception) {
                        if (isServerRunning.get()) {
                            Log.e(TAG, "Error in discovery response server", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting discovery response server", e)
            } finally {
                discoveryResponseSocket?.close()
                Log.d(TAG, "Discovery response server stopped")
            }
        }
        discoveryResponseThread?.start()
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
        try {
            discoveryResponseSocket?.close()
            discoveryResponseSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping discovery response server", e)
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
                    // 检查是否已经在进行同步
                    if (isSyncing) {
                        Log.w(TAG, "Already syncing, rejecting sync request from client")
                        writer.println("ERROR|Device is busy syncing")
                        clientSocket.close()
                        return
                    }

                    // 解析客户端信息
                    val parts = request.split("|")
                    val clientDeviceId = if (parts.size >= 2) parts[1] else "unknown"
                    val clientDeviceName = if (parts.size >= 3) parts[2] else "Unknown Device"

                    // 如果有回调，先询问用户是否接受同步
                    val callback = syncRequestCallback
                    if (callback != null) {
                        Log.d(TAG, "Asking user to accept sync request from $clientDeviceName")

                        // 通知UI显示确认对话框
                        val requestInfo = com.chronie.homemoney.domain.sync.SyncRequestInfo(
                            deviceId = clientDeviceId,
                            deviceName = clientDeviceName,
                            address = clientSocket.inetAddress.hostAddress
                        )

                        // 使用挂起函数等待用户响应
                        val accepted = try {
                            kotlinx.coroutines.withTimeout(30000L) { // 30秒超时
                                kotlin.coroutines.suspendCoroutine<Boolean> { continuation ->
                                    pendingSyncResponse = continuation
                                    // 启动协程调用回调
                                    kotlinx.coroutines.GlobalScope.launch {
                                        val result = callback.onSyncRequest(requestInfo)
                                        respondToSyncRequest(result)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Sync request timeout or error", e)
                            false
                        }

                        if (!accepted) {
                            Log.d(TAG, "User rejected sync request from $clientDeviceName")
                            writer.println("ERROR|Sync request rejected")
                            clientSocket.close()
                            return
                        }

                        Log.d(TAG, "User accepted sync request from $clientDeviceName")
                    }

                    // 处理同步请求
                    handleSyncRequest(request, reader, writer, clientSocket)
                    // 注意：handleSyncRequest 内部会关闭 socket
                    return
                }
                request.startsWith("PING|") -> {
                    // 处理心跳请求
                    writer.println("PONG|$deviceId|$deviceName")
                }
                else -> {
                    Log.w(TAG, "Unknown request type: $request")
                }
            }

            // 关闭连接
            try {
                clientSocket.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing client socket", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client connection", e)
            try {
                clientSocket.close()
            } catch (e2: Exception) {
                // 忽略关闭错误
            }
        }
    }
    
    /**
     * 处理同步请求
     */
    private suspend fun handleSyncRequest(
        request: String,
        reader: BufferedReader,
        writer: PrintWriter,
        clientSocket: Socket
    ) {
        // 使用同步锁防止并发同步
        synchronized(syncLock) {
            if (isSyncing) {
                Log.w(TAG, "Already syncing, rejecting request")
                writer.println("ERROR|Device is busy")
                return
            }
            isSyncing = true
        }

        // 解析客户端设备信息
        val parts = request.split("|")
        val clientDeviceName = if (parts.size >= 3) parts[2] else "Unknown Device"

        try {
            Log.d(TAG, "Starting sync as server with client: ${clientSocket.inetAddress.hostAddress}")

            // 通知UI显示同步进度（服务器端作为被搜索方也需要显示）
            updateSyncProgress(0.1f, "正在接收来自 $clientDeviceName 的数据...", true)

            // 读取客户端发送的 SYNC 头（已经在 handleClientConnection 中读取并传入 request 参数）
            // 现在读取数据长度
            Log.d(TAG, "Waiting for data length...")
            val lengthLine = reader.readLine()
            if (lengthLine == null) {
                Log.w(TAG, "No length received from client")
                writer.println("ERROR|No length received")
                updateSyncProgress(1f, "接收失败：未收到数据长度", false)
                return
            }

            if (!lengthLine.startsWith("LENGTH|")) {
                Log.w(TAG, "Invalid length format: $lengthLine")
                writer.println("ERROR|Invalid length format")
                updateSyncProgress(1f, "接收失败：数据格式错误", false)
                return
            }

            val dataLength = lengthLine.substringAfter("LENGTH|").toIntOrNull()
            if (dataLength == null || dataLength <= 0) {
                Log.w(TAG, "Invalid data length: $dataLength")
                writer.println("ERROR|Invalid data length")
                updateSyncProgress(1f, "接收失败：数据长度无效", false)
                return
            }

            Log.d(TAG, "Expecting $dataLength bytes of data")

            updateSyncProgress(0.3f, "正在接收数据 (${dataLength} 字节)...", true)

            // 重要：在读取字节数据前，需要清空BufferedReader的缓冲区
            // 因为BufferedReader可能已经预读取了一些字节
            // 我们使用DataInputStream来精确读取指定字节数
            val inputStream = clientSocket.getInputStream()
            val dataInputStream = java.io.DataInputStream(inputStream)
            val dataBytes = ByteArray(dataLength)

            try {
                // 使用DataInputStream.readFully确保读取完整数据
                dataInputStream.readFully(dataBytes)
                Log.d(TAG, "Successfully read $dataLength bytes")
                updateSyncProgress(0.6f, "数据接收完成，正在处理...", true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read data", e)
                writer.println("ERROR|Failed to read data: ${e.message}")
                updateSyncProgress(1f, "接收失败：${e.message}", false)
                return
            }

            updateSyncProgress(0.6f, "正在处理接收到的数据...", true)

            val clientDataJson = String(dataBytes, Charsets.UTF_8)
            Log.d(TAG, "Received client data, length: ${clientDataJson.length}")
            val clientData = gson.fromJson(clientDataJson, DeviceSyncData::class.java)
            Log.d(TAG, "Received sync data from ${clientData.deviceName}, entities: ${clientData.entities.size}")

            // 处理客户端数据
            val downloadResult = processDeviceData(clientData)

            updateSyncProgress(0.7f, "正在准备本地数据...", true)

            // 准备本地数据
            val localData = prepareLocalData()
            val localDataJson = gson.toJson(localData)
            val localDataBytes = localDataJson.toByteArray(Charsets.UTF_8)
            Log.d(TAG, "Prepared local data: ${localDataBytes.size} bytes")

            updateSyncProgress(0.8f, "正在发送数据到 $clientDeviceName...", true)

            // 发送响应（使用长度前缀协议）
            writer.println("OK")
            writer.println("LENGTH|${localDataBytes.size}")
            writer.flush()

            // 发送数据
            val outputStream = clientSocket.getOutputStream()
            outputStream.write(localDataBytes)
            outputStream.flush()
            Log.d(TAG, "Sent response to client: ${localDataBytes.size} bytes")

            updateSyncProgress(1f, "与 $clientDeviceName 同步完成！", false)
            Log.d(TAG, "Sync completed with client")

            // 等待一小段时间确保数据被发送
            kotlinx.coroutines.delay(1500)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling sync request", e)
            updateSyncProgress(1f, "同步失败：${e.message}", false)
            try {
                writer.println("ERROR|${e.message}")
                writer.flush()
            } catch (e2: Exception) {
                // 忽略写入错误
            }
        } finally {
            isSyncing = false
            // 延迟清除进度，让用户能看到完成状态
            kotlinx.coroutines.delay(2000)
            clearSyncProgress()
            // 关闭连接
            try {
                clientSocket.close()
                Log.d(TAG, "Client socket closed")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing client socket in handleSyncRequest", e)
            }
        }
    }
    
    override suspend fun connect(device: DeviceInfo): Boolean {
        Log.d(TAG, "Connecting to device: ${device.deviceName} at ${device.address}:$SYNC_PORT")

        var lastException: Exception? = null

        for (attempt in 1..TCP_CONNECTION_RETRIES) {
            try {
                val socket = withContext(Dispatchers.IO) {
                    val newSocket = Socket()
                    newSocket.soTimeout = SOCKET_TIMEOUT
                    newSocket.connect(InetSocketAddress(device.address, SYNC_PORT), TCP_CONNECTION_TIMEOUT)
                    newSocket
                }

                this.socket = socket
                isConnected = true
                currentDevice = device

                Log.d(TAG, "Successfully connected to device ${device.deviceName}")
                return true
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Connection attempt $attempt failed: ${e.message}")

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

        return try {
            withContext(Dispatchers.IO) {
                socket?.close()
                socket = null
                isConnected = false
                currentDevice = null
                Log.d(TAG, "Disconnected successfully")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting", e)
            isConnected = false
            currentDevice = null
            false
        }
    }
    
    override suspend fun sendData(data: DeviceSyncData): Boolean {
        Log.d(TAG, "Sending data to device, entities count: ${data.entities.size}")

        return try {
            withContext(Dispatchers.IO) {
                val socket = this@LanDeviceSyncManager.socket ?: run {
                    Log.e(TAG, "Socket is null, cannot send data")
                    return@withContext false
                }
                val outputStream = socket.getOutputStream()
                val writer = PrintWriter(outputStream, true)

                // 发送同步请求头
                val header = "SYNC|$deviceId|$deviceName"
                writer.println(header)
                writer.flush()
                Log.d(TAG, "Sent header: $header")

                // 发送数据（使用长度前缀协议）
                val dataJson = gson.toJson(data)
                val dataBytes = dataJson.toByteArray(Charsets.UTF_8)
                val length = dataBytes.size

                // 先发送数据长度
                writer.println("LENGTH|$length")
                writer.flush()
                Log.d(TAG, "Sent length: $length bytes")

                // 然后发送数据本身
                outputStream.write(dataBytes)
                outputStream.flush()
                Log.d(TAG, "Sent data: ${dataBytes.size} bytes")

                // 等待一小段时间确保数据被发送
                Thread.sleep(500)

                Log.d(TAG, "Data sent successfully")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send data", e)
            false
        }
    }
    
    override suspend fun receiveData(): DeviceSyncData? {
        Log.d(TAG, "Receiving data from device")

        return try {
            withContext(Dispatchers.IO) {
                val socket = this@LanDeviceSyncManager.socket ?: return@withContext null
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                // 读取响应状态
                Log.d(TAG, "Waiting for server response...")
                val response = reader.readLine()
                Log.d(TAG, "Server response: $response")

                if (response == null) {
                    Log.e(TAG, "Server closed connection without response")
                    return@withContext null
                }

                if (!response.startsWith("OK")) {
                    Log.e(TAG, "Server returned error: $response")
                    return@withContext null
                }

                // 读取数据长度
                Log.d(TAG, "Waiting for data length...")
                val lengthLine = reader.readLine()
                if (lengthLine == null) {
                    Log.e(TAG, "No length received from server")
                    return@withContext null
                }

                if (!lengthLine.startsWith("LENGTH|")) {
                    Log.e(TAG, "Invalid length format: $lengthLine")
                    return@withContext null
                }

                val dataLength = lengthLine.substringAfter("LENGTH|").toIntOrNull()
                if (dataLength == null || dataLength <= 0) {
                    Log.e(TAG, "Invalid data length: $dataLength")
                    return@withContext null
                }

                Log.d(TAG, "Expecting $dataLength bytes of data")

                // 读取数据 - 使用DataInputStream确保读取完整数据
                val inputStream = socket.getInputStream()
                val dataInputStream = java.io.DataInputStream(inputStream)
                val dataBytes = ByteArray(dataLength)

                try {
                    dataInputStream.readFully(dataBytes)
                    Log.d(TAG, "Successfully read $dataLength bytes from server")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read data from server", e)
                    return@withContext null
                }

                val dataJson = String(dataBytes, Charsets.UTF_8)
                Log.d(TAG, "Received data, length: ${dataJson.length}, parsing...")
                gson.fromJson(dataJson, DeviceSyncData::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to receive data", e)
            null
        }
    }

    /**
     * 执行与设备的双向同步（带同步锁）
     */
    override suspend fun syncWithDevice(device: DeviceInfo): com.chronie.homemoney.domain.model.SyncResult {
        Log.d(TAG, "Starting sync with device: ${device.deviceName} at ${device.address}")

        // 检查是否已经在进行同步
        synchronized(syncLock) {
            if (isSyncing) {
                Log.w(TAG, "Already syncing, cannot start new sync")
                return createFailedSyncResult("Device is busy syncing")
            }
            isSyncing = true
        }

        return try {
            withContext(Dispatchers.IO) {
                // 1. 建立连接
                Log.d(TAG, "Step 1: Connecting to device...")
                if (!connect(device)) {
                    Log.e(TAG, "Failed to connect to device")
                    return@withContext createFailedSyncResult("Failed to connect to device")
                }
                Log.d(TAG, "Connected successfully")

                // 2. 获取本地数据
                Log.d(TAG, "Step 2: Preparing local data...")
                val localData = prepareLocalData()
                Log.d(TAG, "Local data prepared: ${localData.entities.size} entities")

                // 3. 发送本地数据到设备
                Log.d(TAG, "Step 3: Sending data to device...")
                if (!sendData(localData)) {
                    Log.e(TAG, "Failed to send data")
                    disconnect()
                    return@withContext createFailedSyncResult("Failed to send data to device")
                }
                Log.d(TAG, "Data sent successfully")

                // 4. 接收设备数据
                Log.d(TAG, "Step 4: Receiving data from device...")
                val deviceData = receiveData()
                if (deviceData == null) {
                    Log.e(TAG, "Failed to receive data")
                    disconnect()
                    return@withContext createFailedSyncResult("Failed to receive data from device")
                }
                Log.d(TAG, "Data received: ${deviceData.entities.size} entities")

                // 5. 处理设备数据
                Log.d(TAG, "Step 5: Processing device data...")
                val downloadResult = processDeviceData(deviceData)
                Log.d(TAG, "Data processed: ${downloadResult.newItems} new, ${downloadResult.updatedItems} updated")

                // 6. 断开连接
                Log.d(TAG, "Step 6: Disconnecting...")
                disconnect()
                Log.d(TAG, "Disconnected")

                // 7. 返回同步结果
                Log.d(TAG, "Sync completed successfully")
                com.chronie.homemoney.domain.model.SyncResult(
                    success = true,
                    uploadResult = com.chronie.homemoney.domain.model.UploadResult(
                        totalItems = localData.entities.size,
                        successCount = localData.entities.size,
                        failedCount = 0
                    ),
                    downloadResult = downloadResult,
                    conflicts = downloadResult.conflicts
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync with device failed", e)
            disconnect()
            createFailedSyncResult(e.message ?: "Unknown error")
        } finally {
            isSyncing = false
        }
    }

    /**
     * 创建失败的同步结果
     */
    private fun createFailedSyncResult(error: String): com.chronie.homemoney.domain.model.SyncResult {
        return com.chronie.homemoney.domain.model.SyncResult(
            success = false,
            uploadResult = com.chronie.homemoney.domain.model.UploadResult(0, 0, 0),
            downloadResult = com.chronie.homemoney.domain.model.DownloadResult(0, 0, 0),
            error = error
        )
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
