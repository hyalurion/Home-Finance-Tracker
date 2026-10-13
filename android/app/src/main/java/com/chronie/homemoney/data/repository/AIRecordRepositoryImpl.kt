package com.chronie.homemoney.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.local.dao.SyncQueueDao
import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.local.entity.SyncQueueEntity
import com.chronie.homemoney.data.mapper.AIRecordMapper
import com.chronie.homemoney.data.mapper.ExpenseMapper
import com.chronie.homemoney.data.ocr.DocumentScanProcessor
import com.chronie.homemoney.data.remote.dto.AIExpenseRecordDto
import com.chronie.homemoney.data.vlm.MnnVlmEngine
import com.chronie.homemoney.data.vlm.OnDeviceModelManager
import com.chronie.homemoney.domain.model.AIExpenseRecord
import com.chronie.homemoney.domain.repository.AIRecordRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Record Repository Implementation — fully on-device recognition.
 *
 * Pipeline:
 *   1. [DocumentScanProcessor] rectifies and enhances each photo (OpenCV).
 *   2. Cleaned pages are written to cache JPEGs and referenced from the
 *      prompt via `<img>` markers.
 *   3. [MnnVlmEngine] (Qwen3-VL 8B, MNN runtime) reads the images and
 *      emits a JSON array of expense records.
 *   4. [AIRecordMapper] validates and converts them to domain models.
 *
 * No network access is performed anywhere in this class.
 */
