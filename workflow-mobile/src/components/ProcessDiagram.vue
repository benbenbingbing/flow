<template>
  <section class="diagram-page">
    <VanNavBar title="完整流程图" left-arrow @click-left="$emit('close')" />
    <div class="diagram-legend"><span><i class="legend-active" />当前办理</span><span><i class="legend-completed" />已完成</span><span><i class="legend-terminated" />已终止</span></div>
    <div v-if="loading" class="diagram-loading"><VanLoading size="22">加载流程图</VanLoading></div>
    <p v-if="error" class="mobile-error" role="alert">{{ error }}</p>
    <div ref="canvas" class="diagram-canvas" />
    <footer class="diagram-footer"><p>拖动查看 · 双指缩放</p><div class="diagram-controls"><VanButton aria-label="缩小" :disabled="loading || Boolean(error)" icon="minus" @click="zoom(.8)" /><span class="diagram-scale">{{ scale }}%</span><VanButton aria-label="放大" :disabled="loading || Boolean(error)" icon="plus" @click="zoom(1.25)" /><span class="controls-divider" /><VanButton aria-label="适应屏幕" :disabled="loading || Boolean(error)" icon="expand-o" @click="fit">适应屏幕</VanButton></div></footer>
  </section>
</template>
<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { Button as VanButton, NavBar as VanNavBar, Loading as VanLoading } from 'vant'
import NavigatedViewer from 'bpmn-js/lib/NavigatedViewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
const props = defineProps({ xml: String, progress: Object })
defineEmits(['close'])
const canvas = ref(), error = ref(''), loading = ref(true), scale = ref(100)
let viewer, disposed = false
onMounted(async () => {
  viewer = new NavigatedViewer({ container: canvas.value })
  viewer.on('canvas.viewbox.changed', ({ viewbox }) => { scale.value = Math.round(viewbox.scale * 100) })
  try {
    await viewer.importXML(props.xml)
    // 弹层可能在异步导入完成前关闭，销毁后不能再读取画布服务。
    if (disposed) return
    const registry = viewer.get('elementRegistry'), renderer = viewer.get('canvas')
    for (const [key, marker] of [['completedNodes', 'mobile-node-completed'], ['activeNodes', 'mobile-node-active'], ['terminatedNodes', 'mobile-node-terminated']]) for (const value of props.progress?.[key] || []) { const id = typeof value === 'object' ? value.nodeId || value.id : value; if (registry.get(id)) renderer.addMarker(id, marker) }
    for (const id of props.progress?.executedSequenceFlows || []) if (registry.get(id)) renderer.addMarker(id, 'mobile-flow-executed')
    fit()
  } catch (cause) { if (!disposed) error.value = cause.message || '流程图加载失败' }
  finally { if (!disposed) loading.value = false }
})
function fit() { viewer?.get('canvas').zoom('fit-viewport', 'auto') }
function zoom(factor) { const service = viewer?.get('canvas'); if (service) service.zoom(Math.min(5, Math.max(.1, service.zoom() * factor))) }
onBeforeUnmount(() => { disposed = true; viewer?.destroy() })
</script>
<style scoped>
/* 仅隐藏移动端查看器的品牌角标，不影响画布节点、连线和交互。 */
.diagram-canvas :deep(.bjs-powered-by) { display: none; }
.diagram-page { height: 100dvh; display: flex; flex-direction: column; background: var(--flow-mobile-background); }.diagram-page :deep(.van-nav-bar__title) { font-size: 16px; }.diagram-legend { display: flex; flex-wrap: wrap; justify-content: center; gap: 20px; padding: 12px 16px; background: var(--flow-mobile-surface); border-bottom: 1px solid var(--flow-mobile-border); font-size: 11px; color: var(--flow-mobile-muted); }.diagram-legend span { display: flex; align-items: center; gap: 5px; }.diagram-legend i { width: 8px; height: 8px; border-radius: 3px; }.legend-active { background: var(--flow-mobile-accent-text); }.legend-completed { background: #d8ebe3; border: 1px solid #6f9f8d; }.legend-terminated { background: #f0d1c9; border: 1px solid #ba6653; }.diagram-loading { position: absolute; inset: 45% 0 auto; z-index: 1; text-align: center; }.diagram-canvas { flex: 1; min-height: 0; touch-action: none; }.diagram-footer { padding: 4px 16px calc(16px + env(safe-area-inset-bottom)); text-align: center; }.diagram-footer > p { color: var(--flow-mobile-muted); font-size: 11px; margin: 6px 0 12px; }.diagram-controls { display: inline-flex; align-items: center; gap: 2px; padding: 3px 5px; border: 1px solid var(--flow-mobile-border); border-radius: 12px; background: var(--flow-mobile-surface); box-shadow: 0 3px 16px #18312e08; }.diagram-controls :deep(.van-button) { border: 0; min-width: 44px; height: 44px; padding: 0 10px; color: var(--flow-mobile-accent-text); font-size: 12px; background: transparent; }.diagram-scale { min-width: 40px; font-size: 12px; font-variant-numeric: tabular-nums; color: var(--flow-mobile-muted); }.controls-divider { height: 18px; width: 1px; background: var(--flow-mobile-border); margin: 0 5px; }
.diagram-canvas :deep(.djs-visual > rect) { rx: 8px; ry: 8px; }.diagram-canvas :deep(.mobile-node-completed .djs-visual > :first-child) { stroke: #6f9f8d !important; fill: #edf6f1 !important; }.diagram-canvas :deep(.mobile-node-active .djs-visual > :first-child) { stroke: var(--flow-mobile-accent-text) !important; fill: var(--flow-mobile-accent-soft) !important; stroke-width: 3px !important; }.diagram-canvas :deep(.mobile-node-terminated .djs-visual > :first-child) { stroke: #ba6653 !important; fill: #fbeee9 !important; }.diagram-canvas :deep(.mobile-flow-executed .djs-visual path) { stroke: #6f9f8d !important; }
</style>
