<template>
  <section class="diagram-page">
    <VanNavBar title="完整流程图" left-arrow @click-left="$emit('close')" />
    <div class="diagram-legend"><span><i class="legend-active" />当前办理</span><span><i class="legend-completed" />已完成</span><span><i class="legend-terminated" />已终止</span></div>
    <div v-if="loading" class="diagram-loading"><VanLoading size="22">加载流程图</VanLoading></div>
    <p v-if="error" class="mobile-error" role="alert">{{ error }}</p>
    <div ref="viewport" class="diagram-viewport" @pointerdown.prevent="startPointer" @pointermove.prevent="movePointer" @pointerup="endPointer" @pointercancel="endPointer" @lostpointercapture="endPointer" @wheel.prevent="wheel">
      <div ref="canvas" class="diagram-canvas" :style="canvasStyle" />
    </div>
    <footer class="diagram-footer"><p>拖动查看 · 双指缩放</p><div class="diagram-controls"><VanButton aria-label="缩小" :disabled="loading || Boolean(error)" icon="minus" @click="zoom(.8)" /><span class="diagram-scale">{{ scale }}%</span><VanButton aria-label="放大" :disabled="loading || Boolean(error)" icon="plus" @click="zoom(1.25)" /><span class="controls-divider" /><VanButton aria-label="适应屏幕" :disabled="loading || Boolean(error)" icon="expand-o" @click="fit">适应屏幕</VanButton><VanButton aria-label="旋转流程图" :aria-pressed="rotated" :disabled="loading || Boolean(error)" icon="replay" @click="rotate">旋转</VanButton></div></footer>
  </section>
