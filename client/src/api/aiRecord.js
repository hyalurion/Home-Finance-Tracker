import axios from 'axios';

/**
 * AI智能记录API模块
 * @module api/aiRecord
 * @desc 提供与SiliconFlow API交互，实现AI智能解析记录功能
 */

// SiliconFlow API配置
const SILICONFLOW_API_URL = 'https://api.siliconflow.cn/v1/chat/completions';
const MODEL_NAME = 'Qwen/Qwen3-8B';

/**
 * 创建与SiliconFlow API交互的axios实例
 */
const aiApi = axios.create({
  baseURL: SILICONFLOW_API_URL,
  timeout: 300000,
  headers: {
    'Content-Type': 'application/json'
    // 注意：Authorization头部将通过setApiKey函数动态设置
  }
});

const today = new Date().toLocaleDateString('zh-CN');

/**
 * 解析长文本为格式化的记录数据
 * @param {string} text - 要解析的长文本
 * @returns {Promise<Object>} 解析后的格式化记录数据
 */
export const parseTextToRecord = async (text) => {
  try {
    const prompt = `请分析以下文本，提取其中的所有消费信息。如果有多个消费记录，请以JSON数组的形式输出。
每个记录应包含：
{
  "type": "消费类型", // 从预定义列表中选择：日常用品、奢侈品、通讯费用、食品、零食糖果、冷饮、方便食品、纺织品、饮品、调味品、交通出行、餐饮、医疗费用、水果、其他、水产品、乳制品、礼物人情、旅行度假、政务、水电煤气、美容美发、豆制品、个护美妆、电子产品、家用电器、五金、服装
  "amount": 金额, // 数字类型
  "date": "日期", // 日期，格式YYYY-MM-DD
  "remark": "备注" // 详细说明，注意：此处必须包含消费物品/服务的名称
}

请注意：
1. 如果文本中有多个消费记录，请返回JSON数组格式
2. 如果只有一个消费记录，请返回单个JSON对象或只有一个元素的数组
3. 如果文本中没有明确的消费类型，请根据内容选择最合适的预定义类型
4. 如果没有明确的日期，请使用今天日期${today}
5. 只返回JSON数据，不要添加其他无关内容

文本内容：${text}`;

    const response = await aiApi.post('', {
      model: MODEL_NAME,
      messages: [
        {
          role: "system",
          content: "你是一个智能消费记录解析助手，能够从文本中提取消费信息并格式化输出。"
        },
        {
          role: "user",
          content: prompt
        }
      ],
      temperature: 0.2,
      stream: false
    });

    // 解析响应内容
    const content = response.data.choices[0].message.content;
    const parsedData = JSON.parse(content);
    
    // 确保返回的是数组格式，方便统一处理
    return Array.isArray(parsedData) ? parsedData : [parsedData];
  } catch (error) {
    console.error('AI文本解析失败:', error);
    throw error;
  }
};

/**
 * 上传图片并解析为记录数据
 * @param {File} imageFile - 要上传的图片文件
 * @returns {Promise<Object>} 解析后的格式化记录数据
 */
export const parseImageToRecord = async (imageFile) => {
  try {
    // 由于SiliconFlow API支持多模态，我们可以直接发送图片和提示
    // 首先将图片转换为Base64
    const base64Image = await fileToBase64(imageFile);
    
    const prompt = `请分析图片中的所有消费信息。如果有多个消费记录，请以JSON数组的形式输出。
每个记录应包含：
{
  "type": "消费类型", // 从预定义列表中选择：日常用品、奢侈品、通讯费用、食品、零食糖果、冷饮、方便食品、纺织品、饮品、调味品、交通出行、餐饮、医疗费用、水果、其他、水产品、乳制品、礼物人情、旅行度假、政务、水电煤气、美容美发、豆制品、个护美妆、电子产品、家用电器、五金、服装
  "amount": 金额, // 数字类型
  "date": "日期", // 日期，格式YYYY-MM-DD
  "remark": "备注" // 详细说明，注意：此处必须包含消费物品/服务的名称
}

请注意：
1. 如果图片中有多个消费记录，请返回JSON数组格式
2. 如果只有一个消费记录，请返回单个JSON对象或只有一个元素的数组
3. 如果图片中没有明确的消费类型，请根据内容选择最合适的预定义类型
4. 如果没有明确的日期，请使用今天日期${today}
5. 只返回JSON数据，不要添加其他无关内容`;

    // 对于图片解析，使用支持多模态的模型
    const imageModel = 'Qwen/Qwen3.5-4B'; // 使用支持图片的模型
    
    const response = await aiApi.post('', {
      model: imageModel,
      messages: [
        {
          role: "system",
          content: "你是一个智能消费记录解析助手，能够从图片中提取消费信息并格式化输出。"
        },
        {
          role: "user",
          content: [
            { type: "text", text: prompt },
            { 
              type: "image_url", 
              image_url: { url: `data:image/jpeg;base64,${base64Image}` } 
            }
          ]
        }
      ],
      temperature: 0.2,
      stream: false
    });

    // 解析响应内容
    const content = response.data.choices[0].message.content;
    const parsedData = JSON.parse(content);
    
    // 确保返回的是数组格式，方便统一处理
    return Array.isArray(parsedData) ? parsedData : [parsedData];
  } catch (error) {
    console.error('AI图片解析失败:', error);
    throw error;
  }
};

/**
 * 将文件转换为Base64
 * @param {File} file - 要转换的文件
 * @returns {Promise<string>} Base64编码的字符串
 */
