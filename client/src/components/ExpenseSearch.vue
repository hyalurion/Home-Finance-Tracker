<!-- ExpenseSearch.vue -->
<template>
    <div class="search-container">
      <div class="search-header">
        <h2>{{ $t('expense.search.title') }}</h2>
        <div class="search-actions">
          <GlassButton @click="handleReset" class="reset-button">
            <i class="icon-reset"></i>
            {{ $t('expense.search.reset') }}
          </GlassButton>
        </div>
      </div>

      <div class="search-grid">
        <!-- 月份选择 -->
        <div class="search-control">
          <label class="control-label">
            <i class="icon-calendar"></i>
            {{ $t('expense.search.month') }}
          </label>
          <div class="control-input">
            <!-- 使用CustomSelect组件 -->
            <CustomSelect
              v-model="month"
              :options="monthOptions"
              :empty-option-label="$t('expense.search.allMonth')"
              :value-formatter="(value) => value ? formatMonthLabelByLocale(value, props.locale) : ''"
            />
          </div>
        </div>

        <!-- 类型选择 -->
        <div class="search-control">
          <label class="control-label">
            <i class="icon-category"></i>
            {{ $t('expense.search.type') }}
          </label>
          <div class="control-input">
            <!-- 使用CustomSelect组件 -->
            <CustomSelect
              v-model="type"
              :options="uniqueTypes"
              :empty-option-label="$t('expense.search.allType')"
            />
          </div>
        </div>

        <!-- 排序方式 -->
        <div class="search-control">
          <label class="control-label">
            <i class="icon-sort"></i>
            {{ $t('expense.search.sort') }}
          </label>
          <div class="control-input">
            <!-- 使用CustomSelect组件 -->
            <CustomSelect
              v-model="sortOption"
              :options="sortOptions"
              :include-empty-option="false"
              :value-formatter="(value) => {
                if (!sortOptions || sortOptions.length === 0 || !value) return '';
                const option = sortOptions.find(opt => opt.value === value);
                return option ? option.label : '';
              }"
            />
          </div>
        </div>

        <!-- 金额范围 -->
        <div class="search-control">
          <label class="control-label" for="minAmount">
            <i class="icon-amount"></i>
            {{ $t('expense.search.amountRange') }}
          </label>
          <div class="amount-range">
            <div class="range-input">
              <input type="number" v-model.number="minAmount" id="minAmount" name="minAmount" min="0" step="0.01"
                     :placeholder="$t('expense.search.minAmountPlaceholder')">
              <span class="range-divider">-</span>
              <input type="number" v-model.number="maxAmount" id="maxAmount" name="maxAmount" min="0" step="0.01"
                     :placeholder="$t('expense.search.maxAmountPlaceholder')">
            </div>
            <div class="range-slider">
              <input type="range" min="0" :max="maxSliderValue" step="10"
                     v-model.number="minAmount" class="slider min-slider">
              <input type="range" min="0" :max="maxSliderValue" step="10"
                     v-model.number="maxAmount" class="slider max-slider">
            </div>
          </div>
        </div>

        <!-- 关键词搜索 -->
        <div class="search-control">
          <label class="control-label" for="keyword">
            <i class="icon-keyword"></i>
            {{ $t('expense.search.keyword') }}
          </label>
          <div class="control-input">
            <input type="text" v-model.trim="keyword" id="keyword" name="keyword"
                   :placeholder="$t('expense.search.keywordPlaceholder')">
          </div>
        </div>
      </div>

      <div v-if="activeFiltersCount > 0" class="active-filters">
        <div class="filter-badge" v-if="month">
          {{ $t('expense.search.month') }}: {{ monthDisplay }}
          <span @click="clearFilter('month')" class="clear-filter">×</span>
        </div>
        <div class="filter-badge" v-if="type">
          {{ $t('expense.search.type') }}: {{ type }}
          <span @click="clearFilter('type')" class="clear-filter">×</span>
        </div>
        <div class="filter-badge" v-if="minAmount || maxAmount">
          {{ $t('expense.search.amountRange') }}:
          {{ minAmount ? minAmount : '0' }} - {{ maxAmount ? maxAmount : '∞' }}
          <span @click="clearAmountFilter" class="clear-filter">×</span>
        </div>
        <div class="filter-badge" v-if="keyword">
          {{ $t('expense.search.keyword') }}: "{{ keyword }}"
          <span @click="clearFilter('keyword')" class="clear-filter">×</span>
        </div>
      </div>
    </div>
  </template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { formatMonthLabelByLocale } from '@/utils/dateFormatter';
