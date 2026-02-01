<!-- ExpenseTable.vue -->
<template>
  <div class="expense-container">
    <!-- 渐变定义 SVG -->
    <svg class="gradient-defs" width="0" height="0">
      <defs>
        <linearGradient id="gradient-arrow" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stop-color="#ff7eb3" stop-opacity="1" />
          <stop offset="100%" stop-color="#ff758c" stop-opacity="1" />
        </linearGradient>
      </defs>
    </svg>
    <!-- 大屏幕表格视图 -->
    <div class="table-view">
      <table class="expense-table">
        <thead>
          <tr>
            <th @click="$emit('sort', 'date')" class="sortable">
              {{ $t('expense.date') }}
              <span v-if="sortField && sortField === 'date'" class="sort-indicator">
                <FontAwesomeIcon :icon="sortOrder === 'asc' ? 'arrow-up' : 'arrow-down'" />
              </span>
            </th>
            <th>
              {{ $t('expense.type') }}
            </th>
            <th @click="$emit('sort', 'amount')" class="sortable">
              {{ $t('expense.amount') }}
              <span v-if="sortField && sortField === 'amount'" class="sort-indicator">
                <FontAwesomeIcon :icon="sortOrder === 'asc' ? 'arrow-up' : 'arrow-down'" />
              </span>
            </th>
            <th>{{ $t('expense.remark') }}</th>
            <th>{{ $t('common.action') }}</th>
          </tr>
        </thead>
        <transition-group name="row-fade" tag="tbody">
          <template v-for="(expenses, date) in groupedExpenses" :key="date">
            <!-- 日期标题行 -->
            <tr class="date-header-row">
              <td colspan="5">
                <div class="date-header">
                  <div class="date-info">
                    <span class="date-text">{{ date }}</span>
                    <span class="count-text">{{ $t('expense.stats.rowCount') }}: {{ expenses.length }}</span>
                  </div>
                  <div class="total-amount">-¥{{ calculateDailyTotal(expenses).toFixed(2) }}</div>
                </div>
              </td>
            </tr>
            <!-- 该日期下的支出项 -->
            <tr v-for="(expense, index) in expenses" :key="expense.id" :data-index="index">
              <td>{{ formatDate(expense.date) }}</td>
              <td>
                <span class="type-tag" :style="{ '--tag-color': getTypeColor(expense.type) }">
                  {{ expense.type }}
                </span>
              </td>
              <td class="amount-cell">¥{{ expense.amount.toFixed(2) }}</td>
              <td class="remark-cell">{{ expense.remark || '-' }}</td>
              <td>
                <div class="action-buttons">
                  <button class="edit-btn" @click="handleEdit(expense)">
                    <FontAwesomeIcon icon="edit" /> 
                  </button>
                  <button class="delete-btn" @click="handleDelete(expense.id)">
                    <FontAwesomeIcon icon="trash-alt" /> 
                  </button>
                </div>
              </td>
            </tr>
          </template>
        </transition-group>
      </table>
    </div>

    <!-- 小屏幕卡片视图 -->
    <div class="card-view">
      <transition-group name="row-fade" tag="div">
        <template v-for="(expenses, date) in groupedExpenses" :key="date">
          <!-- 日期标题卡片 -->
          <div class="date-header-card">
            <div class="date-info">
              <span class="date-text">{{ date }}</span>
              <span class="count-text">{{ $t('expense.stats.rowCount') }}: {{ expenses.length }}</span>
            </div>
            <div class="total-amount">-¥{{ calculateDailyTotal(expenses).toFixed(2) }}</div>
          </div>
          <!-- 该日期下的支出卡片 -->
          <div 
            v-for="(expense, index) in expenses" 
            :key="expense.id" 
            class="expense-card" 
            :data-index="index"
            @touchstart="startLongPress(expense, $event)"
            @touchend="endLongPress"
            @touchcancel="endLongPress"
            @mousedown="startLongPress(expense, $event)"
            @mouseup="endLongPress"
            @mouseleave="endLongPress"
          >
            <div class="card-header">
              <div class="date">{{ formatDate(expense.date) }}</div>
              <div class="amount">¥{{ expense.amount.toFixed(2) }}</div>
            </div>
            <div class="card-body">
              <div class="type-section">
                <span class="type-label">{{ $t('expense.type') }}:</span>
                <span class="type-tag" :style="{ '--tag-color': getTypeColor(expense.type) }">
                    {{ expense.type }}
                </span>
              </div>
              <div v-if="expense.remark" class="remark-section">
                <span class="remark-label">{{ $t('expense.remark') }}:</span>
                <span class="remark-text">{{ expense.remark }}</span>
              </div>
            </div>
          </div>
        
        <!-- 长按菜单 -->
        <transition 
          name="menu-fade"
          mode="out-in"
        >
          <div 
            v-if="showMenu && currentMenuExpense" 
            key="menu"
            class="long-press-menu"
            :style="menuStyle"
          >
            <div class="menu-content">
              <GlassButton 
                type="primary" 
                class="menu-btn menu-edit-btn" 
                @click.stop="() => { handleEdit(currentMenuExpense); closeMenu() }"
              >
                <template #icon>
                  <FontAwesomeIcon icon="edit" />
                </template>
                {{ $t('common.edit') }}
              </GlassButton>
              <GlassButton 
                type="danger" 
                class="menu-btn menu-delete-btn" 
                @click.stop="() => { handleDelete(currentMenuExpense.id); closeMenu() }"
              >
                <template #icon>
                  <FontAwesomeIcon icon="trash-alt" />
                </template>
                {{ $t('common.delete') }}
              </GlassButton>
            </div>
          </div>
        </transition>
        </template>
      </transition-group>
    </div>

    <!-- 空数据状态 -->
    <div v-if="Object.keys(groupedExpenses).length === 0" class="no-data">
      <div class="no-data-icon"></div>
      <h3>{{ $t('expense.noDataTitle') }}</h3>
      <p>{{ $t('expense.noDataMessage') }}</p>
    </div>
  </div>
