<template>
  <div 
    :class="['glassmorphism-component', type, { 'dark-theme': darkTheme }]"
    :style="componentStyle"
  >
    <slot></slot>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: {
    type: String,
    default: 'base', // base, button, input, card, dialog
  },
  darkTheme: {
    type: Boolean,
    default: false,
  },
  width: {
    type: String,
    default: 'auto',
  },
  height: {
    type: String,
    default: 'auto',
  },
  borderRadius: {
    type: String,
    default: '12px',
  },
  padding: {
    type: String,
    default: '16px',
  },
})

const componentStyle = computed(() => {
  return {
    width: props.width,
    height: props.height,
    borderRadius: props.borderRadius,
    padding: props.padding,
  }
})
</script>

<style scoped>
.glassmorphism-component {
  position: relative;
  overflow: hidden;
  transition: all 0.3s ease;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.1);
}

.glassmorphism-component::before {
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

/* Base styles */
.glassmorphism-component.base {
  background: rgba(255, 255, 255, 0.7);
}

/* Button styles */
.glassmorphism-component.button {
  background: rgba(255, 255, 255, 0.8);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 500;
  gap: 8px;
}

.glassmorphism-component.button:hover {
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 12px 48px rgba(31, 38, 135, 0.15);
  transform: translateY(-2px);
}

.glassmorphism-component.button:active {
  transform: translateY(0);
}

/* Input styles */
.glassmorphism-component.input {
  background: rgba(255, 255, 255, 0.8);
}

.glassmorphism-component.input:focus-within {
  border-color: rgba(255, 255, 255, 0.4);
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.1);
}

/* Card styles */
.glassmorphism-component.card {
  background: rgba(255, 255, 255, 0.7);
}

/* Dialog styles */
.glassmorphism-component.dialog {
  background: rgba(255, 255, 255, 0.9);
}

/* Dark theme */
.glassmorphism-component.dark-theme {
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.glassmorphism-component.dark-theme::before {
  background: rgba(0, 0, 0, 0.1);
}

.glassmorphism-component.dark-theme.base,
.glassmorphism-component.dark-theme.card,
.glassmorphism-component.dark-theme.dialog {
  background: rgba(26, 32, 44, 0.7);
}

.glassmorphism-component.dark-theme.button {
  background: rgba(26, 32, 44, 0.8);
}

.glassmorphism-component.dark-theme.button:hover {
  background: rgba(26, 32, 44, 0.9);
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.3);
}

.glassmorphism-component.dark-theme.input {
  background: rgba(26, 32, 44, 0.8);
}

.glassmorphism-component.dark-theme.input:focus-within {
  border-color: rgba(255, 255, 255, 0.2);
  box-shadow: 0 0 0 3px rgba(255, 255, 255, 0.05);
}
</style>