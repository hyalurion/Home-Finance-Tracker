<template>
  <Transition
    name="fade"
  >
    <div v-if="message" :class="['message-tip', type, responsivePosition]">
      {{ message }}
    </div>
  </Transition>
</template>

<script setup>
import { watch, onUnmounted, computed } from 'vue';


// 使用 defineProps 声明组件接收的属性
const props = defineProps({
  // 消息内容，字符串类型
  message: String,
  // 消息类型，可以是 'success' 或 'error'，默认为 'success'
  type: {
    type: String,
    default: 'success',
    validator: val => ['success', 'error'].includes(val)
  },
  // 位置，可以是 'top-left', 'top-right', 'bottom-left', 'bottom-right', 'top', 'bottom', 'auto'，默认为 'auto'
  position: {
    type: String,
    default: 'auto',
    validator: val => ['top-left', 'top-right', 'bottom-left', 'bottom-right', 'top', 'bottom', 'auto'].includes(val)
  }
});

// 响应式位置计算：大屏幕右上角，小屏幕正下
const responsivePosition = computed(() => {
  // 默认使用props.position，如果未指定则根据屏幕尺寸判断
  if (props.position && props.position !== 'auto') {
    return props.position
  }
  
  // 检测屏幕尺寸
  const isSmallScreen = window.innerWidth <= 768
  return isSmallScreen ? 'bottom' : 'top-right'
});

// 使用 defineEmits 声明组件可以发出的事件
// 'update:message' 事件用于通知父组件更新 message 属性
const emit = defineEmits(['update:message']);

// 用于存储定时器 ID
let timer = null;

// 监听 props.message 的变化
watch(() => props.message, (newVal) => {
  // 如果有新消息内容
  if (newVal) {
    // 记录日志
    // 如果存在旧的定时器，先清除它，避免重复触发
    if (timer) {
      clearTimeout(timer);
    }
    // 设置新的定时器，3秒后清空消息
    timer = setTimeout(() => {
      // 触发 'update:message' 事件，将消息内容设置为空字符串
      emit('update:message', '');
      // 定时器触发后，将 timer 重置为 null
      timer = null;
    }, 3000);
  } else {
    // 如果 message 变为空，立即清除任何正在运行的定时器
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  }
}, { immediate: true }); // immediate: true 确保在组件初始化时如果 message 已经有值，也会立即执行一次 watch

// 组件卸载时清除定时器
onUnmounted(() => {
  if (timer) clearTimeout(timer);
});
</script>

<style scoped>
/* 消息提示的基础样式 */
.message-tip {
  position: fixed; /* 固定定位，使其浮动在页面上方 */
  /* 基础样式，位置将由位置类覆盖 */
  padding: 14px 24px; /* 内边距 */
  border-radius: 16px; /* 圆角 */
  font-size: 15px; /* 字体大小 */
  font-weight: 450; /* 字体粗细 */
  z-index: 9999; /* 层级，确保在所有其他内容之上 */
  backdrop-filter: blur(16px); /* 背景模糊效果 */
  border: 1px solid rgba(255, 255, 255, 0.25); /* 半透明边框 */
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 8px 32px rgba(31, 38, 135, 0.15),
    0 0 0 1px rgba(255, 255, 255, 0.1); /* 阴影效果 */
  background: rgba(255, 255, 255, 0.85); /* 半透明背景 */
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1); /* 所有属性的过渡效果 */
  text-align: center; /* 文本居中 */
  max-width: 90vw; /* 最大宽度为视口宽度的90%，防止溢出 */
  min-width: 220px; /* 最小宽度 */
  box-sizing: border-box; /* 边框盒模型，确保padding和border包含在宽度内 */
  word-wrap: break-word; /* 允许长单词或URL地址在必要时换行 */
  letter-spacing: 0.2px;
}

/* 浅色模式成功消息样式 */
.message-tip.success {
  color: #16a34a; /* 深绿色文本 */
  border-left: 4px solid rgba(34, 197, 94, 0.6);
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 8px 32px rgba(34, 197, 94, 0.15),
    0 0 0 1px rgba(34, 197, 94, 0.1);
}

