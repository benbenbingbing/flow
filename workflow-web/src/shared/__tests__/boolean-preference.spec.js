import assert from 'node:assert/strict'
import { createBooleanPreference } from '../boolean-preference.js'

function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}
const tick = () => new Promise(resolve => setImmediate(resolve))
const view = (value, version = null) => ({ value, source: version === null ? 'SYSTEM' : 'USER',
  override: version === null ? null : { id: 'mine', version } })

// 快速切换按同一记录串行保存，第二次写入使用第一次返回的新版本。
{
  let state
  const writes = []
  const first = deferred()
  const preference = createBooleanPreference({
    read: async () => view(false),
    write: async data => {
      writes.push(data)
      return writes.length === 1 ? first.promise : view(JSON.parse(data.settingValue), 1)
    },
    onChange: next => { state = next }
  })
  await preference.refresh()
  const saving = preference.setValue(true)
  preference.setValue(false)
  preference.setValue(true)
  preference.setValue(false)
  assert.equal(state.value, false)
  assert.equal(writes.length, 1)
  first.resolve(view(true, 0))
  await saving
  assert.deepEqual(writes, [
    { settingValue: 'true', expectedId: null, expectedVersion: null },
    { settingValue: 'false', expectedId: 'mine', expectedVersion: 0 }
  ])
  assert.equal(state.value, false)
  assert.equal(state.saving, false)
}

// 初次读取失败不能直接以默认值写入；明确点击后重新读取真实版本。
{
  let reads = 0
  let state
  const writes = []
  const preference = createBooleanPreference({
    read: async () => { if (++reads === 1) throw new Error('offline'); return view(true, 9) },
    write: async data => { writes.push(data); return view(false, 10) },
    onChange: next => { state = next }
  })
  await preference.refresh()
  assert.equal(writes.length, 0)
  assert.equal(state.loaded, false)
  await preference.setValue(false)
  assert.equal(reads, 2)
  assert.equal(writes[0].expectedVersion, 9)
  assert.equal(state.value, false)
}

// 持续读取失败时保留未保存提示，禁止猜测版本写入。
{
  let writes = 0
  let errors = 0
  const preference = createBooleanPreference({ read: async () => { throw new Error('offline') },
    write: async () => { writes++ }, onChange: () => {}, onError: () => { errors++ } })
  await preference.setValue(true)
  assert.equal(writes, 0)
  assert.equal(errors, 1)
}

// 恢复默认使用删除接口与个人版本，继承到的 false 仍是有效值。
{
  let removed
  let state
  const preference = createBooleanPreference({ read: async () => view(true, 4),
    remove: async data => { removed = data; return view(false) }, onChange: next => { state = next } })
  await preference.refresh()
  await preference.reset()
  assert.deepEqual(removed, { expectedId: 'mine', expectedVersion: 4 })
  assert.equal(state.value, false)
  assert.equal(state.overridden, false)
}

// 并发冲突不自动覆盖其他页面，新操作必须重新读取；错误不能标记为已保存。
{
  let state
  let reads = 0
  let errors = 0
  const preference = createBooleanPreference({
    read: async () => { reads++; return view(false, 2) },
    write: async () => { throw Object.assign(new Error('conflict'), { status: 409 }) },
    onChange: next => { state = next }, onError: () => { errors++ }
  })
  await preference.refresh()
  await preference.setValue(true)
  assert.equal(state.loaded, false)
  assert.equal(state.value, false)
  assert.match(state.error, /其他页面/)
  await preference.setValue(true)
  assert.equal(reads, 2)
  assert.equal(errors, 2)
}

// 账号切换后，旧请求不能更新新账号状态或继续发送排队写入。
{
  const reading = deferred()
  let writes = 0
  let changes = 0
  const preference = createBooleanPreference({ read: () => reading.promise,
    write: async () => { writes++; return view(true, 0) }, onChange: () => { changes++ } })
  const load = preference.refresh()
  await tick()
  const save = preference.setValue(true)
  preference.dispose()
  const before = changes
  reading.resolve(view(false))
  await Promise.all([load, save])
  assert.equal(writes, 0)
  assert.equal(changes, before)
}

// 保存中离开账号，已发请求可完成，但不得把旧账号的后续操作发送给新账号。
{
  const saving = deferred()
  let writes = 0
  const preference = createBooleanPreference({ read: async () => view(false),
    write: async () => { writes++; return saving.promise }, onChange: () => {} })
  await preference.refresh()
  const done = preference.setValue(true)
  preference.setValue(false)
  preference.dispose()
  saving.resolve(view(true, 0))
  await done
  assert.equal(writes, 1)
}

console.log('boolean preference tests passed (7 scenarios)')
