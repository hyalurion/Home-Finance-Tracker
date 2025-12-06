<template>
  <div 
    :class="['glass-switch-container', { 'dark-theme': darkTheme }]"
    :style="containerStyle"
  >
    <label 
      :class="['glass-switch', { 'active': modelValue, 'disabled': disabled, 'dark-theme': darkTheme }]"
      :style="switchStyle"
    >
      <input 
        type="checkbox" 
        :checked="modelValue" 
        :disabled="disabled"
        @change="handleChange"
        class="glass-switch-input"
      >
      <span class="glass-switch-slider"></span>
    </label>
    <span v-if="label" class="glass-switch-label">{{ label }}</span>
  </div>
</template>

<script setup>
import { computed } from 'vue'

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
  size: {
    type: String,
    default: 'medium',
    validator: (value) => ['small', 'medium', 'large'].includes(value)
  },
  activeColor: {
    type: String,
    default: '#3b82f6'
  },
  inactiveColor: {
    type: String,
    default: '#9ca3af'
  },
  darkTheme: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const sizeMap = {
  small: {
    width: '40px',
    height: '20px',
    sliderSize: '16px'
  },
  medium: {
    width: '50px',
    height: '24px',
    sliderSize: '20px'
  },
  large: {
    width: '60px',
    height: '30px',
    sliderSize: '26px'
  }
}

const switchStyle = computed(() => {
  const size = sizeMap[props.size]
  return {
    width: size.width,
    height: size.height,
    '--active-color': props.activeColor,
    '--inactive-color': props.inactiveColor,
    '--slider-size': size.sliderSize
  }
})

const containerStyle = computed(() => {
  return props.label ? {
    display: 'flex',
    alignItems: 'center',
    gap: '8px'
  } : {}
})

const handleChange = (event) => {
  if (!props.disabled) {
    const newValue = event.target.checked
    emit('update:modelValue', newValue)
    emit('change', newValue)
  }
}
</script>

<style scoped>
.glass-switch-container {
  display: inline-flex;
  align-items: center;
  user-select: none;
}

.glass-switch {
  position: relative;
  display: inline-block;
  background-color: var(--inactive-color);
  border-radius: 9999px;
  cursor: pointer;
  transition: all 0.3s ease;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 4px 12px rgba(31, 38, 135, 0.1);
}

.glass-switch.active {
  background-color: var(--active-color);
}

.glass-switch.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.glass-switch-input {
  opacity: 0;
  width: 0;
  height: 0;
}

.glass-switch-slider {
  position: absolute;
  top: 2px;
  left: 2px;
  width: var(--slider-size);
  height: var(--slider-size);
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  transition: transform 0.3s ease, background-color 0.3s ease;
}

.glass-switch.active .glass-switch-slider {
  transform: translateX(calc(100% - 4px));
}

.glass-switch-label {
  font-size: 14px;
  color: #4a5568;
  user-select: none;
}

/* Dark theme */
.glass-switch-container.dark-theme .glass-switch {
  border-color: rgba(255, 255, 255, 0.15);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.glass-switch-container.dark-theme .glass-switch-slider {
  background: rgba(26, 32, 44, 0.9);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.3);
}

.glass-switch-container.dark-theme .glass-switch-label {
  color: #cbd5e0;
}
</style>