const express = require('express')
const router = express.Router()
const errorReportController = require('../controllers/errorReportController')

/**
 * 错误报告相关路由
 * 主要提供给移动端应用使用
 */

// 提交错误报告（无需认证）
router.post('/error/report', errorReportController.reportError)

/**
 * 以下路由需要管理员认证
 * 这里暂时省略认证中间件，可以在实际使用时添加
 */

// 获取错误报告列表
router.get('/errors', errorReportController.getErrorReports)

// 获取错误统计信息
router.get('/errors/stats', errorReportController.getErrorStats)

// 标记错误报告为已处理
router.put('/errors/:reportId/process', errorReportController.processErrorReport)

module.exports = router