import assert from 'node:assert/strict'
import {
  EMBED_RUNTIME_STATES,
  createEmbedRuntimeController
} from '../src/embed/runtime/embedRuntimeController.js'
import { createRuntimeFormDiscardGuard } from '../src/shared/runtime-form-discard.js'

const TEST_LAUNCH_CODE = 'A'.repeat(43)
const PARENT_NONCE = Buffer.alloc(32, 201).toString('base64url')
const CHILD_NONCE = Buffer.alloc(32, 151).toString('base64url')

// 关闭/刷新取消必须保留输入；并发触发只显示一个确认框，安全清理不被阻止。
{
  const data = { title: '初始值' }
  let enabled = true
  let confirmations = 0
  let settle
  const guard = createRuntimeFormDiscardGuard({
    readValue: () => data,
    enabled: () => enabled,
    confirm() {
      confirmations += 1
      return new Promise((resolve, reject) => { settle = { resolve, reject } })
    }
  })
  guard.markSaved()
  assert.equal(await guard.confirmDiscard(), true)
  assert.equal(confirmations, 0)
  data.title = '正在填写'
  const first = guard.confirmDiscard()
  assert.equal(first, guard.confirmDiscard())
  await Promise.resolve()
  assert.equal(confirmations, 1)
  settle.reject(new Error('cancel'))
  assert.equal(await first, false)
  assert.equal(data.title, '正在填写')
  assert.equal(guard.isDirty(), true)
  const event = { preventDefault() { this.prevented = true } }
  guard.beforeUnload(event)
  assert.equal(event.prevented, true)
  assert.equal(event.returnValue, '')
  const next = guard.confirmDiscard()
  await Promise.resolve()
  settle.resolve()
  assert.equal(await next, true)
  assert.equal(guard.isDirty(), true, '批准丢弃不等于保存成功')
  enabled = false
  assert.equal(await guard.confirmDiscard(), true)
  assert.equal(guard.isDirty(), false)
  enabled = true
  guard.markSaved()
  assert.equal(guard.isDirty(), false)
}

const entryConfig = Object.freeze({
  launchId: 'lch_0123456789abcdef',
  expectedParentOrigin: 'https://portal.example.com',
  channelId: 'channel-12345678',
  protocolVersion: 'flow-embed/1'
})

const bootstrap = {
  session: {
    id: 'ems_001',
    expiresAt: '2099-08-27T09:00:00.000Z',
    idleExpiresAt: '2099-08-27T08:35:00.000Z'
  },
  actor: { displayName: '张三' },
  view: {
    key: 'supplier-work-orders',
    name: '供应商工单',
    surfaceType: 'LIST',
    entryMode: 'LIST'
  },
  capabilities: ['LIST_QUERY', 'SELECTION_RETURN'],
  target: {
    entityCode: 'work_order',
    listKey: 'supplier_work_orders',
    listReleaseId: 'list-release-001',
    listReleaseVersion: 7,
    listReleaseResolutionToken: 'list_resolution_token_0123456789',
    context: { source: 'partner' }
  },
  ui: {
    locale: 'zh-CN',
    theme: 'light',
    showSearch: true,
    showPagination: true,
    showToolbar: true,
    pageSize: 20,
    heightMode: 'AUTO'
  },
  limits: {
    maxPageSize: 100,
    maxPayloadBytes: 1048576,
    maxSelectionSize: 100
  }
}

const schema = {
  view: {
    key: 'supplier-work-orders',
    surfaceType: 'LIST',
    revision: 7
  },
  entity: { code: 'work_order', name: '工单' },
  list: {
    selection: { mode: 'SINGLE', valueField: 'id', returnableFields: ['code'] },
    pagination: { allowTotal: false, maxPageSize: 100 },
    columns: [
      { code: 'code', label: '工单号', type: 'TEXT', width: 180, sortable: false },
      { code: 'status', label: '状态', type: 'TEXT', width: 120, sortable: false }
    ],
    filters: [
      { code: 'code', label: '工单号', type: 'TEXT', operator: 'CONTAINS' }
    ]
  },
  form: null,
  actions: []
}

