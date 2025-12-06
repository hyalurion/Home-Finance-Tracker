<template>
  <!-- This component doesn't render anything directly -->
  <!-- It's used to define column metadata for GlassTable -->
  <template v-if="$slots.default">
    <slot></slot>
  </template>
</template>

<script setup>
import { onMounted, onUnmounted, computed, inject } from 'vue'

const props = defineProps({
  label: {
    type: String,
    default: ''
  },
  prop: {
    type: String,
    default: ''
  },
  width: {
    type: String,
    default: ''
  },
  key: {
    type: String,
    default: ''
  }
})

const emit = defineEmits([])

// Get parent table component
const table = inject('table', null)

const columnKey = computed(() => props.key || props.prop)

// 提供列的slot给父表格组件
if (table && $slots.default) {
  // 注意：这里我们不能直接将slot传递给父组件
  // 因为Vue的slot是在组件渲染时才可用的
  // 我们需要使用不同的方法来处理这个问题
}

onMounted(() => {
  if (table) {
    const columnConfig = {
      label: props.label,
      prop: props.prop,
      width: props.width,
      key: columnKey.value
    }
    table.registerColumn(columnConfig)
  }
})

onUnmounted(() => {
  if (table) {
    table.unregisterColumn(columnKey.value)
  }
})
</script>

<style scoped>
/* This component doesn't have direct styles */
</style>