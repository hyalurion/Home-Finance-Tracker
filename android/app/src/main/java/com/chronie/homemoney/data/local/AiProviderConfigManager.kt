package com.chronie.homemoney.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chronie.homemoney.domain.model.chat.AiProviderConfig
import com.chronie.homemoney.domain.model.chat.ProviderFormat
import com.chronie.homemoney.domain.model.chat.ProviderToolMode
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for AI chat model sources and tool permissions.
 *
 * Design mirrors [ServerConfigManager]: plain [SharedPreferences] keeps values
 * synchronously readable from OkHttp callback threads, while [StateFlow]s keep
 * the settings UI reactive. API keys never touch the plain preferences file —
 * they live in an Android Keystore-backed [EncryptedSharedPreferences] file.
 *
 * The built-in (on-device MNN) engine is always present and cannot be deleted;
 * users may add any number of OpenAI- or Anthropic-compatible endpoints.
 */
@Singleton
class AiProviderConfigManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Suppress("DEPRECATION")
    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** Built-in provider descriptor, always first in [providers]. Declared
     * before the flows because [readProviders] reads it during initialization. */
    val builtInProvider: AiProviderConfig = AiProviderConfig(
        id = BUILT_IN_ID,
        name = BUILT_IN_NAME,
        format = ProviderFormat.BUILT_IN
    )

    private val _providers = MutableStateFlow(readProviders())
    val providers: StateFlow<List<AiProviderConfig>> = _providers.asStateFlow()

    private val _activeProviderId = MutableStateFlow(
        prefs.getString(KEY_ACTIVE_PROVIDER, null) ?: BUILT_IN_ID
    )
    val activeProviderId: StateFlow<String> = _activeProviderId.asStateFlow()

    private val _queryAuthorized = MutableStateFlow(
        prefs.getBoolean(KEY_QUERY_AUTHORIZED, false)
    )
    val queryAuthorized: StateFlow<Boolean> = _queryAuthorized.asStateFlow()

    private val _autoWriteRecords = MutableStateFlow(
        prefs.getBoolean(KEY_AUTO_WRITE, true)
    )
    val autoWriteRecords: StateFlow<Boolean> = _autoWriteRecords.asStateFlow()

    /**
     * The 8B on-device model is heavy on battery and noticeably weaker than
     * hosted models. Users can switch it off entirely; the provider picker and
     * the active-provider resolution then skip it.
     */
    private val _onDeviceEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_ON_DEVICE_ENABLED, true)
    )
    val onDeviceEnabled: StateFlow<Boolean> = _onDeviceEnabled.asStateFlow()

    /** Resolves the currently selected provider, skipping a disabled built-in. */
    fun activeProvider(): AiProviderConfig {
        val id = _activeProviderId.value
        if (id == BUILT_IN_ID && !_onDeviceEnabled.value) {
            return firstCustomProvider() ?: builtInProvider
        }
        if (id == BUILT_IN_ID) return builtInProvider
        return _providers.value.firstOrNull { it.id == id } ?: firstCustomProvider() ?: builtInProvider
    }

    /** Custom providers ordered as stored; empty when none were configured. */
    private fun firstCustomProvider(): AiProviderConfig? =
        _providers.value.firstOrNull { !it.isBuiltIn }

    /** Providers the user may actually select in the chat model picker. */
    fun selectableProviders(): List<AiProviderConfig> =
        if (_onDeviceEnabled.value) _providers.value else _providers.value.filterNot { it.isBuiltIn }

    /** Reads the secret for [providerId]; returns an empty string when unset. */
    fun apiKeyFor(providerId: String): String =
        securePrefs.getString(KEY_API_KEY_PREFIX + providerId, null).orEmpty()

    /**
     * Inserts or updates a custom provider. Passing a non-blank [apiKey]
     * replaces the stored secret; passing null keeps the existing one.
     */
    fun saveProvider(config: AiProviderConfig, apiKey: String?): Result<Unit> {
        if (config.isBuiltIn) return Result.failure(IllegalArgumentException("Built-in provider is read-only"))
        val normalized = normalizeBaseUrl(config.baseUrl)
            ?: return Result.failure(IllegalArgumentException("Invalid base URL"))
        if (config.model.isBlank()) {
            return Result.failure(IllegalArgumentException("Model name is required"))
        }

        val stored = config.copy(
            baseUrl = normalized,
            hasApiKey = if (apiKey.isNullOrBlank()) apiKeyFor(config.id).isNotBlank() else true
        )
        val custom = _providers.value.filter { !it.isBuiltIn && it.id != stored.id } + stored
        persistProviders(custom)

        if (!apiKey.isNullOrBlank()) {
            securePrefs.edit { putString(KEY_API_KEY_PREFIX + stored.id, apiKey.trim()) }
        }
        return Result.success(Unit)
    }

    /** Removes a custom provider and its secret. Selecting it again is impossible afterwards. */
    fun deleteProvider(id: String) {
        if (id == BUILT_IN_ID) return
        persistProviders(_providers.value.filter { !it.isBuiltIn && it.id != id })
        securePrefs.edit { remove(KEY_API_KEY_PREFIX + id) }
        if (_activeProviderId.value == id) {
            val next = firstCustomProvider()
                ?: builtInProvider.takeIf { _onDeviceEnabled.value }
            if (next != null) {
                setActiveProvider(next.id)
            } else {
                prefs.edit { remove(KEY_ACTIVE_PROVIDER) }
                _activeProviderId.value = ""
            }
        }
    }

    fun setActiveProvider(id: String) {
        if (id == BUILT_IN_ID && !_onDeviceEnabled.value) return
        prefs.edit { putString(KEY_ACTIVE_PROVIDER, id) }
        _activeProviderId.value = id
    }

    /**
     * Remembers that a provider rejected native function calling, so later
     * turns skip the doomed HTTP 400 request instead of retrying every time.
     */
    fun rememberTextToolFallback(providerId: String) {
        if (providerId == BUILT_IN_ID) return
        val updated = _providers.value.map { provider ->
            if (provider.id == providerId) {
                provider.copy(toolMode = ProviderToolMode.TEXT.name)
            } else {
                provider
            }
        }
        persistProviders(updated.filterNot { it.isBuiltIn })
    }

    /** Persists the user's explicit tool-mode choice for a provider. */
    fun setToolMode(providerId: String, mode: ProviderToolMode) {
        val updated = _providers.value
            .filterNot { it.isBuiltIn }
            .map { provider ->
                if (provider.id == providerId) provider.copy(toolMode = mode.name) else provider
            }
        persistProviders(updated)
    }

    /**
     * Disabling the on-device model switches away from it and lets callers
     * release the mmap-ed weights, which is the main battery/RSS win.
     */
    fun setOnDeviceEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ON_DEVICE_ENABLED, enabled) }
        _onDeviceEnabled.value = enabled
        if (!enabled && _activeProviderId.value == BUILT_IN_ID) {
            firstCustomProvider()?.let { setActiveProvider(it.id) }
        }
    }

    fun isOnDeviceEnabled(): Boolean = _onDeviceEnabled.value

    /**
     * Explicit user consent required before query tools are even advertised
     * to the model. Defaults to disabled.
     */
    fun setQueryAuthorized(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_QUERY_AUTHORIZED, enabled) }
        _queryAuthorized.value = enabled
    }

    fun isQueryAuthorized(): Boolean = _queryAuthorized.value

    /** When disabled, add_expense calls are presented for confirmation instead of executing. */
    fun setAutoWriteRecords(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AUTO_WRITE, enabled) }
        _autoWriteRecords.value = enabled
    }

    fun isAutoWriteRecords(): Boolean = _autoWriteRecords.value

    // ------------------------------------------------------------------
    // Persistence helpers
    // ------------------------------------------------------------------

    private fun readProviders(): List<AiProviderConfig> {
        val raw = prefs.getString(KEY_PROVIDERS, null) ?: return listOf(builtInProvider)
        val type = object : TypeToken<List<AiProviderConfig>>() {}.type
        val custom = runCatching {
            gson.fromJson<List<AiProviderConfig>>(raw, type) ?: emptyList()
        }.getOrDefault(emptyList())
        return listOf(builtInProvider) + custom
            .filter { !it.isBuiltIn && it.id != BUILT_IN_ID }
            .map { it.copy(hasApiKey = apiKeyFor(it.id).isNotBlank()) }
    }

    private fun persistProviders(custom: List<AiProviderConfig>) {
        val withoutSecrets = custom.map { it.copy(hasApiKey = false) }
        prefs.edit { putString(KEY_PROVIDERS, gson.toJson(withoutSecrets)) }
        _providers.value = listOf(builtInProvider) + custom.map {
            it.copy(hasApiKey = apiKeyFor(it.id).isNotBlank())
        }
    }

    /** Accepts bare hosts and guarantees a trailing slash for clean path joining. */
    fun normalizeBaseUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = when {
            trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true) -> trimmed
            trimmed.contains("://") -> return null
            else -> "https://$trimmed"
        }
        val parsed = withScheme.toHttpUrlOrNull() ?: return null
        if (parsed.host.isBlank()) return null
        val asString = parsed.toString()
        return if (asString.endsWith("/")) asString else "$asString/"
    }

    companion object {
        const val BUILT_IN_ID = "built_in_on_device"
        const val BUILT_IN_NAME = "On-device model"

        private const val PREFS_NAME = "ai_chat_config"
        private const val SECURE_PREFS_NAME = "ai_chat_secure"
        private const val KEY_PROVIDERS = "providers"
        private const val KEY_ACTIVE_PROVIDER = "active_provider"
        private const val KEY_QUERY_AUTHORIZED = "query_authorized"
        private const val KEY_AUTO_WRITE = "auto_write_records"
        private const val KEY_ON_DEVICE_ENABLED = "on_device_enabled"
        private const val KEY_API_KEY_PREFIX = "api_key_"
    }
}
