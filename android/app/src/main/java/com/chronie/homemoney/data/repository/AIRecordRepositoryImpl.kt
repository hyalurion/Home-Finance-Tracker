package com.chronie.homemoney.data.repository

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.chronie.homemoney.core.common.LocalIdGenerator
import com.chronie.homemoney.data.mapper.AIRecordMapper
import com.chronie.homemoney.data.remote.api.AIRecordApi
import com.chronie.homemoney.data.remote.dto.*
import com.chronie.homemoney.domain.model.AIExpenseRecord
import com.chronie.homemoney.domain.repository.AIRecordRepository
import com.chronie.homemoney.domain.repository.ExpenseRepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 记录仓库实现
 */
@Singleton
class AIRecordRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val aiRecordApi: AIRecordApi,
    private val expenseRepository: ExpenseRepository,
    private val localIdGenerator: LocalIdGenerator,
    private val gson: Gson
) : AIRecordRepository {
    
    companion object {
        private const val TAG = "AIRecordRepository"
        private const val TEXT_MODEL = "Qwen/Qwen2.5-7B-Instruct"
        private const val IMAGE_MODEL = "THUDM/GLM-4.1V-9B-Thinking"
    }
    
    override suspend fun parseTextToRecords(text: String): Result<List<AIExpenseRecord>> {
        return try {
            Log.d(TAG, "Parsing text to records")
            
            val prompt = buildTextPrompt(text)
            val request = AIRecordRequest(
                model = TEXT_MODEL,
                messages = listOf(
                    AIMessage(
                        role = "system",
                        content = "你是一个智能消费记录解析助手，能够从文本中提取消费信息并格式化输出。"
                    ),
                    AIMessage(
                        role = "user",
                        content = prompt
                    )
                ),
                temperature = 0.2,
                stream = false
            )
            
            val response = aiRecordApi.parseRecord(request)
            
            if (!response.isSuccessful) {
                throw Exception("API returned error: ${response.code()}")
            }
            
            val content = response.body()?.choices?.firstOrNull()?.message?.content
                ?: throw Exception("Empty response from AI")
            
            val records = parseAIResponse(content)
            Log.d(TAG, "Parsed ${records.size} records from text")
            
            Result.success(records)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse text", e)
            Result.failure(e)
        }
    }
    
    override suspend fun parseImagesToRecords(imageUris: List<Uri>): Result<List<AIExpenseRecord>> {
        return try {
            Log.d(TAG, "Parsing ${imageUris.size} images to records")
            
            val base64Images = imageUris.map { uri ->
                uriToBase64(uri)
            }
            
            val prompt = buildImagePrompt()
            val messageContent = mutableListOf<AIMessageContent>()
            
            // 添加文本提示
            messageContent.add(
                AIMessageContent(
                    type = "text",
                    text = prompt
                )
            )
            
            // 添加所有图片
            base64Images.forEach { base64 ->
                messageContent.add(
                    AIMessageContent(
                        type = "image_url",
                        imageUrl = AIImageUrl(url = "data:image/jpeg;base64,$base64")
                    )
                )
            }
            
            val request = AIRecordRequest(
                model = IMAGE_MODEL,
                messages = listOf(
                    AIMessage(
                        role = "system",
                        content = "你是一个智能消费记录解析助手，能够从图片中提取消费信息并格式化输出。"
                    ),
                    AIMessage(
                        role = "user",
                        content = messageContent
                    )
                ),
                temperature = 0.2,
                stream = false
            )
            
            val response = aiRecordApi.parseRecord(request)
            
            if (!response.isSuccessful) {
                throw Exception("API returned error: ${response.code()}")
            }
            
            val content = response.body()?.choices?.firstOrNull()?.message?.content
                ?: throw Exception("Empty response from AI")
            
            val records = parseAIResponse(content)
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
            
            records.forEach { aiRecord ->
                if (aiRecord.isValid) {
                    val localId = localIdGenerator.generateNextLocalId()
                    val expense = aiRecord.copy(id = localId).toExpense()
                    expenseRepository.addExpense(expense)
                }
            }
            
            Log.d(TAG, "Successfully saved all records")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save records", e)
            Result.failure(e)
        }
    }
    
    /**
     * 构建文本解析提示
     */
    private fun buildTextPrompt(text: String): String {
        val today = java.time.LocalDate.now()
        val dayOfWeek = today.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.FULL,
            java.util.Locale.SIMPLIFIED_CHINESE
        )
        val dateStr = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        
        return """
今天是 $dateStr，星期$dayOfWeek。

请分析以下文本，提取其中的所有消费信息。如果有多个消费记录，请以JSON数组的形式输出。
每个记录应包含：
{
  "type": "消费类型", // 从预定义列表中选择：日常用品、奢侈品、通讯费用、食品、零食糖果、冷饮、方便食品、纺织品、饮品、调味品、交通出行、餐饮、医疗费用、水果、其他、水产品、乳制品、礼物人情、旅行度假、政务、水电煤气
  "amount": 金额, // 数字类型
  "date": "日期", // 日期格式 YYYY-MM-DD
  "remark": "备注" // 详细说明，注意：此处必须包含消费物品/服务的名称
}

请注意：
1. 如果文本中有多个消费记录，请返回JSON数组格式
2. 如果只有一个消费记录，请返回单个JSON对象或只有一个元素的数组
3. 如果文本中没有明确的消费类型，请根据内容选择最合适的预定义类型
4. 如果没有明确的日期，请使用今天日期（$dateStr）
5. 只返回JSON数据，不要添加其他无关内容，不要使用markdown代码块

文本内容：$text
        """.trimIndent()
    }
    
    /**
     * 构建图片解析提示
     */
    private fun buildImagePrompt(): String {
        val today = java.time.LocalDate.now()
        val dayOfWeek = today.dayOfWeek.getDisplayName(
            java.time.format.TextStyle.FULL,
            java.util.Locale.SIMPLIFIED_CHINESE
        )
        val dateStr = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        
        return """
今天是 $dateStr，星期$dayOfWeek。

请分析图片中的所有消费信息。如果有多个消费记录，请以JSON数组的形式输出。
每个记录应包含：
{
  "type": "消费类型", // 从预定义列表中选择：日常用品、奢侈品、通讯费用、食品、零食糖果、冷饮、方便食品、纺织品、饮品、调味品、交通出行、餐饮、医疗费用、水果、其他、水产品、乳制品、礼物人情、旅行度假、政务、水电煤气
  "amount": 金额, // 数字类型
  "date": "日期", // 日期格式 YYYY-MM-DD
  "remark": "备注" // 详细说明，注意：此处必须包含消费物品/服务的名称
}

请注意：
1. 如果图片中有多个消费记录，请返回JSON数组格式
2. 如果只有一个消费记录，请返回单个JSON对象或只有一个元素的数组
3. 如果图片中没有明确的消费类型，请根据内容选择最合适的预定义类型
4. 如果没有明确的日期，请使用今天日期（$dateStr）
5. 只返回JSON数据，不要添加其他无关内容，不要使用markdown代码块
        """.trimIndent()
    }
    
    /**
     * 解析 AI 响应
     */
    private fun parseAIResponse(content: String): List<AIExpenseRecord> {
        return try {
            // 清理响应内容，移除可能的 markdown 代码块标记
            val cleanContent = content
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            // 尝试解析为数组
            val listType = object : TypeToken<List<AIExpenseRecordDto>>() {}.type
            val dtoList: List<AIExpenseRecordDto> = try {
                gson.fromJson(cleanContent, listType)
            } catch (e: Exception) {
                // 如果解析数组失败，尝试解析单个对象
                val singleDto = gson.fromJson(cleanContent, AIExpenseRecordDto::class.java)
                listOf(singleDto)
            }
            
            dtoList.map { AIRecordMapper.toDomain(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AI response", e)
            emptyList()
        }
    }
    
    /**
     * 将 URI 转换为 Base64
     */
    private suspend fun uriToBase64(uri: Uri): String = withContext(Dispatchers.IO) {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open image")
        
        val bytes = ByteArrayOutputStream()
        inputStream.use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                bytes.write(buffer, 0, read)
            }
        }
        
        Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }
}
