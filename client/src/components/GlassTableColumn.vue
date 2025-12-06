<template>
  <!-- This component doesn't render anything directly -->
  <!-- It's used to define column metadata for GlassTable -->
  <slot></slot>
</template>

<script setup>
import { onMounted, onUnmounted, ref, computed, inject, provide } from 'vue'

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