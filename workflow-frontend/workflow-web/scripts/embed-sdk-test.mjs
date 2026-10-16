import assert from 'node:assert/strict'
import {
  FLOW_EMBED_COMMANDS,
  FLOW_EMBED_EVENTS,
  FLOW_EMBED_PROTOCOL,
  FlowEmbedError,
  createSecureNonce,
  mount
} from '../packages/flow-embed-sdk/src/index.js'
import { validateEventPayload } from '../packages/flow-embed-sdk/src/protocol.js'

const TEST_LAUNCH_CODE = 'A'.repeat(43)
const CHILD_NONCE = Buffer.alloc(32, 151).toString('base64url')
const testNonce = value => Buffer.alloc(32, value).toString('base64url')

let secureRandomCalled = false
const generatedNonce = createSecureNonce({
  randomUUID() { throw new Error('randomUUID 不得被调用') },
  getRandomValues(bytes) {
    assert.equal(bytes.length, 32)
    secureRandomCalled = true
    bytes.fill(37)
    return bytes
  }
})
assert.equal(secureRandomCalled, true)
assert.equal(generatedNonce.length, 43)
assert.equal(Buffer.from(generatedNonce, 'base64url').length, 32)
assert.throws(
  () => createSecureNonce({ randomUUID() { return 'must-not-fallback' } }),
  error => error.errorCode === 'FLOW_EMBED_CRYPTO_UNAVAILABLE'
)
assert.throws(
  () => createSecureNonce({ getRandomValues() { throw new Error('CSPRNG failed') } }),
  error => error.errorCode === 'FLOW_EMBED_CRYPTO_UNAVAILABLE'
)

const maximumRecordId = `A${'b'.repeat(127)}`
const selectionPayload = (id, values) => ({ selection: [{ id, values }] })
assert.equal(validateEventPayload(
  FLOW_EMBED_EVENTS.SELECTION_CHANGED,
  selectionPayload(maximumRecordId, {
    description: 'x'.repeat(100000),
    tags: Array.from({ length: 100 }, (_, index) => `tag-${index}`)
  })
), true)
for (const invalidPayload of [
  selectionPayload('record-1', { description: 'x'.repeat(100001) }),
  selectionPayload('record-1', { tags: Array(101).fill('tag') }),
  selectionPayload('A'.repeat(129), {}),
  selectionPayload('record/1', {})
]) {
  assert.equal(
    validateEventPayload(FLOW_EMBED_EVENTS.SELECTION_CHANGED, invalidPayload),
    false
  )
}

assert.equal(validateEventPayload(
  FLOW_EMBED_EVENTS.INITIALIZED,
  {
    viewKey: 'supplier-work-orders',
    surfaceType: 'FORM',
    capabilities: ['RECORD_CREATE', 'ACTION_EXECUTE']
  }
), true)

function createFakePort() {
  const listeners = new Set()
  return {
    messages: [],
    closed: false,
    started: false,
    postMessage(message) { this.messages.push(structuredClone(message)) },
    addEventListener(type, listener) { if (type === 'message') listeners.add(listener) },
    removeEventListener(type, listener) { if (type === 'message') listeners.delete(listener) },
    start() { this.started = true },
    close() { this.closed = true },
    emit(data) { for (const listener of [...listeners]) listener({ data }) },
    listenerCount() { return listeners.size }
  }
}

