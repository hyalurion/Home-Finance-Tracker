const { Sequelize } = require('sequelize')
const path = require('path')
const fs = require('fs')

const configPath = path.join(__dirname, '../../config/config.json')
let dbPath = path.join(__dirname, '../../database.sqlite')

if (fs.existsSync(configPath)) {
  const config = JSON.parse(fs.readFileSync(configPath, 'utf8'))
  if (config.development && config.development.storage) {
    dbPath = config.development.storage
    if (!path.isAbsolute(dbPath)) {
      dbPath = path.join(__dirname, '../..', dbPath)
    }
  }
}

console.log('数据库路径:', dbPath)

const sequelize = new Sequelize({
  dialect: 'sqlite',
  storage: dbPath,
  logging: console.log
})

async function migrate() {
  try {
    console.log('开始迁移数据库...')

    // 检查 expenses 表是否存在
    const [tables] = await sequelize.query(
      "SELECT name FROM sqlite_master WHERE type='table' AND name='expenses'"
    )

    if (tables.length === 0) {
      console.log('expenses 表不存在，无需迁移')
      return
    }

    // 获取现有表结构
    const [columns] = await sequelize.query('PRAGMA table_info(expenses)')
    const columnNames = columns.map(c => c.name)
    const idColumn = columns.find(c => c.name === 'id')

    console.log('现有字段:', columnNames)
    console.log('id 字段类型:', idColumn?.type)

    // 检查是否需要重建表（id 是 INTEGER 类型需要转换）
    const needsRebuild = idColumn && idColumn.type === 'INTEGER'
    const needsNewColumns = !columnNames.includes('version') || 
                           !columnNames.includes('updatedAt') || 
                           !columnNames.includes('deletedAt')

    if (needsRebuild) {
      console.log('\n需要重建表以支持 UUID...')
      
      // 开启事务
      const transaction = await sequelize.transaction()
      
      try {
        // 1. 创建新表
        console.log('创建新表 expenses_new...')
        await sequelize.query(`
          CREATE TABLE expenses_new (
            id TEXT PRIMARY KEY,
            type TEXT NOT NULL,
            remark TEXT,
            amount REAL NOT NULL,
            date TEXT NOT NULL,
            version INTEGER DEFAULT 1,
            updatedAt INTEGER,
            deletedAt INTEGER
          )
        `, { transaction })

        // 2. 复制数据（将 INTEGER id 转换为字符串）
        console.log('复制数据到新表...')
        const now = Date.now()
        await sequelize.query(`
          INSERT INTO expenses_new (id, type, remark, amount, date, version, updatedAt, deletedAt)
          SELECT 
            CAST(id AS TEXT),
            type,
            remark,
            amount,
            date,
            1,
            ${now},
            NULL
          FROM expenses
        `, { transaction })

        // 3. 删除旧表
        console.log('删除旧表...')
        await sequelize.query('DROP TABLE expenses', { transaction })

        // 4. 重命名新表
        console.log('重命名新表...')
        await sequelize.query('ALTER TABLE expenses_new RENAME TO expenses', { transaction })

        await transaction.commit()
        console.log('表重建完成！')
      } catch (error) {
        await transaction.rollback()
        throw error
      }
    } else if (needsNewColumns) {
      // 只需要添加新字段
      console.log('\n添加新字段...')

      if (!columnNames.includes('version')) {
        console.log('添加 version 字段...')
        await sequelize.query('ALTER TABLE expenses ADD COLUMN version INTEGER DEFAULT 1')
        await sequelize.query('UPDATE expenses SET version = 1 WHERE version IS NULL')
      }

      if (!columnNames.includes('updatedAt')) {
        console.log('添加 updatedAt 字段...')
        await sequelize.query('ALTER TABLE expenses ADD COLUMN updatedAt BIGINT')
        const now = Date.now()
        await sequelize.query(`UPDATE expenses SET updatedAt = ${now} WHERE updatedAt IS NULL`)
      }

      if (!columnNames.includes('deletedAt')) {
        console.log('添加 deletedAt 字段...')
        await sequelize.query('ALTER TABLE expenses ADD COLUMN deletedAt BIGINT DEFAULT NULL')
      }
    } else {
      console.log('\n数据库已是最新结构，无需迁移')
    }

    // 验证最终结构
    const [finalColumns] = await sequelize.query('PRAGMA table_info(expenses)')
    console.log('\n最终表结构:')
    finalColumns.forEach(col => {
      console.log(`  ${col.name}: ${col.type} ${col.notnull ? 'NOT NULL' : 'NULL'} ${col.pk ? 'PRIMARY KEY' : ''}`)
    })

    console.log('\n数据库迁移完成！')
    console.log('请重启服务器以应用更改。')
  } catch (error) {
    console.error('迁移失败:', error)
    throw error
  } finally {
    await sequelize.close()
  }
}

migrate()
