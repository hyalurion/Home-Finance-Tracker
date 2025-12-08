package com.chronie.homemoney.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.R
import com.chronie.homemoney.core.common.DeveloperMode
import com.chronie.homemoney.core.common.Language
import com.chronie.homemoney.core.common.LanguageManager
import com.chronie.homemoney.data.sync.SyncScheduler
import com.chronie.homemoney.domain.model.SyncStatus
import com.chronie.homemoney.domain.sync.SyncManager
import com.chronie.homemoney.domain.usecase.ExportExpensesUseCase
import com.chronie.homemoney.domain.usecase.ImportExpensesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val languageManager: LanguageManager,
    private val developerMode: DeveloperMode,
    private val syncManager: SyncManager,
    private val syncScheduler: SyncScheduler,
    private val exportExpensesUseCase: ExportExpensesUseCase,
    private val importExpensesUseCase: ImportExpensesUseCase,
    val checkLoginStatusUseCase: com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase,
    val checkMembershipUseCase: com.chronie.homemoney.domain.usecase.CheckMembershipUseCase,
    private val logoutUseCase: com.chronie.homemoney.domain.usecase.LogoutUseCase,
    private val getMembershipStatusUseCase: com.chronie.homemoney.domain.usecase.GetMembershipStatusUseCase,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    // 动态颜色开关状态
    private val _useDynamicColor = MutableStateFlow(true)
    val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

    // 手动选择的主色调
    private val _primaryColor = MutableStateFlow(0xFF6750A4.toInt()) // 默认紫色
    val primaryColor: StateFlow<Int> = _primaryColor.asStateFlow()

    val currentLanguage: StateFlow<Language> = languageManager.currentLanguage
    
    val isDeveloperMode: Flow<Boolean> = developerMode.isDeveloperModeEnabled
    
    private val _aiApiKey = MutableStateFlow("")
    val aiApiKey: StateFlow<String> = _aiApiKey.asStateFlow()
    
    val syncStatus: StateFlow<SyncStatus> = syncManager.observeSyncStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncStatus.IDLE
        )
    
    private val _lastSyncTime = MutableStateFlow<String?>(null)
    val lastSyncTime: StateFlow<String?> = _lastSyncTime.asStateFlow()
    
    private val _pendingSyncCount = MutableStateFlow(0)
    val pendingSyncCount: StateFlow<Int> = _pendingSyncCount.asStateFlow()
    
    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()
    
    private val _exportInProgress = MutableStateFlow(false)
    val exportInProgress: StateFlow<Boolean> = _exportInProgress.asStateFlow()
    
    private val _importInProgress = MutableStateFlow(false)
    val importInProgress: StateFlow<Boolean> = _importInProgress.asStateFlow()
    
    private val _currentUsername = MutableStateFlow<String?>(null)
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()
    
    private val _logoutEvent = MutableSharedFlow<Unit>()
    val logoutEvent: SharedFlow<Unit> = _logoutEvent.asSharedFlow()
    
    private val _membershipStatus = MutableStateFlow<com.chronie.homemoney.domain.model.SubscriptionStatus?>(null)
    val membershipStatus: StateFlow<com.chronie.homemoney.domain.model.SubscriptionStatus?> = _membershipStatus.asStateFlow()
    
    private val _membershipLoading = MutableStateFlow(false)
    val membershipLoading: StateFlow<Boolean> = _membershipLoading.asStateFlow()

    init {
        loadSyncInfo()
        loadAIApiKey()
        loadCurrentUser()
        loadMembershipStatus()
        loadDynamicColorSettings()
    }
    
    private fun loadCurrentUser() {
        viewModelScope.launch {
            _currentUsername.value = checkLoginStatusUseCase.getUsername()
        }
    }
    
    private fun loadMembershipStatus() {
        viewModelScope.launch {
            val username = checkLoginStatusUseCase.getUsername()
            if (username != null) {
                _membershipLoading.value = true
                try {
                    val result = getMembershipStatusUseCase(username, forceRefresh = false)
                    if (result.isSuccess) {
                        _membershipStatus.value = result.getOrNull()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SettingsViewModel", "Failed to load membership status", e)
                } finally {
                    _membershipLoading.value = false
                }
            }
        }
    }
    
    fun refreshMembershipStatus() {
        viewModelScope.launch {
            val username = checkLoginStatusUseCase.getUsername()
            if (username != null) {
                _membershipLoading.value = true
                try {
                    val result = getMembershipStatusUseCase(username, forceRefresh = true)
                    if (result.isSuccess) {
                        _membershipStatus.value = result.getOrNull()
                        _syncMessage.value = context.getString(R.string.membership_status_refreshed)
                    } else {
                        _syncMessage.value = context.getString(R.string.membership_status_refresh_failed)
                    }
                } catch (e: Exception) {
                    _syncMessage.value = context.getString(R.string.membership_status_refresh_failed)
                } finally {
                    _membershipLoading.value = false
                }
            }
        }
    }
    
    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _currentUsername.value = null
            _membershipStatus.value = null
            _logoutEvent.emit(Unit)
        }
    }

    fun setLanguage(language: Language) {
        languageManager.setLanguage(language)
    }
    
    fun toggleDeveloperMode() {
        viewModelScope.launch {
            developerMode.toggleDeveloperMode()
        }
    }
    
    fun manualSync() {
        viewModelScope.launch {
            try {
                _syncMessage.value = null
                val result = syncScheduler.manualSync()
                
                if (result.isSuccess) {
                    val syncResult = result.getOrNull()
                    if (syncResult?.success == true) {
                        _syncMessage.value = "同步成功"
                        loadSyncInfo()
                    } else {
                        _syncMessage.value = "同步失败: ${syncResult?.error}"
                    }
                } else {
                    _syncMessage.value = "同步失败: ${result.exceptionOrNull()?.message}"
                }
            } catch (e: Exception) {
                _syncMessage.value = "同步失败: ${e.message}"
            }
        }
    }
    
    fun clearSyncMessage() {
        _syncMessage.value = null
    }
    
    fun setAIApiKey(apiKey: String) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("ai_settings", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("siliconflow_api_key", apiKey).apply()
            _aiApiKey.value = apiKey
            _syncMessage.value = context.getString(R.string.settings_ai_api_key_saved)
        }
    }
    
    private fun loadAIApiKey() {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("ai_settings", android.content.Context.MODE_PRIVATE)
            _aiApiKey.value = prefs.getString("siliconflow_api_key", "") ?: ""
        }
    }
    
    private fun loadSyncInfo() {
        viewModelScope.launch {
            // 加载最后同步时间
            val lastSync = syncManager.getLastSyncTime()
            _lastSyncTime.value = if (lastSync != null) {
                formatTimestamp(lastSync)
            } else {
                null
            }
            
            // 加载待同步项数量
            _pendingSyncCount.value = syncManager.getPendingSyncCount()
        }
    }

    // 加载动态颜色设置
    private fun loadDynamicColorSettings() {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("theme_settings", android.content.Context.MODE_PRIVATE)
            _useDynamicColor.value = prefs.getBoolean("use_dynamic_color", true)
            _primaryColor.value = prefs.getInt("primary_color", 0xFF6750A4.toInt())
        }
    }

    // 切换动态颜色开关
    fun toggleDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("theme_settings", android.content.Context.MODE_PRIVATE)
            prefs.edit().putBoolean("use_dynamic_color", enabled).apply()
            _useDynamicColor.value = enabled
            _syncMessage.value = context.getString(if (enabled) R.string.dynamic_color_enabled else R.string.dynamic_color_disabled)
        }
    }

    // 设置手动颜色
    fun setPrimaryColor(color: Int) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("theme_settings", android.content.Context.MODE_PRIVATE)
            prefs.edit().putInt("primary_color", color).apply()
            _primaryColor.value = color
            _syncMessage.value = context.getString(R.string.primary_color_updated)
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun exportExpenses(startDate: LocalDate? = null, endDate: LocalDate? = null) {
        viewModelScope.launch {
            try {
                _exportInProgress.value = true
                _syncMessage.value = context.getString(R.string.export_in_progress)
                
                val result = exportExpensesUseCase(startDate, endDate)
                
                if (result.isSuccess) {
                    val filePath = result.getOrNull()
                    _syncMessage.value = context.getString(R.string.export_success, filePath)
                } else {
                    _syncMessage.value = context.getString(
                        R.string.export_failed,
                        result.exceptionOrNull()?.message ?: "Unknown error"
                    )
                }
            } catch (e: Exception) {
                _syncMessage.value = context.getString(R.string.export_failed, e.message)
            } finally {
                _exportInProgress.value = false
            }
        }
    }
    
    fun importExpenses(uri: Uri) {
        viewModelScope.launch {
            try {
                _importInProgress.value = true
                _syncMessage.value = context.getString(R.string.import_in_progress)
                
                val result = importExpensesUseCase(uri)
                
                if (result.isSuccess) {
                    val importResult = result.getOrNull()!!
                    _syncMessage.value = context.getString(
                        R.string.import_success,
                        importResult.successCount
                    )
                    
                    // 如果有失败的记录，显示错误信息
                    if (importResult.failedCount > 0) {
                        android.util.Log.w("ImportExpenses", "Failed to import ${importResult.failedCount} records")
                        importResult.errors.forEach { error ->
                            android.util.Log.w("ImportExpenses", error)
                        }
                    }
                } else {
                    _syncMessage.value = context.getString(
                        R.string.import_failed,
                        result.exceptionOrNull()?.message ?: "Unknown error"
                    )
                }
            } catch (e: Exception) {
                _syncMessage.value = context.getString(R.string.import_failed, e.message)
            } finally {
                _importInProgress.value = false
            }
        }
    }
}
