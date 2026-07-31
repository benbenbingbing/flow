import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import {
  JSON_CONFIG_HELP,
  buildSchemaJsonHelp,
  getJsonConfigHelp
} from '../json-config-help.js'
import { applySelectionReturnMappings } from '../../utils/selectionReturnMappings.ts'

const root = process.cwd()

for (const [key, help] of Object.entries(JSON_CONFIG_HELP)) {
  assert.ok(help.title, `${key} 缺少标题`)
  assert.ok(help.summary, `${key} 缺少说明`)
  assert.ok(
    ['object', 'array', 'object-or-array'].includes(help.shape),
    `${key} 的 JSON 形态无效`
  )
  const parsed = JSON.parse(JSON.stringify(help.example))
  if (help.shape === 'object') {
    assert.equal(
      parsed != null && typeof parsed === 'object' && !Array.isArray(parsed),
      true,
      `${key} 示例必须是对象`
    )
  }
  if (help.shape === 'array') {
    assert.equal(Array.isArray(parsed), true, `${key} 示例必须是数组`)
  }
  if (help.shape === 'object-or-array') {
    assert.equal(
      parsed != null && typeof parsed === 'object',
      true,
      `${key} 示例必须是对象或数组`
    )
  }
}

const selectionHelp = getJsonConfigHelp(
  'entityList.selectionReturnMappings'
)
const selectionResult = applySelectionReturnMappings(
  {
    id: 'customer-1',
    data: { customer_name: '示例客户' }
  },
  selectionHelp.example
)
assert.deepEqual(selectionResult.selectionData, {
  customerId: 'customer-1',
  customerName: '示例客户'
})

const genericArrayHelp = buildSchemaJsonHelp({
  label: '级联选项',
  jsonShape: 'array',
  example: [{ value: 'a', label: 'A' }]
})
assert.equal(genericArrayHelp.shape, 'array')
assert.deepEqual(genericArrayHelp.example, [{ value: 'a', label: 'A' }])

const usages = [
  ['src/views/EntityListConfigDesign.vue', 'configInfo.selectionReturnMappingsText', 'entityList.selectionReturnMappings'],
  ['src/views/EntityListConfigDesign.vue', 'configInfo.fixedFilterConfig', 'entityList.fixedFilters'],
  ['src/views/EntityListConfigDesign.vue', 'configInfo.contextBindingConfig', 'entityList.contextBinding'],
  ['src/views/EntityFormList.vue', 'initConfigData.api.paramsText', 'entityForm.init.apiQuery'],
  ['src/views/EntityFormList.vue', 'initConfigData.api.dataText', 'entityForm.init.apiBody'],
  ['src/views/EntityFormList.vue', 'initConfigData.api.mappingText', 'entityForm.init.apiMapping'],
  ['src/views/EntityFormList.vue', 'initConfigData.entity.paramsText', 'entityForm.init.entityFilters'],
  ['src/views/EntityFormList.vue', 'initConfigData.entity.mappingText', 'entityForm.init.entityMapping'],
  ['src/views/EntityFormList.vue', 'initConfigData.staticText', 'entityForm.init.staticValues'],
  ['src/views/EntityFormList.vue', 'initConfigData.custom.paramsText', 'entityForm.init.customParams'],
  ['src/views/EntityFormDesignByEntity.vue', 'selectedField.dataSourceInputMappingText', 'entityForm.dataSourceInputMapping'],
  ['src/views/EntityFormDesignByEntity.vue', 'selectedField.dataSourceOutputMappingText', 'entityForm.dataSourceOutputMapping'],
  ['src/components/ui-config/FormDataSourceCompatDialog.vue', 'binding.inputMappingText', 'entityForm.dataSourceInputMapping'],
  ['src/components/ui-config/FormDataSourceCompatDialog.vue', 'binding.outputMappingText', 'entityForm.dataSourceOutputMapping'],
  ['src/components/NodeConfigPanel.vue', 'assigneeForm.extraParamsText', 'process.assigneeExtraParams'],
  ['src/components/NodeConfigPanel.vue', 'assigneeForm.collectionExtraParamsText', 'process.multiInstanceExtraParams'],
  ['src/components/NodeConfigPanel.vue', 'restForm.headers', 'process.restHeaders'],
  ['src/components/NodeConfigPanel.vue', 'restForm.body', 'process.restBody'],
  ['src/components/NodeConfigPanel.vue', 'restForm.queryParams', 'process.restQueryParams'],
  ['src/components/NodeConfigPanel.vue', 'restForm.resultMapping', 'process.restResultMapping'],
  ['src/components/NodeConfigPanel.vue', 'ruleForm.inputVariables', 'process.dmnInputVariables'],
  ['src/components/NodeConfigPanel.vue', 'callForm.inputParameters', 'process.callInputParameters'],
  ['src/components/NodeConfigPanel.vue', 'callForm.outputParameters', 'process.callOutputParameters'],
  ['src/components/NodeConfigPanel.vue', 'rule.extraParamsText', 'process.ccExtraParams'],
  ['src/components/FlowActionConfigPanel.vue', 'actionParamList', 'process.actionParams']
]

for (const [file, model, helpKey] of usages) {
  const source = readFileSync(path.join(root, file), 'utf8')
  assert.ok(source.includes(model), `${file} 缺少配置项 ${model}`)
  assert.ok(
    source.includes(`help-key="${helpKey}"`)
      || source.includes(`helpKey: '${helpKey}'`),
    `${file} 的 ${model} 缺少帮助 ${helpKey}`
  )
}

const schemaEditor = readFileSync(
  path.join(root, 'src/components/ConfigSchemaEditor.vue'),
  'utf8'
)
assert.ok(
  schemaEditor.includes("item.type === 'json'")
    && schemaEditor.includes('JsonConfigLabel')
    && schemaEditor.includes('buildSchemaJsonHelp'),
  '动态 JSON Schema 配置必须自动显示统一问号说明'
)

console.log('json config help tests passed')
