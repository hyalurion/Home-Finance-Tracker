<template>
  <div
    ref="rootRef"
    :class="['glassmorphism-component', type]"
    :style="rootStyle"
  >
    <slot />
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useLiquidGlass } from '@/composables/useLiquidGlass.js'

const props = defineProps({
  type: {
    type: String,
    default: 'base', // base | button | input | card | dialog
  },
  width: { type: String, default: 'auto' },
  height: { type: String, default: 'auto' },
  borderRadius: { type: String, default: '12px' },
  padding: { type: String, default: '16px' },
  zIndex: { type: Number, default: 10 },
})

const rootRef = ref(null)

// Registers the root as a `.v-liquid-glass` host; the frost itself is painted
// by the global CSS rule (styles/effects/liquid-glass.css).
useLiquidGlass(rootRef)

const rootStyle = computed(() => ({
  width: props.width,
  height: props.height,
  borderRadius: props.borderRadius,
  padding: props.padding,
  zIndex: props.zIndex,
}))
</script>

<style scoped src="../styles/components/GlassmorphismComponent.css"></style>