function createHarness(overrides = {}) {
  const windowListeners = new Set()
  const iframeMessages = []
  const ports = []
  const contentWindow = {
    postMessage(message, targetOrigin, transferredPorts) {
      // 浏览器会在 postMessage 返回前完成结构化克隆，测试也必须保存克隆而不是原对象引用。
      iframeMessages.push({ message: structuredClone(message), targetOrigin, transferredPorts })
    }
  }
  const container = {
    children: [],
    appendChild(node) {
      node.parentNode = this
      this.children.push(node)
    },
    removeChild(node) {
      this.children = this.children.filter(item => item !== node)
      node.parentNode = null
    }
  }
  const documentRef = {
    createElement(name) {
      assert.equal(name, 'iframe')
      const attributes = new Map()
      return {
        contentWindow,
        style: {},
        setAttribute(key, value) { attributes.set(key, value) },
        getAttribute(key) { return attributes.get(key) },
        remove() { this.parentNode?.removeChild(this) }
      }
    }
  }
  const windowRef = {
    addEventListener(type, listener) { if (type === 'message') windowListeners.add(listener) },
    removeEventListener(type, listener) { if (type === 'message') windowListeners.delete(listener) }
  }
  let nonceSequence = 0
  const widget = mount({
    container,
    embedUrl: 'https://embed.flow.example.com/embed/v1/launches/lch_0123456789abcdef',
    launchId: 'lch_0123456789abcdef',
    launchCode: TEST_LAUNCH_CODE,
    channelId: 'channel:12345678',
    targetOrigin: 'https://embed.flow.example.com',
    windowRef,
    documentRef,
    nonceFactory: () => testNonce(++nonceSequence),
    messageChannelFactory() {
      const port1 = createFakePort()
      const port2 = createFakePort()
      ports.push({ port1, port2 })
      return { port1, port2 }
    },
    handshakeTimeoutMs: 0,
    maxMessageBytes: 1024,
    ...overrides
  })
  return { widget, windowListeners, iframeMessages, contentWindow, container, ports }
}

// 浏览器原生 Window timer 不允许被挂到 Widget 实例后以错误接收者调用。
// 严格函数在旧实现中会收到 widget 作为 this，因此这里同时守住 timeout 与 cleanup。
let strictTimerCleared = false
const strictTimerHarness = createHarness({
  handshakeTimeoutMs: 1,
  setTimeoutImpl(callback, delay) {
    assert.equal(this, undefined)
    assert.equal(typeof callback, 'function')
    assert.equal(delay, 1)
    return 37
  },
  clearTimeoutImpl(timerId) {
    assert.equal(this, undefined)
    assert.equal(timerId, 37)
    strictTimerCleared = true
  }
})
strictTimerHarness.widget.destroy()
assert.equal(strictTimerCleared, true)

function emitWindow(harness, { source = harness.contentWindow, origin = 'https://embed.flow.example.com', data }) {
  for (const listener of [...harness.windowListeners]) listener({ source, origin, data })
}

function ready(overrides = {}) {
  return {
    protocol: FLOW_EMBED_PROTOCOL,
    type: 'ready',
    launchId: 'lch_0123456789abcdef',
    channelId: 'channel:12345678',
    childNonce: CHILD_NONCE,
    supportedVersions: [FLOW_EMBED_PROTOCOL],
    ...overrides
  }
}

function connect(harness) {
  emitWindow(harness, { data: ready() })
  const init = harness.iframeMessages.at(-1).message
  harness.ports[0].port1.emit({
    protocol: FLOW_EMBED_PROTOCOL,
    type: 'init.ack',
    launchId: 'lch_0123456789abcdef',
    channelId: 'channel:12345678',
    childNonce: CHILD_NONCE,
    parentNonce: init.parentNonce
  })
  assert.equal(harness.widget.getState(), 'connected')
  return init
}

function eventMessage(init, overrides = {}) {
  return {
    protocol: FLOW_EMBED_PROTOCOL,
    type: 'selection.changed',
    launchId: 'lch_0123456789abcdef',
    channelId: 'channel:12345678',
    messageId: 'message_000000000001',
    requestId: null,
    timestamp: '2026-08-27T08:00:00.000Z',
    childNonce: CHILD_NONCE,
    parentNonce: init.parentNonce,
    payload: { selection: [] },
    ...overrides
  }
}

async function acknowledgeDestroy(harness, init, promise, messageId) {
  const command = harness.ports[0].port1.messages.at(-1)
  assert.equal(command.type, FLOW_EMBED_COMMANDS.DESTROY)
  harness.ports[0].port1.emit(eventMessage(init, {
    type: FLOW_EMBED_EVENTS.ACK,
    messageId,
    requestId: command.requestId,
    payload: { command: FLOW_EMBED_COMMANDS.DESTROY }
  }))
  await promise
}