@Singleton
class AIRecordRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val documentScanProcessor: DocumentScanProcessor,
    private val vlmEngine: MnnVlmEngine,
    private val modelManager: OnDeviceModelManager,
    private val expenseDao: ExpenseDao,
    private val syncQueueDao: SyncQueueDao,
    private val gson: Gson
) : AIRecordRepository {

    companion object {
        private const val TAG = "AIRecordRepository"

        /** Cache subdirectory holding the document-scanned pages for the VLM. */
        private const val VLM_CACHE_DIR = "vlm_pages"

        /** JPEG quality for the scanned pages fed into the vision model. */
        private const val PAGE_JPEG_QUALITY = 90
    }

    override suspend fun parseTextToRecords(text: String): Result<List<AIExpenseRecord>> {
        return try {
            Log.d(TAG, "Parsing text to records with on-device LLM")

            val loadResult = ensureEngineReady()
            if (loadResult != null) return Result.failure(loadResult)

            val prompt = buildPrompt(text = text, hasImages = false)
            val response = vlmEngine.generate(prompt).getOrElse { throw it }

            val records = parseAIResponse(response)
            Log.d(TAG, "Parsed ${records.size} records from text")
            Result.success(records)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse text", e)
            Result.failure(e)
        }
    }

    override suspend fun parseImagesToRecords(imageUris: List<Uri>): Result<List<AIExpenseRecord>> {
        return try {
            Log.d(TAG, "Parsing ${imageUris.size} images with document scan + on-device VLM")

            val loadResult = ensureEngineReady()
            if (loadResult != null) return Result.failure(loadResult)

            // Stage 1: documentize every image (OpenCV) and persist as JPEG.
            val imageFiles = withContext(Dispatchers.IO) {
                imageUris.mapNotNull { uri ->
                    val scan = documentScanProcessor.processUri(uri) ?: run {
                        Log.w(TAG, "Skipping undecodable image: $uri")
                        return@mapNotNull null
                    }
                    Log.d(
                        TAG,
                        "Document scan done: detected=${scan.documentDetected}, " +
                            "enhanced=${scan.enhanced}, size=${scan.bitmap.width}x${scan.bitmap.height}"
                    )
                    savePageBitmap(scan.bitmap)
                }
            }
            if (imageFiles.isEmpty()) {
                throw IllegalStateException("No readable images in the selection")
            }

            // Stage 2: one multimodal request over all pages.
            val imagePathList = imageFiles.map { it.absolutePath }
            val prompt = buildPrompt(text = null, hasImages = true)

            val response = vlmEngine.generate(prompt, imagePathList).getOrElse { throw it }

            // Clean up the cache pages; the model has consumed them.
            withContext(Dispatchers.IO) {
                imageFiles.forEach { it.delete() }
            }

            val records = parseAIResponse(response)
            Log.d(TAG, "Parsed ${records.size} records from images")
            Result.success(records)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse images", e)
            Result.failure(e)
        }
    }

    override suspend fun saveRecords(records: List<AIExpenseRecord>): Result<Unit> {
        return try {
            Log.d(TAG, "Saving ${records.size} AI records")

            val validRecords = records.filter { it.isValid }
            if (validRecords.isEmpty()) {
                Log.d(TAG, "No valid records to save")
                return Result.success(Unit)
            }

            val expenses = validRecords.map { aiRecord ->
                val uuid = java.util.UUID.randomUUID().toString()
                aiRecord.copy(id = uuid).toExpense()
            }

            val entities = expenses.map { ExpenseMapper.toEntity(it).copy(isSynced = false) }
            expenseDao.insertExpenses(entities)

            entities.forEach { entity ->
                addToSyncQueue("expense", entity.id, "CREATE", entity)
            }

            Log.d(TAG, "Successfully saved all ${validRecords.size} records")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save records", e)
            Result.failure(e)
        }
    }

    // ------------------------------------------------------------------
    // On-device engine lifecycle
    // ------------------------------------------------------------------

    /**
     * Verifies the model package is on disk and the native engine is loaded.
     * @return null when ready; otherwise the throwable to fail with.
     */
    private suspend fun ensureEngineReady(): Throwable? {
        if (!modelManager.isModelReady()) {
            val e = IllegalStateException(
                "The on-device AI model is not downloaded yet. Open Settings > AI to download it (~5.5 GB)."
            )
            return e
        }
        val loaded = vlmEngine.ensureLoaded(modelManager.modelDir())
        return loaded.exceptionOrNull()
    }

    /**
     * Persists a scanned page for the VLM and returns its file.
     * The vision model reads images from a plain filesystem path, so the
     * cleaned bitmap must be materialized as a JPEG in cache storage.
     */
    private fun savePageBitmap(bitmap: Bitmap): File? {
        return try {
            val dir = File(context.cacheDir, VLM_CACHE_DIR).apply { mkdirs() }
            val file = File(dir, "page_${System.currentTimeMillis()}_${(0..999).random()}.jpg")
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, PAGE_JPEG_QUALITY, out)
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache scanned page", e)
            null
        }
    }

    // ------------------------------------------------------------------
    // Prompt & response parsing
    // ------------------------------------------------------------------

    /**
     * Builds the instruction prompt for the multimodal model.
     * When [hasImages] is true, the C++ bridge will prepend <img> tags
     * automatically; the prompt just needs to reference "the bill images above".
     */
    private fun buildPrompt(text: String?, hasImages: Boolean): String {
        val today = java.time.LocalDate.now()
        val dayOfWeek = today.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.FULL,
            java.util.Locale.ENGLISH
        )
        val dateStr = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val contentPart = text?.let { "Text content: $text" }
            ?: if (hasImages) "Please read the bill images above." else ""

        return """
Today is $dateStr, $dayOfWeek. $contentPart Please extract all expense information from it.

If there are multiple expense records, output them as a JSON array. Each record must contain:
{
  "type": "expense category", // must be chosen from the following Chinese list: 日常用品、奢侈品、通讯费用、食品、零食糖果、冷饮、方便食品、纺织品、饮品、调味品、交通出行、餐饮、医疗费用、水果、其他、水产品、乳制品、礼物人情、旅行度假、政务、水电煤气、美容美发、豆制品、个护美妆、电子产品、家用电器、五金、服装
  "amount": amount, // numeric value
  "date": "date", // date format YYYY-MM-DD
  "remark": "remark" // name and description of the purchased item/service; keep the original language
}

Notes:
1. If there are multiple expense records, return a JSON array
2. If there is only one expense record, return a single JSON object or an array with one element
3. The "type" field must be exactly one of the Chinese categories listed above; do not use any other language or custom types
4. If no explicit date is found, use today's date ($dateStr)
5. Return only JSON data, with no extra content and no markdown code blocks
        """.trimIndent()
    }

    /**
     * Parse the model output to extract expense records.
     * @param content The response text from the on-device model
     * @return A list of AIExpenseRecord objects
     */
    private fun parseAIResponse(content: String): List<AIExpenseRecord> {
        return try {
            // Clean response content by removing Markdown code block markers
            val cleanContent = content
                .replace("```json", "")
                .replace("```", "")
                .trim()

            // Try to parse as array
            val listType = object : TypeToken<List<AIExpenseRecordDto>>() {}.type
            val dtoList: List<AIExpenseRecordDto> = try {
                gson.fromJson(cleanContent, listType)
            } catch (_: Exception) {
                // If array parsing fails, try parsing single object
                val singleDto = gson.fromJson(cleanContent, AIExpenseRecordDto::class.java)
                listOf(singleDto)
            }

            dtoList.map { AIRecordMapper.toDomain(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse model response", e)
            emptyList()
        }
    }

    /**
     * Add to sync queue for later processing
     */
    private suspend fun addToSyncQueue(
        entityType: String,
        entityId: String,
        operation: String,
        data: Any
    ) {
        val dto = when (data) {
            is ExpenseEntity -> ExpenseMapper.toDto(ExpenseMapper.toDomain(data))
            else -> data
        }

        val jsonData = gson.toJson(dto)
        val syncItem = SyncQueueEntity(
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            data = jsonData
        )
        syncQueueDao.insertSyncItem(syncItem)
    }
}
