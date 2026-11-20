import test from 'node:test'
import assert from 'node:assert/strict'
import { createDataPickerRuntime, createEntityListPickerSource, DEFAULT_PICKER_SCHEMA, normalizePickerSchema, normalizePickerRows, pickerListSchema, pickerQuerySchemaFields, preparePickerPatch } from '../src/shared/data-picker.js'
import { referenceListParameterState } from '../src/shared/page-parameters.js'
import { createFormDataSourceRuntime } from '../src/shared/form-runtime/dataSourceRuntime.js'

const schema = { schemaVersion: '1', valueField: 'code', labelField: 'name', fields: [
  { code: 'code', type: 'STRING' }, { code: 'name', type: 'STRING' }, { code: 'contact', type: 'STRING' },
  { code: 'score', type: 'INTEGER' }, { code: 'active', type: 'BOOLEAN' }
], columns: [{ field: 'name' }] }
const rows = [{ code: '001', name: '甲客户', contact: '联系人', score: 0, active: false }]
const deferred = () => { let resolve; const promise = new Promise(done => { resolve = done }); return { promise, resolve } }
function fixture(overrides = {}) {
  const calls = []
  const runtime = createDataPickerRuntime()
  runtime.configure({ schema, load: async (usage, input) => {
    calls.push({ usage, input })
    if (overrides[usage]) return overrides[usage](input)
    return usage === 'FIELD_PICKER_SCHEMA' ? schema : usage === 'FIELD_PICKER_QUERY' ? { records: rows, total: 1 } : rows
  } })
  return { runtime, calls }
}

test('字段与展示列分离，保留字符串业务键及零/false，拒绝重复键和类型漂移', () => {
  const normalized = normalizePickerSchema(schema)
  const [row] = normalizePickerRows([{ ...rows[0], secret: '隐藏字段' }], normalized)
  assert.equal(row.value, '001'); assert.equal(row.data.score, 0); assert.equal(row.data.active, false)
  assert.equal('secret' in row.data, false)
  assert.throws(() => normalizePickerRows([...rows, ...rows], normalized), /唯一/)
  assert.throws(() => normalizePickerRows([{ ...rows[0], code: 1 }], normalized), /字符串/)
  assert.throws(() => normalizePickerRows([{ ...rows[0], active: 'false' }], normalized), /布尔/)
  assert.throws(() => normalizePickerSchema({ ...schema, columns: [{ field: 'missing' }] }), /展示列/)
})

test('回显独立于当前页，不触发回填；确认重新解析权威记录', async () => {
  const { runtime, calls } = fixture({ FIELD_PICKER_RESOLVE: async () => [{ ...rows[0], contact: '最新联系人' }] })
  await runtime.restore('001')
  assert.equal(runtime.getState().selected.label, '甲客户'); assert.equal(runtime.getState().open, false)
  await runtime.open()
  const result = await runtime.confirm(runtime.getState().rows[0], async selection => selection.data.contact)
  assert.equal(result, '最新联系人')
  assert.deepEqual(calls.filter(call => call.usage === 'FIELD_PICKER_RESOLVE').map(call => call.input.values), [['001'], ['001']])
})

test('九种属性从同一条记录按声明取值，记录标识始终是字符串', async () => {
  const cases = [
    ['STRING', '原始文本', '原始文本'], ['TEXT', '第一行\n第二行', '第一行\n第二行'], ['INTEGER', 0, 0],
    ['LONG', '9223372036854775807', '9223372036854775807'],
    ['DECIMAL', '123456789.123456789', '123456789.123456789'],
    ['DATE', '2024-02-29', '2024-02-29'], ['DATETIME', '2024-02-29T08:23:50', '2024-02-29 08:23:50'],
    ['BOOLEAN', false, false], ['RICH_TEXT', '<p>模板</p><script>remove()</script>', '<p>模板</p>']
  ]
  const typedSchema = normalizePickerSchema({ ...schema, fields: [...schema.fields,
    ...cases.map(([type]) => ({ code: `business${type}`, type }))] })
  const values = Object.fromEntries(cases.map(([type, value]) => [`business${type}`, value]))
  const [row] = normalizePickerRows([{ ...rows[0], ...values }], typedSchema)
  assert.equal(row.value, '001')
  for (const [fieldType, , expected] of cases) {
    const field = { fieldCode: 'target', fieldType,
      componentProps: { pickerSchema: typedSchema, pickerValueMode: 'FIELD', pickerWriteField: `business${fieldType}` } }
    assert.deepEqual(await preparePickerPatch(field, row, { target: 'old' }, undefined, undefined,
      html => html.replace(/<script>.*<\/script>/, '')), { target: expected })
  }
  const field = { fieldCode: 'target', fieldType: 'DATE', componentProps: { pickerSchema: typedSchema, pickerWriteField: 'businessDATE' } }
  await assert.rejects(preparePickerPatch(field, { ...row, data: { businessDATE: '2025-02-29' } }, {}), /有效的日期/)
  await assert.rejects(preparePickerPatch(field, { ...row, data: {} }, {}), /没有值/)
})