function createBridgeHarness() {
  let options
  const sent = []
  const waiters = []
  let state = 'idle'
  function publish(message) {
    sent.push(message)
    for (const waiter of [...waiters]) {
      if (!waiter.predicate(message)) continue
      waiters.splice(waiters.indexOf(waiter), 1)
      waiter.resolve(message)
    }
  }
  return {
    sent,
    factory(nextOptions) {
      options = nextOptions
      return {
        start() {
          state = 'waiting'
          return CHILD_NONCE
        },
        getState() {
          return state
        },
        send(type, payload, options) {
          publish({ type, payload, options })
        },
        destroy() {
          state = 'destroyed'
        }
      }
    },
    async initialize() {
      state = 'connected'
      return options.onInit({
        launchCode: TEST_LAUNCH_CODE,
        parentNonce: PARENT_NONCE,
        childNonce: CHILD_NONCE,
        channelId: 'channel-12345678'
      })
    },
    command(type, payload = {}, requestId = 'request_00000000001') {
      return options.onMessage({
        type,
        payload,
        messageId: 'message_000000000001',
        requestId
      })
    },
    waitForSent(predicate) {
      const existing = sent.find(predicate)
      if (existing) return Promise.resolve(existing)
      return new Promise(resolve => waiters.push({ predicate, resolve }))
    }
  }
}

function deferred() {
  let resolve
  let reject
  const promise = new Promise((nextResolve, nextReject) => {
    resolve = nextResolve
    reject = nextReject
  })
  return { promise, resolve, reject }
}

function createTimerHarness() {
  let nextId = 0
  const timers = new Map()
  return {
    setTimeoutImpl(callback, delay) {
      const id = ++nextId
      timers.set(id, { callback, delay })
      return id
    },
    clearTimeoutImpl(id) {
      timers.delete(id)
    },
    first() {
      return timers.entries().next().value
    },
    take(id) {
      const timer = timers.get(id)
      timers.delete(id)
      return timer
    }
  }
}

const calls = []
let runtimeQueryError = null
let nativeListRefreshes = 0
const api = {
  async exchange(launchId, request) {
    calls.push({ operation: 'exchange', launchId, request })
    return {
      accessToken: 'embed_token_abcdefghijklmnopqrstuvwxyz',
      tokenType: 'Bearer',
      expiresAt: '2099-08-27T09:00:00.000Z',
      idleExpiresAt: '2099-08-27T08:35:00.000Z',
      heartbeatAfterSeconds: 60,
      protocolVersion: 'flow-embed/1'
    }
  },
  async getBootstrap() {
    calls.push({ operation: 'bootstrap' })
    return bootstrap
  },
  async getSchema() {
    calls.push({ operation: 'schema' })
    return schema
  },
  async queryList(query) {
    calls.push({ operation: 'query', query })
    if (runtimeQueryError) throw runtimeQueryError
    return {
      items: [{
        id: `record-${query.pageNum}`,
        recordVersion: null,
        values: { code: `WO-${query.pageNum}`, status: 'PROCESSING', secret: 'drop' },
        meta: { updatedAt: '2026-08-27T08:20:00.000Z' },
        actions: {}
      }],
      hasMore: query.pageNum === 1,
      pageNum: query.pageNum,
      pageSize: query.pageSize
    }
  },
  async heartbeat(payload) {
    calls.push({ operation: 'heartbeat', payload })
    return {
      status: 'ACTIVE',
      idleExpiresAt: '2099-08-27T08:40:00.000Z',
      absoluteExpiresAt: '2099-08-27T09:00:00.000Z',
      nextHeartbeatAfterSeconds: 90
    }
  },
  async logout() {
    calls.push({ operation: 'logout' })
  }
}

const bridge = createBridgeHarness()
const timers = createTimerHarness()
const states = []
const controller = createEmbedRuntimeController({
  entryConfig,
  api,
  bridgeFactory: bridge.factory,
  documentRef: { visibilityState: 'visible' },
  setTimeoutImpl: timers.setTimeoutImpl,
  clearTimeoutImpl: timers.clearTimeoutImpl,
  now: () => new Date('2026-08-27T08:31:00.000Z'),
  async onRefreshNativeList() {
    nativeListRefreshes += 1
    if (runtimeQueryError) throw runtimeQueryError
  }
})
controller.subscribe(snapshot => states.push(`${snapshot.state}:${snapshot.phase}`))

