<template>
  <div :class="['glass-form-item', { 'dark-theme': darkTheme }]">
    <label v-if="label" :class="['glass-form-item-label', { 'required': required }]">
      {{ label }}
    </label>
    <div class="glass-form-item-content">
      <slot></slot>
    </div>
    <div v-if="error" class="glass-form-item-error">
      {{ error }}
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  label: {
    type: String,
    default: ''
  },
  required: {
    type: Boolean,
    default: false
  },
  prop: {
    type: String,
    default: ''
  },
  error: {
    type: String,
    default: ''
  },
  darkTheme: {
    type: Boolean,
    default: false
  }
})
</script>

<style scoped>
.glass-form-item {
  margin-bottom: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.glass-form-item-label {
  font-size: 14px;
  font-weight: 500;
  color: #1a202c;
}

.glass-form-item-label.required::after {
  content: '*';
  color: #e53e3e;
  margin-left: 4px;
}

.glass-form-item-content {
  display: flex;
  width: 100%;
  justify-content: center;
  align-items: center;
}

.glass-form-item-error {
  font-size: 12px;
  color: #e53e3e;
  margin-top: 4px;
}

/* Dark theme */
.glass-form-item.dark-theme .glass-form-item-label {
  color: #e2e8f0;
}

.glass-form-item.dark-theme .glass-form-item-error {
  color: #fc8181;
}
</style>