// 所有公开凭据/URL 边界在创建 iframe 前校验。
const base = {
  container: { appendChild() {} },
  embedUrl: 'https://embed.flow.example.com/embed/v1/launches/lch_0123456789abcdef',
  launchId: 'lch_0123456789abcdef',
  launchCode: TEST_LAUNCH_CODE,
  channelId: 'channel:12345678',
  targetOrigin: 'https://embed.flow.example.com',
  windowRef: { addEventListener() {}, removeEventListener() {} },
  documentRef: { createElement: () => ({ style: {}, setAttribute() {}, contentWindow: {} }) },
  handshakeTimeoutMs: 0
}
for (const options of [
  { embedUrl: 'http://embed.flow.example.com/embed/v1/launches/lch_0123456789abcdef' },
  { embedUrl: 'https://evil.example/embed/v1/launches/lch_0123456789abcdef' },
  { embedUrl: 'https://embed.flow.example.com/admin' },
  { embedUrl: 'https://embed.flow.example.com/embed/v1/launches/lch_0123456789abcdef?code=secret' },
  { embedUrl: 'https://embed.flow.example.com/embed/v1/launches/lch_0123456789abcdef#code=secret' },
  { targetOrigin: 'http://embed.flow.example.com' },
  { targetOrigin: 'https://embed.flow.example.com/path' },
  { launchId: '../bad' },
  { channelId: 'short' },
  { protocol: 'flow-embed/2' }
]) {
  assert.throws(() => mount({ ...base, ...options }), FlowEmbedError)
}

const violations = []
const received = []
const harness = createHarness({
  onEvent: event => received.push(event),
  onViolation: error => violations.push(error.errorCode)
})
assert.equal(harness.container.children.length, 1)
assert.equal(harness.widget.iframe.src, base.embedUrl)
assert.equal(harness.widget.iframe.src.includes('launch_code'), false)
assert.equal(new URL(harness.widget.iframe.src).search, '')
assert.equal(new URL(harness.widget.iframe.src).hash, '')

// 浏览器、扩展和开发工具产生的无关 window.message 必须静默忽略，不能污染宿主日志。
emitWindow(harness, { source: {}, origin: 'chrome-extension://example', data: { type: 'extension.event' } })
emitWindow(harness, {
  source: {},
  origin: 'https://evil.example',
  data: ready({ protocol: 'flow-embed/2', supportedVersions: ['flow-embed/2'] })
})
emitWindow(harness, {
  source: {},
  origin: 'https://evil.example',
  data: ready({ launchId: 'lch_fedcba9876543210' })
})
assert.deepEqual(violations, [])

// 只有声称属于当前握手的 ready 才做 fail-closed 来源与完整协议校验。
emitWindow(harness, { source: {}, data: ready() })
emitWindow(harness, { origin: 'https://evil.example', data: ready() })
emitWindow(harness, { data: ready({ extra: 'forbidden' }) })
emitWindow(harness, { data: ready({ supportedVersions: [FLOW_EMBED_PROTOCOL, 'flow-embed/2'] }) })
assert.equal(harness.iframeMessages.length, 0)
assert.deepEqual(violations, [
  'FLOW_EMBED_READY_SOURCE_INVALID',
  'FLOW_EMBED_READY_SOURCE_INVALID',
  'FLOW_EMBED_READY_INVALID',
  'FLOW_EMBED_READY_INVALID'
])

emitWindow(harness, { data: ready() })
assert.equal(harness.widget.getState(), 'acknowledging')
assert.equal(harness.iframeMessages.length, 1)
const transmittedInit = harness.iframeMessages[0]
assert.equal(transmittedInit.targetOrigin, 'https://embed.flow.example.com')
assert.equal(transmittedInit.message.launchCode, TEST_LAUNCH_CODE)
assert.equal(Buffer.from(transmittedInit.message.parentNonce, 'base64url').length, 32)
assert.equal(Buffer.from(transmittedInit.message.childNonce, 'base64url').length, 32)
assert.equal(harness.widget._launchCode, '', 'code 传输后必须从 SDK 内存清除')
assert.equal(harness.widget.iframe.src.includes(transmittedInit.message.launchCode), false)
assert.equal(transmittedInit.transferredPorts.length, 1)

