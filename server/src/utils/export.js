const XLSX = require('xlsx')
const fs = require('fs')
const path = require('path')
const { Expense } = require('../db')

const HEADERS = {
  'zh-CN': {
    date: '日期',
    type: '分类',
    amount: '金额',
    remark: '备注'
  },
  'en-US': {
    date: 'Date',
    type: 'Category',
    amount: 'Amount',
    remark: 'Notes'
  },
  'zh-TW': {
    date: '日期',
    type: '分類',
    amount: '金額',
    remark: '備註'
  }
}

const formatTimestamp = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hours = String(now.getHours()).padStart(2, '0')
  const minutes = String(now.getMinutes()).padStart(2, '0')
  const seconds = String(now.getSeconds()).padStart(2, '0')
  return `${year}${month}${day}${hours}${minutes}${seconds}`
}

class ExportService {
  constructor () {}

  async getFullData () {
    return await Expense.findAll({ 
      raw: true,
      where: {
        deletedAt: null
      },
      order: [['date', 'DESC']]
    })
  }

  async generateExcel (lang = 'zh-CN') {
    const data = await this.getFullData()
    const headers = HEADERS[lang] || HEADERS['zh-CN']
    
    const processedData = data.map(expense => ({
      [headers.date]: expense.date || new Date().toISOString().split('T')[0],
      [headers.type]: expense.type || '',
      [headers.amount]: expense.amount || 0,
      [headers.remark]: expense.remark || ''
    }))
    
    const worksheet = XLSX.utils.json_to_sheet(processedData)
    
    worksheet['!cols'] = [
      { wch: 12 },
      { wch: 15 },
      { wch: 10 },
      { wch: 30 }
    ]
    
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, worksheet, 'Expenses')

    const exportsDir = path.join(__dirname, '../../exports/')
    if (!fs.existsSync(exportsDir)) {
      fs.mkdirSync(exportsDir, { recursive: true })
    }
    
    const timestamp = formatTimestamp()
    const filePath = path.join(exportsDir, `expenses_${timestamp}.xlsx`)
    XLSX.writeFile(workbook, filePath)
    return { filePath, timestamp }
  }

  cleanupFile (filePath) {
    try {
      if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath)
      }
    } catch (error) {
      console.error('Failed to cleanup file:', error)
    }
  }
}

module.exports = ExportService