assert.equal(controller.start(), CHILD_NONCE)
assert.equal(controller.getSnapshot().state, EMBED_RUNTIME_STATES.WAITING_HANDSHAKE)
await bridge.initialize()

assert.deepEqual(calls.slice(0, 2).map(call => call.operation), [
  'exchange',
  'bootstrap'
])
assert.deepEqual(calls[0].request, {
  launchCode: TEST_LAUNCH_CODE,
  channelId: 'channel-12345678',
  parentOrigin: 'https://portal.example.com',
  parentNonce: PARENT_NONCE,
  childNonce: CHILD_NONCE,
  sdkVersion: '1.0.0'
})
assert.equal(Buffer.from(calls[0].request.parentNonce, 'base64url').length, 32)
assert.equal(Buffer.from(calls[0].request.childNonce, 'base64url').length, 32)
assert.equal(controller.getSnapshot().state, EMBED_RUNTIME_STATES.READY)
assert.equal(controller.getSnapshot().nativeListTarget.listKey, 'supplier_work_orders')
assert.equal(controller.getSnapshot().nativeListTarget.listReleaseVersion, 7)
assert.ok(states.includes('EXCHANGING:exchange'))
assert.ok(states.includes('BOOTSTRAPPING:bootstrap'))
assert.ok(states.includes('BOOTSTRAPPING:native-list'))
assert.ok(states.includes('READY:ready'))
assert.equal(bridge.sent[0].type, 'initialized')

bridge.command('set-theme', { theme: 'dark' })
assert.equal(controller.getSnapshot().theme, 'dark')
assert.equal(bridge.sent.at(-1).type, 'ack')
assert.equal(bridge.sent.at(-1).payload.command, 'set-theme')

bridge.command('set-locale', { locale: 'en-US' }, 'locale_unsupported_01')
assert.equal(controller.getSnapshot().locale, 'zh-CN')
assert.equal(bridge.sent.at(-1).type, 'error')
assert.equal(bridge.sent.at(-1).payload.errorCode, 'EMBED_OPERATION_NOT_ALLOWED')
assert.equal(bridge.sent.at(-1).options.requestId, 'locale_unsupported_01')
bridge.command('set-locale', { locale: 'zh-cn' }, 'locale_supported_01')
assert.equal(controller.getSnapshot().locale, 'zh-CN')
assert.equal(bridge.sent.at(-1).type, 'ack')

await controller.refreshList({
  queryValues: { code: 'WO-2', fixedFilters: { tenant: 'other' } },
  pageNum: 2,
  pageSize: 20
})
assert.equal(nativeListRefreshes, 1)

assert.equal(controller.emitSelection([{
  id: 'record-2',
  code: 'WO-2',
  supplierSecret: 'must-not-cross-origin'
}]), true)
assert.equal(bridge.sent.at(-1).type, 'selection.changed')
assert.deepEqual(bridge.sent.at(-1).payload.selection, [{
  id: 'record-2',
  values: {}
}])

const [heartbeatTimerId, heartbeatTimer] = timers.first()
assert.equal(heartbeatTimer.delay, 60_000)
timers.take(heartbeatTimerId)
await heartbeatTimer.callback()
assert.equal(calls.at(-1).operation, 'heartbeat')
assert.equal(timers.first()[1].delay, 90_000)

runtimeQueryError = Object.assign(new Error('view revoked'), {
  errorCode: 'EMBED_VIEW_NOT_GRANTED',
  status: 403
})
await controller.refreshList()
assert.equal(controller.getSnapshot().state, EMBED_RUNTIME_STATES.READY)
assert.equal(controller.getSnapshot().listError.category, 'RESOURCE')
assert.equal(controller.getSnapshot().listError.recoverable, false)

await controller.destroy()
assert.equal(calls.at(-1).operation, 'logout')
assert.equal(controller.getSnapshot().state, EMBED_RUNTIME_STATES.DESTROYED)

