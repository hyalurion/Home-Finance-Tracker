package com.chronie.homemoney.data.sync

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.*
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Parcelable
import android.util.Log
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.domain.sync.DeviceInfo
import com.chronie.homemoney.domain.sync.DeviceSyncData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

/**
 * NFC设备间同步管理器
 */
class NfcDeviceSyncManager(
    expenseDao: ExpenseDao,
    gson: Gson,
    private val context: Context,
    private val nfcAdapter: NfcAdapter?
) : BaseDeviceSyncManager(expenseDao, gson) {
    
    private val MIME_TYPE = "application/com.chronie.homemoney"
    private val _nfcIntent = MutableStateFlow<Intent?>(null)
    private val _deviceInfo = MutableStateFlow<DeviceInfo?>(null)
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null
    
    init {
        setupNfc()
    }
    
    private fun setupNfc() {
        if (nfcAdapter == null) {
            return
        }
        
        // 创建IntentFilter，用于接收NFC标签的Intent
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        try {
            ndef.addDataType(MIME_TYPE)
            intentFiltersArray = arrayOf(ndef)
        } catch (e: IntentFilter.MalformedMimeTypeException) {
            Log.e(TAG, "Malformed MIME type", e)
        }
        
        // 设置支持的NFC技术
        techListsArray = arrayOf(
            arrayOf(Ndef::class.java.name),
            arrayOf(NdefFormatable::class.java.name)
        )
        
        // 仅当context是Activity时才创建PendingIntent
        if (context is android.app.Activity) {
            pendingIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, context::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
    
    override fun searchDevices(): Flow<DeviceInfo> = flow {
        Log.d(TAG, "NFC device search - waiting for NFC tag to be detected")
        
        // 检查NFC是否可用
        if (nfcAdapter == null) {
            Log.e(TAG, "NFC is not available")
            return@flow
        }
        
        if (!nfcAdapter.isEnabled) {
            Log.e(TAG, "NFC is not enabled")
            return@flow
        }
        
        // 收集NFC意图流 - 前台调度由Activity处理
        _nfcIntent.collect {
            if (it != null) {
                val deviceInfo = processNfcIntent(it)
                if (deviceInfo != null) {
                    emit(deviceInfo)
                }
            }
        }
    }
    
    override suspend fun connect(device: DeviceInfo): Boolean {
        Log.d(TAG, "Connecting to NFC device: ${device.deviceName}")
        
        if (nfcAdapter == null || !nfcAdapter.isEnabled) {
            return false
        }
        
        isConnected = true
        currentDevice = device
        return true
    }
    
    override suspend fun disconnect(): Boolean {
        Log.d(TAG, "Disconnecting from NFC device")
        
        // 调用disableForegroundDispatch关闭NFC前台调度（仅当context是Activity时）
        if (nfcAdapter != null && context is android.app.Activity) {
            nfcAdapter.disableForegroundDispatch(context)
        }
        
        isConnected = false
        currentDevice = null
        return true
    }
    
    override suspend fun sendData(data: DeviceSyncData): Boolean {
        Log.d(TAG, "Sending data via NFC")
        
        if (nfcAdapter == null || currentDevice == null) {
            return false
        }
        
        val intent = _nfcIntent.value ?: return false
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        
        return try {
            writeNdefMessage(tag, data)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write NFC data", e)
            false
        }
    }
    
    override suspend fun receiveData(): DeviceSyncData? {
        Log.d(TAG, "Receiving data via NFC")
        
        if (nfcAdapter == null || currentDevice == null) {
            return null
        }
        
        val intent = _nfcIntent.value ?: return null
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        
        return try {
            readNdefMessage(tag)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read NFC data", e)
            null
        }
    }
    
    /**
     * 处理NFC意图
     */
    fun handleNfcIntent(intent: Intent): DeviceInfo? {
        Log.d(TAG, "Handling NFC intent: ${intent.action}")
        
        if (intent.action != NfcAdapter.ACTION_NDEF_DISCOVERED) {
            return null
        }
        
        _nfcIntent.value = intent
        
        // 读取设备信息
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        val ndef = Ndef.get(tag)
        
        try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            
            if (ndefMessage != null) {
                for (record in ndefMessage.records) {
                    if (record.tnf == NdefRecord.TNF_MIME_MEDIA && record.type.contentEquals(MIME_TYPE.toByteArray())) {
                        val payload = String(record.payload, Charset.forName("UTF-8"))
                        // 假设payload包含设备信息
                        val deviceInfo = DeviceInfo(
                            deviceId = UUID.randomUUID().toString(),
                            deviceName = "NFC Device",
                            deviceType = "ANDROID",
                            connectionType = "NFC",
                            address = tag?.id?.contentToString() ?: "",
                            signalStrength = 100
                        )
                        
                        _deviceInfo.value = deviceInfo
                        ndef.close()
                        return deviceInfo
                    }
                }
            }
            
            ndef.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error handling NFC intent", e)
        }
        
        return null
    }
    
    /**
     * 读取NFC标签中的数据
     */
    private suspend fun readNdefMessage(tag: Tag?): DeviceSyncData? {
        return withContext(Dispatchers.IO) {
            if (tag == null) return@withContext null
            
            val ndef = Ndef.get(tag)
            if (ndef == null) return@withContext null
            
            try {
                ndef.connect()
                val ndefMessage = ndef.ndefMessage
                
                if (ndefMessage != null) {
                    for (record in ndefMessage.records) {
                        if (record.tnf == NdefRecord.TNF_MIME_MEDIA && record.type.contentEquals(MIME_TYPE.toByteArray())) {
                            val payload = String(record.payload, Charset.forName("UTF-8"))
                            return@withContext gson.fromJson(payload, DeviceSyncData::class.java)
                        }
                    }
                }
                
                ndef.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error reading NFC tag", e)
            }
            
            return@withContext null
        }
    }
    
    /**
     * 向NFC标签写入数据
     */
    private suspend fun writeNdefMessage(tag: Tag?, data: DeviceSyncData): Boolean {
        return withContext(Dispatchers.IO) {
            if (tag == null) return@withContext false
            
            val jsonData = gson.toJson(data)
            val ndefMessage = createNdefMessage(jsonData)
            
            try {
                val ndef = Ndef.get(tag)
                if (ndef != null) {
                    ndef.connect()
                    if (!ndef.isWritable) {
                        Log.e(TAG, "NFC tag is not writable")
                        return@withContext false
                    }
                    ndef.writeNdefMessage(ndefMessage)
                    ndef.close()
                    return@withContext true
                } else {
                    val formatable = NdefFormatable.get(tag)
                    if (formatable != null) {
                        formatable.connect()
                        formatable.format(ndefMessage)
                        formatable.close()
                        return@withContext true
                    } else {
                        Log.e(TAG, "NFC tag is not NDEF formatable")
                        return@withContext false
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error writing NFC tag", e)
                return@withContext false
            } catch (e: FormatException) {
                Log.e(TAG, "Error formatting NFC tag", e)
                return@withContext false
            }
        }
    }
    
    /**
     * 创建NDEF消息
     */
    private fun createNdefMessage(content: String): NdefMessage {
        val textBytes = content.toByteArray(Charset.forName("UTF-8"))
        val langBytes = "en".toByteArray(Charset.forName("US-ASCII"))
        val langLength = langBytes.size
        val textLength = textBytes.size
        val payload = ByteArray(1 + langLength + textLength)
        
        // 设置状态字节
        payload[0] = langLength.toByte()
        
        // 复制语言代码和文本内容
        System.arraycopy(langBytes, 0, payload, 1, langLength)
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength)
        
        val record = NdefRecord(NdefRecord.TNF_MIME_MEDIA, MIME_TYPE.toByteArray(), ByteArray(0), payload)
        return NdefMessage(arrayOf(record))
    }
    
    /**
     * 处理NFC意图并返回设备信息
     */
    private fun processNfcIntent(intent: Intent): DeviceInfo? {
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        
        return if (tag != null) {
            DeviceInfo(
                deviceId = tag.id.contentToString(),
                deviceName = "NFC Device",
                deviceType = "ANDROID",
                connectionType = "NFC",
                address = tag?.id?.contentToString() ?: "",
                signalStrength = 100
            )
        } else {
            null
        }
    }
}