// 错误版本/nonce 的 init.ack 不得建立连接。
harness.ports[0].port1.emit({ ...transmittedInit.message, type: 'init.ack', protocol: 'flow-embed/2' })
assert.equal(harness.widget.getState(), 'acknowledging')
harness.ports[0].port1.emit({
  protocol: FLOW_EMBED_PROTOCOL,
  type: 'init.ack',
  launchId: 'lch_0123456789abcdef',
  channelId: 'channel:12345678',
  childNonce: CHILD_NONCE,
  parentNonce: transmittedInit.message.parentNonce,
  extra: 'forbidden'
})
assert.equal(harness.widget.getState(), 'acknowledging')
assert.equal(violations.at(-1), 'FLOW_EMBED_INIT_ACK_INVALID')
harness.ports[0].port1.emit({
  protocol: FLOW_EMBED_PROTOCOL,
  type: 'init.ack',
  launchId: 'lch_0123456789abcdef',
  channelId: 'channel:12345678',
    childNonce: CHILD_NONCE,
  parentNonce: transmittedInit.message.parentNonce
})
assert.equal(harness.widget.getState(), 'connected')
assert.equal(harness.ports[0].port1.started, true)

// 命令仅允许五种封闭类型，且 envelope 与 iframe bridge 完全匹配。
const requestId = harness.widget.refresh()
const command = harness.ports[0].port1.messages.at(-1)
assert.equal(command.type, FLOW_EMBED_COMMANDS.REFRESH)
assert.equal(command.requestId, requestId)
assert.equal(command.childNonce, CHILD_NONCE)
assert.equal(command.parentNonce, transmittedInit.message.parentNonce)
assert.throws(() => harness.widget.sendCommand('set-context', {}), /未授权/)
assert.throws(() => harness.widget.setTheme('blue'), /未授权/)

const selection = eventMessage(transmittedInit.message)
let subscribedSelections = 0
const unsubscribe = harness.widget.on('selection.changed', () => { subscribedSelections += 1 })
harness.ports[0].port1.emit(selection)
assert.equal(received.at(-1).type, 'selection.changed')
assert.equal(subscribedSelections, 1)
unsubscribe()
harness.ports[0].port1.emit(selection)
assert.equal(received.filter(item => item.type === 'selection.changed').length, 1)
assert.equal(subscribedSelections, 1)
assert.equal(violations.at(-1), 'FLOW_EMBED_EVENT_REPLAYED')

