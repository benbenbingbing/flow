import assert from 'node:assert/strict'
import test from 'node:test'
import { withEntityStatusRuntimeForm, PROCESS_STATUS_OPTIONS, resolveProcessStatusLabel } from '../src/shared/entity-status-runtime.js'
import { normalizeEntityRecordForForm } from '../src/shared/form-runtime/index.js'
import { useProcessDetail } from '../src/composables/useProcessDetail.js'
import { resolveApprovalDialogTitle } from '../src/views/entity/components/approval/entityApprovalDisplay.js'

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
  const detail = useProcessDetail({ request: { get: () => new Promise(done => { resolve = done }) } })
  const old = detail.loadProcessDetail('old')
  detail.resetProcessDetail()
  resolve({ bpmnXml: 'old xml', processInstanceId: 'old' })
  assert.equal(await old, false)
  assert.equal(detail.bpmnXml.value, '')
  assert.deepEqual(detail.processRuntimeMetadata.value, {})
})

test('process details preserve configured entity status names and use shared fallbacks', async () => {
  for (const [entity, expected] of [
    [{ status: 'PENDING', _statusText: '财务复核中' }, '财务复核中'],
    [{ status: 'PENDING' }, '处理中'],
    [{ status: 'CUSTOM_REVIEW' }, 'CUSTOM_REVIEW']
  ]) {
    const detail = useProcessDetail({ request: { get: async () => ({ status: 'RUNNING', entityData: entity }) } })
    assert.equal(await detail.loadProcessFormRuntime('instance'), true)
    assert.equal(detail.entityData.value._statusText, expected)
    assert.equal(detail.progressData.value.status, 'RUNNING')
  }
})

test('form returned after switching records is ignored', async () => {
  let resolveForm
  const detail = useProcessDetail({ request: { get: () => new Promise(done => { resolveForm = done }) } })
  const old = detail.loadProcessFormRuntime('old')
  detail.resetProcessDetail()
  resolveForm({ entityData: { id: 'old' }, formConfig: { id: 'old-form' } })
  assert.equal(await old, false)
  assert.equal(detail.entityData.value, null)
  assert.equal(detail.formConfig.value, null)
})

test('entity dialog titles use biz lifecycle even when the viewed process has ended', async () => {
  const detail = useProcessDetail({ request: { get: async () => ({
    status: 'COMPLETED',
    entityData: { processStatus: 'RUNNING', status: 'DRAFT' }
  }) } })
  assert.equal(await detail.loadProcessFormRuntime('previous-instance'), true)
  assert.equal(detail.progressData.value.status, 'COMPLETED')
  assert.equal(resolveApprovalDialogTitle('数据详情', detail.entityData.value), '数据详情（运行中-草稿）')
})


test('withdrawal and cancelled tasks are distinct from approved history', async () => {
  const detail = useProcessDetail({ request: { get: async () => ({
    status: 'COMPLETED', endType: 'WITHDRAWN', endReason: '金额错误', cancelledNodes: ['review'],
    nodeHistory: [
      { nodeId: 'review', nodeName: '财务审批', status: 'CANCELLED', action: 'CANCELLED', assignee: 'finance', endTime: '2026-09-23' },
      { nodeId: 'WITHDRAW_1', nodeName: '流程撤回', status: 'WITHDRAWN', action: 'WITHDRAWN', assignee: 'starter', comment: '金额错误', endTime: '2026-09-23' }
    ]
  }) } })
  assert.equal(await detail.loadProcessDetail('old-instance'), true)
  assert.deepEqual(detail.progressData.value.terminatedNodes, ['review'])
  assert.equal(detail.progressData.value.endType, 'WITHDRAWN')
  assert.equal(detail.processHistory.value[0].status, 'WITHDRAWN')
  assert.match(detail.processHistory.value[0].description, /撤回.*金额错误/)
  assert.equal(detail.processHistory.value[1].status, 'CANCELLED')
  assert.match(detail.processHistory.value[1].description, /已取消/)
  assert.ok(detail.processHistory.value.every(item => !item.description.includes('通过')))
})

test('first screen reads only the task form, and lazy progress preserves edits', async () => {
  const calls = []
  const detail = useProcessDetail({ request: { get: async (url, options) => {
    calls.push({ url, options })
    return url.endsWith('/form-runtime')
      ? { entityData: { id: 'row', amount: 1 }, formConfig: { id: 'form', fields: [] }, approvalConfig: { enabled: true } }
      : { bpmnXml: 'diagram', nodeHistory: [], entityData: { amount: 999 }, formConfig: { id: 'unexpected' } }
  } } })
  assert.equal(await detail.loadProcessFormRuntime('instance', { taskId: 'parallel-task' }), true)
  assert.deepEqual(calls, [{ url: '/process-instance/instance/form-runtime', options: { params: { taskId: 'parallel-task' } } }])
  detail.entityData.value.amount = 2
  const form = detail.formConfig.value
  await detail.loadProcessDetail('instance')
  await detail.loadProcessDetail('instance')
  assert.equal(calls.length, 2)
  assert.equal(detail.entityData.value.amount, 2)
  assert.equal(detail.formConfig.value, form)
  assert.equal(detail.bpmnXml.value, 'diagram')
})

test('concurrent tabs share progress, failed reads can retry, and reset invalidates pending reads', async () => {
  let resolve
  let calls = 0
  const detail = useProcessDetail({ request: { get: () => { calls++; return new Promise(done => { resolve = done }) } } })
  const first = detail.loadProcessDetail('one')
  const second = detail.loadProcessDetail('one')
  assert.equal(first, second)
  resolve(null)
  assert.equal(await first, false)
  assert.ok(detail.progressError.value)
  const retry = detail.loadProcessDetail('one')
  resolve({ bpmnXml: 'valid' })
  assert.equal(await retry, true)
  assert.equal(detail.progressError.value, '')
  assert.equal(calls, 2)
  const stale = detail.loadProcessDetail('two')
  detail.resetProcessDetail()
  resolve({ bpmnXml: 'stale' })
  assert.equal(await stale, false)
  assert.equal(detail.bpmnXml.value, '')
})

test('switching back while another instance is pending reloads and ignores the late response', async () => {
  const pending = new Map()
  const detail = useProcessDetail({ request: { get: url => new Promise(done => pending.set(url, done)) } })
  let first = detail.loadProcessDetail('one')
  pending.get('/process-instance/one/progress')({ status: 'RUNNING', bpmnXml: 'one' })
  await first
  const other = detail.loadProcessDetail('two')
  assert.equal(detail.progressData.value.status, 'RUNNING', '加载进度时仍保留首屏提供的实例状态')
  first = detail.loadProcessDetail('one')
  pending.get('/process-instance/one/progress')({ status: 'RUNNING', bpmnXml: 'one current' })
  assert.equal(await first, true)
  pending.get('/process-instance/two/progress')({ bpmnXml: 'two late' })
  assert.equal(await other, false)
  assert.equal(detail.bpmnXml.value, 'one current')
})
