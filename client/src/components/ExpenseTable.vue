<!-- ExpenseTable.vue -->
<template>
  <div class="expense-container">
    <!-- 大屏幕表格视图 -->
    <div class="table-view">
      <table class="expense-table">
        <thead>
          <tr>
            <th @click="$emit('sort', 'date')" class="sortable">
              {{ $t('expense.date') }}
              <span v-if="sortField && sortField === 'date'" class="sort-indicator">
                {{ sortOrder === 'asc' ? '↑' : '↓' }}
              </span>
            </th>
            <th @click="$emit('sort', 'type')" class="sortable">
              {{ $t('expense.type') }}
              <span v-if="sortField && sortField === 'type'" class="sort-indicator">
                {{ sortOrder === 'asc' ? '↑' : '↓' }}
              </span>
            </th>
            <th @click="$emit('sort', 'amount')" class="sortable">
              {{ $t('expense.amount') }}
              <span v-if="sortField && sortField === 'amount'" class="sort-indicator">
                {{ sortOrder === 'asc' ? '↑' : '↓' }}
              </span>
            </th>
            <th>{{ $t('expense.remark') }}</th>
            <th>{{ $t('common.action') }}</th>
          </tr>
        </thead>
        <transition-group name="row-fade" tag="tbody">
          <tr v-for="(expense, index) in expenses" :key="expense.id" :data-index="index">
            <td>{{ formatDate(expense.date) }}</td>
            <td>
              <span class="type-tag" :style="{ '--tag-color': getTypeColor(expense.type, isDarkMode) }">
                {{ expense.type }}
              </span>
            </td>
            <td class="amount-cell">¥{{ expense.amount.toFixed(2) }}</td>
            <td>{{ expense.remark || '-' }}</td>
            <td>
              <div class="action-buttons">
                <button class="edit-btn" @click="handleEdit(expense)">
                  {{ $t('common.edit') }}
                </button>
                <button class="delete-btn" @click="handleDelete(expense.id)">
                  {{ $t('common.delete') }}
                </button>
              </div>
            </td>
          </tr>
        </transition-group>
      </table>
    </div>

    <!-- 小屏幕卡片视图 -->
    <div class="card-view">
      <transition-group name="row-fade" tag="div">
        <div v-for="(expense, index) in expenses" :key="expense.id" class="expense-card" :data-index="index">
          <div class="card-header">
            <div class="date">{{ formatDate(expense.date) }}</div>
            <div class="amount">¥{{ expense.amount.toFixed(2) }}</div>
          </div>
          <div class="card-body">
            <div class="type-section">
              <span class="type-label">{{ $t('expense.type') }}:</span>
              <span class="type-tag" :style="{ '--tag-color': getTypeColor(expense.type, isDarkMode) }">
                  {{ expense.type }}
              </span>
            </div>
            <div v-if="expense.remark" class="remark-section">
              <span class="remark-label">{{ $t('expense.remark') }}:</span>
              <span class="remark-text">{{ expense.remark }}</span>
            </div>
            <div class="card-actions">
              <GlassButton type="primary" class="card-edit-btn" @click="handleEdit(expense)">
                {{ $t('common.edit') }}
              </GlassButton>
              <GlassButton type="danger" class="card-delete-btn" @click="handleDelete(expense.id)">
                {{ $t('common.delete') }}
              </GlassButton>
            </div>
          </div>
        </div>
      </transition-group>
    </div>

    <!-- 空数据状态 -->
    <div v-if="expenses.length === 0" class="no-data">
      <div class="no-data-icon"></div>
      <h3>{{ $t('expense.noDataTitle') }}</h3>
      <p>{{ $t('expense.noDataMessage') }}</p>
    </div>
  </div>
</template>

<script>
import { getTypeColor } from '../utils/expenseUtils';
import { ref, onMounted, onUnmounted, watch } from 'vue';

export default {
  props: {
    expenses: {
      type: Array,
      default: () => []
    },
    // 使sortField和sortOrder成为可选属性
    sortField: {
      type: String,
      default: ''
    },
    sortOrder: {
      type: String,
      default: 'asc'
    }
  },

  setup (props, { emit }) {
    const isDarkMode = ref(false);
    
    // 检测当前系统主题
    const checkDarkMode = () => {
      const newMode = window.matchMedia('(prefers-color-scheme: dark)').matches;
      if (newMode !== isDarkMode.value) {
        console.log('Dark mode changed:', { from: isDarkMode.value, to: newMode });
        isDarkMode.value = newMode;
      }
    };
    
    // 初始化检测
    checkDarkMode();
    
    // 监听主题变化
    onMounted(() => {
      console.log('ExpenseTable component mounted:', { initialDarkMode: isDarkMode.value });
      window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', checkDarkMode);
    });
    
    // 清理监听器
    onUnmounted(() => {
      window.matchMedia('(prefers-color-scheme: dark)').removeEventListener('change', checkDarkMode);
    });

    const formatDate = (dateString) => {
      // 直接返回YYYY-MM-DD格式的日期字符串，不再需要转换
      return dateString || '';
    };
    
    // 处理编辑事件
    const handleEdit = (expense) => {
      console.log('Edit expense clicked:', expense);
      emit('edit', expense);
    };

    // 处理删除事件
    const handleDelete = (id) => {
      console.log('Delete expense clicked:', { id });
      emit('delete', id);
    };

    // 监听数据变化
    watch(() => props.expenses, (newVal) => {
      console.log('Expense data updated:', { recordCount: newVal?.length || 0 });
    }, { deep: true });

    return {
      getTypeColor,
      formatDate,
      isDarkMode,
      handleEdit,
      handleDelete
    };
  }
};
</script>

