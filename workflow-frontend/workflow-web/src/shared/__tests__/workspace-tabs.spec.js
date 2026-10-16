import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { createRenderer, h, provide, ref } from 'vue'
import { useWorkspaceTabsStore } from '../../stores/workspaceTabs.js'
import { useWorkspacePage } from '../../composables/useWorkspacePage.js'
import { snapshotWorkspaceRoute, workspacePageKey, workspaceRouteKey } from '../workspace-tabs.js'

const route = (path, query = {}) => ({ path, fullPath: path + (Object.keys(query).length ? '?' + new URLSearchParams(query) : ''),
  query, params: {}, meta: { title: path }, matched: [] })
const create = () => { setActivePinia(createPinia()); return useWorkspaceTabsStore() }

// 同一业务页面的页码/定位变更复用标签，实体/列表入口参数不同必须隔离。
assert.equal(workspaceRouteKey(route('/orders', { page: '1', entityId: 'A' })), workspaceRouteKey(route('/orders', { entityId: 'A', page: '2' })))
assert.notEqual(workspaceRouteKey(route('/orders', { entityId: 'A' })), workspaceRouteKey(route('/orders', { entityId: 'B' })))
assert.notEqual(workspaceRouteKey(route('/entity-list/order/main')), workspaceRouteKey(route('/entity-list/customer/main')))
const original = route('/orders', { entityId: 'A' })
const copied = snapshotWorkspaceRoute(original)
original.query.entityId = 'B'
assert.equal(copied.query.entityId, 'A')

// 模式切换不能重建当前实例；多标签导航保留旧快照及用户页面状态。
{
  const store = create()
  const first = store.visit(route('/a'))
  await store.setEnabled(true)
  store.visit(route('/b'))
  assert.equal(store.tabs.length, 2)
  assert.equal(store.tabs[0], first)
  store.visit(route('/a', { page: '2' }))
  assert.equal(store.tabs.length, 2)
  assert.equal(store.tabs[0], first)
  assert.equal(store.tabs[1].route.path, '/b')
  await store.setEnabled(false)
  assert.deepEqual(store.tabs.map(tab => tab.route.path), ['/a'])
  assert.equal(store.tabs[0], first)
}

// 后台标签的脏表单必须检查；取消关闭和关闭模式均不销毁页面。
{
  const store = create()
  await store.setEnabled(true)
  const first = store.visit(route('/a'))
  const unregister = store.registerGuard(first.key, { isDirty: () => true, message: '未保存' })
  store.visit(route('/b'))
  store.configureConfirm(async () => false)
  assert.equal(await store.close(first.key, () => assert.fail('后台标签不应导航')), false)
  assert.equal(await store.setEnabled(false), false)
  assert.equal(store.enabled, true)
  assert.equal(store.tabs.length, 2)
  store.configureConfirm(async dirty => { assert.equal(dirty[0].title, '/a'); return true })
  assert.equal(await store.close(first.key, () => assert.fail('后台标签不应导航')), true)
  assert.equal(store.tabs.length, 1)
  unregister()
}

// 当前页关闭时导航被取消/权限拒绝，标签必须保留；成功后才清理缓存目录。
{
  const store = create()
  await store.setEnabled(true)
  const first = store.visit(route('/a'))
  const second = store.visit(route('/b'))
  assert.equal(await store.close(second.key, async () => false), false)
  assert.equal(store.tabs.length, 2)
  assert.equal(await store.close(second.key, async path => { store.visit(route(path)); return true }), true)
  assert.equal(store.activeKey, first.key)
  assert.equal(store.tabs.length, 1)
  assert.equal(await store.close(first.key, async path => { assert.equal(path, '/home'); store.visit(route(path)); return true }), true)
  assert.equal(await store.close(store.activeKey, async () => assert.fail('首页不能关闭')), false)
}

// 账号在异步确认期间切换，旧确认不得清理新账号页面或恢复旧模式。
{
  const store = create()
  await store.setEnabled(true)
  const first = store.visit(route('/a'))
  store.registerGuard(first.key, { isDirty: () => true })
  store.visit(route('/b'))
  let complete
  store.configureConfirm(() => new Promise(resolve => { complete = resolve }))
  const closing = store.setEnabled(false)
  store.clear()
  store.visit(route('/new-account'))
  complete(true)
  assert.equal(await closing, false)
  assert.deepEqual(store.tabs.map(tab => tab.route.path), ['/new-account'])
  assert.equal(store.busy, false)
}

// 新账号打开同一路由后，旧缓存实例延迟卸载不能撤销新账号的未保存保护。
{
  const store = create()
  const first = store.visit(route('/a'))
  const unregisterOld = store.registerGuard(first.key, { isDirty: () => true })
  store.clear()
  const next = store.visit(route('/a'))
  store.registerGuard(next.key, { isDirty: () => true })
  unregisterOld()
  assert.equal(store.hasUnsavedChanges(), true)
}

// 暂停后台弹窗只能隐藏展示，不能清除逻辑打开状态；其未保存检查直到页面销毁才释放。
{
  const store = create()
  const tab = store.visit(route('/a'))
  const active = ref(true), visible = ref(true)
  let presented
  const renderer = createRenderer({
    createComment: () => ({}), insert() {}, remove() {}, parentNode: () => null, nextSibling: () => null
  })
  const child = { setup() {
    const page = useWorkspacePage()
    presented = page.visibleWhenActive(visible)
    page.registerGuard(() => visible.value)
    return () => null
  } }
  const app = renderer.createApp({ setup() {
    provide(workspacePageKey, { active, registerGuard: guard => store.registerGuard(tab.key, guard) })
    return () => h(child)
  } })
  app.mount({})
  assert.equal(presented.value, true)
  active.value = false
  assert.equal(presented.value, false)
  presented.value = false
  assert.equal(visible.value, true)
  assert.equal(store.hasUnsavedChanges(), true)
  active.value = true
  assert.equal(presented.value, true)
  presented.value = false
  assert.equal(visible.value, false)
  visible.value = true
  app.unmount()
  assert.equal(store.hasUnsavedChanges(), false)
}

console.log('workspace tab tests passed (identity, state retention, close guards, mode changes, account isolation, inactive dialogs)')
