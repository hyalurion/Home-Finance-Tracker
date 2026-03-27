import axios from 'axios';
import { v4 as uuidv4 } from 'uuid';

// 使用相对路径API基础URL，通过Vite代理转发请求
export const API_BASE = '/api';

// 生成UUID
export const generateId = () => uuidv4();

export const ExpenseAPI = {
  async addExpensesBatch (records) {
    try {
      // 为每条记录添加UUID和版本信息
      const recordsWithMeta = records.map(record => ({
        id: record.id || generateId(),
        ...record,
        version: record.version || 1,
        updatedAt: record.updatedAt || Date.now()
      }));
      return await axios.post(`${API_BASE}/expenses/batch`, recordsWithMeta, {
        headers: {
          'Content-Type': 'application/json'
        }
      });
    } catch (error) {
      console.error('批量添加消费记录失败:', error);
      throw error;
    }
  },

  async getExpenses (page = 1, limit = 10, searchParams = {}) {
    console.log('[Expense API] 尝试获取消费数据，API基础URL:', API_BASE);
    try {
      // 构建查询参数
      let params;
      
      // 处理URLSearchParams对象或普通对象
      if (searchParams instanceof URLSearchParams) {
        params = {};
        // 添加基础分页参数
        params.page = page;
        params.limit = limit;
        
        // 从URLSearchParams中提取所有参数
        searchParams.forEach((value, key) => {
          params[key] = value;
        });
      } else {
        // 普通对象的情况
        params = {
          page,
          limit,
          ...searchParams
        };
      }
      
      console.log('[Expense API] 请求参数:', params);
      const response = await axios.get(`${API_BASE}/expenses`, {
        params
      });
      
      // 返回完整的响应对象，包括数据、总数、页码等信息
      return response;
    } catch (error) {
      if (error.code === 'ERR_NETWORK') {
        console.error('获取消费数据失败：网络连接异常，请检查服务器或网络状态。', error);
      } else {
        console.error('获取消费数据失败:', error);
      }
      console.error('[Expense API] 获取消费数据失败详情:', error.response || error.message || error);
      throw error; // 向上传递错误以便前端处理
    }
  },

  async addExpense (data) {
    try {
      const expenseData = {
        id: data.id || generateId(),
        ...data,
        amount: parseFloat(data.amount),
        date: data.date,
        remark: data.remark || '',
        version: data.version || 1,
        updatedAt: data.updatedAt || Date.now()
      };
      return await axios.post(`${API_BASE}/expenses`, expenseData, {
        headers: {
          'Content-Type': 'application/json',
          'X-Requested-With': 'XMLHttpRequest'
        }
      });
    } catch (error) {
      console.error('添加消费数据失败:', error);
      throw error; // 添加操作失败需要向上抛出错误
    }
  },

  // 同步API - 用于离线同步
  async syncExpenses (lastSyncTime, changes) {
    try {
      const response = await axios.post(`${API_BASE}/expenses/sync`, {
        lastSyncTime,
        changes
      }, {
        headers: {
          'Content-Type': 'application/json'
        }
      });
      return response.data;
    } catch (error) {
      console.error('同步消费数据失败:', error);
      throw error;
    }
  },

  async getStatistics (searchParams = {}) {
    try {
      // 处理URLSearchParams对象或普通对象
      let params;
      if (searchParams instanceof URLSearchParams) {
        params = {};
        // 从URLSearchParams中提取所有参数
        searchParams.forEach((value, key) => {
          params[key] = value;
        });
      } else {
        // 普通对象的情况
        params = { ...searchParams };
      }
      
      console.log('[Expense API] 获取统计数据请求参数:', params);
      const response = await axios.get(`${API_BASE}/expenses/statistics`, {
        params
      });
      return response.data;
    } catch (error) {
      console.error('获取统计数据失败:', error);
      return { error: error.message || '未知错误' };
    }
  },

  // 更新消费记录
  async updateExpense (id, data) {
    try {
      console.log(`[Expense API] 更新消费记录 ID: ${id}`, data);
      const updateData = {
        ...data,
        amount: parseFloat(data.amount),
        date: data.date,
        version: (data.version || 0) + 1,
        updatedAt: Date.now()
      };
      const response = await axios.put(`${API_BASE}/expenses/${id}`, updateData, {
        headers: {
          'Content-Type': 'application/json'
        }
      });
      return response.data;
    } catch (error) {
      console.error(`更新消费记录失败 ID: ${id}:`, error);
      throw error;
    }
  },

  // 删除消费记录
  async deleteExpense (id) {
    try {
      console.log(`[Expense API] 删除消费记录 ID: ${id}`);
      const response = await axios.delete(`${API_BASE}/expenses/${id}`);
      return response.data;
    } catch (error) {
      console.error(`删除消费记录失败 ID: ${id}:`, error);
      throw error;
    }
  },

  // 获取按日期分组的消费记录（用于每日统计）
  async getExpensesByDate (page = 1, limit = 10, searchParams = {}) {
    console.log('[Expense API] 尝试获取按日期分组的消费数据');
    try {
      // 构建查询参数
      let params;
      
      // 处理URLSearchParams对象或普通对象
      if (searchParams instanceof URLSearchParams) {
        params = {};
        // 添加基础分页参数
        params.page = page;
        params.limit = limit;
        
        // 从URLSearchParams中提取所有参数
        searchParams.forEach((value, key) => {
          params[key] = value;
        });
      } else {
        // 普通对象的情况
        params = {
          page,
          limit,
          ...searchParams
        };
      }
      
      console.log('[Expense API] 请求参数:', params);
      const response = await axios.get(`${API_BASE}/expenses/by-date`, {
        params
      });
      
      // 返回完整的响应对象，包括数据、总数、页码等信息
      return response;
    } catch (error) {
      if (error.code === 'ERR_NETWORK') {
        console.error('获取按日期分组的消费数据失败：网络连接异常，请检查服务器或网络状态。', error);
      } else {
        console.error('获取按日期分组的消费数据失败:', error);
      }
      console.error('[Expense API] 获取按日期分组的消费数据失败详情:', error.response || error.message || error);
      throw error;
    }
  }
};