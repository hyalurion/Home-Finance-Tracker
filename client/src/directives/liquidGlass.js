/**
 * v-liquid-glass — Vue directive that upgrades any element into a frosted-glass
 * surface.
 *
 * Usage:
 *   <div v-liquid-glass>                                  // glass surface
 *   <div v-liquid-glass="{ disabled: true }">             // plain surface
 *
 * The WebGL liquidGL engine has been removed. The directive now only tags the
 * element with the `.v-liquid-glass` class; the blur/frost is rendered by the
 * global rule in styles/effects/liquid-glass.css. Element stays interactive,
 * so containers (cards, dialogs, toasts, forms) and controls (buttons, inputs,
 * switches) all keep working.
 *
 * The directive is kept as a thin class hook instead of inlining the class in
 * templates so every glass surface stays declarative and in one place.
 */

export const liquidGlassDirective = {
  mounted(el, binding) {
    if (binding.value && binding.value.disabled) return
    el.classList.add('v-liquid-glass')
  },
}

export default liquidGlassDirective
