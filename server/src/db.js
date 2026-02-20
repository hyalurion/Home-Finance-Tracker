const Sequelize = require('sequelize')
const path = require('path')
const sequelize = new Sequelize({ dialect: 'sqlite', storage: path.join(__dirname, '../database.sqlite') })

// 导入现有模型
const Expense = require('./models/expense')(sequelize)

// 导入会员相关模型
const Member = require('./models/member')(sequelize)

// 导入错误报告模型
const ErrorReport = require('./models/errorReport')(sequelize)

// 导入日志相关函数
const { initLogTable, saveLog, getLogs, getLogsCount, cleanOldLogs } = require('./models/log')

// 定义模型关系
Member.hasMany(ErrorReport, { foreignKey: 'memberId' })
ErrorReport.belongsTo(Member, { foreignKey: 'memberId' })

const syncDatabase = async () => {
  try {
    await sequelize.sync({ force: false })
    console.log('Database synchronized successfully.')
  } catch (error) {
    console.error('Unable to synchronize the database:', error)
    throw error
  }
}

module.exports = {
  sequelize,
  Expense,
  Member,
  ErrorReport,
  syncDatabase,
  initLogTable,
  saveLog,
  getLogs,
  getLogsCount,
  cleanOldLogs
}
