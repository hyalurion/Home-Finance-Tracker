<!-- ExpenseStats.vue -->
<template>
    <div class="stats-container">
      <div class="stats-summary">
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.rowCount') }}：</span>
          <span class="stat-value">{{ statistics.count }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.totalAmount') }}：</span>
          <span class="stat-value">{{ $t('common.currencySymbol') }}{{ statistics.totalAmount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.averageAmount') }}：</span>
          <span class="stat-value">{{ $t('common.currencySymbol') }}{{ statistics.averageAmount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.medianAmount') }}：</span>
          <span class="stat-value">{{ $t('common.currencySymbol') }}{{ statistics.medianAmount }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ $t('expense.stats.amountRange') }}：</span>
          <span class="stat-value">{{ $t('common.currencySymbol') }}{{ statistics.minAmount }}-{{ $t('common.currencySymbol') }}{{ statistics.maxAmount }}</span>
        </div>
      </div>
    </div>
  </template>

<script>
import { onMounted, watch } from 'vue';

export default {
  props: {
    statistics: {
      type: Object,
      required: true
    }
  },
  setup(props) {
    // 组件挂载日志
    onMounted(() => {
      console.log('ExpenseStats component mounted:', {
        count: props.statistics.count,
        totalAmount: props.statistics.totalAmount
      });
    });
    
    // 监听统计数据变化
    watch(() => props.statistics, (newStats) => {
      console.log('Expense statistics updated:', {
        count: newStats.count,
        totalAmount: newStats.totalAmount,
        averageAmount: newStats.averageAmount
      });
    }, { deep: true });
    
    return {};
  }
};
</script>

  <style scoped>
  .stats-container {
    background: transparent;
    border-radius: 10px;
    padding: 20px;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

  .stats-summary {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
    gap: 15px;
  }

  .stat-item {
    display: flex;
    justify-content: space-between;
    padding: 10px;
    background: transparent;
    border-radius: 8px;
}

  .stat-label {
    font-weight: 500;
    color: #495057;
  }

  .stat-value {
    font-weight: 600;
    color: #4361ee;
  }

  @media (prefers-color-scheme: dark) {
    .stats-container {
      background: rgba(30, 30, 30, 0.7);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
    }

    .stat-item {
      background: rgba(40, 40, 40, 0.5);
    }

    .stat-label {
      color: #e9ecef;
    }

    .stat-value {
      color: #a5b4fc;
    }
  }
  </style>
