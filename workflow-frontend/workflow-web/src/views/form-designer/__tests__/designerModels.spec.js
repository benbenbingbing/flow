import assert from 'node:assert/strict'
import test from 'node:test'
import { compatibleFormComponentOptions, createFormNodeEditorModel, getEditableNodeDataSourceUsages } from '../formNodeEditorModel.js'
import { registerFormFieldComponent, getFormFieldComponentDescriptor } from '@/extensions/core/registries/formFieldRegistry.js'
import { mergeFieldConfig, normalizeFieldForSave } from '../../list-designer/listColumnModel.js'
import { contentScopedFieldOptions, isPublishedAsset } from '../../../components/related-content/relatedContentOptions.js'
import { updateBpmnExtensionProperty } from '../../../components/node-config/bpmnExtensionProperties.js'
import { changeFieldDefaultMode, getFieldDefaultError, readFieldDefaultDataSourceDraft, readFieldDefaultMode } from '../../../shared/form-field-default.js'

function editor(entityFields = []) {
  return createFormNodeEditorModel({ entityFields: { value: entityFields }, activeExtensionMap: { value: new Map() } })
}
test('级联接口使用独立草稿，保存及回读不占用通用绑定，切换为静态树时清除接口', () => {
  const model = editor()
  const node = { nodeType: 'FIELD', nodeKey: 'address', bindingType: 'ENTITY_FIELD', bindingRef: 'address',
    propsDocument: JSON.stringify({ fieldCode: 'address', fieldType: 'STRING', componentType: 'cascader',
      componentProps: { cascaderSource: 'INTERFACE', cascaderLoadMode: 'CHILDREN' } }),
    dataSourceBindingsDocument: JSON.stringify({ FIELD_DEFAULT: { extensionId: 'default-api' } }) }
  const field = model.nodeToField(node)
  field.cascaderDataSourceDraft = { extensionId: 'cascader', inputMappingText: '{"country":"data.country"}', outputMappingText: '' }
  const { props, dataSourceBindings } = model.fieldToNodePayload(field)
  assert.deepEqual(dataSourceBindings.FIELD_OPTIONS, { extensionId: 'cascader', inputMapping: { country: 'data.country' }, outputMapping: {} })
  assert.equal(dataSourceBindings.FIELD_DEFAULT.extensionId, 'default-api')
  const restored = model.nodeToField({ ...node, propsDocument: JSON.stringify(props), dataSourceBindingsDocument: JSON.stringify(dataSourceBindings) })
  assert.equal(restored.dataSourceUsage, '')
  assert.equal(restored.dataSourceBindings.FIELD_OPTIONS.extensionId, 'cascader')
  restored.componentProps = JSON.stringify({ cascaderSource: 'STATIC', cascaderOptions: [] })
  assert.deepEqual(model.fieldToNodePayload(restored).dataSourceBindings, { FIELD_DEFAULT: { extensionId: 'default-api' } })
})
test('字段保留已有的加载和提交绑定，默认值单独维护，退役的计算接口随保存清除', () => {
  const model = editor()
  const legacy = { extensionId: 'default-api', inputMapping: { id: 'data.id' }, outputMapping: { value: 'data.name' } }
  const compute = { extensionId: 'compute-api', modes: ['edit'] }
  const afterLoad = { extensionId: 'load-api', inputMapping: { id: 'data.id' }, outputMapping: { name: 'data.owner' } }
  const beforeSubmit = { extensionId: 'submit-api', inputMapping: { id: 'data.id' }, sideEffectFree: true }
  const node = {
    id: 'name', nodeType: 'FIELD', nodeKey: 'name', bindingType: 'ENTITY_FIELD',
    propsDocument: JSON.stringify({ fieldCode: 'name', fieldType: 'STRING', componentType: 'input' }),
    dataSourceBindingsDocument: JSON.stringify({ FIELD_DEFAULT: legacy, FIELD_COMPUTE: compute, AFTER_LOAD: afterLoad, BEFORE_SUBMIT: beforeSubmit })
  }
  const field = model.nodeToField(node)
  assert.equal(field.dataSourceUsage, 'AFTER_LOAD')
  assert.deepEqual(getEditableNodeDataSourceUsages('FIELD', field.dataSourceBindings), ['AFTER_LOAD', 'BEFORE_SUBMIT'])
  assert.deepEqual(getEditableNodeDataSourceUsages('FIELD', { FIELD_DEFAULT: legacy, AFTER_LOAD: {} }), [])
  assert.deepEqual(getEditableNodeDataSourceUsages('SUB_FORM', {}), ['SUBFORM_ROWS', 'AFTER_LOAD', 'BEFORE_SUBMIT'])
  assert.equal(readFieldDefaultMode(field), 'dataSource')
  assert.deepEqual(model.fieldToNodePayload(field).dataSourceBindings.FIELD_DEFAULT, legacy)
  field.defaultDataSourceDraft = { ...readFieldDefaultDataSourceDraft(field), outputMappingText: '{' }
  assert.match(getFieldDefaultError(field), /不是有效的 JSON/)
  field.defaultDataSourceDraft.outputMappingText = '{"value":"data.label"}'
  assert.equal(getFieldDefaultError(field), '')
  const payload = model.fieldToNodePayload(field, { forPatch: true })
  assert.equal(payload.dataSourceBindings.FIELD_COMPUTE, undefined)
  assert.deepEqual(payload.dataSourceBindings.AFTER_LOAD, afterLoad)
  assert.deepEqual(payload.dataSourceBindings.BEFORE_SUBMIT, beforeSubmit)
  assert.deepEqual(payload.dataSourceBindings.FIELD_DEFAULT, { ...legacy, outputMapping: { value: 'data.label' } })
  const restored = model.nodeToField({ ...node, dataSourceBindingsDocument: JSON.stringify(payload.dataSourceBindings) })
  assert.equal(readFieldDefaultDataSourceDraft(restored).extensionId, 'default-api')
  changeFieldDefaultMode(restored, 'none')
  const cleared = model.fieldToNodePayload(restored)
  assert.equal(cleared.props.defaultValueMode, 'none')
  assert.equal(cleared.props.defaultValue, null)
  assert.equal(cleared.dataSourceBindings.FIELD_DEFAULT, undefined)
  assert.deepEqual(cleared.dataSourceBindings, { AFTER_LOAD: afterLoad, BEFORE_SUBMIT: beforeSubmit })
})
test('单选和多选实体的列表参数映射可保存、回读和清空', () => {
  const model = editor()
  const parameterMappings = [
    { parameter: 'projectId', sourceType: 'FIELD', sourceField: 'project_id' },
    { parameter: 'department', sourceType: 'ENTITY_FIELD', sourceField: 'deptId' },
    { parameter: 'enabled', sourceType: 'LITERAL', value: false }
  ]
  const refConfig = { refEntityType: 'CUSTOM', refEntityId: 'members', entityCode: 'project_member', listKey: 'picker', parameterMappings }
  for (const fieldType of ['REFERENCE', 'MULTI_REFERENCE']) {
    const node = {
      nodeType: 'FIELD', nodeKey: 'project_member_id', bindingType: 'ENTITY_FIELD', bindingRef: 'project_member_id',
      propsDocument: JSON.stringify({
        fieldCode: 'project_member_id', fieldType, componentType: fieldType.toLowerCase(),
        componentProps: { refConfig }
      })
    }
    const field = model.nodeToField(node)
    assert.deepEqual(field.refListParameterMappings, parameterMappings)
    assert.deepEqual(model.fieldToNodePayload(field).props.componentProps.refConfig, refConfig)
    field.refListParameterMappings = []
    const savedProps = model.fieldToNodePayload(field).props
    assert.equal(savedProps.componentProps.refConfig.parameterMappings, undefined)
    assert.deepEqual(model.nodeToField({ ...node, propsDocument: JSON.stringify(savedProps) }).refListParameterMappings, [])
  }
})
test('节点复制和保存回读保留函数引用，根草稿显式清空覆盖旧代码', () => {
  const model = editor()
  const binding = { mode: 'function', handler: 'trimText', version: 1 }
  const node = { nodeType: 'FIELD', nodeKey: 'name', bindingType: 'NONE', propsDocument: JSON.stringify({
    fieldCode: 'name', fieldType: 'STRING', componentType: 'input', componentProps: { events: { onChange: binding, onInput: 'old' } }
  }) }
  const field = model.nodeToField(node)
  assert.deepEqual(field.eventOnChange, binding)
  field.eventOnInput = ''
  const payload = model.fieldToNodePayload(JSON.parse(JSON.stringify(field)))
  assert.deepEqual(payload.props.componentProps.events, { onChange: binding })
  const restored = model.nodeToField({ ...node, propsDocument: JSON.stringify(payload.props) })
  assert.deepEqual(restored.eventOnChange, binding)
  assert.equal(restored.eventOnInput, undefined)
})
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

