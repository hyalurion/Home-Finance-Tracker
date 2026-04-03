package com.chronie.homemoney.domain.usecase

import android.content.Context
import android.os.Environment
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseFilters
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.domain.repository.ExpenseRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 导出支出记录为 Excel 文件
 */
class ExportExpensesUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    @param:ApplicationContext private val context: Context
) {
    
    suspend operator fun invoke(
        startDate: LocalDate? = null,
        endDate: LocalDate? = null
    ): Result<String> {
        return try {
            // 获取要导出的数据
            val filters = ExpenseFilters(
                startDate = startDate,
                endDate = endDate
            )
            
            val expenses = mutableListOf<Expense>()
            var page = 1
            val limit = 100
            
            // 分页获取所有数据
            while (true) {
                val result = expenseRepository.getExpensesList(page, limit, filters)
                if (result.isFailure) {
                    return Result.failure(result.exceptionOrNull() ?: Exception("Failed to fetch expenses"))
                }
                
                val pageExpenses = result.getOrNull() ?: emptyList()
                if (pageExpenses.isEmpty()) break
                
                expenses.addAll(pageExpenses)
                page++
            }
            
            if (expenses.isEmpty()) {
                return Result.failure(Exception(context.getString(R.string.no_records_to_export)))
            }
            
            // 保存文件到 Downloads 目录
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            val timestamp = System.currentTimeMillis()
            val dateRange = if (startDate != null && endDate != null) {
                "_${startDate}_${endDate}"
            } else {
                ""
            }
            val filename = "${context.getString(R.string.export_filename_prefix)}${dateRange}_$timestamp.xlsx"
            val file = File(downloadsDir, filename)
            
            FileOutputStream(file).use { outputStream ->
                Workbook(outputStream, "HomeMoney", "1.0").use { workbook ->
                    val sheet = workbook.newWorksheet(context.getString(R.string.expense_records))
                    
                    val headers = listOf(
                        context.getString(R.string.excel_header_date),
                        context.getString(R.string.excel_header_type),
                        context.getString(R.string.excel_header_amount),
                        context.getString(R.string.excel_header_remark)
                    )
                    
                    headers.forEachIndexed { index, header ->
                        sheet.value(0, index, header)
                        sheet.style(0, index).bold().set()
                    }
                    
                    expenses.forEachIndexed { index, expense ->
                        val row = index + 1
                        
                        sheet.value(row, 0, expense.date)
                        sheet.value(row, 1, getExpenseTypeName(expense.type))
                        sheet.value(row, 2, expense.amount)
                        sheet.value(row, 3, expense.remark ?: "")
                    }
                    
                    sheet.width(0, 5000.0)
                    sheet.width(1, 4000.0)
                    sheet.width(2, 3000.0)
                    sheet.width(3, 6000.0)
                }
            }
            
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun getExpenseTypeName(type: ExpenseType): String {
        val resourceId = context.resources.getIdentifier(
            type.displayNameKey,
            "string",
            context.packageName
        )
        return if (resourceId != 0) {
            context.getString(resourceId)
        } else {
            type.name
        }
    }
}
