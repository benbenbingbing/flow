import test from 'node:test'
import assert from 'node:assert/strict'
import { createInboxPageLoader, createInboxPageState } from '../inboxPageState.js'
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no }); return { promise, resolve, reject } }

test('迟到成功不能覆盖新查询，迟到失败也不能覆盖新查询状态', async () => {
  for (const fails of [false, true]) {
    const old = deferred(), recent = deferred(), state = createInboxPageState()
    let count = 0
    const loader = createInboxPageLoader(state, { done: () => (++count === 1 ? old : recent).promise })
    const first = loader.load('done', { keyword: 'old' }), second = loader.load('done', { keyword: 'new' })
    recent.resolve({ records: [{ id: 'new' }], total: 1 }); await second
    if (fails) old.reject(Error('旧请求失败')); else old.resolve({ records: [{ id: 'old' }], total: 2 })
    await first
    assert.equal(state.done.rows[0].id, 'new'); assert.equal(state.done.total, 1)
    assert.equal(state.done.error, ''); assert.equal(state.done.loading, false)
  }
})
test('页签加载状态独立，旧请求 finally 不关闭新请求 loading', async () => {
  const old = deferred(), recent = deferred(), cc = deferred(), state = createInboxPageState()
  let count = 0
  const loader = createInboxPageLoader(state, { done: () => (++count === 1 ? old : recent).promise, cc: () => cc.promise })
  const first = loader.load('done'), second = loader.load('done'), third = loader.load('cc')
  old.resolve({}); cc.resolve([]); await Promise.all([first, third])
  assert.equal(state.done.loading, true); assert.equal(state.cc.loading, false)
  recent.resolve({}); await second; assert.equal(state.done.loading, false)
})
test('能力补齐与列表同代提交，卸载后响应不再提交', async () => {
  const gate = deferred(), state = createInboxPageState()
  let counter = 0
  const loader = createInboxPageLoader(state, { todo: async () => ({ records: [{ id: ++counter }] }) }, {
    prepareRows: async (_key, rows) => { if (rows[0].id === 1) await gate.promise; return rows }
  })
  const first = loader.load('todo'); await Promise.resolve()
  await loader.load('todo'); assert.equal(state.todo.rows[0].id, 2)
  gate.resolve(); await first; assert.equal(state.todo.rows[0].id, 2)
  const pending = loader.load('todo'); loader.dispose(); await pending
  assert.equal(state.todo.rows[0].id, 2)
})
