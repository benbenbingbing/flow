import assert from 'node:assert/strict'
import { mapPageParameters, resolvePageParameters, initializePageFields, pageParameterFields, pageParameterFilterFields, pageParameterInitializationFields, validatePageParameterMappings } from '@flow/workflow-core/page-parameters'
import { buildSubFormParentContext } from '@flow/workflow-core/subform-parameter-contract'
import { mergeFormNodeFieldMetadata } from '@flow/workflow-core/form-node-property-schema'

const schema = { type: 'object', properties: { project: { type: 'string' }, count: { type: 'integer', default: 3 }, enabled: { type: 'boolean' }, info: { type: 'object' } }, required: ['project'] }
const mappings = [{ parameter: 'project', sourceType: 'FIELD', sourceField: 'projectId' }, { parameter: 'enabled', sourceType: 'LITERAL', value: 'false' }, { parameter: 'info', sourceType: 'PARAMETER', sourceField: 'parentInfo' }]
const data = { projectId: 'unsaved-project' }, parentInfo = { name: 'A' }
const transferred = mapPageParameters(mappings, { data, recordId: 'record-1', params: { parentInfo } })
assert.deepEqual(transferred, { project: 'unsaved-project', enabled: 'false', info: { name: 'A' } })
parentInfo.name = 'B'
assert.equal(transferred.info.name, 'A', '传参是独立快照')
assert.equal(mapPageParameters([{ parameter: 'project', sourceType: 'FIELD', sourceField: 'projectId' }], { data: { data: { projectId: 'row' } } }).project, 'row')
const parameters = resolvePageParameters({ inputParameterSchema: schema }, transferred)
assert.equal(parameters.enabled, false)
assert.equal(parameters.count, 3)
assert.throws(() => resolvePageParameters({ inputParameterSchema: schema }, { count: 'bad' }), /页面输入参数/)
assert.throws(() => resolvePageParameters({ inputParameterSchema: schema }, { project: 'p', count: 1.5 }), /页面输入参数/)
assert.throws(() => mapPageParameters([{ parameter: 'x', sourceType: 'FIELD', sourceField: 'missing' }, { parameter: 'x', sourceType: 'LITERAL', value: 'v' }]), /重复/)
assert.throws(() => mapPageParameters([{ parameter: 'constructor', sourceType: 'LITERAL' }]), /不合法/)
assert.equal(validatePageParameterMappings(mappings, schema, [{ fieldCode: 'projectId' }]), '')
assert.match(validatePageParameterMappings(mappings, {}, []), /未声明/)
const fields = [{ fieldCode: 'projectId' }, { fieldCode: 'locked', isReadonly: true }, { fieldCode: 'fk' }]
const config = { inputParameterBindings: ['projectId', 'locked', 'id', 'fk'].map(targetField => ({ usage: 'INITIALIZE', parameter: 'project', targetField })) }
assert.deepEqual(initializePageFields({ projectId: '', locked: '', id: 'record' }, config, parameters, fields, ['fk']), { projectId: 'unsaved-project', locked: 'unsaved-project', id: 'record' })
assert.equal(initializePageFields({ projectId: 'existing' }, config, parameters, fields).projectId, 'existing')
assert.equal(buildSubFormParentContext({ record: { id: 'p', data: { projectId: 'old' } }, getFormData: () => data }).data.projectId, 'unsaved-project')
assert.equal(pageParameterFields([{ bindingType: 'ENTITY_FIELD', bindingRef: 'projectId', propsDocument: '{"readonly":true}' }])[0].readonly, true)
console.log('page parameters passed: current edits, row data, snapshots, defaults/types, required, readonly initialization/identity protection, subform latest values')