import CustomSelect from './CustomSelect.vue';

const props = defineProps({
  uniqueTypes: Array,
  initialKeyword: String,
  initialType: String,
  initialMonth: String,
  initialMinAmount: [String, Number],
  initialMaxAmount: [String, Number],
  initialSortOption: String,
  locale: { type: String, default: 'en-US' },
  maxAmountRange: { type: Number, default: 5000 }, // 默认最大金额范围
  availableMonths: { type: Array, default: () => [] }
});

const emit = defineEmits(['search']);

// 响应式搜索条件
const keyword = ref(props.initialKeyword || '');
const type = ref(props.initialType || '');
const month = ref(props.initialMonth || '');
const minAmount = ref(props.initialMinAmount || '');
const maxAmount = ref(props.initialMaxAmount || '');
const sortOption = ref(props.initialSortOption || 'dateDesc');
const monthOptions = ref([]);
const { t, locale } = useI18n();

// 排序选项数据
const sortOptions = computed(() => [
  { value: 'dateDesc', label: t('expense.sort.dateDesc') },
  { value: 'dateAsc', label: t('expense.sort.dateAsc') },
  { value: 'amountDesc', label: t('expense.sort.amountDesc') },
  { value: 'amountAsc', label: t('expense.sort.amountAsc') }
]);

// 计算属性
const monthDisplay = computed(() => {
  if (!month.value) return '';
  return formatMonthLabelByLocale(month.value, props.locale);
});

const activeFiltersCount = computed(() => {
  let count = 0;
  if (month.value) count++;
  if (type.value) count++;
  if (minAmount.value || maxAmount.value) count++;
  if (keyword.value) count++;
  return count;
});

const maxSliderValue = computed(() => {
  return props.maxAmountRange || 5000;
});

// 生成搜索参数对象的计算属性
const searchParams = computed(() => {
  // 验证并转换数值类型
  const min = minAmount.value !== '' ? Number(minAmount.value) : undefined;
  const max = maxAmount.value !== '' ? Number(maxAmount.value) : undefined;

  // 确保数值有效性
  const validMin = !isNaN(min) ? min : undefined;
  const validMax = !isNaN(max) ? max : undefined;

  return {
    keyword: keyword.value,
    type: type.value,
    month: month.value,
    minAmount: validMin,
    maxAmount: validMax,
    sort: sortOption.value
  };
});

// 根据表格数据生成月份选项，无数据时显示最近12个月
const generateMonthOptions = () => {
  let options = [];

  // 检查是否有可用的月份数据，并且数据数组不为空
  if (props.availableMonths?.length) {
    // 使用提供的月份数据，将每个月份转换为包含值和显示标签的对象
    // 直接使用props.locale而不是再次调用useI18n()
    options = props.availableMonths.map(month => {
      // 标准化语言环境以支持更多格式
      const localeMap = {
        en: 'en-US'
      };
      const normalizedLocale = localeMap[props.locale] || props.locale || 'en-US';
      return {
        value: month,
        label: formatMonthLabelByLocale(month, normalizedLocale)
      };
    });
  }

  monthOptions.value = options;
};

// 搜索处理
const handleSearch = () => {
  console.log('Search initiated with params:', searchParams.value);
  
  // 验证金额范围逻辑
  const { minAmount, maxAmount } = searchParams.value;
  if (minAmount !== undefined && maxAmount !== undefined && minAmount > maxAmount) {
    console.warn('Amount range invalid - min > max, swapping values', { minAmount, maxAmount });
    // 如果最小值大于最大值，交换它们
    minAmount.value = maxAmount;
    maxAmount.value = minAmount;
    return;
  }

  // 由于我们已经在searchParams计算属性中处理了数据验证，
  // 这里可以直接使用该属性的值
  const searchData = {
    ...searchParams.value,
    sortOption: sortOption.value // 保留向后兼容性
  };
  
  console.log('Emitting search event:', searchData);
  emit('search', searchData);
};

