<template>
  <div class="ai-report-filter">
    <div class="filter-section">
      <h4 class="filter-title">{{ t('ai.report.filterTitle') }}</h4>
      
      <!-- 年份和月份选择 -->
      <div class="filter-row date-filter-row">
        <div class="date-select-wrapper">
          <span class="date-label">年份</span>
          <CustomSelect
            v-model="localYear"
            :options="availableYears"
            :empty-option-label="'全部年份'"
            :include-empty-option="true"
            @change="handleYearChange"
          />
        </div>
        
        <div class="date-select-wrapper">
          <span class="date-label">月份</span>
          <CustomSelect
            v-model="localMonth"
            :options="months"
            :empty-option-label="'全部月份'"
            :include-empty-option="true"
            @change="handleMonthChange"
          />
        </div>
      </div>
      
      <!-- 消费类型多选 -->
      <div class="filter-row types-section">
        <GlassFormItem label="消费类型（可多选）" class="filter-item types-label">
          <div class="type-checkboxes" :class="{ 'has-active': localSelectedTypes.length > 0 }">
            <label 
              v-for="type in expenseTypes" 
              :key="type" 
              class="checkbox-label"
              :class="{ active: localSelectedTypes.includes(type) }"
            >
              <input 
                type="checkbox" 
                :value="type" 
                v-model="localSelectedTypes"
                class="checkbox-input"
              />
              <span class="checkbox-custom"></span>
              <span class="checkbox-text">{{ type }}</span>
            </label>
          </div>
        </GlassFormItem>
      </div>
      
      <!-- 快捷选择按钮 -->
      <div class="quick-buttons">
        <GlassButton size="small" variant="secondary" @click="selectCurrentMonth">
          <span class="btn-icon">◉</span>本月
        </GlassButton>
        <GlassButton size="small" variant="secondary" @click="selectLastMonth">
          <span class="btn-icon">◑</span>上月
        </GlassButton>
        <GlassButton size="small" variant="secondary" @click="selectThisYear">
          <span class="btn-icon">⊙</span>本年
        </GlassButton>
        <GlassButton size="small" variant="outline" @click="clearFilters">
          <span class="btn-icon">✕</span>清空筛选
        </GlassButton>
      </div>
      
      <!-- 数据统计预览 -->
      <div class="stats-preview">
        <div class="stats-header">
          <h5>{{ t('ai.report.statsPreview') }}</h5>
          <span class="stats-badge" v-if="filteredExpensesCount > 0">
            {{ filteredExpensesCount }} 条记录
          </span>
        </div>
        <div v-if="isLoading" class="loading-state">
          <div class="loading-spinner">
            <div class="spinner-ring"></div>
          </div>
          <span class="loading-text">{{ t('ai.report.loading') }}</span>
        </div>
        <div v-else class="stats-grid">
          <div class="stat-card">
            <span class="stat-label">{{ t('ai.report.totalCount') }}</span>
            <span class="stat-value primary">{{ stats.totalCount }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ t('ai.report.totalAmount') }}</span>
            <span class="stat-value success">¥{{ formatNumber(stats.totalAmount) }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ t('ai.report.averageAmount') }}</span>
            <span class="stat-value">¥{{ formatNumber(stats.averageAmount) }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ t('ai.report.medianAmount') }}</span>
            <span class="stat-value">¥{{ formatNumber(stats.medianAmount) }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ t('ai.report.minAmount') }}</span>
            <span class="stat-value warning">¥{{ formatNumber(stats.minAmount) }}</span>
          </div>
          <div class="stat-card">
            <span class="stat-label">{{ t('ai.report.maxAmount') }}</span>
            <span class="stat-value danger">¥{{ formatNumber(stats.maxAmount) }}</span>
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

// Filtered expenses count for badge display
const filteredExpensesCount = computed(() => filteredExpenses.value.length);