// 参数初始化和手工编辑权限独立，普通文本、数值、多选和对象值都应能作为初值。
const allTypeFields = [
  { fieldCode: 'name', fieldType: 'STRING', isReadonly: '1' },
  { fieldCode: 'amount', fieldType: 'DECIMAL', readonly: true },
  { fieldCode: 'enabled', fieldType: 'BOOLEAN', extensionConfig: { modes: { create: { editable: false } } } },
  { fieldCode: 'date', fieldType: 'DATE', props: { readonly: true } },
  { fieldCode: 'projectId', fieldType: 'REFERENCE' },
  { fieldCode: 'members', fieldType: 'MULTI_REFERENCE', isReadonly: 1 },
  { fieldCode: 'tags', fieldType: 'MULTI_SELECT' },
  { fieldCode: 'files', fieldType: 'FILE' },
  { fieldCode: 'details', fieldType: 'JSON' },
  { fieldCode: 'rows', fieldType: 'SUB_FORM', nodeType: 'REPEATER' }
]
const allTypeParameters = {
  name: '项目名称', amount: 0, enabled: false, date: '2026-10-12', projectId: 'p1',
  members: ['m1', 'm2'], tags: ['a', 'b'], files: [{ id: 'file1' }], details: { summary: '说明' }, rows: [{ title: '子表记录' }]
}
const allTypeConfig = { inputParameterBindings: allTypeFields.map(field => ({
  usage: 'INITIALIZE', parameter: field.fieldCode, targetField: field.fieldCode
})) }
assert.deepEqual(pageParameterInitializationFields(allTypeFields).map(field => field.fieldCode), Object.keys(allTypeParameters))
const allTypeRecord = initializePageFields({ members: [], name: '' }, allTypeConfig, allTypeParameters, allTypeFields)
assert.deepEqual(allTypeRecord, allTypeParameters)
assert.deepEqual(initializePageFields({}, allTypeConfig, allTypeParameters, allTypeFields, [], 'edit'), allTypeParameters)
assert.deepEqual(initializePageFields(allTypeRecord, allTypeConfig, { amount: 99, enabled: true, members: ['replacement'] }, allTypeFields), allTypeParameters, '零值、false 和已有数组不能被覆盖')
allTypeRecord.members.push('local-edit')
allTypeRecord.details.summary = '已编辑'
assert.deepEqual(allTypeParameters.members, ['m1', 'm2'])
assert.equal(allTypeParameters.details.summary, '说明', '数组和对象初值必须与来源参数隔离')
for (const mode of ['view', 'approve']) {
  assert.deepEqual(initializePageFields({}, allTypeConfig, allTypeParameters, allTypeFields, [], mode), {}, '查看和审批不通过输入参数改写记录')
}

// 发布节点和旧字段使用不同键名；布局节点不承载数据，自定义字段无需绑定实体。
const formNodes = [{ nodeType: 'GRID', nodeKey: 'layout', fieldCode: 'layout', children: [
  { nodeType: 'FIELD', nodeKey: 'customText', bindingType: 'NONE', propsDocument: '{"label":"自定义文本","readonly":true}' },
  { nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: 'projectId' },
  { fieldKey: 'legacyField' }
] }, ...allTypeFields, { fieldCode: 'id' }, { fieldCode: 'createdAt', isSystem: '1' },
{ nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: 'createdAt' }]
const nodeCandidates = pageParameterInitializationFields(formNodes)
assert.deepEqual(nodeCandidates.map(field => field.fieldCode), ['customText', 'projectId', 'legacyField', ...Object.keys(allTypeParameters).filter(key => key !== 'projectId')])
assert.equal(nodeCandidates[0].fieldLabel, '自定义文本')
const nodeBindings = ['customText', 'legacyField', 'id', 'createdAt', 'layout', 'missing'].map(targetField => ({ usage: 'INITIALIZE', parameter: 'project', targetField }))
assert.deepEqual(initializePageFields({}, { inputParameterBindings: nodeBindings }, parameters, formNodes), { customText: 'unsaved-project', legacyField: 'unsaved-project' }, '只初始化真实表单业务字段，身份和系统字段保持受保护')
// 实际旧发布投影没有 isSystem，不能因放开 readonly 而允许初始化审计或流程状态字段。
const legacySystemFields = ['create_time', 'update_by', 'processInstanceId', 'status'].map(fieldCode => ({ fieldCode, isReadonly: 1 }))
const legacySystemNodes = legacySystemFields.map(field => ({ nodeType: 'FIELD', nodeKey: field.fieldCode, props: { fieldCode: field.fieldCode, readonly: true } }))
const legacySystemBindings = legacySystemFields.map(field => ({ usage: 'INITIALIZE', parameter: 'project', targetField: field.fieldCode }))
assert.deepEqual(pageParameterInitializationFields([...legacySystemFields, ...legacySystemNodes]), [])
assert.deepEqual(initializePageFields({}, { inputParameterBindings: legacySystemBindings }, parameters, [...legacySystemFields, ...legacySystemNodes]), {})
// 设计器会合并实体元数据：内置名称、编码、部门虽是 isSystem，也允许作为业务初值。
const builtinBusinessFields = ['name', 'code', 'deptId'].map(fieldCode => ({
  ...mergeFormNodeFieldMetadata([{ fieldCode, isSystem: true, editable: true }], {}, { fieldCode }, fieldCode),
  fieldCode, isReadonly: 1
}))
const builtinBusinessBindings = builtinBusinessFields.map(field => ({ usage: 'INITIALIZE', parameter: 'project', targetField: field.fieldCode }))
assert.deepEqual(pageParameterInitializationFields(builtinBusinessFields).map(field => field.fieldCode), ['name', 'code', 'deptId'])
assert.deepEqual(initializePageFields({}, { inputParameterBindings: builtinBusinessBindings }, parameters, builtinBusinessFields), { name: 'unsaved-project', code: 'unsaved-project', deptId: 'unsaved-project' })
console.log('page parameter field coverage passed: all field types, readonly values, node/legacy keys, empty-only initialization and protected fields')

