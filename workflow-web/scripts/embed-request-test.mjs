import assert from 'node:assert/strict'
import {
  EmbedApiError,
  buildEmbedApiUrl,
  createEmbedRequest,
  isEmbedSessionFailure
} from '../src/embed/api/embedRequest.js'
import { createEmbedRuntimeApi } from '../src/embed/api/embedRuntimeApi.js'

const TEST_LAUNCH_CODE = 'A'.repeat(43)
const PARENT_NONCE = Buffer.alloc(32, 201).toString('base64url')
const CHILD_NONCE = Buffer.alloc(32, 151).toString('base64url')

function response(status, payload, headers = {}) {
  return {
    status,
    ok: status >= 200 && status < 300,
    headers: {
      get(name) {
        return headers[String(name).toLowerCase()] ?? null
      }
    },
    async text() {
      if (payload === undefined) return ''
      return typeof payload === 'string' ? payload : JSON.stringify(payload)
    }
  }
}

assert.equal(buildEmbedApiUrl('/runtime/bootstrap'), '/api/embed/v1/runtime/bootstrap')
assert.equal(
  buildEmbedApiUrl('runtime/list/query?fixed=1'),
  '/api/embed/v1/runtime/list/query?fixed=1'
)
for (const path of [
  '',
  'https://evil.example/x',
  '//evil.example/x',
  '../admin',
  'runtime/../admin',
  'runtime/%2e%2e/admin',
  'runtime/%2Fadmin',
  'runtime/%ZZ'
]) {
  assert.throws(
    () => buildEmbedApiUrl(path),
    error => error instanceof EmbedApiError
      && error.errorCode === 'EMBED_REQUEST_PATH_INVALID'
  )
}

const calls = []
const queuedResponses = [
  response(201, {
    code: 201,
    data: { accessToken: 'opaque' },
    traceId: 'trace-created'
  }),
  response(204),
  response(401, {
    errorCode: 'EMBED_SESSION_EXPIRED',
    message: 'expired'
  }, { 'x-trace-id': 'trace-expired' })
]
const client = createEmbedRequest({
  session: {
    getAccessToken() {
      return 'memory-only-token'
    }
  },
  async fetchImpl(url, options) {
    calls.push({ url, options })
    return queuedResponses.shift()
  },
  timeoutMs: 0
})

assert.deepEqual(
  await client.post('/runtime/list/query', { pageNum: 1 }, {
    headers: {
      Authorization: 'attacker-token',
      Cookie: 'flow=ordinary-session',
      Origin: 'https://evil.example',
      'X-Flow-Embed-Protocol': '1'
    }
  }),
  { accessToken: 'opaque' }
)
assert.equal(calls[0].url, '/api/embed/v1/runtime/list/query')
assert.equal(calls[0].options.credentials, 'omit')
assert.equal(calls[0].options.redirect, 'error')
assert.equal(calls[0].options.referrerPolicy, 'no-referrer')
assert.equal(calls[0].options.headers.Authorization, 'Bearer memory-only-token')
assert.equal(Object.hasOwn(calls[0].options.headers, 'Cookie'), false)
assert.equal(Object.hasOwn(calls[0].options.headers, 'Origin'), false)
assert.equal(calls[0].options.body, JSON.stringify({ pageNum: 1 }))

assert.equal(await client.delete('/session'), undefined)
assert.equal(calls[1].options.method, 'DELETE')
assert.equal(calls[1].options.body, undefined)

await assert.rejects(
  () => client.get('/session'),
  error => {
    assert.equal(error.status, 401)
    assert.equal(error.errorCode, 'EMBED_SESSION_EXPIRED')
    assert.equal(error.traceId, 'trace-expired')
    assert.equal(isEmbedSessionFailure(error), true)
    return true
  }
)
assert.equal(isEmbedSessionFailure({ status: 403, errorCode: 'EMBED_VIEW_NOT_GRANTED' }), false)
assert.equal(isEmbedSessionFailure({ status: 403, errorCode: 'EMBED_SESSION_REVOKED' }), true)

