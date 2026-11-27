import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const processDesignSource = readFileSync(new URL(
  '../../views/ProcessDesign.vue',
  import.meta.url
), 'utf8')
const nodeConfigPanelSource = readFileSync(new URL(
  '../../components/NodeConfigPanel.vue',
  import.meta.url
), 'utf8')

const desktopPanelStyle = processDesignSource.match(/\.node-config-panel\s*\{([\s\S]*?)\}/)?.[1] || ''
assert.match(
  desktopPanelStyle,
  /width:\s*33\.333vw\s*;/,
  '桌面端节点配置侧栏应约占视口宽度的三分之一'
)
assert.match(
  processDesignSource,
  /@media \(max-width:\s*900px\)[\s\S]*?\.node-config-panel\s*\{[\s\S]*?width:\s*100%\s*;[\s\S]*?min-width:\s*0\s*;/,
  '窄屏节点配置侧栏应继续使用全屏覆盖，避免三分之一宽度挤压配置项'
)

const multiInstanceStart = nodeConfigPanelSource.indexOf('title="会签规则"')
const multiInstanceEnd = nodeConfigPanelSource.indexOf('<NextApproverConfigEditor', multiInstanceStart)
assert.ok(multiInstanceStart >= 0 && multiInstanceEnd > multiInstanceStart, '办理人配置应包含会签规则分组')
const multiInstanceSource = nodeConfigPanelSource.slice(multiInstanceStart, multiInstanceEnd)

const operationPermissionStart = nodeConfigPanelSource.indexOf('title="操作权限"')
const operationPermissionEnd = nodeConfigPanelSource.indexOf('</section>', operationPermissionStart)
assert.ok(
  operationPermissionStart > multiInstanceEnd && operationPermissionEnd > operationPermissionStart,
  '操作权限必须是独立区块，不能继续放在多人办理配置内'
)
assert.ok(nodeConfigPanelSource.slice(operationPermissionStart, operationPermissionEnd).includes('<NodeOperationPermissions v-model="assigneeForm"'))
const operationPermissionSource = readFileSync(new URL('../../components/NodeOperationPermissions.vue', import.meta.url), 'utf8')
assert.equal((operationPermissionSource.match(/<el-switch\b/g) || []).length, 4)
for (const field of ['allowTransfer', 'allowAddSign', 'allowTerminate', 'allowWithdraw']) {
  assert.ok(operationPermissionSource.includes(`modelValue.${field}`))
}
assert.match(operationPermissionSource, /grid-template-columns: repeat\(2, minmax\(0, 1fr\)\)/)
assert.equal((operationPermissionSource.match(/<ConfigHelpLabel /g) || []).length, 2)
assert.ok(!operationPermissionSource.includes('class="form-tip"'))
assert.equal(
  nodeConfigPanelSource.includes('NodeOperationMatrixEditor'),
  false,
  '节点配置面板不得继续加载节点操作矩阵组件'
)
assert.equal(
  nodeConfigPanelSource.includes('nodeOperationPolicy'),
  true,
  '节点配置面板必须识别并清理早期独立矩阵属性'
)
assert.ok(
  nodeConfigPanelSource.includes('extProps.nodeOperationPolicy')
    && nodeConfigPanelSource.includes("updateExtensionProperty('nodeOperationPolicy', null)"),
  '节点配置面板必须安全折叠并删除早期独立矩阵属性'
)
for (const permission of ['allowTransfer', 'allowAddSign', 'allowTerminate', 'allowWithdraw']) {
  assert.ok(
    nodeConfigPanelSource.includes(`${permission}: operationPermissions.${permission}`),
    `加载节点配置时必须显式回填 ${permission}`
  )
}

for (const label of ['抢占', '顺序', '会签']) {
  assert.ok(nodeConfigPanelSource.includes(`>${label}</el-radio-button>`))
}
for (const label of ['启用多实例', '任务分配', '多人命中', '集合变量', '元素变量']) {
  assert.ok(!nodeConfigPanelSource.includes(`label="${label}"`))
}
assert.match(multiInstanceSource, /v-model="assigneeForm.multiInstanceCompletionRate"/)
assert.match(multiInstanceSource, /v-model="assigneeForm.multiInstanceNeedAllApprovers"/)
assert.match(nodeConfigPanelSource, /:ordered="assigneeForm.handlingMode === 'SEQUENTIAL'"/)

console.log('node config layout contract passed')
