import assert from 'node:assert/strict'
import {
  EMBED_RUNTIME_STATES,
  createEmbedRuntimeController
} from '../src/embed/runtime/embedRuntimeController.js'

const TEST_LAUNCH_CODE = 'A'.repeat(43)
const PARENT_NONCE = Buffer.alloc(32, 201).toString('base64url')
const CHILD_NONCE = Buffer.alloc(32, 151).toString('base64url')

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
    revision: 7,
    entryMode: 'LIST'
  },
  capabilities: ['LIST_QUERY', 'SELECTION_RETURN'],
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
  let state = 'idle'
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
        send(type, payload) {
          sent.push({ type, payload })
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
    command(type, payload = {}) {
      return options.onMessage({
        type,
        payload,
        messageId: 'message_000000000001',
        requestId: 'request_00000000001'
      })
    }
  }
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
  now: () => new Date('2026-08-27T08:31:00.000Z')
})
controller.subscribe(snapshot => states.push(`${snapshot.state}:${snapshot.phase}`))

assert.equal(controller.start(), CHILD_NONCE)
assert.equal(controller.getSnapshot().state, EMBED_RUNTIME_STATES.WAITING_HANDSHAKE)
await bridge.initialize()

assert.deepEqual(calls.slice(0, 4).map(call => call.operation), [
  'exchange',
  'bootstrap',
  'schema',
  'query'
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
assert.equal(controller.getSnapshot().page.items[0].values.secret, undefined)
assert.ok(states.includes('EXCHANGING:exchange'))
assert.ok(states.includes('BOOTSTRAPPING:bootstrap'))
assert.ok(states.includes('BOOTSTRAPPING:schema'))
assert.ok(states.includes('BOOTSTRAPPING:query'))
assert.ok(states.includes('READY:ready'))
assert.equal(bridge.sent[0].type, 'initialized')

bridge.command('set-theme', { theme: 'dark' })
assert.equal(controller.getSnapshot().theme, 'dark')
assert.equal(bridge.sent.at(-1).type, 'ack')
assert.equal(bridge.sent.at(-1).payload.command, 'set-theme')

await controller.refreshList({
  queryValues: { code: 'WO-2', fixedFilters: { tenant: 'other' } },
  pageNum: 2,
  pageSize: 20
})
assert.equal(controller.getSnapshot().page.pageNum, 2)
assert.deepEqual(calls.at(-1).query.filters, [{ field: 'code', value: 'WO-2' }])

assert.equal(controller.emitSelection(controller.getSnapshot().page.items), true)
assert.equal(bridge.sent.at(-1).type, 'selection.changed')
assert.deepEqual(bridge.sent.at(-1).payload.selection, [{
  id: 'record-2',
  values: { code: 'WO-2' }
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
assert.equal(controller.getSnapshot().state, EMBED_RUNTIME_STATES.FATAL_ERROR)
assert.equal(controller.getSnapshot().error.category, 'RESOURCE')
assert.equal(controller.getSnapshot().error.recoverable, false)

await controller.destroy()
assert.equal(calls.at(-1).operation, 'logout')
assert.equal(controller.getSnapshot().state, EMBED_RUNTIME_STATES.DESTROYED)

const expiredBridge = createBridgeHarness()
const expiredApi = {
  ...api,
  async queryList() {
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
