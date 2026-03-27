const { Expense, sequelize } = require('../db')
const dayjs = require('dayjs')
const { Op } = require('sequelize')
const { v4: uuidv4 } = require('uuid')

const getExpenses = async (req, res) => {
  try {
    const { page = 1, limit = 20, keyword, type, month, minAmount, maxAmount, sort = 'dateDesc' } = req.query
    const pageNum = parseInt(page, 10)
    const pageSize = parseInt(limit, 10)

    const offset = (pageNum - 1) * pageSize

    // 构建查询条件
    const where = {
      // 默认只查询未删除的记录
      deletedAt: null
    }
    if (type) where.type = type
    if (month) {
      // 解析月份并构建日期范围
      try {
        const [year, monthNum] = month.split('-').map(Number)
        const startDateStr = `${year}-${String(monthNum).padStart(2, '0')}-01`
        const endDateStr = `${year}-${String(monthNum).padStart(2, '0')}-${new Date(year, monthNum, 0).getDate()}`
        where.date = {
          [Op.between]: [startDateStr, endDateStr]
        }
      } catch (error) {
        console.warn('无效的月份格式:', month)
      }
    }
    // 金额范围筛选，添加有效性检查
    const validMinAmount = parseFloat(minAmount)
    const validMaxAmount = parseFloat(maxAmount)
    if (!isNaN(validMinAmount)) {
      where.amount = { ...where.amount, [Op.gte]: validMinAmount }
    }
    if (!isNaN(validMaxAmount)) {
      where.amount = { ...where.amount, [Op.lte]: validMaxAmount }
    }
    // 关键词搜索，移除对time字段的like搜索（time是日期类型，不应该用like）
    if (keyword) {
      where[Op.or] = [
        { type: { [Op.like]: `%${keyword}%` } },
        { remark: { [Op.like]: `%${keyword}%` } }
      ]
      // 如果需要日期搜索，可以添加适当的日期查询逻辑
    }

    // 构建排序规则
    let order = []
    switch (sort) {
      case 'dateAsc':
        order = [['date', 'ASC']]
        break
      case 'dateDesc':
        order = [['date', 'DESC']]
        break
      case 'amountAsc':
        order = [['amount', 'ASC']]
        break
      case 'amountDesc':
        order = [['amount', 'DESC']]
        break
      default:
        order = [['date', 'DESC']]
    }

    const { count, rows } = await Expense.findAndCountAll({
      where,
      limit: pageSize,
      offset: offset,
      order
    })

    // 获取唯一类型和可用月份信息（排除当前的筛选条件）
    const meta = {
      uniqueTypes: [],
      availableMonths: []
    }
    
    try {
      // 获取所有唯一类型
      const typesResult = await Expense.findAll({
        attributes: [['type', 'type']],
        group: ['type'],
        where: {}, // 不使用筛选条件，获取所有类型
        raw: true
      })
      
      meta.uniqueTypes = typesResult
        .map(item => item.type)
        .filter(type => type && type.trim() !== '')
        .sort()
      
      // 获取所有可用月份（格式：YYYY-MM）
      // 使用SQLite的strftime函数替代MySQL的DATE_FORMAT
      const monthsResult = await Expense.findAll({
        attributes: [
          [
            sequelize.fn('strftime', '%Y-%m', sequelize.col('date')),
            'month'
          ]
        ],
        group: ['month'],
        where: {}, // 不使用筛选条件，获取所有月份
        order: [[sequelize.literal('month'), 'DESC']],
        raw: true
      })
      
      meta.availableMonths = monthsResult.map(item => item.month)
    } catch (metaError) {
      console.warn('获取元数据时出错，但不影响主要功能:', metaError)
    }

    res.json({
      data: rows,
      total: count,
      page: pageNum,
      limit: pageSize,
      meta
    })
  } catch (err) {
    console.error('获取消费记录失败:', err)
    res.status(500).json({ error: '读取数据失败' })
  }
}

