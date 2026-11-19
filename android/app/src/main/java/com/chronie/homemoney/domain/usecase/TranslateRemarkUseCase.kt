package com.chronie.homemoney.domain.usecase

import android.util.Log
import com.chronie.homemoney.core.common.Language
import com.chronie.homemoney.data.chat.LlmClientFactory
import com.chronie.homemoney.data.local.AiProviderConfigManager
import com.chronie.homemoney.data.vlm.OnDeviceModelManager
import com.chronie.homemoney.domain.model.chat.AiProviderConfig
import com.chronie.homemoney.domain.model.chat.ChatRole
import com.chronie.homemoney.domain.model.chat.LlmRequest
import com.chronie.homemoney.domain.model.chat.LlmTurn
import com.chronie.homemoney.domain.model.chat.StreamEvent
import com.chronie.homemoney.domain.model.chat.ToolProtocol
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates an expense remark into the user's current app language with
 * whichever AI model is active in Settings > AI.
 *
 * Typical scenario: remarks recorded abroad in a foreign language
 * (e.g. "Cafe Latte Grande" or "スーパーで牛乳") being read later in the
 * user's own language — or the reverse for a user reading an app in
 * English with Chinese-language remarks.
 *
 * Design notes:
 *  * **Provider-agnostic** — resolves [AiProviderConfigManager.activeProvider]
 *    and routes through [LlmClientFactory], so a configured OpenAI or
 *    Anthropic endpoint is used instead of always loading the 8B on-device
 *    model. The on-device engine remains available when it is the active
 *    provider and its model package is verified.
 *  * **No tools** — the request carries an empty tool list and
 *    [ToolProtocol.NONE], which avoids the HTTP 400 "Function call is not
 *    supported" rejection seen on some gateways.
 *  * **Serialized inference** — the native engine serves one request at a
 *    time; a mutex keeps concurrent callers from interleaving sessions.
 *  * **"Unchanged" is a valid outcome** — the prompt instructs the model
 *    to return the text verbatim when it is already in the target
 *    language or contains nothing translatable (numbers/symbols only).
 */
