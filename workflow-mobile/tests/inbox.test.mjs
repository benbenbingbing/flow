import test from 'node:test'
import assert from 'node:assert/strict'
import { createInboxLoader, createInboxState } from '../src/inbox.js'
import { buildMobileFormTree, buildMobileFormPages, groupsContainingField } from '../../packages/workflow-mobile-ui/src/form/mobileFormTree.js'

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

test('仅提升根级 Tab，区块内的 Tab 保留；关联内容跟随所属页签且只出现一次', () => {
  const nodes = [
    { id: 'root-tabs', nodeType: 'TAB_SET' }, { id: 'a', nodeType: 'TAB', parentId: 'root-tabs', props: { label: '第一页' } },
    { id: 'a-field', nodeType: 'FIELD', parentId: 'a' },
    { id: 'section', nodeType: 'SECTION' }, { id: 'nested-tabs', nodeType: 'TAB_SET', parentId: 'section' },
    { id: 'nested-tab', nodeType: 'TAB', parentId: 'nested-tabs', props: { label: '区块内页签' } }, { id: 'nested-field', nodeType: 'FIELD', parentId: 'nested-tab' }
  ]
  const form = { nodes }, fields = [{ id: 'a-field', fieldCode: 'first' }, { id: 'nested-field', fieldCode: 'nested' }]
  const tree = buildMobileFormTree(form, fields, { form, mode: 'view' })
  const related = [{ compositionKey: 'global', anchorType: 'FORM' }, { compositionKey: 'tab-related', anchorType: 'FORM_NODE', anchorKey: 'a-field' }]
  const layout = buildMobileFormPages(form, tree, related, true)
  assert.deepEqual(layout.pages.map(page => page.name), ['basic', 'form_tab_a'])
  assert.equal(layout.pages[0].items[0].children[0].title, '区块内页签')
  assert.equal(layout.pages[1].items[0].field.fieldCode, 'first')
  assert.deepEqual(layout.pages.map(page => page.relatedContents.map(item => item.compositionKey)), [['global'], ['tab-related']])
  assert.deepEqual(buildMobileFormPages(form, tree, related).pages[0].items, tree)
})

test('首次进入与激活共用请求，显式刷新和失效仍可重新加载', async () => {
  const state = createInboxState(), pending = []
  const load = createInboxLoader(state, { getDoneList: () => new Promise(resolve => pending.push(resolve)) })
  const initial = load.ensure('done')
  assert.equal(load.ensure('done'), initial)
  assert.equal(load('done'), initial)
  assert.equal(pending.length, 1)
  const refreshed = load('done', true)
  pending[0]({ records: [{ taskId: 'obsolete' }], total: 1 }); await initial
  assert.equal(state.done.initialized, false)
  pending[1]({ records: [{ taskId: 'fresh' }], total: 1 }); await refreshed
  await load.ensure('done')
  assert.equal(pending.length, 2)
  Object.assign(state.done, { initialized: false, generation: state.done.generation + 1 })
  const invalidated = load.ensure('done')
  pending[2]({ records: [], total: 0 }); await invalidated
  assert.deepEqual(state.done.rows, [])
})

test('实际 Inbox.vue 在 KeepAlive 首次挂载只请求一遍首页', async () => {
  const vue = await import('vue')
  const { readFile } = await import('node:fs/promises')
  const source = await readFile(new URL('../src/pages/Inbox.vue', import.meta.url), 'utf8')
  const script = source.match(/<script setup>([\s\S]*?)<\/script>/)[1].replace(/^import .*$/gm, '')
  const { INBOXES } = await import('../src/inbox.js')
  const requests = []
  const values = {
    ...vue, INBOXES, createInboxState, createInboxLoader,
    defineOptions: () => {}, useRoute: () => vue.reactive({ params: { kind: 'done' } }),
    useRouter: () => ({ options: { history: { state: {} } } }),
    session: { userInfo: {} }, tasks: { getDoneList: () => new Promise(resolve => requests.push(resolve)), getStatistics: async () => ({}) },
    onSessionCleared: () => () => {}, onInboxesInvalidated: () => () => {},
    window: { scrollY: 0, scrollTo: () => {} }
  }
  const setup = new Function(...Object.keys(values), script)
  const node = () => ({ children: [] })
  const renderer = vue.createRenderer({
    createElement: node, createText: node, createComment: node,
    setText() {}, setElementText() {}, patchProp() {}, parentNode: n => n.parent,
    nextSibling: () => null, remove() {}, insert(n, parent) { n.parent = parent; parent.children.push(n) }
  })
  const Inbox = { setup() { setup(...Object.values(values)); return () => vue.h('div') } }
  const app = renderer.createApp({ render: () => vue.h(vue.KeepAlive, null, { default: () => vue.h(Inbox) }) })
  app.mount(node())
  await vue.nextTick()
  assert.equal(requests.length, 1)
  requests[0]({ records: [], total: 0 })
  await new Promise(resolve => setImmediate(resolve))
  app.unmount()
})