const runtimeCalls = []
const fakeClient = {
  get(path, options) {
    runtimeCalls.push({ method: 'GET', path, options })
    return Promise.resolve({})
  },
  post(path, body, options) {
    runtimeCalls.push({ method: 'POST', path, body, options })
    return Promise.resolve({})
  },
  delete(path, options) {
    runtimeCalls.push({ method: 'DELETE', path, options })
    return Promise.resolve()
  }
}
const runtimeApi = createEmbedRuntimeApi(fakeClient)
await runtimeApi.exchange('lch_0123456789abcdef', {
  launchCode: TEST_LAUNCH_CODE,
  channelId: 'tenant:channel_001',
  parentOrigin: 'https://portal.example.com',
  parentNonce: PARENT_NONCE,
  childNonce: CHILD_NONCE,
  sdkVersion: '1.0.0'
})
await runtimeApi.getBootstrap()
await runtimeApi.getSchema()
await runtimeApi.queryList({ pageNum: 1, pageSize: 20, filters: [] })
await runtimeApi.getSession()
await runtimeApi.heartbeat({
  visible: true,
  clientTime: '2026-08-27T08:00:00.000Z'
})
await runtimeApi.logout()

assert.deepEqual(runtimeCalls.map(call => `${call.method} ${call.path}`), [
  'POST /launches/lch_0123456789abcdef/exchange',
  'GET /runtime/bootstrap',
  'GET /runtime/schema',
  'POST /runtime/list/query',
  'GET /session',
  'POST /session/heartbeat',
  'DELETE /session'
])
assert.deepEqual(runtimeCalls[0].body, {
  launchCode: TEST_LAUNCH_CODE,
  channelId: 'tenant:channel_001',
  parentOrigin: 'https://portal.example.com',
  parentNonce: PARENT_NONCE,
  childNonce: CHILD_NONCE,
  sdkVersion: '1.0.0'
})
assert.equal(Buffer.from(runtimeCalls[0].body.parentNonce, 'base64url').length, 32)
assert.equal(Buffer.from(runtimeCalls[0].body.childNonce, 'base64url').length, 32)
assert.equal(runtimeCalls[0].options.auth, false)
assert.equal(runtimeCalls[0].options.headers['X-Flow-Embed-Protocol'], '1')
assert.equal(runtimeCalls[3].options.headers['X-Flow-Embed-Protocol'], '1')
assert.equal(runtimeCalls[6].options.keepalive, true)

assert.throws(
  () => runtimeApi.exchange('lch_too_short', {
    launchCode: TEST_LAUNCH_CODE,
    channelId: 'tenant:channel_001',
    parentOrigin: 'https://portal.example.com',
    parentNonce: PARENT_NONCE,
    childNonce: CHILD_NONCE,
    sdkVersion: '1.0.0'
  }),
  /launchId 无效/
)
assert.throws(
  () => runtimeApi.exchange('lch_0123456789abcdef', {
    launchCode: TEST_LAUNCH_CODE,
    channelId: 'short-channel',
    parentOrigin: 'https://portal.example.com',
    parentNonce: PARENT_NONCE,
    childNonce: CHILD_NONCE,
    sdkVersion: '1.0.0'
  }),
  /channelId 无效/
)
assert.throws(
  () => runtimeApi.exchange('lch_0123456789abcdef', {
    launchCode: 'A'.repeat(42),
    channelId: 'tenant:channel_001',
    parentOrigin: 'https://portal.example.com',
    parentNonce: PARENT_NONCE,
    childNonce: CHILD_NONCE,
    sdkVersion: '1.0.0'
  }),
  /launch code 无效/
)
assert.throws(
  () => runtimeApi.exchange('lch_0123456789abcdef', {
    launchCode: TEST_LAUNCH_CODE,
    channelId: 'tenant:channel_001',
    parentOrigin: 'https://portal.example.com',
    parentNonce: 'A'.repeat(42),
    childNonce: CHILD_NONCE,
    sdkVersion: '1.0.0'
  }),
  /parentNonce 无效/
)

console.log('embed request tests passed')
