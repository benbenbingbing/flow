import test from 'node:test'
import assert from 'node:assert/strict'
import { createDataPickerRuntime, normalizePickerSchema, normalizePickerRows, preparePickerPatch } from '../src/shared/data-picker.js'
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
