package com.chronie.homemoney.data.mapper

import com.chronie.homemoney.data.local.entity.ExpenseEntity
import com.chronie.homemoney.data.remote.dto.ExpenseDto
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseType
import java.util.UUID

/**
 * Bidirectional mapper for converting between the three Expense representations:
 * - [ExpenseEntity] — Room database entity (type stored as Chinese string)
 * - [ExpenseDto] — Network/API data transfer object
 * - [Expense] — Domain model (type stored as [ExpenseType] enum)
 *
 * The key mapping concern is the [ExpenseType] ↔ Chinese name conversion,
 * since the database and API use Chinese display names while the domain layer
 * uses the typed enum for type safety.
 */
object ExpenseMapper {
    
    /**
     * Converts a database entity to a domain model.
     * Resolves the stored Chinese type name back to an [ExpenseType] enum.
     */
    fun toDomain(entity: ExpenseEntity): Expense {
        return Expense(
            id = entity.id,
            type = ExpenseType.fromString(entity.type),
            remark = entity.remark,
            amount = entity.amount,
            date = entity.date,
            version = entity.version,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt,
            isSynced = entity.isSynced
        )
    }
    
    /**
     * Converts a domain model to a database entity for local persistence.
     * Maps the [ExpenseType] enum to its Chinese display name.
     */
    fun toEntity(expense: Expense): ExpenseEntity {
        return ExpenseEntity(
            id = expense.id,
            type = getChineseTypeName(expense.type),
            remark = expense.remark,
            amount = expense.amount,
            date = expense.date,
            version = expense.version,
            updatedAt = expense.updatedAt,
            deletedAt = expense.deletedAt,
            isSynced = expense.isSynced
        )
    }
    
    /**
     * Converts a server DTO to a domain model.
     *
     * Handles date format normalization — server dates may include time
     * components (e.g., "2024-01-15T12:00:00Z") which are stripped to
     * "YYYY-MM-DD" format. Falls back to the current date if parsing fails.
     * Missing IDs are assigned a random UUID.
     */
    fun toDomain(dto: ExpenseDto): Expense {
        val dateStr = try {
            if (dto.date.contains('T') || dto.date.contains(' ')) {
                val datePart = dto.date.substringBefore('T').substringBefore(' ')
                java.time.LocalDate.parse(datePart)
                datePart
            } else {
                java.time.LocalDate.parse(dto.date)
                dto.date
            }
        } catch (_: Exception) {
            // Fallback to today's date if the server date is unparseable
            java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        }
        
        return Expense(
            id = dto.id ?: UUID.randomUUID().toString(),
            type = ExpenseType.fromString(dto.type),
            remark = dto.remark,
            amount = dto.amount,
            date = dateStr,
            version = dto.version,
            updatedAt = dto.updatedAt,
            deletedAt = dto.deletedAt,
            // Mark as synced since it came from the server
            isSynced = true
        )
    }
    
    /**
     * Converts a domain model to a server DTO for API requests.
     * Maps the [ExpenseType] enum to its Chinese display name.
     */
    fun toDto(expense: Expense): ExpenseDto {
        return ExpenseDto(
            id = expense.id,
            type = getChineseTypeName(expense.type),
            remark = expense.remark,
            amount = expense.amount,
            date = expense.date,
            version = expense.version,
            updatedAt = expense.updatedAt,
            deletedAt = expense.deletedAt
        )
    }
    
    /**
     * Maps an [ExpenseType] enum value to its canonical Chinese storage name.
     * The enum owns the name now, so adding a category can no longer leave
     * this mapper behind (which previously turned new categories into OTHER).
     */
    private fun getChineseTypeName(type: ExpenseType): String = type.chineseName
}
