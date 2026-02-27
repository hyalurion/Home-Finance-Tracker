import { defineStore } from 'pinia';

const isValidDate = (date) => {
  return date instanceof Date && !isNaN(date.getTime());
};

const formatDate = (date) => {
  if (!isValidDate(date)) return '';
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
};

const formatMonth = (date) => {
  if (!isValidDate(date)) return '';
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  return `${year}-${month}`;
};

const parseDate = (dateString) => {
  if (!dateString) return null;
  const date = new Date(dateString);
  return isValidDate(date) ? date : null;
};

export const useSpendingStore = defineStore('spending', {
  state: () => ({
    monthlyLimit: 0, // 月度消费上限
    currentMonthSpending: 0, // 当前月消费总额
    isLimitEnabled: false, // 是否启用限制功能
    lastCalculatedMonth: '', // 上次计算的月份，用于缓存优化
    warningThreshold: 0.8, // 警告阈值（80%）
    expenses: [] // 消费记录缓存
  }),

  getters: {
    // 计算消费百分比（不限制最大值，用于显示真实比例）
    spendingPercentage: (state) => {
      if (!state.monthlyLimit || state.monthlyLimit <= 0) return 0;
      return (state.currentMonthSpending / state.monthlyLimit) * 100;
    },

    // 剩余可消费金额
    remainingAmount: (state) => {
      return Math.max(state.monthlyLimit - state.currentMonthSpending, 0);
    },

    // 是否超出限制
    isOverLimit: (state) => {
      return state.isLimitEnabled && state.currentMonthSpending > state.monthlyLimit;
    },

    // 是否接近限制（达到警告阈值）
    isNearLimit: (state) => {
      return state.isLimitEnabled &&
             state.currentMonthSpending >= (state.monthlyLimit * state.warningThreshold) &&
             !state.isOverLimit;
    },

    // 获取当前状态类型
    statusType: (state) => {
      if (!state.isLimitEnabled) return 'disabled';
      if (state.isOverLimit) return 'danger';
      if (state.isNearLimit) return 'warning';
      return 'normal';
    },

    // 获取状态颜色
    statusColor: (state) => {
      switch (state.statusType) {
      case 'danger': return '#f56c6c';
      case 'warning': return '#e6a23c';
      case 'normal': return '#67c23a';
      default: return '#909399';
      }
    },

    // 当前月份字符串
    currentMonth: () => {
      return formatMonth(new Date());
    }
  },

  actions: {
    // 设置月度消费限制
    setMonthlyLimit (limit) {
      this.monthlyLimit = Math.max(0, Number(limit) || 0);
      this.saveSettings();
    },

    // 启用/禁用限制功能
    toggleLimitEnabled (enabled) {
      this.isLimitEnabled = Boolean(enabled);
      this.saveSettings();
    },

    // 设置警告阈值
    setWarningThreshold (threshold) {
      this.warningThreshold = Math.max(0.1, Math.min(1, Number(threshold) || 0.8));
      this.saveSettings();
    },

    // 更新消费记录并重新计算当前月消费
    updateExpenses (expenses) {
      this.expenses = expenses || [];
      this.calculateCurrentMonthSpending();
    },

    // 计算当前月消费总额
    calculateCurrentMonthSpending () {
      const currentMonth = this.currentMonth;

      // 如果是同一个月且已经计算过，直接返回缓存结果
      if (this.lastCalculatedMonth === currentMonth && this.currentMonthSpending > 0) {
        return this.currentMonthSpending;
      }

      // 计算当前月的消费总额
      const monthlyTotal = this.expenses
        .filter(expense => {
          // 使用date字段替代time字段，与后端数据保持一致
          // 同时保持向后兼容性，先检查date字段，再检查time字段
          const expenseDate = expense.date || expense.time;
          if (!expenseDate) return false;
          const parsedDate = parseDate(expenseDate);
          if (!parsedDate) return false;
          const expenseMonth = formatMonth(parsedDate);
          return expenseMonth === currentMonth;
        })
        .reduce((total, expense) => {
          const amount = Number(expense.amount) || 0;
          return total + Math.abs(amount); // 使用绝对值，确保都是正数
        }, 0);

      this.currentMonthSpending = monthlyTotal;
      this.lastCalculatedMonth = currentMonth;

      return monthlyTotal;
    },

    // 添加新的消费记录
    addExpense (expense) {
      // 使用date字段替代time字段，保持向后兼容
      if (expense && expense.amount && (expense.date || expense.time)) {
        this.expenses.push(expense);
        this.calculateCurrentMonthSpending();
      }
    },

    // 获取消费状态信息
    getSpendingStatus () {
      if (!this.isLimitEnabled) {
        return {
          type: 'info',
          message: 'spending.status.disabled',
          showProgress: false
        };
      }

      const percentage = this.spendingPercentage;
      const remaining = this.remainingAmount;

      if (this.isOverLimit) {
        const overAmount = this.currentMonthSpending - this.monthlyLimit;
        return {
          type: 'danger',
          message: 'spending.status.overLimit',
          data: { overAmount, percentage: Math.round(percentage) },
          showProgress: true
        };
      }

      if (this.isNearLimit) {
        return {
          type: 'warning',
          message: 'spending.status.nearLimit',
          data: { remaining, percentage: Math.round(percentage) },
          showProgress: true
        };
      }

      return {
        type: 'success',
        message: 'spending.status.normal',
        data: { remaining, percentage: Math.round(percentage) },
        showProgress: true
      };
    },

    // 保存设置到本地存储
    saveSettings () {
      try {
        const settings = {
          monthlyLimit: this.monthlyLimit,
          isLimitEnabled: this.isLimitEnabled,
          warningThreshold: this.warningThreshold
        };
        localStorage.setItem('homemoney-spending-settings', JSON.stringify(settings));
      } catch (error) {
        console.error('Failed to save spending settings:', error);
      }
    },

    // 从本地存储加载设置
    loadSettings () {
      try {
        const saved = localStorage.getItem('homemoney-spending-settings');
        if (saved) {
          const settings = JSON.parse(saved);
          this.monthlyLimit = Number(settings.monthlyLimit) || 0;
          this.isLimitEnabled = Boolean(settings.isLimitEnabled);
          this.warningThreshold = Number(settings.warningThreshold) || 0.8;
        }
      } catch (error) {
        console.error('Failed to load spending settings:', error);
        // 使用默认值
        this.monthlyLimit = 0;
        this.isLimitEnabled = false;
        this.warningThreshold = 0.8;
      }
      this.fetchExpenses();
    },

    // 重置所有设置
    resetSettings () {
      this.monthlyLimit = 0;
      this.isLimitEnabled = false;
      this.warningThreshold = 0.8;
      this.currentMonthSpending = 0;
      this.lastCalculatedMonth = '';
      this.saveSettings();
    },

    // 从后端API获取消费数据
    async fetchExpenses () {
      try {
        console.log('Fetching expenses from API...');
        const response = await fetch('/api/expenses?limit=1000'); // 获取更多数据用于计算
        console.log('Expenses API response status:', response.status);
        if (!response.ok) throw new Error(`Failed to fetch expenses: ${response.statusText}`);
        const result = await response.json();
        console.log('Fetched expenses data:', result);
        
        // 适配新的API响应格式
        let expenses = [];
        if (result && result.data && Array.isArray(result.data)) {
          expenses = result.data;
        } else if (Array.isArray(result)) {
          expenses = result;
        }
        
        this.updateExpenses(expenses);
        console.log('Updated expenses - currentMonthSpending:', this.currentMonthSpending);
      } catch (error) {
        console.error('Error fetching expenses:', error);
      }
    }
  }
});
