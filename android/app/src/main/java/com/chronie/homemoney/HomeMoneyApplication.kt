package com.chronie.homemoney

import android.app.Application
import android.util.Log
import com.chronie.homemoney.core.error.ErrorReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HomeMoneyApplication : Application() {

    @Inject
    lateinit var errorReporter: ErrorReporter

    override fun onCreate() {
        super.onCreate()
        
        // 初始化错误收集系统
        try {
            errorReporter.initialize()
            Log.d("HomeMoneyApplication", "Error reporting system initialized")
        } catch (e: Exception) {
            // 即使错误收集系统初始化失败，也要确保应用能正常运行
            Log.e("HomeMoneyApplication", "Failed to initialize error reporting system", e)
        }
    }
}