</template>

<script>
import { getTypeColor } from '../utils/expenseUtils';
import { ref, computed, onMounted, onUnmounted, watch, toRefs } from 'vue';
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome';

export default {
  components: {
    FontAwesomeIcon
  },
  props: {
    groupedExpenses: {
      type: Object,
      default: () => ({})
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
    // 使用 toRefs 保持 props 的响应性
    const { sortField, sortOrder } = toRefs(props);
    
    const formatDate = (dateString) => {
      // 直接返回YYYY-MM-DD格式的日期字符串，不再需要转换
      return dateString || '';
    };

    // 计算每日总金额
    const calculateDailyTotal = (expenses) => {
      return expenses.reduce((total, expense) => total + parseFloat(expense.amount || 0), 0);
    };

    // 长按相关状态
    const showMenu = ref(false);
    const menuExpenseId = ref('');
    const currentMenuExpense = ref(null);
    const menuPosition = ref({ x: 0, y: 0 });
    const longPressTimer = ref(null);
    const LONG_PRESS_DURATION = 500; // 长按触发时间

    // 菜单样式计算
    const menuStyle = computed(() => {
      return {
        top: `${menuPosition.value.y}px`,
        right: `${menuPosition.value.x}px`
      };
    });

    // 开始长按
    const startLongPress = (expense, event) => {
      // 清除之前的定时器
      if (longPressTimer.value) {
        clearTimeout(longPressTimer.value);
      }
      
      // 保存target引用，避免在异步回调中丢失
      const target = event.currentTarget;
      
      // 设置新的定时器
      longPressTimer.value = setTimeout(() => {
        // 计算菜单位置
        // 添加空值检查，确保target存在
        if (!target) {
          console.warn('Long press target is null or undefined');
          return;
        }
        
        const rect = target.getBoundingClientRect();
        // 获取触摸或鼠标事件的坐标
        const clientX = event.touches ? event.touches[0].clientX : event.clientX;
        const clientY = event.touches ? event.touches[0].clientY : event.clientY;
        
        // 计算菜单位置，从长按位置附近弹出
        menuPosition.value = {
          x: window.innerWidth - clientX - 110, // 调整菜单宽度
          y: clientY + 10
        };
        
        // 先设置菜单数据，再显示菜单
        menuExpenseId.value = expense.id;
        currentMenuExpense.value = expense;
        
        // 确保DOM更新后再显示菜单，触发动画
        setTimeout(() => {
          showMenu.value = true;
          
          console.log('Long press detected, showing menu for expense:', expense.id);
          console.log('Menu state:', { showMenu: showMenu.value, menuExpenseId: menuExpenseId.value, menuPosition: menuPosition.value, currentMenuExpense: currentMenuExpense.value });
        }, 10);
      }, LONG_PRESS_DURATION);
    };

    // 结束长按
    const endLongPress = () => {
      if (longPressTimer.value) {
        clearTimeout(longPressTimer.value);
        longPressTimer.value = null;
      }
    };

    // 关闭菜单
    const closeMenu = () => {
      showMenu.value = false;
      menuExpenseId.value = '';
      currentMenuExpense.value = null;
    };

    // 点击外部关闭菜单
    const handleClickOutside = (event) => {
      if (showMenu.value && !event.target.closest('.long-press-menu')) {
        closeMenu();
      }
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

    // 添加全局点击事件监听
    onMounted(() => {
      document.addEventListener('click', handleClickOutside);
    });

    // 移除全局点击事件监听
    onUnmounted(() => {
      document.removeEventListener('click', handleClickOutside);
      if (longPressTimer.value) {
        clearTimeout(longPressTimer.value);
      }
    });

    // 监听数据变化
    watch(() => props.groupedExpenses, (newVal) => {
      const totalExpenses = Object.values(newVal || {}).reduce((sum, expenses) => sum + expenses.length, 0);
      console.log('Expense data updated:', { recordCount: totalExpenses });
    }, { deep: true });

    return {
      getTypeColor,
      formatDate,
      calculateDailyTotal,
      handleEdit,
      handleDelete,
      showMenu,
      menuExpenseId,
      currentMenuExpense,
      menuStyle,
      startLongPress,
      endLongPress,
      closeMenu,
      sortField,
      sortOrder
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
  background: linear-gradient(90deg, #ff7eb3, #ff758c);
  -webkit-background-clip: text;
  -moz-background-clip: text;
  -ms-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  -moz-text-fill-color: transparent;
  -ms-text-fill-color: transparent;
  text-align: left;
  padding: 12px 15px;
  font-weight: 600;
  font-size: 15px;
}

.expense-table td {
  padding: 10px 15px;
  border-bottom: 1px solid #e9ecef;
}

.expense-table tr:hover {
  background-color: rgba(67, 97, 238, 0.03);
}

/* 日期标题行样式 */
.date-header-row {
  background-color: #f8f9fa;
}

.date-header-row td {
  padding: 8px 15px;
  border-bottom: 2px solid #dee2e6;
}

.date-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}

.date-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.date-text {
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
}

.count-text {
  font-size: 12px;
  color: #6c757d;
}

.total-amount {
  font-size: 16px;
  font-weight: 600;
  color: #e63946;
}

/* 卡片视图样式 */
.card-view {
  display: none;
}

/* 日期标题卡片样式 */
.date-header-card {
  background: #f8f9fa;
  border-radius: 10px;
  padding: 12px 16px;
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.date-header-card .date-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.date-header-card .date-text {
  font-size: 16px;
  font-weight: 600;
  color: #2c3e50;
}

.date-header-card .count-text {
  font-size: 12px;
  color: #6c757d;
}

.date-header-card .total-amount {
  font-size: 18px;
  font-weight: 600;
  color: #e63946;
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
    white-space: pre-wrap;
    line-height: 1.5;
  }

  .remark-cell {
    white-space: pre-wrap;
    word-break: break-word;
    line-height: 1.5;
    min-height: 40px;
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

/* 长按菜单样式 */
.long-press-menu {
  position: fixed;
  z-index: 9999;
  pointer-events: auto;
}

.menu-content {
  background: white;
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  padding: 8px;
  min-width: 120px;
  width: 120px;
  overflow: hidden;
  z-index: 10000;
  transform-origin: top right;
}

.menu-btn {
  width: 100%;
  margin-bottom: 8px;
  font-size: 12px;
  padding: 6px 12px;
  transition: all 0.2s ease;
  font-weight: 500;
}

.menu-btn:last-child {
  margin-bottom: 0;
}

.menu-edit-btn {
  background-color: #4361ee;
  color: white;
}

.menu-edit-btn:hover {
  background-color: #3a56d4;
}

.menu-delete-btn {
  background-color: #e63946;
  color: white;
}

.menu-delete-btn:hover {
  background-color: #c1121f;
}

/* 菜单动画效果 */
.menu-fade-enter-active,
.menu-fade-leave-active {
  transition: all 0.3s ease-out !important;
  transform-origin: top right !important;
  will-change: transform, opacity !important;
}

.menu-fade-enter-from {
  opacity: 0 !important;
  transform: scale(0.8) rotate(-10deg) !important;
}

.menu-fade-leave-to {
  opacity: 0 !important;
  transform: scale(0.8) rotate(10deg) !important;
}

/* 兼容Vue 2的动画类名 */
.menu-fade-enter {
  opacity: 0 !important;
  transform: scale(0.8) rotate(-10deg) !important;
}

.menu-fade-leave-active {
  opacity: 1 !important;
  transform: scale(1) rotate(0deg) !important;
}

.menu-fade-leave-to {
  opacity: 0 !important;
  transform: scale(0.8) rotate(10deg) !important;
}

/* 深色模式适配 */
@media (prefers-color-scheme: dark) {
  .menu-content {
    background-color: #2a2a2a;
    border: 1px solid #444;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
  }
  
  .menu-btn {
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  }
  
  .menu-btn:hover {
    opacity: 0.9;
  }
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
  top: 50%;
  transform: translateY(-50%);
  font-size: 12px;
  margin-left: 5px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

:deep(.sort-indicator svg) {
  width: 14px;
  height: 14px;
}

:deep(.sort-indicator svg path) {
  fill: url(#gradient-arrow);
}

/* 渐变定义 */
.gradient-defs {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
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

  .remark-cell {
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

  /* 深色模式下的日期标题样式 */
  .date-header-row {
    background-color: #2a2a2a;
  }

  .date-header-row td {
    border-bottom: 2px solid #444;
  }

  .date-text {
    color: #e0e0e0;
  }

  .count-text {
    color: #aaa;
  }

  .total-amount {
    color: #f87171;
  }

  .date-header-card {
    background-color: #2a2a2a;
    border: 1px solid #444;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
  }

  .date-header-card .date-text {
    color: #e0e0e0;
  }

  .date-header-card .count-text {
    color: #aaa;
  }

  .date-header-card .total-amount {
    color: #f87171;
  }
}
</style>







