import assert from 'node:assert/strict'
import { mapPageParameters, resolvePageParameters, initializePageFields, pageParameterFields, validatePageParameterMappings } from '../page-parameters.js'
import { buildSubFormParentContext } from '../subform-parameter-contract.js'

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
assert.deepEqual(initializePageFields({ projectId: '', locked: '', id: 'record' }, config, parameters, fields, ['fk']), { projectId: 'unsaved-project', locked: '', id: 'record' })
assert.equal(initializePageFields({ projectId: 'existing' }, config, parameters, fields).projectId, 'existing')
assert.equal(buildSubFormParentContext({ record: { id: 'p', data: { projectId: 'old' } }, getFormData: () => data }).data.projectId, 'unsaved-project')
assert.equal(pageParameterFields([{ bindingType: 'ENTITY_FIELD', bindingRef: 'projectId', propsDocument: '{"readonly":true}' }])[0].readonly, true)
console.log('page parameters passed: current edits, row data, snapshots, defaults/types, required, readonly/identity protection, subform latest values')

// 覆盖真正的数据源生命周期：目标参数先初始化空字段，再供接口 input.params 使用。
const { createFormDataSourceRuntime } = await import('../form-runtime/dataSourceRuntime.js')
const calls = []
const targetForm = { id: 'target', runtimeReleaseId: 'release-1', runtimeReleaseVersion: 1, releaseResolutionToken: 'signed-release',
  viewConfig: { inputParameterSchema: schema, inputParameterBindings: [{ parameter: 'project', usage: 'INITIALIZE', targetField: 'projectId' }] },
  dataSourceBindingsDocument: { AFTER_LOAD: [{ bindingCode: 'read', extensionId: 'reader' }] } }
const targetRecord = { projectId: '' }
const runtime = createFormDataSourceRuntime({ getForm: () => targetForm, getRecord: () => targetRecord, getMode: () => 'create', executeDataSource: async request => { calls.push(request); return { data: {} } } }).withContext({ params: { project: 'latest' } })
await runtime.initialize({ form: targetForm, fields: [{ fieldCode: 'projectId' }] })
assert.equal(targetRecord.projectId, 'latest')
assert.equal(calls[0].input.params.project, 'latest')
assert.equal(calls[0].input.params.count, 3)
assert.equal(calls[0].releaseId, 'release-1')
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