// destroy ACK 必须严格晚于 DELETE Session 完成，重复命令复用同一注销。
const delayedLogout = deferred()
let delayedLogoutCalls = 0
const delayedBridge = createBridgeHarness()
const delayedController = createEmbedRuntimeController({
  entryConfig,
  api: {
    ...api,
    async logout() {
      delayedLogoutCalls += 1
      return delayedLogout.promise
    }
  },
  bridgeFactory: delayedBridge.factory,
  setTimeoutImpl() { return 1 },
  clearTimeoutImpl() {}
})
delayedController.start()
await delayedBridge.initialize()
const delayedAck = delayedBridge.waitForSent(message => message.type === 'ack'
  && message.payload.command === 'destroy')
delayedBridge.command('destroy', {}, 'request_destroy_delayed_01')
delayedBridge.command('destroy', {}, 'request_destroy_delayed_02')
await Promise.resolve()
assert.equal(delayedController.getSnapshot().state, EMBED_RUNTIME_STATES.DESTROYING)
assert.equal(delayedLogoutCalls, 1)
assert.equal(delayedBridge.sent.some(message => message.type === 'ack'
  && message.payload.command === 'destroy'), false)
delayedLogout.resolve()
const completedDestroyAck = await delayedAck
assert.equal(completedDestroyAck.options.requestId, 'request_destroy_delayed_01')
await delayedBridge.waitForSent(message => message.type === 'ack'
  && message.options.requestId === 'request_destroy_delayed_02')
assert.equal(delayedController.getSnapshot().state, EMBED_RUNTIME_STATES.DESTROYED)

// 直达 FORM 只消费 Bootstrap 的固定原生坐标；不能再读取 projected form/schema。
const nativeTarget = Object.freeze({
  entityCode: 'work_order',
  formId: 'form-001',
  formReleaseId: 'form-release-001',
  formReleaseVersion: 3,
  formReleaseResolutionToken: 'resolution_token_0123456789',
  entryMode: 'CREATE',
  recordId: null,
  initialData: { supplierId: 'supplier-1' },
  parameters: { source: 'partner' },
  context: { permissions: ['entity:record:create'] }
})
const nativeBootstrap = {
  ...bootstrap,
  actor: {
    displayName: '映射用户',
    username: 'mapped-user',
    nickname: '合作方用户',
    roles: ['operator'],
    permissions: ['entity:record:create'],
    isSuperAdmin: false
  },
  view: {
    key: 'supplier-work-order-create',
    name: '新建工单',
    surfaceType: 'FORM',
    revision: 8,
    entryMode: 'CREATE'
  },
  capabilities: ['RECORD_CREATE'],
  target: nativeTarget
}
const nativeCalls = []
const mappedActors = []
let delegatedReady = 0
let delegatedReset = 0
let nativeRefreshError = null
let nativeRefreshResult = true
const nativeBridge = createBridgeHarness()
const nativeController = createEmbedRuntimeController({
  entryConfig,
  api: {
    ...api,
    async getBootstrap() {
      nativeCalls.push('bootstrap')
      return nativeBootstrap
    },
    async getSchema() {
      throw new Error('direct FORM must not request schema')
    },
    async queryList() {
      throw new Error('direct FORM must not query list')
    },
    async createRecord(body, options) {
      nativeCalls.push({ operation: 'createRecord', body, options })
      return {
        receiptId: 'eor_0123456789abcdef',
        record: { id: 'record-created', values: { supplierId: body.data.supplierId } },
        clientMutationId: body.clientMutationId
      }
    }
  },
  bridgeFactory: nativeBridge.factory,
  onDelegatedSessionReady() { delegatedReady += 1 },
  onDelegatedSessionReset() { delegatedReset += 1 },
  onRuntimeIdentityReady(actor) { mappedActors.push(actor) },
  async onRefreshNativeForm() {
    if (nativeRefreshError) throw nativeRefreshError
    return nativeRefreshResult
  },
  setTimeoutImpl() { return 1 },
  clearTimeoutImpl() {}
})
nativeController.start()
await nativeBridge.initialize()
const nativeSnapshot = nativeController.getSnapshot()
assert.equal(nativeSnapshot.state, EMBED_RUNTIME_STATES.READY)
assert.equal(nativeSnapshot.navigation.surfaceType, 'FORM')
assert.equal(nativeSnapshot.nativeFormTarget.formId, 'form-001')
assert.equal(nativeSnapshot.nativeFormTarget.formReleaseId, 'form-release-001')
assert.equal(nativeSnapshot.nativeFormTarget.formReleaseVersion, 3)
assert.equal(nativeSnapshot.nativeFormTarget.mode, 'CREATE')
assert.deepEqual(nativeSnapshot.nativeFormTarget.initialData, { supplierId: 'supplier-1' })
assert.deepEqual(nativeSnapshot.nativeFormTarget.parameters, { source: 'partner' })
assert.deepEqual(mappedActors[0].permissions, ['entity:record:create'])
assert.equal(delegatedReady, 1)
assert.deepEqual(nativeCalls, ['bootstrap'])