/* 浅色模式错误消息样式 */
.message-tip.error {
  color: #dc2626; /* 深红色文本 */
  border-left: 4px solid rgba(239, 68, 68, 0.6);
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 8px 32px rgba(239, 68, 68, 0.15),
    0 0 0 1px rgba(239, 68, 68, 0.1);
}

/* 深色模式支持 */
@media (prefers-color-scheme: dark) {
  .message-tip {
    background: rgba(30, 41, 59, 0.85);
    border-color: rgba(255, 255, 255, 0.12);
    box-shadow: 
      inset 0 1px 0 rgba(255, 255, 255, 0.1),
      0 8px 32px rgba(0, 0, 0, 0.25),
      0 0 0 1px rgba(255, 255, 255, 0.05);
  }

  /* 深色模式成功消息样式 */
  .message-tip.dark-theme.success,
  .message-tip.success {
    color: #34d399;
    border-left: 4px solid rgba(34, 197, 94, 0.7);
    box-shadow: 
      inset 0 1px 0 rgba(255, 255, 255, 0.1),
      0 8px 32px rgba(34, 197, 94, 0.12),
      0 0 0 1px rgba(34, 197, 94, 0.15);
  }

  /* 深色模式错误消息样式 */
  .message-tip.dark-theme.error,
  .message-tip.error {
    color: #fca5a5;
    border-left: 4px solid rgba(239, 68, 68, 0.7);
    box-shadow: 
      inset 0 1px 0 rgba(255, 255, 255, 0.1),
      0 8px 32px rgba(239, 68, 68, 0.12),
      0 0 0 1px rgba(239, 68, 68, 0.15);
  }
}

/* 消息提示的过渡效果 */
.fade-enter-active,
.fade-leave-active {
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 根据位置应用不同的动画 */
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  scale: 0.9;
}

/* 顶部位置动画 */
.message-tip.top {
  .fade-enter-from,
  .fade-leave-to {
    transform: translateX(-50%) translateY(-30px) scale(0.9);
  }
}

.message-tip.top-right,
.message-tip.top-left {
  .fade-enter-from,
  .fade-leave-to {
    transform: translateY(-30px) scale(0.9);
  }
}

/* 底部位置动画 */
.message-tip.bottom {
  .fade-enter-from,
  .fade-leave-to {
    transform: translateX(-50%) translateY(30px) scale(0.9);
  }
}

.message-tip.bottom-right,
.message-tip.bottom-left {
  .fade-enter-from,
  .fade-leave-to {
    transform: translateY(30px) scale(0.9);
  }
}

/* 悬停效果 */
.message-tip:hover {
  box-shadow: 
    inset 0 1px 0 rgba(255, 255, 255, 0.4),
    0 12px 40px rgba(31, 38, 135, 0.2),
    0 0 0 1px rgba(255, 255, 255, 0.1);
}

.message-tip.top:hover {
  transform: translateX(-50%) translateY(-2px);
}

.message-tip.top-right:hover,
.message-tip.top-left:hover {
  transform: translateY(-2px);
}

.message-tip.bottom:hover {
  transform: translateX(-50%) translateY(2px);
}

.message-tip.bottom-right:hover,
.message-tip.bottom-left:hover {
  transform: translateY(2px);
}

/* 位置样式 */
.message-tip.top {
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
}

.message-tip.top-right {
  top: 20px;
  right: 20px;
  transform: none;
}

.message-tip.top-left {
  top: 20px;
  left: 20px;
  transform: none;
}

.message-tip.bottom {
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
}

.message-tip.bottom-right {
  bottom: 20px;
  right: 20px;
  transform: none;
}

.message-tip.bottom-left {
  bottom: 20px;
  left: 20px;
  transform: none;
}

/* 响应式调整 */
@media (max-width: 480px) {
  .message-tip {
    max-width: calc(100vw - 40px);
    border-radius: 14px;
    padding: 12px 20px;
  }
}
</style>
