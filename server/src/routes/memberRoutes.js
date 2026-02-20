const express = require('express')
const router = express.Router()
const memberController = require('../controllers/memberController')

// 会员相关路由
router.post('/members', memberController.getOrCreateMember)
router.get('/members/:username', memberController.getMemberInfo)
router.put('/members/:username/avatar', memberController.updateAvatar)

module.exports = router