</template>
<script setup>
import { getProcessNodeStatus } from '@flow/workflow-core/process-progress'
import { computed, nextTick, onMounted, onBeforeUnmount, ref } from 'vue'
import { Button as VanButton, NavBar as VanNavBar, Loading as VanLoading } from 'vant'
import Viewer from 'bpmn-js/lib/Viewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
const props = defineProps({ xml: String, progress: Object })
defineEmits(['close'])
const viewport = ref(), canvas = ref(), error = ref(''), loading = ref(true), scale = ref(100), rotated = ref(false)
const size = ref({ width: 0, height: 0 }), pointers = new Map()
const canvasStyle = computed(() => ({ width: `${rotated.value ? size.value.height : size.value.width}px`, height: `${rotated.value ? size.value.width : size.value.height}px`, transform: `translate(-50%, -50%) rotate(${rotated.value ? 90 : 0}deg)` }))
let viewer, resizeObserver, disposed = false
onMounted(async () => {
  await resizeCanvas()
  if (disposed) return
  // 使用同一套屏幕坐标映射处理触摸和鼠标，避免默认拖动在旋转后方向错位。
  viewer = new Viewer({ container: canvas.value })
  resizeObserver = new ResizeObserver(resizeCanvas)
  resizeObserver.observe(viewport.value)
  viewer.on('canvas.viewbox.changed', ({ viewbox }) => { scale.value = Math.round(viewbox.scale * 100) })
  try {
    await viewer.importXML(props.xml)
    // 弹层可能在异步导入完成前关闭，销毁后不能再读取画布服务。
    if (disposed) return
    const registry = viewer.get('elementRegistry'), renderer = viewer.get('canvas')
    // 一个节点只保留一种状态标记，回退重审时不叠加历史完成/取消样式。
    for (const element of registry.getAll()) {
      const status = getProcessNodeStatus(props.progress, element.id)
      if (status !== 'pending') renderer.addMarker(element.id, `mobile-node-${status}`)
    }
    for (const id of props.progress?.executedSequenceFlows || []) if (registry.get(id)) renderer.addMarker(id, 'mobile-flow-executed')
    fit()
  } catch (cause) { if (!disposed) error.value = cause.message || '流程图加载失败' }
  finally { if (!disposed) loading.value = false }
})
/** 弹层隐藏阶段的零尺寸会使 fit-viewport 产生无效比例，等待 ResizeObserver 通知可见尺寸后再适配。 */
function fit() {
  if (disposed || !viewer) return
  const service = viewer.get('canvas'), bounds = service.getSize()
  if (!(bounds.width > 0 && bounds.height > 0)) return
  // 导入过程中可能缓存过隐藏时的视口，适配前必须重新读取实际尺寸。
  service.resized()
  service.zoom('fit-viewport', 'auto')
}
function zoom(factor, center) {
  if (disposed || !viewer || !Number.isFinite(factor) || factor <= 0) return
  const service = viewer.get('canvas'), bounds = service.getSize(), current = service.zoom()
  if (bounds.width > 0 && bounds.height > 0 && Number.isFinite(current) && current > 0) service.zoom(Math.min(5, Math.max(.1, current * factor)), center)
}
/** 交换画布宽高后再适配，使宽流程使用竖屏的长边；容器变化也覆盖手机横竖屏切换。 */
async function resizeCanvas() {
  // 子组件挂载时，父弹层的 v-show 可能尚未恢复；等待本轮 DOM 更新完成后再测量。
  await nextTick()
  if (disposed || !viewport.value) return
  const width = viewport.value.clientWidth, height = viewport.value.clientHeight
  if (!(width > 0 && height > 0)) return
  size.value = { width, height }
  pointers.clear()
  await nextTick()
  if (disposed || !viewer) return
  viewer.get('canvas').resized()
  if (!loading.value && !error.value) fit()
}
function rotate() { rotated.value = !rotated.value; return resizeCanvas() }
/** SVG 屏幕矩阵包含 CSS 旋转，将手势坐标还原到画布，保证平移方向和缩放中心跟手。 */
function canvasPoint(clientX, clientY) {
  const matrix = canvas.value?.querySelector('.djs-container > svg')?.getScreenCTM()
  return matrix ? new DOMPoint(clientX, clientY).matrixTransform(matrix.inverse()) : null
}
function startPointer(event) {
  if (loading.value || error.value || pointers.size >= 2 || (event.pointerType === 'mouse' && event.button !== 0)) return
  const point = canvasPoint(event.clientX, event.clientY)
  if (!point) return
  pointers.set(event.pointerId, point)
  viewport.value.setPointerCapture(event.pointerId)
}
function gesture() {
  const [first, second = first] = [...pointers.values()]
  return { center: { x: (first.x + second.x) / 2, y: (first.y + second.y) / 2 }, distance: Math.hypot(first.x - second.x, first.y - second.y) }
}
/** 双指同时移动和缩放时，先平移到新中点，再以该点缩放，避免画布跳动。 */
function movePointer(event) {
  if (!pointers.has(event.pointerId) || !viewer) return
  const point = canvasPoint(event.clientX, event.clientY)
  if (!point) return
  const previous = gesture()
  pointers.set(event.pointerId, point)
  const current = gesture()
  viewer.get('canvas').scroll({ dx: current.center.x - previous.center.x, dy: current.center.y - previous.center.y })
  if (previous.distance > 0 && current.distance > 0) zoom(current.distance / previous.distance, current.center)
}
function endPointer(event) {
  pointers.delete(event.pointerId)
  if (viewport.value?.hasPointerCapture(event.pointerId)) viewport.value.releasePointerCapture(event.pointerId)
}
/** 桌面模拟手机时保留滚轮平移与触控板缩放，并使用相同的旋转坐标转换。 */
function wheel(event) {
  if (loading.value || error.value || !viewer) return
  const center = canvasPoint(event.clientX, event.clientY)
  if (!center) return
  const unit = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? size.value.height : 1
  if (event.ctrlKey || event.metaKey) { zoom(Math.exp(-event.deltaY * unit * .01), center); return }
  const deltaX = (event.shiftKey ? event.deltaY : event.deltaX) * unit, deltaY = (event.shiftKey ? 0 : event.deltaY) * unit
  const target = canvasPoint(event.clientX - deltaX, event.clientY - deltaY)
  if (target) viewer.get('canvas').scroll({ dx: target.x - center.x, dy: target.y - center.y })
}
onBeforeUnmount(() => { disposed = true; resizeObserver?.disconnect(); pointers.clear(); viewer?.destroy() })
</script>
<style scoped>
/* 仅隐藏移动端查看器的品牌角标，不影响画布节点、连线和交互。 */
.diagram-canvas :deep(.bjs-powered-by) { display: none; }
.diagram-page { height: 100dvh; display: flex; flex-direction: column; background: var(--flow-mobile-background); }.diagram-page :deep(.van-nav-bar__title) { font-size: 16px; }.diagram-legend { display: flex; flex-wrap: wrap; justify-content: center; gap: 20px; padding: 12px 16px; background: var(--flow-mobile-surface); border-bottom: 1px solid var(--flow-mobile-border); font-size: 11px; color: var(--flow-mobile-muted); }.diagram-legend span { display: flex; align-items: center; gap: 5px; }.diagram-legend i { width: 8px; height: 8px; border-radius: 3px; }.legend-active { background: var(--flow-mobile-accent-text); }.legend-completed { background: #d8ebe3; border: 1px solid #6f9f8d; }.legend-terminated { background: #f0d1c9; border: 1px solid #ba6653; }.diagram-loading { position: absolute; inset: 45% 0 auto; z-index: 1; text-align: center; }.diagram-viewport { position: relative; flex: 1; min-height: 0; overflow: hidden; touch-action: none; }.diagram-canvas { position: absolute; left: 50%; top: 50%; touch-action: none; }.diagram-footer { padding: 4px 16px calc(16px + env(safe-area-inset-bottom)); text-align: center; }.diagram-footer > p { color: var(--flow-mobile-muted); font-size: 11px; margin: 6px 0 12px; }.diagram-controls { display: inline-flex; max-width: 100%; flex-wrap: wrap; justify-content: center; align-items: center; gap: 2px; padding: 3px 5px; border: 1px solid var(--flow-mobile-border); border-radius: 12px; background: var(--flow-mobile-surface); box-shadow: 0 3px 16px #18312e08; }.diagram-controls :deep(.van-button) { flex-shrink: 0; border: 0; min-width: 44px; height: 44px; padding: 0 10px; color: var(--flow-mobile-accent-text); font-size: 12px; background: transparent; }.diagram-scale { min-width: 40px; font-size: 12px; font-variant-numeric: tabular-nums; color: var(--flow-mobile-muted); }.controls-divider { height: 18px; width: 1px; background: var(--flow-mobile-border); margin: 0 5px; }
.diagram-canvas :deep(.djs-visual > rect) { rx: 8px; ry: 8px; }.diagram-canvas :deep(.mobile-node-completed .djs-visual > :first-child) { stroke: #6f9f8d !important; fill: #edf6f1 !important; }.diagram-canvas :deep(.mobile-node-active .djs-visual > :first-child) { stroke: var(--flow-mobile-accent-text) !important; fill: var(--flow-mobile-accent-soft) !important; stroke-width: 3px !important; }.diagram-canvas :deep(.mobile-node-terminated .djs-visual > :first-child) { stroke: #ba6653 !important; fill: #fbeee9 !important; }.diagram-canvas :deep(.mobile-flow-executed .djs-visual path) { stroke: #6f9f8d !important; }
</style>
