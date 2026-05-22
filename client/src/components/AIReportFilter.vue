<template>
  <div class="ai-report-filter">
    <div class="filter-section">
      <h4 class="filter-title">{{ t('ai.report.filterTitle') }}</h4>
      
      <!-- 年份和月份选择 -->
      <div class="filter-row">
        <GlassFormItem label="年份">
          <CustomSelect
            v-model="localYear"
            :options="availableYears"
            :empty-option-label="'全部年份'"
            :include-empty-option="true"
            @change="handleYearChange"
          />
        </GlassFormItem>
        
        <GlassFormItem label="月份">
          <CustomSelect
            v-model="localMonth"
            :options="months"
            :empty-option-label="'全部月份'"
            :include-empty-option="true"
            @change="handleMonthChange"
          />
        </GlassFormItem>
      </div>
      
      <!-- 消费类型多选 -->
      <div class="filter-row">
        <GlassFormItem label="消费类型（可多选）">
          <div class="type-checkboxes">
            <label v-for="type in expenseTypes" :key="type" class="checkbox-label">
              <input 
                type="checkbox" 
                :value="type" 
                v-model="localSelectedTypes"
                class="checkbox-input"
              />
              <span>{{ type }}</span>
            </label>
          </div>
        </GlassFormItem>
      </div>
      
      <!-- 快捷选择按钮 -->
      <div class="quick-buttons">
        <GlassButton size="small" @click="selectCurrentMonth">本月</GlassButton>
        <GlassButton size="small" @click="selectLastMonth">上月</GlassButton>
        <GlassButton size="small" @click="selectThisYear">本年</GlassButton>
        <GlassButton size="small" @click="clearFilters">清空筛选</GlassButton>
      </div>
      
      <!-- 数据统计预览 -->
      <div class="stats-preview">
        <h5>{{ t('ai.report.statsPreview') }}</h5>
        <div v-if="isLoading" class="loading-text">
          {{ t('ai.report.loading') }}
        </div>
        <div v-else class="stats-grid">
          <div class="stat-item">
            <span class="stat-label">{{ t('ai.report.totalCount') }}</span>
            <span class="stat-value">{{ stats.totalCount }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">{{ t('ai.report.totalAmount') }}</span>
            <span class="stat-value">{{ stats.totalAmount.toFixed(2) }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">{{ t('ai.report.averageAmount') }}</span>
            <span class="stat-value">{{ stats.averageAmount.toFixed(2) }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">{{ t('ai.report.medianAmount') }}</span>
            <span class="stat-value">{{ stats.medianAmount.toFixed(2) }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">{{ t('ai.report.minAmount') }}</span>
            <span class="stat-value">{{ stats.minAmount.toFixed(2) }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-label">{{ t('ai.report.maxAmount') }}</span>
            <span class="stat-value">{{ stats.maxAmount.toFixed(2) }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import GlassFormItem from '@/components/GlassFormItem.vue';
import GlassButton from '@/components/GlassButton.vue';
import CustomSelect from '@/components/CustomSelect.vue';
import { ExpenseAPI } from '@/api/expenses';

const { t } = useI18n();

const emit = defineEmits(['filterChange']);

// 消费类型列表
const expenseTypes = [
  '日常用品', '奢侈品', '通讯费用', '食品', '零食糖果', '冷饮', '方便食品', 
  '纺织品', '饮品', '调味品', '交通出行', '餐饮', '医疗费用', '水果', '其他', 
  '水产品', '乳制品', '礼物人情', '旅行度假', '政务', '水电煤气', '美容美发', 
  '豆制品', '个护美妆', '电子产品', '家用电器', '五金', '服装'
];

// 月份选项
const months = [
  { value: '01', label: '1月' },
  { value: '02', label: '2月' },
  { value: '03', label: '3月' },
  { value: '04', label: '4月' },
  { value: '05', label: '5月' },
  { value: '06', label: '6月' },
  { value: '07', label: '7月' },
  { value: '08', label: '8月' },
  { value: '09', label: '9月' },
  { value: '10', label: '10月' },
  { value: '11', label: '11月' },
  { value: '12', label: '12月' }
];

// 当前日期
const now = new Date();
const currentYear = now.getFullYear();
const currentMonth = String(now.getMonth() + 1).padStart(2, '0');

// 可用年份（最近10年）
const availableYears = computed(() => {
  return Array.from({ length: 10 }, (_, i) => ({
    value: String(currentYear - i),
    label: `${currentYear - i}年`
  }));
});

// 加载状态
const isLoading = ref(true);
// 所有消费数据
const allExpenses = ref([]);

// 本地筛选状态
const localYear = ref('');
const localMonth = ref('');
const localSelectedTypes = ref([]);

// 获取所有消费数据
const fetchAllExpenses = async () => {
  isLoading.value = true;
  try {
    console.log('AIReportFilter: Fetching all expenses...');
    
    // 使用分页获取所有数据
    const allData = [];
    let page = 1;
    const limit = 100;
    let hasMore = true;
    
    while (hasMore) {
      const response = await ExpenseAPI.getExpenses(page, limit);
      const data = response?.data?.data || [];
      
      if (data.length > 0) {
        allData.push(...data);
        page++;
        // 如果返回的数据少于请求的数量，说明已经没有更多数据了
        if (data.length < limit) {
          hasMore = false;
        }
      } else {
        hasMore = false;
      }
    }
    
    // 确保数据格式正确
    allExpenses.value = allData
      .map(item => ({
        type: item.type?.trim() || item.type,
        remark: item.remark?.trim() || item.remark,
        amount: Number(item.amount),
        date: item.date
      }))
      .filter(item => !isNaN(item.amount) && item.amount > 0);
    
    console.log('AIReportFilter: Fetched expenses count:', allExpenses.value.length);
    
    // 触发筛选更新（包含筛选条件信息）
    emit('filterChange', {
      ...stats.value,
      filterConditions: {
        year: localYear.value,
        month: localMonth.value,
        types: [...localSelectedTypes.value]
      }
    });
  } catch (error) {
    console.error('AIReportFilter: Error fetching expenses:', error);
    allExpenses.value = [];
  } finally {
    isLoading.value = false;
  }
};

// 筛选后的消费数据
const filteredExpenses = computed(() => {
  let result = [...allExpenses.value];
  
  // 按年份筛选
  if (localYear.value) {
    result = result.filter(expense => {
      try {
        const expenseDate = new Date(expense.date);
        return expenseDate.getFullYear() === parseInt(localYear.value);
      } catch {
        return false;
      }
    });
  }
  
  // 按月份筛选
  if (localMonth.value) {
    result = result.filter(expense => {
      try {
        const expenseDate = new Date(expense.date);
        return String(expenseDate.getMonth() + 1).padStart(2, '0') === localMonth.value;
      } catch {
        return false;
      }
    });
  }
  
  // 按类型筛选
  if (localSelectedTypes.value.length > 0) {
    result = result.filter(expense => 
      localSelectedTypes.value.includes(expense.type)
    );
  }
  
  return result;
});

// 统计数据
const stats = computed(() => {
  const expenses = filteredExpenses.value;
  const amounts = expenses
    .map(e => parseFloat(e.amount))
    .filter(a => !isNaN(a))
    .sort((a, b) => a - b);
  
  const totalCount = expenses.length;
  const totalAmount = amounts.reduce((sum, a) => sum + a, 0);
  const averageAmount = totalCount > 0 ? totalAmount / totalCount : 0;
  const medianAmount = totalCount > 0 
    ? amounts[Math.floor(amounts.length / 2)] 
    : 0;
  const minAmount = amounts.length > 0 ? amounts[0] : 0;
  const maxAmount = amounts.length > 0 ? amounts[amounts.length - 1] : 0;
  
  return {
    totalCount,
    totalAmount,
    averageAmount,
    medianAmount,
    minAmount,
    maxAmount,
    filteredExpenses: expenses
  };
});

// 监听筛选变化，通知父组件
watch([localYear, localMonth, localSelectedTypes], () => {
  console.log('Filter changed:', {
    year: localYear.value,
    month: localMonth.value,
    types: localSelectedTypes.value,
    filteredCount: filteredExpenses.value.length
  });
  
  // 传递完整的筛选信息和统计数据
  emit('filterChange', {
    ...stats.value,
    filterConditions: {
      year: localYear.value,
      month: localMonth.value,
      types: [...localSelectedTypes.value]
    }
  });
}, { deep: true });

// 处理年份变化
const handleYearChange = (value) => {
  console.log('Year changed:', value);
  localYear.value = value;
};

// 处理月份变化
const handleMonthChange = (value) => {
  console.log('Month changed:', value);
  localMonth.value = value;
};

// 快捷选择方法
const selectCurrentMonth = () => {
  localYear.value = String(currentYear);
  localMonth.value = currentMonth;
};

const selectLastMonth = () => {
  const lastMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1);
  localYear.value = String(lastMonth.getFullYear());
  localMonth.value = String(lastMonth.getMonth() + 1).padStart(2, '0');
};

const selectThisYear = () => {
  localYear.value = String(currentYear);
  localMonth.value = '';
};

const clearFilters = () => {
  localYear.value = '';
  localMonth.value = '';
  localSelectedTypes.value = [];
};

// 初始化时获取数据
onMounted(() => {
  fetchAllExpenses();
});

// 暴露方法供外部调用
defineExpose({
  getStats: () => stats.value,
  clearFilters,
  refreshData: fetchAllExpenses
});
</script>

<style scoped>
.ai-report-filter {
  padding: 16px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 8px;
  backdrop-filter: blur(10px);
}

.filter-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin: 0 0 8px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #eee;
}

.filter-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  align-items: flex-start;
}

.filter-row .glass-form-item {
  flex: 1;
  min-width: 150px;
}

.type-checkboxes {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  max-height: 120px;
  overflow-y: auto;
  padding-right: 8px;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 14px;
  color: #333;
}

.checkbox-input {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.quick-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.stats-preview {
  padding: 12px;
  background: #f8f9fa;
  border-radius: 4px;
}

.stats-preview h5 {
  font-size: 14px;
  font-weight: 600;
  color: #333;
  margin: 0 0 12px 0;
}

.loading-text {
  text-align: center;
  padding: 20px;
  color: #666;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.stat-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px;
  background: white;
  border-radius: 4px;
}

.stat-label {
  font-size: 12px;
  color: #666;
}

.stat-value {
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

@media (prefers-color-scheme: dark) {
  .ai-report-filter {
    background: rgba(30, 30, 46, 0.8);
  }
  
  .filter-title {
    color: #e4e4e7;
    border-bottom-color: #3f3f46;
  }
  
  .checkbox-label {
    color: #e4e4e7;
  }
  
  .stats-preview {
    background: #27272a;
  }
  
  .stats-preview h5 {
    color: #e4e4e7;
  }
  
  .loading-text {
    color: #a1a1aa;
  }
  
  .stat-item {
    background: #1f1f2e;
  }
  
  .stat-label {
    color: #a1a1aa;
  }
  
  .stat-value {
    color: #e4e4e7;
  }
}
</style>