const express = require('express')
const router = express.Router()
const { 
  getExpenses, 
  getExpensesByDate, 
  addExpense, 
  deleteExpense, 
  hardDeleteExpense,
  updateExpense, 
  getStatistics,
  syncExpenses 
} = require('../controllers/expenseController')

router.get('/', getExpenses)
router.get('/by-date', getExpensesByDate)
router.post('/', addExpense)
router.post('/sync', syncExpenses)
router.put('/:id', updateExpense)
router.delete('/:id', deleteExpense)
router.delete('/:id/hard', hardDeleteExpense)
router.get('/statistics', getStatistics)

module.exports = router
