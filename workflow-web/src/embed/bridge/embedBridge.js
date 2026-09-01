import {
  generateSecureHandshakeNonce,
  isSecureHandshakeNonce
} from '../security/secureNonce.js'

export const EMBED_BRIDGE_PROTOCOL = 'flow-embed/1'

export const EMBED_BRIDGE_MESSAGE_TYPES = Object.freeze({
  READY: 'ready',
  INIT: 'init',
  INIT_ACK: 'init.ack',
  ACK: 'ack',
  INITIALIZED: 'initialized',
  RESIZE: 'resize',
  SELECTION_CHANGED: 'selection.changed',
  FORM_SAVED: 'form.saved',
  CLOSE_REQUESTED: 'close.requested',
  ERROR: 'error',
  SESSION_EXPIRED: 'session.expired',
  REFRESH: 'refresh',
  SET_THEME: 'set-theme',
  SET_LOCALE: 'set-locale',
  FOCUS: 'focus',
  DESTROY: 'destroy'
})

const DANGEROUS_KEYS = new Set(['__proto__', 'constructor', 'prototype'])
const FIELD_CODE_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,127}$/
const RECORD_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/
const ERROR_CODE_PATTERN = /^[A-Z][A-Z0-9_]{0,127}$/
const RECEIPT_ID_PATTERN = /^eor_[A-Za-z0-9_-]{16,64}$/
const VIEW_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9._:-]{0,99}$/
const CLIENT_MUTATION_ID_PATTERN = /^[\x20-\x7E]{1,128}$/
const ERROR_CATEGORIES = new Set([
  'SESSION', 'IDENTITY', 'ORIGIN', 'RESOURCE', 'TRANSIENT', 'UNKNOWN'
])
const V1_CAPABILITIES = new Set([
  'LIST_QUERY', 'SELECTION_RETURN', 'RECORD_VIEW', 'RECORD_CREATE', 'ACTION_EXECUTE'
])
const MAX_CLIENT_STRING_LENGTH = 100000
const MAX_CLIENT_VALUE_ITEMS = 100
const MAX_RECORD_ID_LENGTH = 128
const DEFAULT_OUTBOUND_TYPES = new Set([
  EMBED_BRIDGE_MESSAGE_TYPES.ACK,
  EMBED_BRIDGE_MESSAGE_TYPES.INITIALIZED,
  EMBED_BRIDGE_MESSAGE_TYPES.SELECTION_CHANGED,
  EMBED_BRIDGE_MESSAGE_TYPES.FORM_SAVED,
  EMBED_BRIDGE_MESSAGE_TYPES.CLOSE_REQUESTED,
  EMBED_BRIDGE_MESSAGE_TYPES.RESIZE,
  EMBED_BRIDGE_MESSAGE_TYPES.ERROR,
  EMBED_BRIDGE_MESSAGE_TYPES.SESSION_EXPIRED
])
const DEFAULT_INBOUND_TYPES = new Set([
  EMBED_BRIDGE_MESSAGE_TYPES.REFRESH,
  EMBED_BRIDGE_MESSAGE_TYPES.SET_THEME,
  EMBED_BRIDGE_MESSAGE_TYPES.SET_LOCALE,
  EMBED_BRIDGE_MESSAGE_TYPES.FOCUS,
  EMBED_BRIDGE_MESSAGE_TYPES.DESTROY
])

export class EmbedBridgeError extends Error {
  constructor(message, errorCode = 'EMBED_BRIDGE_INVALID') {
    super(message)
    this.name = 'EmbedBridgeError'
    this.errorCode = errorCode
  }
}

function isPlainRecord(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const prototype = Object.getPrototypeOf(value)
  return prototype === Object.prototype || prototype === null
}

function hasDangerousKeys(value, seen = new WeakSet()) {
  if (value === null || typeof value !== 'object') return false
  if (seen.has(value)) return true
  seen.add(value)
  if (Array.isArray(value)) {
    return value.some(item => hasDangerousKeys(item, seen))
  }
  if (!isPlainRecord(value) || Object.keys(value).some(key => DANGEROUS_KEYS.has(key))) {
    return true
  }
  return Object.values(value).some(item => hasDangerousKeys(item, seen))
}

function estimateMessageBytes(value) {
  try {
    const text = JSON.stringify(value)
    if (typeof TextEncoder !== 'undefined') return new TextEncoder().encode(text).byteLength
    return text.length
  } catch {
    return Number.POSITIVE_INFINITY
  }
}

