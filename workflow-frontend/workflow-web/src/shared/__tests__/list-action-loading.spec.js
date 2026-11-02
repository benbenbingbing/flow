import assert from 'node:assert/strict'
import { test } from 'node:test'
import { effectScope, nextTick } from 'vue'
import { showsListActionLoading, useListActionLoading } from '../list-action-loading.js'
import { normalizeListActionForSave, listActionFingerprint } from '../list-config-design.js'

const view = { key: 'view', type: 'built-in' }
const custom = { key: 'custom', type: 'custom' }
const row = { id: 'a' }
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}
function runner(t) {
  const scope = effectScope()
  t.after(() => scope.stop())
  return scope.run(() => useListActionLoading())
}

test('旧配置默认开启；显式关闭能保存，恢复默认后不遗留 false', () => {
  for (const showLoading of [undefined, null, true, false]) {
    const button = { ...custom, showLoading }
    assert.equal(showsListActionLoading(button), showLoading !== false)
    for (const position of ['ROW', 'TOOLBAR']) {
      const saved = normalizeListActionForSave(button, position)
      assert.equal(showsListActionLoading({ ...custom, ...saved.actionParams }), showLoading !== false)
      assert.equal(Object.hasOwn(saved.actionParams, 'showLoading'), showLoading === false)
    }
  }
  assert.equal(showsListActionLoading({ ...view, showLoading: false }), true)
  assert.notEqual(listActionFingerprint(custom, 'ROW'), listActionFingerprint({ ...custom, showLoading: false }, 'ROW'))
})

test('立即反馈，同一记录和共用弹窗阻止重复执行，等待真正完成后释放', async t => {
  const loading = runner(t), wait = deferred()
  let calls = 0
  const first = loading.run(view, row, async () => { calls++; await wait.promise })
  assert.equal(loading.isLoading(view, { id: 'a' }), true, '刷新后的行对象仍共享状态')
  assert.equal(loading.isLoading(view, { id: 'b' }), false, '其他记录不一起转圈')
  await loading.run(view, row, () => { calls++ })
  await loading.run({ key: 'edit', type: 'built-in' }, { id: 'b' }, () => { calls++ })
  await nextTick()
  assert.equal(calls, 1)
  wait.resolve()
  await first
  assert.equal(loading.isDisabled(view, row), false)
  assert.equal(loading.isLoading(view, row), false)
})

test('关闭展示仍防重；失败和提前返回均恢复', async t => {
  const loading = runner(t), wait = deferred(), button = { ...custom, showLoading: false }
  let invoked = false
  const first = loading.run(button, row, () => { invoked = true; return wait.promise })
  assert.equal(invoked, true, '不展示加载时保留同步点击语义，避免影响弹出窗口等扩展')
  assert.equal(loading.isLoading(button, row), false)
  assert.equal(loading.isDisabled(button, row), true)
  wait.reject(Error('网络失败'))
  await assert.rejects(first, /网络失败/)
  assert.equal(loading.isDisabled(button, row), false)
  await loading.run(button, row, () => undefined)
  assert.equal(loading.isDisabled(button, row), false)
})

test('删除确认期间锁定但不转圈，确认提交后开始 loading', async t => {
  const loading = runner(t), wait = deferred(), started = deferred(), button = { key: 'delete', type: 'built-in' }
  let execution
  const first = loading.run(button, row, context => { execution = context; started.resolve(); return wait.promise })
  assert.equal(loading.isLoading(button, row), false)
  assert.equal(loading.isDisabled(button, row), true)
  await started.promise
  execution.startLoading()
  assert.equal(loading.isLoading(button, row), true)
  wait.resolve()
  await first
  assert.equal(loading.isDisabled(button, row), false)
})

test('页面切换或卸载会清理状态，旧请求完成不能释放新请求的锁', async t => {
  const loading = runner(t), previous = deferred(), current = deferred()
  const oldTask = loading.run(view, row, () => previous.promise)
  await nextTick()
  loading.reset()
  const newTask = loading.run(view, row, () => current.promise)
  previous.resolve()
  await oldTask
  assert.equal(loading.isLoading(view, row), true)
  current.resolve()
  await newTask
  const scope = effectScope()
  const disposable = scope.run(() => useListActionLoading())
  let calls = 0
  const pending = disposable.run(view, row, () => { calls++ })
  scope.stop()
  await pending
  assert.equal(calls, 0)
  assert.equal(disposable.isDisabled(view, row), false)
})
