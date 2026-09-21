import test from 'node:test'
import assert from 'node:assert/strict'
import { createInboxLoader, createInboxState } from '../src/inbox.js'
import { buildMobileFormTree, groupsContainingField } from '../../packages/workflow-mobile-ui/src/form/mobileFormTree.js'

test('刷新后旧请求不得覆盖新列表，其它列表状态独立保留', async () => {
  const state = createInboxState(), pending = []
  const load = createInboxLoader(state, { getTodoList: () => new Promise(resolve => pending.push(resolve)) })
  const old = load('todo'), fresh = load('todo', true)
  pending[1]({ records: [{ taskId: 'new' }], total: 1 }); await fresh
  pending[0]({ records: [{ taskId: 'old' }], total: 1 }); await old
  assert.equal(state.todo.rows[0].taskId, 'new'); assert.equal(state.done.initialized, false)
})

test('分页去重且加载失败保留已有记录', async () => {
  const state = createInboxState(); let count = 0
  const load = createInboxLoader(state, { getTodoList: async () => { count++; if (count === 3) throw new Error('离线'); return { records: count === 1 ? [{ taskId: 'a' }, { taskId: 'b' }] : [{ taskId: 'b' }, { taskId: 'c' }], total: 6 } } }, 2)
  await load('todo'); await load('todo'); await load('todo')
  assert.deepEqual(state.todo.rows.map(item => item.taskId), ['a', 'b', 'c']); assert.equal(state.todo.error, '离线'); assert.equal(state.todo.page, 2)
})

test('表单 Tab 转折叠分组保留默认项与错误祖先路径', () => {
  const nodes = [{ id: 'tabs', nodeType: 'TAB_SET', props: { defaultActiveTabKey: 'tab-b' } }, { id: 'a', nodeKey: 'tab-a', parentId: 'tabs', nodeType: 'TAB' }, { id: 'b', nodeKey: 'tab-b', parentId: 'tabs', nodeType: 'TAB' }, { id: 'f1', parentId: 'a', nodeType: 'FIELD' }, { id: 'f2', parentId: 'b', nodeType: 'FIELD' }]
  const form = { nodes }, fields = [{ id: 'f1', fieldCode: 'first' }, { id: 'f2', fieldCode: 'last' }]
  const tree = buildMobileFormTree(form, fields, { form, mode: 'view' })
  assert.deepEqual(tree.map(item => item.kind), ['group', 'group']); assert.equal(tree[1].defaultExpanded, true); assert.equal(tree[0].defaultExpanded, false)
  assert.deepEqual(groupsContainingField(tree, 'last'), ['b'])
})