function isNonce(value) {
  return isSecureHandshakeNonce(value)
}

function isMessageId(value) {
  return typeof value === 'string' && /^[A-Za-z0-9_-]{12,256}$/.test(value)
}

function isChannelId(value) {
  return typeof value === 'string' && /^[A-Za-z0-9._:-]{16,128}$/.test(value)
}

function isLaunchCode(value) {
  return typeof value === 'string' && /^[A-Za-z0-9_-]{43,86}$/.test(value)
}

function isLaunchId(value) {
  return typeof value === 'string' && /^lch_[A-Za-z0-9_-]{16,60}$/.test(value)
}

function isRequestId(value) {
  return value === null || value === undefined || isMessageId(value)
}

function validateCommandPayload(type, payload) {
  const source = payload === undefined || payload === null ? {} : payload
  if (!isPlainRecord(source) || hasDangerousKeys(source)) return false
  if ([
    EMBED_BRIDGE_MESSAGE_TYPES.REFRESH,
    EMBED_BRIDGE_MESSAGE_TYPES.FOCUS,
    EMBED_BRIDGE_MESSAGE_TYPES.DESTROY
  ].includes(type)) {
    return Object.keys(source).length === 0
  }
  if (type === EMBED_BRIDGE_MESSAGE_TYPES.SET_THEME) {
    return Object.keys(source).length === 1
      && ['light', 'dark', 'system'].includes(String(source.theme || '').toLowerCase())
  }
  if (type === EMBED_BRIDGE_MESSAGE_TYPES.SET_LOCALE) {
    return Object.keys(source).length === 1
      && /^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8}){0,2}$/.test(String(source.locale || ''))
  }
  return false
}

function hasExactShape(value, allowed, required = allowed) {
  const keys = Object.keys(value)
  return keys.every(key => allowed.includes(key))
    && required.every(key => Object.prototype.hasOwnProperty.call(value, key))
}

function isSafeText(value, maxLength, { allowNull = false } = {}) {
  if (allowNull && value === null) return true
  return typeof value === 'string' && value.length <= maxLength
    && !/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/.test(value)
}

function isRecordId(value) {
  return isSafeText(value, MAX_RECORD_ID_LENGTH) && RECORD_ID_PATTERN.test(value)
}

function isClientValue(value) {
  if (value === null || typeof value === 'boolean') return true
  if (typeof value === 'number') return Number.isFinite(value)
  if (typeof value === 'string') return value.length <= MAX_CLIENT_STRING_LENGTH
  return Array.isArray(value) && value.length <= MAX_CLIENT_VALUE_ITEMS
    && value.every(item => item === null
      || typeof item === 'boolean'
      || (typeof item === 'number' && Number.isFinite(item))
      || (typeof item === 'string' && item.length <= MAX_CLIENT_STRING_LENGTH))
}

function isReturnableValues(value) {
  return isPlainRecord(value) && Object.keys(value).length <= 100
    && Object.entries(value).every(([key, item]) => FIELD_CODE_PATTERN.test(key)
      && !DANGEROUS_KEYS.has(key) && isClientValue(item))
}

function isRecordProjection(value) {
  return isPlainRecord(value)
    && hasExactShape(value, ['id', 'values'])
    && isRecordId(value.id)
    && isReturnableValues(value.values)
}

function validateErrorPayload(payload) {
  const allowed = [
    'errorCode', 'message', 'traceId', 'recoverable',
    'category', 'phase', 'relaunchRequired'
  ]
  return hasExactShape(payload, allowed, ['errorCode', 'message', 'traceId', 'recoverable'])
    && ERROR_CODE_PATTERN.test(payload.errorCode)
    && isSafeText(payload.message, 512)
    && isSafeText(payload.traceId, 256, { allowNull: true })
    && typeof payload.recoverable === 'boolean'
    && (payload.category === undefined || ERROR_CATEGORIES.has(payload.category))
    && (payload.phase === undefined || isSafeText(payload.phase, 64))
    && (payload.relaunchRequired === undefined
      || typeof payload.relaunchRequired === 'boolean')
}

