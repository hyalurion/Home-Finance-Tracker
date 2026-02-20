const path = require('path')
const http = require('http')
const express = require('express')
const fs = require('fs')
console.log('Top-level NODE_ENV:', process.env.NODE_ENV);
const { syncDatabase, sequelize } = require('./db')
const cors = require('cors')
require('dotenv').config()
const memberRoutes = require('./routes/memberRoutes')
// 导入日志模型用于初始化日志表
const { initLogTable } = require('./models/log')

const app = express()
const PORT = process.env.PORT || 3010

// Middlewares
const corsOptions = {
  origin: ['http://localhost:5173', 'http://192.168.0.197:5173'],
  methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization'],
  credentials: true,
  optionsSuccessStatus: 200
}

app.use(cors(corsOptions))
// 增加JSON解析的大小限制，支持头像上传（10MB）
app.use(express.json({ limit: '10mb' }))
app.use(express.urlencoded({ extended: true, limit: '10mb' }))

// API Routes
app.use('/api/expenses', require('./routes/expense'))
app.use('/api/json-files', require('./routes/jsonFiles'))
app.use('/api/members', require('./routes/memberRoutes'))
app.use('/api', require('./routes/errorReportRoutes'))
app.use('/api', require('./routes/api'))

// 根路径路由
app.use('/', require('./routes/baseRoutes'))
app.use('/', require('./routes/exportRoutes'))

// Serve frontend in production
console.log('NODE_ENV:', process.env.NODE_ENV);
if (process.env.NODE_ENV === 'production') {
  const distPath = path.join(__dirname, '../../client/dist');
  console.log('Serving static files from:', distPath);
    // 写入调试信息到文件
    const debugInfo = `NODE_ENV: ${process.env.NODE_ENV}\nDist Path: ${distPath}\nPath exists: ${fs.existsSync(distPath)}\n`;
    fs.writeFileSync('debug.log', debugInfo);
    app.use(express.static(distPath))

  // Handle SPA routing - 必须放在最后
  app.get(/.*/, (req, res) => {
    res.sendFile(path.join(distPath, 'index.html'))
  })
}

// Global error handler
app.use((err, req, res, next) => {
  console.error('An error occurred:', err.stack)
  res.status(err.status || 500).json({
    success: false,
    error: 'Internal Server Error'
  })
})

const startServer = async () => {
  try {
    await syncDatabase()
    // 初始化日志表
    await initLogTable(sequelize)

    const server = http.createServer(app)
    server.listen(PORT, '0.0.0.0', () => {
      console.log(`✅ Server is running on port ${PORT}`)
    })

    // 进程终止信号处理
    const handleShutdown = () => {
      console.log('Received shutdown signal, closing server...')

      // 关闭服务器
      server.close(() => {
        console.log('Server closed gracefully')
        process.exit(0)
      })
    }

    // 监听终止信号
    process.on('SIGTERM', handleShutdown)
    process.on('SIGINT', handleShutdown)
  } catch (error) {
    console.error('❌ Failed to start server:', error)
    process.exit(1)
  }
}

startServer()

