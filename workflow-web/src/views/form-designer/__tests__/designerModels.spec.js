import assert from 'node:assert/strict'
import test from 'node:test'
import { createFormNodeEditorModel } from '../formNodeEditorModel.js'
import { mergeFieldConfig, normalizeFieldForSave } from '../../list-designer/listColumnModel.js'
import { contentScopedFieldOptions, isPublishedAsset } from '../../../components/related-content/relatedContentOptions.js'
import { updateBpmnExtensionProperty } from '../../../components/node-config/bpmnExtensionProperties.js'

function editor(entityFields = []) {
  return createFormNodeEditorModel({ entityFields: { value: entityFields }, activeExtensionMap: { value: new Map() } })
}
test('节点读取、序列化往返保留身份、实体必填约束和显式 false', () => {
  const model = editor([{ id: 'amount-field', fieldCode: 'amount', fieldName: '金额', fieldType: 'DECIMAL', isRequired: 1, isReadonly: 1 }])
  const node = { id: 'node', formId: 'form', nodeKey: 'amount', nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: 'amount', revision: 3, orderKey: 2000000, propsDocument: JSON.stringify({ fieldId: 'amount-field', fieldCode: 'amount', fieldType: 'DECIMAL', required: false, readonly: false, span: 12 }) }
  const field = model.nodeToField(node)
  assert.equal(field.gridSpan, 12)
  assert.equal(field.isReadonly, 0)
  const payload = model.fieldToNodePayload(field)
  assert.equal(payload.id, 'node'); assert.equal(payload.bindingRef, 'amount')
  assert.equal(payload.props.required, true)
  assert.equal(payload.props.readonly, false)
  const restored = model.nodeToField({ ...node, propsDocument: JSON.stringify(payload.props), rulesDocument: JSON.stringify(payload.rules), dataSourceBindingsDocument: JSON.stringify(payload.dataSourceBindings) })
  assert.deepEqual(model.fieldToNodePayload(restored), payload)
})
test('子表单和子列表保持固定发布身份，序列化不修改当前草稿', () => {
  const model = editor()
  const field = { nodeType: 'SUB_FORM', fieldType: 'SUB_FORM', relationType: 'ONE_TO_ONE', childEntityId: 'child', childFormId: 'form-v1', childFormReleaseId: 'release-v1', childFormReleaseVersion: 1, componentProps: JSON.stringify({ extra: 'keep', subFormConfig: { relationCode: 'items' } }) }
  const before = structuredClone(field)
  const props = model.fieldToNodePayload(field).props.componentProps
  assert.equal(props.subFormConfig.repeatable, false)
  assert.equal(props.subFormConfig.childFormReleaseId, 'release-v1')
  assert.equal(props.subFormConfig.publishedFormReleaseVersion, 1)
  assert.equal(props.extra, 'keep'); assert.deepEqual(field, before)
  const list = model.fieldToNodePayload({ fieldType: 'SUB_LIST', refListId: 'list', refListReleaseId: 'snapshot', refListReleaseVersion: 4, subListShowToolbar: false }).props.componentProps
  assert.equal(list.subListConfig.listReleaseId, 'snapshot')
  assert.equal(list.subListConfig.listReleaseVersion, 4)
  assert.equal(list.subListConfig.showToolbar, false)
})
test('附件规则取实体约束并克隆数组，字段脚本可往返', () => {
  const fileItems = [{ itemKey: 'proof', itemName: '证明', required: 1, fileTypes: ['.pdf'], nameAliases: ['旧证明'] }]
  const model = editor([{ id: 'file', fieldCode: 'file', fileItems }])
  const field = { fieldId: 'file', fieldCode: 'file', fieldType: 'FILE', eventOnChange: 'return value', componentProps: '{}' }
  const props = model.fieldToNodePayload(field).props.componentProps
  assert.equal(props.fileItems[0].required, true)
  props.fileItems[0].fileTypes.push('.png')
  assert.deepEqual(fileItems[0].fileTypes, ['.pdf'])
  const restored = { componentProps: JSON.stringify(props) }
  model.restoreFieldConfig(restored)
  assert.equal(restored.eventOnChange, 'return value')
})
test('列模型保留隐藏、排序、revision，并且模板只初始化一次', () => {
  const fields = [{ id: 'a', fieldCode: 'a', fieldName: 'A' }]
  const saved = [{ id: 'column-a', fieldId: 'a', showInList: false, isQuery: true, sortOrder: 2, revision: 8 }, { id: 'column-v', fieldId: 'virtual_v', fieldCode: 'v', sortOrder: 0 }]
  const result = mergeFieldConfig(saved, { entityFields: fields, availableListColumnInterfaces: [], isSystemEntity: false })
  assert.equal(result[0].fieldId, 'virtual_v'); assert.equal(result[1].showInList, false)
  assert.equal(result[1].revision, 8)
  const payload = normalizeFieldForSave({ ...result[1], templateId: 'old-template' }, 1)
  assert.equal(payload.templateId, null); assert.equal(payload.sortOrder, 1)
  assert.equal(payload.id, 'column-a'); assert.equal(payload.interfaceExtensionId, null)
  assert.equal(mergeFieldConfig(saved, { entityFields: fields, availableListColumnInterfaces: [], isSystemEntity: true }).length, 1)
})
test('关联内容映射不暴露隐藏或只读字段，启用状态不等同已发布', () => {
  const options = ['a', 'b', 'c'].map(value => ({ value }))
  const fields = [{ fieldCode: 'a', isReadonly: 1 }, { fieldCode: 'b', isHidden: 1 }, { fieldCode: 'c' }]
  assert.deepEqual(contentScopedFieldOptions(options, fields, 'FORM', true), [{ value: 'c' }])
  assert.deepEqual(contentScopedFieldOptions(options, fields, 'FORM', false), [{ value: 'a' }, { value: 'c' }])
  assert.equal(isPublishedAsset({ status: 1 }), false)
  assert.equal(isPublishedAsset({ activeReleaseId: 'fixed' }), true)
})
test('BPMN 扩展写入不修改命令栈的旧快照，保留其他扩展和值', () => {
  const moddle = { create: (type, attrs = {}) => ({ $type: type, ...attrs, get(key) { return this[key] } }) }
  const oldProperty = moddle.create('flowable:Property', { name: 'slaConfig', value: 'before' })
  const untouched = moddle.create('flowable:Property', { name: 'entityCode', value: 'orders' })
  const otherExtension = moddle.create('flowable:ExecutionListener', { event: 'start' })
  const old = moddle.create('bpmn:ExtensionElements', { values: [otherExtension, moddle.create('flowable:Properties', { values: [oldProperty, untouched] })] })
  const element = { businessObject: { extensionElements: old } }
  const changes = []
  const modeling = { updateProperties: (el, change) => { changes.push({ before: el.businessObject.extensionElements, after: change.extensionElements }); Object.assign(el.businessObject, change) } }
  updateBpmnExtensionProperty(element, moddle, modeling, 'slaConfig', 'after')
  assert.equal(oldProperty.value, 'before'); assert.equal(changes[0].before, old)
  assert.equal(element.businessObject.extensionElements.values[0], otherExtension)
  assert.equal(element.businessObject.extensionElements.values[1].values[1], untouched)
  updateBpmnExtensionProperty(element, moddle, modeling, 'slaConfig', null)
  assert.deepEqual(element.businessObject.extensionElements.values[1].values.map(x => x.name), ['entityCode'])
  assert.equal(changes[1].before.values[1].values[0].value, 'after')
})
