<template>
  <div ref="layoutRef" class="resizable-tree-layout" :class="{ 'is-resizing': resizing }" :style="{ '--tree-sidebar-width': `${sidebarWidth}px` }">
    <slot name="sidebar" />
    <div
      ref="separatorRef"
      class="tree-layout-resizer"
      role="separator"
      tabindex="0"
      aria-orientation="vertical"
      :aria-label="sidebarLabel"
      :aria-valuemin="MIN_WIDTH"
      :aria-valuemax="maxWidth"
      :aria-valuenow="sidebarWidth"
      title="拖拽调整树宽度，双击恢复默认宽度"
      @pointerdown="startResize"
      @pointermove="resize"
      @pointerup="finishResize"
      @pointercancel="finishResize"
      @lostpointercapture="finishResize"
      @keydown="handleKeydown"
      @dblclick="preferredWidth = DEFAULT_WIDTH"
    />
    <slot />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'

defineProps({ sidebarLabel: { type: String, required: true } })

const DEFAULT_WIDTH = 260
const MIN_WIDTH = 200
const MAX_WIDTH = 600
const CONTENT_MIN_WIDTH = 360
const SEPARATOR_WIDTH = 20
const layoutRef = ref(null)
const separatorRef = ref(null)
// 保留用户选择的宽度，容器暂时缩小时只压缩显示值，恢复空间后可回到原宽度。
const preferredWidth = ref(DEFAULT_WIDTH)
const containerWidth = ref(0)
const stacked = ref(false)
const resizing = ref(false)
const maxWidth = computed(() => Math.max(MIN_WIDTH, Math.min(MAX_WIDTH,
  containerWidth.value - CONTENT_MIN_WIDTH - SEPARATOR_WIDTH)))
const sidebarWidth = computed(() => clampWidth(preferredWidth.value))
let resizeSession = null
let resizeObserver

function clampWidth(width) {
  return Math.round(Math.max(MIN_WIDTH, Math.min(maxWidth.value, width)))
}

/** 随容器变化限制树宽度，为右侧列表留出操作空间；窄屏上下布局禁用拖拽。 */
function updateContainerSize() {
  containerWidth.value = layoutRef.value.getBoundingClientRect().width
  stacked.value = window.matchMedia('(max-width: 1000px)').matches
  if (stacked.value) stopResize()
}

/** 从起点计算位移，使用指针捕获保证移出分隔条或页面后仍能结束拖拽。 */
function startResize(event) {
  if (event.button !== 0 || resizeSession || stacked.value) return
  event.preventDefault()
  event.currentTarget.focus()
  resizeSession = { pointerId: event.pointerId, startX: event.clientX, startWidth: sidebarWidth.value }
  event.currentTarget.setPointerCapture(event.pointerId)
  resizing.value = true
  document.body.classList.add('management-tree-resizing')
}

function resize(event) {
  if (!resizeSession || event.pointerId !== resizeSession.pointerId) return
  preferredWidth.value = clampWidth(resizeSession.startWidth + event.clientX - resizeSession.startX)
}

function finishResize(event) {
  if (event.pointerId === resizeSession?.pointerId) stopResize()
}

/** 页面切换、窗口失焦和拖拽取消都必须恢复全局光标与文字选择。 */
function stopResize() {
  if (!resizeSession) return
  const { pointerId } = resizeSession
  resizeSession = null
  resizing.value = false
  document.body.classList.remove('management-tree-resizing')
  if (separatorRef.value?.hasPointerCapture(pointerId)) separatorRef.value.releasePointerCapture(pointerId)
}

/** 分隔条获得焦点后也可使用方向键调宽，Home / End 跳转到宽度边界。 */
function handleKeydown(event) {
  const widths = { ArrowLeft: sidebarWidth.value - 20, ArrowRight: sidebarWidth.value + 20, Home: MIN_WIDTH, End: maxWidth.value }
  if (!(event.key in widths)) return
  event.preventDefault()
  preferredWidth.value = clampWidth(widths[event.key])
}

onMounted(() => {
  updateContainerSize()
  resizeObserver = new ResizeObserver(updateContainerSize)
  resizeObserver.observe(layoutRef.value)
  window.addEventListener('blur', stopResize)
})
onDeactivated(stopResize)
onBeforeUnmount(() => {
  stopResize()
  resizeObserver?.disconnect()
  window.removeEventListener('blur', stopResize)
})
</script>

<style scoped>
.resizable-tree-layout {
  display: grid;
  grid-template-columns: var(--tree-sidebar-width) 20px minmax(0, 1fr);
  min-width: 0;
}

.tree-layout-resizer {
  position: relative;
  cursor: col-resize;
  touch-action: none;
  outline: none;
}

.tree-layout-resizer::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 9px;
  width: 2px;
  background: var(--el-border-color-light);
  transition: background-color 0.15s;
}

.tree-layout-resizer:hover::before,
.tree-layout-resizer:focus-visible::before,
.is-resizing .tree-layout-resizer::before {
  background: var(--el-color-primary);
}

:global(body.management-tree-resizing),
:global(body.management-tree-resizing *) {
  cursor: col-resize !important;
  user-select: none !important;
}

@media (max-width: 1000px) {
  .resizable-tree-layout {
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
  }

  .tree-layout-resizer {
    display: none;
  }
}
</style>
