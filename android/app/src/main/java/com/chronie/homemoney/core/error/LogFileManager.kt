package com.chronie.homemoney.core.error

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.annotation.WorkerThread
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 日志文件管理器
 * 负责错误日志文件的创建、写入和清理
 */
@Singleton
class LogFileManager @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "LogFileManager"
        private const val LOG_DIR_NAME = "error_logs"
        private const val LOG_FILE_PREFIX = "error_"
        private const val LOG_FILE_EXTENSION = ".log"
        private const val MAX_LOG_FILE_SIZE = 5 * 1024 * 1024L // 5MB
        private const val MAX_LOG_FILES = 3 // 最多保留3个日志文件
    }

    private val logDir: File by lazy {
        val dir = if (Environment.MEDIA_MOUNTED == Environment.getExternalStorageState()) {
            // 优先使用外部存储
            File(context.getExternalFilesDir(null), LOG_DIR_NAME)
        } else {
            // 外部存储不可用，使用内部存储
            File(context.filesDir, LOG_DIR_NAME)
        }
        
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * 获取当前日志文件
     * 如果当前日志文件超过大小限制，则创建新文件
     */
    private fun getCurrentLogFile(): File {
        synchronized(this) {
            // 获取最新的日志文件
            val logFiles = logDir.listFiles()?.filter {
                it.name.startsWith(LOG_FILE_PREFIX) && it.name.endsWith(LOG_FILE_EXTENSION)
            }?.sortedByDescending { it.lastModified() }
            
            val currentFile = logFiles?.firstOrNull()
            
            if (currentFile != null && currentFile.length() < MAX_LOG_FILE_SIZE) {
                // 文件存在且未超过大小限制
                return currentFile
            }
            
            // 创建新的日志文件
            val newFileName = LOG_FILE_PREFIX + dateFormat.format(Date()) + LOG_FILE_EXTENSION
            val newFile = File(logDir, newFileName)
            
            // 清理旧的日志文件
            cleanupOldLogFiles()
            
            return newFile
        }
    }

    /**
     * 将错误信息追加到日志文件
     */
    @WorkerThread
    fun appendToLogFile(content: String) {
        try {
            val file = getCurrentLogFile()
            file.appendText(content + "\n\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }

    /**
     * 清理旧的日志文件
     * 只保留最新的MAX_LOG_FILES个文件
     */
    private fun cleanupOldLogFiles() {
        try {
            val logFiles = logDir.listFiles()?.filter {
                it.name.startsWith(LOG_FILE_PREFIX) && it.name.endsWith(LOG_FILE_EXTENSION)
            }?.sortedByDescending { it.lastModified() }
            
            if (logFiles != null && logFiles.size > MAX_LOG_FILES) {
                // 删除多余的旧文件
                for (i in MAX_LOG_FILES until logFiles.size) {
                    val fileToDelete = logFiles[i]
                    if (fileToDelete.delete()) {
                        Log.d(TAG, "Deleted old log file: ${fileToDelete.name}")
                    } else {
                        Log.e(TAG, "Failed to delete old log file: ${fileToDelete.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old log files", e)
        }
    }

    /**
     * 获取所有日志文件的列表
     */
    fun getLogFiles(): List<File> {
        return logDir.listFiles()?.filter {
            it.name.startsWith(LOG_FILE_PREFIX) && it.name.endsWith(LOG_FILE_EXTENSION)
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * 读取指定日志文件的内容
     */
    @WorkerThread
    fun readLogFile(file: File): String {
        return try {
            if (file.exists() && file.length() > 0) {
                file.readText()
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read log file: ${file.name}", e)
            ""
        }
    }

    /**
     * 删除所有日志文件
     */
    fun deleteAllLogFiles() {
        try {
            val logFiles = getLogFiles()
            for (file in logFiles) {
                if (file.delete()) {
                    Log.d(TAG, "Deleted log file: ${file.name}")
                } else {
                    Log.e(TAG, "Failed to delete log file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting all log files", e)
        }
    }

    /**
     * 获取日志文件总大小
     */
    fun getTotalLogSize(): Long {
        return try {
            val logFiles = getLogFiles()
            logFiles.sumOf { it.length() }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating log size", e)
            0
        }
    }
}