// 重置搜索条件
const handleReset = () => {
  console.log('Resetting all search filters');
  keyword.value = '';
  type.value = '';
  month.value = '';
  minAmount.value = '';
  maxAmount.value = '';
  sortOption.value = 'dateDesc';
  console.log('Filters reset completed, triggering search');
  handleSearch();
};

// 清除单个过滤器
const clearFilter = (filterName) => {
  console.log('Clearing filter:', filterName);
  switch (filterName) {
  case 'month':
    month.value = '';
    break;
  case 'type':
    type.value = '';
    break;
  case 'keyword':
    keyword.value = '';
    break;
  }
  handleSearch();
};

// 清除金额过滤器
const clearAmountFilter = () => {
  console.log('Clearing amount range filters');
  minAmount.value = '';
  maxAmount.value = '';
  handleSearch();
};

// 初始化月份选项
onMounted(() => {
  console.log('ExpenseSearch component mounted with initial props:', {
    locale: props.locale,
    uniqueTypesCount: props.uniqueTypes?.length || 0,
    availableMonthsCount: props.availableMonths?.length || 0
  });
  generateMonthOptions();
});

// 监听availableMonths变化，当数据从父组件更新时重新生成月份选项
watch(() => props.availableMonths, () => {
  generateMonthOptions();
}, { deep: true });

// 监听语言变化，当语言切换时重新生成月份选项
watch(locale, (newLocale) => {
  console.log('ExpenseSearch: 语言已切换，重新生成月份选项:', newLocale);
  generateMonthOptions();
});

// 监听props中的locale变化，确保能响应外部传入的语言变化
watch(() => props.locale, (newLocale) => {
  console.log('ExpenseSearch: props locale已更新，重新生成月份选项:', newLocale);
  generateMonthOptions();
});

// 监听所有筛选条件变化
watch([keyword, type, month, minAmount, maxAmount, sortOption], (newValues) => {
  console.log('Filter condition changed:', {
    keyword: newValues[0],
    type: newValues[1],
    month: newValues[2],
    minAmount: newValues[3],
    maxAmount: newValues[4],
    sortOption: newValues[5]
  });
  handleSearch();
}, { deep: true });

