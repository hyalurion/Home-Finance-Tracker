<template>
  <transition name="glass-dialog" @after-enter="afterEnter" @after-leave="afterLeave">
    <div v-if="visible" class="glass-dialog-overlay" @click.self="handleClose">
      <div :class="['glass-dialog', { 'dark-theme': darkTheme }]" :style="dialogStyle">
        <div class="glass-dialog-header">
          <h3 class="glass-dialog-title">{{ title }}</h3>
          <button class="glass-dialog-close" @click="handleClose">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>
        <div class="glass-dialog-body">
          <slot></slot>
        </div>
        <div v-if="$slots.footer" class="glass-dialog-footer">
          <slot name="footer"></slot>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup>
import { computed, watch } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: ''
  },
  width: {
    type: String,
    default: '50%'
  },
  darkTheme: {
    type: Boolean,
    default: false
  },
  animationDuration: {
    type: Number,
    default: 300
  },
  preventScroll: {
    type: Boolean,
    default: true
  },
  zIndex: {
    type: Number,
    default: 1000
  }
})

const emit = defineEmits(['update:visible', 'close', 'opened', 'closed'])

const dialogStyle = computed(() => ({
  width: props.width,
  '--animation-duration': `${props.animationDuration}ms`
}))

let originalOverflow = ''

watch(() => props.visible, (newVal) => {
  if (props.preventScroll) {
    if (newVal) {
      // 打开时禁止背景滚动
      originalOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
    } else {
      // 关闭时恢复滚动
      setTimeout(() => {
        document.body.style.overflow = originalOverflow
      }, props.animationDuration)
    }
  }
})

const handleClose = () => {
  emit('update:visible', false)
  emit('close')
}

const afterEnter = () => {
  emit('opened')
}

const afterLeave = () => {
  emit('closed')
}
</script>

<style scoped>
.glass-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: v-bind('zIndex');
  backdrop-filter: blur(4px);
}

.glass-dialog {
  position: relative;
  border-radius: 12px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.37);
  background: rgba(255, 255, 255, 0.9);
  overflow: hidden;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  transition-property: transform, opacity;
  transition-duration: var(--animation-duration, 300ms);
  transition-timing-function: cubic-bezier(0.16, 1, 0.3, 1); /* 优雅的缓动曲线 */
}

/* 进入动画 */
.glass-dialog-enter-active,
.glass-dialog-leave-active {
  transition: all var(--animation-duration, 350ms) cubic-bezier(0.4, 0, 0.2, 1);
}

.glass-dialog-enter-active .glass-dialog,
.glass-dialog-leave-active .glass-dialog {
  transition: all var(--animation-duration, 350ms) cubic-bezier(0.4, 0, 0.2, 1);
}

.glass-dialog-enter-from,
.glass-dialog-leave-to {
  opacity: 0;
}

.glass-dialog-enter-from .glass-dialog {
  opacity: 0;
  transform: scale(0.95) translateY(-10px); /* 减小缩放差异和位移距离，降低冲击感 */
}

.glass-dialog-leave-to .glass-dialog {
  opacity: 0;
  transform: scale(0.97) translateY(10px); /* 优化退出动画，降低冲击感 */
}

.glass-dialog-enter-to,
.glass-dialog-leave-from {
  opacity: 1;
}

.glass-dialog-enter-to .glass-dialog,
.glass-dialog-leave-from .glass-dialog {
  opacity: 1;
  transform: scale(1) translateY(0);
}

/* 对话框内容逐项动画 */
.glass-dialog-enter-active .glass-dialog-header,
.glass-dialog-enter-active .glass-dialog-body,
.glass-dialog-enter-active .glass-dialog-footer {
  animation: slideUpFadeIn calc(var(--animation-duration, 350ms) * 0.8) cubic-bezier(0.4, 0, 0.2, 1) forwards;
  opacity: 0;
  transform: translateY(10px); /* 减小内容位移，降低冲击感 */
}

.glass-dialog-leave-active .glass-dialog-header,
.glass-dialog-leave-active .glass-dialog-body,
.glass-dialog-leave-active .glass-dialog-footer {
  animation: slideDownFadeOut calc(var(--animation-duration, 350ms) * 0.8) cubic-bezier(0.4, 0, 0.2, 1) forwards;
  opacity: 1;
  transform: translateY(0);
}

/* 调整内容动画延迟，使过渡更自然 */
.glass-dialog-enter-active .glass-dialog-header {
  animation-delay: calc(var(--animation-duration, 300ms) * 0.2);
}

.glass-dialog-enter-active .glass-dialog-body {
  animation-delay: calc(var(--animation-duration, 300ms) * 0.3);
}

.glass-dialog-enter-active .glass-dialog-footer {
  animation-delay: calc(var(--animation-duration, 300ms) * 0.4);
}

.glass-dialog-leave-active .glass-dialog-header {
  animation-delay: calc(var(--animation-duration, 300ms) * 0.1);
}

.glass-dialog-leave-active .glass-dialog-body {
  animation-delay: calc(var(--animation-duration, 300ms) * 0.2);
}

.glass-dialog-leave-active .glass-dialog-footer {
  animation-delay: calc(var(--animation-duration, 300ms) * 0.3);
}

@keyframes slideUpFadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes slideDownFadeOut {
  from {
    opacity: 1;
    transform: translateY(0);
  }
  to {
    opacity: 0;
    transform: translateY(10px);
  }
}

.glass-dialog-header {
  padding: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.3);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.glass-dialog-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1a202c;
}

.glass-dialog-close {
  background: none;
  border: none;
  cursor: pointer;
  color: #718096;
  padding: 4px;
  border-radius: 4px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  transform-origin: center;
}

.glass-dialog-close:hover {
  background: rgba(0, 0, 0, 0.1);
  color: #1a202c;
  transform: rotate(90deg);
}

.glass-dialog-body {
  padding: 20px;
  overflow-y: auto;
  flex: 1;
}

.glass-dialog-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.3);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* Dark theme */
.glass-dialog.dark-theme {
  background: rgba(26, 32, 44, 0.9);
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5);
}

.glass-dialog.dark-theme .glass-dialog-header {
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

.glass-dialog.dark-theme .glass-dialog-title {
  color: #e2e8f0;
}

.glass-dialog.dark-theme .glass-dialog-close {
  color: #a0aec0;
}

.glass-dialog.dark-theme .glass-dialog-close:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #e2e8f0;
}

.glass-dialog.dark-theme .glass-dialog-footer {
  border-top-color: rgba(255, 255, 255, 0.1);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .glass-dialog {
    width: 90% !important;
    max-height: 85vh;
    margin: 20px;
  }
}

/* 滚动条美化 */
.glass-dialog-body::-webkit-scrollbar {
  width: 6px;
}

.glass-dialog-body::-webkit-scrollbar-track {
  background: rgba(0, 0, 0, 0.1);
  border-radius: 3px;
}

.glass-dialog-body::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.2);
  border-radius: 3px;
}

.glass-dialog.dark-theme .glass-dialog-body::-webkit-scrollbar-track {
  background: rgba(255, 255, 255, 0.1);
}

.glass-dialog.dark-theme .glass-dialog-body::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.2);
}
</style>