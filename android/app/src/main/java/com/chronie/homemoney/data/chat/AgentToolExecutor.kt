package com.chronie.homemoney.data.chat

import android.util.Log
import com.chronie.homemoney.data.local.AiProviderConfigManager
import com.chronie.homemoney.data.local.dao.ExpenseDao
import com.chronie.homemoney.data.sync.SyncScheduler
import com.chronie.homemoney.domain.model.AIExpenseRecord
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.domain.model.chat.PendingToolCall
import com.chronie.homemoney.domain.model.chat.ToolCallRecord
import com.chronie.homemoney.domain.model.chat.ToolCallStatus
import com.chronie.homemoney.domain.repository.AIRecordRepository
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of one tool invocation, including the payload fed back to the model. */
data class ToolExecution(
    val record: ToolCallRecord,
    val resultForModel: String,
    val recordsCreated: Int = 0
)

/**
 * Executes the tools the AI may call against the user's local ledger.
 *
 * Two explicit consent gates protect the data:
 *  - query tools are only registered (and re-checked here) when the user has
 *    enabled "Allow AI to query records" in Settings;
 *  - writes can be forced into a per-call confirmation when "auto write" is off.
 *
 * Writes reuse [AIRecordRepository.saveRecords] so they go through the same
 * Room insert + sync_queue + immediate sync path as the legacy smart scan.
 */
