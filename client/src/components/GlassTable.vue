<template>
  <div :class="['glass-table-wrapper', { 'dark-theme': darkTheme }]">
    <table :class="['glass-table', { 'dark-theme': darkTheme }]">
      <thead>
        <tr>
          <th v-for="(column, index) in columns" :key="index" :style="{ width: column.width }" class="glass-table-th">
            {{ column.label }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, rowIndex) in data" :key="row.id || rowIndex" class="glass-table-tr">
          <td v-for="(column, colIndex) in columns" :key="colIndex" class="glass-table-td">
            {{ row[column.prop] }}
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup>

const props = defineProps({
  data: {
    type: Array,
    default: () => []
  },
  columns: {
    type: Array,
    default: () => []
  },
  darkTheme: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits([])
</script>

<style scoped>
.glass-table-wrapper {
  overflow-x: auto;
  white-space: nowrap;
  border-radius: 12px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.1);
  margin-bottom: 16px;
}

.glass-table {
  width: 100%;
  border-collapse: collapse;
}

.glass-table-th {
  padding: 16px;
  text-align: left;
  font-weight: 600;
  border-bottom: 1px solid rgba(255, 255, 255, 0.3);
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