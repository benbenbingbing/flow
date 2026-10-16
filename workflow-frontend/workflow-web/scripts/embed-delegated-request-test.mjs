import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import request, {
  configureEmbedDelegatedRequest,
  isEmbedDelegatedRequestEnabled,
  refreshAuthSession,
  restoreAuthSession,
  resetEmbedDelegatedRequest
} from '../src/shared/request/index.js'
import {
  enableEphemeralUserStoreRuntime,
  useUserStore
} from '../src/stores/user.js'

function header(config, name) {
  return typeof config.headers?.get === 'function'
    ? config.headers.get(name)
    : config.headers?.[name] || config.headers?.[name.toLowerCase()]
}

function successfulAdapter(calls) {
  return async config => {
    calls.push(config)
    return {
      data: { code: 0, data: { ok: true } },
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
      request: {}
    }
  }
}

const storageOperations = []
globalThis.localStorage = {
  getItem(key) { storageOperations.push(['local:get', key]); return null },
  setItem(key, value) { storageOperations.push(['local:set', key, value]) },
  removeItem(key) { storageOperations.push(['local:remove', key]) }
}
globalThis.sessionStorage = {
  getItem(key) { storageOperations.push(['session:get', key]); return null },
  setItem(key, value) { storageOperations.push(['session:set', key, value]) },
  removeItem(key) { storageOperations.push(['session:remove', key]) }
}
enableEphemeralUserStoreRuntime()
setActivePinia(createPinia())
const userStore = useUserStore()
userStore.applyEphemeralRuntimeIdentity({
  username: 'mapped-user',
  nickname: '映射用户',
  roles: ['operator'],
  permissions: ['entity:record:view'],
  isSuperAdmin: false
})
assert.equal(userStore.username, 'mapped-user')
assert.deepEqual(userStore.roles, ['operator'])
assert.deepEqual(userStore.permissions, ['entity:record:view'])
assert.equal(userStore.token, '')
assert.equal(userStore.isLoggedIn, true)
assert.equal(userStore.isSuperAdmin, false)
assert.deepEqual(storageOperations, [])
userStore.clearEphemeralRuntimeIdentity()
assert.equal(userStore.userInfo, null)
assert.equal(userStore.isLoggedIn, false)
assert.deepEqual(storageOperations, [])
userStore.setToken('ordinary-admin-token', '2099-01-01T00:00:00.000Z')

const delegatedCalls = []
configureEmbedDelegatedRequest({
  getAccessToken: () => 'opaque-memory-only-token'
})
assert.equal(isEmbedDelegatedRequestEnabled(), true)
await request.get('/entity/code/order', {
  adapter: successfulAdapter(delegatedCalls)
})
assert.equal(delegatedCalls.length, 1)
assert.equal(header(delegatedCalls[0], 'Authorization'), 'Bearer opaque-memory-only-token')
assert.equal(header(delegatedCalls[0], 'X-Flow-Embed-Protocol'), '1')
assert.equal(delegatedCalls[0].withCredentials, false)
assert.equal(delegatedCalls[0].skipAuthRefresh, true)

// 原生 LIST/FORM 的失败继续交由 controller 回传；两种 Axios 响应出口都必须保留诊断关联。
for (const rejectHttpResponse of [false, true]) {
  await assert.rejects(
    () => request.get('/entity/code/order', {
      silentError: true,
      adapter: async config => {
        const response = {
          data: {
            code: 503,
            errorCode: 'EMBED_RUNTIME_UNAVAILABLE',
            message: '列表暂时不可用',
            traceId: 'trace-native-list-failure'
          },
          status: 503,
          config
        }
        if (rejectHttpResponse) {
          throw Object.assign(new Error('HTTP 503'), { response, config })
        }
        return response
      }
    }),
    error => error.errorCode === 'EMBED_RUNTIME_UNAVAILABLE'
      && error.traceId === 'trace-native-list-failure'
      && error.status === 503
  )
}

// Embed 令牌和协议标记只能发往 Flow API origin；任意绝对外域 URL 都必须剥离。
const externalCalls = []
await request.get('https://untrusted.example/resource', {
  adapter: successfulAdapter(externalCalls),
  headers: {
    Authorization: 'Bearer attacker-value',
    'X-Flow-Embed-Protocol': 'stale'
  }
})
assert.equal(header(externalCalls[0], 'Authorization'), undefined)
assert.equal(header(externalCalls[0], 'X-Flow-Embed-Protocol'), undefined)
assert.equal(externalCalls[0].withCredentials, false)

await assert.rejects(
  () => request.post('/auth/refresh', {}, {
    adapter: successfulAdapter([]),
    silentError: true
  }),
  error => error?.errorCode === 'EMBED_OPERATION_NOT_ALLOWED'
)
await assert.rejects(
  () => refreshAuthSession(),
  error => error?.errorCode === 'EMBED_OPERATION_NOT_ALLOWED'
)
assert.equal(await restoreAuthSession(), false)

configureEmbedDelegatedRequest({ getAccessToken: () => '' })
await assert.rejects(
  () => request.get('/entity/code/order', {
    adapter: successfulAdapter([]),
    silentError: true
  }),
  error => error?.errorCode === 'EMBED_SESSION_MISSING'
)

// reset 后恢复原管理端 transport：普通 token 与 Cookie 配置仍保持原行为。
resetEmbedDelegatedRequest()
assert.equal(isEmbedDelegatedRequestEnabled(), false)
const adminCalls = []
await request.get('/entity/code/order', {
  adapter: successfulAdapter(adminCalls)
})
assert.equal(header(adminCalls[0], 'Authorization'), 'Bearer ordinary-admin-token')
assert.equal(header(adminCalls[0], 'X-Flow-Embed-Protocol'), undefined)
assert.equal(adminCalls[0].withCredentials, true)

console.log('embed delegated request tests passed')
