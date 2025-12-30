const express = require('express')
const router = express.Router()
const { getExpenses, getExpensesByDate, addExpense, deleteExpense, updateExpense, getStatistics } = require('../controllers/expenseController')

router.get('/', getExpenses)
router.get('/by-date', getExpensesByDate) // 添加按日期分组的API路由
router.post('/', addExpense)
router.put('/:id', updateExpense) // 添加更新消费记录API路由
router.delete('/:id', deleteExpense)
router.get('/statistics', getStatistics) // 添加统计API路由

module.exports = router
