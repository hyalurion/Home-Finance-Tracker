<template>
  <div :class="['glass-input-number-container', { 'dark-theme': darkTheme }]">
    <div v-if="label" class="glass-input-number-label">{{ label }}</div>
    <div :class="['glass-input-number-wrapper', { 'disabled': disabled, 'dark-theme': darkTheme }]">
      <button 
        class="glass-input-number-btn decrease"
        @click="decrease"
        :disabled="disabled || modelValue <= min"
        :class="{ 'dark-theme': darkTheme }"
      >
        <slot name="decrease-icon">−</slot>
      </button>
      <div class="glass-input-number-content">
        <div v-if="prefix" class="glass-input-number-prefix">{{ prefix }}</div>
        <input 
          ref="inputRef"
          type="number"
          :value="modelValue"
          @input="handleInput"
          @focus="handleFocus"
          @blur="handleBlur"
          @keydown.up="handleKeyUp"
          @keydown.down="handleKeyDown"
          :min="min"
          :max="max"
          :step="step"
          :disabled="disabled"
          :placeholder="placeholder"
          :class="['glass-input-number', { 'dark-theme': darkTheme }]"
        />
        <div v-if="suffix" class="glass-input-number-suffix">{{ suffix }}</div>
      </div>
      <button 
        class="glass-input-number-btn increase"
        @click="increase"
        :disabled="disabled || modelValue >= max"
        :class="{ 'dark-theme': darkTheme }"
      >
        <slot name="increase-icon">+</slot>
      </button>
    </div>
    <div v-if="error" class="glass-input-number-error">{{ error }}</div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Number,
    default: 0
  },
  label: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: ''
  },
  min: {
    type: Number,
    default: -Infinity
  },
  max: {
    type: Number,
    default: Infinity
  },
  step: {
    type: Number,
    default: 1
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
  },
  prefix: {
    type: String,
    default: ''
  },
  suffix: {
    type: String,
    default: ''
  },
  precision: {
    type: Number,
    default: 0
  },
  size: {
    type: String,
    default: 'medium'
  }
})

const emit = defineEmits(['update:modelValue', 'change', 'focus', 'blur'])

const internalValue = ref(props.modelValue)

// 确保值在范围内
const clampValue = (value) => {
  return Math.max(props.min, Math.min(props.max, value))
}

// 监听外部值变化
watch(
  () => props.modelValue,
  (newVal) => {
    internalValue.value = clampValue(newVal)
  }
)

// 增加数值
const increase = () => {
  if (props.disabled || internalValue.value >= props.max) return
  const newValue = clampValue(internalValue.value + props.step)
  updateValue(newValue)
}

// 减少数值
const decrease = () => {
  if (props.disabled || internalValue.value <= props.min) return
  const newValue = clampValue(internalValue.value - props.step)
  updateValue(newValue)
}

// 处理输入
const handleInput = (e) => {
  let value = parseFloat(e.target.value)
  
  if (isNaN(value)) {
    // 如果输入为空或非数字，暂时保持空值
    internalValue.value = ''
    return
  }
  
  value = clampValue(value)
  internalValue.value = value
  emit('update:modelValue', value)
  emit('change', value)
}

// 处理焦点
const handleFocus = () => {
  emit('focus')
}

// 处理失焦
const handleBlur = () => {
  if (internalValue.value === '') {
    // 失焦时如果为空，重置为默认值
    const defaultValue = clampValue(props.min)
    updateValue(defaultValue)
  }
  emit('blur')
}

// 处理键盘上键
const handleKeyUp = (e) => {
  e.preventDefault()
  increase()
}

// 处理键盘下键
const handleKeyDown = (e) => {
  e.preventDefault()
  decrease()
}

// 更新值的通用方法
const updateValue = (newValue) => {
  internalValue.value = newValue
  emit('update:modelValue', newValue)
  emit('change', newValue)
}
</script>

<style scoped>
.glass-input-number-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.glass-input-number-label {
  font-size: 14px;
  color: #4a5568;
  font-weight: 500;
}

.glass-input-number-wrapper {
  display: flex;
  align-items: center;
  border-radius: 8px;
  padding: 4px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 4px 12px rgba(31, 38, 135, 0.1);
  transition: all 0.3s ease;
}

.glass-input-number-content {
  display: flex;
  align-items: center;
  flex: 1;
}

.glass-input-number {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  padding: 10px 8px;
  font-size: 16px;
  color: #2d3748;
  text-align: center;
  font-family: inherit;
}

.glass-input-number-prefix,
.glass-input-number-suffix {
  padding: 0 8px;
  font-size: 16px;
  color: #4a5568;
  font-family: inherit;
  user-select: none;
}

.glass-input-number::placeholder {
  color: #a0aec0;
}

.glass-input-number-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.7);
  border: none;
  border-radius: 6px;
  font-size: 20px;
  color: #4a5568;
  cursor: pointer;
  transition: all 0.2s ease;
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 8px rgba(31, 38, 135, 0.1);
}

.glass-input-number-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.9);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(31, 38, 135, 0.15);
}

.glass-input-number-btn:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 2px 6px rgba(31, 38, 135, 0.1);
}

.glass-input-number-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.glass-input-number-wrapper.disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.glass-input-number-error {
  font-size: 12px;
  color: #e53e3e;
  margin-top: 4px;
}

/* Dark theme */
@media (prefers-color-scheme: dark) {
.glass-input-number-container .glass-input-number-label {
  color: #cbd5e0;
}

.glass-input-number-wrapper {
  background: rgba(26, 32, 44, 0.5);
  border-color: rgba(255, 255, 255, 0.15);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.glass-input-number {
  color: #e2e8f0;
}

.glass-input-number-prefix,
.glass-input-number-suffix {
  color: #cbd5e0;
}

.glass-input-number::placeholder {
  color: #718096;
}

.glass-input-number-btn {
  background: rgba(45, 55, 72, 0.7);
  color: #cbd5e0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
}

.glass-input-number-btn:hover:not(:disabled) {  
  background: rgba(45, 55, 72, 0.9);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.4);
}
}
</style>