/** 子页面也对出站事件做精确 Schema 校验，阻止投影漂移在到达 SDK 前发生。 */
function validateEventPayload(type, payload) {
  if (!isPlainRecord(payload) || hasDangerousKeys(payload)) return false
  if (type === EMBED_BRIDGE_MESSAGE_TYPES.ACK) {
    return hasExactShape(payload, ['command']) && DEFAULT_INBOUND_TYPES.has(payload.command)
  }
  if (type === EMBED_BRIDGE_MESSAGE_TYPES.INITIALIZED) {
    return hasExactShape(payload, ['viewKey', 'surfaceType', 'capabilities'])
      && VIEW_KEY_PATTERN.test(payload.viewKey)
      && ['LIST', 'FORM'].includes(payload.surfaceType)
      && Array.isArray(payload.capabilities) && payload.capabilities.length <= 32
      && payload.capabilities.every(value => V1_CAPABILITIES.has(value))
  }
  if (type === EMBED_BRIDGE_MESSAGE_TYPES.RESIZE) {
    return hasExactShape(payload, ['height'])
      && Number.isFinite(payload.height) && payload.height >= 0 && payload.height <= 100000
  }
  if (type === EMBED_BRIDGE_MESSAGE_TYPES.SELECTION_CHANGED) {
    return hasExactShape(payload, ['selection'])
      && Array.isArray(payload.selection) && payload.selection.length <= 1000
      && payload.selection.every(isRecordProjection)
  }
  if (type === EMBED_BRIDGE_MESSAGE_TYPES.FORM_SAVED) {
    return hasExactShape(payload, ['receiptId', 'record', 'clientMutationId'])
      && RECEIPT_ID_PATTERN.test(payload.receiptId)
      && isRecordProjection(payload.record)
      && CLIENT_MUTATION_ID_PATTERN.test(payload.clientMutationId)
  }
  if (type === EMBED_BRIDGE_MESSAGE_TYPES.CLOSE_REQUESTED) {
    return hasExactShape(payload, ['reason'])
      && isSafeText(payload.reason, 128)
  }
  if (type === EMBED_BRIDGE_MESSAGE_TYPES.ERROR) return validateErrorPayload(payload)
  if (type === EMBED_BRIDGE_MESSAGE_TYPES.SESSION_EXPIRED) {
    return hasExactShape(payload, ['reason', 'relaunchRequired'])
      && ERROR_CODE_PATTERN.test(payload.reason)
      && payload.relaunchRequired === true
  }
  return false
}

/** 生成不可预测 nonce；没有 Web Crypto 时拒绝启动，而不是降级为 Math.random。 */
export function createSecureNonce(cryptoRef = globalThis.crypto) {
  try {
    return generateSecureHandshakeNonce(cryptoRef)
  } catch {
    throw new EmbedBridgeError(
      '当前浏览器不支持安全随机数',
      'EMBED_BRIDGE_CRYPTO_UNAVAILABLE'
    )
  }
}

/**
 * 只接受规范的 origin，而不是可携带 path、query 或通配符的 URL。
 * iframe 的 targetOrigin 与接收消息的 event.origin 均使用这个值精确比较。
 */
export function normalizeTrustedOrigin(origin, { allowInsecure = false } = {}) {
  const raw = String(origin || '').trim()
  let parsed
  try {
    parsed = new URL(raw)
  } catch {
    throw new EmbedBridgeError('父页面 origin 无效', 'EMBED_BRIDGE_ORIGIN_INVALID')
  }

  if (parsed.origin === 'null' || parsed.username || parsed.password
    || (parsed.protocol !== 'https:' && !(allowInsecure && parsed.protocol === 'http:'))
    || (parsed.pathname && parsed.pathname !== '/') || parsed.search || parsed.hash) {
    throw new EmbedBridgeError('父页面 origin 不受信任', 'EMBED_BRIDGE_ORIGIN_INVALID')
  }
  return parsed.origin
}

function normalizeAllowedTypes(types, fallback) {
  const source = types === undefined ? fallback : types
  const result = new Set()
  for (const type of source || []) {
    const value = String(type || '')
    if (fallback.has(value)) result.add(value)
  }
  return result
}

/**
 * iframe 子页面 bridge。窗口消息仅用于建立 MessagePort；连接后所有业务
 * 消息只能走已验证的 port，以缩小其他页面伪造 postMessage 的攻击面。
 */