test('查询失败不保留旧可选行，可以重试，字段结构变化明确阻止选择', async () => {
  let failure = true
  const { runtime } = fixture({ FIELD_PICKER_QUERY: async () => { if (failure) throw Error('网络失败'); return { records: rows, total: 1 } } })
  await runtime.open(); assert.equal(runtime.getState().rows.length, 0); assert.match(runtime.getState().error, /网络失败/)
  failure = false; await runtime.query(); assert.equal(runtime.getState().rows.length, 1)
  const changed = fixture({ FIELD_PICKER_SCHEMA: async () => ({ ...schema, schemaVersion: '2' }) }).runtime
  await changed.open(); assert.match(changed.getState().error, /结构已变化/); assert.equal(changed.getState().rows.length, 0)
})

test('清空或切换记录后，迟到回显不会恢复旧名称', async () => {
  const pending = deferred()
  const { runtime } = fixture({ FIELD_PICKER_RESOLVE: () => pending.promise })
  const restoring = runtime.restore('001')
  await runtime.restore(null); pending.resolve(rows); await restoring
  assert.equal(runtime.getState().selected, null)
  assert.equal(runtime.getState().resolving, false)
})

test('关闭、重新搜索与切换数据源均撤销未完成的选择确认', async () => {
  for (const cancel of [runtime => runtime.close(), runtime => runtime.query(), runtime => runtime.configure({ schema, load: async () => [] })]) {
    const pending = deferred()
    const { runtime } = fixture({ FIELD_PICKER_RESOLVE: () => pending.promise })
    await runtime.open()
    let prepared = false
    const confirming = runtime.confirm(runtime.getState().rows[0], async () => { prepared = true; return {} })
    await cancel(runtime); pending.resolve(rows)
    assert.equal(await confirming, null); assert.equal(prepared, false)
  }
})

test('覆盖确认期间不修改表单，零/false不按空值处理，取消选择保留手工字段', async () => {
  const record = { customer: 'old', contact: '手工值', score: 5, active: true }
  const field = { fieldCode: 'customer', componentProps: { pickerSchema: schema, pickerMappings: [
    { sourcePath: 'selection.data.contact', targetPath: 'form.contact', overwrite: 'CONFIRM', clearOnEmpty: true },
    { sourcePath: 'selection.data.score', targetPath: 'form.score', overwrite: 'ALWAYS', clearOnEmpty: false },
    { sourcePath: 'selection.data.active', targetPath: 'form.active', overwrite: 'ALWAYS', clearOnEmpty: false }
  ] } }
  const pending = deferred()
  const patchPromise = preparePickerPatch(field, normalizePickerRows(rows, normalizePickerSchema(schema))[0], record, () => pending.promise)
  assert.equal(record.customer, 'old'); assert.equal(record.contact, '手工值')
  pending.resolve(false)
  assert.deepEqual(await patchPromise, { customer: '001', score: 0, active: false })
  assert.deepEqual(await preparePickerPatch(field, null, record), { customer: null })
  assert.throws(() => normalizePickerSchema({ ...schema, fields: [...schema.fields, { code: 'constructor', type: 'STRING' }] }), /编码非法/)
})

test('inputMapping仅映射业务参数，分页及所选业务键始终保留在input.picker', async () => {
  const calls = []
  const runtime = createFormDataSourceRuntime({ getForm: () => ({ id: 'f' }), executeDataSource: async request => { calls.push(request); return [] } })
  const field = { fieldCode: 'customer', dataSourceBindings: { FIELD_PICKER_RESOLVE: {
    extensionId: 'resolve-customers', inputMapping: { company: 'data.company', picker: { literal: { values: ['forged'] } } }
  } } }
  await runtime.executeOwnerUsage(field, 'FIELD_PICKER_RESOLVE', { record: { company: 'A' }, input: { picker: { values: ['001'] } } })
  assert.deepEqual(calls[0].input, { company: 'A', picker: { values: ['001'] } })
  assert.equal(calls[0].targetKey, 'customer')
})