// Format number with thousand separators
const formatNumber = (num) => {
  if (num === 0) return '0.00';
  return num.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
};

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
  padding: 20px;
  background: linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.25) 0%,
    rgba(255, 255, 255, 0.1) 100%
  );
  border-radius: 16px;
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 
    0 8px 32px rgba(0, 0, 0, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
}

.filter-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.filter-title {
  font-size: 18px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 0 0 4px 0;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.3);
  text-shadow: 0 1px 2px rgba(255, 255, 255, 0.5);
}

.filter-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  align-items: start;
}

.date-filter-row {
  display: flex;
  justify-content: center;
  gap: 32px;
  padding: 8px 0;
}

.date-select-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.date-label {
  font-size: 12px;
  font-weight: 500;
  color: #6b7280;
}

.date-filter-row .custom-select {
  min-width: 110px;
  max-width: 140px;
  width: 100%;
}

.filter-row:not(.types-section) {
  gap: 12px;
}

.filter-item {
  min-width: 0;
}

.types-section {
  grid-template-columns: 1fr;
}

.types-label {
  width: 100%;
}

.type-checkboxes {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-height: 140px;
  overflow-y: auto;
  padding: 12px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  transition: all 0.3s ease;
}

.type-checkboxes::-webkit-scrollbar {
  width: 6px;
}

.type-checkboxes::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.05);
  border-radius: 3px;
}

.type-checkboxes::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
  border-radius: 3px;
}

.type-checkboxes.has-active {
  background: rgba(99, 179, 237, 0.15);
  border-color: rgba(99, 179, 237, 0.3);
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
  padding: 6px 10px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.3);
  border: 1px solid transparent;
  transition: all 0.2s ease;
  user-select: none;
}

.checkbox-label:hover {
  background: rgba(255, 255, 255, 0.5);
  border-color: rgba(255, 255, 255, 0.5);
}

.checkbox-label.active {
  background: rgba(99, 179, 237, 0.25);
  border-color: rgba(99, 179, 237, 0.5);
  color: #1e40af;
}

.checkbox-input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.checkbox-custom {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.5);
  border: 1px solid rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.checkbox-label.active .checkbox-custom {
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  border-color: #3b82f6;
}

.checkbox-label.active .checkbox-custom::after {
  content: '✓';
  color: white;
  font-size: 10px;
  font-weight: bold;
}

.checkbox-text {
  white-space: nowrap;
}

.quick-buttons {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.quick-buttons .glass-button {
  flex: 1;
  min-width: fit-content;
  justify-content: center;
}

.btn-icon {
  margin-right: 4px;
  font-size: 12px;
}

.stats-preview {
  padding: 16px;
  background: linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.3) 0%,
    rgba(255, 255, 255, 0.15) 100%
  );
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.stats-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.stats-preview h5 {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin: 0;
}

.stats-badge {
  font-size: 12px;
  padding: 4px 10px;
  background: linear-gradient(135deg, #60a5fa 0%, #3b82f6 100%);
  color: white;
  border-radius: 20px;
  font-weight: 500;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.3);
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 30px 20px;
  gap: 12px;
}

.loading-spinner {
  position: relative;
  width: 40px;
  height: 40px;
}