export class EmbedBridge {
  constructor({
    windowRef = globalThis.window,
    expectedParentOrigin,
    launchId,
    channelId,
    protocol = EMBED_BRIDGE_PROTOCOL,
    nonceFactory = createSecureNonce,
    maxMessageBytes = 256 * 1024,
    maxSeenMessageIds = 256,
    handshakeTimeoutMs = 10000,
    setTimeoutImpl = globalThis.setTimeout,
    clearTimeoutImpl = globalThis.clearTimeout,
    allowedInboundTypes,
    allowedOutboundTypes,
    onInit,
    onConnected,
    onMessage,
    onViolation,
    onError,
    allowInsecureParentOrigin = false
  } = {}) {
    if (!windowRef || typeof windowRef.addEventListener !== 'function') {
      throw new EmbedBridgeError('Embed bridge 缺少 window 上下文', 'EMBED_BRIDGE_WINDOW_UNAVAILABLE')
    }
    if (!isChannelId(String(channelId || ''))) {
      throw new EmbedBridgeError('Embed channel 无效', 'EMBED_BRIDGE_CHANNEL_INVALID')
    }
    if (!isLaunchId(String(launchId || ''))) {
      throw new EmbedBridgeError('Embed launch 无效', 'EMBED_BRIDGE_LAUNCH_INVALID')
    }
    if (typeof nonceFactory !== 'function') {
      throw new EmbedBridgeError('Embed nonce 生成器无效', 'EMBED_BRIDGE_NONCE_INVALID')
    }

    this.windowRef = windowRef
    this.expectedParentOrigin = normalizeTrustedOrigin(expectedParentOrigin, {
      allowInsecure: allowInsecureParentOrigin
    })
    this.launchId = String(launchId)
    this.channelId = String(channelId)
    this.protocol = String(protocol || EMBED_BRIDGE_PROTOCOL)
    this.nonceFactory = nonceFactory
    this.childNonce = this.createNonce()
    this.maxMessageBytes = Math.max(1024, Math.min(Number(maxMessageBytes) || 16384, 262144))
    this.maxSeenMessageIds = Math.max(16, Math.min(Number(maxSeenMessageIds) || 256, 2048))
    this.handshakeTimeoutMs = Math.max(0, Number(handshakeTimeoutMs) || 0)
    // Window timer 不能以 EmbedBridge 实例作为接收者调用；真实 Chrome 会抛出
    // Illegal invocation。闭包同时让默认实现与测试注入实现保持普通函数语义。
    this.setTimeoutImpl = typeof setTimeoutImpl === 'function'
      ? (...args) => setTimeoutImpl(...args)
      : undefined
    this.clearTimeoutImpl = typeof clearTimeoutImpl === 'function'
      ? (...args) => clearTimeoutImpl(...args)
      : undefined
    this.allowedInboundTypes = normalizeAllowedTypes(allowedInboundTypes, DEFAULT_INBOUND_TYPES)
    this.allowedOutboundTypes = normalizeAllowedTypes(allowedOutboundTypes, DEFAULT_OUTBOUND_TYPES)
    this.onInit = onInit
    this.onConnected = onConnected
    this.onMessage = onMessage
    this.onViolation = onViolation
    this.onError = onError
    this.state = 'idle'
    this.parentWindow = null
    this.parentNonce = ''
    this.port = null
    this.timeoutId = undefined
    this.seenMessageIds = new Set()
    this.handleWindowMessage = this.handleWindowMessage.bind(this)
    this.handlePortMessage = this.handlePortMessage.bind(this)
  }

  createNonce() {
    const nonce = String(this.nonceFactory() || '')
    if (!isNonce(nonce)) {
      throw new EmbedBridgeError('Embed nonce 无效', 'EMBED_BRIDGE_NONCE_INVALID')
    }
    return nonce
  }

  getState() {
    return this.state
  }

  start() {
    if (this.state !== 'idle') {
      throw new EmbedBridgeError('Embed bridge 已启动', 'EMBED_BRIDGE_STATE_INVALID')
    }
    const parentWindow = this.windowRef.parent
    if (!parentWindow || parentWindow === this.windowRef
      || typeof parentWindow.postMessage !== 'function') {
      throw new EmbedBridgeError('Embed 页面必须在 iframe 中打开', 'EMBED_BRIDGE_PARENT_UNAVAILABLE')
    }

    this.parentWindow = parentWindow
    this.state = 'waiting'
    this.windowRef.addEventListener('message', this.handleWindowMessage)
    try {
      parentWindow.postMessage({
        protocol: this.protocol,
        type: EMBED_BRIDGE_MESSAGE_TYPES.READY,
        launchId: this.launchId,
        channelId: this.channelId,
        childNonce: this.childNonce,
        supportedVersions: [this.protocol]
      }, this.expectedParentOrigin)
    } catch (cause) {
      this.fail(new EmbedBridgeError('无法通知 Embed 父页面', 'EMBED_BRIDGE_READY_FAILED'), cause)
      throw new EmbedBridgeError('无法通知 Embed 父页面', 'EMBED_BRIDGE_READY_FAILED')
    }

    if (this.handshakeTimeoutMs > 0 && typeof this.setTimeoutImpl === 'function') {
      this.timeoutId = this.setTimeoutImpl(() => {
        if (this.state === 'waiting') {
          this.fail(new EmbedBridgeError('Embed 握手超时', 'EMBED_BRIDGE_HANDSHAKE_TIMEOUT'))
        }
      }, this.handshakeTimeoutMs)
    }
    return this.childNonce
  }