// 暴露方法给父组件
defineExpose({
  handleReset
});
</script>

  <style scoped>
  .search-container {
    background: transparent;
    border-radius: 12px;
    padding: 20px;
    box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
    margin-bottom: 25px;
    overflow: visible;
}

  .search-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
    padding-bottom: 15px;
    border-bottom: 1px solid #eee;
  }

  .search-header h2 {
    margin: 0;
    font-size: 1.4rem;
    font-weight: 600;
    color: #2c3e50;
  }

  .search-actions {
    display: flex;
    gap: 12px;
  }

  .search-button, .reset-button {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 10px 18px;
    border-radius: 8px;
    font-weight: 500;
    font-size: 14px;
    cursor: pointer;
    transition: all 0.25s ease;
    border: none;
  }

  .search-button {
    background: linear-gradient(135deg, #4361ee, #3a56d4);
    color: white;
  }

  .search-button:hover {
    background: linear-gradient(135deg, #3a56d4, #314bc0);
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(67, 97, 238, 0.3);
  }

  .reset-button {
    background: #f5f7fa;
    color: #606266;
    border: 1px solid #dcdfe6;
  }

  .reset-button:hover {
    background: #eef2f7;
    color: #4361ee;
    border-color: #cbd5e0;
  }

  .search-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 20px;
  }

  .search-control {
    display: flex;
    flex-direction: column;
  }

  .control-label {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 500;
    margin-bottom: 8px;
    color: #495057;
    font-size: 14px;
  }

  .control-label i {
    font-size: 16px;
    color: #4361ee;
  }

  .control-input {
    position: relative;
  }

  .control-input input {
    width: auto;
    max-width: 100%;
    padding: 12px 15px;
    border: 1px solid #e2e8f0;
    border-radius: 8px;
    font-size: 14px;
    background: #f8fafc;
    transition: all 0.2s ease;
    box-sizing: border-box;
}

  /* CustomSelect组件样式覆盖 */
  :deep(.custom-select) {
    max-width: 60%;
  }
  
  @media (max-width: 768px) {
    :deep(.custom-select) {
      max-width: 100%;
    }
  }

  .control-input input:focus {
    border-color: #4361ee;
    outline: none;
    box-shadow: 0 0 0 3px rgba(67, 97, 238, 0.15);
    background: #fff;
  }

  .amount-range {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .range-input {
    display: flex;
    align-items: center;
    max-width: 80px;
    gap: 10px;
  }

  .range-input input {
    flex: 1;
    padding: 10px 12px;
    max-width: 100%;
    border: 1px solid #e2e8f0;
    border-radius: 6px;
    text-align: center;
  }

  .range-divider {
    color: #94a3b8;
    font-weight: 500;
    max-width: 100%;
  }

  .range-slider {
    display: none
  }

  .slider {
    flex: 1;
    height: 6px;
    border-radius: 3px;
    background: #e2e8f0;
    outline: none;
    -webkit-appearance: none;
    appearance: none;
    max-width: 100%;
  }

  .slider::-webkit-slider-thumb {
    -webkit-appearance: none;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: #4361ee;
    cursor: pointer;
    transition: all 0.2s;
  }

  .slider::-webkit-slider-thumb:hover {
    transform: scale(1.2);
    box-shadow: 0 0 0 3px rgba(67, 97, 238, 0.2);
  }

  .min-slider::-webkit-slider-thumb {
    background: #4cc9f0;
  }

  .max-slider::-webkit-slider-thumb {
    background: #f72585;
  }

  .active-filters {
    display: flex;
    flex-wrap: wrap;
    gap: 12px;
    margin-top: 20px;
    padding-top: 20px;
    border-top: 1px solid #eee;
  }

  .filter-badge {
    display: flex;
    align-items: center;
    padding: 8px 15px;
    background: #f0f7ff;
    border: 1px solid #c2e0ff;
    border-radius: 20px;
    font-size: 13px;
    color: #4361ee;
  }

  .clear-filter {
    margin-left: 8px;
    cursor: pointer;
    font-size: 16px;
    font-weight: bold;
    color: #94a3b8;
    transition: all 0.2s;
  }

  .clear-filter:hover {
    color: #f72585;
    transform: scale(1.2);
  }

  @media (max-width: 768px) {
    .search-container {
      padding: 16px;
    }
    
    .search-grid {
      grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
      gap: 16px;
    }

    .search-header {
      flex-direction: column;
      align-items: flex-start;
      gap: 15px;
      margin-bottom: 16px;
      padding-bottom: 12px;
    }

    .search-header h2 {
      font-size: 1.2rem;
    }

    .search-actions {
      width: 100%;
    }

    .search-button, .reset-button {
      flex: 1;
      justify-content: center;
      padding: 9px 16px;
    }
    
    .control-input {
      position: relative;
      max-width: 100%;
    }
    
    .control-input input {
      width: 100%;
      max-width: 100%;
    }
    
    .custom-select {
      max-width: 100%;
    }
    
    .amount-range {
      gap: 8px;
    }
    
    .range-input {
      display: flex;
      align-items: center;
      max-width: 100%;
      gap: 8px;
      width: 100%;
    }
    
    .range-input input {
      flex: 1;
      width: 20px;
    }
    
    .range-slider {
      display: none;
    }
    
    .active-filters {
      gap: 8px;
      margin-top: 16px;
      padding-top: 16px;
    }
    
    .filter-badge {
      padding: 6px 12px;
      font-size: 12px;
    }
  }
  
  /* 手机端适配 */
  @media (max-width: 480px) {
    .search-container {
      padding: 12px;
      margin-bottom: 15px;
    }
    
    .search-header {
      gap: 12px;
    }
    
    .search-header h2 {
      font-size: 1.1rem;
    }
    
    .search-grid {
      gap: 12px;
    }
    
    .control-label {
      font-size: 13px;
      margin-bottom: 6px;
    }
    
    .control-input input {
      padding: 10px 12px;
      font-size: 13px;
    }
    
    .select-trigger {
      padding: 10px 12px;
      font-size: 13px;
    }
    
    .search-button, .reset-button {
      padding: 8px 14px;
      font-size: 13px;
    }
    
    .range-input {
      gap: 6px;
    }
    
    .range-input input {
      padding: 8px 10px;
      font-size: 12px;
    }
    
  }

  /* 深色模式适配 */
  @media (prefers-color-scheme: dark) {
    .search-container {
      background: rgba(30, 30, 30, 0.7);
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
      border: 1px solid rgba(75, 85, 99, 0.5);
    }

    .search-header {
      border-bottom-color: rgba(75, 85, 99, 0.3);
    }

    .search-header h2 {
      color: #f9fafb;
    }

    .reset-button {
      background: #1f2937;
      color: #d1d5db;
      border-color: #374151;
    }

    .reset-button:hover {
      background: #374151;
      color: #a5b4fc;
      border-color: #4b5563;
    }

    .control-label {
      color: #e5e7eb;
    }

    .control-label i {
      color: #a5b4fc;
    }

    .control-input input {
      background: #1f2937;
      border-color: #374151;
      color: #e5e7eb;
    }

    .control-input input:focus {
      border-color: #4361ee;
      background: #1f2937;
      box-shadow: 0 0 0 3px rgba(67, 97, 238, 0.2);
    }
    
    /* 深色模式下的自定义下拉菜单样式 */
    .select-trigger {
      background: #1f2937;
      border-color: #374151;
      color: #e5e7eb;
    }
    
    .select-trigger:hover {
      background: #374151;
      border-color: #4b5563;
    }
    
    .select-icon::before {
      border-right-color: #9ca3af;
      border-bottom-color: #9ca3af;
    }
    
    .select-dropdown {
      background: #1f2937;
      border-color: #374151;
      box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
    }

    /* 自定义滚动条样式 - 深色模式 */
    .select-dropdown::-webkit-scrollbar {
      width: 6px;
    }

    .select-dropdown::-webkit-scrollbar-track {
      background: #374151;
      border-radius: 3px;
      margin: 4px 0;
    }

    .select-dropdown::-webkit-scrollbar-thumb {
      background: #4b5563;
      border-radius: 3px;
      transition: background 0.2s ease;
    }

    .select-dropdown::-webkit-scrollbar-thumb:hover {
      background: #6b7280;
    }
    
    .select-option {
      color: #e5e7eb;
    }
    
    .select-option:hover {
      background: #374151;
      color: #a5b4fc;
    }
    
    .select-option.selected {
      background: #4361ee;
      color: #fff;
    }

    .range-divider {
      color: #9ca3af;
    }

    .slider {
      background: #374151;
    }

    .slider::-webkit-slider-thumb {
      background: #4361ee;
    }

    .min-slider::-webkit-slider-thumb {
      background: #3b82f6;
    }

    .max-slider::-webkit-slider-thumb {
      background: #ec4899;
    }

    .active-filters {
      border-top-color: rgba(75, 85, 99, 0.3);
    }

    .filter-badge {
      background: rgba(30, 64, 175, 0.2);
      border-color: rgba(96, 165, 250, 0.3);
      color: #a5b4fc;
    }

    .clear-filter {
      color: #9ca3af;
    }

    .clear-filter:hover {
      color: #f472b6;
    }
      .range-input input {
        background: #1f2937;
        border-color: #374151;
        color: #e5e7eb;
      }

      .range-input input:focus {
        border-color: #4361ee;
        background: #1f2937;
        box-shadow: 0 0 0 3px rgba(67, 97, 238, 0.2);
      }
  }
  </style>
