import test from 'node:test'
import assert from 'node:assert/strict'
import { createFormDataSourceRuntime, getFieldOptionQueryKey } from '../dist/shared/form-runtime/dataSourceRuntime.js'
import { applyRuntimeFieldDefaults } from '../dist/shared/form-runtime/index.js'

test('加载后事件按顺序回填后再判断条件，预校验只调用已启用且允许的步骤', async () => {
  const form = { id: 'form-1', dataSourceBindings: {
    AFTER_LOAD: [
      { extensionId: 'load', condition: { path: 'input.mode', equals: 'view' } },
      { extensionId: 'fill', condition: { path: 'data.code', equals: 'A' }, inputMapping: { code: 'data.code' } },
      { extensionId: 'skipped', enabled: false },
      { extensionId: 'unmatched', condition: { path: 'data.code', equals: 'B' } }
    ],
    BEFORE_SUBMIT: [
      { extensionId: 'server-only', sideEffectFree: true },
      { extensionId: 'wrong-mode', condition: { path: 'input.mode', equals: 'create' }, sideEffectFree: true, clientPrevalidate: true },
      { extensionId: 'check', condition: { path: 'data.code', equals: 'A' }, sideEffectFree: true, clientPrevalidate: true }
    ]
  } }
  const calls = []
  const record = {}
  const runtime = createFormDataSourceRuntime({
    getForm: () => form, getRecord: () => record,
    executeDataSource: async request => {
      calls.push(request)
      return request.extensionId === 'load' ? { code: 'A' } : { label: '已回填' }
    }
  })
  await runtime.initialize({ form, record, runtimeContext: { mode: 'view' } })
  await runtime.prevalidateBeforeSubmit({ form, runtimeContext: { mode: 'edit' } })
  assert.deepEqual(record, { code: 'A', label: '已回填' })
  assert.deepEqual(calls.map(call => call.extensionId), ['load', 'fill', 'check'])
  assert.deepEqual(calls[1].input, { code: 'A' })
})

test('实体选项联动只使用映射字段触发查询，并保留空值、零与布尔值', async () => {
  const requests = []
  const runtime = createFormDataSourceRuntime({ executeDataSource: async request => {
    requests.push(request)
    return [{ value: 'A1', label: '库位 A1' }]
  } })
  const field = {
    fieldCode: 'bin', dataSourceBindings: { FIELD_OPTIONS: { extensionId: 'entity-options' } },
    componentProps: { linkageRules: { optionsLinkage: { mode: 'query', parameterMappings: [
      { fieldCode: 'warehouse_id', parameter: 'warehouseId' },
      { fieldCode: 'active', parameter: 'active' },
      { fieldCode: 'floor', parameter: 'floor' },
      { fieldCode: 'ctx.form.currentMode', parameter: 'mode' }
    ] } } }
  }
  const record = { warehouse_id: 'A', active: false, floor: 0, note: '备注' }
  assert.equal(getFieldOptionQueryKey(field, record), getFieldOptionQueryKey(field, { ...record, note: '无关改动' }))
  assert.notEqual(getFieldOptionQueryKey(field, record), getFieldOptionQueryKey(field, { ...record, warehouse_id: 'B' }))
  assert.deepEqual(await runtime.loadOptions(field, { form: { id: 'form' }, record, mode: 'edit' }), [{ value: 'A1', label: '库位 A1' }])
  assert.equal(requests[0].extensionId, 'entity-options')
  assert.equal(requests[0].formMode, 'edit')
  assert.deepEqual(requests[0].input.optionParams, { warehouseId: 'A', active: false, floor: 0, mode: 'edit' })
  assert.deepEqual(requests[0].input.formData, record)
  await runtime.loadOptions(field, { form: { id: 'form' }, record: {}, mode: 'edit' })
  assert.deepEqual(requests[1].input.optionParams, { warehouseId: null, active: null, floor: null, mode: 'edit' })
})

test('历史发布的数据源绑定完整传递服务和操作，新接口不携带旧路由', async () => {
  const requests = []
  const form = { id: 'form-1', dataSourceBindingsDocument: JSON.stringify({
    FORM_INIT: { serviceId: 'old-service', operationCode: 'initialize' },
    AFTER_LOAD: { extensionId: 'new-interface', serviceId: 'old-service', operationCode: 'ignored-method' }
  }) }
  const record = { deptId: '', userId: 'selected-user' }
  const runtime = createFormDataSourceRuntime({ getForm: () => form, getRecord: () => record,
    executeDataSource: async request => { requests.push(request); return {} } })
  // 通过 owner 执行会先规范化一次，再经过 execute；兼容坐标必须跨两次规范化保留。
  await runtime.executeOwnerUsage(form, 'FORM_INIT')
  await runtime.executeOwnerUsage(form, 'AFTER_LOAD')
  assert.equal(requests[0].extensionId, 'old-service')
  assert.equal(requests[0].legacyOperationCode, 'initialize')
  assert.deepEqual(requests[0].input.formData, record)
  assert.equal(requests[1].extensionId, 'new-interface')
  assert.equal('legacyOperationCode' in requests[1], false)
  assert.equal('operationCode' in requests[1], false)
})