const addExpense = async (req, res) => {
  try {
    const { id, type, remark, amount, time, date, version, updatedAt } = req.body

    if (!type || !amount) {
      return res.status(400).json({ error: '消费类型和金额是必填项' })
    }

    let dateStr = dayjs().format('YYYY-MM-DD')
    if (date) {
      dateStr = dayjs(date).format('YYYY-MM-DD')
    } else if (time) {
      dateStr = dayjs(time).format('YYYY-MM-DD')
    }

    const expenseId = id || uuidv4()
    const now = Date.now()

    const newExpense = await Expense.create({
      id: expenseId,
      type,
      remark,
      amount: parseFloat(amount),
      date: dateStr,
      version: version || 1,
      updatedAt: updatedAt || now
    })

    res.status(201).json(newExpense)
  } catch (error) {
    console.error('添加消费记录失败:', error)
    res.status(500).json({ error: '无法添加记录' })
  }
}

const deleteExpense = async (req, res) => {
  try {
    const { id } = req.params
    
    const existingExpense = await Expense.findByPk(id)
    if (!existingExpense) {
      return res.status(404).json({ error: '未找到要删除的记录' })
    }

    await Expense.update(
      { deletedAt: Date.now() },
      { where: { id: id } }
    )

    res.status(204).send()
  } catch (error) {
    console.error('删除消费记录失败:', error)
    res.status(500).json({ error: '无法删除记录' })
  }
}

const updateExpense = async (req, res) => {
  try {
    const { id } = req.params
    const { type, remark, amount, time, date, version, updatedAt } = req.body

    const existingExpense = await Expense.findByPk(id)
    if (!existingExpense) {
      return res.status(404).json({ error: '未找到要更新的记录' })
    }

    if (type === undefined && amount === undefined && remark === undefined && time === undefined) {
      return res.status(400).json({ error: '至少需要提供一个要更新的字段' })
    }

    if (amount !== undefined && (isNaN(amount) || parseFloat(amount) <= 0)) {
      return res.status(400).json({ error: '金额必须是有效的正数' })
    }

    const clientVersion = version || existingExpense.version + 1
    const clientUpdatedAt = updatedAt || Date.now()

    if (clientUpdatedAt <= existingExpense.updatedAt) {
      return res.status(409).json({ 
        error: 'Conflict: server has newer version',
        serverVersion: existingExpense.version,
        serverUpdatedAt: existingExpense.updatedAt,
        currentData: existingExpense
      })
    }

    const updateData = {
      version: clientVersion,
      updatedAt: clientUpdatedAt
    }
    if (type !== undefined) updateData.type = type
    if (remark !== undefined) updateData.remark = remark
    if (amount !== undefined) updateData.amount = parseFloat(amount)
    if (date !== undefined) {
      updateData.date = dayjs(date).format('YYYY-MM-DD')
    } else if (time !== undefined) {
      updateData.date = dayjs(time).format('YYYY-MM-DD')
    }

    await Expense.update(updateData, {
      where: { id: id }
    })

    const updatedExpense = await Expense.findByPk(id)
    res.json(updatedExpense)
  } catch (error) {
    console.error('更新消费记录失败:', error)
    res.status(500).json({ error: '无法更新记录' })
  }
}

