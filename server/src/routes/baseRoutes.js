/**
 * 基础路由配置
 * @module routes/baseRoutes
 * @desc 定义基础健康检查等通用API端点 - 适配全新架构
 * @api {get} /api 项目API帮助文档
 */
const express = require('express')
const router = express.Router()
const { sequelize } = require('../db')
const fs = require('fs')
const path = require('path')
const os = require('os')

// 数据库连接状态检查函数
const checkDatabaseConnection = async () => {
  try {
    await sequelize.authenticate()
    return { status: 'connected', error: null }
  } catch (error) {
    return { status: 'disconnected', error: error.message }
  }
}

/**
 * @api {get} /api/health 系统健康检查
 * @apiName HealthCheck
 * @apiGroup Base
 * @apiSuccess {object} status 服务状态信息
 */
router.get('/api/health', async (req, res) => {
  // 设置CORS头
  res.header('Access-Control-Allow-Origin', '*')
  res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS')
  res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization')

  // 获取系统资源信息
  const memoryUsage = process.memoryUsage()
  const cpuInfo = os.cpus()
  
  // 检查关键目录和文件
  const serverDir = path.dirname(__dirname)
  const clientDistPath = path.join(__dirname, '../../../client/dist')
  const serverConfigPath = path.join(__dirname, '../../config/config.js')

    // 获取CPU使用率（基于两次采样差值计算）
    const getCpuUsage = () => {
      const cpus = os.cpus();
      let totalIdle = 0;
      let totalTick = 0;

      cpus.forEach(cpu => {
        Object.keys(cpu.times).forEach(key => {
          totalTick += cpu.times[key];
        });
        totalIdle += cpu.times.idle;
      });

      return { idle: totalIdle, total: totalTick };
    };

    // 第一次采样
    const startMeasure = getCpuUsage();

    // 等待100ms进行第二次采样
    const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
    await sleep(100);

    // 第二次采样
    const endMeasure = getCpuUsage();

    // 计算两次采样的差值
    const idleDiff = endMeasure.idle - startMeasure.idle;
    const totalDiff = endMeasure.total - startMeasure.total;

    // 计算CPU使用率百分比
    const cpuUsagePercent = Math.max(0, Math.min(100, 100 - Math.round((idleDiff / totalDiff) * 100)));
  
  // 健康状态数据
  const healthData = {
    status: 'OK',
    timestamp: new Date().toISOString(),
    version: '2.0.0', // 更新版本号适配新架构
    uptime: process.uptime().toFixed(2) + 's',
    environment: {
      nodeVersion: process.version,
      nodeEnv: process.env.NODE_ENV || 'development',
      platform: os.platform(),
      arch: os.arch(),
      hostname: os.hostname()
    },
    resources: {
      memory: {
        rss: `${(memoryUsage.rss / 1024 / 1024).toFixed(2)} MB`,
        heapTotal: `${(memoryUsage.heapTotal / 1024 / 1024).toFixed(2)} MB`,
        heapUsed: `${(memoryUsage.heapUsed / 1024 / 1024).toFixed(2)} MB`,
        external: `${(memoryUsage.external / 1024 / 1024).toFixed(2)} MB`
      },
      cpu: {
        count: cpuInfo.length,
        model: cpuInfo[0]?.model || 'Unknown',
        usagePercent: cpuUsagePercent + '%',
        // 在Windows上，loadavg通常返回全0数组，所以提供说明
        systemLoad: os.platform() === 'win32' ? {
          message: 'Windows does not support load average as unix',
          rawValue: os.loadavg().map(avg => avg.toFixed(2))
        } : os.loadavg().map(avg => avg.toFixed(2))
      }
    },
    services: {
      database: await checkDatabaseConnection(),
      fileSystem: {
        serverDirExists: fs.existsSync(serverDir),
        clientDistExists: fs.existsSync(clientDistPath),
        configExists: fs.existsSync(serverConfigPath)
      }
    },
    paths: {
      serverDir,
      clientDistPath,
      serverConfigPath
    }
  }

  res.status(200).json(healthData)
})

/**
 * @api {get} /api/health/lite 轻量级健康检查
 * @apiName LiteHealthCheck
 * @apiGroup Base
 * @apiSuccess {object} status 轻量服务状态信息
 */
router.get('/api/health/lite', async (req, res) => {
  res.header('Access-Control-Allow-Origin', '*')
  const dbStatus = await checkDatabaseConnection()
  res.status(200).json({
    status: 'OK',
    timestamp: new Date().toISOString(),
    database: dbStatus.status
  })
})

