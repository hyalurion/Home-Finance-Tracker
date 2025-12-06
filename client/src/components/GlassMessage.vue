<template>
  <Transition name="glass-message-fade">
    <div
      v-if="visible"
      :class="['glass-message', messageType, position]"
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
    default: 'top-right',
    validator: (value) => ['top-left', 'top-right', 'bottom-left', 'bottom-right', 'top', 'bottom'].includes(value)
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
  max-width: 350px;
  min-width: 200px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 4px 16px rgba(31, 38, 135, 0.2);
  background: rgba(255, 255, 255, 0.9);
  border-radius: 8px;
  padding: 12px 16px;
  overflow: hidden;
  transition: all 0.3s ease;
  z-index: 3000;
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
  align-items: center;
  gap: 10px;
}

.glass-message-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.glass-message-text {
  flex: 1;
  font-size: 14px;
  line-height: 1.4;
  color: #4a5568;
  word-wrap: break-word;
}

.glass-message-close {
  background: none;
  border: none;
  cursor: pointer;
  color: #718096;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.glass-message-close:hover {
  background: rgba(0, 0, 0, 0.1);
  color: #1a202c;
}

/* Type variants */
.glass-message.success {
  border-left: 4px solid #22c55e;
}

.glass-message.success .glass-message-icon {
  color: #22c55e;
}

.glass-message.warning {
  border-left: 4px solid #eab308;
}

.glass-message.warning .glass-message-icon {
  color: #eab308;
}

.glass-message.error {
  border-left: 4px solid #ef4444;
}

.glass-message.error .glass-message-icon {
  color: #ef4444;
}

.glass-message.info {
  border-left: 4px solid #3b82f6;
}

.glass-message.info .glass-message-icon {
  color: #3b82f6;
}

/* Dark theme */
.glass-message.dark-theme {
  background: rgba(26, 32, 44, 0.9);
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.3);
}

.glass-message.dark-theme .glass-message-text {
  color: #cbd5e0;
}

.glass-message.dark-theme .glass-message-close {
  color: #a0aec0;
}

.glass-message.dark-theme .glass-message-close:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #e2e8f0;
}

/* Transition */
.glass-message-fade-enter-active,
.glass-message-fade-leave-active {
  transition: all 0.3s ease;
}

.glass-message-fade-enter-from {
  opacity: 0;
  transform: translateY(-20px);
}

.glass-message-fade-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>