<template>
  <form
    :class="['glass-form']"
    @submit.prevent="handleSubmit"
  >
    <slot></slot>
  </form>
</template>

<script setup>
const props = defineProps({
  model: {
    type: Object,
    default: () => ({})
  },
})

const emit = defineEmits(['submit'])

const handleSubmit = () => {
  emit('submit')
}
</script>

<style scoped>
.glass-form {
  width: 100%;
  padding: 20px;
  border-radius: 12px;
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.1);
}

/* Dark theme */
@media (prefers-color-scheme: dark) {
.glass-form {
  background: rgba(26, 32, 44, 0.5);
  border-color: rgba(255, 255, 255, 0.1);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
}
</style>