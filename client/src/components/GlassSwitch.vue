<template>
  <div 
    :class="['glass-switch-container', { 'dark-theme': darkTheme }]"
    :style="containerStyle"
  >
    <label 
      :class="[
        'glass-switch', 
        { 
          'active': modelValue, 
          'disabled': disabled, 
          'dark-theme': darkTheme,
          'loading': loading
        }
      ]"
      :style="switchStyle"
    >
      <input 
        type="checkbox" 
        :checked="modelValue" 
        :disabled="disabled || loading"
        @change="handleChange"
        class="glass-switch-input"
      >
      <span class="glass-switch-track">
        <span class="liquid-effect"></span>
        <span class="inner-glow"></span>
      </span>
      <span class="glass-switch-slider">
        <span class="slider-shine"></span>
        <span class="slider-reflect"></span>
      </span>
      <span class="switch-ripple" v-if="showRipple"></span>
    </label>
    <span v-if="label" class="glass-switch-label">{{ label }}</span>
    
    <!-- 加载动画 -->
    <div v-if="loading" class="switch-loading-overlay">
      <div class="loading-spinner"></div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

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
  loading: {
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
    default: 'linear-gradient(135deg, #60a5fa, #3b82f6, #2563eb)'
  },
  inactiveColor: {
    type: String,
    default: 'linear-gradient(135deg, rgba(156, 163, 175, 0.3), rgba(107, 114, 128, 0.2))'
  },
  darkTheme: {
    type: Boolean,
    default: false
  },
  glassIntensity: {
    type: Number,
    default: 15,
    validator: (value) => value >= 0 && value <= 30
  }
})

const emit = defineEmits(['update:modelValue', 'change'])

const showRipple = ref(false)

const sizeMap = {
  small: {
    width: '46px',
    height: '24px',
    sliderSize: '18px',
    fontSize: '12px'
  },
  medium: {
    width: '58px',
    height: '30px',
    sliderSize: '24px',
    fontSize: '14px'
  },
  large: {
    width: '72px',
    height: '36px',
    sliderSize: '30px',
    fontSize: '16px'
  }
}

const switchStyle = computed(() => {
  const size = sizeMap[props.size]
  return {
    width: size.width,
    height: size.height,
    '--active-color': props.activeColor,
    '--inactive-color': props.inactiveColor,
    '--slider-size': size.sliderSize,
    '--glass-blur': `${props.glassIntensity}px`,
    '--switch-width': size.width,
    '--switch-height': size.height
  }
})

const containerStyle = computed(() => {
  return props.label ? {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    '--label-font-size': sizeMap[props.size].fontSize
  } : {}
})

const handleChange = (event) => {
  if (!props.disabled && !props.loading) {
    const newValue = event.target.checked
    emit('update:modelValue', newValue)
    emit('change', newValue)
    
    // 触发涟漪效果
    showRipple.value = true
    setTimeout(() => {
      showRipple.value = false
    }, 600)
  }
}
</script>

<style scoped>
.glass-switch-container {
  display: inline-flex;
  align-items: center;
  user-select: none;
  position: relative;
}

/* 主开关容器 */
.glass-switch {
  position: relative;
  display: inline-block;
  cursor: pointer;
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  border-radius: 24px;
  overflow: hidden;
  isolation: isolate;
}

/* 玻璃效果轨道 */
.glass-switch-track {
  position: absolute;
  inset: 0;
  background: var(--inactive-color);
  border-radius: inherit;
  backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(180%);
  border: 1px solid rgba(255, 255, 255, 0.15);
  box-shadow: 
    inset 0 1px 1px rgba(255, 255, 255, 0.3),
    inset 0 -1px 1px rgba(0, 0, 0, 0.1),
    0 8px 24px rgba(31, 38, 135, 0.15),
    0 4px 8px rgba(31, 38, 135, 0.1);
  transition: all 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.glass-switch.active .glass-switch-track {
  background: var(--active-color);
  border-color: rgba(255, 255, 255, 0.3);
  box-shadow: 
    inset 0 1px 2px rgba(255, 255, 255, 0.4),
    inset 0 -1px 1px rgba(0, 0, 0, 0.1),
    0 8px 32px rgba(59, 130, 246, 0.25),
    0 4px 12px rgba(59, 130, 246, 0.2);
}

/* 液体流动效果 */
.liquid-effect {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(255, 255, 255, 0.1) 50%,
    transparent 100%
  );
  opacity: 0;
  transition: opacity 0.3s ease;
  animation: liquidFlow 3s infinite linear;
  border-radius: inherit;
}

.glass-switch.active .liquid-effect {
  opacity: 0.6;
}

@keyframes liquidFlow {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(100%);
  }
}

/* 内部辉光 */
.inner-glow {
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: radial-gradient(
    circle at 30% 50%,
    rgba(255, 255, 255, 0.2) 0%,
    transparent 70%
  );
  opacity: 0.6;
  transition: opacity 0.3s ease;
}

.glass-switch.active .inner-glow {
  opacity: 0.8;
  background: radial-gradient(
    circle at 70% 50%,
    rgba(255, 255, 255, 0.4) 0%,
    transparent 70%
  );
}

