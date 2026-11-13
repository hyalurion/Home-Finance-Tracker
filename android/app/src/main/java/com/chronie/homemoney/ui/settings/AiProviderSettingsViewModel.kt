package com.chronie.homemoney.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chronie.homemoney.data.local.AiProviderConfigManager
import com.chronie.homemoney.domain.model.chat.AiProviderConfig
import com.chronie.homemoney.domain.model.chat.ProviderFormat
import com.chronie.homemoney.domain.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the "Test connection" action in the provider editor. */
sealed interface ProviderTestState {
    data object Idle : ProviderTestState
    data object Testing : ProviderTestState
    data class Success(val message: String) : ProviderTestState
    data class Failed(val message: String) : ProviderTestState
}

/** Settings-page view model for custom model sources and tool permissions. */
@HiltViewModel
class AiProviderSettingsViewModel @Inject constructor(
    private val configManager: AiProviderConfigManager,
    private val repository: ChatRepository
) : ViewModel() {

    val providers: StateFlow<List<AiProviderConfig>> = configManager.providers
    val activeProviderId: StateFlow<String> = configManager.activeProviderId
    val queryAuthorized: StateFlow<Boolean> = configManager.queryAuthorized
    val autoWriteRecords: StateFlow<Boolean> = configManager.autoWriteRecords

    private val _testState = MutableStateFlow<ProviderTestState>(ProviderTestState.Idle)
    val testState: StateFlow<ProviderTestState> = _testState.asStateFlow()

    fun addOrUpdate(
        id: String?,
        name: String,
        format: ProviderFormat,
        baseUrl: String,
        model: String,
        apiKey: String
    ): Result<Unit> {
        val config = AiProviderConfig(
            id = id ?: java.util.UUID.randomUUID().toString(),
            name = name.trim(),
            format = format,
            baseUrl = baseUrl.trim(),
            model = model.trim()
        )
        return configManager.saveProvider(config, apiKey)
    }

    fun delete(id: String) = configManager.deleteProvider(id)

    fun setActive(id: String) = configManager.setActiveProvider(id)

    fun setQueryAuthorized(enabled: Boolean) = configManager.setQueryAuthorized(enabled)

    fun setAutoWriteRecords(enabled: Boolean) = configManager.setAutoWriteRecords(enabled)

    fun testConnection(
        id: String?,
        name: String,
        format: ProviderFormat,
        baseUrl: String,
        model: String,
        apiKey: String
    ) {
        val normalized = configManager.normalizeBaseUrl(baseUrl)
        if (normalized == null || model.isBlank()) {
            _testState.value = ProviderTestState.Failed("Invalid base URL or model")
            return
        }
        val config = AiProviderConfig(
            id = id ?: "probe",
            name = name,
            format = format,
            baseUrl = normalized,
            model = model.trim()
        )
        val key = apiKey.ifBlank {
            id?.let { configManager.apiKeyFor(it) }.orEmpty()
        }
        viewModelScope.launch {
            _testState.value = ProviderTestState.Testing
            val result = repository.probeProvider(config, key)
            _testState.value = result.fold(
                onSuccess = { ProviderTestState.Success(it) },
                onFailure = { ProviderTestState.Failed(it.message ?: "Connection failed") }
            )
        }
    }

    fun clearTestState() {
        _testState.value = ProviderTestState.Idle
    }
}
