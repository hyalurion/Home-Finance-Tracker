package com.chronie.homemoney.ui.settings

import android.annotation.SuppressLint
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.R
import com.chronie.homemoney.core.common.DeveloperMode
import com.chronie.homemoney.core.common.Language
import com.chronie.homemoney.core.common.LanguageManager
import com.chronie.homemoney.data.local.ServerConfigManager
import com.chronie.homemoney.data.sync.SyncScheduler
import com.chronie.homemoney.domain.model.SyncStatus
import com.chronie.homemoney.domain.sync.DeviceInfo
import com.chronie.homemoney.domain.sync.SyncManager
import com.chronie.homemoney.domain.usecase.ExportExpensesUseCase
import com.chronie.homemoney.domain.usecase.ImportExpensesUseCase
import com.chronie.homemoney.ui.theme.PaletteStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.content.edit

/**
 * ViewModel for the Settings screen.
 *
 * The most feature-rich ViewModel in the app — manages:
 * - Language switching
 * - Developer mode toggle
 * - Dynamic color / palette theme settings
 * - Manual and device-to-device LAN sync
 * - On-device AI model management (download/delete)
 * - Expense export/import
 * - Avatar management
 * - Device name configuration
 * - User logout
 *
 * Implements [SyncRequestCallback] to handle incoming sync requests
 * from other devices on the local network.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val languageManager: LanguageManager,
    private val currencyManager: com.chronie.homemoney.core.common.CurrencyManager,
    private val developerMode: DeveloperMode,
    private val syncManager: SyncManager,
    private val syncScheduler: SyncScheduler,
    private val exportExpensesUseCase: ExportExpensesUseCase,
    private val importExpensesUseCase: ImportExpensesUseCase,
    val checkLoginStatusUseCase: com.chronie.homemoney.domain.usecase.CheckLoginStatusUseCase,
    private val logoutUseCase: com.chronie.homemoney.domain.usecase.LogoutUseCase,
    private val memberRepository: com.chronie.homemoney.domain.repository.MemberRepository,
    private val preferencesManager: com.chronie.homemoney.data.local.PreferencesManager,
    private val serverConfigManager: com.chronie.homemoney.data.local.ServerConfigManager,
    private val serverConnectionTester: com.chronie.homemoney.service.ServerConnectionTester,
    private val aiModelManager: com.chronie.homemoney.data.vlm.OnDeviceModelManager,
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel(), com.chronie.homemoney.domain.sync.SyncRequestCallback {

    // Dynamic color switch state
    private val _useDynamicColor = MutableStateFlow(true)
    val useDynamicColor: StateFlow<Boolean> = _useDynamicColor.asStateFlow()

    // Manual primary color selection (0 = use Miuix default)
    private val _primaryColor = MutableStateFlow(0)

    // Palette style selection
    private val _paletteStyle = MutableStateFlow(PaletteStyle.Expressive)

    val currentLanguage: StateFlow<Language> = languageManager.currentLanguage

    /** Pinned display currency (`null` = follow the app language). */
    val selectedCurrency: StateFlow<com.chronie.homemoney.core.common.CurrencyManager.CurrencyOption?> =
        currencyManager.selectedOption

    /** Pins [option] as the display currency, or `null` to follow the language. */
    fun setCurrency(option: com.chronie.homemoney.core.common.CurrencyManager.CurrencyOption?) {
        currencyManager.setCurrency(option)
    }

    val isDeveloperMode: Flow<Boolean> = developerMode.isDeveloperModeEnabled

    // On-device AI model lifecycle, surfaced to the settings UI.
    val aiModelState: StateFlow<com.chronie.homemoney.data.vlm.OnDeviceModelManager.ModelState> =
        aiModelManager.state

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

    // Avatar status
    private val _avatar = MutableStateFlow<String?>(null)
    val avatar: StateFlow<String?> = _avatar.asStateFlow()

    private val _avatarLoading = MutableStateFlow(false)
    val avatarLoading: StateFlow<Boolean> = _avatarLoading.asStateFlow()

    // Device name status
    private val _deviceName = MutableStateFlow("")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    // Sync progress status
    private val _syncProgress = MutableStateFlow(0f)
    val syncProgress: StateFlow<Float> = _syncProgress.asStateFlow()

    private val _syncProgressMessage = MutableStateFlow("")
    val syncProgressMessage: StateFlow<String> = _syncProgressMessage.asStateFlow()

    private val _showSyncProgress = MutableStateFlow(false)
    val showSyncProgress: StateFlow<Boolean> = _showSyncProgress.asStateFlow()

    // Sync request confirmation status
    private val _pendingSyncRequest = MutableStateFlow<DeviceInfo?>(null)
    val pendingSyncRequest: StateFlow<DeviceInfo?> = _pendingSyncRequest.asStateFlow()

    private val _showSyncRequestDialog = MutableStateFlow(false)
    val showSyncRequestDialog: StateFlow<Boolean> = _showSyncRequestDialog.asStateFlow()

    // Server-side passive sync progress (searcher)
    val serverSyncProgress: StateFlow<com.chronie.homemoney.domain.sync.SyncProgressInfo> =
        syncManager.getDeviceSyncManager().syncProgress

    // Incoming sync request (searcher)
    private val _incomingSyncRequest = MutableStateFlow<com.chronie.homemoney.domain.sync.SyncRequestInfo?>(null)
    val incomingSyncRequest: StateFlow<com.chronie.homemoney.domain.sync.SyncRequestInfo?> = _incomingSyncRequest.asStateFlow()

    // Sync request callback continuation
    private var syncRequestContinuation: kotlin.coroutines.Continuation<Boolean>? = null

    // Server address configuration
    val serverBaseUrl: StateFlow<String> = serverConfigManager.baseUrl

    val isUsingCustomServer: StateFlow<Boolean> = serverConfigManager.baseUrl
        .map { it != ServerConfigManager.DEFAULT_BASE_URL }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = serverConfigManager.isUsingCustomServer
        )

    private val _serverTestState = MutableStateFlow<ServerTestUiState>(ServerTestUiState.Idle)
    val serverTestState: StateFlow<ServerTestUiState> = _serverTestState.asStateFlow()

    init {
        loadSyncInfo()
        aiModelManager.refreshState()
        loadCurrentUser()
        loadDynamicColorSettings()
        loadAvatar()
        loadDeviceName()

        // Set sync request callback
        syncManager.getDeviceSyncManager().setSyncRequestCallback(this)
    }

    @SuppressLint("EmptySuperCall")
    override fun onCleared() {
        super.onCleared()
        // Clear sync request callback to prevent memory leak
        android.util.Log.d("SettingsViewModel", "Clearing syncRequestCallback")
        syncManager.getDeviceSyncManager().setSyncRequestCallback(null)
    }

    /**
     * Sync request callback implementation
     */
    override suspend fun onSyncRequest(requestInfo: com.chronie.homemoney.domain.sync.SyncRequestInfo): Boolean {
        android.util.Log.d("SettingsViewModel", "Received onSyncRequest from ${requestInfo.deviceName}")
        return suspendCancellableCoroutine { continuation ->
            syncRequestContinuation = continuation
            _incomingSyncRequest.value = requestInfo
        }
    }

    /**
     * Accept incoming sync request
     */
    fun acceptIncomingSyncRequest() {
        syncRequestContinuation?.resume(true)
        syncRequestContinuation = null
        _incomingSyncRequest.value = null
    }

    /**
     * Reject incoming sync request
     */
    fun rejectIncomingSyncRequest() {
        syncRequestContinuation?.resume(false)
        syncRequestContinuation = null
        _incomingSyncRequest.value = null
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            _currentUsername.value = checkLoginStatusUseCase.getUsername()
        }
    }

    private fun loadAvatar() {
        viewModelScope.launch {
            // First load avatar from local preferences
            val localAvatar = preferencesManager.getAvatar()
            _avatar.value = localAvatar

            // Then try to fetch latest avatar from backend
            fetchAvatarFromBackend()
        }
    }

    private suspend fun fetchAvatarFromBackend() {
        val username = checkLoginStatusUseCase.getUsername()
        if (username.isNullOrEmpty()) return

        _avatarLoading.value = true
        try {
            // Use memberRepository to fetch member info, including avatar
            val result = memberRepository.getMemberInfo(username)
            if (result.isSuccess) {
                val member = result.getOrNull()
                if (member != null && member.avatar != null) {
                    // Log avatar data prefix to check format, up to 50 characters
                    android.util.Log.d("SettingsViewModel", "Fetched avatar data: ${member.avatar.take(50)}...")
                    _avatar.value = member.avatar
                    preferencesManager.saveAvatar(member.avatar)
                }
            } else {
                android.util.Log.e("SettingsViewModel", "Failed to fetch avatar from backend: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            // Network request failed, use local avatar
            android.util.Log.e("SettingsViewModel", "Failed to fetch avatar from backend", e)
        } finally {
            _avatarLoading.value = false
        }
    }

    fun updateAvatar(avatarData: String) {
        viewModelScope.launch {
            _avatarLoading.value = true
            try {
                // Update local avatar
                _avatar.value = avatarData
                preferencesManager.saveAvatar(avatarData)
                android.util.Log.d("SettingsViewModel", "Avatar saved locally")

                // Update avatar on backend
                val username = checkLoginStatusUseCase.getUsername()
                if (username.isNullOrEmpty()) {
                    android.util.Log.w("SettingsViewModel", "Username is null or empty, cannot update avatar on backend")
                } else {
                    android.util.Log.d("SettingsViewModel", "Updating avatar on backend for user: $username")
                    val result = memberRepository.updateAvatar(username, avatarData)
                    if (result.isSuccess) {
                        android.util.Log.d("SettingsViewModel", "Avatar updated successfully on backend")
                    } else {
                        val errorMessage = result.exceptionOrNull()?.message ?: "Unknown error"
                        android.util.Log.e("SettingsViewModel", "Failed to update avatar on backend: $errorMessage")
                        throw Exception("Update avatar failed: $errorMessage")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Failed to update avatar", e)
                // Add error handling logic, show error message
                _syncMessage.value = context.getString(R.string.update_avatar_failed) + ": ${e.message}"
            } finally {
                _avatarLoading.value = false
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _currentUsername.value = null
            _avatar.value = null
            preferencesManager.clearAvatar()
            _logoutEvent.emit(Unit)
        }
    }

    fun clearSkippedLogin() {
        preferencesManager.setSkippedLogin(false)
    }

    fun setLanguage(language: Language) {
        languageManager.setLanguage(language)
    }

    fun toggleDeveloperMode() {
        viewModelScope.launch {
            developerMode.toggleDeveloperMode()
        }
    }

    /**
     * Triggers a manual sync with the server, showing a success/failure message.
     */
    fun manualSync() {
        viewModelScope.launch {
            try {
                _syncMessage.value = null
                val result = syncScheduler.manualSync()

                if (result.isSuccess) {
                    val syncResult = result.getOrNull()
                    if (syncResult?.success == true) {
                        _syncMessage.value = context.getString(R.string.device_sync_success)
                        loadSyncInfo()
                    } else {
                        _syncMessage.value = context.getString(R.string.device_sync_failed, syncResult?.error ?: "Unknown error")
                    }
                } else {
                    _syncMessage.value = context.getString(R.string.device_sync_failed, result.exceptionOrNull()?.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                _syncMessage.value = context.getString(R.string.device_sync_failed, e.message ?: "Unknown error")
            }
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    /**
     * Search for devices on the local network
     */
    fun searchDevices(): Flow<DeviceInfo> {
        return syncManager.getDeviceSyncManager().searchDevices()
    }

    /**
     * Synchronizes with a specific device discovered on the local network.
     * Shows a progress dialog during the sync operation.
     *
     * @param deviceInfo The target device's discovery information.
     */
    fun deviceSync(deviceInfo: DeviceInfo) {
        viewModelScope.launch {
            try {
                android.util.Log.d("SettingsViewModel", "Starting device sync with: ${deviceInfo.deviceName} at ${deviceInfo.address}")
                _syncMessage.value = null
                _showSyncProgress.value = true
                _syncProgress.value = 0f
                _syncProgressMessage.value = context.getString(R.string.device_sync_connecting, deviceInfo.deviceName)

                // Get device sync manager
                val deviceSyncManager = syncManager.getDeviceSyncManager()
                android.util.Log.d("SettingsViewModel", "Got device sync manager")

                // Update progress - Connecting
                _syncProgress.value = 0.1f
                _syncProgressMessage.value = context.getString(R.string.device_sync_connecting, deviceInfo.deviceName)

                android.util.Log.d("SettingsViewModel", "Calling syncWithDevice...")
                val syncResult = deviceSyncManager.syncWithDevice(deviceInfo)
                android.util.Log.d("SettingsViewModel", "syncWithDevice returned: success=${syncResult.success}, error=${syncResult.error}")

                // Update progress - completed or failed
                _syncProgress.value = 1f
                if (syncResult.success) {
                    _syncProgressMessage.value = context.getString(R.string.device_sync_success)
                    _syncMessage.value = context.getString(R.string.device_sync_success)
                    loadSyncInfo()
                } else {
                    _syncProgressMessage.value = context.getString(R.string.device_sync_failed, syncResult.error)
                    _syncMessage.value = context.getString(R.string.device_sync_failed, syncResult.error)
                }

                // Delay closing the progress dialog
                kotlinx.coroutines.delay(1500.milliseconds)
                _showSyncProgress.value = false

            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Device sync failed", e)
                _syncProgress.value = 1f
                _syncProgressMessage.value = context.getString(R.string.device_sync_failed, e.message)
                _syncMessage.value = context.getString(R.string.device_sync_failed, e.message)
                kotlinx.coroutines.delay(1500.milliseconds)
                _showSyncProgress.value = false
            }
        }
    }

    /**
     * Show sync progress dialog
     */
    fun showSyncProgress() {
        _showSyncProgress.value = true
        _syncProgress.value = 0f
    }

    /**
     * Hide sync progress dialog
     */
    fun hideSyncProgress() {
        _showSyncProgress.value = false
    }

    /**
     * Update sync progress
     */
    fun updateSyncProgress(progress: Float, message: String) {
        _syncProgress.value = progress
        _syncProgressMessage.value = message
    }

    /**
     * Show a dialog to confirm sync request with a device
     */
    fun showSyncRequestDialog(deviceInfo: DeviceInfo) {
        _pendingSyncRequest.value = deviceInfo
        _showSyncRequestDialog.value = true
    }

    /**
     * Hide sync request dialog
     */
    fun hideSyncRequestDialog() {
        _showSyncRequestDialog.value = false
        _pendingSyncRequest.value = null
    }

    /**
     * Accept sync request
     */
    fun acceptSyncRequest() {
        val deviceInfo = _pendingSyncRequest.value
        if (deviceInfo != null) {
            hideSyncRequestDialog()
            deviceSync(deviceInfo)
        }
    }

    /**
     * Reject sync request
     */
    fun rejectSyncRequest() {
        hideSyncRequestDialog()
    }

    /**
     * Clear server sync progress dialog
     */
    fun clearServerSyncProgress() {
        syncManager.getDeviceSyncManager().clearSyncProgress()
    }

    /**
     * Probes [rawUrl] for reachability and reports the outcome through [serverTestState].
     */
    fun testServerConnection(rawUrl: String) {
        viewModelScope.launch {
            _serverTestState.value = ServerTestUiState.Testing
            when (val result = serverConnectionTester.test(rawUrl)) {
                is com.chronie.homemoney.service.ServerTestResult.Success ->
                    _serverTestState.value = ServerTestUiState.Reachable(result.latencyMs)
                is com.chronie.homemoney.service.ServerTestResult.InvalidUrl ->
                    _serverTestState.value = ServerTestUiState.Failed(context.getString(R.string.server_url_invalid))
                is com.chronie.homemoney.service.ServerTestResult.BadResponse ->
                    _serverTestState.value = ServerTestUiState.Failed(context.getString(R.string.server_test_bad_response, result.code))
                is com.chronie.homemoney.service.ServerTestResult.Unreachable ->
                    _serverTestState.value = ServerTestUiState.Failed(context.getString(R.string.server_test_unreachable, result.reason))
            }
        }
    }

    /**
     * Persists [rawUrl] as the active server address.
     * @return true when the value was accepted and stored.
     */
    fun saveServerUrl(rawUrl: String): Boolean {
        val result = serverConfigManager.setBaseUrl(rawUrl)
        return if (result.isSuccess) {
            _serverTestState.value = ServerTestUiState.Idle
            true
        } else {
            val message = result.exceptionOrNull()?.message ?: context.getString(R.string.server_url_invalid)
            _serverTestState.value = ServerTestUiState.Failed(message)
            false
        }
    }

    /** Restores the compiled-in default address. */
    fun resetServerUrl() {
        serverConfigManager.resetToDefault()
        _serverTestState.value = ServerTestUiState.Idle
    }

    /** Clears any transient test result so the dialog starts fresh. */
    fun clearServerTestState() {
        _serverTestState.value = ServerTestUiState.Idle
    }

    /**
     * Starts (or resumes) downloading the on-device AI model (~5.5 GB).
     *
     * Uses a [ModelDownloadService] foreground service so the download
     * survives the app being backgrounded (Android restricts network
     * access for backgrounded apps in Doze/app-standby).
     */
    fun downloadAiModel() {
        val intent = android.content.Intent(
            context,
            com.chronie.homemoney.service.ModelDownloadService::class.java
        ).apply {
            action = com.chronie.homemoney.service.ModelDownloadService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    /**
     * Cancels an in-flight model download.
     */
    fun cancelAiModelDownload() {
        aiModelManager.cancelDownload()
    }

    /**
     * Deletes the on-device AI model and frees its storage.
     */
    fun deleteAiModel() {
        viewModelScope.launch {
            aiModelManager.deleteModel()
            _syncMessage.value = context.getString(R.string.settings_ai_model_deleted)
        }
    }

    /** Selected mirror used for AI model downloads (auto / pinned). */
    val aiModelSourcePreference: StateFlow<com.chronie.homemoney.data.vlm.OnDeviceModelManager.SourcePreference> =
        aiModelManager.sourcePreference

    /**
     * Updates the download-source preference. Takes effect on the next
     * download start; an in-flight download keeps its current mirror.
     */
    fun setAiModelSourcePreference(
        pref: com.chronie.homemoney.data.vlm.OnDeviceModelManager.SourcePreference
    ) {
        aiModelManager.setSourcePreference(pref)
    }

    private fun loadSyncInfo() {
        viewModelScope.launch {
            // Load last sync time
            val lastSync = syncManager.getLastSyncTime()
            _lastSyncTime.value = if (lastSync != null) {
                formatTimestamp(lastSync)
            } else {
                null
            }

            // Load pending sync count
            _pendingSyncCount.value = syncManager.getPendingSyncCount()
        }
    }

    // Load dynamic color settings
    private fun loadDynamicColorSettings() {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("theme_settings", android.content.Context.MODE_PRIVATE)
            _useDynamicColor.value = prefs.getBoolean("use_dynamic_color", true)
            _primaryColor.value = prefs.getInt("primary_color", 0)
            val paletteStyleValue = prefs.getInt("palette_style", PaletteStyle.Expressive.ordinal)
            val paletteStyle = PaletteStyle.entries.toTypedArray().getOrElse(paletteStyleValue) { PaletteStyle.Expressive }
            _paletteStyle.value = paletteStyle
        }
    }

    // Toggle dynamic color switch
    fun toggleDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("theme_settings", android.content.Context.MODE_PRIVATE)
            prefs.edit { putBoolean("use_dynamic_color", enabled) }
            _useDynamicColor.value = enabled
            _syncMessage.value = context.getString(if (enabled) R.string.dynamic_color_enabled else R.string.dynamic_color_disabled)
        }
    }

    // Set manual primary color
    fun setPrimaryColor(color: Int) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("theme_settings", android.content.Context.MODE_PRIVATE)
            prefs.edit { putInt("primary_color", color) }
            _primaryColor.value = color
            _syncMessage.value = context.getString(R.string.primary_color_updated)
        }
    }

    // Load device name from preferences
    private fun loadDeviceName() {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("sync_prefs", android.content.Context.MODE_PRIVATE)
            _deviceName.value = prefs.getString("device_custom_name", android.os.Build.MODEL ?: "Android Device") ?: "Android Device"
        }
    }

    // Set device name
    fun setDeviceName(name: String) {
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("sync_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit { putString("device_custom_name", name) }
            _deviceName.value = name
            _syncMessage.value = context.getString(R.string.device_name_updated)
        }
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * Exports expenses to an Excel file.
     * @param startDate Optional export range start (inclusive).
     * @param endDate Optional export range end (inclusive).
     */
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

    /**
     * Imports expenses from an Excel file via content URI.
     * @param uri Content URI pointing to the .xlsx file to import.
     */
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

                    // If any records failed to import, log the errors
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

/**
 * UI representation of a server reachability probe, driven by [SettingsViewModel.serverTestState].
 */
sealed interface ServerTestUiState {

    /** No probe has run yet (or the result was dismissed). */
    data object Idle : ServerTestUiState

    /** A probe is in flight. */
    data object Testing : ServerTestUiState

    /** The candidate server answered the health endpoint. [latencyMs] is the round-trip time. */
    data class Reachable(val latencyMs: Long) : ServerTestUiState

    /** The probe failed; [message] is already localized for display. */
    data class Failed(val message: String) : ServerTestUiState
}
