<template>
  <div
    class="form-node-grid-resize-handle"
    :class="{ 'is-resizing': gesture }"
    role="slider"
    :tabindex="disabled ? -1 : 0"
    :aria-label="`${label}栅格宽度`"
    :aria-disabled="disabled"
    :aria-valuenow="span"
    :aria-valuetext="`${span} 格`"
    aria-valuemin="1"
    aria-valuemax="24"
    title="拖拽调整栅格宽度"
    @pointerdown.stop.prevent="startResize"
    @pointermove="previewResize"
    @pointerup="finishResize"
    @pointercancel="cancelResize"
    @lostpointercapture="cancelResize"
    @keydown="resizeByKey"
    @click.stop
    @dblclick.stop
  >
    <span v-if="gesture" class="grid-resize-value" aria-hidden="true">{{ span }} 格</span>
  </div>
</template>

<script setup>
import { onBeforeUnmount, ref } from 'vue'

const props = defineProps({
  label: { type: String, required: true },
  span: { type: Number, required: true },
  disabled: { type: Boolean, default: false },
  begin: { type: Function, required: true }
})

const emit = defineEmits(['preview', 'finish'])
const gesture = ref(null)

/** 按直接所属的布局行测量单格宽度；嵌套 GRID 使用包含 gutter 的实际行宽。 */
function startResize(event) {
  if (props.disabled || gesture.value || event.button !== 0 || !event.isPrimary) return
  const handle = event.currentTarget
  const row = handle.closest('.form-node-drop-zone')
  if (!row) return
  const { paddingLeft, paddingRight } = getComputedStyle(row)
  const rowWidth = row.clientWidth - parseFloat(paddingLeft) - parseFloat(paddingRight)
  if (rowWidth <= 0 || !props.begin()) return

  gesture.value = {
    pointerId: event.pointerId,
    startX: event.clientX,
    startSpan: props.span,
    columnWidth: rowWidth / 24
  }
  // 宽度变化会让节点换行。捕获指针后，即使手柄移位或鼠标离开画布也能完成拖拽。
  handle.setPointerCapture(event.pointerId)
  handle.focus({ preventScroll: true })
}

function clampSpan(span) {
  return Math.max(1, Math.min(24, span))
}

function spanAt(clientX) {
  const { startX, startSpan, columnWidth } = gesture.value
  return clampSpan(startSpan + Math.round((clientX - startX) / columnWidth))
}

function previewResize(event) {
  if (event.pointerId === gesture.value?.pointerId) emit('preview', spanAt(event.clientX))
}

function finishResize(event) {
  if (event.pointerId === gesture.value?.pointerId) endResize(spanAt(event.clientX))
}

function cancelResize(event) {
  if (event.pointerId === gesture.value?.pointerId) endResize(null)
}

function endResize(span) {
  gesture.value = null
  emit('finish', span)
}

/** 让聚焦的手柄支持按格调整，以及直接跳到最窄或最宽。 */
function resizeByKey(event) {
  const spans = {
    ArrowLeft: props.span - 1,
    ArrowDown: props.span - 1,
    ArrowRight: props.span + 1,
    ArrowUp: props.span + 1,
    Home: 1,
    End: 24
  }
  const span = spans[event.key]
  if (typeof span !== 'number') return
  event.preventDefault()
  event.stopPropagation()
  if (!props.disabled && !gesture.value && props.begin()) emit('finish', clampSpan(span))
}

onBeforeUnmount(() => {
  if (gesture.value) endResize(null)
})
</script>

<style scoped>
.form-node-grid-resize-handle {
  position: absolute;
  z-index: 2;
  right: 2px;
  bottom: 4px;
  display: grid;
  width: 18px;
  height: 24px;
  place-items: center;
  cursor: ew-resize;
  touch-action: none;
  user-select: none;
  opacity: 0;
}

.form-node-grid-resize-handle::before {
  width: 6px;
  height: 18px;
  border: 1px solid var(--el-bg-color);
  border-radius: 4px;
  background: var(--el-color-primary);
  content: '';
}

.form-node-grid-resize-handle:hover,
.form-node-grid-resize-handle:focus-visible,
.form-node-grid-resize-handle.is-resizing {
  opacity: 1;
}

.form-node-grid-resize-handle.is-resizing {
  z-index: 4;
}

.form-node-grid-resize-handle[aria-disabled='true'] {
  cursor: wait;
}

.grid-resize-value {
  position: absolute;
  right: 100%;
  padding: 2px 6px;
  border-radius: 4px;
  background: var(--el-color-primary);
  color: var(--el-color-white);
  font-size: 12px;
  white-space: nowrap;
  pointer-events: none;
}
</style>