/* 滑块 */
.glass-switch-slider {
  position: absolute;
  top: 5px;
  left: 3px;
  width: calc(var(--slider-size) - 6px);
  height: calc(var(--slider-size) - 6px);
  border-radius: 50%;
  background: linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.95),
    rgba(255, 255, 255, 0.85)
  );
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  box-shadow: 
    0 4px 12px rgba(0, 0, 0, 0.15),
    0 2px 4px rgba(0, 0, 0, 0.1),
    inset 0 1px 1px rgba(255, 255, 255, 0.9),
    inset 0 -1px 1px rgba(0, 0, 0, 0.05);
  transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  z-index: 2;
}

.glass-switch.active .glass-switch-slider {
  transform: translateX(calc(var(--switch-width) - var(--slider-size) - 3px));
  background: linear-gradient(
    135deg,
    rgba(255, 255, 255, 1),
    rgba(255, 255, 255, 0.9)
  );
}

/* 滑块光泽效果 */
.slider-shine {
  position: absolute;
  top: 1px;
  left: 1px;
  right: 1px;
  height: 30%;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.8),
    transparent
  );
  border-radius: 50% 50% 0 0;
}

.slider-reflect {
  position: absolute;
  top: 25%;
  left: 25%;
  width: 50%;
  height: 50%;
  background: radial-gradient(
    circle,
    rgba(255, 255, 255, 0.4) 0%,
    transparent 70%
  );
  border-radius: 50%;
}

/* 涟漪效果 */
.switch-ripple {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%) scale(0);
  width: calc(var(--switch-width) * 1.5);
  height: calc(var(--switch-height) * 1.5);
  border-radius: 50%;
  background: radial-gradient(
    circle,
    rgba(59, 130, 246, 0.2) 0%,
    rgba(59, 130, 246, 0.1) 50%,
    transparent 70%
  );
  animation: rippleEffect 0.6s ease-out;
  z-index: 1;
}

@keyframes rippleEffect {
  to {
    transform: translate(-50%, -50%) scale(1);
    opacity: 0;
  }
}

/* 标签 */
.glass-switch-label {
  font-size: var(--label-font-size, 14px);
  color: #4a5568;
  font-weight: 500;
  letter-spacing: 0.02em;
  transition: color 0.3s ease;
  user-select: none;
}

/* 禁用状态 */
.glass-switch.disabled {
  opacity: 0.6;
  cursor: not-allowed;
  filter: grayscale(0.3);
}

.glass-switch.disabled .glass-switch-track {
  box-shadow: none;
}

.glass-switch.disabled .glass-switch-slider {
  box-shadow: 
    0 2px 6px rgba(0, 0, 0, 0.1),
    inset 0 1px 1px rgba(255, 255, 255, 0.7);
}

/* 加载状态 */
.glass-switch.loading .glass-switch-slider {
  animation: loadingPulse 1.5s ease-in-out infinite;
}

.switch-loading-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(2px);
  border-radius: inherit;
  z-index: 3;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(59, 130, 246, 0.3);
  border-top-color: #3b82f6;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes loadingPulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(0.9); }
}

/* 暗黑主题 */
.glass-switch-container.dark-theme .glass-switch-track {
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 
    inset 0 1px 1px rgba(255, 255, 255, 0.1),
    inset 0 -1px 1px rgba(0, 0, 0, 0.3),
    0 8px 24px rgba(0, 0, 0, 0.4),
    0 4px 8px rgba(0, 0, 0, 0.3);
}

.glass-switch-container.dark-theme .glass-switch.active .glass-switch-track {
  box-shadow: 
    inset 0 1px 2px rgba(255, 255, 255, 0.2),
    inset 0 -1px 1px rgba(0, 0, 0, 0.4),
    0 8px 32px rgba(59, 130, 246, 0.4),
    0 4px 12px rgba(59, 130, 246, 0.3);
}

.glass-switch-container.dark-theme .glass-switch-slider {
  background: linear-gradient(
    135deg,
    rgba(255, 255, 255, 0.9),
    rgba(255, 255, 255, 0.75)
  );
  box-shadow: 
    0 4px 12px rgba(0, 0, 0, 0.3),
    0 2px 4px rgba(0, 0, 0, 0.2),
    inset 0 1px 1px rgba(255, 255, 255, 0.8),
    inset 0 -1px 1px rgba(0, 0, 0, 0.1);
}

.glass-switch-container.dark-theme .glass-switch-label {
  color: #cbd5e0;
}

/* 隐藏原生输入框 */
.glass-switch-input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

/* 悬停效果 */
.glass-switch:not(.disabled):not(.loading):hover {
  transform: translateY(-2px);
}

.glass-switch:not(.disabled):not(.loading):hover .glass-switch-track {
  box-shadow: 
    inset 0 1px 1px rgba(255, 255, 255, 0.4),
    inset 0 -1px 1px rgba(0, 0, 0, 0.1),
    0 12px 32px rgba(31, 38, 135, 0.2),
    0 6px 16px rgba(31, 38, 135, 0.15);
}

.glass-switch.active:not(.disabled):not(.loading):hover .glass-switch-track {
  box-shadow: 
    inset 0 1px 2px rgba(255, 255, 255, 0.5),
    inset 0 -1px 1px rgba(0, 0, 0, 0.1),
    0 12px 40px rgba(59, 130, 246, 0.35),
    0 6px 20px rgba(59, 130, 246, 0.25);
}

/* 点击反馈 */
.glass-switch:not(.disabled):not(.loading):active .glass-switch-slider {
  transform: scale(0.95);
}

.glass-switch.active:not(.disabled):not(.loading):active .glass-switch-slider {
  transform: 
    translateX(calc(var(--switch-width) - var(--slider-size) - 3px))
    scale(0.95);
}
</style>