// 超大消息和原型污染字段均在调用宿主事件前被拒绝。
harness.ports[0].port1.emit(eventMessage(transmittedInit.message, {
  messageId: 'message_000000000002',
  payload: { value: 'x'.repeat(2000) }
}))
assert.equal(violations.at(-1), 'FLOW_EMBED_EVENT_INVALID')
const receivedBeforeInvalidInitialized = received.length
harness.ports[0].port1.emit(eventMessage(transmittedInit.message, {
  type: 'initialized',
  messageId: 'message_000000000098',
  payload: {
    viewKey: 'supplier-work-orders',
    surfaceType: 'LIST',
    capabilities: ['LIST_QUERY', 'INTERNAL_ADMIN']
  }
}))
assert.equal(received.length, receivedBeforeInvalidInitialized)
assert.equal(violations.at(-1), 'FLOW_EMBED_EVENT_FORBIDDEN')
const receivedBeforeForgedSave = received.length
harness.ports[0].port1.emit(eventMessage(transmittedInit.message, {
  type: 'form.saved',
  messageId: 'message_000000000099',
  payload: {
    receiptId: 'eor_0123456789abcdef',
    record: { id: 'record-1', values: { code: 'WO-1' } },
    clientMutationId: 'cm_0123456789abcdef',
    effects: [{ type: 'redirect', url: 'https://evil.example' }]
  }
}))
assert.equal(received.length, receivedBeforeForgedSave, '未知 form.saved 字段不得到达宿主回调')
assert.equal(violations.at(-1), 'FLOW_EMBED_EVENT_FORBIDDEN')
const receivedBeforeEditNavigation = received.length
harness.ports[0].port1.emit(eventMessage(transmittedInit.message, {
  type: 'navigation.request',
  messageId: 'message_000000000100',
  payload: { target: 'EDIT', recordId: 'record-1' }
}))
assert.equal(received.length, receivedBeforeEditNavigation, 'V1 不得向宿主请求 EDIT 导航')
assert.equal(violations.at(-1), 'FLOW_EMBED_EVENT_FORBIDDEN')
const polluted = JSON.parse('{"selection":[],"__proto__":{"admin":true}}')
harness.ports[0].port1.emit(eventMessage(transmittedInit.message, {
  messageId: 'message_000000000003', payload: polluted
}))
assert.equal(violations.at(-1), 'FLOW_EMBED_EVENT_INVALID')
assert.equal({}.admin, undefined)

// 自动高度始终夹在宿主显式 min/max 内。
const heightHarness = createHarness({ height: { mode: 'auto', min: 480, max: 1200 } })
const heightInit = connect(heightHarness)
heightHarness.ports[0].port1.emit(eventMessage(heightInit, {
  type: 'resize', messageId: 'message_000000000004', payload: { height: 5000 }
}))
assert.equal(heightHarness.widget.iframe.style.height, '1200px')
await acknowledgeDestroy(
  heightHarness,
  heightInit,
  heightHarness.widget.destroy(),
  'message_destroy_height_0001'
)

// destroy 必须保留 iframe/port 等待关联 ACK，重复调用不重复发命令。
const oldIframe = harness.widget.iframe
const port = harness.ports[0].port1
const destroyPromise = harness.widget.destroy()
assert.equal(harness.widget.destroy(), destroyPromise)
assert.equal(harness.widget.getState(), 'destroying')
assert.equal(port.closed, false)
assert.equal(harness.container.children.includes(oldIframe), true)
assert.equal(port.messages.filter(message => message.type === FLOW_EMBED_COMMANDS.DESTROY).length, 1)
const destroyCommand = port.messages.at(-1)
port.emit(eventMessage(transmittedInit.message, {
  type: FLOW_EMBED_EVENTS.ACK,
  messageId: 'message_destroy_wrong_001',
  requestId: 'request_destroy_wrong_001',
  payload: { command: FLOW_EMBED_COMMANDS.DESTROY }
}))
assert.equal(harness.widget.getState(), 'destroying', '不匹配 requestId 不得提前拆除 iframe')
port.emit(eventMessage(transmittedInit.message, {
  type: FLOW_EMBED_EVENTS.ACK,
  messageId: 'message_destroy_match_001',
  requestId: destroyCommand.requestId,
  payload: { command: FLOW_EMBED_COMMANDS.DESTROY }
}))
await destroyPromise
assert.equal(harness.widget.getState(), 'destroyed')
assert.equal(harness.widget._launchCode, '')
assert.equal(port.closed, true)
assert.equal(port.listenerCount(), 0)
assert.equal(harness.windowListeners.size, 0)
assert.equal(harness.container.children.includes(oldIframe), false)
const receivedBeforeStaleMessage = received.length
port.emit(eventMessage(transmittedInit.message, { messageId: 'message_000000000005' }))
assert.equal(received.length, receivedBeforeStaleMessage)

