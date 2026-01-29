package com.chronie.homemoney.domain.usecase

import android.content.Context
import android.os.Environment
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseFilters
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.domain.repository.ExpenseRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.FillPatternType
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.xssf.usermodel.XSSFWorkbook
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
            
            // 创建 Excel 文件
            val workbook = XSSFWorkbook()
            val sheet = workbook.createSheet(context.getString(R.string.expense_records))
            
            // 创建标题行样式
            val headerStyle = workbook.createCellStyle().apply {
                fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
                fillPattern = FillPatternType.SOLID_FOREGROUND
                val font = workbook.createFont()
                font.bold = true
                setFont(font)
            }
            
            // 创建标题行
            val headerRow = sheet.createRow(0)
            val headers = listOf(
                context.getString(R.string.excel_header_date),
                context.getString(R.string.excel_header_type),
                context.getString(R.string.excel_header_amount),
                context.getString(R.string.excel_header_remark)
            )
            
            headers.forEachIndexed { index, header ->
                val cell = headerRow.createCell(index)
                cell.setCellValue(header)
                cell.cellStyle = headerStyle
            }
            
            // 填充数据
            expenses.forEachIndexed { index, expense ->
                val row = sheet.createRow(index + 1)
                
                // 日期
                row.createCell(0).setCellValue(expense.date)
                
                // 类型（使用当前语言）
                row.createCell(1).setCellValue(getExpenseTypeName(expense.type))
                
                // 金额
                row.createCell(2).setCellValue(expense.amount)
                
                // 备注
                row.createCell(3).setCellValue(expense.remark ?: "")
            }
            
            // 手动设置列宽（autoSizeColumn 在 Android 上不可用，因为依赖 AWT）
            sheet.setColumnWidth(0, 5000)  // 日期列
            sheet.setColumnWidth(1, 4000)  // 类型列
            sheet.setColumnWidth(2, 3000)  // 金额列
            sheet.setColumnWidth(3, 6000)  // 备注列
            
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
                workbook.write(outputStream)
            }
            
            workbook.close()
            
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
