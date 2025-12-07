<template>
  <Transition
    name="glass-message-fade"
  >
    <div
      v-if="visible"
      :class="['glass-message', messageType, responsivePosition]"
      :style="{ zIndex: messageZIndex }"
    >
      <div class="glass-message-content">
        <div class="glass-message-icon">
          <svg v-if="messageType === 'success'" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
            <polyline points="22 4 12 14.01 9 11.01"></polyline>
          </svg>
          <svg v-else-if="messageType === 'warning'" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path>
            <line x1="12" y1="9" x2="12" y2="13"></line>
            <line x1="12" y1="17" x2="12.01" y2="17"></line>
          </svg>
          <svg v-else-if="messageType === 'error'" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="15" y1="9" x2="9" y2="15"></line>
            <line x1="9" y1="9" x2="15" y2="15"></line>
          </svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"></circle>
            <line x1="12" y1="16" x2="12" y2="12"></line>
            <line x1="12" y1="8" x2="12.01" y2="8"></line>
          </svg>
        </div>
        <div class="glass-message-text">
          {{ message }}
        </div>
        <button v-if="closable" class="glass-message-close" @click="close">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>
    </div>
  </Transition>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  message: {
    type: String,
    default: ''
  },
  type: {
    type: String,
    default: 'info',
    validator: (value) => ['success', 'warning', 'error', 'info'].includes(value)
  },
  duration: {
    type: Number,
    default: 3000
  },
  closable: {
    type: Boolean,
    default: false
  },
  position: {
    type: String,
    default: 'auto',
    validator: (value) => ['top-left', 'top-right', 'bottom-left', 'bottom-right', 'top', 'bottom', 'auto'].includes(value)
  },
  zIndex: {
    type: Number,
    default: 3000
  },
  darkTheme: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['close'])

const visible = ref(true)
const messageType = computed(() => props.type)
const messageZIndex = computed(() => props.zIndex)

// 响应式位置计算：大屏幕右上角，小屏幕正下
const responsivePosition = computed(() => {
  // 默认使用props.position，如果未指定则根据屏幕尺寸判断
  if (props.position && props.position !== 'auto') {
    return props.position
  }
  
  // 检测屏幕尺寸
  const isSmallScreen = window.innerWidth <= 768
  return isSmallScreen ? 'bottom' : 'top-right'
})

let timer = null

const close = () => {
  visible.value = false
  clearTimeout(timer)
  emit('close')
}

onMounted(() => {
  if (props.duration > 0) {
    timer = setTimeout(close, props.duration)
  }
})

onUnmounted(() => {
  clearTimeout(timer)
})
</script>

<style scoped>
.glass-message {
  position: fixed;
  max-width: 380px;
  min-width: 220px;
  backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: 
    0 8px 32px rgba(31, 38, 135, 0.15),
    0 0 0 1px rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.85);
  border-radius: 16px;
  padding: 16px 20px;
  overflow: hidden;
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  z-index: 3000;
  /* 添加内部发光效果 */
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 8px 32px rgba(31, 38, 135, 0.15),
    0 0 0 1px rgba(255, 255, 255, 0.1);
}

.glass-message.top {
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
}

.glass-message.top-right {
  top: 20px;
  right: 20px;
}

.glass-message.top-left {
  top: 20px;
  left: 20px;
}

.glass-message.bottom {
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
}

.glass-message.bottom-right {
  bottom: 20px;
  right: 20px;
}

.glass-message.bottom-left {
  bottom: 20px;
  left: 20px;
}

.glass-message-content {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.glass-message-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 1px;
}

.glass-message-text {
  flex: 1;
  font-size: 14px;
  font-weight: 450;
  line-height: 1.5;
  color: #2d3748;
  word-wrap: break-word;
  letter-spacing: 0.2px;
}

.glass-message-close {
  background: rgba(255, 255, 255, 0.3);
  border: none;
  cursor: pointer;
  color: #64748b;
  padding: 6px;
  border-radius: 8px;
  transition: all 0.25s ease;
  flex-shrink: 0;
  backdrop-filter: blur(8px);
}

