const sqlite3 = require('sqlite3').verbose();
const path = require('path');

// 数据库文件路径
const dbPath = path.join(__dirname, '../database.sqlite');

// 创建数据库连接
const db = new sqlite3.Database(dbPath);

db.serialize(() => {
  console.log('开始数据库迁移：为Members表添加avatar字段...');
  
  // 为Members表添加avatar列
  db.run(`
    ALTER TABLE Members ADD COLUMN avatar TEXT;
  `, (err) => {
    if (err) {
      console.error('添加avatar字段失败:', err.message);
      db.close();
      return;
    }
    
    console.log('成功为Members表添加avatar字段');
    
    // 验证表结构
    console.log('验证表结构...');
    db.all('PRAGMA table_info(Members);', (err, columns) => {
      if (err) {
        console.error('获取表结构失败:', err.message);
      } else {
        console.log('Members表当前结构:');
        columns.forEach(col => {
          console.log(`- ${col.name}: ${col.type}${col.notnull ? ' NOT NULL' : ''}${col.pk ? ' PRIMARY KEY' : ''}`);
        });
      }
      console.log('数据库迁移完成!');
      db.close();
    });
  });
});

// 捕获未处理的错误
db.on('error', (err) => {
  console.error('数据库错误:', err.message);
});
