import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

function source(relativePath) {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8')
}

const viewer = source('../VueBpmnViewer.vue')
const approvalDialog = source(
  '../../views/entity/components/approval/EntityApprovalDialog.vue'
)
const entityDataDialog = source(
  '../../views/entity/components/EntityDataFormDialog.vue'
)

const applyViewportBlock = viewer.slice(
  viewer.indexOf('const applyViewport'),
  viewer.indexOf('const waitForAnimationFrame')
)
const commitViewportBlock = viewer.slice(
  viewer.indexOf('const commitImportedViewport'),
  viewer.indexOf('const importXML')
)
const importBlock = viewer.slice(
  viewer.indexOf('const importXML'),
  viewer.indexOf('const loadXml')
)
const progressWatcherBlock = viewer.slice(
  viewer.indexOf('watch(() => props.progressData'),
  viewer.indexOf('onMounted(() =>')
)

assert.match(
  applyViewportBlock,
  /canvas\.viewbox\(false\)/,
  '流程图视口必须基于未缓存的活动图层流程坐标计算'
)
assert.doesNotMatch(
  applyViewportBlock,
  /svg\.getBBox\(\)/,
  '流程图视口不得混用受 viewport transform 影响的根 SVG 坐标'
)
assert.equal(
  (commitViewportBlock.match(/applyViewport\(\)/g) || []).length,
  1,
  '每次导入只能提交一次最终视口'
)
assert.ok(
  commitViewportBlock.indexOf('clientWidth')
    < commitViewportBlock.indexOf('highlightProcess()')
    && commitViewportBlock.indexOf('highlightProcess()')
      < commitViewportBlock.indexOf('applyViewport()'),
  '节点高亮只能在容器可见后执行，并先于最终视口计算完成'
)
assert.doesNotMatch(
  importBlock,
  /setTimeout\(/,
  '导入后不得通过延迟任务再次改变视口'
)
assert.match(
  viewer,
  /if \(!applyViewport\(\)\) return false[\s\S]*renderReady\.value = true/,
  '容器零尺寸时不得提前显示尚未提交最终视口的画布'
)
assert.match(
  viewer,
  /new ResizeObserver\([\s\S]*commitImportedViewport\(pendingViewportGeneration\)/,
  '隐藏页签恢复尺寸后必须补交待处理视口'
)
assert.match(
  viewer,
  /importQueue = importQueue\.then\(runImport, runImport\)/,
  '同一 Viewer 的 XML 导入必须串行执行'
)
assert.doesNotMatch(
  progressWatcherBlock,
  /applyViewport\(/,
  '流程状态更新不得重置用户当前查看位置'
)
assert.match(
  progressWatcherBlock,
  /currentImport !== importGeneration[\s\S]*!renderReady\.value/,
  '流程状态异步更新必须在卸载或换图后停止'
)
assert.ok(
  viewer.includes(':class="{ \'is-ready\': renderReady }"')
    && viewer.includes('.vue-bpmn-viewer-canvas.is-ready'),
  '流程图必须在最终视口稳定后再显示'
)

for (const [name, dialog] of [
  ['审批弹窗', approvalDialog],
  ['实体查看弹窗', entityDataDialog]
]) {
  assert.match(
    dialog,
    /label="流程图"[\s\S]{0,100}\blazy\b/,
    `${name}必须延迟到流程图页签可见后再创建 Viewer`
  )
  assert.doesNotMatch(
    dialog,
    /bpmnXml\.value\s*=/,
    `${name}切换页签时不得通过清空 XML 强制重建 Viewer`
  )
}

console.log('bpmn viewer stability contract tests passed')
