<template>
  <div class="custom-progress">
    <div class="progress-bar-outer">
      <div 
        class="progress-bar-inner" 
        :style="{
          width: `${percentage}%`,
          backgroundColor: color
        }"
      ></div>
    </div>
  </div>
</template>

<script setup>

const props = defineProps({
  percentage: {
    type: Number,
    default: 0,
    validator: (value) => value >= 0 && value <= 100
  },
  color: {
    type: String,
    default: '#4361ee'
  },
  strokeWidth: {
    type: Number,
    default: 12
  }
})
</script>

<style scoped>
.custom-progress {
  width: 100%;
  height: 20px;
  display: flex;
  align-items: center;
}

.progress-bar-outer {
  width: 100%;
  height: var(--stroke-width, 12px);
  background-color: rgba(75, 85, 99, 0.3);
  border-radius: 100px;
  overflow: hidden;
}

.progress-bar-inner {
  height: 100%;
  border-radius: 100px;
  transition: width 0.3s ease;
}

/* Dark theme styles */
@media (prefers-color-scheme: dark) {
.custom-progress .progress-bar-outer {
  background-color: rgba(75, 85, 99, 0.5);
}
}
</style>