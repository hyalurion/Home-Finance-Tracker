const { ErrorReport, Member, sequelize } = require('../db')
const { Op } = require('sequelize')
const logger = console

/**
 * 接收并保存错误报告
 */
exports.reportError = async (req, res) => {
  try {
    const { 
      errorType, 
      message, 
      stackTrace, 
      deviceInfo, 
      appVersion,
      appBuild,
      environment,
      memberId,
      additionalInfo
    } = req.body

    if (!errorType || !message) {
      return res.status(400).json({
        success: false,
        message: '错误类型和错误消息是必填项'
      })
    }

    const errorReportData = {
      errorType,
      message,
      stackTrace,
      deviceInfo,
      appVersion,
      appBuild,
      environment,
      isProcessed: false
    }

    if (memberId) {
      errorReportData.memberId = memberId
    }

    if (additionalInfo) {
      errorReportData.deviceInfo = {
        ...deviceInfo,
        additionalInfo
      }
    }

    const errorReport = await ErrorReport.create(errorReportData)

    logger.log(`接收到错误报告: ${errorType} - ${message.substring(0, 50)}...`)

    return res.status(201).json({
      success: true,
      message: '错误报告已成功提交',
      reportId: errorReport.id
    })
  } catch (error) {
    logger.error('处理错误报告失败:', error)
    
    return res.status(500).json({
      success: false,
      message: '服务器处理错误报告时出现问题'
    })
  }
}

/**
 * 获取错误报告列表（用于管理界面）
 */
exports.getErrorReports = async (req, res) => {
  try {
    const { 
      page = 1, 
      limit = 20, 
      errorType, 
      isProcessed, 
      dateFrom, 
      dateTo 
    } = req.query

    const pageNum = parseInt(page, 10)
    const pageSize = parseInt(limit, 10)
    const offset = (pageNum - 1) * pageSize

    const where = {}
    
    if (errorType) {
      where.errorType = errorType
    }
    
    if (isProcessed !== undefined) {
      where.isProcessed = isProcessed === 'true'
    }
    
    if (dateFrom) {
      where.createdAt = { ...where.createdAt, [Op.gte]: new Date(dateFrom) }
    }
    
    if (dateTo) {
      const endDate = new Date(dateTo)
      endDate.setHours(23, 59, 59, 999)
      where.createdAt = { ...where.createdAt, [Op.lte]: endDate }
    }

    const { count, rows } = await ErrorReport.findAndCountAll({
      where,
      limit: pageSize,
      offset,
      order: [['createdAt', 'DESC']],
      include: [
        {
          model: Member,
          attributes: ['id', 'username'],
          required: false
        }
      ]
    })

    const reports = rows.map(report => ({
      id: report.id,
      errorType: report.errorType,
      message: report.message,
      stackTrace: report.stackTrace,
      deviceInfo: report.deviceInfo,
      appVersion: report.appVersion,
      appBuild: report.appBuild,
      environment: report.environment,
      isProcessed: report.isProcessed,
      processedAt: report.processedAt,
      createdAt: report.createdAt,
      member: report.Member ? {
        id: report.Member.id,
        username: report.Member.username
      } : null
    }))

    return res.json({
      success: true,
      data: {
        reports,
        pagination: {
          total: count,
          page: pageNum,
          pages: Math.ceil(count / pageSize),
          limit: pageSize
        }
      }
    })
  } catch (error) {
    logger.error('获取错误报告列表失败:', error)
    return res.status(500).json({
      success: false,
      message: '获取错误报告列表时发生错误'
    })
  }
}

/**
 * 标记错误报告为已处理
 */
exports.processErrorReport = async (req, res) => {
  try {
    const { reportId } = req.params

    if (!reportId) {
      return res.status(400).json({
        success: false,
        message: '报告ID不能为空'
      })
    }

    const errorReport = await ErrorReport.findByPk(reportId)
    
    if (!errorReport) {
      return res.status(404).json({
        success: false,
        message: '未找到指定的错误报告'
      })
    }

    await errorReport.update({
      isProcessed: true,
      processedAt: new Date()
    })

    return res.json({
      success: true,
      message: '错误报告已标记为处理'
    })
  } catch (error) {
    logger.error('处理错误报告状态更新失败:', error)
    return res.status(500).json({
      success: false,
      message: '更新错误报告状态时发生错误'
    })
  }
}

/**
 * 获取错误统计信息
 */
exports.getErrorStats = async (req, res) => {
  try {
    const typeStats = await ErrorReport.findAll({
      attributes: [
        'errorType',
        [sequelize.fn('COUNT', sequelize.col('id')), 'count']
      ],
      group: ['errorType'],
      raw: true
    })

    const processedStats = await ErrorReport.findAll({
      attributes: [
        'isProcessed',
        [sequelize.fn('COUNT', sequelize.col('id')), 'count']
      ],
      group: ['isProcessed'],
      raw: true
    })

    const last24Hours = new Date()
    last24Hours.setHours(last24Hours.getHours() - 24)
    
    const recentCount = await ErrorReport.count({
      where: {
        createdAt: { [Op.gte]: last24Hours }
      }
    })

    return res.json({
      success: true,
      data: {
        byType: typeStats,
        byStatus: processedStats,
        last24Hours: recentCount
      }
    })
  } catch (error) {
    logger.error('获取错误统计信息失败:', error)
    return res.status(500).json({
      success: false,
      message: '获取错误统计信息时发生错误'
    })
  }
}