  /** 仅处理来自已配置父窗口、且 origin 精确匹配的 init 消息。 */
  handleWindowMessage(event) {
    if (this.state !== 'waiting') return false
    if (event?.source !== this.parentWindow || event?.origin !== this.expectedParentOrigin) {
      return this.violation('Embed init 来源不受信任', 'EMBED_BRIDGE_INIT_SOURCE_INVALID')
    }

    const data = event?.data
    if (!this.validateBaseMessage(data)
      || !hasExactShape(data, [
        'protocol', 'type', 'launchId', 'channelId',
        'childNonce', 'parentNonce', 'launchCode'
      ])
      || data.type !== EMBED_BRIDGE_MESSAGE_TYPES.INIT) {
      return this.violation('Embed init 消息无效', 'EMBED_BRIDGE_INIT_INVALID')
    }
    if (data.launchId !== this.launchId || data.childNonce !== this.childNonce
      || !isNonce(data.parentNonce) || !isLaunchCode(data.launchCode)) {
      return this.violation('Embed init nonce 无效', 'EMBED_BRIDGE_INIT_NONCE_INVALID')
    }

    const ports = Array.isArray(event?.ports) ? event.ports : []
    const port = ports.length === 1 ? ports[0] : null
    if (!port || typeof port.postMessage !== 'function') {
      return this.violation('Embed init 未携带 MessagePort', 'EMBED_BRIDGE_PORT_INVALID')
    }

    this.port = port
    this.parentNonce = data.parentNonce
    this.bindPort(port)
    this.clearHandshakeTimeout()
    this.state = 'connected'
    try {
      port.postMessage({
        protocol: this.protocol,
        type: EMBED_BRIDGE_MESSAGE_TYPES.INIT_ACK,
        launchId: this.launchId,
        channelId: this.channelId,
        childNonce: this.childNonce,
        parentNonce: data.parentNonce
      })
    } catch (cause) {
      this.fail(new EmbedBridgeError('Embed init 确认失败', 'EMBED_BRIDGE_ACK_FAILED'), cause)
      return false
    }

    this.callHandler(this.onConnected, {
      parentNonce: data.parentNonce
    })
    this.callHandler(this.onInit, {
      launchCode: data.launchCode,
      parentNonce: data.parentNonce,
      childNonce: this.childNonce,
      channelId: this.channelId
    })
    return true
  }

  /** 连接后不再相信 window.postMessage，port 成为唯一的父页面通信边界。 */
  handlePortMessage(event) {
    if (this.state !== 'connected') return false
    const data = event?.data
    if (!this.validateBaseMessage(data) || !isMessageId(data.messageId)
      || !isMessageId(data.requestId)
      || data.childNonce !== this.childNonce
      || data.parentNonce !== this.parentNonce) {
      return this.violation('Embed port 消息无效', 'EMBED_BRIDGE_PORT_MESSAGE_INVALID')
    }
    if (this.seenMessageIds.has(data.messageId)) {
      return this.violation('Embed port 消息重复', 'EMBED_BRIDGE_PORT_REPLAYED')
    }

    this.seenMessageIds.add(data.messageId)
    if (this.seenMessageIds.size > this.maxSeenMessageIds) {
      const firstMessageId = this.seenMessageIds.values().next().value
      this.seenMessageIds.delete(firstMessageId)
    }
    if (!this.allowedInboundTypes.has(data.type)) {
      if (data.requestId) {
        try {
          this.send(EMBED_BRIDGE_MESSAGE_TYPES.ERROR, {
            errorCode: 'UNSUPPORTED_MESSAGE_TYPE',
            message: 'Embed 命令类型不受支持',
            traceId: null,
            recoverable: false
          }, { requestId: data.requestId })
        } catch {
          // 未知命令仍应保持 Fail Closed；错误响应失败不执行该命令。
        }
      }
      return this.violation('Embed port 消息类型未授权', 'EMBED_BRIDGE_PORT_TYPE_FORBIDDEN')
    }
    if (!validateCommandPayload(data.type, data.payload)) {
      return this.violation('Embed port 命令内容无效', 'EMBED_BRIDGE_PORT_PAYLOAD_INVALID')
    }
    this.callHandler(this.onMessage, {
      type: data.type,
      payload: data.payload,
      messageId: data.messageId,
      requestId: data.requestId ?? null
    })
    return true
  }

