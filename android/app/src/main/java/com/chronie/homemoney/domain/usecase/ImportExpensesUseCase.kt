package com.chronie.homemoney.domain.usecase

import android.content.Context
import android.net.Uri
import com.chronie.homemoney.R
import com.chronie.homemoney.domain.model.Expense
import com.chronie.homemoney.domain.model.ExpenseType
import com.chronie.homemoney.domain.repository.ExpenseRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory

import java.util.*
import javax.inject.Inject

/**
 * 从 Excel 文件导入支出记录
 */
class ImportExpensesUseCase @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    @param:ApplicationContext private val context: Context
) {
    
    data class ImportResult(
        val successCount: Int,
        val failedCount: Int,
        val errors: List<String>
    )
    
    suspend operator fun invoke(uri: Uri): Result<ImportResult> {
        return try {
            val expenses = parseExcelFile(uri)
            
            if (expenses.isEmpty()) {
                return Result.failure(Exception("No valid records found in file"))
            }
            
            // 导入数据
            var successCount = 0
            var failedCount = 0
            val errors = mutableListOf<String>()
            
            expenses.forEachIndexed { index, expense ->
                val result = expenseRepository.addExpense(expense)
                if (result.isSuccess) {
                    successCount++
                } else {
                    failedCount++
                    errors.add("Row ${index + 2}: ${result.exceptionOrNull()?.message}")
                }
            }
            
            Result.success(ImportResult(successCount, failedCount, errors))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun parseExcelFile(uri: Uri): List<Expense> {
        val expenses = mutableListOf<Expense>()
        
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheetAt(0)
            
            // 获取标题行以确定列的位置
            val headerRow = sheet.getRow(0) ?: throw Exception("Empty file")
            val headers = mutableMapOf<String, Int>()
            
            for (i in 0 until headerRow.lastCellNum) {
                val cell = headerRow.getCell(i)
                if (cell != null) {
                    headers[cell.stringCellValue.trim()] = i
                }
            }
            
            // 获取列索引（支持多语言）
            val dateColIndex = findColumnIndex(headers, "date")
            val typeColIndex = findColumnIndex(headers, "type")
            val amountColIndex = findColumnIndex(headers, "amount")
            val remarkColIndex = findColumnIndex(headers, "remark")
            
            if (dateColIndex == -1 || typeColIndex == -1 || amountColIndex == -1) {
                throw Exception(context.getString(R.string.import_validation_error, "Missing required columns"))
            }
            
            // 解析数据行
            
            for (i in 1..sheet.lastRowNum) {
                val row = sheet.getRow(i) ?: continue
                
                try {
                    // 日期
                    val dateCell = row.getCell(dateColIndex)
                    // 提取日期字符串，确保格式为YYYY-MM-DD
                        val dateStr = when (dateCell?.cellType) {
                            CellType.STRING -> {
                                val cellValue = dateCell.stringCellValue
                                // 如果包含时间部分，只取日期部分
                                if (cellValue.contains('T') || cellValue.contains(' ')) {
                                    val datePart = cellValue.substringBefore('T').substringBefore(' ')
                                    // 验证是否是有效的YYYY-MM-DD格式
                                    try {
                                        java.time.LocalDate.parse(datePart)
                                        datePart
                                    } catch (e: Exception) {
                                        continue
                                    }
                                } else {
                                    // 尝试直接解析为日期
                                    try {
                                        java.time.LocalDate.parse(cellValue)
                                        cellValue
                                    } catch (e: Exception) {
                                        continue
                                    }
                                }
                            }
                            CellType.NUMERIC -> {
                                val date = dateCell.dateCellValue
                                val calendar = Calendar.getInstance()
                                calendar.time = date
                                String.format(
                                    "%04d-%02d-%02d",
                                    calendar.get(Calendar.YEAR),
                                    calendar.get(Calendar.MONTH) + 1,
                                    calendar.get(Calendar.DAY_OF_MONTH)
                                )
                            }
                            else -> continue
                        }
                    
                    // 类型
                    val typeCell = row.getCell(typeColIndex)
                    val typeStr = typeCell?.stringCellValue ?: continue
                    val expenseType = parseExpenseType(typeStr)
                    
                    // 金额
                    val amountCell = row.getCell(amountColIndex)
                    val amount = when (amountCell?.cellType) {
                        CellType.NUMERIC -> amountCell.numericCellValue
                        CellType.STRING -> amountCell.stringCellValue.toDoubleOrNull() ?: continue
                        else -> continue
                    }
                    
                    if (amount <= 0) continue
                    
                    // 备注
                    val remarkCell = row.getCell(remarkColIndex)
                    val remark = remarkCell?.stringCellValue
                    
                    val expense = Expense(
                        id = UUID.randomUUID().toString(),
                        type = expenseType,
                        remark = remark,
                        amount = amount,
                        date = dateStr,
                        isSynced = false
                    )
                    
                    expenses.add(expense)
                } catch (e: Exception) {
                    // 跳过无效行
                    android.util.Log.w("ImportExpenses", "Failed to parse row $i: ${e.message}")
                }
            }
            
            workbook.close()
        }
        
        return expenses
    }
    
    private fun findColumnIndex(headers: Map<String, Int>, columnKey: String): Int {
        // 尝试匹配多语言标题
        val possibleHeaders = when (columnKey) {
            "date" -> listOf(
                context.getString(R.string.excel_header_date),
                "Date", "日期", "日期"
            )
            "type" -> listOf(
                context.getString(R.string.excel_header_type),
                "Type", "类型", "類型"
            )
            "amount" -> listOf(
                context.getString(R.string.excel_header_amount),
                "Amount", "金额", "金額"
            )
            "remark" -> listOf(
                context.getString(R.string.excel_header_remark),
                "Remark", "备注", "備註"
            )
            else -> emptyList()
        }
        
        for (header in possibleHeaders) {
            val index = headers[header]
            if (index != null) return index
        }
        
        return -1
    }
    
    private fun parseExpenseType(typeStr: String): ExpenseType {
        // 尝试匹配所有语言的类型名称
        return ExpenseType.values().find { type ->
            val resourceId = context.resources.getIdentifier(
                type.displayNameKey,
                "string",
                context.packageName
            )
            if (resourceId != 0) {
                context.getString(resourceId) == typeStr
            } else {
                false
            }
        } ?: ExpenseType.fromString(typeStr)
    }
}
