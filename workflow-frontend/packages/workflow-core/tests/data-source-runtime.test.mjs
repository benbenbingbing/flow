import test from 'node:test'
import assert from 'node:assert/strict'
import { createFormDataSourceRuntime } from '../dist/shared/form-runtime/dataSourceRuntime.js'

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
