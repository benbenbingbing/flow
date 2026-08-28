import assert from 'node:assert/strict'
import {
  EMBED_BRIDGE_MESSAGE_TYPES,
  EMBED_BRIDGE_PROTOCOL,
  EmbedBridgeError,
  createSecureNonce,
  createEmbedBridge,
  normalizeTrustedOrigin
} from '../src/embed/bridge/embedBridge.js'

const TEST_LAUNCH_CODE = 'A'.repeat(43)
const PARENT_NONCE = Buffer.alloc(32, 201).toString('base64url')
const testNonce = value => Buffer.alloc(32, value).toString('base64url')

let secureRandomCalled = false
const generatedNonce = createSecureNonce({
  randomUUID() { throw new Error('randomUUID 不得被调用') },
  getRandomValues(bytes) {
    assert.equal(bytes.length, 32)
    secureRandomCalled = true
    for (let index = 0; index < bytes.length; index += 1) bytes[index] = index
    return bytes
  }
})
assert.equal(secureRandomCalled, true)
assert.equal(generatedNonce.length, 43)
assert.equal(Buffer.from(generatedNonce, 'base64url').length, 32)
assert.throws(
  () => createSecureNonce({ randomUUID() { return 'must-not-fallback' } }),
  error => error.errorCode === 'EMBED_BRIDGE_CRYPTO_UNAVAILABLE'
)

function createFakePort() {
  const messages = []
  const listeners = new Set()
  return {
    messages,
    closed: false,
    started: false,
    postMessage(message) {
      messages.push(message)
    },
    addEventListener(type, listener) {
      if (type === 'message') listeners.add(listener)
    },
    removeEventListener(type, listener) {
      if (type === 'message') listeners.delete(listener)
    },
    start() {
      this.started = true
    },
    close() {
      this.closed = true
    },
    emit(data) {
      for (const listener of listeners) listener({ data })
    }
  }
}

const windowListeners = new Set()
const parentMessages = []
const parentWindow = {
  postMessage(message, targetOrigin) {
    parentMessages.push({ message, targetOrigin })
  }
}
const windowRef = {
  parent: parentWindow,
  addEventListener(type, listener) {
    if (type === 'message') windowListeners.add(listener)
  },
  removeEventListener(type, listener) {
    if (type === 'message') windowListeners.delete(listener)
  }
}

let nonceSequence = 0
const violations = []
const initMessages = []
const commands = []
const bridge = createEmbedBridge({
  windowRef,
  expectedParentOrigin: 'https://portal.example.com',
  launchId: 'lch_0123456789abcdef',
  channelId: 'tenant:channel_001',
  nonceFactory: () => testNonce(++nonceSequence),
  maxMessageBytes: 64 * 1024,
  handshakeTimeoutMs: 0,
  onInit: value => initMessages.push(value),
  onMessage: value => commands.push(value),
  onViolation: error => violations.push(error.errorCode)
})

const childNonce = bridge.start()
assert.equal(Buffer.from(childNonce, 'base64url').length, 32)
assert.equal(bridge.getState(), 'waiting')
assert.equal(parentMessages.length, 1)
assert.deepEqual(parentMessages[0], {
  targetOrigin: 'https://portal.example.com',
  message: {
    protocol: EMBED_BRIDGE_PROTOCOL,
    type: 'ready',
    launchId: 'lch_0123456789abcdef',
    channelId: 'tenant:channel_001',
    childNonce,
    supportedVersions: [EMBED_BRIDGE_PROTOCOL]
  }
})

const port = createFakePort()
const validInit = {
  protocol: EMBED_BRIDGE_PROTOCOL,
  type: EMBED_BRIDGE_MESSAGE_TYPES.INIT,
  launchId: 'lch_0123456789abcdef',
  channelId: 'tenant:channel_001',
  childNonce,
  parentNonce: PARENT_NONCE,
  launchCode: TEST_LAUNCH_CODE
}
for (const listener of windowListeners) {
  listener({
    source: {},
    origin: 'https://evil.example',
    data: validInit,
    ports: [port]
  })
}
assert.equal(bridge.getState(), 'waiting')
assert.equal(violations.at(-1), 'EMBED_BRIDGE_INIT_SOURCE_INVALID')

for (const listener of windowListeners) {
  listener({
    source: parentWindow,
    origin: 'https://portal.example.com',
    data: { ...validInit, extra: 'forbidden' },
    ports: [port]
  })
}
assert.equal(bridge.getState(), 'waiting')
assert.equal(violations.at(-1), 'EMBED_BRIDGE_INIT_INVALID')

