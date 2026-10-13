package com.chronie.homemoney.domain.usecase

import android.util.Log
import com.chronie.homemoney.core.common.Language
import com.chronie.homemoney.data.vlm.MnnVlmEngine
import com.chronie.homemoney.data.vlm.OnDeviceModelManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Translates an expense remark into the user's current app language using
 * the on-device AI model (Qwen3-VL 8B via the MNN runtime).
 *
 * Typical scenario: remarks recorded abroad in a foreign language
 * (e.g. "Cafe Latte Grande" or "スーパーで牛乳") being read later in the
 * user's own language — or the reverse for a user reading an app in
 * English with Chinese-language remarks.
 *
 * Design notes:
 *  * **Any source language, any target language** — the prompt instructs
 *    the model to auto-detect the source and render into [targetLanguage].
 *  * **Fully offline** — only [MnnVlmEngine] is involved; no network I/O
 *    happens anywhere in this pipeline. The engine is only usable after
 *    [OnDeviceModelManager] has verified the model package on disk, which
 *    the UI gates on before showing the translate button.
 *  * **Serialized inference** — the native engine serves one request at a
 *    time; a mutex here keeps concurrent callers from interleaving
 *    `nativeChat` calls (which would corrupt the KV cache session).
 *  * **"Unchanged" is a valid outcome** — the prompt instructs the model
 *    to return the text verbatim when it is already in the target
 *    language or contains nothing translatable (numbers/symbols only).
 *    The caller compares input and output to surface the
 *    "already in your language" hint instead of a confusing duplicate.
 */
@Singleton
class TranslateRemarkUseCase @Inject constructor(
    private val modelManager: OnDeviceModelManager,
    private val vlmEngine: MnnVlmEngine
) {

    companion object {
        private const val TAG = "TranslateRemarkUseCase"

        /** Hard cap on remark length fed to the model (protects latency). */
        private const val MAX_REMARK_LENGTH = 512

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

    /** Thrown when the model package has not been downloaded/verified yet. */
    class ModelNotReadyException : IllegalStateException(
        "The on-device AI model is not downloaded. Open Settings > AI to download it first."
    )

    /** Thrown when the remark has no translatable content (blank/numbers only). */
    class EmptyRemarkException : IllegalArgumentException("The remark is empty")

    /** Serializes access to the single native inference session. */
    private val inferenceMutex = Mutex()

    /**
     * Translates [remark] into [targetLanguage] on-device.
     *
     * @param remark the raw remark text of the expense record, in any language.
     * @param targetLanguage the language the app is currently displayed in.
     * @return Result.success with the translated text (which may equal the
     *         input when it is already in the target language).
     */
    suspend operator fun invoke(remark: String, targetLanguage: Language): Result<String> =
        inferenceMutex.withLock {
            val trimmed = remark.trim()
            if (trimmed.isEmpty()) {
                return@withLock Result.failure(EmptyRemarkException())
            }

            // Gate on the verified model package before touching the engine.
            if (!modelManager.isModelReady()) {
                return@withLock Result.failure(ModelNotReadyException())
            }

            val loadResult = vlmEngine.ensureLoaded(modelManager.modelDir())
            loadResult.exceptionOrNull()?.let {
                return@withLock Result.failure(it)
            }

            val prompt = buildPrompt(trimmed.take(MAX_REMARK_LENGTH), targetLanguage)
            val generated = vlmEngine.generate(prompt).getOrElse { return@withLock Result.failure(it) }

            val cleaned = cleanOutput(generated)
            if (cleaned.isEmpty()) {
                return@withLock Result.failure(
                    IllegalStateException("The on-device model returned an empty translation")
                )
            }

            Log.d(TAG, "Remark translated (${trimmed.length} -> ${cleaned.length} chars)")
            Result.success(cleaned)
        }

    /**
     * Builds the translation prompt. English keeps the instruction
     * unambiguous for the model for every user locale, and the model
     * auto-detects the source language.
     */
    private fun buildPrompt(remark: String, targetLanguage: Language): String {
        val targetName = PROMPT_LANGUAGE_NAMES[targetLanguage] ?: "English"
        return """
Translate the following text into $targetName.

Requirements:
1. Output only the translation itself — no quotes, explanations, prefixes, or markdown code blocks.
2. Preserve numbers, amounts, dates, and proper nouns as they appear in the original.
3. If the text is already in $targetName, or contains nothing translatable (numbers or symbols only), return the text unchanged.

Text: $remark
        """.trimIndent()
    }

    /**
     * Strips the decorations small local models like to add: markdown
     * fences, wrapping quotes, and leading "Translation:" style prefixes.
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