// 覆盖真正的数据源生命周期：目标参数先初始化空字段，再供接口 input.params 使用。
const { createFormDataSourceRuntime } = await import('@flow/workflow-core/form-runtime/dataSourceRuntime')
const calls = []
const targetForm = { id: 'target', configHash: 'current-hash',
  viewConfig: { inputParameterSchema: schema, inputParameterBindings: [{ parameter: 'project', usage: 'INITIALIZE', targetField: 'projectId' }] },
  dataSourceBindingsDocument: { AFTER_LOAD: [{ bindingCode: 'read', extensionId: 'reader' }] } }
const targetRecord = { projectId: '' }
const runtime = createFormDataSourceRuntime({ getForm: () => targetForm, getRecord: () => targetRecord, getMode: () => 'create', executeDataSource: async request => { calls.push(request); return { data: {} } } }).withContext({ params: { project: 'latest' } })
await runtime.initialize({ form: targetForm, fields: [{ fieldCode: 'projectId', fieldType: 'STRING', isReadonly: true }] })
assert.equal(targetRecord.projectId, 'latest')
assert.equal(calls[0].input.params.project, 'latest')
assert.equal(calls[0].input.params.count, 3)
assert.equal(calls[0].configHash, 'current-hash')
assert.equal(calls[0].releaseId, undefined)
targetRecord.projectId = 'user-edited'
await runtime.initialize({ form: targetForm, fields: [{ fieldCode: 'projectId' }] })
assert.equal(targetRecord.projectId, 'user-edited', '重复渲染不能覆盖用户编辑')
console.log('page parameter runtime passed: initialization before interface, params in pinned requests, no repeated reset')
const retryParams = {}
const retryRecord = {}
const retryRuntime = createFormDataSourceRuntime({ getForm: () => targetForm, getRecord: () => retryRecord, getMode: () => 'create', executeDataSource: async () => ({ data: {} }) }).withContext(() => ({ params: retryParams }))
await assert.rejects(retryRuntime.initialize({ form: targetForm, fields: [{ fieldCode: 'projectId' }] }), /页面输入参数/)
retryParams.project = 'fixed'
await retryRuntime.initialize({ form: targetForm, fields: [{ fieldCode: 'projectId' }] })
assert.equal(retryRecord.projectId, 'fixed', '参数修正后可重试，不能被初始化去重拦截')

// 隐藏筛选独立于列表查询/展示；包括旧接口的数字、字符串开关以及未保存过列配置的字段。
const hiddenFilterFields = [false, 0, '0', undefined, true].map((isQuery, index) => ({
  fieldCode: `field${index}`, fieldName: `字段${index}`, isQuery, showInList: false
}))
assert.deepEqual(pageParameterFilterFields([
  ...hiddenFilterFields,
  { fieldCode: 'summary', fieldId: 'virtual_summary', dataSourceType: 'ENTITY_FIELD' },
  { fieldCode: 'computed', dataSourceType: 'FIELD_TEMPLATE' },
  { fieldCode: 'interfaceValue', dataSourceType: 'INTERFACE' },
  { fieldCode: 'legacyInterfaceValue', interfaceExtensionId: 'extension-1' }
]).map(field => field.fieldCode), hiddenFilterFields.map(field => field.fieldCode))
console.log('list parameter fields passed: hidden/non-query entity fields included, computed columns excluded')
