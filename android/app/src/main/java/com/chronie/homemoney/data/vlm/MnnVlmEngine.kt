package com.chronie.homemoney.data.vlm

import android.util.Log
import androidx.annotation.Keep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One role/content pair handed to MNN's native chat template. */
data class NativeChatTurn(val role: String, val content: String)

/**
 * Kotlin facade for the on-device multimodal LLM (Qwen3-VL 8B, MNN runtime).
 *
 * The heavy lifting happens in native code (cpp/mnn/mnn_bridge.cpp) — this
 * class owns the lifecycle of the single process-wide engine instance and
 * exposes a coroutine-friendly [generate] API.
 *
 * Contract:
 *  * [ensureLoaded] must succeed before [generate]; it is idempotent and
 *    safe to call concurrently (a mutex serializes model loading, which can
 *    take tens of seconds for a ~5.5 GB mmap-ed model).
 *  * [generate] runs on Dispatchers.Default and blocks that thread until
 *    generation completes; call it from a coroutine that can be cancelled
 *    from the UI via structured concurrency.
 *  * Images are passed as a list of absolute file paths; the bridge embeds
 *    <img>path</img> markers before the text prompt, which MNN's visual
 *    model processes.
 */
@Keep
@Singleton
class MnnVlmEngine @Inject constructor() {

    companion object {
        private const val TAG = "MnnVlmEngine"

        /** Loads libmnn_bridge.so (real bridge or stub) once per process. */
        private const val LIBRARY_NAME = "mnn_bridge"

        init {
            System.loadLibrary(LIBRARY_NAME)
        }

        /** nativeCreate result codes, mirrored from mnn_bridge.cpp. */
        private const val CREATE_OK = 0
        private const val CREATE_FAILED = -1
        private const val CREATE_ALREADY = -2
        private const val CREATE_RUNTIME_MISSING = -3

        /** Embeds an image file path as a multimodal marker in the prompt. */
        fun buildImageTag(imagePath: String): String = "<img>$imagePath</img>"
    }

    /**
     * Receives incrementally generated text (called on the caller's thread).
     *
     * The fully qualified name and the [onToken] method name are part of the
     * native ABI: mnn_bridge.cpp resolves them with FindClass/GetMethodID.
     * @Keep plus the matching ProGuard rules stop R8 from rewriting them.
     */
    @Keep
    fun interface TokenListener {
        @Keep
        fun onToken(token: String)
    }

    /**
     * Explicit listener implementation instead of a SAM lambda.
     *
     * R8 rewrites Kotlin SAM lambdas into obfuscated classes (e.g. "wj1")
     * whose bridge method may no longer be called `onToken`, which made
     * GetMethodID fail at runtime. A named, kept class keeps the JNI lookup
     * deterministic in release builds.
     */
    @Keep
    private class TokenRelay(
        private val sink: suspend (String) -> Unit
    ) : TokenListener {
        @Keep
        override fun onToken(token: String) {
            // The callback arrives on the native generation thread; the sink
            // only does a non-suspending channel trySend, so runBlocking is
            // cheap and preserves token ordering.
            runBlocking { sink(token) }
        }
    }

    // Native entry points implemented in cpp/mnn/mnn_bridge(.cpp|_stub.cpp).
    private external fun nativeCreate(configPath: String, extraConfigJson: String): Int
    private external fun nativeIsCreated(): Int
    private external fun nativeDestroy()
    private external fun nativeChat(
        prompt: String,
        imagePaths: String?,  // comma-separated absolute paths (may be null)
        listener: TokenListener?
    ): String
    private external fun nativeReset()

    /**
     * Multi-turn overload. MNN applies the model's real chat template to these
     * role/content pairs, so history is no longer flattened into one user
     * message — that flattening was what made small models continue the
     * transcript with invented `[User]` / `[Assistant]` lines.
     */
    private external fun nativeChatTurns(
        roles: Array<String>,
        contents: Array<String>,
        imagePaths: String?,
        listener: TokenListener?,
        endWith: String?,
        maxNewTokens: Int
    ): String

    /** Serializes load/destroy so concurrent coroutines cannot interleave. */
    private val lifecycleMutex = Mutex()

    @Volatile
    private var isLoaded = false

    /** @return true when the native engine currently holds a live model. */
    fun isReady(): Boolean = isLoaded && nativeIsCreated() == 1