// 项目API帮助文档
// API帮助信息
router.get('/api', (req, res) => {
  // 设置CORS头
  res.header('Access-Control-Allow-Origin', '*')
  res.header('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS')
  res.header('Access-Control-Allow-Headers', 'Content-Type, Authorization')
  
  // 项目后端所有可用的API和作用/用法
  const apiHelp = {
    apiVersion: '2.0.0',
    timestamp: new Date().toISOString(),
    projectName: 'Home Finance Tracker API',
    description: '家庭财务管理系统后端API文档',
    availableAPIs: {
      base: [
        {
          endpoint: '/api/health',
          method: 'GET',
          description: {
            en: 'System health check - returns detailed system status',
            zh: '系统健康检查 - 返回详细的系统状态信息'
          },
          usage: {
            en: 'Checks system status, database connection, and resource usage',
            zh: '检查系统状态、数据库连接和资源使用情况'
          }
        },
        {
          endpoint: '/api/health/lite',
          method: 'GET',
          description: {
            en: 'Lightweight health check - returns basic system status',
            zh: '轻量级健康检查 - 返回基本的系统状态信息'
          },
          usage: {
            en: 'Quick check of system and database status',
            zh: '快速检查系统和数据库状态'
          }
        },
        {
          endpoint: '/api/healthcheck',
          method: 'GET',
          description: {
            en: 'Enhanced health check endpoint',
            zh: '增强版健康检查端点'
          },
          usage: {
            en: 'Returns service information, version, and memory usage',
            zh: '返回服务信息、版本和内存使用情况'
          }
        }
      ],
      expenses: [
        {
          endpoint: '/api/expenses',
          method: 'GET',
          description: {
            en: 'Get expense records',
            zh: '获取消费记录'
          },
          usage: {
            en: 'Retrieve all expense records with optional filtering',
            zh: '获取所有消费记录，支持筛选'
          }
        },
        {
          endpoint: '/api/expenses',
          method: 'POST',
          description: {
            en: 'Add new expense record',
            zh: '添加新的消费记录'
          },
          usage: {
            en: 'Create a new expense entry in the system',
            zh: '在系统中创建新的消费记录'
          }
        },
        {
          endpoint: '/api/expenses/:id',
          method: 'DELETE',
          description: {
            en: 'Delete expense record',
            zh: '删除消费记录'
          },
          usage: {
            en: 'Remove an expense record by its ID',
            zh: '通过ID删除消费记录'
          }
        },
        {
          endpoint: '/api/expenses/statistics',
          method: 'GET',
          description: {
            en: 'Get expense statistics',
            zh: '获取消费统计信息'
          },
          usage: {
            en: 'Retrieve statistical analysis of expense data',
            zh: '获取消费数据的统计分析'
          }
        }
      ],
    
      jsonFiles: [
        {
          endpoint: '/api/json-files',
          method: 'GET',
          description: {
            en: 'Get all JSON files list',
            zh: '获取所有JSON文件列表'
          },
          usage: {
            en: 'List all available JSON files in the storage directory',
            zh: '列出存储目录中所有可用的JSON文件'
          }
        },
        {
          endpoint: '/api/json-files/:filename',
          method: 'GET',
          description: {
            en: 'Read specific JSON file',
            zh: '读取指定的JSON文件'
          },
          usage: {
            en: 'Retrieve the content of a specific JSON file',
            zh: '获取特定JSON文件的内容'
          }
        },
        {
          endpoint: '/api/json-files/:filename',
          method: 'POST',
          description: {
            en: 'Write data to JSON file',
            zh: '写入数据到JSON文件'
          },
          usage: {
            en: 'Save data to a specific JSON file',
            zh: '将数据保存到特定的JSON文件'
          }
        },
        {
          endpoint: '/api/json-files/:filename',
          method: 'DELETE',
          description: {
            en: 'Delete specific JSON file',
            zh: '删除指定的JSON文件'
          },
          usage: {
            en: 'Remove a specific JSON file from storage',
            zh: '从存储中删除特定的JSON文件'
          }
        }
      ],
      payments: [
        {
          endpoint: '/api/payments/subscribe',
          method: 'POST',
          description: {
            en: 'Subscription payment',
            zh: '订阅支付'
          },
          usage: {
            en: 'Process a subscription payment',
            zh: '处理订阅支付'
          }
        }
      ],
      exportImport: [
        {
          endpoint: '/api/export/excel',
          method: 'GET',
          description: {
            en: 'Export Excel file',
            zh: '导出Excel文件'
          },
          usage: {
            en: 'Generate and download an Excel file with expense records',
            zh: '生成并下载包含消费记录的Excel文件'
          }
        },
        {
          endpoint: '/api/import/excel',
          method: 'POST',
          description: {
            en: 'Import Excel file',
            zh: '导入Excel文件'
          },
          usage: {
            en: 'Upload and import data from an Excel file',
            zh: '上传并导入Excel文件中的数据'
          }
        }
      ],
      members: [
        {
          endpoint: '/api/members',
          method: 'POST',
          description: {
            en: 'Get or create member',
            zh: '获取或创建会员'
          },
          usage: {
            en: 'Retrieve an existing member or create a new one if not exists',
            zh: '检索现有会员或创建新会员（如果不存在）'
          }
        },
        {
          endpoint: '/api/members/:username',
          method: 'GET',
          description: {
            en: 'Get member information',
            zh: '获取会员信息'
          },
          usage: {
            en: 'Retrieve detailed information about a specific member',
            zh: '获取特定会员的详细信息'
          }
        },
        {
          endpoint: '/api/members/:id/status',
          method: 'PUT',
          description: {
            en: 'Update member status',
            zh: '更新会员状态'
          },
          usage: {
            en: 'Modify the status of a member',
            zh: '修改会员状态'
          }
        },
        {
          endpoint: '/api/members/:username/subscriptions',
          method: 'GET',
          description: {
            en: 'Get member subscriptions',
            zh: '获取会员订阅'
          },
          usage: {
            en: 'Retrieve all subscriptions for a specific member',
            zh: '获取特定会员的所有订阅'
          }
        },
        {
          endpoint: '/api/members/:username/current-subscription',
          method: 'GET',
          description: {
            en: 'Get current subscription',
            zh: '获取当前订阅'
          },
          usage: {
            en: 'Retrieve the active subscription for a member',
            zh: '获取会员的活跃订阅'
          }
        },
        {
          endpoint: '/api/members/:username/subscriptions',
          method: 'DELETE',
          description: {
            en: 'Cancel subscription',
            zh: '取消订阅'
          },
          usage: {
            en: 'Cancel a member\'s subscription',
            zh: '取消会员的订阅'
          }
        },
        {
          endpoint: '/api/subscription-plans',
          method: 'GET',
          description: {
            en: 'Get subscription plans',
            zh: '获取订阅计划'
          },
          usage: {
            en: 'Retrieve all available subscription plans',
            zh: '获取所有可用的订阅计划'
          }
        },
        {
          endpoint: '/api/subscriptions',
          method: 'POST',
          description: {
            en: 'Create subscription',
            zh: '创建订阅'
          },
          usage: {
            en: 'Create a new subscription for a member',
            zh: '为会员创建新的订阅'
          }
        }
      ],
      logs: [
        {
          endpoint: '/api/logs',
          method: 'POST',
          description: {
            en: 'Receive operation logs',
            zh: '接收操作日志'
          },
          usage: {
            en: 'Store frontend operation logs in the database',
            zh: '将前端操作日志存储到数据库'
          }
        },
        {
          endpoint: '/api/logs',
          method: 'GET',
          description: {
            en: 'Get logs list (admin function)',
            zh: '获取日志列表（管理员功能）'
          },
          usage: {
            en: 'Retrieve logs with filtering options',
            zh: '获取日志，支持筛选选项'
          }
        },
        {
          endpoint: '/api/logs/stats',
          method: 'GET',
          description: {
            en: 'Get log statistics (admin function)',
            zh: '获取日志统计信息（管理员功能）'
          },
          usage: {
            en: 'Retrieve statistics about logs within a date range',
            zh: '获取特定日期范围内的日志统计信息'
          }
        },
        {
          endpoint: '/api/logs/clean',
          method: 'DELETE',
          description: {
            en: 'Clean expired logs (admin function)',
            zh: '清理过期日志（管理员功能）'
          },
          usage: {
            en: 'Delete logs older than specified days',
            zh: '删除早于指定天数的日志'
          }
        }
      ],

    },
    usageGuide: {
      en: 'This API provides endpoints for managing household finances including expense tracking, subscription handling, and data import/export functionality.',
      zh: '本API提供了家庭财务管理的各种端点，包括消费记录跟踪、订阅处理和数据导入/导出功能。'
    },
    lastUpdated: new Date().toISOString()
  }
  
  res.status(200).json(apiHelp)
})

module.exports = router
