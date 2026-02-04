<template>
  <div :class="['glass-table-wrapper']">
    <table :class="['glass-table', 'desktop-view']">
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
            <template v-if="column.render">
              {{ column.render(row[column.prop], row, rowIndex) }}
            </template>
            <template v-else>
              {{ row[column.prop] }}
            </template>
          </td>
        </tr>
      </tbody>
    </table>

    <div :class="['mobile-view']">
      <div v-for="(row, rowIndex) in data" :key="row.id || rowIndex" class="glass-card">
        <div v-for="(column, colIndex) in columns" :key="colIndex" class="glass-card-item">
          <div class="glass-card-label">{{ column.label }}</div>
          <div class="glass-card-value">
            <template v-if="column.render">
              {{ column.render(row[column.prop], row, rowIndex) }}
            </template>
            <template v-else>
              {{ row[column.prop] }}
            </template>
          </div>
        </div>
      </div>
    </div>
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

.mobile-view {
  display: none;
}

.glass-card {
  border-radius: 12px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.1);
  margin-bottom: 16px;
  padding: 16px;
  transition: all 0.2s ease;
}

.glass-card:hover {
  background: rgba(255, 255, 255, 0.3);
}

.glass-card-item {
  display: flex;
  padding: 12px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.15);
}

.glass-card-item:last-child {
  border-bottom: none;
}

.glass-card-label {
  flex: 0 0 100px;
  font-weight: 600;
  color: #4a5568;
  padding-right: 12px;
}

.glass-card-value {
  flex: 1;
  color: #2d3748;
}

/* Dark theme */
@media (prefers-color-scheme: dark) {
.glass-table-wrapper {
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.glass-table {
  background: rgba(26, 32, 44, 0.7);
}

.glass-table .glass-table-th {
  color: #e2e8f0;
  border-bottom-color: rgba(255, 255, 255, 0.1);
  background: rgba(26, 32, 44, 0.9);
}

.glass-table .glass-table-tr {
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

.glass-table .glass-table-tr:hover {
  background: rgba(255, 255, 255, 0.05);
}

.glass-table .glass-table-td {
  color: #cbd5e0;
}

.glass-card {
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  background: rgba(26, 32, 44, 0.7);
}

.glass-card:hover {
  background: rgba(255, 255, 255, 0.05);
}

.glass-card-item {
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

.glass-card-label {
  color: #a0aec0;
}

.glass-card-value {
  color: #e2e8f0;
}
}

/* Mobile responsive */
@media (max-width: 768px) {
.desktop-view {
  display: none;
}

.mobile-view {
  display: block;
}

.glass-card-item {
  flex-direction: column;
}

.glass-card-label {
  flex: none;
  padding-right: 0;
  padding-bottom: 4px;
  font-size: 12px;
  color: #718096;
}

.glass-card-value {
  font-size: 14px;
}

@media (prefers-color-scheme: dark) {
.glass-card-label {
  color: #718096;
}

.glass-card-value {
  color: #e2e8f0;
}
}
}
</style>