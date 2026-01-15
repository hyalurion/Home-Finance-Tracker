const express = require('express')
const router = express.Router()
const memberController = require('../controllers/memberController')
const subscriptionController = require('../controllers/subscriptionController')
const paymentController = require('../controllers/paymentController')

// 会员相关路由
router.post('/members', memberController.getOrCreateMember)
router.get('/members/:username', memberController.getMemberInfo)
router.put('/members/:id/status', memberController.updateMemberStatus)
router.get('/members/:username/subscriptions', memberController.getMemberSubscriptions)
router.put('/members/:username/avatar', memberController.updateAvatar)

// 订阅计划相关路由
router.get('/subscription-plans', subscriptionController.getSubscriptionPlans)

// 用户订阅相关路由
router.post('/subscriptions', subscriptionController.createSubscription)
router.get('/members/:username/current-subscription', subscriptionController.getCurrentSubscription)
router.delete('/members/:username/subscriptions', subscriptionController.cancelSubscription)

// 订阅支付相关路由
router.post('/payments/subscribe', paymentController.subscribePayment)

module.exports = router