@Singleton
class AgentToolExecutor @Inject constructor(
    private val aiRecordRepository: AIRecordRepository,
    private val expenseDao: ExpenseDao,
    private val configManager: AiProviderConfigManager,
    private val syncScheduler: SyncScheduler,
    private val gson: Gson
) {

    suspend fun execute(call: PendingToolCall, forceWrite: Boolean = false): ToolExecution {
        return try {
            val args = runCatching {
                gson.fromJson(call.argumentsJson, JsonObject::class.java)
            }.getOrNull() ?: JsonObject()

            when (call.name) {
                AgentToolSpecs.ADD_EXPENSE -> addExpense(call, args, forceWrite)
                AgentToolSpecs.QUERY_EXPENSES -> queryExpenses(call, args)
                AgentToolSpecs.SPENDING_SUMMARY -> spendingSummary(call, args)
                AgentToolSpecs.CURRENT_DATE -> currentDate(call)
                else -> failure(call, "Unknown tool: ${call.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tool ${call.name} failed", e)
            failure(call, e.message ?: "Tool execution failed")
        }
    }

    // ------------------------------------------------------------------
    // Write tool
    // ------------------------------------------------------------------

    private suspend fun addExpense(
        call: PendingToolCall,
        args: JsonObject,
        forceWrite: Boolean
    ): ToolExecution {
        val rawArray = args.getAsJsonArray("records") ?: JsonArray().apply { add(args) }
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        val records = rawArray.mapNotNull { element ->
            val obj = element.asJsonObject
            val amount = obj.getAsJsonPrimitive("amount")?.asDouble ?: return@mapNotNull null
            if (amount <= 0.0) return@mapNotNull null
            val category = obj.getAsJsonPrimitive("type")?.asString.orEmpty()
            val type = if (category in AgentToolSpecs.CATEGORIES) {
                ExpenseType.fromString(category)
            } else {
                ExpenseType.OTHER
            }
            val rawDate = obj.getAsJsonPrimitive("date")?.asString.orEmpty()
            val date = runCatching { LocalDate.parse(rawDate) }
                .getOrNull()?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: today
            val remark = obj.getAsJsonPrimitive("remark")?.asString?.takeIf { it.isNotBlank() }
                ?: category.ifBlank { "Expense" }
            AIExpenseRecord(type = type, amount = amount, date = date, remark = remark)
        }

        if (records.isEmpty()) {
            return failure(call, "No valid expense records in the tool arguments")
        }

        if (!forceWrite && !configManager.isAutoWriteRecords()) {
            val payload = JsonObject().apply {
                addProperty("status", "requires_confirmation")
                addProperty("pending_records", records.size)
                addProperty(
                    "message",
                    "The user must approve this write. Tell them briefly what will be saved."
                )
            }.toString()
            return ToolExecution(
                record = call.toRecord(
                    status = ToolCallStatus.PENDING_CONFIRM,
                    result = payload,
                    summary = "${records.size} pending"
                ),
                resultForModel = payload
            )
        }

        val saved = aiRecordRepository.saveRecords(records)
        if (saved.isFailure) {
            return failure(call, saved.exceptionOrNull()?.message ?: "Failed to save records")
        }
        runCatching { syncScheduler.triggerImmediateSync() }
            .onFailure { Log.w(TAG, "Immediate sync trigger failed", it) }

        val payload = JsonObject().apply {
            addProperty("status", "ok")
            addProperty("records_created", records.size)
            add("records", JsonArray().apply {
                records.forEach { r ->
                    add(JsonObject().apply {
                        addProperty("category", r.type.name)
                        addProperty("amount", r.amount)
                        addProperty("date", r.date)
                        addProperty("remark", r.remark)
                    })
                }
            })
        }.toString()

        return ToolExecution(
            record = call.toRecord(
                status = ToolCallStatus.SUCCESS,
                result = payload,
                summary = "${records.size} saved"
            ),
            resultForModel = payload,
            recordsCreated = records.size
        )
    }

    // ------------------------------------------------------------------
    // Read tools (explicit user authorization required)
    // ------------------------------------------------------------------

    private suspend fun queryExpenses(call: PendingToolCall, args: JsonObject): ToolExecution {
        requireQueryPermission(call)?.let { return it }
        val (start, end) = parseRange(args)
        val category = args.getAsJsonPrimitive("type")?.asString?.takeIf { it.isNotBlank() }
        val limit = (args.getAsJsonPrimitive("limit")?.asInt ?: 20).coerceIn(1, 50)

        val rows = expenseDao.getExpensesByDateRange(start, end).first()
            .filter { it.deletedAt == null }
            .filter { category == null || it.type == category }
            .take(limit)

        val payload = JsonObject().apply {
            addProperty("status", "ok")
            addProperty("start_date", start)
            addProperty("end_date", end)
            addProperty("returned", rows.size)
            addProperty("total_amount", rows.sumOf { it.amount })
            add("records", JsonArray().apply {
                rows.forEach { row ->
                    add(JsonObject().apply {
                        addProperty("date", row.date)
                        addProperty("category", row.type)
                        addProperty("amount", row.amount)
                        addProperty("remark", row.remark ?: "")
                    })
                }
            })
        }.toString()

        return ToolExecution(
            record = call.toRecord(ToolCallStatus.SUCCESS, payload, "${rows.size} records"),
            resultForModel = payload
        )
    }

    /** Returns null when authorized; otherwise a DENIED execution. */
    private fun requireQueryPermission(call: PendingToolCall): ToolExecution? {
        if (configManager.isQueryAuthorized()) return null
        val payload = JsonObject().apply {
            addProperty("status", "denied")
            addProperty("reason", "The user has not authorized AI access to expense records")
        }.toString()
        return ToolExecution(
            record = call.toRecord(ToolCallStatus.DENIED, payload, "permission denied"),
            resultForModel = payload
        )
    }

    private suspend fun spendingSummary(call: PendingToolCall, args: JsonObject): ToolExecution {
        requireQueryPermission(call)?.let { return it }
        val (start, end) = parseRange(args)
        val rows = expenseDao.getExpensesByDateRange(start, end).first()
            .filter { it.deletedAt == null }

        val byCategory = rows.groupBy { it.type }
            .map { (category, items) ->
                JsonObject().apply {
                    addProperty("category", category)
                    addProperty("total", items.sumOf { it.amount })
                    addProperty("count", items.size)
                }
            }
            .sortedByDescending { it.getAsJsonPrimitive("total").asDouble }

        val payload = JsonObject().apply {
            addProperty("status", "ok")
            addProperty("start_date", start)
            addProperty("end_date", end)
            addProperty("record_count", rows.size)
            addProperty("total", rows.sumOf { it.amount })
            add("by_category", JsonArray().apply { byCategory.forEach { add(it) } })
        }.toString()

        return ToolExecution(
            record = call.toRecord(ToolCallStatus.SUCCESS, payload, "${rows.size} records"),
            resultForModel = payload
        )
    }

    private fun currentDate(call: PendingToolCall): ToolExecution {
        val now = LocalDate.now()
        val payload = JsonObject().apply {
            addProperty("date", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            addProperty("weekday", now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
        }.toString()
        return ToolExecution(
            record = call.toRecord(ToolCallStatus.SUCCESS, payload, payload),
            resultForModel = payload
        )
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun parseRange(args: JsonObject): Pair<String, String> {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        fun parse(value: String?, fallback: LocalDate): String = runCatching {
            LocalDate.parse(value.orEmpty())
        }.getOrNull()?.format(formatter) ?: fallback.format(formatter)

        var start = parse(args.getAsJsonPrimitive("start_date")?.asString, today.withDayOfMonth(1))
        var end = parse(args.getAsJsonPrimitive("end_date")?.asString, today)
        // Cap the range so a hallucinated query cannot dump the entire ledger.
        if (runCatching {
                java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.parse(start), LocalDate.parse(end)
                ) > 366
            }.getOrDefault(false)) {
            start = LocalDate.parse(end).minusDays(365).format(formatter)
        }
        if (start > end) start = end
        return start to end
    }

    private fun failure(call: PendingToolCall, message: String): ToolExecution {
        val payload = JsonObject().apply {
            addProperty("status", "error")
            addProperty("message", message)
        }.toString()
        return ToolExecution(
            record = call.toRecord(ToolCallStatus.FAILED, payload, message.take(80)),
            resultForModel = payload
        )
    }

    private fun PendingToolCall.toRecord(
        status: ToolCallStatus,
        result: String,
        summary: String
    ): ToolCallRecord = ToolCallRecord(
        id = id,
        name = name,
        argumentsJson = argumentsJson,
        resultJson = result,
        status = status,
        summary = summary
    )

    private companion object {
        const val TAG = "AgentToolExecutor"
    }
}
