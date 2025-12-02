package com.chronie.homemoney.core.error

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.runBlocking

/**
 * 自定义未捕获异常处理器
 * 捕获应用中所有未被捕获的异常，记录到日志文件并上报
 */
class UncaughtExceptionHandler(
    private val defaultHandler: Thread.UncaughtExceptionHandler,
    private val errorReporter: ErrorReporter
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "UncaughtExceptionHandler"
        
        // 在应用程序初始化时设置全局异常处理器
        fun init(context: Context, errorReporter: ErrorReporter) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            val uncaughtExceptionHandler = UncaughtExceptionHandler(
                defaultHandler,
                errorReporter
            )
            Thread.setDefaultUncaughtExceptionHandler(uncaughtExceptionHandler)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val mainThreadId = Looper.getMainLooper().thread.id
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // 创建错误信息
            val errorInfo = createErrorInfo(thread, throwable)
            
            // 保存到本地日志文件
            saveErrorToLocal(errorInfo)
            
            // 尝试上报错误到服务器
            reportErrorToServer(errorInfo)
            
        } catch (e: Exception) {
            // 即使在处理错误时发生异常，也要确保原始异常能被默认处理器处理
            Log.e(TAG, "Failed to handle uncaught exception", e)
        } finally {
            // 调用默认的未捕获异常处理器，确保应用能正确终止
            defaultHandler.uncaughtException(thread, throwable)
        }
    }

    /**
     * 创建错误信息
     */
    private fun createErrorInfo(thread: Thread, throwable: Throwable): ErrorReporter.ErrorInfo {
        return ErrorReporter.ErrorInfo(
            errorType = "UNCAUGHT_EXCEPTION",
            message = throwable.message ?: "Unknown error",
            stackTrace = getStackTraceString(throwable),
            threadName = thread.name,
            isMainThread = thread.id == mainThreadId,
            timestamp = System.currentTimeMillis(),
            deviceInfo = getDeviceInfo()
        )
    }

    /**
     * 获取堆栈跟踪字符串
     */
    private fun getStackTraceString(throwable: Throwable): String {
        return Log.getStackTraceString(throwable)
    }

    /**
     * 获取设备信息
     */
    private fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "deviceModel" to android.os.Build.MODEL,
            "manufacturer" to android.os.Build.MANUFACTURER,
            "osVersion" to android.os.Build.VERSION.RELEASE,
            "sdkVersion" to android.os.Build.VERSION.SDK_INT.toString(),
            "deviceLanguage" to Locale.getDefault().language,
            "timeZone" to TimeZone.getDefault().id
        )
    }

    /**
     * 保存错误到本地
     * 直接调用ErrorReporter中的方法
     */
    private fun saveErrorToLocal(errorInfo: ErrorReporter.ErrorInfo) {
        try {
            // 由于ErrorReporter中的saveErrorToLocal是suspend函数，需要在协程中调用
            runBlocking {
                errorReporter.saveErrorToLocal(errorInfo)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save error to local file", e)
        }
    }

    /**
     * 上报错误到服务器
     * 使用协程包装suspend函数调用
     */
    private fun reportErrorToServer(errorInfo: ErrorReporter.ErrorInfo) {
        // 使用线程上报，避免阻塞当前线程
        if (Thread.currentThread().id != mainThreadId) {
            // 如果不在主线程，直接上报
            Thread {
                runBlocking {
                    errorReporter.reportErrorToServer(errorInfo)
                }
            }.start()
        } else {
            // 如果在主线程，使用Handler切换到工作线程上报
            handler.post {
                Thread {
                    runBlocking {
                        errorReporter.reportErrorToServer(errorInfo)
                    }
                }.start()
            }
        }
    }

    /**
     * 将错误信息转换为JSON字符串
     */
    private fun convertErrorToJson(errorInfo: ErrorReporter.ErrorInfo): String {
        val timestampStr = dateFormat.format(Date(errorInfo.timestamp))
        val deviceInfoStr = errorInfo.deviceInfo.entries.joinToString(", ") { "${it.key}: ${it.value}" }
        val additionalInfoStr = errorInfo.additionalInfo?.entries?.joinToString(", ") { "${it.key}: ${it.value}" }

        return "[${timestampStr}] ${errorInfo.errorType}: ${errorInfo.message}\n" +
               "Thread: ${errorInfo.threadName} (${if (errorInfo.isMainThread) "Main" else "Worker"})\n" +
               "Device: $deviceInfoStr\n" +
               (additionalInfoStr?.let { "Additional info: $it\n" } ?: "") +
               "Stack trace:\n${errorInfo.stackTrace}\n"
    }
}