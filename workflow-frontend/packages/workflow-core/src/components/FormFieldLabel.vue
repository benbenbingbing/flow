<template>
  <span
    ref="root"
    class="form-field-label"
    :class="{ 'is-shrunk': fontSize }"
    :title="truncated ? text : undefined"
    :tabindex="truncated ? 0 : undefined"
    :role="truncated ? 'button' : undefined"
    :aria-expanded="truncated ? expanded : undefined"
    @click="toggleFullLabel"
    @keydown.enter="toggleFullLabel"
    @keydown.space="toggleFullLabel"
  >
    <span
      class="form-field-label__text"
      :class="{ 'is-single-line': !expanded && lineLimit === 1, 'is-two-lines': !expanded && lineLimit === 2 }"
      :style="fontSize && !expanded ? { fontSize: `${fontSize}px` } : undefined"
    >{{ text }}</span>
    <span v-if="lineLimit" ref="probe" class="form-field-label__probe" :class="{ 'is-nowrap': lineLimit === 1 }" aria-hidden="true">{{ text }}</span>
  </span>
</template>

<script setup>
import { computed, inject, ref, unref, watch } from 'vue'
import { FORM_LABEL_OVERFLOW_KEY } from '../shared/config-runtime/index.js'

const props = defineProps({ text: { type: String, required: true } })
const mode = inject(FORM_LABEL_OVERFLOW_KEY, 'wrap')
const lineLimit = computed(() => {
  const current = unref(mode)
  if (current === 'wrap') return 0
  return current.endsWith('-two') ? 2 : 1
})

const root = ref(null)
const probe = ref(null)
const fontSize = ref(null)
const truncated = ref(false)
const expanded = ref(false)
const MIN_FONT_SIZE = 12

/** 在实际标签宽度内测量排版。原字号超出行数后，才尝试逐级缩小。 */
function measureLabel() {
  const element = probe.value
  const width = root.value?.clientWidth
  if (!element || !width) return
  const originalSize = parseFloat(getComputedStyle(root.value).fontSize)
  element.style.fontSize = ''
  const fits = () => lineLimit.value === 1
    ? element.scrollWidth <= width
    : element.getBoundingClientRect().height <= parseFloat(getComputedStyle(element).lineHeight) * 2 + 0.5

  let size = originalSize
  let contentFits = fits()
  while (!contentFits && unref(mode).startsWith('shrink') && size > MIN_FONT_SIZE) {
    size = Math.max(MIN_FONT_SIZE, size - 1)
    element.style.fontSize = `${size}px`
    contentFits = fits()
  }
  fontSize.value = size === originalSize ? null : size
  truncated.value = !contentFits
  if (contentFits) expanded.value = false
}

// 只在限制行数的模式下观察尺寸。画布拖拽、折叠区重新展开和字体加载都会触发重测。
watch([root, probe, () => unref(mode), () => props.text], ([element, measurement], _, onCleanup) => {
  expanded.value = false
  fontSize.value = null
  truncated.value = false
  if (!element || !measurement) return
  const fonts = element.ownerDocument.fonts
  let frame = 0
  const schedule = () => {
    cancelAnimationFrame(frame)
    frame = requestAnimationFrame(measureLabel)
  }
  const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(schedule)
  observer?.observe(element)
  fonts?.addEventListener('loadingdone', schedule)
  schedule()
  onCleanup(() => {
    observer?.disconnect()
    fonts?.removeEventListener('loadingdone', schedule)
    cancelAnimationFrame(frame)
  })
}, { flush: 'post' })

/** 截断标签支持点击或键盘展开，避免把同一次操作转交给关联的输入控件。 */
function toggleFullLabel(event) {
  if (!truncated.value) return
  event.preventDefault()
  event.stopPropagation()
  expanded.value = !expanded.value
}
</script>

<style scoped>
.form-field-label {
  position: relative;
  display: block;
  min-width: 0;
  max-width: 100%;
  line-height: 1.4;
  white-space: normal;
  overflow-wrap: anywhere;
}

.form-field-label[tabindex] { cursor: pointer; }
/* 缩小后继续占用标签的可用宽度，窗口变宽时可重新放大到原字号。 */
.form-field-label.is-shrunk { flex: 1 1 0; }
.form-field-label__text { display: block; }
.form-field-label__text.is-single-line {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.form-field-label__text.is-two-lines {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}
.form-field-label__probe {
  position: absolute;
  inset: 0 auto auto 0;
  width: 100%;
  overflow: hidden;
  visibility: hidden;
  pointer-events: none;
}
.form-field-label__probe.is-nowrap { white-space: nowrap; }
</style>