let defaultNonceCalls = 0
const defaultNonceHarness = createHarness({
  nonceFactory: undefined,
  cryptoRef: {
    getRandomValues(bytes) {
      defaultNonceCalls += 1
      bytes.fill(defaultNonceCalls)
      return bytes
    }
  }
})
emitWindow(defaultNonceHarness, { data: ready() })
const defaultInit = defaultNonceHarness.iframeMessages[0].message
assert.equal(defaultNonceCalls, 1)
assert.equal(defaultInit.parentNonce.length, 43)
assert.equal(Buffer.from(defaultInit.parentNonce, 'base64url').length, 32)
assert.equal(Buffer.from(defaultInit.childNonce, 'base64url').length, 32)
defaultNonceHarness.ports[0].port1.emit({
  protocol: FLOW_EMBED_PROTOCOL,
  type: 'init.ack',
  launchId: 'lch_0123456789abcdef',
  channelId: 'channel:12345678',
  childNonce: CHILD_NONCE,
  parentNonce: defaultInit.parentNonce
})
await acknowledgeDestroy(
  defaultNonceHarness,
  defaultInit,
  defaultNonceHarness.widget.destroy(),
  'message_destroy_nonce_001'
)

// init.ack 在途时 destroy 也要经 MessagePort 注销，不能直接拆 iframe 泄漏 Session。
const acknowledgingHarness = createHarness()
emitWindow(acknowledgingHarness, { data: ready() })
const acknowledgingInit = acknowledgingHarness.iframeMessages[0].message
const acknowledgingDestroy = acknowledgingHarness.widget.destroy()
assert.equal(acknowledgingHarness.widget.getState(), 'destroying')
acknowledgingHarness.ports[0].port1.emit({
  protocol: FLOW_EMBED_PROTOCOL,
  type: 'init.ack',
  launchId: 'lch_0123456789abcdef',
  channelId: 'channel:12345678',
  childNonce: CHILD_NONCE,
  parentNonce: acknowledgingInit.parentNonce
})
assert.equal(acknowledgingHarness.widget.getState(), 'destroying')
await acknowledgeDestroy(
  acknowledgingHarness,
  acknowledgingInit,
  acknowledgingDestroy,
  'message_destroy_acknowledging_001'
)

// 子页没有确认时超时 fail-safe：本地资源必须清理，Promise 拒绝以阻止新 Launch。
let destroyTimeoutCallback
const timeoutHarness = createHarness({
  destroyTimeoutMs: 1000,
  setTimeoutImpl(callback) {
    destroyTimeoutCallback = callback
    return 91
  },
  clearTimeoutImpl() {}
})
const timeoutInit = connect(timeoutHarness)
assert.ok(timeoutInit)
const timedDestroy = timeoutHarness.widget.destroy()
destroyTimeoutCallback()
await assert.rejects(timedDestroy, error => error.errorCode === 'FLOW_EMBED_DESTROY_TIMEOUT')
assert.equal(timeoutHarness.widget.getState(), 'destroyed')
assert.equal(timeoutHarness.container.children.length, 0)
assert.equal(timeoutHarness.widget.destroy(), timedDestroy)

// 握手超时是否能够确认清理，取决于一次性 code 是否已经交给子页。
for (const initTransferred of [false, true]) {
  let handshakeTimeoutCallback
  const failedHandshake = createHarness({
    handshakeTimeoutMs: 1000,
    setTimeoutImpl(callback) {
      handshakeTimeoutCallback = callback
      return 92
    },
    clearTimeoutImpl() {}
  })
  if (initTransferred) emitWindow(failedHandshake, { data: ready() })
  handshakeTimeoutCallback()
  assert.equal(failedHandshake.widget.getState(), 'failed')
  assert.equal(failedHandshake.container.children.length, 0)
  const failedHandshakeDestroy = failedHandshake.widget.destroy()
  if (initTransferred) {
    await assert.rejects(failedHandshakeDestroy, error => (
      error.errorCode === 'FLOW_EMBED_DESTROY_FAILED' && /回收未确认/.test(error.message)
    ))
  } else {
    await failedHandshakeDestroy
  }
  assert.equal(failedHandshake.widget.getState(), 'destroyed')
  assert.equal(failedHandshake.widget.destroy(), failedHandshakeDestroy)
}

console.log('flow embed host SDK tests passed')