for (const listener of windowListeners) {
  listener({
    source: parentWindow,
    origin: 'https://portal.example.com',
    data: { ...validInit, launchCode: 'A'.repeat(42) },
    ports: [port]
  })
}
assert.equal(bridge.getState(), 'waiting')
assert.equal(violations.at(-1), 'EMBED_BRIDGE_INIT_NONCE_INVALID')

for (const listener of windowListeners) {
  listener({
    source: parentWindow,
    origin: 'https://portal.example.com',
    data: validInit,
    ports: [port]
  })
}
assert.equal(bridge.getState(), 'connected')
assert.equal(port.started, true)
assert.equal(initMessages.length, 1)
assert.equal(initMessages[0].launchCode, TEST_LAUNCH_CODE)
assert.equal(initMessages[0].childNonce, childNonce)
assert.equal(Buffer.from(validInit.parentNonce, 'base64url').length, 32)
assert.equal(port.messages[0].type, EMBED_BRIDGE_MESSAGE_TYPES.INIT_ACK)
assert.equal(port.messages[0].parentNonce, PARENT_NONCE)

const refresh = {
  protocol: EMBED_BRIDGE_PROTOCOL,
  type: EMBED_BRIDGE_MESSAGE_TYPES.REFRESH,
  launchId: 'lch_0123456789abcdef',
  channelId: 'tenant:channel_001',
  messageId: 'message_000000000001',
  requestId: 'request_00000000001',
  childNonce,
  parentNonce: PARENT_NONCE,
  payload: {}
}
port.emit({ ...refresh, parentNonce: 'wrong_parent_nonce' })
assert.equal(commands.length, 0)
assert.equal(violations.at(-1), 'EMBED_BRIDGE_PORT_MESSAGE_INVALID')

port.emit(refresh)
assert.equal(commands.length, 1)
assert.equal(commands[0].type, EMBED_BRIDGE_MESSAGE_TYPES.REFRESH)
assert.equal(commands[0].requestId, 'request_00000000001')
port.emit(refresh)
assert.equal(commands.length, 1, '重复 messageId 必须被拒绝')
assert.equal(violations.at(-1), 'EMBED_BRIDGE_PORT_REPLAYED')

port.emit({
  ...refresh,
  type: 'execute-arbitrary-action',
  messageId: 'message_000000000002',
  requestId: 'request_00000000002'
})
assert.equal(commands.length, 1)
assert.equal(violations.at(-1), 'EMBED_BRIDGE_PORT_TYPE_FORBIDDEN')
assert.equal(port.messages.at(-1).type, EMBED_BRIDGE_MESSAGE_TYPES.ERROR)
assert.equal(port.messages.at(-1).requestId, 'request_00000000002')
assert.equal(port.messages.at(-1).payload.errorCode, 'UNSUPPORTED_MESSAGE_TYPE')

const sentMessageId = bridge.send(
  EMBED_BRIDGE_MESSAGE_TYPES.SELECTION_CHANGED,
  { selection: [{ id: 'record-1', values: { code: 'WO-1' } }] }
)
const selectionMessage = port.messages.at(-1)
assert.equal(selectionMessage.messageId, sentMessageId)
assert.equal(selectionMessage.childNonce, childNonce)
assert.equal(selectionMessage.parentNonce, PARENT_NONCE)
assert.equal(selectionMessage.type, 'selection.changed')
assert.equal(selectionMessage.protocol, 'flow-embed/1')

const maximumRecordId = `A${'b'.repeat(127)}`
assert.doesNotThrow(() => bridge.send(
  EMBED_BRIDGE_MESSAGE_TYPES.SELECTION_CHANGED,
  {
    selection: [{
      id: maximumRecordId,
      values: {
        description: 'x'.repeat(2048),
        tags: Array.from({ length: 100 }, (_, index) => `tag-${index}`)
      }
    }]
  }
))
for (const invalidSelection of [
  [{ id: 'record-1', values: { description: 'x'.repeat(2049) } }],
  [{ id: 'record-1', values: { tags: Array(101).fill('tag') } }],
  [{ id: 'A'.repeat(129), values: {} }],
  [{ id: 'record/1', values: {} }]
]) {
  assert.throws(
    () => bridge.send(
      EMBED_BRIDGE_MESSAGE_TYPES.SELECTION_CHANGED,
      { selection: invalidSelection }
    ),
    error => error.errorCode === 'EMBED_BRIDGE_OUTBOUND_INVALID'
  )
}

