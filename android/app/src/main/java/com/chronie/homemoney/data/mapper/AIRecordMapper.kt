package com.chronie.homemoney.data.mapper

import com.chronie.homemoney.data.remote.dto.AIExpenseRecordDto
import com.chronie.homemoney.domain.model.AIExpenseRecord
import com.chronie.homemoney.domain.model.ExpenseType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Mapper for converting AI-recognized expense records from the server DTO
 * to the domain model.
 *
 * AI records come from the OCR/recognition pipeline and may need user
 * validation before being saved as regular expenses.
 */
object AIRecordMapper {
    
    /**
     * Converts an AI expense record DTO to a domain model.
     *
     * The resulting record is marked as not yet edited ([isEdited] = false)
     * and its validity is determined by whether the amount is positive
     * and the remark is non-empty.
     *
     * @param dto The raw AI recognition result from the server.
     * @return An [AIExpenseRecord] ready for user review and editing.
     */
    fun toDomain(dto: AIExpenseRecordDto): AIExpenseRecord {
        return AIExpenseRecord(
            type = parseExpenseType(dto.type),
            amount = dto.amount,
            date = dto.date,
            remark = dto.remark,
            isEdited = false,
            isValid = validateRecord(dto)
        )
    }
    
    /**
     * Delegates to the enum-owned resolver so the OCR/smart-scan pipeline
     * accepts exactly the same category names as the rest of the app.
     * The old local table stopped at 20 entries and silently converted the
     * seven newest categories to OTHER.
     */
    private fun parseExpenseType(typeStr: String): ExpenseType =
        ExpenseType.fromString(typeStr)
    
    /**
     * Parses a date/datetime string into a [LocalDateTime].
     * Tries ISO datetime format first, then ISO date format,
     * falling back to the current time if all parsing fails.
     */
    private fun parseDateTime(dateStr: String): LocalDateTime {
        return try {
            LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
        } catch (_: Exception) {
            try {
                val date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE)
                date.atStartOfDay()
            } catch (_: Exception) {
                LocalDateTime.now()
            }
        }
    }
    
    /**
     * Validates whether an AI-recognized record is usable.
     * A valid record must have a positive amount and a non-blank remark.
     */
    private fun validateRecord(dto: AIExpenseRecordDto): Boolean {
        return dto.amount > 0 && dto.remark.isNotBlank()
    }
}
