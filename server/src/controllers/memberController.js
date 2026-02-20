const { Member, sequelize } = require('../db')

// 创建或获取会员
const getOrCreateMember = async (req, res) => {
  try {
    // 检查数据库连接状态 - 更健壮的检查
    try {
      // 尝试执行一个简单的查询来验证连接是否真正可用
      await sequelize.query('SELECT 1', { raw: true })
    } catch (connectionError) {
      console.log('数据库连接不可用，无法创建或获取会员:', connectionError.message)
      return res.status(503).json({ error: '数据库服务暂时不可用' })
    }
    const { username } = req.body

    if (!username) {
      return res.status(400).json({ error: '用户名不能为空' })
    }

    // 查找或创建会员
    let member = await Member.findOne({ where: { username } })

    if (!member) {
      member = await Member.create({ username })
    }

    res.json({
      success: true,
      data: member
    })
  } catch (error) {
    console.error('创建或获取会员失败:', error)
    res.status(500).json({ error: '服务器错误' })
  }
}

// 获取会员信息
const getMemberInfo = async (req, res) => {
  try {
    // 检查数据库连接状态 - 更健壮的检查
    try {
      // 尝试执行一个简单的查询来验证连接是否真正可用
      await sequelize.query('SELECT 1', { raw: true })
    } catch (connectionError) {
      console.log('数据库连接不可用，无法获取会员信息:', connectionError.message)
      return res.status(503).json({ error: '数据库服务暂时不可用' })
    }
    const { username } = req.params

    const member = await Member.findOne({ where: { username } })

    if (!member) {
      return res.status(404).json({ error: '会员不存在' })
    }

    res.json({
      success: true,
      data: member
    })
  } catch (error) {
    console.error('获取会员信息失败:', error)
    res.status(500).json({ error: '服务器错误' })
  }
}

// 更新会员头像
const updateAvatar = async (req, res) => {
  try {
    // 检查数据库连接状态 - 更健壮的检查
    try {
      // 尝试执行一个简单的查询来验证连接是否真正可用
      await sequelize.query('SELECT 1', { raw: true })
    } catch (connectionError) {
      console.log('数据库连接不可用，无法更新头像:', connectionError.message)
      return res.status(503).json({ error: '数据库服务暂时不可用' })
    }
    const { username } = req.params
    const { avatar } = req.body

    if (!username || !avatar) {
      return res.status(400).json({ error: '用户名和头像数据不能为空' })
    }

    // 查找会员
    const member = await Member.findOne({ where: { username } })

    if (!member) {
      return res.status(404).json({ error: '会员不存在' })
    }

    // 更新头像
    await member.update({ avatar })

    res.json({
      success: true,
      data: member
    })
  } catch (error) {
    console.error('更新头像失败:', error)
    res.status(500).json({ error: '服务器错误' })
  }
}

module.exports = {
  getOrCreateMember,
  getMemberInfo,
  updateAvatar
}
