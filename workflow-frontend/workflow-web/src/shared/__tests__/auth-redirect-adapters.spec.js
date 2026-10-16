import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { babelParse } from '@vue/compiler-sfc'
import { createPinia, setActivePinia } from 'pinia'
import { buildAuthRedirectUrl } from '../login-redirect.js'

// 执行真实请求适配器注册的回调，确保安全 helper 已接到 401/428 的实际导航出口。
const source = await readFile(new URL('../request/index.js', import.meta.url), 'utf8')
const ast = babelParse(source, { sourceType: 'module' })
const declaration = ast.program.body.find(node => node.type === 'VariableDeclaration'
  && node.declarations.some(item => item.id.name === 'runtime'))
const code = source.slice(declaration.start, declaration.end).replace('import.meta.env?.VITE_API_BASE_URL', 'undefined')
const target = '/process/progress/instance-1?taskId=task-1#history'
/** 浏览器 Location.href 接受站内相对地址，Node URL.href 只接受完整 URL，夹具需保留这一差别。 */
function browserLocation(path) {
  let url = new URL(path, 'https://workflow.test')
  return {
    get pathname() { return url.pathname },
    get search() { return url.search },
    get hash() { return url.hash },
    get searchParams() { return url.searchParams },
    get href() { return url.href },
    set href(value) { url = new URL(value, url) }
  }
}
const browser = { location: browserLocation(target) }
const options = new Function('createRequestRuntime', 'useUserStore', 'ElMessage', 'globalThis', 'buildAuthRedirectUrl',
  `${code}\nreturn runtime`)(value => value, () => ({}), { error() {} }, browser, buildAuthRedirectUrl)
options.onAuthExpired()
assert.equal(browser.location.pathname, '/login')
assert.equal(browser.location.searchParams.get('redirect'), target)
const loginUrl = browser.location.href
options.onAuthExpired()
assert.equal(browser.location.href, loginUrl)
options.onPasswordResetRequired()
assert.equal(browser.location.pathname, '/change-password')
assert.equal(browser.location.searchParams.get('redirect'), target)
options.onAuthExpired()
assert.equal(browser.location.pathname, '/login')
assert.equal(browser.location.searchParams.get('redirect'), target)

// 真实 Pinia store 接收跨标签页退出事件后应清会话，并保留当前单据供重新登录使用。
const previous = { window: globalThis.window, location: globalThis.location, localStorage: globalThis.localStorage, sessionStorage: globalThis.sessionStorage }
const values = new Map()
const storage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: key => values.delete(key) }
let onStorage
try {
  globalThis.location = browserLocation(target)
  globalThis.window = { location: globalThis.location, addEventListener: (name, handler) => { if (name === 'storage') onStorage = handler } }
  globalThis.localStorage = storage
  globalThis.sessionStorage = storage
  setActivePinia(createPinia())
  const { useUserStore } = await import('../../stores/user.js')
  const store = useUserStore()
  store.applySession({ token: 'test-session', tokenExpiresAt: '2099-01-01T00:00:00Z', username: 'test' })
  onStorage({ key: 'auth.session.sync', newValue: '{}' })
  assert.equal(store.token, '')
  assert.equal(globalThis.location.pathname, '/login')
  assert.equal(globalThis.location.searchParams.get('redirect'), target)
} finally {
  for (const [key, value] of Object.entries(previous)) {
    if (value === undefined) delete globalThis[key]
    else globalThis[key] = value
  }
}
console.log('auth request and cross-tab redirect adapter tests passed')