assert.doesNotThrow(() => bridge.send(
  EMBED_BRIDGE_MESSAGE_TYPES.ACK,
  { command: 'refresh' },
  { requestId: 'request_00000000003' }
))

assert.doesNotThrow(() => bridge.send(
  EMBED_BRIDGE_MESSAGE_TYPES.INITIALIZED,
  {
    viewKey: 'supplier-work-orders',
    surfaceType: 'LIST',
    capabilities: ['LIST_QUERY', 'SELECTION_RETURN', 'RECORD_VIEW', 'RECORD_CREATE']
  }
))
assert.throws(
  () => bridge.send(EMBED_BRIDGE_MESSAGE_TYPES.INITIALIZED, {
    viewKey: 'supplier-work-orders',
    surfaceType: 'LIST',
    capabilities: ['LIST_QUERY', 'INTERNAL_ADMIN']
  }),
  error => error.errorCode === 'EMBED_BRIDGE_OUTBOUND_INVALID'
)

assert.doesNotThrow(() => bridge.send(
  EMBED_BRIDGE_MESSAGE_TYPES.FORM_SAVED,
  {
    receiptId: 'eor_0123456789abcdef',
    record: { id: 'record-1', values: { code: 'WO-1' } },
    clientMutationId: 'cm_0123456789abcdef'
  }
))
assert.throws(
  () => bridge.send(EMBED_BRIDGE_MESSAGE_TYPES.FORM_SAVED, {
    receiptId: 'eor_0123456789abcdef',
    record: { id: 'record-1', values: { code: 'WO-1' } },
    clientMutationId: 'cm_0123456789abcdef',
    effects: [{ type: 'redirect', url: 'https://evil.example' }]
  }),
  error => error.errorCode === 'EMBED_BRIDGE_OUTBOUND_INVALID'
)

assert.throws(
  () => bridge.send(EMBED_BRIDGE_MESSAGE_TYPES.ERROR, { message: 'x'.repeat(2000) }),
  error => error instanceof EmbedBridgeError
    && error.errorCode === 'EMBED_BRIDGE_OUTBOUND_INVALID'
)
assert.throws(
  () => bridge.send('execute-arbitrary-action', {}),
  error => error.errorCode === 'EMBED_BRIDGE_OUTBOUND_TYPE_FORBIDDEN'
)

bridge.destroy()
assert.equal(bridge.getState(), 'destroyed')
assert.equal(port.closed, true)
assert.equal(windowListeners.size, 0)

// 原生 Window timer 作为 bridge 实例方法调用会在浏览器抛 Illegal invocation。
let strictTimerCleared = false
const strictTimerBridge = createEmbedBridge({
  windowRef,
  expectedParentOrigin: 'https://portal.example.com',
  launchId: 'lch_0123456789abcdef',
  channelId: 'tenant:channel_001',
  nonceFactory: () => testNonce(10),
  handshakeTimeoutMs: 1,
  setTimeoutImpl(callback, delay) {
    assert.equal(this, undefined)
    assert.equal(typeof callback, 'function')
    assert.equal(delay, 1)
    return 41
  },
  clearTimeoutImpl(timerId) {
    assert.equal(this, undefined)
    assert.equal(timerId, 41)
    strictTimerCleared = true
  }
})
strictTimerBridge.start()
strictTimerBridge.destroy()
assert.equal(strictTimerCleared, true)

assert.equal(normalizeTrustedOrigin('https://EXAMPLE.com:443'), 'https://example.com')
assert.throws(() => createEmbedBridge({
  windowRef,
  expectedParentOrigin: 'https://portal.example.com',
  launchId: 'lch_too_short',
  channelId: 'tenant:channel_001',
  nonceFactory: () => testNonce(11)
}), error => error.errorCode === 'EMBED_BRIDGE_LAUNCH_INVALID')
assert.throws(() => createEmbedBridge({
  windowRef,
  expectedParentOrigin: 'https://portal.example.com',
  launchId: 'lch_0123456789abcdef',
  channelId: 'short-channel',
  nonceFactory: () => testNonce(12)
}), error => error.errorCode === 'EMBED_BRIDGE_CHANNEL_INVALID')
for (const origin of [
  '*',
  'https://example.com/path',
  'https://example.com?query=1',
  'http://example.com',
  'https://user:pass@example.com'
]) {
  assert.throws(() => normalizeTrustedOrigin(origin), EmbedBridgeError)
}

console.log('embed bridge tests passed')
