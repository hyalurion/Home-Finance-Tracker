const XLSX = require('xlsx')
const { Expense } = require('../db')
const dayjs = require('dayjs')
const { Op } = require('sequelize')

class ImportService {
  async importFromExcel (filePath) {
    const workbook = XLSX.readFile(filePath)
    const sheetName = workbook.SheetNames[0]
    const worksheet = workbook.Sheets[sheetName]
    const data = XLSX.utils.sheet_to_json(worksheet)

    // 数据验证和转换 - 支持多种语言的表头
    const candidateRecords = data.map(item => ({
      type: item['分类'] || item.Category || item['分類'],
      remark: item['备注'] || item.Description || item['備註'] || item.Notes || '',
      amount: parseFloat((item['金额'] || item['Amount'] || item['金額'] || '').toString().replace(/[^0-9.]/g, '')) || 0,
      date: dayjs(item['日期'] || item.Date).format('YYYY-MM-DD')
    })).filter(r => r.type && r.amount > 0 && r.date)

    if (candidateRecords.length === 0) {
      return { success: true, message: '没有有效数据被导入。' }
    }

    // 获取所有候选记录的唯一键，用于去重
    const getRecordKey = (record) => {
      const normalizedRemark = (record.remark || '').trim()
      return `${record.date}_${record.type}_${record.amount}_${normalizedRemark}`
    }

    // 步骤1: 去重Excel文件内部的重复记录
    const uniqueRecordsMap = new Map()
    candidateRecords.forEach(record => {
      const key = getRecordKey(record)
      if (!uniqueRecordsMap.has(key)) {
        uniqueRecordsMap.set(key, record)
      }
    })
    const uniqueInFile = Array.from(uniqueRecordsMap.values())
    const fileInternalDuplicates = candidateRecords.length - uniqueInFile.length

    // 步骤2: 查询数据库中的现有记录，检查是否重复
    const existingKeys = new Set()
    if (uniqueInFile.length > 0) {
      const uniqueDates = [...new Set(uniqueInFile.map(r => r.date))]
      const existingRecords = await Expense.findAll({
        where: {
          date: { [Op.in]: uniqueDates },
          deletedAt: null
        },
        raw: true
      })
      existingRecords.forEach(record => {
        existingKeys.add(getRecordKey(record))
      })
    }

    // 步骤3: 过滤出真正要导入的新记录
    const recordsToImport = uniqueInFile.filter(record => {
      return !existingKeys.has(getRecordKey(record))
    })
    const duplicatesWithDatabase = uniqueInFile.length - recordsToImport.length

    if (recordsToImport.length === 0) {
      return { 
        success: true, 
        message: '所有记录都已存在，没有新数据导入。',
        stats: {
          total: candidateRecords.length,
          skippedInternal: fileInternalDuplicates,
          skippedExisting: duplicatesWithDatabase,
          imported: 0
        }
      }
    }

    try {
      await Expense.bulkCreate(recordsToImport)
      
      let message = `成功导入 ${recordsToImport.length} 条记录。`
      if (fileInternalDuplicates > 0 || duplicatesWithDatabase > 0) {
        const skipDetails = []
        if (fileInternalDuplicates > 0) skipDetails.push(`${fileInternalDuplicates} 条文件内重复`)
        if (duplicatesWithDatabase > 0) skipDetails.push(`${duplicatesWithDatabase} 条已存在`)
        message += ` (跳过: ${skipDetails.join(', ')})`
      }
      
      return { 
        success: true, 
        message,
        stats: {
          total: candidateRecords.length,
          skippedInternal: fileInternalDuplicates,
          skippedExisting: duplicatesWithDatabase,
          imported: recordsToImport.length
        }
      }
    } catch (error) {
      console.error('导入Excel数据失败:', error)
      throw new Error('数据库插入失败。')
    }
  }
}

module.exports = ImportService