  /** 向父页面发送白名单事件；所有事件均使用已认证的 MessagePort。 */
  send(type, payload = {}, { requestId = null } = {}) {
    if (this.state !== 'connected' || !this.port) {
      throw new EmbedBridgeError('Embed bridge 尚未连接', 'EMBED_BRIDGE_NOT_CONNECTED')
    }
    const normalizedType = String(type || '')
    if (!this.allowedOutboundTypes.has(normalizedType)) {
      throw new EmbedBridgeError('Embed 事件类型未授权', 'EMBED_BRIDGE_OUTBOUND_TYPE_FORBIDDEN')
    }

    const message = {
      protocol: this.protocol,
      type: normalizedType,
      launchId: this.launchId,
      channelId: this.channelId,
      messageId: this.createNonce(),
      requestId,
      timestamp: new Date().toISOString(),
      childNonce: this.childNonce,
      parentNonce: this.parentNonce,
      payload
    }
    if (!this.validateBaseMessage(message) || !isMessageId(message.messageId)
      || !isRequestId(requestId) || !validateEventPayload(normalizedType, payload)) {
      throw new EmbedBridgeError('Embed 事件无效', 'EMBED_BRIDGE_OUTBOUND_INVALID')
    }
    this.port.postMessage(message)
    return message.messageId
  }

  validateBaseMessage(data) {
    if (!isPlainRecord(data) || hasDangerousKeys(data)
      || estimateMessageBytes(data) > this.maxMessageBytes) {
      return false
    }
    return data.protocol === this.protocol
      && data.launchId === this.launchId
      && data.channelId === this.channelId
      && typeof data.type === 'string'
      && /^[a-z][a-z.-]{1,63}$/.test(data.type)
  }

  bindPort(port) {
    if (typeof port.addEventListener === 'function') {
      port.addEventListener('message', this.handlePortMessage)
      port.start?.()
      return
    }
    port.onmessage = this.handlePortMessage
  }

  unbindPort(port) {
    if (!port) return
    if (typeof port.removeEventListener === 'function') {
      port.removeEventListener('message', this.handlePortMessage)
    } else if (port.onmessage === this.handlePortMessage) {
      port.onmessage = null
    }
  }

  clearHandshakeTimeout() {
    if (this.timeoutId !== undefined && typeof this.clearTimeoutImpl === 'function') {
      this.clearTimeoutImpl(this.timeoutId)
    }
    this.timeoutId = undefined
  }

  violation(message, errorCode) {
    const error = new EmbedBridgeError(message, errorCode)
    this.callHandler(this.onViolation, error)
    return false
  }

  callHandler(handler, value) {
    if (typeof handler !== 'function') return
    try {
      const result = handler(value)
      if (result?.catch) {
        result.catch(error => this.fail(error))
      }
    } catch (error) {
      this.fail(error)
    }
  }

  fail(error, cause) {
    if (this.state === 'destroyed' || this.state === 'failed') return
    this.clearHandshakeTimeout()
    this.windowRef.removeEventListener?.('message', this.handleWindowMessage)
    this.unbindPort(this.port)
    this.port?.close?.()
    this.port = null
    this.parentNonce = ''
    this.state = 'failed'
    const normalizedError = error instanceof Error
      ? error
      : new EmbedBridgeError('Embed bridge 失败', 'EMBED_BRIDGE_FAILED')
    if (cause && !normalizedError.cause) normalizedError.cause = cause
    try {
      this.onError?.(normalizedError)
    } catch {
      // 错误上报回调不能恢复已失败的安全通道。
    }
  }

  destroy() {
    if (this.state === 'destroyed') return
    this.clearHandshakeTimeout()
    this.windowRef.removeEventListener?.('message', this.handleWindowMessage)
    this.unbindPort(this.port)
    this.port?.close?.()
    this.port = null
    this.parentWindow = null
    this.parentNonce = ''
    this.seenMessageIds.clear()
    this.state = 'destroyed'
  }
}

export function createEmbedBridge(options) {
  return new EmbedBridge(options)
}
