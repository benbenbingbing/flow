import assert from 'node:assert/strict'
import { changeListColumnSource, getListColumnSourceSchema, listColumnSourceOptions, validateListColumnSource } from '../list-column-source.js'
import { mergeFieldConfig, normalizeFieldForSave } from '../../views/list-designer/listColumnModel.js'
import { buildListColumnTemplateSnapshot, createListColumnTemplateEditor, applyListColumnTemplateSnapshot } from '../list-column-template.js'

assert.deepEqual(listColumnSourceOptions.map(option => option.value), ['ENTITY_FIELD', 'FIELD_TEMPLATE', 'INTERFACE'])
const sources = [
  { value: 'FIELD_TEMPLATE', configSchema: [{ key: 'template', label: '组合模板', type: 'textarea', defaultValue: '${name}', required: true }] },
  { value: 'CUSTOM_PROVIDER', configSchema: [{ key: 'prefix', defaultValue: '旧前缀' }] }
]
const interfaces = [{ extensionId: 'column-interface', displayName: '业务计算' }]
assert.deepEqual(getListColumnSourceSchema('INTERFACE', sources), [])
assert.deepEqual(getListColumnSourceSchema('ENTITY_FIELD', sources), [])
assert.deepEqual(getListColumnSourceSchema('CUSTOM_PROVIDER', sources), [])
assert.equal(getListColumnSourceSchema('FIELD_TEMPLATE', sources)[0].helpKey, 'entityList.fieldTemplate')

const field = { fieldId: 'field-1', fieldCode: 'name', dataSourceType: 'ENTITY_FIELD', isQuery: true }
changeListColumnSource(field, 'INTERFACE', sources)
assert.equal(field.isQuery, false)
assert.deepEqual(field.dataSourceConfig, {})
assert.equal(validateListColumnSource(field, interfaces), '请选择扩展接口')
field.interfaceExtensionId = 'unavailable'
assert.equal(validateListColumnSource(field, interfaces), '请选择当前列表可用的扩展接口')
field.interfaceExtensionId = 'column-interface'
assert.equal(validateListColumnSource(field, interfaces), '')
assert.equal(validateListColumnSource({ ...field, fieldId: 'virtual_1' }, interfaces), '', '虚拟列可以直接绑定接口，不需要伪造组合模板')

changeListColumnSource(field, 'FIELD_TEMPLATE', sources)
assert.equal(field.interfaceExtensionId, '', '切换来源须清掉会覆盖列值的接口')
assert.deepEqual(field.dataSourceConfig, { template: '${name}' })
assert.equal(validateListColumnSource(field, interfaces), '')
assert.equal(validateListColumnSource({ ...field, dataSourceConfig: '{"template":"  "}' }, interfaces), '请填写组合模板')
assert.equal(validateListColumnSource({ ...field, dataSourceConfig: '{bad' }, interfaces), '请填写组合模板')
field.dataSourceConfig.prefix = '旧参数'
changeListColumnSource(field, 'ENTITY_FIELD', sources)
assert.deepEqual(field.dataSourceConfig, {}, '实体字段不能残留组合或扩展参数')
assert.equal(validateListColumnSource(field, interfaces), '')
assert.match(validateListColumnSource({ ...field, fieldId: 'virtual_1' }, interfaces), /虚拟列/)
assert.match(validateListColumnSource({ ...field, interfaceExtensionId: 'column-interface' }, interfaces), /只有“扩展接口”/)
const legacySourceError = validateListColumnSource({
  ...field, fieldName: '项目扩展列', dataSourceType: 'PROJECT_CUSTOM_FIELD'
}, interfaces)
assert.match(legacySourceError, /列“项目扩展列”（字段编码：name）/)
assert.match(legacySourceError, /取值配置与当前版本不兼容（PROJECT_CUSTOM_FIELD）/)
assert.match(legacySourceError, /重新选择可用的取值方式/)
assert.match(legacySourceError, /“扩展接口”并绑定接口/)

// 列与模板都以同一协议保存 INTERFACE，重载后不可退回到实体字段或虚假的模板来源。
changeListColumnSource(field, 'INTERFACE', sources)
field.interfaceExtensionId = 'column-interface'
const saved = normalizeFieldForSave(field)
const [loaded] = mergeFieldConfig([saved], {
  entityFields: [{ id: 'field-1', fieldCode: 'name' }], availableListColumnInterfaces: interfaces, isSystemEntity: false
})
assert.equal(loaded.dataSourceType, 'INTERFACE')
assert.equal(loaded.interfaceExtensionId, 'column-interface')
assert.equal(validateListColumnSource(loaded, interfaces), '')
const template = createListColumnTemplateEditor({ field: saved })
const snapshot = buildListColumnTemplateSnapshot(template)
const virtual = applyListColumnTemplateSnapshot({ fieldId: 'virtual_1', fieldCode: 'computed_name' }, snapshot)
assert.equal(virtual.dataSourceType, 'INTERFACE')
assert.equal(virtual.interfaceExtensionId, 'column-interface')
assert.equal(virtual.isQuery, false)
assert.equal(validateListColumnSource(virtual, interfaces), '')
console.log('list-column-source tests passed')
