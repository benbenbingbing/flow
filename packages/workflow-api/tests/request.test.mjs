import test from 'node:test'
import assert from 'node:assert/strict'
import { createRequestRuntime, createProcessTaskApi, createFileApi, BUSINESS_TRACE_HEADER } from '../dist/index.js'
const session = (token = '') => ({ token, tokenExpiresAt: new Date(Date.now() + 3600000).toISOString(), applySession(value) { Object.assign(this, value) }, clearAuth() { this.token = '' } })
const response = (config, data, code = 200) => ({ config, status: 200, headers: {}, data: { code, data } })

test('每个宿主持有自己的会话，第三方请求不携带令牌或 Cookie', async () => {
  const seen = [], make = value => createRequestRuntime({ getSession: () => value, getOrigin: () => 'https://flow.example', adapter: async config => { seen.push(config); return response(config, {}) } })
  const left = make(session('left')), right = make(session('right'))
  await Promise.all([left.request.get('/process-task/todo'), right.request.get('/process-task/todo')])
  assert.deepEqual(seen.map(config => config.headers.get('Authorization')).sort(), ['Bearer left', 'Bearer right'])
  await left.request.get('https://third.example/api/data')
  assert.equal(seen.at(-1).headers.get('Authorization'), undefined)
  assert.equal(seen.at(-1).withCredentials, false)
})

test('并发过期请求合并刷新，业务写请求保留独立追踪头', async () => {
  const state = session('old'); state.tokenExpiresAt = new Date(0).toISOString()
  let refreshes = 0; const seen = []
  const runtime = createRequestRuntime({ getSession: () => state, getOrigin: () => 'https://flow.example', adapter: async config => {
    if (config.url === '/auth/refresh') { refreshes++; await new Promise(resolve => setTimeout(resolve, 10)); return response(config, { token: 'new', tokenExpiresAt: new Date(Date.now() + 3600000).toISOString() }) }
    seen.push(config); return response(config, { ok: true })
  } })
  await Promise.all([runtime.request.post('/one', {}), runtime.request.post('/two', {})])
  assert.equal(refreshes, 1); assert.ok(seen.every(config => config.headers.get('Authorization') === 'Bearer new'))
  assert.ok(seen.every(config => config.headers.get(BUSINESS_TRACE_HEADER)))
  assert.notEqual(seen[0].headers.get(BUSINESS_TRACE_HEADER), seen[1].headers.get(BUSINESS_TRACE_HEADER))
})

test('Embed 不调用普通登录刷新，不携带 Cookie', async () => {
  const seen = [], state = session('admin')
  const runtime = createRequestRuntime({ getSession: () => state, getOrigin: () => 'https://embed.example', adapter: async config => { seen.push(config); return response(config, {}) } })
  runtime.configureEmbedDelegatedRequest({ getAccessToken: () => 'delegated' })
  await runtime.request.get('/entity-data/demo')
  assert.equal(seen[0].headers.get('Authorization'), 'Bearer delegated'); assert.equal(seen[0].withCredentials, false)
  await assert.rejects(runtime.request.post('/auth/login', {}), /不能调用普通登录/)
  assert.equal(await runtime.restoreAuthSession(), false); assert.equal(state.token, 'admin')
})

test('任务、发布坐标和上传幂等协议跨宿主一致', async () => {
  const seen = [], request = { post: async (...args) => seen.push(args) }
  const tasks = createProcessTaskApi(request)
  const body = { taskId: 'task', action: 'approve', formData: { amount: 0 }, formReleaseId: 'release-old', nextApprovalScopeKey: 'scope' }
  await tasks.completeTask(body); assert.equal(seen[0][0], '/process-task/complete'); assert.equal(seen[0][1], body)
  const file = new File(['test'], 'test.txt'), api = createFileApi(request).fileApi
  await api.uploadForEntity(file, { entityCode: 'demo', action: 'approve', fieldCode: 'files' })
  await api.uploadForEntity(file, { entityCode: 'demo', action: 'approve', fieldCode: 'files' })
  assert.equal(seen[1][0], '/file/entity/demo/upload'); assert.equal(seen[1][1].get('action'), 'approve')
  assert.equal(seen[1][2].headers['Idempotency-Key'], seen[2][2].headers['Idempotency-Key'])
})

test('同一次接口失败在 transport 和页面只提示一次，独立失败保留提示', async () => {
  const { notifyRequestError } = await import('../dist/request.js')
  for (const httpFailure of [false, true]) {
    const notifications = []
    const notify = message => notifications.push(message)
    const runtime = createRequestRuntime({ getSession: () => session(), notifyError: notify, adapter: async config => {
      const data = { code: 409, message: '接口执行失败', errorCode: 'UI_TEST_FAILURE' }
      if (httpFailure) throw Object.assign(new Error('HTTP 409'), { config, response: { status: 409, data } })
      return { config, status: 200, headers: {}, data }
    } })
    const showFailure = async config => {
      try { await runtime.request.post('/ui-runtime/events/FORM_OPEN/execute', {}, config) }
      catch (error) { notifyRequestError(error, notify, '打开失败') }
    }
    await showFailure()
    assert.deepEqual(notifications, ['接口执行失败'])
    await showFailure()
    assert.equal(notifications.length, 2)
    await showFailure({ silentError: true })
    assert.equal(notifications.length, 3)
    notifyRequestError(new Error('本地运行失败'), notify)
    assert.equal(notifications.at(-1), '本地运行失败')
  }
})
