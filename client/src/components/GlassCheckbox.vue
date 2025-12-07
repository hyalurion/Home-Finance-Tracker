<template>
  <div :class="['glass-checkbox-container', { 'dark-theme': darkTheme }]">
    <label :class="['glass-checkbox-wrapper', { 'disabled': disabled, 'dark-theme': darkTheme }]">
      <input 
        ref="checkboxRef"
        type="checkbox"
        :checked="modelValue"
        @change="handleChange"
        :disabled="disabled"
        class="glass-checkbox-input"
      />
      <div 
        :class="['glass-checkbox-box', { 'checked': modelValue, 'disabled': disabled, 'dark-theme': darkTheme }]"
      >
        <slot name="check-icon">
          <svg v-if="modelValue" width="14" height="14" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M11.6667 3.83334L5.66671 9.83334L2.33337 6.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </slot>
      </div>
      <span v-if="label" class="glass-checkbox-label">{{ label }}</span>
    </label>
    <div v-if="error" class="glass-checkbox-error">{{ error }}</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  label: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
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

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])

const checkboxRef = ref(null)

// 监听外部值变化
watch(
  () => props.modelValue,
  (newVal) => {
    if (checkboxRef.value) {
      checkboxRef.value.checked = newVal
    }
  }
)

// 处理变化事件
const handleChange = (e) => {
  const checked = e.target.checked
  emit('update:modelValue', checked)
  emit('change', checked)
}
</script>

<style scoped>
.glass-checkbox-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.glass-checkbox-wrapper {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
  gap: 8px;
}

.glass-checkbox-input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.glass-checkbox-box {
  width: 20px;
  height: 20px;
  background: rgba(255, 255, 255, 0.5);
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.3s ease;
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 8px rgba(31, 38, 135, 0.1);
  color: #3b82f6;
}

.glass-checkbox-box:hover:not(.disabled) {
  background: rgba(255, 255, 255, 0.7);
  border-color: rgba(255, 255, 255, 0.5);
  box-shadow: 0 4px 12px rgba(31, 38, 135, 0.15);
}

.glass-checkbox-box.checked {
  background: rgba(59, 130, 246, 0.3);
  border-color: #3b82f6;
}

.glass-checkbox-box.checked:hover:not(.disabled) {
  background: rgba(59, 130, 246, 0.4);
}

.glass-checkbox-label {
  font-size: 14px;
  color: #4a5568;
  line-height: 1.5;
  cursor: pointer;
}

.glass-checkbox-wrapper.disabled {
  cursor: not-allowed;
}

.glass-checkbox-box.disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: rgba(255, 255, 255, 0.3);
  border-color: rgba(255, 255, 255, 0.2);
}

.glass-checkbox-wrapper.disabled .glass-checkbox-label {
  opacity: 0.5;
  cursor: not-allowed;
}

.glass-checkbox-error {
  font-size: 12px;
  color: #e53e3e;
  margin-top: 2px;
}

/* Dark theme */
.glass-checkbox-container.dark-theme .glass-checkbox-label {
  color: #cbd5e0;
}

.glass-checkbox-container.dark-theme .glass-checkbox-box {
  background: rgba(26, 32, 44, 0.5);
  border-color: rgba(255, 255, 255, 0.15);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
  color: #60a5fa;
}

.glass-checkbox-container.dark-theme .glass-checkbox-box:hover:not(.disabled) {
  background: rgba(45, 55, 72, 0.7);
  border-color: rgba(255, 255, 255, 0.25);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
}

.glass-checkbox-container.dark-theme .glass-checkbox-box.checked {
  background: rgba(96, 165, 250, 0.3);
  border-color: #60a5fa;
}

.glass-checkbox-container.dark-theme .glass-checkbox-box.checked:hover:not(.disabled) {
  background: rgba(96, 165, 250, 0.4);
}

.glass-checkbox-container.dark-theme .glass-checkbox-box.disabled {
  background: rgba(26, 32, 44, 0.3);
  border-color: rgba(255, 255, 255, 0.1);
}

.glass-checkbox-container.dark-theme .glass-checkbox-wrapper.disabled .glass-checkbox-label {
  color: #718096;
}
</style>