// 统计功能API
const getStatistics = async (req, res) => {
  try {
    const { type, month, keyword, minAmount, maxAmount } = req.query

    // 构建查询条件，与getExpenses相同
    const where = {
      deletedAt: null
    }
    if (type) where.type = type
    if (month) {
      try {
        const [year, monthNum] = month.split('-').map(Number)
        const startDateStr = `${year}-${String(monthNum).padStart(2, '0')}-01`
        const endDateStr = `${year}-${String(monthNum).padStart(2, '0')}-${new Date(year, monthNum, 0).getDate()}`
        where.date = {
          [Op.between]: [startDateStr, endDateStr]
        }
      } catch (error) {
        console.warn('无效的月份格式:', month)
      }
    }
    // 金额范围筛选
    const validMinAmount = parseFloat(minAmount)
    const validMaxAmount = parseFloat(maxAmount)
    if (!isNaN(validMinAmount)) {
      where.amount = { ...where.amount, [Op.gte]: validMinAmount }
    }
    if (!isNaN(validMaxAmount)) {
      where.amount = { ...where.amount, [Op.lte]: validMaxAmount }
    }
    // 关键词搜索
    if (keyword) {
      where[Op.or] = [
        { type: { [Op.like]: `%${keyword}%` } },
        { remark: { [Op.like]: `%${keyword}%` } }
      ]
    }

    // 使用aggregate函数进行统计计算
    const statsResult = await Expense.findAll({
      where,
      attributes: [
        [sequelize.fn('COUNT', sequelize.col('id')), 'count'],
        [sequelize.fn('SUM', sequelize.col('amount')), 'totalAmount'],
        [sequelize.fn('AVG', sequelize.col('amount')), 'averageAmount'],
        [sequelize.fn('MIN', sequelize.col('amount')), 'minAmount'],
        [sequelize.fn('MAX', sequelize.col('amount')), 'maxAmount']
      ],
      raw: true
    })

    const stats = statsResult[0]
    
    // 获取类型分布统计
    const typeDistributionResult = await Expense.findAll({
      where,
      attributes: [
        'type',
        [sequelize.fn('COUNT', sequelize.col('id')), 'count'],
        [sequelize.fn('SUM', sequelize.col('amount')), 'amount']
      ],
      group: ['type'],
      raw: true
    })

    // 计算类型分布的百分比
    const typeDistribution = {}
    const totalCount = parseInt(stats.count)
    
    typeDistributionResult.forEach(item => {
      const type = item.type || '未知类型'
      typeDistribution[type] = {
        count: parseInt(item.count),
        amount: parseFloat(item.amount),
        percentage: totalCount > 0 ? Math.round((parseInt(item.count) / totalCount) * 100) : 0
      }
    })

    // 计算中位数
    let medianAmount = 0
    if (totalCount > 0) {
      const sortedAmounts = await Expense.findAll({
        where,
        attributes: ['amount'],
        order: [['amount', 'ASC']],
        raw: true
      })
      
      const amounts = sortedAmounts.map(item => parseFloat(item.amount))
      if (amounts.length > 0) {
        if (amounts.length % 2 === 0) {
          medianAmount = (amounts[amounts.length / 2 - 1] + amounts[amounts.length / 2]) / 2
        } else {
          medianAmount = amounts[Math.floor(amounts.length / 2)]
        }
      }
    }

    res.json({
      count: parseInt(stats.count) || 0,
      totalAmount: parseFloat(stats.totalAmount) || 0,
      averageAmount: parseFloat(stats.averageAmount) || 0,
      medianAmount: medianAmount || 0,
      minAmount: parseFloat(stats.minAmount) || 0,
      maxAmount: parseFloat(stats.maxAmount) || 0,
      typeDistribution
    })
  } catch (error) {
    console.error('获取统计数据失败:', error)
    res.status(500).json({ error: '获取统计数据失败' })
  }
}

