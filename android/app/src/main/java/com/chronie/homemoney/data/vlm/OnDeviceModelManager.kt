package com.chronie.homemoney.data.vlm

import android.content.Context
import android.os.StatFs
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.SocketException
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * Downloads and manages the on-device multimodal model
 * (MNN/Qwen3-VL-8B-Instruct-MNN, ~5.45 GB).
 *
 * The model is mirrored on two endpoints:
 *  * **ModelScope** (`www.modelscope.cn`) — fastest inside mainland China.
 *  * **Hugging Face** (`huggingface.co`, repo `taobao-mnn/...`) — fastest
 *    elsewhere. All manifest files are byte-identical except the 46-byte
 *    `configuration.json`, which only exists on ModelScope.
 *
 * Which mirror to use is controlled by [SourcePreference]:
 *  * [SourcePreference.AUTO] races a tiny probe request against both
 *    endpoints at download start and picks the first that answers, so
 *    users outside mainland China don't get stuck on the slow mirror.
 *    The probe result is cached for [PROBE_CACHE_TTL_MS].
 *  * The other two pin the download to a specific mirror.
 * Any object the mirror doesn't have (404) transparently falls back to
 * ModelScope for that request.
 *
 * Key design decisions:
 *  * **Accept-Encoding: identity** — OkHttp's default gzip handling
 *    silently decompresses the response, which makes `skip()` on the
 *    decompressed stream skip the WRONG number of raw-file bytes when
 *    resuming a Range request.  Forcing identity encoding ensures that
 *    the byte stream we read is a 1:1 mapping to the file on disk.
 *  * **Parallel chunk download** — Files larger than [CHUNK_THRESHOLD]
 *    are split into [PARALLEL_CHUNKS] ranges and fetched concurrently.
 *    Each chunk has independent retry logic; a failure only costs one
 *    chunk, not the entire file.  This also speeds up the download
 *    significantly (4× on a bandwidth-limited but multi-connection-
 *    friendly CDN).  Chunks write straight into the shared `.part`
 *    file through a [java.io.RandomAccessFile], so peak disk usage is
 *    1× the file size instead of 2×.
 *  * **Range is mandatory for chunking** — every request that covers
 *    less than the whole object carries a `Range` header, *including*
 *    chunk 0 (`bytes=0-N`).  Omitting it made the server return the
 *    entire multi-GB file for chunk 0, which failed the size check and
 *    burned 15 retries re-downloading gigabytes.
 *  * ModelScope answers Range requests on non-LFS files with **200 +
 *    `Content-Range`** (instead of 206).  Presence of `Content-Range`
 *    — not the status code — is what tells us the range was honoured.
 *  * The complete file manifest (name + size + sha256) is baked in so
 *    the download can run without calling the ModelScope file-listing API.
 *  * Every file is downloaded to a staging area, verified (size +
 *    SHA-256), then atomically renamed into place.
 *  * [deleteModel] wipes the whole model directory.
 */
