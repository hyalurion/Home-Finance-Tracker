package com.chronie.homemoney.domain.sync

import com.chronie.homemoney.domain.model.SyncResult
import kotlinx.coroutines.flow.Flow

/**
 * 设备间同步管理器接口
 * 用于实现不同设备之间的数据同步
 */
interface DeviceSyncManager {
    
    /**
     * 搜索附近可用的设备
     */
    fun searchDevices(): Flow<DeviceInfo>
    
    /**
     * 与指定设备建立连接
     */
    suspend fun connect(device: DeviceInfo): Boolean
    
    /**
     * 断开与当前设备的连接
     */
    suspend fun disconnect(): Boolean
    
    /**
     * 向连接的设备发送数据
     */
    suspend fun sendData(data: DeviceSyncData): Boolean
    
    /**
     * 从连接的设备接收数据
     */
    suspend fun receiveData(): DeviceSyncData?
    
    /**
     * 执行与设备的双向同步
     */
    suspend fun syncWithDevice(device: DeviceInfo): SyncResult
}

/**
 * 设备间同步数据模型
 */
data class DeviceSyncData(
    val deviceId: String,
    val deviceName: String,
    val syncTimestamp: Long,
    val entities: List<SyncEntity>
)

/**
 * 同步实体
 */
data class SyncEntity(
    val entityType: String,
    val entityId: String,
    val operation: String, // "CREATE", "UPDATE", "DELETE"
    val data: String, // JSON格式的数据
    val timestamp: Long
)

/**
 * 设备信息
 */
data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val deviceType: String, // "ANDROID", "IOS", "WEB"
    val connectionType: String, // "LAN", "BLUETOOTH", "NFC"
    val address: String, // 设备地址
    val signalStrength: Int // 信号强度 (0-100)
)
