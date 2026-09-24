import assert from 'node:assert/strict'
import test from 'node:test'
import { withEntityStatusRuntimeForm, PROCESS_STATUS_OPTIONS, resolveProcessStatusLabel } from '../src/shared/entity-status-runtime.js'
import { normalizeEntityRecordForForm } from '../src/shared/form-runtime/index.js'
import { useProcessDetail } from '../src/composables/useProcessDetail.js'

test('lifecycle options are independent, readonly, and available to forms', () => {
  const form = withEntityStatusRuntimeForm({ fields: [{ id: 'f', fieldCode: 'processStatus' }], nodes: [{ nodeType: 'FIELD', bindingRef: 'f' }] }, [], [{ statusCode: 'RUNNING', statusName: '业务自定义状态' }])
  assert.deepEqual(form.fields[0].options, PROCESS_STATUS_OPTIONS)
  assert.equal(form.fields[0].editable, false)
  assert.equal(form.nodes[0].props.disabled, true)
  assert.equal(resolveProcessStatusLabel('COMPLETED'), '已完成')
  assert.equal(normalizeEntityRecordForForm({ processStatus: 'RUNNING', data: { processStatus: 'FORGED' } }).processStatus, 'RUNNING')
})

test('discarded progress requests cannot repopulate a newly opened record', async () => {
  let resolve
  let historyCalls = 0
  const detail = useProcessDetail({ request: { get: () => new Promise(done => { resolve = done }) }, getProcessHistory: async () => { historyCalls++; return [] } })
  const old = detail.loadProcessDetail('old')
  detail.resetProcessDetail()
  resolve({ bpmnXml: 'old xml', processInstanceId: 'old' })
  assert.equal(await old, false)
  assert.equal(detail.bpmnXml.value, '')
  assert.deepEqual(detail.processRuntimeMetadata.value, {})
  assert.equal(historyCalls, 0)
})

test('process details preserve configured entity status names and use shared fallbacks', async () => {
  for (const [entity, expected] of [
    [{ status: 'PENDING', _statusText: '财务复核中' }, '财务复核中'],
    [{ status: 'PENDING' }, '处理中'],
    [{ status: 'CUSTOM_REVIEW' }, 'CUSTOM_REVIEW']
  ]) {
    const detail = useProcessDetail({ request: { get: async () => ({ status: 'RUNNING', entityData: entity }) }, getProcessHistory: async () => [] })
    assert.equal(await detail.loadProcessDetail('instance'), true)
    assert.equal(detail.entityData.value._statusText, expected)
    assert.equal(detail.progressData.value.status, 'RUNNING')
  }
})

test('history returned after switching records is also ignored', async () => {
  let resolveHistory
  const detail = useProcessDetail({ request: { get: async () => ({ processInstanceId: 'old' }) }, getProcessHistory: () => new Promise(done => { resolveHistory = done }) })
  const old = detail.loadProcessDetail('old')
  await Promise.resolve()
  detail.resetProcessDetail()
  resolveHistory([{ taskName: 'old task' }])
  assert.equal(await old, false)
  assert.deepEqual(detail.processHistory.value, [])
})


test('withdrawal and cancelled tasks are distinct from approved history', async () => {
  const detail = useProcessDetail({ request: { get: async () => ({
    status: 'COMPLETED', endType: 'WITHDRAWN', endReason: '金额错误', cancelledNodes: ['review'],
    nodeHistory: [
      { nodeId: 'review', nodeName: '财务审批', status: 'CANCELLED', action: 'CANCELLED', assignee: 'finance', endTime: '2026-09-23' },
      { nodeId: 'WITHDRAW_1', nodeName: '流程撤回', status: 'WITHDRAWN', action: 'WITHDRAWN', assignee: 'starter', comment: '金额错误', endTime: '2026-09-23' }
    ]
  }) }, getProcessHistory: async () => { throw new Error('should use node history') } })
  assert.equal(await detail.loadProcessDetail('old-instance'), true)
  assert.deepEqual(detail.progressData.value.terminatedNodes, ['review'])
  assert.equal(detail.progressData.value.endType, 'WITHDRAWN')
  assert.equal(detail.processHistory.value[0].status, 'WITHDRAWN')
  assert.match(detail.processHistory.value[0].description, /撤回.*金额错误/)
  assert.equal(detail.processHistory.value[1].status, 'CANCELLED')
  assert.match(detail.processHistory.value[1].description, /已取消/)
  assert.ok(detail.processHistory.value.every(item => !item.description.includes('通过')))
})
