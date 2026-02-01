const fs = require('fs');
const path = require('path');

// 翻译文件路径
const localesDir = path.join(__dirname, 'client', 'src', 'locales');
const translationFiles = [
  'en-US.json',
  'zh-CN.json', 
  'zh-TW.json'
];

// 源代码目录
const srcDir = path.join(__dirname, 'client', 'src');

// 提取翻译键的函数
function extractKeysFromTranslationFile(filePath) {
  try {
    const content = fs.readFileSync(filePath, 'utf8');
    const translations = JSON.parse(content);
    const keys = [];
    
    function traverse(obj, prefix = '') {
      for (const key in obj) {
        if (obj.hasOwnProperty(key)) {
          const fullKey = prefix ? `${prefix}.${key}` : key;
          if (typeof obj[key] === 'object' && obj[key] !== null) {
            traverse(obj[key], fullKey);
          } else {
            keys.push(fullKey);
          }
        }
      }
    }
    
    traverse(translations);
    return keys;
  } catch (error) {
    console.error(`Error reading translation file ${filePath}:`, error.message);
    return [];
  }
}

// 搜索源代码中的翻译键使用
function searchKeysInSourceCode(keys) {
  const usedKeys = new Set();
  
  function searchInFile(filePath) {
    try {
      const content = fs.readFileSync(filePath, 'utf8');
      // 匹配 $t('key') 或 t('key') 模式
      const regex = /\$t\(['"]([^'"]+)['"]\)|t\(['"]([^'"]+)['"]\)/g;
      let match;
      while ((match = regex.exec(content)) !== null) {
        const key = match[1] || match[2];
        usedKeys.add(key);
      }
    } catch (error) {
      // 忽略读取错误
    }
  }
  
  function traverseDirectory(dir) {
    const files = fs.readdirSync(dir);
    for (const file of files) {
      const fullPath = path.join(dir, file);
      const stat = fs.statSync(fullPath);
      if (stat.isDirectory()) {
        if (file !== 'node_modules' && file !== '.git') {
          traverseDirectory(fullPath);
        }
      } else if (['.vue', '.js', '.jsx', '.ts', '.tsx'].includes(path.extname(file))) {
        searchInFile(fullPath);
      }
    }
  }
  
  traverseDirectory(srcDir);
  return usedKeys;
}

// 主函数
function main() {
  console.log('开始分析翻译键使用情况...');
  
  // 提取所有翻译键
  const allKeys = new Set();
  const keysByFile = {};
  
  for (const file of translationFiles) {
    const filePath = path.join(localesDir, file);
    const keys = extractKeysFromTranslationFile(filePath);
    keysByFile[file] = keys;
    keys.forEach(key => allKeys.add(key));
  }
  
  console.log(`共提取到 ${allKeys.size} 个翻译键`);
  
  // 搜索使用情况
  const usedKeys = searchKeysInSourceCode(allKeys);
  console.log(`在源代码中找到 ${usedKeys.size} 个使用的翻译键`);
  
  // 找出未使用的键
  const unusedKeys = [];
  allKeys.forEach(key => {
    if (!usedKeys.has(key)) {
      unusedKeys.push(key);
    }
  });
  
  console.log(`发现 ${unusedKeys.length} 个未使用的翻译键`);
  
  // 生成报告
  const reportPath = path.join(__dirname, 'translation-keys-analysis.txt');
  let reportContent = `翻译键使用情况分析报告\n`;
  reportContent += `生成时间: ${new Date().toLocaleString()}\n`;
  reportContent += `=====================================\n\n`;
  
  reportContent += `1. 总体统计\n`;
  reportContent += `-----------------\n`;
  reportContent += `总翻译键数量: ${allKeys.size}\n`;
  reportContent += `已使用翻译键: ${usedKeys.size}\n`;
  reportContent += `未使用翻译键: ${unusedKeys.length}\n`;
  reportContent += `使用率: ${((usedKeys.size / allKeys.size) * 100).toFixed(2)}%\n\n`;
  
  reportContent += `2. 未使用的翻译键\n`;
  reportContent += `-----------------\n`;
  if (unusedKeys.length > 0) {
    unusedKeys.sort().forEach(key => {
      reportContent += `- ${key}\n`;
    });
  } else {
    reportContent += `所有翻译键都在使用中\n`;
  }
  reportContent += `\n`;
  
  reportContent += `3. 各文件翻译键统计\n`;
  reportContent += `-----------------\n`;
  for (const file in keysByFile) {
    const keys = keysByFile[file];
    const fileUsedKeys = keys.filter(key => usedKeys.has(key));
    const fileUnusedKeys = keys.filter(key => !usedKeys.has(key));
    
    reportContent += `${file}:\n`;
    reportContent += `  总键数: ${keys.length}\n`;
    reportContent += `  已使用: ${fileUsedKeys.length}\n`;
    reportContent += `  未使用: ${fileUnusedKeys.length}\n`;
    reportContent += `  使用率: ${((fileUsedKeys.length / keys.length) * 100).toFixed(2)}%\n\n`;
  }
  
  fs.writeFileSync(reportPath, reportContent, 'utf8');
  console.log(`分析报告已生成: ${reportPath}`);
}

// 运行分析
main();