.spinner-ring {
  position: absolute;
  width: 100%;
  height: 100%;
  border: 3px solid rgba(99, 102, 241, 0.1);
  border-top-color: #6366f1;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

.spinner-ring::before {
  content: '';
  position: absolute;
  top: -3px;
  left: -3px;
  right: -3px;
  bottom: -3px;
  border: 3px solid transparent;
  border-top-color: #818cf8;
  border-radius: 50%;
  animation: spin 1.5s linear infinite;
  opacity: 0.6;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-text {
  font-size: 13px;
  color: #6b7280;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.4);
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  transition: all 0.2s ease;
}

.stat-card:hover {
  background: rgba(255, 255, 255, 0.6);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.stat-label {
  font-size: 11px;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.stat-value {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
}

.stat-value.primary { color: #3b82f6; }
.stat-value.success { color: #10b981; }
.stat-value.warning { color: #f59e0b; }
.stat-value.danger { color: #ef4444; }

/* Responsive Design */
@media (max-width: 768px) {
  .ai-report-filter {
    padding: 16px;
    border-radius: 12px;
  }
  
  .filter-row {
    grid-template-columns: 1fr;
    gap: 12px;
  }
  
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  
  .quick-buttons .glass-button {
    flex: 1 1 calc(50% - 5px);
  }
}

@media (max-width: 480px) {
  .ai-report-filter {
    padding: 12px;
  }
  
  .filter-title {
    font-size: 16px;
  }
  
  .stats-grid {
    grid-template-columns: 1fr;
    gap: 8px;
  }
  
  .stat-card {
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    padding: 10px 14px;
  }
  
  .quick-buttons {
    gap: 8px;
  }
  
  .quick-buttons .glass-button {
    flex: 1 1 100%;
    padding: 8px 12px;
  }
  
  .type-checkboxes {
    max-height: 120px;
    gap: 6px;
    padding: 10px;
  }
  
  .checkbox-label {
    font-size: 12px;
    padding: 5px 8px;
  }
}

/* Dark Mode */
@media (prefers-color-scheme: dark) {
  .ai-report-filter {
    background: linear-gradient(
      135deg,
      rgba(30, 30, 46, 0.6) 0%,
      rgba(30, 30, 46, 0.4) 100%
    );
    border-color: rgba(255, 255, 255, 0.1);
    box-shadow: 
      0 8px 32px rgba(0, 0, 0, 0.3),
      inset 0 1px 0 rgba(255, 255, 255, 0.05);
  }
  
  .filter-title {
  color: #e4e4e7;
  border-bottom-color: rgba(255, 255, 255, 0.1);
  text-shadow: none;
}

.date-label {
  color: #9ca3af;
}

.type-checkboxes {
    background: rgba(0, 0, 0, 0.2);
    border-color: rgba(255, 255, 255, 0.08);
  }
  
  .type-checkboxes.has-active {
    background: rgba(59, 130, 246, 0.15);
    border-color: rgba(59, 130, 246, 0.3);
  }
  
  .type-checkboxes::-webkit-scrollbar-track {
    background: rgba(0, 0, 0, 0.2);
  }
  
  .type-checkboxes::-webkit-scrollbar-thumb {
    background: rgba(255, 255, 255, 0.15);
  }
  
  .checkbox-label {
    color: #d1d5db;
    background: rgba(255, 255, 255, 0.05);
  }
  
  .checkbox-label:hover {
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(255, 255, 255, 0.15);
  }
  
  .checkbox-label.active {
    background: rgba(59, 130, 246, 0.2);
    border-color: rgba(59, 130, 246, 0.4);
    color: #93c5fd;
  }
  
  .checkbox-custom {
    background: rgba(255, 255, 255, 0.08);
    border-color: rgba(255, 255, 255, 0.15);
  }
  
  .stats-preview {
    background: linear-gradient(
      135deg,
      rgba(0, 0, 0, 0.25) 0%,
      rgba(0, 0, 0, 0.15) 100%
    );
    border-color: rgba(255, 255, 255, 0.08);
  }
  
  .stats-preview h5 {
    color: #e4e4e7;
  }
  
  .loading-text {
    color: #9ca3af;
  }
  
  .stat-card {
    background: rgba(255, 255, 255, 0.05);
    border-color: rgba(255, 255, 255, 0.08);
  }
  
  .stat-card:hover {
    background: rgba(255, 255, 255, 0.1);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  }
  
  .stat-label {
    color: #9ca3af;
  }
  
  .stat-value {
    color: #f3f4f6;
  }
  
  .stat-value.primary { color: #60a5fa; }
  .stat-value.success { color: #34d399; }
  .stat-value.warning { color: #fbbf24; }
  .stat-value.danger { color: #f87171; }
}
</style>