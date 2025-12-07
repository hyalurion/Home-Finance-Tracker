<template>
  <div class="container">
    <!-- 顶部加载和错误提示 -->
    <div v-if="isLoading" class="loading-alert">{{ t('app.loading') }}</div>
    <div v-if="error" class="error-alert">{{ error }}</div>
    <MessageTip v-model:message="successMessage" type="success" />
    <MessageTip v-model:message="errorMessage" type="error" />

    <!-- 页面标题 -->
    <Header :title="t('chart.title')" />

    <!-- 消费图表分析 -->
    <ExpenseCharts :expenses="Expenses" />

    <!-- 返回主页按钮 -->
    <div class="back-button-container">
      <GlassButton type="primary" @click="goBack" size="default">
       < {{ t('common.back') }}
      </GlassButton>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import GlassButton from '@/components/GlassButton.vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import axios from 'axios';

import { useExpenseData } from '@/composables/useExpenseData';
import { fetchAllPages, createCancellationController } from '@/utils/pagination';
import Header from '@/components/Header.vue';
import ExpenseCharts from '@/components/ExpenseCharts.vue';
import MessageTip from '@/components/MessageTip.vue';

const { t } = useI18n();
const router = useRouter();

// 状态数据
const Expenses = ref([]);
const isLoading = ref(false);

// 取消控制器
let paginationController = null;

// 费用数据管理
const { error, successMessage, errorMessage } = useExpenseData();

// 载入消费数据（从SQLite数据库）- 使用分页加载优化性能
const loadExpenses = async () => {
  if (isLoading.value) return;

  // 取消之前的请求
  if (paginationController) {
    paginationController.abort();
  }

  paginationController = createCancellationController();
  isLoading.value = true;

  try {
    console.log('ChartsView: 开始使用分页加载数据...');

    // 使用分页工具获取所有数据
    const allData = await fetchAllPages({
      apiCall: ({ page, limit }) => 
        axios.get(`/api/expenses?page=${page}&limit=${limit}`),
      pageSize: 100,           // 每页100条记录
      maxConcurrent: 2,        // 图表页面使用2个并发请求，避免影响其他操作
      signal: paginationController.signal,
      onProgress: (progressData) => {
        console.log(`ChartsView: 数据加载进度: ${progressData.progress}% (${progressData.loaded}/${progressData.total})`);
      },
      onError: (error) => {
        console.error('ChartsView: 分页加载错误:', error);
        throw error;
      }
    });

    // 确保数据格式正确
    Expenses.value = allData
      .map(item => ({
        type: item.type?.trim() || item.type,
        remark: item.remark?.trim() || item.remark,
        amount: Number(item.amount),
        date: item.date
      }))
      .filter(item => !isNaN(item.amount) && item.amount > 0);

    if (Expenses.value.length === 0) {
      console.warn('ChartsView: No valid data found in API response');
    } else {
      console.log('ChartsView: 分页加载完成, count:', Expenses.value.length);
    }
  } catch (err) {
    if (err.message !== '操作已被取消') {
      const errorInfo = err.response
        ? `${err.response.status} ${err.message}: ${JSON.stringify(err.response.data)}`
        : err.message;
      errorMessage.value = t('common.loadFailed', { error: errorInfo });
      error.value = errorMessage.value;

      console.error('ChartsView: Error Details:', err);
      Expenses.value = [];
    } else {
      console.log('ChartsView: 数据加载被用户取消');
    }
  } finally {
    isLoading.value = false;
  }
};

// 返回主页
const goBack = () => {
  router.push('/');
};

// 组件挂载时加载数据
onMounted(async () => {
  try {
    await loadExpenses();
  } catch (err) {
    console.error('Failed to initialize data:', err);
    error.value = t('error.dataInitializationFailed');
  }
});
</script>

<style scoped>
.container {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.loading-alert,
.error-alert {
  padding: 10px;
  margin-bottom: 15px;
  border-radius: 4px;
  text-align: center;
}

.loading-alert {
  background-color: #e3f2fd;
  color: #1976d2;
  border: 1px solid #90caf9;
}

.error-alert {
  background-color: #ffebee;
  color: #d32f2f;
  border: 1px solid #ffcdd2;
}

.back-button-container {
  margin-top: 30px;
  text-align: center;
}
</style>