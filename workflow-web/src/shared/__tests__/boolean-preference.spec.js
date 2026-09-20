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

// 并发冲突不自动覆盖其他页面，也不能回退当前界面；新操作必须重新读取。
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
  assert.equal(state.value, true)
  assert.match(state.error, /其他页面/)
  await preference.setValue(true)
  assert.equal(reads, 2)
  assert.equal(errors, 2)
}

// 读取失败也不阻止连续切换；网络恢复后的后台读取不能撤销尚未保存的选择。
{
  let state
  let offline = true
  const writes = []
  const preference = createBooleanPreference({
    read: async () => { if (offline) throw new Error('offline'); return view(false, 7) },
    write: async data => { writes.push(data); return view(JSON.parse(data.settingValue), 8) },
    onChange: next => { state = next }
  })
  await preference.setValue(true)
  assert.equal(state.value, true)
  await preference.setValue(false)
  assert.equal(state.value, false)
  await preference.setValue(true)
  assert.equal(state.value, true)
  assert.equal(writes.length, 0)
  offline = false
  await preference.refresh()
  assert.equal(state.value, true, '后台读取不得撤销当前会话的选择')
  assert.match(state.error, /未保存/)
  assert.equal(writes.length, 0, '仅恢复网络和读取不自动创建个人覆盖')
  await preference.setValue(false)
  assert.deepEqual(writes, [{ settingValue: 'false', expectedId: 'mine', expectedVersion: 7 }])
  assert.equal(state.error, '')
}

// 保存响应失败时保留最后一次点击，后续操作仍可继续，不回滚到服务端旧值。
{
  let state
  let rejectWrite
  const preference = createBooleanPreference({
    read: async () => view(true, 1),
    write: () => new Promise((resolve, reject) => { rejectWrite = reject }),
    onChange: next => { state = next }
  })
  await preference.refresh()
  const saving = preference.setValue(false)
  preference.setValue(true)
  preference.setValue(false)
  rejectWrite(new Error('network error'))
  await saving
  assert.equal(state.value, false)
  assert.equal(state.saving, false)
  assert.match(state.error, /未保存/)
  await preference.refresh()
  assert.equal(state.value, false)
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

console.log('boolean preference tests passed (9 scenarios)')
