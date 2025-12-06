<template>
  <div :class="['glass-table-wrapper', { 'dark-theme': darkTheme }]">
    <table :class="['glass-table', { 'dark-theme': darkTheme }]">
      <thead>
        <tr>
          <th v-for="column in columns" :key="column.key" :style="{ width: column.width }" class="glass-table-th">
            {{ column.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in data" :key="row.id || index" class="glass-table-tr">
          <td v-for="column in columns" :key="column.key" class="glass-table-td">
            <slot :name="`default:${column.key}`" :row="row">
              {{ row[column.prop] }}
            </slot>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>
import { computed, ref, provide } from 'vue'

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  },
  darkTheme: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits([])

// 收集表格列
const columns = ref([])

// 注册列组件的方法
const registerColumn = (column) => {
  columns.value.push(column)
}

// 取消注册列组件的方法
const unregisterColumn = (columnKey) => {
  columns.value = columns.value.filter(column => column.key !== columnKey)
}

// 提供表格实例给子列组件
provide('table', {
  registerColumn,
  unregisterColumn
})

// 暴露注册和取消注册方法
defineExpose({
  registerColumn,
  unregisterColumn
})
</script>

<style scoped>
.glass-table-wrapper {
  border-radius: 12px;
  overflow: hidden;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.1);
  margin-bottom: 16px;
}

.glass-table {
  width: 100%;
  border-collapse: collapse;
  background: rgba(255, 255, 255, 0.7);
}

.glass-table-th {
  padding: 16px;
  text-align: left;
  font-weight: 600;
  color: #1a202c;
  border-bottom: 1px solid rgba(255, 255, 255, 0.3);
  background: rgba(255, 255, 255, 0.9);
}

.glass-table-tr {
  border-bottom: 1px solid rgba(255, 255, 255, 0.3);
  transition: all 0.2s ease;
}

.glass-table-tr:hover {
  background: rgba(255, 255, 255, 0.5);
}

.glass-table-td {
  padding: 16px;
  color: #4a5568;
  font-size: 14px;
}

/* Dark theme */
.glass-table-wrapper.dark-theme {
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.glass-table.dark-theme {
  background: rgba(26, 32, 44, 0.7);
}

.glass-table.dark-theme .glass-table-th {
  color: #e2e8f0;
  border-bottom-color: rgba(255, 255, 255, 0.1);
  background: rgba(26, 32, 44, 0.9);
}

.glass-table.dark-theme .glass-table-tr {
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

.glass-table.dark-theme .glass-table-tr:hover {
  background: rgba(255, 255, 255, 0.05);
}

.glass-table.dark-theme .glass-table-td {
  color: #cbd5e0;
}
</style>