nativeRefreshError = Object.assign(new Error('暂时不可用'), {
  errorCode: 'EMBED_RUNTIME_UNAVAILABLE', status: 503, traceId: 'refresh-trace-001'
})
nativeBridge.command('refresh', {}, 'refresh_failure_01')
const refreshFailure = await nativeBridge.waitForSent(message =>
  message.type === 'error' && message.options?.requestId === 'refresh_failure_01')
assert.equal(refreshFailure.payload.traceId, 'refresh-trace-001')
assert.equal(refreshFailure.payload.recoverable, true)
assert.equal(nativeController.getSnapshot().state, EMBED_RUNTIME_STATES.READY)
assert.equal(nativeController.getSnapshot().formLoading, false)
assert.equal(nativeBridge.sent.some(message =>
  message.type === 'ack' && message.options?.requestId === 'refresh_failure_01'), false)

nativeRefreshError = null
nativeRefreshResult = false
nativeBridge.command('refresh', {}, 'refresh_cancelled_01')
const refreshCancelled = await nativeBridge.waitForSent(message =>
  message.type === 'error' && message.options?.requestId === 'refresh_cancelled_01')
assert.equal(refreshCancelled.payload.errorCode, 'EMBED_OPERATION_NOT_ALLOWED')
assert.match(refreshCancelled.payload.message, /内容已保留/)
assert.equal(nativeController.getSnapshot().nativeFormTarget.formId, 'form-001')

nativeRefreshResult = true
nativeBridge.command('refresh', {}, 'refresh_success_01')
await nativeBridge.waitForSent(message =>
  message.type === 'ack' && message.options?.requestId === 'refresh_success_01')
assert.equal(nativeController.getSnapshot().formError, null)

await nativeController.submitNativeRecord({ supplierId: 'supplier-1' }, 'save')
const createCall = nativeCalls.find(call => call?.operation === 'createRecord')
assert.deepEqual(createCall.body.data, { supplierId: 'supplier-1' })
assert.equal(createCall.body.actionKey, 'save')
assert.match(createCall.options.idempotencyKey, /^emb_/)
assert.equal(nativeBridge.sent.at(-1).type, 'form.saved')
assert.equal(nativeBridge.sent.at(-1).payload.receiptId, 'eor_0123456789abcdef')
nativeController.requestClose('native-flow-form-closed')
assert.equal(nativeBridge.sent.at(-1).type, 'close.requested')
await nativeController.destroy()
assert.equal(delegatedReset, 1)

// Logout 失败只发关联 ERROR，不发 ACK；第二次 destroy 可幂等重试。
let retryLogoutCalls = 0
const retryBridge = createBridgeHarness()
const retryController = createEmbedRuntimeController({
  entryConfig,
  api: {
    ...api,
    async logout() {
      retryLogoutCalls += 1
      if (retryLogoutCalls === 1) {
        throw Object.assign(new Error('offline'), { errorCode: 'EMBED_NETWORK_ERROR' })
      }
    }
  },
  bridgeFactory: retryBridge.factory,
  setTimeoutImpl() { return 1 },
  clearTimeoutImpl() {}
})
retryController.start()
await retryBridge.initialize()
retryBridge.command('destroy', {}, 'request_destroy_retry_001')
const destroyError = await retryBridge.waitForSent(message => message.type === 'error'
  && message.options?.requestId === 'request_destroy_retry_001')