test('新增表单在接口初始化后按节点公式填空且同一次打开只执行一次', async () => {
  const node = (id, valueFormula, parentId) => ({
    id, parentId, nodeType: 'FIELD', bindingType: 'ENTITY_FIELD', bindingRef: id,
    propsDocument: JSON.stringify({
      fieldCode: id, componentProps: { linkageRules: { valueFormula, valueFormulaMode: 'initial' } }
    })
  })
  const form = { id: 'date-form', dataSourceBindings: { FORM_INIT: { extensionId: 'init' } } }
  const nodes = [
    node('end', 'ADD_DAYS(${start}, 7)'),
    node('start', 'ADD_DAYS(${received}, 1)'),
    node('supplied', 'TODAY()'),
    { id: 'details', nodeType: 'SUB_FORM' },
    node('child_date', 'TODAY()', 'details')
  ]
  const record = { supplied: '2024-02-27' }
  const runtime = createFormDataSourceRuntime({
    executeDataSource: async () => ({ received: '2024-02-28' })
  })
  const options = { form, nodes, record, runtimeContext: { mode: 'create' } }
  await runtime.initialize(options)
  assert.deepEqual(record, { supplied: '2024-02-27', received: '2024-02-28', start: '2024-02-29', end: '2024-03-07' })
  record.end = ''
  await runtime.initialize(options)
  assert.equal(record.end, '')
  const existing = { received: '2024-02-28', start: '' }
  await runtime.initialize({ ...options, record: existing, runtimeContext: { mode: 'edit' } })
  assert.equal(existing.start, '')
  const newRow = {}
  await runtime.initialize({ ...options, record: newRow, initializationKey: 'new-row', runtimeContext: { mode: 'edit', rootParentId: 'details', row: { isNew: true } } })
  assert.match(newRow.child_date, /^\d{4}-\d{2}-\d{2}$/)
  assert.equal(newRow.supplied, undefined)
})

test('默认值接口只填新增记录，已退役的计算绑定在初始化时跳过', async () => {
  const requests = []
  const runtime = createFormDataSourceRuntime({ executeDataSource: async request => {
    requests.push(request.extensionId)
    return { value: request.extensionId === 'node-api' ? false : '预填' }
  } })
  const legacyField = { fieldCode: 'legacy', dataSourceBindings: { FIELD_DEFAULT: { extensionId: 'legacy-api' } } }
  const fields = [legacyField, { fieldCode: 'active', dataSourceBindingsDocument: JSON.stringify({
    FIELD_DEFAULT: { extensionId: 'stale-api' },
    FIELD_COMPUTE: { extensionId: 'retired-api' }
  }) }]
  const node = (code, parentId, extensionId) => ({
    id: code, parentId, nodeKey: code, nodeType: 'FIELD', bindingType: 'ENTITY_FIELD',
    props: { fieldCode: code, defaultValueMode: 'dataSource' },
    dataSourceBindings: { FIELD_DEFAULT: { extensionId, outputMapping: { value: 'data.value' } } }
  })
  const nodes = [node('active', '', 'node-api'), { id: 'details', nodeType: 'REPEATER' },
    node('child', 'details', 'child-api')]
  const form = { id: 'defaults' }
  for (const mode of ['edit', 'view', 'approve']) {
    const record = { legacy: '', active: null }
    await runtime.initialize({ form, fields, nodes, record, runtimeContext: { mode } })
    assert.deepEqual(record, { legacy: '', active: null })
  }
  assert.deepEqual(requests, [])
  const record = {}
  await runtime.initialize({ form, fields, nodes, record, runtimeContext: { mode: 'create' } })
  assert.deepEqual(record, { legacy: '预填', active: false })
  assert.deepEqual(requests, ['legacy-api', 'node-api'])
  const supplied = { legacy: 0, active: false }
  await runtime.initialize({ form, fields, nodes, record: supplied, initializationKey: 'supplied', runtimeContext: { mode: 'create' } })
  assert.deepEqual(supplied, { legacy: 0, active: false })
  const row = {}
  await runtime.initialize({ form, nodes, record: row, initializationKey: 'row', runtimeContext: {
    mode: 'edit', rootParentId: 'details', row: { isNew: true }
  } })
  assert.deepEqual(row, { child: '预填' })
  assert.deepEqual(requests, ['legacy-api', 'node-api', 'child-api'])
})

test('显式默认值来源覆盖实体静态值，公式可读取页面参数', async () => {
  const fields = ['none', 'inherit', 'fixed', 'due'].map(fieldCode => ({ fieldCode, defaultValue: 'legacy' }))
  const nodes = [
    ['none', 'none'],
    ['inherit', 'inherit'],
    ['fixed', 'fixed', { defaultValue: false }],
    ['due', 'formula', { defaultFormula: 'ADD_DAYS("2026-11-25", ${param.days})' }]
  ].map(([fieldCode, defaultValueMode, config]) => ({
    id: fieldCode, nodeKey: fieldCode, nodeType: 'FIELD', bindingType: 'ENTITY_FIELD',
    props: { fieldCode, defaultValueMode, ...config }
  }))
  const form = { id: 'sources', fields, nodes, viewConfig: { inputParameterSchema: {
    properties: { days: { type: 'integer', default: 3 } }
  } } }
  const record = applyRuntimeFieldDefaults({}, form, fields.map(field => ({ ...field, defaultValue: 'entity' })))
  assert.deepEqual(record, { inherit: 'entity', fixed: false })
  const runtime = createFormDataSourceRuntime({})
  await runtime.initialize({ form, fields, nodes, record, runtimeContext: { mode: 'create', params: { days: 2 } } })
  assert.deepEqual(record, { inherit: 'entity', fixed: false, due: '2026-11-27' })
})