// 获取按日期分组的消费记录（用于每日统计）
const getExpensesByDate = async (req, res) => {
  try {
    const { page = 1, limit = 10, keyword, type, month, minAmount, maxAmount, sort = 'dateDesc' } = req.query
    const pageNum = parseInt(page, 10)
    const pageSize = parseInt(limit, 10)

    // 构建查询条件
    const where = {
      deletedAt: null
    }
    if (type) where.type = type
    if (month) {
      try {
        const [year, monthNum] = month.split('-').map(Number)
        const startDateStr = `${year}-${String(monthNum).padStart(2, '0')}-01`
        const endDateStr = `${year}-${String(monthNum).padStart(2, '0')}-${new Date(year, monthNum, 0).getDate()}`
        where.date = {
          [Op.between]: [startDateStr, endDateStr]
        }
      } catch (error) {
        console.warn('无效的月份格式:', month)
      }
    }
    // 金额范围筛选
    const validMinAmount = parseFloat(minAmount)
    const validMaxAmount = parseFloat(maxAmount)
    if (!isNaN(validMinAmount)) {
      where.amount = { ...where.amount, [Op.gte]: validMinAmount }
    }
    if (!isNaN(validMaxAmount)) {
      where.amount = { ...where.amount, [Op.lte]: validMaxAmount }
    }
    // 关键词搜索
    if (keyword) {
      where[Op.or] = [
        { type: { [Op.like]: `%${keyword}%` } },
        { remark: { [Op.like]: `%${keyword}%` } }
      ]
    }

    // 构建排序规则
    // 根据排序类型确定排序方式
    let orderClause = []
    if (sort === 'amountAsc' || sort === 'amountDesc') {
      // 按金额排序
      orderClause = [['amount', sort === 'amountDesc' ? 'DESC' : 'ASC'], ['date', 'DESC']]
    } else {
      // 按日期排序（默认）
      orderClause = [['date', sort === 'dateDesc' ? 'DESC' : 'ASC']]
    }

    // 获取所有符合筛选条件的消费记录
    const allExpenses = await Expense.findAll({
      where,
      order: orderClause,
      raw: true
    })

    // 按日期分组
    const groupedData = {}
    allExpenses.forEach(expense => {
      const date = expense.date
      if (!groupedData[date]) {
        groupedData[date] = []
      }
      groupedData[date].push(expense)
    })

    // 为每个日期组添加统计信息
    const dateGroups = Object.keys(groupedData).map(date => {
      const expenses = groupedData[date]
      const totalAmount = expenses.reduce((sum, exp) => sum + parseFloat(exp.amount), 0)
      return {
        date: date,
        count: expenses.length,
        totalAmount: totalAmount,
        expenses: expenses
      }
    })

    // 根据排序类型对日期组进行排序
    if (sort === 'amountAsc' || sort === 'amountDesc') {
      // 按金额排序时，日期组按组内第一条记录的金额排序
      dateGroups.sort((a, b) => {
        const amountA = a.expenses[0].amount
        const amountB = b.expenses[0].amount
        return sort === 'amountDesc' ? amountB - amountA : amountA - amountB
      })
    } else {
      // 按日期排序
      dateGroups.sort((a, b) => {
        const dateA = new Date(a.date)
        const dateB = new Date(b.date)
        return sort === 'dateDesc' ? dateB - dateA : dateA - dateB
      })
    }

    // 计算总记录数（用于分页）
    const totalRecords = allExpenses.length

    // 按日期组进行分页，确保同一个日期组的记录不会被拆分到不同页
    const pages = []
    let currentPageData = []
    let currentRecordCount = 0

    dateGroups.forEach(group => {
      const groupRecordCount = group.count
      
      // 如果当前页为空，或者添加这个组不会超过太多记录限制，则添加到当前页
      if (currentRecordCount === 0 || currentRecordCount + groupRecordCount <= pageSize * 1.5) {
        currentPageData.push(group)
        currentRecordCount += groupRecordCount
      } else {
        // 否则，开始新的一页
        pages.push(currentPageData)
        currentPageData = [group]
        currentRecordCount = groupRecordCount
      }
    })

    // 添加最后一页
    if (currentPageData.length > 0) {
      pages.push(currentPageData)
    }

    // 获取请求的页码数据
    const requestedPageData = pages[pageNum - 1] || []

    // 获取唯一类型和可用月份信息
    const meta = {
      uniqueTypes: [],
      availableMonths: []
    }
    
    try {
      const typesResult = await Expense.findAll({
        attributes: [['type', 'type']],
        group: ['type'],
        where: {},
        raw: true
      })
      
      meta.uniqueTypes = typesResult
        .map(item => item.type)
        .filter(type => type && type.trim() !== '')
        .sort()
      
      const monthsResult = await Expense.findAll({
        attributes: [
          [
            sequelize.fn('strftime', '%Y-%m', sequelize.col('date')),
            'month'
          ]
        ],
        group: ['month'],
        where: {},
        order: [[sequelize.literal('month'), 'DESC']],
        raw: true
      })
      
      meta.availableMonths = monthsResult.map(item => item.month)
    } catch (metaError) {
      console.warn('获取元数据时出错，但不影响主要功能:', metaError)
    }

    res.json({
      data: requestedPageData,
      total: totalRecords,
      page: pageNum,
      limit: pageSize,
      meta
    })
  } catch (err) {
    console.error('获取按日期分组的消费记录失败:', err)
    res.status(500).json({ error: '读取数据失败' })
  }
}

