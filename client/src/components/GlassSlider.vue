<template>
  <div :class="['glass-slider-container', { 'dark-theme': darkTheme }]">
    <div v-if="label" class="glass-slider-label">{{ label }}</div>
    <div 
      :class="['glass-slider', { 'disabled': disabled, 'dark-theme': darkTheme }]"
      :style="sliderStyle"
      @mousedown="handleMouseDown"
      @touchstart="handleTouchStart"
    >
      <div 
        class="glass-slider-track"
        :style="{
          width: `${progress}%`,
          backgroundColor: trackColor
        }"
      ></div>
      <div 
        ref="sliderThumb"
        class="glass-slider-thumb"
        :style="{
          left: `${progress}%`,
          backgroundColor: thumbColor,
          transform: `translateX(-50%) scale(${isDragging ? 1.2 : 1})`
        }"
      ></div>
    </div>
    <div v-if="showValue" class="glass-slider-value">{{ modelValue }}</div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  modelValue: {
    type: Number,
    default: 0
  },
  label: {
    type: String,
    default: ''
  },
  min: {
    type: Number,
    default: 0
  },
  max: {
    type: Number,
    default: 100
  },
  step: {
    type: Number,
    default: 1
  },
  disabled: {
    type: Boolean,
    default: false
  },
  showValue: {
    type: Boolean,
    default: false
  },
  trackColor: {
    type: String,
    default: '#3b82f6'
  },
  thumbColor: {
    type: String,
    default: '#3b82f6'
  },
  darkTheme: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const sliderThumb = ref(null)
const isDragging = ref(false)

const progress = computed(() => {
  return ((props.modelValue - props.min) / (props.max - props.min)) * 100
})

const sliderStyle = computed(() => {
  return {
    '--track-color': props.trackColor,
    '--thumb-color': props.thumbColor
  }
})

const updateValue = (clientX) => {
  if (props.disabled) return
  
  const sliderRect = sliderThumb.value.parentElement.getBoundingClientRect()
  const offsetX = clientX - sliderRect.left
  const percentage = Math.max(0, Math.min(100, (offsetX / sliderRect.width) * 100))
  
  // 计算新值
  const rawValue = (percentage / 100) * (props.max - props.min) + props.min
  // 按步长取整
  const newValue = Math.round(rawValue / props.step) * props.step
  
  emit('update:modelValue', newValue)
  emit('change', newValue)
}

const handleMouseDown = (e) => {
  if (props.disabled) return
  isDragging.value = true
  updateValue(e.clientX)
}

const handleTouchStart = (e) => {
  if (props.disabled) return
  isDragging.value = true
  updateValue(e.touches[0].clientX)
}

const handleMouseMove = (e) => {
  if (isDragging.value) {
    updateValue(e.clientX)
  }
}

const handleTouchMove = (e) => {
  if (isDragging.value) {
    updateValue(e.touches[0].clientX)
  }
}

const handleMouseUp = () => {
  isDragging.value = false
}

const handleTouchEnd = () => {
  isDragging.value = false
}

onMounted(() => {
  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
  document.addEventListener('touchmove', handleTouchMove)
  document.addEventListener('touchend', handleTouchEnd)
})

onUnmounted(() => {
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
  document.removeEventListener('touchmove', handleTouchMove)
  document.removeEventListener('touchend', handleTouchEnd)
})
</script>

<style scoped>
.glass-slider-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
  padding: 12px;
}

.glass-slider-label {
  font-size: 14px;
  color: #4a5568;
  font-weight: 500;
}

.glass-slider {
  position: relative;
  width: 100%;
  height: 8px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 4px;
  cursor: pointer;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 4px 12px rgba(31, 38, 135, 0.1);
}

.glass-slider-track {
  position: absolute;
  height: 100%;
  border-radius: 4px;
  transition: width 0.1s ease;
}

.glass-slider-thumb {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 20px;
  height: 20px;
  background: var(--thumb-color);
  border-radius: 50%;
  cursor: grab;
  transition: transform 0.2s ease, background-color 0.2s ease;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
  backdrop-filter: blur(10px);
  border: 2px solid rgba(255, 255, 255, 0.8);
}

.glass-slider-thumb:active {
  cursor: grabbing;
}

.glass-slider.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.glass-slider.disabled .glass-slider-thumb {
  cursor: not-allowed;
}

.glass-slider-value {
  align-self: flex-end;
  font-size: 14px;
  color: #4a5568;
  font-weight: 500;
  min-width: 40px;
  text-align: right;
}

/* Dark theme */
.glass-slider-container.dark-theme .glass-slider-label {
  color: #cbd5e0;
}

.glass-slider-container.dark-theme .glass-slider {
  background: rgba(26, 32, 44, 0.5);
  border-color: rgba(255, 255, 255, 0.15);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.glass-slider-container.dark-theme .glass-slider-thumb {
  border-color: rgba(26, 32, 44, 0.8);
}

.glass-slider-container.dark-theme .glass-slider-value {
  color: #cbd5e0;
}
</style>