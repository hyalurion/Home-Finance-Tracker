<!-- ExpenseList.vue -->
<template>
  <div class="expense-list">
      <!-- 搜索组件 -->
      <ExpenseSearch
        ref="searchComponent"
        :uniqueTypes="uniqueTypes"
        :availableMonths="availableMonths"
        :initialKeyword="searchParams.keyword"
        :initialType="searchParams.type"
        :initialMonth="searchParams.month"
        :initialMinAmount="searchParams.minAmount"
        :initialMaxAmount="searchParams.maxAmount"
        :initialSortOption="searchParams.sortOption"
        :locale="$i18n.locale"
        @search="handleSearch"
      />

      <!-- 空状态提示 -->
      <div v-if="filteredExpenses.length === 0" class="empty-state">
        <div class="empty-icon">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
            <path d="M19,13H5V11H19V13M12,5A2,2 0 0,1 14,7A2,2 0 0,1 12,9A2,2 0 0,1 10,7A2,2 0 0,1 12,5Z" />
          </svg>
        </div>
        <h3>{{ $t('expense.empty.title') }}</h3>
        <p>{{ $t('expense.empty.description') }}</p>
        <button @click="resetFilters" class="reset-button">
          {{ $t('expense.empty.reset') }}
        </button>
      </div>

      <template v-else>
        <!-- 统计组件 -->
        <ExpenseStats :statistics="statistics" />

        <!-- 表格组件 - 直接使用后端处理的数据 -->
        <ExpenseTable
          :expenses="paginatedExpenses"
          @sort="sortBy"
          @edit="handleEdit"
          @delete="handleDelete"
        />

        <!-- 分页组件 -->
        <ExpensePagination
          v-if="totalPages > 1"
          :currentPage="currentPage"
          :totalPages="totalPages"
          :visiblePages="visiblePages"
          @page-change="changePage"
        />
      </template>
  </div>
</template>

<script>
import { ref, computed, onMounted, watch, watchEffect } from 'vue';
import { useI18n } from 'vue-i18n';
import ExpenseStats from './ExpenseStats.vue';
import ExpenseSearch from './ExpenseSearch.vue';
import ExpenseTable from './ExpenseTable.vue';
import ExpensePagination from './ExpensePagination.vue';
import { getTypeColor } from '../utils/expenseUtils';
import { ExpenseAPI } from '../api/expenses';

