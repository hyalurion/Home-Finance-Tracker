<template>
  <button
    :class="['glass-button', { 'dark-theme': darkTheme, 'primary': type === 'primary', 'success': type === 'success', 'warning': type === 'warning' }, $attrs.class]"
    :disabled="disabled"
    @click="$emit('click', $event)"
  >
    <slot name="icon"></slot>
    <span v-if="$slots.default"><slot></slot></span>
  </button>
</template>

<script setup>

const props = defineProps({
  type: {
    type: String,
    default: 'default', // default, primary, success, warning
  },
  disabled: {
    type: Boolean,
    default: false,
  },
  darkTheme: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['click'])
</script>

<style scoped>
.glass-button {
  position: relative;
  padding: 10px 20px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  backdrop-filter: blur(10px);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(255, 255, 255, 0.7);
  color: #1a202c;
  box-shadow: 0 4px 16px rgba(31, 38, 135, 0.1);
}

.glass-button::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.05);
  border-radius: inherit;
  z-index: -1;
}

.glass-button:hover {
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 8px 24px rgba(31, 38, 135, 0.15);
  transform: translateY(-2px);
}

.glass-button:active {
  transform: translateY(0);
  box-shadow: 0 4px 12px rgba(31, 38, 135, 0.1);
}

.glass-button:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

/* Color variants */
.glass-button.primary {
  border-color: rgba(59, 130, 246, 0.3);
}

.glass-button.success {
  background: rgba(34, 197, 94, 0.7);
  color: white;
  border-color: rgba(34, 197, 94, 0.3);
}

.glass-button.success:hover {
  background: rgba(34, 197, 94, 0.9);
  box-shadow: 0 8px 24px rgba(34, 197, 94, 0.3);
}

.glass-button.warning {
  background: rgba(234, 179, 8, 0.7);
  color: white;
  border-color: rgba(234, 179, 8, 0.3);
}

.glass-button.warning:hover {
  background: rgba(234, 179, 8, 0.9);
  box-shadow: 0 8px 24px rgba(234, 179, 8, 0.3);
}

/* Dark theme */
@media (prefers-color-scheme: dark) {
.glass-button {
  background: rgba(255, 255, 255, 0.08);
  color: #e2e8f0;
  border-color: rgba(255, 255, 255, 0.15);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
}

.glass-button::before {
  background: rgba(255, 255, 255, 0.05);
}

.glass-button:hover {
  background: rgba(255, 255, 255, 0.12);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
}

.glass-button.primary:hover {
  background: rgba(128, 128, 128, 0.9);
}

.glass-button.success {
  background: rgba(34, 197, 94, 0.7);
}

.glass-button.success:hover {
  background: rgba(34, 197, 94, 0.9);
}

.glass-button.warning {
  background: rgba(234, 179, 8, 0.7);
}

.glass-button.warning:hover {
  background: rgba(234, 179, 8, 0.9);
}
}
</style>