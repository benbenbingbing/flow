import assert from 'node:assert/strict'
import test from 'node:test'
import { createUserInterfacePreferences, DEFAULT_USER_INTERFACE_PREFERENCES } from '../user-interface-preferences.js'

const tick = () => new Promise(resolve => setImmediate(resolve))
function deferred() {
  let resolve, reject
  const promise = new Promise((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}
const view = (overrides = null, version = 0, system = {}) => ({
  value: { ...DEFAULT_USER_INTERFACE_PREFERENCES, ...system, ...overrides },
  overrideValue: overrides,
  override: overrides === null ? null : { id: 'mine', version },
  source: overrides && Object.keys(overrides).length ? 'USER' : 'SYSTEM'
})

// 各测试使用真实保存器，模拟独立接口响应，不访问浏览器或业务数据库。
test('读取继承值不创建覆盖，首次修改只保存目标字段', async () => {
  let state, payload
  const runtime = createUserInterfacePreferences({
    read: async () => view(null, 0, { tabsEnabled: true }),
    write: async data => { payload = data; return view(JSON.parse(data.settingValue)) },
    onChange: next => { state = next }
  })
  await runtime.refresh()
  assert.equal(payload, undefined)
  assert.equal(state.value.tabsEnabled, true)
  await runtime.setValue('sidebarCollapsed', true)
  assert.deepEqual(JSON.parse(payload.settingValue), { sidebarCollapsed: true })
  assert.equal(payload.expectedId, null)
})

test('三个字段共用串行队列，快速反向切换只提交最后意图', async () => {
  const first = deferred(), writes = []
  let state
  const runtime = createUserInterfacePreferences({ read: async () => view(),
    write: async data => { writes.push(data); return writes.length === 1 ? first.promise : view(JSON.parse(data.settingValue), 1) },
    onChange: next => { state = next }
  })
  await runtime.refresh()
  const done = runtime.setValue('sidebarCollapsed', true)
  runtime.setValue('tabsEnabled', true)
  runtime.setValue('fieldTypesCollapsed', true)
  runtime.setValue('sidebarCollapsed', false)
  assert.equal(writes.length, 1)
  assert.deepEqual(state.value, { sidebarCollapsed: false, tabsEnabled: true, fieldTypesCollapsed: true })
  first.resolve(view({ sidebarCollapsed: true }))
  await done
  assert.equal(writes.length, 2)
  assert.deepEqual(JSON.parse(writes[1].settingValue), { sidebarCollapsed: false, tabsEnabled: true, fieldTypesCollapsed: true })
  assert.equal(writes[1].expectedVersion, 0)
  assert.equal(state.value.sidebarCollapsed, false)
  assert.equal(state.saving, false)
})

test('单项恢复删除对应字段，保留其他显式 false 及继承值', async () => {
  let state, payload, removed = 0
  const system = { fieldTypesCollapsed: true }
  const runtime = createUserInterfacePreferences({
    read: async () => view({ fieldTypesCollapsed: false, sidebarCollapsed: false }, 4, system),
    write: async data => { payload = data; return view(JSON.parse(data.settingValue), 5, system) },
    remove: async () => { removed++; return view() }, onChange: next => { state = next }
  })
  await runtime.refresh()
  await runtime.reset('fieldTypesCollapsed')
  assert.equal(removed, 0)
  assert.deepEqual(JSON.parse(payload.settingValue), { sidebarCollapsed: false })
  assert.equal(payload.expectedVersion, 4)
  assert.equal(state.value.fieldTypesCollapsed, true)
  assert.equal(Object.hasOwn(state.overrideValue, 'fieldTypesCollapsed'), false)
})

test('最后一个字段恢复时删除记录并使用个人版本', async () => {
  let removed, state
  const runtime = createUserInterfacePreferences({ read: async () => view({ fieldTypesCollapsed: true }, 8),
    remove: async data => { removed = data; return view() }, onChange: next => { state = next } })
  await runtime.refresh()
  await runtime.reset('fieldTypesCollapsed')
  assert.deepEqual(removed, { expectedId: 'mine', expectedVersion: 8 })
  assert.deepEqual(state.overrideValue, {})
  assert.equal(state.value.fieldTypesCollapsed, false)
})

test('首次读取失败后重新获取版本，不以默认值覆盖现有偏好', async () => {
  let reads = 0, payload
  const runtime = createUserInterfacePreferences({
    read: async () => { if (++reads === 1) throw Error('offline'); return view({ tabsEnabled: true }, 9) },
    write: async data => { payload = data; return view(JSON.parse(data.settingValue), 10) }, onChange: () => {} })
  await runtime.refresh()
  assert.equal(payload, undefined)
  await runtime.setValue('sidebarCollapsed', false)
  assert.equal(reads, 2)
  assert.equal(payload.expectedVersion, 9)
  assert.deepEqual(JSON.parse(payload.settingValue), { tabsEnabled: true, sidebarCollapsed: false })
})

test('持续读取失败不写入，后台恢复读取不撤销未保存选择', async () => {
  let offline = true, writes = 0, state
  const runtime = createUserInterfacePreferences({ read: async () => { if (offline) throw Error('offline'); return view() },
    write: async () => { writes++; return view() }, onChange: next => { state = next } })
  await runtime.setValue('sidebarCollapsed', true)
  await runtime.setValue('tabsEnabled', true)
  assert.equal(writes, 0)
  offline = false
  await runtime.refresh()
  assert.equal(state.value.sidebarCollapsed, true)
  assert.equal(state.value.tabsEnabled, true)
  assert.match(state.error, /未保存/)
})

test('冲突后保留当前选择，下次操作重新合并服务端其他字段', async () => {
  let reads = 0, writes = 0, state, payload
  const runtime = createUserInterfacePreferences({
    read: async () => ++reads === 1 ? view() : view({ fieldTypesCollapsed: true }, 3),
    write: async data => {
      if (++writes === 1) throw Object.assign(Error('conflict'), { status: 409 })
      payload = data; return view(JSON.parse(data.settingValue), 4)
    }, onChange: next => { state = next } })
  await runtime.refresh()
  await runtime.setValue('sidebarCollapsed', true)
  assert.match(state.error, /其他页面/)
  assert.equal(state.value.sidebarCollapsed, true)
  await runtime.setValue('tabsEnabled', true)
  assert.equal(payload.expectedVersion, 3)
  assert.deepEqual(JSON.parse(payload.settingValue), { fieldTypesCollapsed: true, sidebarCollapsed: true, tabsEnabled: true })
  assert.equal(state.error, '')
})

test('保存失败保留所有字段的最后选择，后续操作可一起重试', async () => {
  const first = deferred()
  let state, calls = 0, payload
  const runtime = createUserInterfacePreferences({ read: async () => view(),
    write: async data => { if (++calls === 1) return first.promise; payload = data; return view(JSON.parse(data.settingValue)) },
    onChange: next => { state = next } })
  await runtime.refresh()
  const done = runtime.setValue('sidebarCollapsed', true)
  runtime.setValue('sidebarCollapsed', false)
  runtime.setValue('tabsEnabled', true)
  first.reject(Error('network'))
  await done
  assert.equal(state.value.sidebarCollapsed, false)
  assert.equal(state.value.tabsEnabled, true)
  await runtime.setValue('fieldTypesCollapsed', true)
  assert.deepEqual(JSON.parse(payload.settingValue), { sidebarCollapsed: false, tabsEnabled: true, fieldTypesCollapsed: true })
})

test('切换账号后丢弃进行中的读取及待写队列', async () => {
  const first = deferred()
  let writes = 0, changes = 0
  const runtime = createUserInterfacePreferences({ read: () => first.promise,
    write: async () => { writes++; return view() }, onChange: () => { changes++ } })
  const reading = runtime.refresh()
  await tick()
  const done = runtime.setValue('sidebarCollapsed', true)
  runtime.dispose()
  const before = changes
  first.resolve(view())
  await Promise.all([reading, done])
  assert.equal(writes, 0)
  assert.equal(changes, before)
})

test('切换账号后已发保存可结束，但不继续发送另一个字段的操作', async () => {
  const first = deferred()
  let writes = 0
  const runtime = createUserInterfacePreferences({ read: async () => view(),
    write: async () => { writes++; return first.promise }, onChange: () => {} })
  await runtime.refresh()
  const done = runtime.setValue('sidebarCollapsed', true)
  runtime.setValue('tabsEnabled', true)
  runtime.dispose()
  first.resolve(view({ sidebarCollapsed: true }))
  await done
  assert.equal(writes, 1)
})

test('不接受缺失稀疏覆盖或非布尔协议，防止锁定其他继承字段', async () => {
  for (const invalid of [
    { value: true }, { ...view({ tabsEnabled: true }), overrideValue: undefined },
    { ...view(), value: { tabsEnabled: true } }, { ...view(), overrideValue: { other: true } }
  ]) {
    let writes = 0, state
    const runtime = createUserInterfacePreferences({ read: async () => invalid,
      write: async () => { writes++ }, onChange: next => { state = next } })
    await runtime.setValue('tabsEnabled', true)
    assert.equal(writes, 0)
    assert.equal(state.loaded, false)
  }
})
