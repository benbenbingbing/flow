import test from 'node:test'
import assert from 'node:assert/strict'
import { applyRuntimeEventEffects } from '../dist/shared/form-runtime/eventEffects.js'
import { createFormActionRuntime } from '../dist/shared/form-action-runtime.js'
import { getProcessNodeStatus } from '../dist/shared/process-progress.js'
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }
const effect = (path, value, options = {}) => ({ effects: [{ type: 'FIELD_MAPPING', data: path.startsWith('form.') ? { form: { amount: value } } : path.startsWith('data.') ? { data: { amount: value } } : { amount: value }, mappings: [{ targetPath: path, ...options }] }] })
const handlers = record => ({ getRecord: () => record, setField: (key, value) => { record[key] = value } })
const runtime = () => createFormActionRuntime({ createBusinessTraceKey: () => 'test' })

test('表单事件按完整源路径取值，只对目标记录路径去掉 form/data 前缀', async () => {
  for (const path of ['form.amount', 'data.amount', 'amount']) {
    const record = { amount: 7 }
    await applyRuntimeEventEffects(effect(path, 100), handlers(record))
    assert.equal(record.amount, 100, path)
  }
})
test('空值不清空及 IF_EMPTY 覆盖规则在所有入口一致', async () => {
  const record = { amount: 7 }
  await applyRuntimeEventEffects(effect('form.amount', undefined, { clearOnEmpty: false }), handlers(record))
  await applyRuntimeEventEffects(effect('form.amount', 100, { overwrite: 'IF_EMPTY' }), handlers(record))
  assert.equal(record.amount, 7)
  record.amount = ''
  await applyRuntimeEventEffects(effect('form.amount', 100, { overwrite: 'IF_EMPTY' }), handlers(record))
  assert.equal(record.amount, 100)
})
test('覆盖确认取消保持原值，过期会话在等待确认后不能回填', async () => {
  const record = { amount: 7 }, gate = deferred()
  await applyRuntimeEventEffects(effect('form.amount', 100, { overwrite: 'CONFIRM' }), { ...handlers(record), confirmOverwrite: async () => false })
  assert.equal(record.amount, 7)
  let current = true
  const pending = applyRuntimeEventEffects(effect('form.amount', 100, { overwrite: 'CONFIRM' }), { ...handlers(record), confirmOverwrite: () => gate.promise, isCurrent: () => current })
  current = false; gate.resolve(true); await pending
  assert.equal(record.amount, 7)
})
test('效果按返回顺序执行，异步导航后仍保留关闭和父列表刷新', async () => {
  const record = {}, calls = [], gate = deferred()
  const pending = applyRuntimeEventEffects({ effects: [
    ...effect('form.amount', 5).effects, { type: 'MESSAGE', message: '已计算' },
    { type: 'OPEN_ROUTE', route: '/next' }, { type: 'CLOSE_FORM' }, { type: 'REFRESH_PARENT' }, { type: 'DOWNLOAD_TASK' }
  ] }, { ...handlers(record), message: value => calls.push(value.message), navigate: async () => { calls.push('navigate'); await gate.promise }, close: () => calls.push('close'), refresh: () => calls.push('refresh'), download: () => calls.push('download') })
  await new Promise(resolve => setImmediate(resolve))
  assert.equal(record.amount, 5); assert.deepEqual(calls, ['已计算', 'navigate'])
  gate.resolve(); await pending
  assert.deepEqual(calls, ['已计算', 'navigate', 'close', 'refresh', 'download'])
})
test('嵌套映射、旧结果形状与非法路径处理保持一致', async () => {
  const record = { detail: { kept: true } }
  await applyRuntimeEventEffects({ effects: [{ type: 'field_mapping', data: { form: { detail: { amount: 5 } } }, mappings: [{ targetPath: 'form.detail.amount' }] }] }, handlers(record))
  assert.deepEqual(record.detail, { kept: true, amount: 5 })
  await applyRuntimeEventEffects({ data: JSON.parse('{"form":{"amount":0,"__proto__.polluted":true}}') }, handlers(record))
  assert.equal(record.amount, 0); assert.equal({}.polluted, undefined)
})
test('宿主不支持的效果必须明确失败，不能静默报告成功', async () => {
  await assert.rejects(applyRuntimeEventEffects({ effects: [{ type: 'OPEN_ROUTE', route: '/missing' }] }, handlers({})), /不支持事件效果/)
})
test('统一动作在确认期间占锁，取消不执行，再次点击仍可执行', async () => {
  const { runFormAction } = runtime(), gate = deferred(), loadingState = { value: '' }
  let executes = 0, settled = 0
  const action = { key: 'fill', confirm: { enabled: true, message: '确认回填' } }
  const options = { loadingState, confirm: () => gate.promise, execute: () => { executes++ }, settled: () => { settled++ } }
  const first = runFormAction(action, options)
  assert.equal(loadingState.value, 'fill')
  assert.equal((await runFormAction(action, options)).status, 'busy')
  gate.resolve(false)
  assert.equal((await first).status, 'cancelled'); assert.equal(executes, 0); assert.equal(settled, 1); assert.equal(loadingState.value, '')
  await runFormAction(action, { ...options, confirm: async () => true })
  assert.equal(executes, 1); assert.equal(settled, 2)
})
test('校验失败不执行，执行错误释放锁且传给宿主', async () => {
  const { runFormAction } = runtime(), loadingState = { value: '' }
  const options = { loadingState, validate: async () => false, execute: () => assert.fail('不得执行') }
  assert.equal((await runFormAction({ key: 'fill', validateBeforeExecute: true }, options)).status, 'invalid')
  await assert.rejects(runFormAction({ key: 'fill' }, { loadingState, execute: () => { throw Error('服务失败') } }), /服务失败/)
  assert.equal(loadingState.value, '')
})
for (const phase of ['confirm', 'validate', 'execute']) test(`${phase} 等待期间更换记录，迟到动作不回填`, async () => {
  const { runFormAction } = runtime(), gate = deferred()
  let current = true, applied = false, executions = 0
  const pending = runFormAction({ key: 'fill', confirm: { enabled: true }, validateBeforeExecute: true }, {
    loadingState: { value: '' }, isCurrent: () => current,
    confirm: () => phase === 'confirm' ? gate.promise : true,
    validate: () => phase === 'validate' ? gate.promise : true,
    execute: () => { executions++; return phase === 'execute' ? gate.promise : {} },
    applyResult: () => { applied = true }
  })
  await new Promise(resolve => setImmediate(resolve))
  current = false; gate.resolve(true)
  assert.equal((await pending).status, 'stale'); assert.equal(applied, false)
  assert.equal(executions, phase === 'execute' ? 1 : 0)
})
test('回退重审节点优先活跃，其次取消，最后历史完成；兼容节点对象', () => {
  assert.equal(getProcessNodeStatus({ activeNodes: ['review'], completedNodes: ['review'], terminatedNodes: ['review'] }, 'review'), 'active')
  assert.equal(getProcessNodeStatus({ terminatedNodes: [{ nodeId: 'review' }], completedNodes: ['review'] }, 'review'), 'terminated')
  assert.equal(getProcessNodeStatus({ completedNodes: [{ id: 'review' }] }, 'review'), 'completed')
  assert.equal(getProcessNodeStatus({}, 'review'), 'pending')
})
