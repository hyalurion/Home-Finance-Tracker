/**
 * useLiquidGlass — Vue composable that turns a component root element into a
 * frosted-glass surface.
 *
 * The WebGL liquidGL engine has been removed from the project. The glass look is
 * now produced entirely by CSS (`backdrop-filter` on the `.v-liquid-glass`
 * host, see styles/effects/liquid-glass.css), so this composable only wires the
 * host class to the component lifecycle and keeps the same public API as before
 * so callers do not need to change.
 *
 * `setOptions()` is kept for API compatibility: the props that used to drive
 * the lens (preset, refraction, tilt, ...) are globals now and cannot be tuned
 * per instance, so it is intentionally a no-op.
 */

import { onMounted } from 'vue'

let _uid = 0
function nextLensId() {
  _uid += 1
  return `lg-${_uid}-${Math.random().toString(36).slice(2, 7)}`
}

/**
 * @param {import('vue').Ref<HTMLElement|null>} targetRef  the pane root element
 * @param {object} [config]  accepted for API compatibility; only `disabled` is used
 */
export function useLiquidGlass(targetRef, config) {
  const id = nextLensId()

  onMounted(() => {
    const el = targetRef.value
    if (!el || (config && config.disabled)) return
    // The global rule in effects/liquid-glass.css paints the frost.
    el.classList.add('v-liquid-glass')
  })

  // No-op: lens parameters are global (CSS custom properties) now.
  function setOptions() {}

  return { id, setOptions }
}