@Singleton
class OnDeviceModelManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "OnDeviceModelManager"

        /** Local directory name — keep stable regardless of mirror used. */
        private const val MODEL_REPO_DIR = "MNN/Qwen3-VL-8B-Instruct-MNN"

        /** SharedPreferences file + keys for the download-source preference. */
        private const val PREFS_NAME = "ai_model_download"
        private const val KEY_SOURCE_PREF = "source_preference"
        private const val KEY_PROBE_RESULT = "probe_result"
        private const val KEY_PROBE_TIME = "probe_time"

        /**
         * How long a cached AUTO probe result stays valid. A device's
         * network region rarely changes; re-probing daily keeps travellers
         * correct without paying a probe request on every download start.
         */
        private const val PROBE_CACHE_TTL_MS = 24L * 60 * 60 * 1000 // 24 h

        /** Hard cap for the whole probe race, independent of HTTP timeouts. */
        private const val PROBE_TIMEOUT_MS = 8_000L

        /** Tiny file used for the mirror speed probe (605 bytes). */
        private const val PROBE_FILE = "config.json"

        /** Manifest of files that make up a runnable model (from ModelScope). */
        private val MODEL_FILES = listOf(
            ModelFile("config.json", 605L, "1ed5c6e65459fdc4b0c33319715b763005013ba8580dd3c687bd2651546ca2a4"),
            ModelFile("configuration.json", 46L, "56c362f108bb9f17fc0e7b490b7e8927c03dc4478631b4f771c37728019beaa2"),
            ModelFile("embeddings_int4.bin", 388_956_160L, "311cd44e48dcec950bcc5cffb465ae0e2fec8154247f2ff545b7fd1240960bf4"),
            ModelFile("llm.mnn", 591_728L, "61f0fe3b5d0447518ae5b26ab19c0b8a7f07c51f598be5f52fa0aee7405972e3"),
            ModelFile("llm.mnn.json", 1_245_313L, "785b31ac0e0b4def1a9e707cd0876fc1e072f957db61d56868c89e0f8219aa2a"),
            ModelFile("llm.mnn.weight", 4_732_532_162L, "a52971cb29c0bef35ab336b370db00d676223e6d721b99e3bf0b9c70f2d9bcfe"),
            ModelFile("llm_config.json", 6_436L, "ce8f3a6532832d37eff85cf45ddb24b4d21567d293cfc8c64a67fa0fdca93df9"),
            ModelFile("tokenizer.txt", 3_193_555L, "7119de4966cc6a8ae87d7f083e65b315282d06c3122fdd41ce783fdd2d3c1ca2"),
            ModelFile("visual.mnn", 562_048L, "8f1653348a94c29e56d47529dd461215c80d5380263136adee7945809f407bf7"),
            ModelFile("visual.mnn.weight", 326_728_744L, "6d8b1a4886cca9ab8b8c86979f4e5291e126ac930124e9a2361833e7b11301e6")
        )

        /** Total bytes to download (weight files dominate). */
        val TOTAL_BYTES: Long = MODEL_FILES.sumOf { it.size }

        /** Extra head-room required on disk beyond the model itself. */
        private const val FREE_SPACE_MARGIN_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB

        /**
         * Progress tick granularity. 4 MB balances responsiveness with
         * overhead — at 50 MB/s that's ~12 ticks/s.
         */
        private const val DOWNLOAD_CHUNK_BYTES = 4L * 1024 * 1024

        /**
         * I/O buffer size for both download and SHA-256 verification.
         * 256 KB amortises system-call overhead and reduces the chance
         * of a partial write on connection abort.
         */
        private const val BUFFER_SIZE = 256 * 1024

        /**
         * Maximum per-file download attempts before giving up.
         * Large LFS files (>4 GB) on ModelScope's CDN can close the
         * connection prematurely; retrying from the last byte avoids
         * restarting the whole download.
         */
        private const val MAX_FILE_RETRIES = 10

        /** Read timeout long enough for cold CDN connections on slow links. */
        private const val READ_TIMEOUT_SECONDS = 180L

        /** Connect timeout for establishing TCP + TLS. */
        private const val CONNECT_TIMEOUT_SECONDS = 45L

        /** Write timeout — prevents stalls when the OS buffer is full. */
        private const val WRITE_TIMEOUT_SECONDS = 60L

        /**
         * Window size for the speed-tracker sliding window.
         * We keep the last [SPEED_WINDOW_SIZE] progress samples to compute
         * a smoothed instantaneous speed.
         */
        private const val SPEED_WINDOW_SIZE = 8

        /**
         * Base delay (ms) for exponential backoff between retries.
         * Sequence: 1s, 2s, 4s, 8s, 16s, 30s, 60s, 60s, …
         */
        private const val RETRY_BACKOFF_BASE_MS = 1000L

        /** Cap for the backoff delay. */
        private const val RETRY_BACKOFF_CAP_MS = 60_000L

        /**
         * Files larger than this threshold are downloaded using parallel
         * chunks for speed and resilience.
         */
        private const val CHUNK_THRESHOLD = 100L * 1024 * 1024 // 100 MB

        /** Number of parallel connections for chunk-based downloads. */
        private const val PARALLEL_CHUNKS = 4

        /**
         * Files smaller than this are never resumed — a failure restarts
         * them from byte 0 with a plain full GET.
         *
         * ModelScope serves non-LFS objects straight from www.modelscope.cn,
         * whose range endpoint answers some offsets with 404 "文件内容为空".
         * Re-fetching a few MB in one request is both cheaper and far more
         * reliable than resuming through that code path.
         */
        private const val RESUME_THRESHOLD_BYTES = 32L * 1024 * 1024 // 32 MB

        /**
         * Maximum retries per individual chunk before giving up on the
         * whole file.
         */
        private const val MAX_CHUNK_RETRIES = 15
    }

    /** One file of the model package. */
    private data class ModelFile(val name: String, val size: Long, val sha256: String)

    /** A mirror that serves the model package. */
    enum class ModelSource {
        MODELSCOPE,
        HUGGINGFACE;

        fun resolveUrl(fileName: String): String = when (this) {
            MODELSCOPE ->
                "https://www.modelscope.cn/models/MNN/Qwen3-VL-8B-Instruct-MNN/resolve/master/$fileName"
            HUGGINGFACE ->
                "https://huggingface.co/taobao-mnn/Qwen3-VL-8B-Instruct-MNN/resolve/main/$fileName"
        }
    }

    /** User-facing download-source preference (persisted). */
    enum class SourcePreference {
        /** Probe both mirrors at download start and use the fastest. */
        AUTO,
        MODELSCOPE,
        HUGGINGFACE;

        fun toSourceOrNull(): ModelSource? = when (this) {
            AUTO -> null
            MODELSCOPE -> ModelSource.MODELSCOPE
            HUGGINGFACE -> ModelSource.HUGGINGFACE
        }
    }

    /**
     * Thrown when the endpoint answers a `Range` request with the full
     * entity (no `Content-Range`), meaning partial transfers are
     * unavailable for that object. Callers react by restarting the file
     * over a single full-length connection instead of retrying ranges.
     */
    private class RangeNotSupportedException(message: String) : IOException(message)

    /** Lifecycle of the on-device model, surfaced to the UI. */
    sealed interface ModelState {
        /** No usable model on disk (may still contain partial downloads). */
        data object NotDownloaded : ModelState

        /** Actively fetching files; [progress] is 0..1 across the whole package. */
        data class Downloading(
            val progress: Float,
            val downloadedBytes: Long,
            val totalBytes: Long,
            val currentFile: String,
            /** Instantaneous download speed in bytes/sec (0 until first sample). */
            val speedBps: Long = 0L,
            /** Estimated seconds remaining (-1 = unknown / still calculating). */
            val etaSeconds: Long = -1L
        ) : ModelState

        /** All files present and verified. */
        data class Ready(val totalBytes: Long) : ModelState

        /** Terminal failure; [reason] is user-presentable. */
        data class Failed(val reason: String) : ModelState
    }

    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    // ---- Download-source preference --------------------------------------

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _sourcePreference = MutableStateFlow(loadSourcePreference())

    /** Currently selected download-source preference, surfaced to the UI. */
    val sourcePreference: StateFlow<SourcePreference> = _sourcePreference.asStateFlow()

    private fun loadSourcePreference(): SourcePreference {
        val name = prefs.getString(KEY_SOURCE_PREF, null)
        return SourcePreference.entries.firstOrNull { it.name == name }
            ?: SourcePreference.AUTO
    }

    /** Persists [pref]; takes effect on the next download start. */
    fun setSourcePreference(pref: SourcePreference) {
        prefs.edit().putString(KEY_SOURCE_PREF, pref.name).apply()
        _sourcePreference.value = pref
        // Invalidate the probe cache when switching back to AUTO so the
        // next download re-measures instead of reusing a stale winner.
        if (pref == SourcePreference.AUTO) {
            prefs.edit().remove(KEY_PROBE_RESULT).remove(KEY_PROBE_TIME).apply()
        }
    }

    /** Guards download/delete against concurrent invocations. */
    private val downloadMutex = Mutex()

    @Volatile
    private var cancelled = false

    // ---- Speed tracker --------------------------------------------------

    private val speedSamples = ArrayDeque<Pair<Long, Long>>(SPEED_WINDOW_SIZE + 1)
    private var currentSpeedBps: Long = 0L

    @Synchronized
    private fun recordSpeedSample(bytesSoFar: Long) {
        val now = System.nanoTime()
        speedSamples.addLast(now to bytesSoFar)
        if (speedSamples.size > SPEED_WINDOW_SIZE) speedSamples.removeFirst()
        if (speedSamples.size >= 2) {
            val oldest = speedSamples.first()
            val elapsedNs = now - oldest.first
            if (elapsedNs > 0) {
                currentSpeedBps = (bytesSoFar - oldest.second) * 1_000_000_000L / elapsedNs
            }
        }
    }

    @Synchronized
    private fun resetSpeedTracker() {
        speedSamples.clear()
        currentSpeedBps = 0L
    }

    // ---- OkHttp client --------------------------------------------------

    /**
     * We build two clients:
     *  - [downloadClient] for actual data transfer (no transparent gzip).
     *  - A general-purpose client is not needed here.
     *
     * **Critical**: we disable OkHttp's transparent gzip decompression
     * by adding `Accept-Encoding: identity` to every request.  When the
     * CDN returns gzip-compressed content, OkHttp wraps the body in a
     * decompressing stream, but the `Content-Length` header still
     * reports the *compressed* size.  This means:
     *   - `skip()` on the decompressed stream skips a different number
     *     of raw file bytes than expected;
     *   - `FileOutputStream.append` at byte offsets derived from the
     *     decompressed stream misaligns with the actual file layout;
     *   - the final file has the right size but wrong SHA-256 because
     *     bytes were shifted.
     *
     * Forcing `identity` encoding ensures a 1:1 mapping between the
     * network stream and the bytes on disk.
     */
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(PARALLEL_CHUNKS + 2, 5, TimeUnit.MINUTES))
        .build()

    /**
     * Short-timeout client used only for the mirror speed probe — the
     * probe must fail fast so a dead endpoint doesn't stall the race.
     * Shares the dispatcher/pool with [downloadClient].
     */
    private val probeClient = downloadClient.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    // ---- Mirror selection -------------------------------------------------

    /**
     * Resolves which mirror the next download should use.
     * A pinned preference is returned as-is; [SourcePreference.AUTO] runs
     * (or reuses) a probe race between both endpoints.
     */
    private fun resolveDownloadSource(): ModelSource {
        _sourcePreference.value.toSourceOrNull()?.let { return it }

        // AUTO: reuse a fresh cached probe result when available.
        val cached = prefs.getString(KEY_PROBE_RESULT, null)
        val probedAt = prefs.getLong(KEY_PROBE_TIME, 0L)
        val cacheValid = System.currentTimeMillis() - probedAt < PROBE_CACHE_TTL_MS
        if (cacheValid) {
            ModelSource.entries.firstOrNull { it.name == cached }?.let {
                Log.d(TAG, "AUTO source: reusing cached probe result $it")
                return it
            }
        }

        val winner = probeFastestSource()
        prefs.edit()
            .putString(KEY_PROBE_RESULT, winner.name)
            .putLong(KEY_PROBE_TIME, System.currentTimeMillis())
            .apply()
        return winner
    }

    /**
     * Races a 1-byte range request for [PROBE_FILE] against every mirror
     * and returns the first endpoint that answers successfully. If none
     * answers within [PROBE_TIMEOUT_MS], defaults to [ModelSource.MODELSCOPE]
     * (historical behaviour).
     */
    private fun probeFastestSource(): ModelSource {
        val winner = AtomicReference<ModelSource?>(null)
        val latch = CountDownLatch(1)
        val calls = CopyOnWriteArrayList<okhttp3.Call>()

        val threads = ModelSource.entries.map { source ->
            thread(name = "model-probe-${source.name}", isDaemon = true) {
                try {
                    val call = probeClient.newCall(
                        Request.Builder()
                            .url(source.resolveUrl(PROBE_FILE))
                            .header("Accept-Encoding", "identity")
                            .header("Range", "bytes=0-0")
                            .build()
                    )
                    calls.add(call)
                    call.execute().use { response ->
                        if (response.isSuccessful &&
                            winner.compareAndSet(null, source)
                        ) {
                            Log.d(TAG, "Probe winner: $source")
                            latch.countDown()
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Probe failed for $source: ${e.message}")
                }
            }
        }

        try {
            latch.await(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        // Abandon whichever probes are still in flight.
        calls.forEach { it.cancel() }
        threads.forEach { it.join(500) }

        return winner.get() ?: ModelSource.MODELSCOPE.also {
            Log.w(TAG, "Probe inconclusive — defaulting to MODELSCOPE")
        }
    }

    // ---- Public API -----------------------------------------------------

    /** Directory where the model package lives (app-private storage). */
    fun modelDir(): File = File(context.filesDir, "models/$MODEL_REPO_DIR")

    fun isModelReady(): Boolean = missingFiles().isEmpty()

    fun downloadedBytes(): Long {
        var total = 0L
        val dir = modelDir()
        MODEL_FILES.forEach { mf ->
            val done = File(dir, mf.name)
            if (done.exists() && done.length() == mf.size) {
                total += mf.size
            } else {
                // Chunked downloads write out of order, so the part file can
                // be longer than the bytes actually received — cap it.
                val part = File(dir, "${mf.name}.part")
                if (part.exists()) total += part.length().coerceAtMost(mf.size)
            }
        }
        return total
    }

    private fun missingFiles(): List<ModelFile> {
        val dir = modelDir()
        return MODEL_FILES.filter { mf ->
            val f = File(dir, mf.name)
            !f.exists() || f.length() != mf.size
        }
    }

    private fun backoffMs(attemptNumber: Int): Long {
        if (attemptNumber <= 0) return 0L
        val exponential = RETRY_BACKOFF_BASE_MS * (1L shl (attemptNumber - 1))
        return exponential.coerceAtMost(RETRY_BACKOFF_CAP_MS)
    }

    /**
     * Starts (or resumes) downloading the model.
     * Safe to call repeatedly: an in-flight download is a no-op, and a
     * completed download transitions straight to Ready.
     */
    suspend fun downloadModel(): Result<Unit> = downloadMutex.withLock {
        cancelled = false
        resetSpeedTracker()
        val dir = modelDir()

        if (missingFiles().isEmpty()) {
            _state.value = ModelState.Ready(TOTAL_BYTES)
            return Result.success(Unit)
        }

        val stat = StatFs(context.filesDir.absolutePath)
        val freeBytes = stat.availableBytes
        if (freeBytes < TOTAL_BYTES + FREE_SPACE_MARGIN_BYTES - downloadedBytes()) {
            val reason = "Insufficient storage: need ~${(TOTAL_BYTES + FREE_SPACE_MARGIN_BYTES) / (1024L * 1024 * 1024)} GB free, only ${freeBytes / (1024L * 1024 * 1024)} GB available"
            Log.e(TAG, reason)
            _state.value = ModelState.Failed(reason)
            return Result.failure(IOException(reason))
        }

        if (!dir.exists()) dir.mkdirs()

        // Pick the mirror once for the whole run — pinned by preference,
        // or measured by a probe race when set to AUTO.
        val source = withContext(Dispatchers.IO) { resolveDownloadSource() }
        Log.i(TAG, "Downloading model from $source")

        return try {
            withContext(Dispatchers.IO) {
                var downloaded = MODEL_FILES.sumOf { mf ->
                    val f = File(dir, mf.name)
                    if (f.exists() && f.length() == mf.size) mf.size else 0L
                }

                for (mf in MODEL_FILES) {
                    if (cancelled) {
                        _state.value = ModelState.NotDownloaded
                        return@withContext Result.failure(IOException("Download cancelled"))
                    }

                    val finalFile = File(dir, mf.name)
                    if (finalFile.exists() && finalFile.length() == mf.size) continue

                    val partFile = File(dir, "${mf.name}.part")

                    // Clean up stale .part files that exceed the expected size.
                    if (partFile.exists() && partFile.length() > mf.size) {
                        Log.w(TAG, "Part file ${mf.name}.part (${partFile.length()}B) " +
                                "exceeds expected ${mf.size}B — deleting")
                        partFile.delete()
                    }

                    // Remove stale chunk files from a previous interrupted download.
                    cleanChunkFiles(dir, mf.name)

                    val emitProgress = { bytesSoFar: Long ->
                        // Clamp: concurrent chunks + retries must never let
                        // the reported figure exceed the real package size.
                        val overall = (downloaded + bytesSoFar).coerceIn(0L, TOTAL_BYTES)
                        recordSpeedSample(overall)
                        val remaining = TOTAL_BYTES - overall
                        val eta = if (currentSpeedBps > 0) remaining / currentSpeedBps else -1L
                        _state.value = ModelState.Downloading(
                            progress = (overall.toFloat() / TOTAL_BYTES).coerceIn(0f, 1f),
                            downloadedBytes = overall,
                            totalBytes = TOTAL_BYTES,
                            currentFile = mf.name,
                            speedBps = currentSpeedBps,
                            etaSeconds = eta
                        )
                    }

                    if (mf.size >= CHUNK_THRESHOLD) {
                        try {
                            downloadParallelChunks(mf, source, dir, emitProgress)
                        } catch (e: RangeNotSupportedException) {
                            // CDN refuses ranges — degrade gracefully to a
                            // single full-length connection instead of failing.
                            Log.w(TAG, "Range unsupported for ${mf.name}, " +
                                    "falling back to a single connection")
                            // Chunk writes may have left a sparse .part whose
                            // length lies about real progress — start clean.
                            partFile.delete()
                            resetSpeedTracker()
                            downloadSmallFile(mf, source, partFile, emitProgress)
                        }
                    } else {
                        downloadSmallFile(mf, source, partFile, emitProgress)
                    }

                    // Verify then atomically promote.
                    verifyFile(partFile, mf)
                    if (finalFile.exists()) finalFile.delete()
                    if (!partFile.renameTo(finalFile)) {
                        throw IOException("Failed to finalize ${mf.name}")
                    }
                    downloaded += mf.size
                }

                _state.value = ModelState.Ready(TOTAL_BYTES)
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Model download failed", e)
            _state.value = ModelState.Failed(e.message ?: "Download failed")
            Result.failure(e)
        }
    }

    fun cancelDownload() {
        cancelled = true
    }

    suspend fun deleteModel(): Result<Unit> = downloadMutex.withLock {
        withContext(Dispatchers.IO) {
            val dir = modelDir()
            if (dir.exists()) {
                dir.listFiles()?.forEach { it.delete() }
                dir.delete()
            }
            _state.value = ModelState.NotDownloaded
            Result.success(Unit)
        }
    }

    fun refreshState() {
        _state.value = if (missingFiles().isEmpty()) {
            ModelState.Ready(TOTAL_BYTES)
        } else {
            ModelState.NotDownloaded
        }
    }

    fun readyModelDir(): File? = if (isModelReady()) modelDir() else null

    // ---- Small-file download (single connection, with resume) -----------

    /**
     * Downloads a file using a single HTTP connection with Range-based
     * resume. Also serves as the fallback path for large files whose
     * endpoint refuses Range requests.
     *
     * When the transport reports [RangeNotSupportedException] the partial
     * file is worthless (it would hold a second copy of the head of the
     * object), so we delete it and restart from byte 0 on the next
     * attempt instead of trying to resume again.
     */
    private fun downloadSmallFile(
        mf: ModelFile,
        source: ModelSource,
        partFile: File,
        onProgress: (bytesSoFar: Long) -> Unit
    ) {
        var attempt = 0
        while (attempt < MAX_FILE_RETRIES) {
            attempt++
            if (cancelled) throw IOException("Download cancelled")
            val startByte = if (partFile.exists()) partFile.length().coerceAtMost(mf.size) else 0L
            if (startByte == mf.size) {
                onProgress(mf.size)
                return
            }
            try {
                var cumulative = startByte
                var lastTick = startByte
                FileOutputStream(partFile, startByte > 0).use { output ->
                    val sink: (ByteArray, Int) -> Unit = { buffer, count ->
                        output.write(buffer, 0, count)
                    }
                    downloadRange(mf, source, startByte, mf.size, { read ->
                        cumulative += read
                        if (cumulative - lastTick >= DOWNLOAD_CHUNK_BYTES) {
                            lastTick = cumulative
                            onProgress(cumulative)
                        }
                    }, sink)
                }
                onProgress(mf.size)
                return
            } catch (e: RangeNotSupportedException) {
                Log.w(TAG, "Range not honoured for ${mf.name}; restarting from byte 0")
                partFile.delete()
                if (attempt >= MAX_FILE_RETRIES) throw e
            } catch (e: IOException) {
                // ModelScope's non-LFS range endpoint answers some offsets with
                // 404 "文件内容为空". Small files are cheap to re-fetch with a
                // plain full GET, which is the reliable path, so never try to
                // resume them — just restart.
                if (mf.size < RESUME_THRESHOLD_BYTES) partFile.delete()
                handleDownloadException(e, mf, partFile, attempt, onProgress)
            }
        }
    }

    // ---- Parallel chunk download for large files -----------------------

    /**
     * Downloads a large file using [PARALLEL_CHUNKS] concurrent HTTP
     * Range requests.  Every chunk writes **directly** into the shared
     * `.part` file through a [RandomAccessFile] seeked to the chunk
     * offset — there is no per-chunk temp file and no final
     * concatenation.
     *
     * Why this matters: the previous "N temp files, then concatenate"
     * scheme held chunk files *and* the assembled part on disk at the
     * same time, i.e. 2× the file size — about 9.4 GB of head-room for
     * the 4.7 GB weight file. Writing in place keeps peak usage at 1×.
     *
     * Advantages over single-connection resume:
     *  - 4× bandwidth on CDN endpoints that throttle per-connection.
     *  - A single-chunk failure only costs that chunk, not the whole file.
     *
     * @throws RangeNotSupportedException when the endpoint cannot serve
     *   partial content; the caller falls back to one full-length request.
     */
    private fun downloadParallelChunks(
        mf: ModelFile,
        source: ModelSource,
        dir: File,
        onProgress: (bytesSoFar: Long) -> Unit
    ) {
        val partFile = File(dir, "${mf.name}.part")
        val chunkSize = (mf.size + PARALLEL_CHUNKS - 1) / PARALLEL_CHUNKS

        // Build chunk descriptors.
        val chunks = (0 until PARALLEL_CHUNKS).map { i ->
            val start = i * chunkSize
            val end = (start + chunkSize).coerceAtMost(mf.size)
            ChunkDesc(i, start, end)
        }.filter { it.start < it.end } // last chunk may be empty

        // Per-chunk completed bytes for the *current* attempt. The file's
        // progress is the sum of the slots; a retrying chunk resets its own
        // slot first, so the reported total never inflates across retries.
        val chunkProgress = AtomicLongArray(chunks.size)
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        // Download each chunk in its own thread.
        val threads = chunks.map { chunk ->
            Thread {
                try {
                    downloadChunkWithRetry(mf, source, partFile, chunk, chunkProgress, onProgress)
                } catch (e: Exception) {
                    errors.add(e)
                }
            }.also { it.start() }
        }

        // Wait for all threads.
        threads.forEach { it.join() }

        if (errors.isNotEmpty()) {
            val first = errors.first()
            // Range support is a hard requirement for chunking — surface it
            // verbatim so the caller can degrade to a single connection
            // instead of reporting a generic I/O failure.
            if (first is RangeNotSupportedException) throw first
            throw IOException("Parallel download of ${mf.name} failed: ${first.message}", first)
        }

        // Final progress report.
        onProgress(mf.size)
    }

    /** Byte range owned by one parallel worker. */
    private data class ChunkDesc(
        val index: Int,
        val start: Long,
        val end: Long
    ) {
        val size: Long get() = end - start
    }

    /**
     * Downloads a single chunk with retry, writing straight into
     * [partFile] at [ChunkDesc.start].
     *
     * A retry rewrites the very same byte range from scratch, which is
     * safe (and simpler) because the chunk is small relative to the
     * whole file and avoids any corrupted-append pitfall.
     *
     * [chunkProgress] is shared across all chunk threads; slot
     * [ChunkDesc.index] holds this chunk's completed bytes so the caller
     * can report an accurate overall figure.
     */
    private fun downloadChunkWithRetry(
        mf: ModelFile,
        source: ModelSource,
        partFile: File,
        chunk: ChunkDesc,
        chunkProgress: AtomicLongArray,
        onProgress: (overallBytes: Long) -> Unit
    ) {
        var attempt = 0
        while (attempt < MAX_CHUNK_RETRIES) {
            if (cancelled) throw IOException("Download cancelled")
            attempt++
            // Discard this chunk's previous contribution before re-reading.
            chunkProgress.set(chunk.index, 0L)
            var written = 0L
            var lastTickOverall = 0L
            try {
                RandomAccessFile(partFile, "rw").use { raf ->
                    raf.seek(chunk.start)
                    val sink: (ByteArray, Int) -> Unit = { buffer, count ->
                        raf.write(buffer, 0, count)
                    }
                    downloadRange(mf, source, chunk.start, chunk.end, { read ->
                        written += read
                        chunkProgress.set(chunk.index, written)
                        val overall = sumChunkProgress(chunkProgress)
                        if (overall - lastTickOverall >= DOWNLOAD_CHUNK_BYTES) {
                            lastTickOverall = overall
                            onProgress(overall)
                        }
                    }, sink)
                    raf.fd.sync()
                }
                chunkProgress.set(chunk.index, chunk.size)
                onProgress(sumChunkProgress(chunkProgress))
                return
            } catch (e: RangeNotSupportedException) {
                // Retrying is pointless — the server cannot serve ranges.
                throw e
            } catch (e: IOException) {
                if (attempt >= MAX_CHUNK_RETRIES) {
                    throw IOException("Chunk ${chunk.index} of ${mf.name} failed " +
                            "after $MAX_CHUNK_RETRIES attempts: ${e.message}", e)
                }
                val delayMs = backoffMs(attempt)
                Log.w(TAG, "Chunk ${chunk.index} retry $attempt/$MAX_CHUNK_RETRIES " +
                        "for ${mf.name}: $written/${chunk.size}B (${e.message}). " +
                        "Waiting ${delayMs}ms")
                if (delayMs > 0) {
                    try { Thread.sleep(delayMs) } catch (_: InterruptedException) {}
                }
            }
        }
    }

    /** Sums every slot of [values] to get the whole-file progress. */
    private fun sumChunkProgress(values: AtomicLongArray): Long {
        var total = 0L
        for (i in 0 until values.length()) total += values.get(i)
        return total
    }

    /**
     * Removes leftover `.chunkN` temp files for [baseName].
     *
     * Chunked downloads no longer create these (chunks write directly
     * into the `.part` file), but a device upgrading from an older build
     * may still hold gigabytes of orphaned ones.
     */
    private fun cleanChunkFiles(dir: File, baseName: String) {
        dir.listFiles()?.filter {
            it.name.startsWith("$baseName.chunk")
        }?.forEach { it.delete() }
    }

    // ---- Core download primitive ----------------------------------------

    /**
     * Core transport primitive.  Streams bytes [startByte, endByte) of
     * model file [mf] and hands each filled buffer to [write], which is
     * responsible for putting those bytes at the right place on disk.
     *
     * A `Range` header is attached whenever the request covers **less
     * than the whole object** — that includes chunk 0 (`bytes=0-N`).
     * Skipping it for chunk 0 used to make the server reply with the
     * entire multi-GB file, fail the size check below, and burn every
     * retry re-downloading gigabytes.
     *
     * When [source] answers 404 the object simply doesn't exist on that
     * mirror (e.g. `configuration.json` is absent from Hugging Face), so
     * the request transparently falls back to ModelScope.
     *
     * @param startByte    First byte of the range (inclusive).
     * @param endByte      Last byte of the range (exclusive).
     * @param onBytesRead  Incremental byte counter — bytes just read from
     *                     the network in each buffer fill, NOT cumulative.
     * @param write        Sink receiving `(buffer, count)` pairs.
     *
     * Always sends `Accept-Encoding: identity` to prevent CDN gzip
     * compression, which would corrupt Range-based byte offsets.
     *
     * @throws RangeNotSupportedException when a partial request comes back
     *   as the full entity.
     */
    private fun downloadRange(
        mf: ModelFile,
        source: ModelSource,
        startByte: Long,
        endByte: Long,
        onBytesRead: (bytesRead: Int) -> Unit,
        write: (buffer: ByteArray, count: Int) -> Unit
    ) {
        val url = source.resolveUrl(mf.name)
        val partial = startByte > 0 || endByte < mf.size

        val requestBuilder = Request.Builder()
            .url(url)
            // CRITICAL: prevent CDN gzip — see class-level doc.
            .header("Accept-Encoding", "identity")

        if (partial) {
            requestBuilder.header("Range", "bytes=$startByte-${endByte - 1}")
        }

        downloadClient.newCall(requestBuilder.build()).execute().use { response ->
            // Mirror lacks this object — fall back to the primary repo.
            if (response.code == 404 && source != ModelSource.MODELSCOPE) {
                Log.w(TAG, "$source has no ${mf.name} (404) — retrying via MODELSCOPE")
                response.close()
                downloadRange(mf, ModelSource.MODELSCOPE, startByte, endByte, onBytesRead, write)
                return
            }

            if (response.code !in 200..299) {
                throw IOException("HTTP ${response.code} while downloading ${mf.name} from $source")
            }

            // ModelScope answers Range requests on non-LFS files with **200 +
            // Content-Range** rather than 206, so the header — not the status
            // code — is what proves the range was honoured.
            val rangeHonored = response.code == 206 || response.header("Content-Range") != null
            if (partial && !rangeHonored) {
                throw RangeNotSupportedException(
                    "Server returned ${response.code} without Content-Range for " +
                        "${mf.name} (Range: bytes=$startByte-${endByte - 1})"
                )
            }

            val body = response.body ?: throw IOException("Empty body for ${mf.name}")
            val expectedBytes = endByte - startByte

            body.byteStream().use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                var totalRead = 0L
                while (true) {
                    if (cancelled) throw IOException("Download cancelled")
                    val read = try {
                        input.read(buffer)
                    } catch (e: SocketException) {
                        Log.w(TAG, "SocketException reading ${mf.name}: ${e.message}")
                        -1
                    }
                    if (read == -1) break
                    write(buffer, read)
                    totalRead += read
                    onBytesRead(read)
                }

                if (totalRead != expectedBytes) {
                    throw IOException("Size mismatch for ${mf.name} range " +
                            "$startByte-${endByte - 1}: got $totalRead, " +
                            "expected $expectedBytes")
                }
            }
        }
    }

    // ---- Retry exception handler ----------------------------------------

    /**
     * Handles an [IOException] from a download attempt.  If the part
     * file has reached the expected size, we let verification decide.
     * Otherwise we retry from the current file position.
     */
    private fun handleDownloadException(
        e: IOException,
        mf: ModelFile,
        partFile: File,
        attempt: Int,
        onProgress: (bytesSoFar: Long) -> Unit
    ) {
        val partialSize = partFile.length()

        // If the part file already has the full size, the exception was
        // likely from the closing handshake — let verifyFile decide.
        if (partialSize == mf.size) {
            Log.i(TAG, "Part file ${mf.name}.part already at full size " +
                    "despite exception (${e.message}), proceeding to verify")
            return
        }

        if (attempt < MAX_FILE_RETRIES && partialSize < mf.size) {
            val delayMs = backoffMs(attempt)
            Log.w(TAG, "Retry $attempt/$MAX_FILE_RETRIES for ${mf.name}: " +
                    "got ${partialSize}/${mf.size} bytes (${e.message}). " +
                    "Waiting ${delayMs}ms before resume from byte $partialSize")
            if (delayMs > 0) {
                try { Thread.sleep(delayMs) } catch (_: InterruptedException) {}
            }
        } else {
            throw e
        }
    }

    // ---- SHA-256 verification -------------------------------------------

    /** Checks size and SHA-256 of a staged .part file before promotion. */
    private fun verifyFile(file: File, mf: ModelFile) {
        if (file.length() != mf.size) {
            val msg = "Verification failed for ${mf.name}: " +
                    "size ${file.length()} != ${mf.size}"
            Log.e(TAG, msg)
            file.delete()
            throw IOException(msg)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (!hash.equals(mf.sha256, ignoreCase = true)) {
            val msg = "SHA-256 mismatch for ${mf.name}: " +
                    "got $hash, expected ${mf.sha256}"
            Log.e(TAG, msg)
            // Delete the corrupt file so the next attempt starts fresh.
            file.delete()
            throw IOException(msg)
        }
        Log.i(TAG, "Verified ${mf.name}: SHA-256 OK")
    }
}
