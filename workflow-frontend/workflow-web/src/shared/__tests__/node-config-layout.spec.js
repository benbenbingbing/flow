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
const multiInstanceEnd = nodeConfigPanelSource.indexOf('</SettingsSection>', multiInstanceStart)
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

const handlingModeOptions = nodeConfigPanelSource.matchAll(
  /<el-radio-button value="(?:CLAIM|COUNTERSIGN|SEQUENTIAL)">([^<]+)<\/el-radio-button>/g
)
assert.deepEqual([...handlingModeOptions].map(([, label]) => label), ['抢占', '会签', '顺序'])
for (const label of ['启用多实例', '任务分配', '多人命中', '集合变量', '元素变量']) {
  assert.ok(!nodeConfigPanelSource.includes(`label="${label}"`))
}
assert.match(multiInstanceSource, /v-model="assigneeForm.multiInstanceCompletionRate"/)
assert.match(multiInstanceSource, /v-model="assigneeForm.multiInstanceNeedAllApprovers"/)
assert.match(nodeConfigPanelSource, /:ordered="assigneeForm.handlingMode === 'SEQUENTIAL'"/)

const lastBasicSection = nodeConfigPanelSource.lastIndexOf("activeTab === 'basic'")
const lastBasicSectionEnd = nodeConfigPanelSource.indexOf('</section>', lastBasicSection)
const firstBasicSection = nodeConfigPanelSource.indexOf('<section v-show="activeTab')
const basicSections = nodeConfigPanelSource.slice(firstBasicSection, lastBasicSectionEnd)
assert.doesNotMatch(basicSections, /:collapsible="false"/, '常用各分组都应允许折叠')
const defaultExpandedGroups = [...basicSections.matchAll(
  /<SettingsSection\b[^>]*title="([^"]+)"[^>]*:default-expanded="([^"]+)"/g
)].map(([, title, expanded]) => [title, expanded])
assert.deepEqual(defaultExpandedGroups, [['基本配置', 'true']], '常用仅基本配置默认展开')
const nextApproverEditorSource = readFileSync(new URL('../../components/NextApproverConfigEditor.vue', import.meta.url), 'utf8')
assert.doesNotMatch(nextApproverEditorSource, /:default-expanded=/, '已配置展示时下一节点审批人也应默认折叠')
assert.ok(lastBasicSection > nodeConfigPanelSource.indexOf('title="审批设置"'))
assert.match(
  nodeConfigPanelSource.slice(lastBasicSection, lastBasicSectionEnd),
  /<NextApproverConfigEditor\b/,
  '下一节点审批人应作为常用页签的最后一个独立分组'
)

console.log('node config layout contract passed')
