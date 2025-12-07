<template>
  <div :class="['glass-alert', alertType, { 'dark-theme': darkTheme }]">
    <div class="glass-alert-content">
      <div class="glass-alert-icon">
        <slot name="icon">
          <svg v-if="alertType === 'success'" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
            <polyline points="22 4 12 14.01 9 11.01"></polyline>
          </svg>
          <svg v-else-if="alertType === 'warning'" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
            <line x1="12" y1="9" x2="12" y2="13"></line>
            <line x1="12" y1="17" x2="12.01" y2="17"></line>
          </svg>
          <svg v-else-if="alertType === 'error'" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="15" y1="9" x2="9" y2="15"></line>
            <line x1="9" y1="9" x2="15" y2="15"></line>
          </svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="16" x2="12" y2="12"></line>
            <line x1="12" y1="8" x2="12.01" y2="8"></line>
          </svg>
        </slot>
      </div>
      <div class="glass-alert-message">
        <slot></slot>
      </div>
    </div>
    <button v-if="closable" class="glass-alert-close" @click="handleClose">
      <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <line x1="18" y1="6" x2="6" y2="18"></line>
        <line x1="6" y1="6" x2="18" y2="18"></line>
      </svg>
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: {
    type: String,
    default: 'info',
    validator: (value) => ['success', 'warning', 'error', 'info'].includes(value)
  },
  closable: {
    type: Boolean,
    default: false
  },
  darkTheme: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close'])

const alertType = computed(() => props.type)

const handleClose = () => {
  emit('close')
}
</script>

<style scoped>
.glass-alert {
  position: relative;
  border-radius: 8px;
  padding: 16px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 4px 16px rgba(31, 38, 135, 0.1);
  background: rgba(255, 255, 255, 0.8);
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.glass-alert-content {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
}

.glass-alert-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.glass-alert-message {
  color: #4a5568;
  font-size: 14px;
  line-height: 1.5;
}

.glass-alert-close {
  background: none;
  border: none;
  cursor: pointer;
  color: #718096;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.glass-alert-close:hover {
  background: rgba(0, 0, 0, 0.1);
  color: #1a202c;
}

/* Type variants */
.glass-alert.success {
  border-left: 4px solid #22c55e;
}

.glass-alert.success .glass-alert-icon {
  color: #22c55e;
}
.glass-alert.success .glass-alert-message :deep(strong) {
  color: #22c55e;
}

.glass-alert.warning {
  border-left: 4px solid #eab308;
}

.glass-alert.warning .glass-alert-icon {
  color: #eab308;
}
.glass-alert.warning .glass-alert-message :deep(strong) {
  color: #eab308;
}

.glass-alert.error {
  border-left: 4px solid #ef4444;
}

.glass-alert.error .glass-alert-icon {
  color: #ef4444;
}
.glass-alert.error .glass-alert-message :deep(strong) {
  color: #ef4444;
}

.glass-alert.info {
  border-left: 4px solid #3b82f6;
}

.glass-alert.info .glass-alert-icon {
  color: #3b82f6;
}
.glass-alert.info .glass-alert-message :deep(strong) {
  color: #3b82f6;
}

/* Dark theme */
@media (prefers-color-scheme: dark) {
.glass-alert {
  background: rgba(26, 32, 44, 0.8);
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
}

.glass-alert-message {
  color: #cbd5e0;
}

.glass-alert .glass-alert-close {
  color: #a0aec0;
}

.glass-alert .glass-alert-close:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #e2e8f0;
}

/* Type variants for dark theme */
.glass-alert.success .glass-alert-message :deep(strong) {
  color: #22c55e;
}

.glass-alert.warning .glass-alert-message :deep(strong) {
  color: #eab308;
}

.glass-alert.error .glass-alert-message :deep(strong) {
  color: #ef4444;
}

.glass-alert.info .glass-alert-message :deep(strong) {
  color: #3b82f6;
}
}
</style>