.glass-message-close:hover {
  background: rgba(255, 255, 255, 0.5);
  color: #1e293b;
  transform: scale(1.05);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

/* Type variants */
.glass-message.success {
  border-left: 4px solid rgba(34, 197, 94, 0.6);
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 8px 32px rgba(34, 197, 94, 0.15),
    0 0 0 1px rgba(34, 197, 94, 0.1);
}

.glass-message.success .glass-message-icon {
  color: #16a34a;
}

.glass-message.warning {
  border-left: 4px solid rgba(234, 179, 8, 0.6);
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 8px 32px rgba(234, 179, 8, 0.15),
    0 0 0 1px rgba(234, 179, 8, 0.1);
}

.glass-message.warning .glass-message-icon {
  color: #ca8a04;
}

.glass-message.error {
  border-left: 4px solid rgba(239, 68, 68, 0.6);
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 8px 32px rgba(239, 68, 68, 0.15),
    0 0 0 1px rgba(239, 68, 68, 0.1);
}

.glass-message.error .glass-message-icon {
  color: #dc2626;
}

.glass-message.info {
  border-left: 4px solid rgba(59, 130, 246, 0.6);
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 8px 32px rgba(59, 130, 246, 0.15),
    0 0 0 1px rgba(59, 130, 246, 0.1);
}

.glass-message.info .glass-message-icon {
  color: #2563eb;
}

/* Dark theme */
.glass-message.dark-theme {
  background: rgba(30, 41, 59, 0.85);
  border-color: rgba(255, 255, 255, 0.12);
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 8px 32px rgba(0, 0, 0, 0.25),
    0 0 0 1px rgba(255, 255, 255, 0.05);
}

.glass-message.dark-theme .glass-message-text {
  color: #e2e8f0;
}

.glass-message.dark-theme .glass-message-close {
  color: #94a3b8;
}

.glass-message.dark-theme .glass-message-close:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #f1f5f9;
}

/* Dark theme type variants */
.glass-message.dark-theme.success {
  border-left: 4px solid rgba(34, 197, 94, 0.7);
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 8px 32px rgba(34, 197, 94, 0.12),
    0 0 0 1px rgba(34, 197, 94, 0.15);
}

.glass-message.dark-theme.warning {
  border-left: 4px solid rgba(234, 179, 8, 0.7);
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 8px 32px rgba(234, 179, 8, 0.12),
    0 0 0 1px rgba(234, 179, 8, 0.15);
}

.glass-message.dark-theme.error {
  border-left: 4px solid rgba(239, 68, 68, 0.7);
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 8px 32px rgba(239, 68, 68, 0.12),
    0 0 0 1px rgba(239, 68, 68, 0.15);
}

.glass-message.dark-theme.info {
  border-left: 4px solid rgba(59, 130, 246, 0.7);
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 8px 32px rgba(59, 130, 246, 0.12),
    0 0 0 1px rgba(59, 130, 246, 0.15);
}

/* Transition */
.glass-message-fade-enter-active,
.glass-message-fade-leave-active {
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 根据位置应用不同的动画 */
.glass-message-fade-enter-from,
.glass-message-fade-leave-to {
  opacity: 0;
  scale: 0.9;
}

/* 顶部位置动画 */
.glass-message.top {
  .glass-message-fade-enter-from,
  .glass-message-fade-leave-to {
    transform: translateX(-50%) translateY(-30px) scale(0.9);
  }
}

.glass-message.top-right,
.glass-message.top-left {
  .glass-message-fade-enter-from,
  .glass-message-fade-leave-to {
    transform: translateY(-30px) scale(0.9);
  }
}

/* 底部位置动画 */
.glass-message.bottom {
  .glass-message-fade-enter-from,
  .glass-message-fade-leave-to {
    transform: translateX(-50%) translateY(30px) scale(0.9);
  }
}

.glass-message.bottom-right,
.glass-message.bottom-left {
  .glass-message-fade-enter-from,
  .glass-message-fade-leave-to {
    transform: translateY(30px) scale(0.9);
  }
}

/* 添加悬停效果 */
.glass-message:hover {
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 12px 40px rgba(31, 38, 135, 0.2),
    0 0 0 1px rgba(255, 255, 255, 0.1);
}

.glass-message.top:hover {
  transform: translateX(-50%) translateY(-2px);
}

.glass-message.top-right:hover,
.glass-message.top-left:hover {
  transform: translateY(-2px);
}

.glass-message.bottom:hover {
  transform: translateX(-50%) translateY(2px);
}

.glass-message.bottom-right:hover,
.glass-message.bottom-left:hover {
  transform: translateY(2px);
}

/* 响应式调整 */
@media (max-width: 480px) {
  .glass-message {
    max-width: calc(100vw - 40px);
    border-radius: 14px;
    padding: 14px 18px;
  }
}
</style>