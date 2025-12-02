package com.chronie.homemoney.core.error

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.WorkerThread
// 移除BuildConfig导入，将在代码中直接使用应用版本信息
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * 错误报告器类
 * 负责收集应用中的错误信息，保存到本地日志文件，并上报到后端
 */
@Singleton
class ErrorReporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // 使用MockErrorReportApi实例
    private val errorReportApi = MockErrorReportApi()
    
    // 内部日志管理功能，替代缺失的LogFileManager
    private fun appendToLogFile(text: String) {
        try {
            val logDir = File(context.filesDir, "logs")
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            val logFile = File(logDir, "error_log.txt")
            logFile.appendText(text + "\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    companion object {
        private const val TAG = "ErrorReporter"
        private const val MAX_QUEUE_SIZE = 10 // 最大错误队列大小
        private const val RETRY_COUNT = 3 // 最大重试次数
    }

    private val errorQueue = LinkedList<ErrorInfo>()
    private val handler = Handler(Looper.getMainLooper())
    private val mainThreadId = Looper.getMainLooper().thread.id
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * 初始化错误收集器
     */
    fun initialize() {
        // 获取当前默认的未捕获异常处理器
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        // 设置自定义的未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler(
            UncaughtExceptionHandler(defaultHandler, this)
        )

        Log.d(TAG, "Error reporter initialized")
    }

    /**
     * 公开的错误上报方法，供UncaughtExceptionHandler调用
     */
    @WorkerThread
    suspend fun reportErrorToServer(errorInfo: ErrorInfo) {
        reportErrorToServerInternal(errorInfo)
    }

    /**
     * 记录自定义错误
     */
    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val errorInfo = ErrorInfo(
            errorType = "CUSTOM_ERROR",
            message = "[$tag] $message",
            stackTrace = throwable?.let { getStackTraceString(it) } ?: getCurrentStackTrace(),
            threadName = Thread.currentThread().name,
            isMainThread = Thread.currentThread().id == mainThreadId,
            timestamp = System.currentTimeMillis(),
            deviceInfo = getDeviceInfo()
        )

        // 添加到队列
        synchronized(errorQueue) {
            if (errorQueue.size >= MAX_QUEUE_SIZE) {
                errorQueue.removeFirst() // 移除最旧的错误
            }
            errorQueue.add(errorInfo)
        }

        // 异步保存到本地
        Thread { saveErrorToLocal(errorInfo) }.start()

        // 在非主线程异步上报，使用协程
        if (!isMainThread()) {
            Thread {
                // 使用runBlocking包装协程调用
                kotlinx.coroutines.runBlocking {
                    reportErrorToServerInternal(errorInfo)
                }
            }.start()
        } else {
            handler.post {
                Thread {
                    // 使用runBlocking包装协程调用
                    kotlinx.coroutines.runBlocking {
                        reportErrorToServerInternal(errorInfo)
                    }
                }.start()
            }
        }
    }

    /**
     * 记录网络错误
     */
    fun logNetworkError(endpoint: String, errorCode: Int, message: String, throwable: Throwable? = null) {
        val errorInfo = ErrorInfo(
            errorType = "NETWORK_ERROR",
            message = "Network error at $endpoint: $errorCode - $message",
            stackTrace = throwable?.let { getStackTraceString(it) } ?: getCurrentStackTrace(),
            threadName = Thread.currentThread().name,
            isMainThread = Thread.currentThread().id == mainThreadId,
            timestamp = System.currentTimeMillis(),
            deviceInfo = getDeviceInfo(),
            additionalInfo = mapOf(
                "endpoint" to endpoint,
                "errorCode" to errorCode.toString() // 保持String类型
            )
        )

        // 添加到队列
        synchronized(errorQueue) {
            if (errorQueue.size >= MAX_QUEUE_SIZE) {
                errorQueue.removeFirst()
            }
            errorQueue.add(errorInfo)
        }

        // 异步保存到本地和上报，使用协程
        Thread { 
            saveErrorToLocal(errorInfo)
            // 使用runBlocking包装协程调用
            kotlinx.coroutines.runBlocking {
                reportErrorToServerInternal(errorInfo)
            }
        }.start()
    }

    /**
     * 保存错误到本地文件
     */
    @WorkerThread
    fun saveErrorToLocal(errorInfo: ErrorInfo) {
        try {
            val errorJson = convertErrorToJson(errorInfo)
            appendToLogFile(errorJson) // 使用内部方法替代logFileManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save error to local file", e)
        }
    }

    /**
     * 上报错误到服务器的内部实现方法
     */
    @WorkerThread
    private suspend fun reportErrorToServerInternal(errorInfo: ErrorInfo) {
        // 移除DEBUG检查，始终尝试上报错误
        Log.d(TAG, "Preparing to report error: ${errorInfo.message}")

        var retryCount = 0
        var success = false

        while (retryCount < RETRY_COUNT && !success) {
            try {
                val result = errorReportApi.reportError(
                    ErrorReportRequest(
                        errorType = errorInfo.errorType,
                        message = errorInfo.message,
                        stackTrace = errorInfo.stackTrace,
                        timestamp = errorInfo.timestamp,
                        deviceInfo = errorInfo.deviceInfo,
                        appVersion = "1.0.0", // 临时版本号
                        appBuild = "1", // 临时构建号,
                        additionalInfo = errorInfo.additionalInfo
                    )
                )
                
                success = result.isSuccessful
                if (success) {
                    Log.d(TAG, "Error reported successfully")
                } else {
                    Log.e(TAG, "Failed to report error, response code: ${result.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception when reporting error, retry $retryCount", e)
            } finally {
                retryCount++
                if (!success && retryCount < RETRY_COUNT) {
                    Thread.sleep(1000L * retryCount) // 指数退避策略，转换为Long类型
                }
            }
        }
    }

    /**
     * 获取设备信息
     */
    private fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "deviceModel" to Build.MODEL,
            "manufacturer" to Build.MANUFACTURER,
            "osVersion" to Build.VERSION.RELEASE,
            "sdkVersion" to Build.VERSION.SDK_INT.toString(),
            "deviceLanguage" to Locale.getDefault().language,
            "timeZone" to TimeZone.getDefault().id
        )
    }

    /**
     * 获取堆栈跟踪字符串
     */
    private fun getStackTraceString(throwable: Throwable): String {
        return Log.getStackTraceString(throwable)
    }

    /**
     * 获取当前线程的堆栈跟踪
     */
    private fun getCurrentStackTrace(): String {
        return Thread.currentThread().stackTrace.joinToString("\n") { it.toString() }
    }

    /**
     * 将错误信息转换为JSON字符串
     */
    private fun convertErrorToJson(errorInfo: ErrorInfo): String {
        val timestampStr = dateFormat.format(Date(errorInfo.timestamp))
        val deviceInfoStr = errorInfo.deviceInfo.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        val additionalInfoStr = errorInfo.additionalInfo?.entries?.joinToString(", ") { "${it.key}: ${it.value}" }

        return "[${timestampStr}] ${errorInfo.errorType}: ${errorInfo.message}\n" +
               "Thread: ${errorInfo.threadName} (${if (errorInfo.isMainThread) "Main" else "Worker"})\n" +
               "Device: $deviceInfoStr\n" +
               (additionalInfoStr?.let { "Additional info: $it\n" } ?: "") +
               "Stack trace:\n${errorInfo.stackTrace}\n"
    }

    /**
     * 检查当前是否在主线程
     */
    private fun isMainThread(): Boolean {
        return Thread.currentThread().id == mainThreadId
    }

    /**
     * 错误信息数据类
     */
    data class ErrorInfo(
        val errorType: String,
        val message: String,
        val stackTrace: String,
        val threadName: String,
        val isMainThread: Boolean,
        val timestamp: Long,
        val deviceInfo: Map<String, String>,
        val additionalInfo: Map<String, String>? = null
    )

    // 移除内部定义的ErrorReportRequest，使用ErrorReportApi中定义的类型
    
    /**
     * 未捕获异常处理器
     * 负责处理应用中未被捕获的异常
     */
    private class UncaughtExceptionHandler(
        private val defaultHandler: Thread.UncaughtExceptionHandler,
        private val errorReporter: ErrorReporter
    ) : Thread.UncaughtExceptionHandler {
        
        override fun uncaughtException(thread: Thread, ex: Throwable) {
            // 创建错误信息
            val errorInfo = ErrorInfo(
                errorType = "UNCAUGHT_EXCEPTION",
                message = ex.message ?: "Unknown error",
                stackTrace = Log.getStackTraceString(ex),
                threadName = thread.name,
                isMainThread = thread.id == errorReporter.mainThreadId,
                timestamp = System.currentTimeMillis(),
                deviceInfo = errorReporter.getDeviceInfo()
            )
            
            // 保存到本地并上报
            try {
                errorReporter.saveErrorToLocal(errorInfo)
                // 使用runBlocking包装协程调用
                kotlinx.coroutines.runBlocking {
                    errorReporter.reportErrorToServer(errorInfo)
                }
            } catch (e: Exception) {
                // 如果保存或上报失败，至少记录日志
                Log.e("UncaughtExceptionHandler", "Failed to handle uncaught exception", e)
            }
            
            // 调用默认处理器
            defaultHandler.uncaughtException(thread, ex)
        }
    }
}