export default {
  components: {
    ExpenseStats,
    ExpenseSearch,
    ExpenseTable,
    ExpensePagination
  },
  props: {
    // 用于触发数据刷新的信号
    refreshTrigger: {
      type: Number,
      default: 0
    }
  },

  emits: ['refreshCompleted', 'edit', 'delete'],
  
  setup (props, { emit }) {
    const { t, locale } = useI18n(); // 解构出locale响应式对象
    const searchComponent = ref(null);
    
    // 处理编辑事件
    const handleEdit = (expense) => {
      emit('edit', expense);
    };
    
    // 处理删除事件
    const handleDelete = (expense) => {
      emit('delete', expense);
    };

    // 统一搜索参数
    const searchParams = ref({
      keyword: '',
      type: '',
      month: '',
      minAmount: null,
      maxAmount: null,
      sortOption: 'dateDesc'
    });

    // 分页相关状态
    const currentPage = ref(1);
    const pageSize = ref(10);
    const totalItems = ref(0);
    const expenses = ref([]);
    
    // 添加缺失的变量定义
    const uniqueTypes = ref([]);
    // 初始化时提供默认的模拟月份数据，确保即使在API请求完成前，下拉菜单也有选项可显示
    const defaultMonths = [];
    const now = new Date();
    // 生成最近120个月的月份数据
    for (let i = 0; i < 120; i++) {
      const year = now.getFullYear();
      const month = now.getMonth() - i;
      const date = new Date(year, month);
      defaultMonths.push(`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`);
    }
    const availableMonths = ref(defaultMonths);

    // 监听语言变化，当语言切换时重新生成月份选项
    watch(locale, (newLocale) => {
      console.log('语言已切换，重新生成月份选项:', newLocale);
      // 重新获取数据以更新月份显示
      fetchPaginatedData();
    });
    
    // 移除前端筛选逻辑，直接使用后端返回的数据
    const filteredExpenses = computed(() => {
      return expenses.value || [];
    });

    // 获取分页数据
    const fetchPaginatedData = async () => {
      try {
        // 添加空值检查，确保searchParams.value存在
        if (!searchParams.value) {
          return;
        }
        
        console.log('Fetching paginated expenses:', {
          page: currentPage.value,
          pageSize: pageSize.value,
          searchParams: { ...searchParams.value }
        });
        
        // 添加排序参数到请求中
        const params = new URLSearchParams();
        params.append('page', currentPage.value);
        params.append('limit', pageSize.value);
        
        // 添加搜索参数
        if (searchParams.value.keyword) params.append('keyword', searchParams.value.keyword);
        if (searchParams.value.type) params.append('type', searchParams.value.type);
        if (searchParams.value.month) params.append('month', searchParams.value.month);
        // 验证金额参数是否为有效数字
        const minAmount = parseFloat(searchParams.value.minAmount);
        const maxAmount = parseFloat(searchParams.value.maxAmount);
        if (searchParams.value.minAmount !== null && !isNaN(minAmount)) {
          params.append('minAmount', minAmount.toString());
        }
        if (searchParams.value.maxAmount !== null && !isNaN(maxAmount)) {
          params.append('maxAmount', maxAmount.toString());
        }
        if (searchParams.value.sortOption) params.append('sort', searchParams.value.sortOption);
        
        const response = await ExpenseAPI.getExpenses(currentPage.value, pageSize.value, params);
        
        // 适配后端返回的分页格式
        if (response && response.data && response.data.data && Array.isArray(response.data.data)) {
          expenses.value = response.data.data;
          totalItems.value = response.data.total || 0;
          
          // 更新类型和月份数据
          if (response.data.meta) {
            uniqueTypes.value = response.data.meta.uniqueTypes || [];
            // 只有当API返回了有效的月份数据时，才替换默认数据
            if (response.data.meta.availableMonths && response.data.meta.availableMonths.length > 0) {
              // 对月份进行排序，从新到旧
              availableMonths.value = response.data.meta.availableMonths.sort((a, b) => b.localeCompare(a));
            }
          } else {
            // 从当前页数据中提取类型和月份信息
            const types = [...new Set(expenses.value.map(e => e.type))];
            const months = [...new Set(expenses.value.map(e => {
              const date = new Date(e.date || e.time);
              return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
            }))];
            
            uniqueTypes.value = types.filter(type => type && type.trim() !== '');
            // 只有当提取到有效月份时，才替换默认数据
            const filteredMonths = months.filter(month => month && month.trim() !== '');
            if (filteredMonths.length > 0) {
              // 对月份进行排序，从新到旧
              availableMonths.value = filteredMonths.sort((a, b) => b.localeCompare(a));
            }
          }
        } else if (Array.isArray(response)) {
          // 兼容旧格式
          expenses.value = response;
          totalItems.value = response.length;
        }
        console.log('Expenses data fetched successfully:', {
          recordCount: expenses.value.length,
          totalItems: totalItems.value,
          page: currentPage.value
        });
      } catch (error) {
        console.error('获取分页数据失败:', error);
        console.error('Data fetch error details:', { message: error.message, stack: error.stack });
      }
    };

    // 搜索处理
    const handleSearch = (params) => {
      console.log('Search requested with params:', { ...params });
      searchParams.value = { ...params };
      currentPage.value = 1;
      fetchPaginatedData();
      fetchStatistics(); // 更新统计数据
    };

    // 重置筛选条件
    const resetFilters = () => {
      console.log('Filters reset requested');
      if (searchComponent.value) {
        searchComponent.value.handleReset();
      }
      searchParams.value = {
        keyword: '',
        type: '',
        month: '',
        minAmount: null,
        maxAmount: null,
        sortOption: 'dateDesc'
      };
      currentPage.value = 1;
      fetchPaginatedData();
      fetchStatistics(); // 更新统计数据
      console.log('Filters reset completed');
    };

    // 分页处理 - 直接使用后端返回的分页数据
    const paginatedExpenses = computed(() => {
      return expenses.value || [];
    });

    // 总页数
    const totalPages = computed(() => {
      return Math.ceil(totalItems.value / pageSize.value) || 1;
    });

    // 引入 window 以方便测试模拟
    const { innerWidth } = window;
    // 可见页码（小屏幕最多显示3个，大屏幕最多显示5个）
    const visiblePages = computed(() => {
      const pages = [];
      const total = totalPages.value;
      const current = currentPage.value;
      // 根据屏幕宽度确定最多显示的页码数
      const maxVisible = innerWidth < 768 ? 1 : 5;

      if (total <= maxVisible) {
        for (let i = 1; i <= total; i++) pages.push(i);
      } else {
        const start = Math.max(1, current - Math.floor(maxVisible / 2));
        const end = Math.min(total, start + maxVisible - 1);

        if (start > 1) pages.push(1);
        if (start > 2) pages.push('...');

        for (let i = start; i <= end; i++) pages.push(i);

        if (end < total - 1) pages.push('...');
        if (end < total) pages.push(total);
      }

      return pages;
    });

    // 使用ref存储从后端获取的统计数据，而不是基于当前页数据计算
    const statistics = ref({
      count: 0,
      totalAmount: '0.00',
      averageAmount: '0.00',
      medianAmount: '0.00',
      minAmount: '0.00',
      maxAmount: '0.00',
      typeDistribution: {}
    });

    // 定义获取统计数据的函数
    const fetchStatistics = async () => {
      try {
        // 添加空值检查，确保searchParams.value存在
        if (!searchParams.value) {
          return;
        }
        
        console.log('Fetching statistics with filters:', { ...searchParams.value });
        
        // 构建与getExpenses相同的查询参数
        const statsSearchParams = new URLSearchParams();
        if (searchParams.value.keyword) statsSearchParams.set('keyword', searchParams.value.keyword);
        if (searchParams.value.type) statsSearchParams.set('type', searchParams.value.type);
        if (searchParams.value.month) statsSearchParams.set('month', searchParams.value.month);
        
        // 添加金额范围参数，确保是有效数字
        const validMinAmount = parseFloat(searchParams.value.minAmount);
        const validMaxAmount = parseFloat(searchParams.value.maxAmount);
        if (!isNaN(validMinAmount)) {
          statsSearchParams.set('minAmount', validMinAmount.toString());
        }
        if (!isNaN(validMaxAmount)) {
          statsSearchParams.set('maxAmount', validMaxAmount.toString());
        }

        // 调用后端统计API获取完整数据
        const statsData = await ExpenseAPI.getStatistics(statsSearchParams);
        
        // 格式化数字以保持一致性
        if (statsData && !statsData.error) {
          statistics.value = {
            count: statsData.count || 0,
            totalAmount: (statsData.totalAmount || 0).toFixed(2),
            averageAmount: (statsData.averageAmount || 0).toFixed(2),
            medianAmount: (statsData.medianAmount || 0).toFixed(2),
            minAmount: (statsData.minAmount || 0).toFixed(2),
            maxAmount: (statsData.maxAmount || 0).toFixed(2),
            typeDistribution: statsData.typeDistribution || {}
          };
        }
          
          console.log('Statistics fetched successfully:', {
            recordCount: statistics.value.count,
            totalAmount: statistics.value.totalAmount,
            typeCategories: Object.keys(statistics.value.typeDistribution).length
          });
        
      } catch (error) {
        console.error('获取统计数据失败:', error);
        console.error('Statistics fetch error details:', { message: error.message, stack: error.stack });
        // 出错时保持原有的统计数据
      }
    };

    // 当筛选条件变化时重新获取统计数据
    watchEffect(() => {
      // 添加空值检查，确保searchParams.value存在
      if (!searchParams.value) {
        return;
      }
      
      // 延迟执行，避免频繁请求
      const timer = setTimeout(() => {
        fetchStatistics();
      }, 300);
      
      return () => clearTimeout(timer);
    });

    // 初始加载时获取统计数据
    fetchStatistics();

    // 页面切换方法
    const changePage = (page) => {
      console.log('Page change requested:', { from: currentPage.value, to: page, totalPages: totalPages.value });
      if (page >= 1 && page <= totalPages.value) {
        currentPage.value = page;
        fetchPaginatedData();
      }
    };

    // 排序方法
    const sortBy = (field) => {
      const currentSort = searchParams.value.sortOption;
      let newSort = '';

      if (field === 'date') {
        newSort = currentSort === 'dateAsc' ? 'dateDesc' : 'dateAsc';
      } else if (field === 'amount') {
        newSort = currentSort === 'amountAsc' ? 'amountDesc' : 'amountAsc';
      }

      if (newSort) {
        console.log('Sort requested:', { field, from: currentSort, to: newSort });
        searchParams.value.sortOption = newSort;
        currentPage.value = 1;
        fetchPaginatedData();
      }
    };

    // 监听pageSize变化
    watch(pageSize, () => {
      currentPage.value = 1;
      fetchPaginatedData();
    });

    // 当外部触发刷新时重新获取数据
    watch(
      () => props.refreshTrigger,
      (newValue, oldValue) => {
        if (newValue !== oldValue) {
          console.log('Refresh triggered from parent, reloading data');
          // 重置到第一页以显示最新添加的记录
          currentPage.value = 1;
          fetchPaginatedData();
          fetchStatistics();
          // 发出事件通知父组件刷新已完成
          emit('refreshCompleted');
        }
      }
    );

    // 提供手动刷新方法供父组件调用
    const refreshData = () => {
      console.log('Manual data refresh requested');
      currentPage.value = 1;
      fetchPaginatedData();
      fetchStatistics();
    };

    // 初始化时加载数据
    onMounted(() => {
      console.log('ExpenseList component mounted, initializing data fetch');
      fetchPaginatedData();
    });

    return {
      searchComponent,
      searchParams,
      currentPage,
      pageSize,
      uniqueTypes,
      availableMonths,
      filteredExpenses,
      paginatedExpenses,
      totalPages,
      visiblePages,
      statistics,
      getTypeColor,
      handleSearch,
      resetFilters,
      changePage,
      handleEdit,
      handleDelete,
      sortBy,
      refreshData
    };
  }
};
</script>

<style scoped>
.expense-list {
  display: flex;
  flex-direction: column;
  gap: 25px;
  padding: 0 10px;
}

.loader {
  width: 50px;
  height: 50px;
  border: 5px solid #f3f3f3;
  border-top: 5px solid #4361ee;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 40px 20px;
  background: transparent;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
  margin-top: 20px;
}

.empty-icon {
  width: 80px;
  height: 80px;
  background: #f0f7ff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.empty-icon svg {
  width: 40px;
  height: 40px;
  fill: #4361ee;
}

.empty-state h3 {
  margin: 0 0 10px;
  font-size: 1.4rem;
  color: #2c3e50;
}

.empty-state p {
  margin: 0 0 20px;
  color: #6c757d;
  max-width: 500px;
  line-height: 1.6;
}

.reset-button {
  padding: 10px 25px;
  background: linear-gradient(135deg, #4361ee, #3a56d4);
  color: white;
  border: none;
  border-radius: 8px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.reset-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(67, 97, 238, 0.3);
}

@media (max-width: 768px) {
  .expense-list {
    gap: 15px;
  }

  .empty-state {
    padding: 30px 15px;
  }
}
</style>
