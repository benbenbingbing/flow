import { test } from 'node:test'
import assert from 'node:assert/strict'
import { DEFAULT_MOBILE_THEME as defaults } from '@flow/workflow-core/mobile-theme'
import { applyTheme, resetTheme, loadSystemTheme } from '../src/theme/index.js'
const fakeDocument = () => {
  const element = () => ({ classList: { add() {} }, style: new Map() })
  const doc = { documentElement: element(), body: element() }
  for (const target of Object.values(doc)) target.style.setProperty = target.style.set.bind(target.style)
  return doc
}
test('根节点和 body 同步主题，teleport 弹窗继承同一份配色', () => {
  const doc = fakeDocument()
  applyTheme({ ...defaults, primaryColor: '#722ED1' }, doc)
  for (const target of Object.values(doc)) assert.equal(target.style.get('--van-primary-color'), '#722ED1')
  resetTheme(doc)
  assert.equal(doc.body.style.get('--van-primary-color'), defaults.primaryColor)
})
test('每次加载重新读取最新系统值，禁用浏览器缓存且不发送登录信息', async () => {
  const doc = fakeDocument(), calls = []
  let theme = defaults
  const fetchImpl = async (url, options) => { calls.push({ url, options }); return { ok: true, json: async () => ({ code: 200, data: theme }) } }
  await loadSystemTheme({ documentRef: doc, fetchImpl })
  theme = { ...defaults, primaryColor: '#1677FF' }
  await loadSystemTheme({ documentRef: doc, fetchImpl })
  assert.equal(doc.body.style.get('--van-primary-color'), '#1677FF')
  assert.equal(calls.length, 2)
  for (const call of calls) { assert.equal(call.url, '/api/system/mobile-theme'); assert.equal(call.options.cache, 'no-store'); assert.equal(call.options.credentials, 'omit') }
})
test('网络、HTTP、业务码和坏配置均回退默认，超时仍可进入应用', async () => {
  for (const fetchImpl of [async () => { throw Error('offline') }, async () => ({ ok: false }), async () => ({ ok: true, json: async () => ({ code: 403 }) }), async () => ({ ok: true, json: async () => ({ code: 200, data: {} }) }), (url, { signal }) => new Promise((resolve, reject) => signal.addEventListener('abort', () => reject(Error('timeout'))))]) {
    const doc = fakeDocument()
    const result = await loadSystemTheme({ documentRef: doc, fetchImpl, timeoutMs: 10 })
    assert.deepEqual(result, defaults)
    assert.equal(doc.body.style.get('--flow-mobile-accent'), defaults.primaryColor)
  }
})
