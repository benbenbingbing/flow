import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import {
  CONFIG_FIELD_HELP,
  getConfigFieldHelp
} from '../config-field-help.js'

const root = process.cwd()
const files = [
  'src/views/EntityDesign.vue',
  'src/views/EntityListConfigDesign.vue',
  'src/components/UiConfigPublishDialog.vue',
  'src/components/NodeConfigPanel.vue',
  'src/components/FlowActionConfigPanel.vue',
  'src/components/ui-config/EventBindingEditor.vue',
  'src/components/ui-config/InterfaceServiceEditorDialog.vue',
  'src/components/ui-config/InterfaceServiceTestDialog.vue',
  'src/views/system/EntityVersionManagement.vue',
  'src/views/system/components/EntityVersionConfigDialogs.vue',
  'src/views/system/WorkCalendarManagement.vue',
  'src/views/system/embed-management/EmbedViewWorkspace.vue',
  'src/views/system/embed-management/EmbedViewDraftPanel.vue',
  'src/views/system/embed-management/EmbedGrantPanel.vue',
  'src/views/system/embed-management/EmbedProviderPanel.vue',
  'src/views/system/embed-management/EmbedBindingPanel.vue',
  'src/views/system/embed-management/EmbedOperationsWorkspace.vue',
  'src/components/form-designer/FormNodeDataSettings.vue',
  'src/components/ui-config/FormDataSourceDialog.vue',
  'src/components/ui-config/ListColumnTemplateEditorDialog.vue',
  'src/components/NextApproverConfigEditor.vue',
  'src/components/ui-config/EntitySelectionMappingEditor.vue',
  'src/components/ActionRuleEditorDialog.vue'
]

for (const [key, content] of Object.entries(CONFIG_FIELD_HELP)) {
  assert.ok(content.length >= 20, `配置帮助过短: ${key}`)
  assert.equal(getConfigFieldHelp(key), content)
}

const usedKeys = new Set()
for (const file of files) {
  const source = readFileSync(path.join(root, file), 'utf8')
  for (const match of source.matchAll(/<ConfigHelpLabel[\s\S]*?help-key="([^"]+)"[\s\S]*?\/>/g)) {
    usedKeys.add(match[1])
  }
}

for (const key of usedKeys) {
  assert.ok(CONFIG_FIELD_HELP[key], `页面引用了不存在的配置帮助: ${key}`)
}

for (const required of [
  'entityList.dataScopeMode',
  'uiConfig.releaseMode',
  'process.allowManualCc',
  'process.personResolver',
  'process.flowActionHandler',
  'uiEvent.inheritanceMode',
  'uiDataSource.service',
  'interfaceService.backendImplementation',
  'interfaceService.debugService',
  'interfaceService.debugOperation',
  'interfaceService.debugBusinessContext',
  'interfaceService.debugConfigObject',
  'interfaceService.debugUsage',
  'interfaceService.debugInput',
  'interfaceService.debugResult',
  'entityList.dataSourceType',
  'entityVersion.enabled',
  'entityVersion.triggerType',
  'entityVersion.businessIntents',
  'entityVersion.triggerCondition',
  'entityVersion.triggerPriority',
  'entityVersion.scopeFields',
  'entityVersion.scopeRelation',
  'entityVersion.scopeFilter',
  'entityVersion.scopeMaxRows',
  'entityVersion.scopeLimits',
  'entityVersion.diffChangedOnly',
  'entityVersion.diffTrackOrder',
  'entityVersion.diffIgnoredFields',
  'entityVersion.stepImplementation',
  'entityVersion.targetResolver',
  'entityVersion.applyStrategy',
  'embed.application.internalId',
  'embed.view.entityCode',
  'embed.view.listKey',
  'embed.view.defaultFormId',
  'embed.provider.type',
  'embed.binding.externalSubject',
  'embed.operations.queryScope'
]) {
  assert.ok(usedKeys.has(required), `关键复杂配置缺少问号帮助: ${required}`)
}

const interfaceServiceTestDialogSource = readFileSync(
  path.join(root, 'src/components/ui-config/InterfaceServiceTestDialog.vue'),
  'utf8'
)
const debugHelpFields = Object.freeze({
  '接口服务': 'interfaceService.debugService',
  '操作': 'interfaceService.debugOperation',
  '业务上下文': 'interfaceService.debugBusinessContext',
  '配置对象': 'interfaceService.debugConfigObject',
  '调用用途': 'interfaceService.debugUsage',
  '输入参数': 'interfaceService.debugInput',
  '执行结果': 'interfaceService.debugResult'
})
const debugHelpTags = interfaceServiceTestDialogSource.match(
  /<ConfigHelpLabel\b[^>]*\/>/g
) || []

assert.equal(debugHelpTags.length, Object.keys(debugHelpFields).length,
  '调试接口操作的每个字段都应提供一个问号帮助')
const groupedDebugFormItems = interfaceServiceTestDialogSource.match(
  /<el-form-item\b[^>]*\bfor=""/g
) || []
assert.equal(groupedDebugFormItems.length, Object.keys(debugHelpFields).length,
  '带问号帮助的调试字段应使用 ARIA group，避免把帮助按钮嵌入原生 label')
for (const [label, key] of Object.entries(debugHelpFields)) {
  const tag = debugHelpTags.find(item =>
    item.includes(`label="${label}"`) && item.includes(`help-key="${key}"`))
  assert.ok(tag, `调试接口操作字段缺少问号帮助: ${label}`)
  for (const section of ['含义：', '使用方法：', '适用场景：']) {
    assert.ok(CONFIG_FIELD_HELP[key].includes(section),
      `${label}帮助缺少“${section}”说明`)
  }
}
for (const label of Object.keys(debugHelpFields).filter(item => item !== '执行结果')) {
  assert.ok(interfaceServiceTestDialogSource.includes(`aria-label="${label}"`),
    `调试接口操作控件缺少无障碍名称: ${label}`)
}

console.log(`config field help audit passed: ${usedKeys.size} usages, ${Object.keys(CONFIG_FIELD_HELP).length} definitions`)
