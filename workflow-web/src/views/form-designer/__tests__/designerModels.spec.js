import assert from 'node:assert/strict'
import test from 'node:test'
import { compatibleFormComponentOptions, createFormNodeEditorModel } from '../formNodeEditorModel.js'
import { registerFormFieldComponent, getFormFieldComponentDescriptor } from '@/extensions/core/registries/formFieldRegistry.js'
import { mergeFieldConfig, normalizeFieldForSave } from '../../list-designer/listColumnModel.js'
import { contentScopedFieldOptions, isPublishedAsset } from '../../../components/related-content/relatedContentOptions.js'
import { updateBpmnExtensionProperty } from '../../../components/node-config/bpmnExtensionProperties.js'

function editor(entityFields = []) {
  return createFormNodeEditorModel({ entityFields: { value: entityFields }, activeExtensionMap: { value: new Map() } })
}
test('自定义组件使用唯一组件类型往返，参数和锁定版本不会恢复成平台默认控件', () => {
  const name = 'designer_summary_component'
  const schema = [{ key: 'caption', label: '标题', type: 'string' }]
  registerFormFieldComponent(name, {}, { nodeTypes: ['FIELD'], configSchema: schema, version: 3, snapshotVersion: 2 })
  const model = createFormNodeEditorModel({ entityFields: { value: [] }, activeExtensionMap: { value: new Map([[`FIELD:${name}`, { version: 4, snapshotVersion: 3 }]]) } })
  const field = model.nodeToField({
    id: 'summary-node', nodeType: 'FIELD', nodeKey: 'summary', bindingType: 'NONE',
    componentVersion: 3, snapshotVersion: 2,
    propsDocument: JSON.stringify({ fieldCode: 'summary', fieldType: 'STRING', componentType: name, componentProps: { caption: '验收结果' } })
  })
  assert.equal(field.componentType, name)
  assert.deepEqual(getFormFieldComponentDescriptor(field.componentType, field.componentVersion).configSchema, schema)
  assert.equal(JSON.parse(field.componentProps).caption, '验收结果')
  const payload = model.fieldToNodePayload(field, { forPatch: true })
  assert.equal(payload.props.componentType, name)
  assert.equal(payload.props.componentProps.caption, '验收结果')
  assert.equal(payload.componentVersion, 3, '编辑普通参数不得自动升级组件版本')
  assert.equal(payload.snapshotVersion, 2)
  for (const key of ['componentName', 'fieldComponentName', 'fieldComponentVersion', 'fieldComponentSnapshotVersion', 'componentExtensionType']) {
    assert.equal(Object.hasOwn(field, key), false)
    assert.equal(Object.hasOwn(payload, key), false)
    assert.equal(Object.hasOwn(payload.props, key), false)
  }
  field.componentType = 'input'
  const builtinPayload = model.fieldToNodePayload(field, { forPatch: true })
  assert.equal(builtinPayload.props.componentType, 'input')
  assert.equal(builtinPayload.componentVersion, undefined)
  assert.ok(builtinPayload.clearFields.includes('componentVersion'))
  assert.ok(builtinPayload.clearFields.includes('snapshotVersion'))
})
test('组件选择同时遵守节点类型、字段类型和绑定约束', () => {
  const options = [
    { value: 'input', origin: 'PLATFORM', supportedFieldTypes: ['STRING'] },
    { value: 'sub_form', nodeTypes: ['SUB_FORM', 'REPEATER'], supportedFieldTypes: ['SUB_FORM'] },
    { value: 'summary', nodeTypes: ['FIELD'], supportedBindings: ['NONE'] },
    { value: 'relation_cards', nodeTypes: ['SUB_FORM', 'REPEATER'], supportedBindings: ['RELATION'] }
  ]
  assert.deepEqual(compatibleFormComponentOptions({ nodeType: 'FIELD', fieldType: 'STRING', bindingType: 'NONE' }, options).map(option => option.value), ['input', 'summary'])
  assert.deepEqual(compatibleFormComponentOptions({ nodeType: 'FIELD', fieldType: 'STRING', bindingType: 'ENTITY_FIELD', bindingRef: 'customer' }, options).map(option => option.value), ['input'])
  assert.deepEqual(compatibleFormComponentOptions({ nodeType: 'SUB_FORM', fieldType: 'SUB_FORM', relationCode: 'items' }, options).map(option => option.value), ['sub_form', 'relation_cards'])
  assert.deepEqual(compatibleFormComponentOptions({ nodeType: 'SUB_FORM', fieldType: 'SUB_FORM' }, options).map(option => option.value), ['sub_form'])
})
test('子表单和重复器保留自定义组件类型及参数，与字段使用同一序列化协议', () => {
  const name = 'designer_relation_cards'
  registerFormFieldComponent(name, {}, { nodeTypes: ['SUB_FORM', 'REPEATER'], version: 2 })
  const model = editor()
  for (const nodeType of ['SUB_FORM', 'REPEATER']) {
    const field = model.nodeToField({
      nodeType, nodeKey: 'items', bindingType: 'RELATION', bindingRef: 'order_items',
      componentVersion: 2, snapshotVersion: 1,
      propsDocument: JSON.stringify({ componentType: name, childFormId: 'item-form', componentProps: { title: '明细卡片' } })
    })
    const payload = model.fieldToNodePayload(field)
    assert.equal(field.componentType, name)
    assert.equal(payload.props.componentType, name)
    assert.equal(payload.props.componentProps.title, '明细卡片')
    assert.equal(payload.childFormId, 'item-form')
    assert.equal(payload.bindingRef, 'order_items')
    assert.equal(payload.componentVersion, 2)
  }
})
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
test('子表单保持稳定身份，子列表保留自己的发布身份，序列化不修改当前草稿', () => {
  const model = editor()
  const field = { nodeType: 'SUB_FORM', fieldType: 'SUB_FORM', relationType: 'ONE_TO_ONE', childEntityId: 'child', childFormId: 'form-v1', childFormReleaseId: 'release-v1', childFormReleaseVersion: 1, componentProps: JSON.stringify({ extra: 'keep', subFormConfig: { relationCode: 'items' } }) }
  const before = structuredClone(field)
  const props = model.fieldToNodePayload(field).props.componentProps
  assert.equal(props.subFormConfig.repeatable, false)
  assert.equal(props.subFormConfig.childFormId, 'form-v1')
  assert.equal(props.subFormConfig.childFormReleaseId, undefined)
  assert.equal(props.subFormConfig.publishedFormReleaseVersion, undefined)
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