test('固定字段描述直接查询和解析，不请求描述接口', async () => {
  const runtime = createDataPickerRuntime()
  const calls = []
  const records = [{ id: '001', name: '甲客户' }]
  runtime.configure({ schema: DEFAULT_PICKER_SCHEMA, dynamicSchema: false, load: async usage => {
    calls.push(usage)
    return usage === 'FIELD_PICKER_QUERY' ? { records, total: 1 } : records
  } })
  await runtime.open()
  assert.equal(runtime.getState().rows[0].value, '001')
  await runtime.restore('001')
  assert.equal(runtime.getState().selected.label, '甲客户')
  assert.deepEqual(calls, ['FIELD_PICKER_QUERY', 'FIELD_PICKER_RESOLVE'])
  assert.deepEqual(pickerQuerySchemaFields({ properties: { records: { items: { properties: {
    id: { type: 'string' }, enabled: { type: 'boolean', title: '启用' }, detail: { type: 'object' }
  } } } } }), [{ code: 'id', type: 'STRING', label: 'id' }, { code: 'enabled', type: 'BOOLEAN', label: '启用' }])
})

test('实体列表按当前草稿和存量实体属性附加筛选，确认按 ID 重新读取原始实体字段', async () => {
  const fields = [
    { fieldCode: 'name', fieldName: '成员姓名', showInList: true, isQuery: true },
    { fieldCode: 'employeeNo', fieldName: '工号', showInList: true }
  ]
  const calls = []
  const config = {
    pickerList: { entityCode: 'member', listKey: 'select', parameterMappings: [
      { parameter: 'projectId', sourceType: 'FIELD', sourceField: 'project_id' },
      { parameter: 'deptId', sourceType: 'ENTITY_FIELD', sourceField: 'deptId' },
      { parameter: 'active', sourceType: 'LITERAL', value: false }
    ] },
    pickerSchema: pickerListSchema(fields, [{ fieldCode: 'employeeNo', fieldType: 'STRING' }]),
    pickerMappings: [{ sourcePath: 'selection.data.employeeNo', targetPath: 'form.code', overwrite: 'ALWAYS', clearOnEmpty: false }]
  }
  const viewConfig = { pagination: { pageSize: 20 }, inputParameterSchema: { properties: {
    projectId: { type: 'string' }, deptId: { type: 'string' }, active: { type: 'boolean' }
  } }, inputParameterBindings: config.pickerList.parameterMappings.map(mapping => ({
    parameter: mapping.parameter, targetField: mapping.parameter, usage: 'FILTER'
  })) }
  const record = { project_id: 'draft-project' }
  const list = createEntityListPickerSource({
    getSchema: async () => ({ fields, viewConfig, configHash: 'release-hash' }),
    query: async (entity, key, request) => {
      calls.push({ entity, key, request })
      return { total: 1, records: [{ id: '0001', name: '成员甲',
        data: { employeeNo: request.selectionRecordId ? '最新工号' : '工号' }, extData: { employeeNo: '展示工号' } }] }
    }
  }, config, () => referenceListParameterState(config.pickerList.parameterMappings, record, [], { data: { deptId: 'saved-dept' } }), () => ({}))
  const runtime = createDataPickerRuntime()
  runtime.configure({ schema: config.pickerSchema, list })
  await runtime.open()
  assert.equal(runtime.getState().pageSize, 20)
  assert.equal(runtime.getState().rows[0].data.employeeNo, '展示工号')
  const patch = await runtime.confirm(runtime.getState().rows[0], selection =>
    preparePickerPatch({ fieldCode: 'member', componentProps: config }, selection, record))
  assert.deepEqual(patch, { member: '0001', code: '最新工号' })
  assert.deepEqual(calls[1], { entity: 'member', key: 'select', request: {
    pageNum: 1, pageSize: 1, selectionRecordId: '0001', scene: 'FORM_PICKER',
    configHash: 'release-hash', runtimeContext: {}, context: { parameters: { projectId: 'draft-project', deptId: 'saved-dept', active: false } }
  } })
  record.project_id = ''
  await runtime.open()
  assert.match(runtime.getState().error, /请先填写/)
  assert.equal(calls.length, 2, '缺少来源值时不发起列表查询')
})