const syncExpenses = async (req, res) => {
  try {
    const { lastSyncTime, changes, localIds } = req.body
    
    let serverChanges = []
    
    // If client provides localIds, return records that client is missing
    if (localIds && Array.isArray(localIds)) {
      // Get all records from server (including deleted ones for conflict detection)
      const allServerRecords = await Expense.findAll({
        attributes: ['id'],
        raw: true
      })
      const allServerIds = new Set(allServerRecords.map(r => r.id))
      const localIdSet = new Set(localIds)
      
      // Find IDs that exist on server but not on client
      const missingIds = [...allServerIds].filter(id => !localIdSet.has(id))
      
      if (missingIds.length > 0) {
        serverChanges = await Expense.findAll({
          where: {
            id: { [Op.in]: missingIds }
          },
          raw: true
        })
      }
    } else {
      // Fallback to old behavior: return records updated after lastSyncTime
      serverChanges = await Expense.findAll({
        where: {
          updatedAt: {
            [Op.gt]: lastSyncTime || 0
          }
        },
        order: [['updatedAt', 'ASC']],
        raw: true
      })
    }

    const conflicts = []
    if (changes && changes.length > 0) {
      for (const change of changes) {
        try {
          const serverRecord = await Expense.findByPk(change.id)
          
          if (change.deletedAt) {
            if (serverRecord) {
              await Expense.update(
                { deletedAt: change.deletedAt, updatedAt: change.updatedAt },
                { where: { id: change.id } }
              )
            }
            continue
          }

          if (!serverRecord) {
            await Expense.create({
              id: change.id,
              type: change.type,
              remark: change.remark,
              amount: change.amount,
              date: change.date,
              version: change.version || 1,
              updatedAt: change.updatedAt || Date.now()
            })
          } else if (change.updatedAt > serverRecord.updatedAt) {
            await Expense.update(
              {
                type: change.type,
                remark: change.remark,
                amount: change.amount,
                date: change.date,
                version: change.version,
                updatedAt: change.updatedAt
              },
              { where: { id: change.id } }
            )
          } else if (change.updatedAt < serverRecord.updatedAt) {
            conflicts.push({
              id: change.id,
              clientVersion: change.version,
              serverVersion: serverRecord.version,
              clientUpdatedAt: change.updatedAt,
              serverUpdatedAt: serverRecord.updatedAt,
              serverData: serverRecord
            })
          }
        } catch (err) {
          console.error('Error processing change:', change.id, err)
        }
      }
    }

    res.json({
      serverChanges: serverChanges,
      conflicts: conflicts,
      syncTime: Date.now()
    })
  } catch (error) {
    console.error('同步失败:', error)
    res.status(500).json({ error: '同步失败' })
  }
}

const hardDeleteExpense = async (req, res) => {
  try {
    const { id } = req.params
    const deleted = await Expense.destroy({
      where: { id: id }
    })

    if (deleted) {
      res.status(204).send()
    } else {
      res.status(404).json({ error: '未找到要删除的记录' })
    }
  } catch (error) {
    console.error('永久删除消费记录失败:', error)
    res.status(500).json({ error: '无法删除记录' })
  }
}

module.exports = {
  getExpenses,
  getExpensesByDate,
  addExpense,
  deleteExpense,
  hardDeleteExpense,
  updateExpense,
  getStatistics,
  syncExpenses
}