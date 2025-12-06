<template>
  <div :class="['glass-card', { 'dark-theme': darkTheme }]">
    <div v-if="title" class="glass-card-title">
      <slot name="title">{{ title }}</slot>
    </div>
    <div class="glass-card-content">
      <slot></slot>
    </div>
    <div v-if="$slots.footer" class="glass-card-footer">
      <slot name="footer"></slot>
    </div>
  </div>
</template>

<script setup>
const props = defineProps({
  title: {
    type: String,
    default: '',
  },
  darkTheme: {
    type: Boolean,
    default: false,
  },
})
</script>

<style scoped>
.glass-card {
  position: relative;
  border-radius: 12px;
  padding: 20px;
  transition: all 0.3s ease;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.1);
  background: rgba(255, 255, 255, 0.7);
}

.glass-card::before {
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

.glass-card:hover {
  box-shadow: 0 12px 48px rgba(31, 38, 135, 0.15);
  transform: translateY(-2px);
}

.glass-card-title {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 16px;
  color: #1a202c;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.3);
}

.glass-card-content {
  color: #4a5568;
}

.glass-card-footer {
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.3);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

/* Dark theme */
.glass-card.dark-theme {
  background: rgba(26, 32, 44, 0.7);
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}

.glass-card.dark-theme::before {
  background: rgba(0, 0, 0, 0.1);
}

.glass-card.dark-theme .glass-card-title {
  color: #e2e8f0;
  border-bottom-color: rgba(255, 255, 255, 0.1);
}

.glass-card.dark-theme .glass-card-content {
  color: #cbd5e0;
}

.glass-card.dark-theme .glass-card-footer {
  border-top-color: rgba(255, 255, 255, 0.1);
}
</style>