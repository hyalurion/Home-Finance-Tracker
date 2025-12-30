import axios from 'axios';

// 使用相对路径API基础URL，通过Vite代理转发请求
export const API_BASE = '/api';

export const ExpenseAPI = {
  async addExpensesBatch (records) {
    try {
      return await axios.post(`${API_BASE}/expenses/batch`, records, {
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
      return await axios.post(`${API_BASE}/expenses`, data, {
        headers: {
          'Content-Type': 'application/json',
          'X-Requested-With': 'XMLHttpRequest'
        },
        transformRequest: [(data) => JSON.stringify({
          ...data,
          amount: parseFloat(data.amount),
          // 确保使用date字段，不再需要time字段
          date: data.date,
          // 使用remark字段
          remark: data.remark || ''
        })]
      });
    } catch (error) {
      console.error('添加消费数据失败:', error);
      throw error; // 添加操作失败需要向上抛出错误
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
      const response = await axios.put(`${API_BASE}/expenses/${id}`, data, {
        headers: {
          'Content-Type': 'application/json'
        },
        transformRequest: [(data) => JSON.stringify({
          ...data,
          amount: parseFloat(data.amount),
          // 确保使用date字段，不再需要time字段
          date: data.date
        })]
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