const fileToBase64 = (file) => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = () => resolve(reader.result.split(',')[1]);
    reader.onerror = error => reject(error);
  });
};

/**
 * 设置API密钥
 * @param {string} apiKey - 用户的API密钥
 */
export const setApiKey = (apiKey) => {
  if (apiKey) {
    aiApi.defaults.headers['Authorization'] = `Bearer ${apiKey}`;
    console.log('SiliconFlow API密钥已设置');
  } else {
    console.warn('未提供有效的SiliconFlow API密钥');
  }
};

/**
 * 使用DeepSeek模型生成消费记录分析报告
 * @param {Array} expenses - 消费记录数组
 * @param {string} question - 用户的问题（可选）
 * @returns {Promise<string>} 生成的报告内容
 */
export const generateExpenseReport = async (expenses, question = '') => {
  try {
    // 数据验证
    if (!Array.isArray(expenses)) {
      throw new Error('消费数据必须是数组格式');
    }
    
    // 定义报告生成模型
    const reportModel = 'deepseek-ai/DeepSeek-R1-0528-Qwen3-8B';
    
    // 准备消费数据摘要
    const expenseSummary = {
      totalCount: expenses.length,
      totalAmount: expenses.reduce((sum, item) => sum + parseFloat(item.amount), 0),
      recentExpenses: expenses.slice(0, 10).map(item => ({
        type: item.type,
        amount: item.amount,
        date: item.date,
        remark: item.remark
      }))
    };
    
    // 根据是否有问题构建不同的提示
    let prompt;
    if (question) {
      // 过滤问题内容，确保安全性
      const filteredQuestion = filterQuestionContent(question);
      prompt = `用户提供了以下消费数据摘要和问题，请基于这些信息回答用户的问题。用户问题可能与消费数据摘要中的内容相关，可供参考：\n\n消费数据摘要：\n${JSON.stringify(expenseSummary, null, 2)}\n\n用户问题：${filteredQuestion}\n\n请以友好、专业的语气回答，提供详细的分析和建议。`;
    } else {
      prompt = `请根据用户的问题进行回答，用户的问题可能与消费数据摘要中的内容相关，可供参考：\n\n消费数据摘要：\n${JSON.stringify(expenseSummary, null, 2)}\n\n用户问题：${question}`;
    }
    
    const response = await aiApi.post('', {
      model: reportModel,
      messages: [
        {
          role: "system",
          content: "你是一个智能助手，能够参考用户的消费记录回答相关问题。请有需要的根据用户提供的消费数据摘要回答。请使用Markdown格式输出，并确保内容易于阅读和理解。对于数据请尽量使用表格和列表的形式展示，以提高可读性。"
        },
        {
          role: "user",
          content: prompt
        }
      ],
      temperature: 0.7,
      stream: false,
      top_p: 0.95,
      frequency_penalty: 0,
      presence_penalty: 0
    });
    
    // 响应验证
    if (!response.data || !response.data.choices || response.data.choices.length === 0) {
      throw new Error('API返回格式不正确');
    }
    
    const reportContent = response.data.choices[0].message.content;
    
    // 确保返回的内容是Markdown格式
    if (!isMarkdownContent(reportContent)) {
      // 如果不是Markdown格式，尝试转换为Markdown格式
      return convertToMarkdown(reportContent);
    }
    
    return reportContent;
  } catch (error) {
    console.error('生成消费报告失败:', error);
    // 提供友好的错误信息
    const errorMessage = error.response?.data?.error?.message || error.message || '生成报告失败，请稍后重试';
    throw new Error(`生成消费报告失败: ${errorMessage}`);
  }
};

/**
 * 过滤问题内容，确保安全性
 * @param {string} question - 要过滤的问题
 * @returns {string} 过滤后的问题
 */
function filterQuestionContent(question) {
  // 简单的内容过滤，可以根据需要扩展
  const unsafePatterns = [
    /<script.*?>.*?<\/script>/gi,
    /<.*?>/gi,
    /alert\(.*?\)/gi,
    /eval\(.*?\)/gi
  ];
  
  let filteredQuestion = question;
  unsafePatterns.forEach(pattern => {
    filteredQuestion = filteredQuestion.replace(pattern, '');
  });
  
  return filteredQuestion;
}

/**
 * 检查内容是否为Markdown格式
 * @param {string} content - 要检查的内容
 * @returns {boolean} 是否为Markdown格式
 */
function isMarkdownContent(content) {
  // 简单检查常见的Markdown元素
  const markdownPatterns = [
    /^# .*$/m,          // 一级标题
    /^## .*$/m,         // 二级标题
    /^\* .*$/m,         // 无序列表
    /^\d+\. .*$/m,      // 有序列表
    /`.*?`/m,           // 行内代码
    /```[\s\S]*?```/m,  // 代码块
    /\*\*.*?\*\*/m,     // 粗体
    /\*.*?\*/m          // 斜体
  ];
  
  return markdownPatterns.some(pattern => pattern.test(content));
}

/**
 * 转换内容为Markdown格式
 * @param {string} content - 要转换的内容
 * @returns {string} 转换后的Markdown内容
 */
function convertToMarkdown(content) {
  // 简单的转换，实际应用中可能需要更复杂的处理
  let markdownContent = content;
  
  // 添加标题
  markdownContent = `# 消费记录分析报告\n\n${markdownContent}`;
  
  // 将换行转换为段落
  markdownContent = markdownContent.replace(/\n\n+/g, '\n\n');
  
  return markdownContent;
};