assert.equal(destroyError.payload.errorCode, 'EMBED_SESSION_RELEASE_UNCONFIRMED')
assert.equal(retryBridge.sent.some(message => message.type === 'ack'
  && message.options?.requestId === 'request_destroy_retry_001'), false)
retryBridge.command('destroy', {}, 'request_destroy_retry_002')
await retryBridge.waitForSent(message => message.type === 'ack'
  && message.options.requestId === 'request_destroy_retry_002')
assert.equal(retryLogoutCalls, 2)

// Exchange 在途时的 destroy 必须等待 Token，随后 Logout，且不得继续 Bootstrap。
const pendingExchange = deferred()
const exchangeClosingCalls = []
const exchangeClosingBridge = createBridgeHarness()
const exchangeClosingController = createEmbedRuntimeController({
  entryConfig,
  api: {
    ...api,
    async exchange() {
      exchangeClosingCalls.push('exchange')
      return pendingExchange.promise
    },
    async getBootstrap() {
      exchangeClosingCalls.push('bootstrap')
      return bootstrap
    },
    async logout() {
      exchangeClosingCalls.push('logout')
    }
  },
  bridgeFactory: exchangeClosingBridge.factory,
  setTimeoutImpl() { return 1 },
  clearTimeoutImpl() {}
})
exchangeClosingController.start()
const initialization = exchangeClosingBridge.initialize()
await Promise.resolve()
exchangeClosingBridge.command('destroy', {}, 'request_destroy_exchange_01')
assert.equal(exchangeClosingController.getSnapshot().state, EMBED_RUNTIME_STATES.DESTROYING)
pendingExchange.resolve({
  accessToken: 'embed_token_exchange_closing_abcdefghijklmnopqrstuvwxyz',
  tokenType: 'Bearer',
  expiresAt: '2099-08-27T09:00:00.000Z',
  idleExpiresAt: '2099-08-27T08:35:00.000Z',
  heartbeatAfterSeconds: 60,
  protocolVersion: 'flow-embed/1'
})
await exchangeClosingBridge.waitForSent(message => message.type === 'ack'
  && message.options.requestId === 'request_destroy_exchange_01')
await initialization
assert.deepEqual(exchangeClosingCalls, ['exchange', 'logout'])
assert.equal(exchangeClosingController.getSnapshot().state, EMBED_RUNTIME_STATES.DESTROYED)

// Exchange 失败即使已经 settle，仍可能是“服务端提交、响应丢失”；
// 没有 Token 时必须 fail closed，不得伪造 destroy ACK。
const ambiguousBridge = createBridgeHarness()
const ambiguousController = createEmbedRuntimeController({
  entryConfig,
  api: {
    ...api,
    async exchange() {
      throw Object.assign(new Error('exchange response lost'), {
        errorCode: 'EMBED_NETWORK_ERROR'
      })
    }
  },
  bridgeFactory: ambiguousBridge.factory,
  setTimeoutImpl() { return 1 },
  clearTimeoutImpl() {}
})
ambiguousController.start()
await ambiguousBridge.initialize()
ambiguousBridge.command('destroy', {}, 'request_destroy_ambiguous_1')
await ambiguousBridge.waitForSent(message => message.type === 'error'
  && message.options?.requestId === 'request_destroy_ambiguous_1')
assert.equal(ambiguousBridge.sent.some(message => message.type === 'ack'
  && message.options?.requestId === 'request_destroy_ambiguous_1'), false)

const expiredBridge = createBridgeHarness()
const expiredApi = {
  ...api,
  async getBootstrap() {
    const error = new Error('expired')
    error.errorCode = 'EMBED_SESSION_EXPIRED'
    error.status = 401
    throw error
  }
}
const expiredController = createEmbedRuntimeController({
  entryConfig,
  api: expiredApi,
  bridgeFactory: expiredBridge.factory,
  setTimeoutImpl() { return 1 },
  clearTimeoutImpl() {}
})
expiredController.start()
await expiredBridge.initialize()
assert.equal(expiredController.getSnapshot().state, EMBED_RUNTIME_STATES.SESSION_EXPIRED)
assert.equal(expiredController.getSnapshot().error.relaunchRequired, true)
assert.equal(expiredBridge.sent.at(-1).type, 'session.expired')

console.log('embed runtime state machine tests passed')