<style scoped>
.expense-container {
  background: transparent;
  border-radius: 10px;
  overflow: hidden;
}

/* 表格视图样式 */
.table-view {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.05);
}

.expense-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.expense-table th {
  background-color: #4361ee;
  color: white;
  text-align: left;
  padding: 12px 15px;
  font-weight: 600;
}

.expense-table td {
  padding: 10px 15px;
  border-bottom: 1px solid #e9ecef;
}

.expense-table tr:hover {
  background-color: rgba(67, 97, 238, 0.03);
}

/* 卡片视图样式 */
.card-view {
  display: none;
}

.expense-card {
  background: white;
  border-radius: 10px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.expense-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.12);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.date {
  font-size: 14px;
  color: #666;
}

.amount {
  font-size: 18px;
  font-weight: 600;
  color: #4361ee;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.type-section {
  display: flex;
  align-items: center;
  gap: 8px;
}

.type-label,
.remark-label {
  font-size: 12px;
  color: #999;
  min-width: 50px;
}

.remark-section {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.remark-text {
    font-size: 14px;
    color: #333;
    flex: 1;
    word-break: break-word;
  }

  .card-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    margin-top: 10px;
  }

  .card-edit-btn,
  .card-delete-btn {
    padding: 4px 12px;
    font-size: 12px;
    font-weight: 500;
    transition: all 0.2s ease;
  }

  .card-edit-btn {
    background-color: #4361ee;
    color: white;
  }

  .card-edit-btn:hover {
    background-color: #3a56d4;
  }

  .card-delete-btn {
    background-color: #e63946;
    color: white;
  }

  .card-delete-btn:hover {
    background-color: #c1121f;
  }

/* 通用样式 */
.sortable {
  cursor: pointer;
  position: relative;
  user-select: none;
}

.sort-indicator {
  position: absolute;
  right: 5px;
}

.type-tag {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  color: rgb(0, 0, 0);
  background-color: var(--tag-color);
  border: none;
}

.amount-cell {
  font-weight: 600;
  color: #2b2d42;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.edit-btn,
.delete-btn {
  padding: 4px 10px;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: all 0.2s ease;
}

.edit-btn {
  background-color: #4361ee;
  color: white;
}

.edit-btn:hover {
  background-color: #3a56d4;
}

.delete-btn {
  background-color: #e63946;
  color: white;
}

.delete-btn:hover {
  background-color: #c1121f;
}

.no-data {
  text-align: center;
  padding: 40px 20px;
}

.no-data-icon {
  font-size: 48px;
  color: #e9ecef;
  margin-bottom: 15px;
}

.no-data h3 {
  font-size: 18px;
  margin-bottom: 10px;
  color: #6c757d;
}

.no-data p {
  color: #6c757d;
  max-width: 500px;
  margin: 0 auto;
}

/* 表格行动画效果 */
.row-fade-enter-active,
.row-fade-leave-active {
  transition: all 0.3s ease-out;
  position: relative;
}

.row-fade-leave-active {
  transition: all 0.2s ease-in;
}

.row-fade-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.row-fade-leave-to {
  opacity: 0;
  transform: translateY(-10px);
  position: absolute;
  width: 100%;
  left: 0;
}

/* 为每行添加不同的延迟，实现逐行动画 */
.row-fade-enter-active > [data-index="0"] { transition-delay: 0ms; }
.row-fade-enter-active > [data-index="1"] { transition-delay: 30ms; }
.row-fade-enter-active > [data-index="2"] { transition-delay: 60ms; }
.row-fade-enter-active > [data-index="3"] { transition-delay: 90ms; }
.row-fade-enter-active > [data-index="4"] { transition-delay: 120ms; }
.row-fade-enter-active > [data-index="5"] { transition-delay: 150ms; }
.row-fade-enter-active > [data-index="6"] { transition-delay: 180ms; }
.row-fade-enter-active > [data-index="7"] { transition-delay: 210ms; }
.row-fade-enter-active > [data-index="8"] { transition-delay: 240ms; }
.row-fade-enter-active > [data-index="9"] { transition-delay: 270ms; }

/* 响应式设计 - 小屏幕使用卡片视图 */
@media (max-width: 768px) {
  .table-view {
    display: none;
  }
  
  .card-view {
    display: block;
  }
}

/* 深色模式支持 */
@media (prefers-color-scheme: dark) {
  .table-view {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  }

  .expense-table th {
    background-color: #333;
    color: #e0e0e0;
  }

  .expense-table td {
    color: #e0e0e0;
    border-bottom: 1px solid #444;
  }

  .expense-table tr:hover {
    background-color: rgba(255, 255, 255, 0.05);
  }

  .amount-cell {
    color: #e0e0e0;
  }

  .type-tag {
    color: var(--tag-color);
    background-color: transparent;
    border: 1px solid white;
    box-shadow: none;
  }

  .no-data-icon {
    color: #333;
  }

  .no-data h3,
  .no-data p {
    color: #aaa;
  }

  /* 深色模式下的卡片样式 */
  .expense-card {
    background-color: #2a2a2a;
    border: 1px solid #444;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  }
  
  .card-header {
    border-bottom: 1px solid #444;
  }
  
  .date {
    color: #aaa;
  }
  
  .amount {
    color: #60a5fa;
  }
  
  .remark-text {
    color: #e0e0e0;
  }
  
  .type-label,
  .remark-label {
    color: #888;
  }
  
  .card-edit-btn,
  .card-delete-btn {
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  }
  
  .card-edit-btn:hover,
  .card-delete-btn:hover {
    opacity: 0.9;
  }
}
</style>
