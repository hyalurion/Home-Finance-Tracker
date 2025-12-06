const express = require('express')
const router = express.Router()
const paymentController = require('../controllers/paymentController')

/**
 * 支付相关路由
 * @module routes/payment
 * @desc 处理用户捐赠等支付请求
 */

// 会员订阅支付路由
router.post('/subscribe', paymentController.subscribePayment)

module.exports = router