    /**
     * Loads the model if necessary.
     *
     * @param modelDir directory containing config.json + MNN weights
     * @return Result.success(Unit) when the engine is ready for inference.
     */
    suspend fun ensureLoaded(modelDir: File): Result<Unit> = lifecycleMutex.withLock {
        if (isReady()) return Result.success(Unit)

        val configFile = File(modelDir, "config.json")
        if (!configFile.exists()) {
            return Result.failure(IllegalStateException("Model config not found: $configFile"))
        }

        return withContext(Dispatchers.Default) {
            when (nativeCreate(configFile.absolutePath, buildRuntimeConfig())) {
                CREATE_OK, CREATE_ALREADY -> {
                    isLoaded = true
                    Log.i(TAG, "VLM engine loaded from $modelDir")
                    Result.success(Unit)
                }
                CREATE_RUNTIME_MISSING -> {
                    Log.e(TAG, "MNN runtime is not bundled in this build")
                    Result.failure(IllegalStateException(RuntimeMissingException.MESSAGE))
                }
                else -> {
                    Log.e(TAG, "nativeCreate failed (out of memory or corrupt weights?)")
                    Result.failure(IllegalStateException("Failed to initialize the on-device AI model"))
                }
            }
        }
    }

    /**
     * Runs one-shot generation with the given prompt and optional images.
     *
     * @param prompt prompt text
     * @param imagePaths optional list of absolute paths to pre-processed images
     * @param onToken optional incremental callback for progress UI
     * @return Result with the full generated text (empty output = failure).
     */
    suspend fun generate(
        prompt: String,
        imagePaths: List<String>? = null,
        onToken: (suspend (String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.Default) {
        if (!isReady()) {
            return@withContext Result.failure(IllegalStateException("Engine not loaded"))
        }

        // Comma-join image paths for the JNI bridge (null when no images).
        val imagePathsStr = if (!imagePaths.isNullOrEmpty()) {
            imagePaths.joinToString(",")
        } else {
            null
        }

        // Bridge the synchronous Java callback into the coroutine world.
        val listener: TokenListener? = if (onToken == null) null else TokenRelay(onToken)

        val output = try {
            nativeChat(prompt, imagePathsStr, listener)
        } catch (e: Throwable) {
            Log.e(TAG, "nativeChat threw", e)
            return@withContext Result.failure(e)
        }

        if (output.isBlank()) {
            Result.failure(IllegalStateException("The on-device model returned an empty response"))
        } else {
            Result.success(output)
        }
    }

    /**
     * Runs generation over properly role-tagged turns.
     *
     * @param endWith optional literal stop string (e.g. `[User]`) that cuts off
     *   a model which still tries to continue a fake dialogue.
     * @param maxNewTokens hard cap kept low on-device to bound battery usage.
     */
    suspend fun generateTurns(
        turns: List<NativeChatTurn>,
        imagePaths: List<String>? = null,
        endWith: String? = null,
        maxNewTokens: Int = 512,
        onToken: (suspend (String) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.Default) {
        if (!isReady()) {
            return@withContext Result.failure(IllegalStateException("Engine not loaded"))
        }
        if (turns.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("No chat turns supplied"))
        }
        val paths = if (imagePaths.isNullOrEmpty()) null else imagePaths.joinToString(",")
        val listener: TokenListener? = if (onToken == null) null else TokenRelay(onToken)

        val output = try {
            nativeChatTurns(
                turns.map { it.role }.toTypedArray(),
                turns.map { it.content }.toTypedArray(),
                paths,
                listener,
                endWith,
                maxNewTokens
            )
        } catch (e: Throwable) {
            Log.e(TAG, "nativeChatTurns threw", e)
            return@withContext Result.failure(e)
        }

        if (output.isBlank()) {
            Result.failure(IllegalStateException("The on-device model returned an empty response"))
        } else {
            Result.success(output)
        }
    }

    /** Releases the native model and frees its (mmap-ed) memory. */
    suspend fun release() = lifecycleMutex.withLock {
        withContext(Dispatchers.Default) {
            if (isLoaded) {
                nativeDestroy()
                isLoaded = false
                Log.i(TAG, "VLM engine released")
            }
        }
    }

    /**
     * Runtime tuning merged into the engine config after creation.
     * use_mmap keeps peak RSS manageable for the 4.4 GB weight file on
     * 12-16 GB devices; max_new_tokens bounds bill-generation latency.
     */
    private fun buildRuntimeConfig(): String {
        return """{"max_new_tokens":1024,"use_mmap":true}"""
    }

    /** Thrown when the APK was built without the MNN native runtime. */
    class RuntimeMissingException : IllegalStateException(MESSAGE) {
        companion object {
            const val MESSAGE =
                "On-device AI runtime is not available in this build. " +
                    "Rebuild the app after running scripts/build-mnn-android.ps1."
        }
    }
}