@Singleton
class TranslateRemarkUseCase @Inject constructor(
    private val configManager: AiProviderConfigManager,
    private val clientFactory: LlmClientFactory,
    private val modelManager: OnDeviceModelManager
) {

    companion object {
        private const val TAG = "TranslateRemarkUseCase"

        /** Hard cap on remark length fed to the model (protects latency). */
        private const val MAX_REMARK_LENGTH = 512

        /** Room for reasoning tokens on hosted reasoning models. */
        private const val MAX_OUTPUT_TOKENS = 1024

        /**
         * Maps an app [Language] to the English target-language name used
         * in the prompt. English names keep the instruction unambiguous
         * for the model regardless of which UI language the user runs.
         */
        private val PROMPT_LANGUAGE_NAMES: Map<Language, String> = mapOf(
            Language.ENGLISH to "English",
            Language.INDONESIAN to "Indonesian",
            Language.JAPANESE to "Japanese",
            Language.KOREAN to "Korean",
            Language.MALAY to "Malay",
            Language.SIMPLIFIED_CHINESE to "Simplified Chinese",
            Language.SIMPLIFIED_CHINESE_SINGAPORE to "Simplified Chinese",
            Language.THAI to "Thai",
            Language.TRADITIONAL_CHINESE_HONG_KONG to "Traditional Chinese",
            Language.TRADITIONAL_CHINESE_MACAU to "Traditional Chinese",
            Language.TRADITIONAL_CHINESE_TAIWAN to "Traditional Chinese",
            Language.VIETNAMESE to "Vietnamese"
        )
    }

    /** No usable model is configured (on-device disabled and no API provider). */
    class NoModelAvailableException : IllegalStateException("NO_MODEL_AVAILABLE")

    /** The active on-device model package has not been downloaded/verified. */
    class ModelNotReadyException : IllegalStateException("ON_DEVICE_MODEL_NOT_READY")

    /** Thrown when the remark has no translatable content (blank/numbers only). */
    class EmptyRemarkException : IllegalArgumentException("The remark is empty")

    /** Serializes access to the single native inference session. */
    private val inferenceMutex = Mutex()

    /**
     * Translates [remark] into [targetLanguage] with the active provider.
     *
     * @return Result.success with the translated text (which may equal the
     *         input when it is already in the target language).
     */
    suspend operator fun invoke(remark: String, targetLanguage: Language): Result<String> =
        inferenceMutex.withLock {
            val trimmed = remark.trim()
            if (trimmed.isEmpty()) {
                return@withLock Result.failure(EmptyRemarkException())
            }

            val config = resolveProvider()
                .getOrElse { return@withLock Result.failure(it) }
            val apiKey = configManager.apiKeyFor(config.id)
            val client = clientFactory.create(config)
            val request = LlmRequest(
                systemPrompt = translationInstruction(targetLanguage),
                turns = listOf(
                    LlmTurn(
                        role = ChatRole.USER,
                        content = "Text: ${trimmed.take(MAX_REMARK_LENGTH)}"
                    )
                ),
                tools = emptyList(),
                maxTokens = MAX_OUTPUT_TOKENS,
                toolProtocol = ToolProtocol.NONE
            )

            val answer = StringBuilder()
            val reasoning = StringBuilder()
            var failure: String? = null
            runCatching {
                client.stream(request, config, apiKey).collect { event ->
                    when (event) {
                        is StreamEvent.TextDelta -> answer.append(event.text)
                        is StreamEvent.ReasoningDelta -> reasoning.append(event.text)
                        is StreamEvent.Failed -> failure = event.message
                        else -> Unit
                    }
                }
            }.exceptionOrNull()?.let {
                return@withLock Result.failure(it)
            }
            failure?.let { return@withLock Result.failure(IllegalStateException(it)) }

            // Reasoning-only models emit no visible text; use their reasoning
            // as the last resort instead of reporting an empty translation.
            var cleaned = cleanOutput(answer.toString())
            if (cleaned.isEmpty() && reasoning.isNotBlank()) {
                cleaned = cleanOutput(reasoning.toString())
            }
            if (cleaned.isEmpty()) {
                return@withLock Result.failure(
                    IllegalStateException("The model returned an empty translation")
                )
            }

            Log.d(TAG, "Remark translated by ${config.name} (${trimmed.length} -> ${cleaned.length} chars)")
            Result.success(cleaned)
        }

    /**
     * Picks the model the user selected in Settings > AI. The built-in
     * provider is only usable when it is enabled and its package is verified;
     * otherwise the caller gets an explicit failure rather than a silent
     * fallback to a model the user turned off.
     */
    private fun resolveProvider(): Result<AiProviderConfig> {
        val selectable = configManager.selectableProviders()
        if (selectable.isEmpty()) return Result.failure(NoModelAvailableException())
        val active = configManager.activeProvider()
        if (!active.isBuiltIn) {
            if (selectable.none { it.id == active.id }) {
                return Result.failure(NoModelAvailableException())
            }
            return Result.success(active)
        }
        if (!configManager.isOnDeviceEnabled()) {
            return Result.failure(NoModelAvailableException())
        }
        if (!modelManager.isModelReady()) {
            return Result.failure(ModelNotReadyException())
        }
        return Result.success(active)
    }

    /**
     * Builds the translation instruction. English keeps the prompt
     * unambiguous for every user locale, and the model auto-detects the
     * source language. Reasoning-only output is discarded by the collector.
     */
    private fun translationInstruction(targetLanguage: Language): String {
        val targetName = PROMPT_LANGUAGE_NAMES[targetLanguage] ?: "English"
        return """
            You are a translation engine. Translate the user's text into $targetName.
            Rules:
            1. Output only the translation itself, with no quotes, explanations, prefixes, or markdown code blocks.
            2. Preserve numbers, amounts, dates, and proper nouns exactly as written.
            3. If the text is already in $targetName, or contains nothing translatable, return the text unchanged.
            4. Never answer questions, add commentary, or continue a conversation.
        """.trimIndent()
    }

    /**
     * Strips the decorations models like to add: markdown fences, wrapping
     * quotes, and leading "Translation:" style prefixes.
     */
    private fun cleanOutput(raw: String): String {
        var text = raw.trim()
            .replace("```json", "", ignoreCase = true)
            .replace("```", "")

        // Remove a single pair of wrapping quotes of any style.
        text = text.trim()
        val quotePairs = listOf(
            '"' to '"',
            '“' to '”',
            '『' to '』',
            '「' to '」'
        )
        for ((open, close) in quotePairs) {
            if (text.length >= 2 && text.startsWith(open) && text.endsWith(close)) {
                text = text.substring(1, text.length - 1)
                break
            }
        }

        // Drop a leading "Translation:" prefix if present.
        text = text.replace(Regex("^(Translation|译文)\\s*[:：]\\s*", RegexOption.IGNORE_CASE), "")

        return text.trim()
    }
}