// 操作栏属于引用实例，往返保存不能覆盖子表关系或参数映射。
test('组成型子表按钮外观在 SUB_FORM/REPEATER 的编辑保存回显中保留', () => {
  const actionBar = { position: 'BOTH', align: 'RIGHT', gap: 12, buttons: {
    add: { label: '新增验收项', buttonType: 'primary', buttonAppearance: 'PLAIN', size: 'large', borderRadius: 8 },
    remove: { label: '移除此项', icon: 'Delete', buttonType: 'danger', buttonAppearance: 'TEXT' }
  } }
  for (const nodeType of ['SUB_FORM', 'REPEATER']) {
    const model = editor()
    const node = { id: 'items', nodeType, nodeKey: 'items', bindingType: 'RELATION', bindingRef: 'order_items', propsDocument: JSON.stringify({
      componentType: 'sub_form', childFormId: 'item-form', componentProps: { subFormConfig: { actionBar, showTitle: false, parameterContract: { parameterMapping: { orderId: 'parent.recordId' } } } }
    }) }
    const field = model.nodeToField(node)
    const payload = model.fieldToNodePayload(field)
    assert.deepEqual(payload.props.componentProps.subFormConfig.actionBar, actionBar)
    assert.equal(payload.props.componentProps.subFormConfig.showTitle, false)
    assert.deepEqual(payload.props.componentProps.subFormConfig.parameterContract, { parameterMapping: { orderId: 'parent.recordId' } })
    assert.equal(payload.bindingRef, 'order_items')
    const restored = model.nodeToField({ ...node, propsDocument: JSON.stringify(payload.props) })
    assert.deepEqual(model.fieldToNodePayload(restored).props.componentProps.subFormConfig.actionBar, actionBar)
  }
})
