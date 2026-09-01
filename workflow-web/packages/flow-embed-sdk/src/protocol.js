export const FLOW_EMBED_PROTOCOL = 'flow-embed/1'

export const FLOW_EMBED_COMMANDS = Object.freeze({
  REFRESH: 'refresh',
  SET_THEME: 'set-theme',
  SET_LOCALE: 'set-locale',
  FOCUS: 'focus',
  DESTROY: 'destroy'
})

export const FLOW_EMBED_EVENTS = Object.freeze({
  ACK: 'ack',
  INITIALIZED: 'initialized',
  RESIZE: 'resize',
  SELECTION_CHANGED: 'selection.changed',
  ACTION_STARTED: 'action.started',
  ACTION_COMPLETED: 'action.completed',
  FORM_SAVED: 'form.saved',
  NAVIGATION_REQUEST: 'navigation.request',
  CLOSE_REQUESTED: 'close.requested',
  ERROR: 'error',
  SESSION_EXPIRED: 'session.expired'
})

/** OpenAPI x-flow-v1-boundary 的封闭能力集合；宿主可据此做静态能力判断。 */
export const FLOW_EMBED_CAPABILITIES = Object.freeze([
  'LIST_QUERY',
  'SELECTION_RETURN',
  'RECORD_VIEW',
  'RECORD_CREATE',
  'ACTION_EXECUTE'
])

export const MAX_MESSAGE_BYTES = 256 * 1024

/** selection.changed 的运行时约束；生成器会逐项核对 OpenAPI，防止静态类型掩盖边界漂移。 */
export const FLOW_EMBED_SELECTION_CONSTRAINTS = Object.freeze({
  scalarStringMaxLength: 100000,
  clientArrayMaxItems: 100,
  recordIdMaxLength: 128,
  recordIdPattern: '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
})

export class FlowEmbedError extends Error {
  constructor(message, errorCode = 'FLOW_EMBED_INVALID', cause) {
    super(message, cause ? { cause } : undefined)
    this.name = 'FlowEmbedError'
    this.errorCode = errorCode
  }
}

const DANGEROUS_KEYS = new Set(['__proto__', 'constructor', 'prototype'])
const COMMAND_TYPES = new Set(Object.values(FLOW_EMBED_COMMANDS))
const EVENT_TYPES = new Set(Object.values(FLOW_EMBED_EVENTS))
const FIELD_CODE_PATTERN = /^[A-Za-z][A-Za-z0-9_]{0,127}$/
const RECORD_ID_PATTERN = new RegExp(FLOW_EMBED_SELECTION_CONSTRAINTS.recordIdPattern)
const ACTION_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9_.-]{0,127}$/
const ERROR_CODE_PATTERN = /^[A-Z][A-Z0-9_]{0,127}$/
const RECEIPT_ID_PATTERN = /^eor_[A-Za-z0-9_-]{16,64}$/
const VIEW_KEY_PATTERN = /^[A-Za-z][A-Za-z0-9._:-]{0,99}$/
const CLIENT_MUTATION_ID_PATTERN = /^[\x20-\x7E]{1,128}$/
const NAVIGATION_TARGETS = new Set(['LIST', 'CREATE', 'VIEW', 'BACK'])
const ERROR_CATEGORIES = new Set([
  'SESSION', 'IDENTITY', 'ORIGIN', 'RESOURCE', 'TRANSIENT', 'UNKNOWN'
])
const V1_CAPABILITIES = new Set(FLOW_EMBED_CAPABILITIES)

export function isPlainRecord(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const prototype = Object.getPrototypeOf(value)
  return prototype === Object.prototype || prototype === null
}

/** 拒绝危险键、非普通对象和循环引用，避免把不可信 payload 合并进宿主对象时污染原型。 */
export function hasDangerousKeys(value, seen = new WeakSet()) {
  if (value === null || typeof value !== 'object') return false
  if (seen.has(value)) return true
  seen.add(value)
  if (Array.isArray(value)) return value.some(item => hasDangerousKeys(item, seen))
  if (!isPlainRecord(value) || Object.keys(value).some(key => DANGEROUS_KEYS.has(key))) return true
  return Object.values(value).some(item => hasDangerousKeys(item, seen))
}

export function estimateMessageBytes(value) {
  try {
    const text = JSON.stringify(value)
    return typeof TextEncoder !== 'undefined' ? new TextEncoder().encode(text).byteLength : text.length
  } catch {
    return Number.POSITIVE_INFINITY
  }
}

export function isNonce(value) {
  if (typeof value !== 'string' || !/^[A-Za-z0-9_-]{43,128}$/.test(value)
    || value.length % 4 === 1) return false
  return Math.floor(value.length * 6 / 8) >= 32
}

export function isMessageId(value) {
  return typeof value === 'string' && /^[A-Za-z0-9_-]{12,256}$/.test(value)
}

export function isLaunchId(value) {
  return typeof value === 'string' && /^lch_[A-Za-z0-9_-]{16,60}$/.test(value)
}

export function isChannelId(value) {
  return typeof value === 'string' && /^[A-Za-z0-9._:-]{16,128}$/.test(value)
}

export function isLaunchCode(value) {
  return typeof value === 'string' && /^[A-Za-z0-9_-]{43,86}$/.test(value)
}

export function createSecureNonce(cryptoRef = globalThis.crypto) {
  if (typeof cryptoRef?.getRandomValues !== 'function') {
    throw new FlowEmbedError('当前浏览器不支持安全随机数', 'FLOW_EMBED_CRYPTO_UNAVAILABLE')
  }
  const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_'
  const bytes = new Uint8Array(32)
  try {
    cryptoRef.getRandomValues(bytes)
  } catch (cause) {
    throw new FlowEmbedError(
      '无法生成安全随机数',
      'FLOW_EMBED_CRYPTO_UNAVAILABLE',
      cause
    )
  }
  let nonce = ''
  for (let index = 0; index < bytes.length; index += 3) {
    const first = bytes[index]
    const hasSecond = index + 1 < bytes.length
    const hasThird = index + 2 < bytes.length
    const second = hasSecond ? bytes[index + 1] : 0
    const third = hasThird ? bytes[index + 2] : 0
    nonce += alphabet[first >>> 2]
    nonce += alphabet[((first & 0x03) << 4) | (second >>> 4)]
    if (hasSecond) nonce += alphabet[((second & 0x0f) << 2) | (third >>> 6)]
    if (hasThird) nonce += alphabet[third & 0x3f]
  }
  if (nonce.length !== 43 || !isNonce(nonce)) {
    throw new FlowEmbedError('无法生成安全随机数', 'FLOW_EMBED_CRYPTO_UNAVAILABLE')
  }
  return nonce
}

/** targetOrigin 只能是一个规范 HTTPS Origin，不能携带 path、凭据、query 或 fragment。 */
export function normalizeTargetOrigin(value) {
  let url
  try {
    url = new URL(String(value || '').trim())
  } catch {
    throw new FlowEmbedError('targetOrigin 无效', 'FLOW_EMBED_ORIGIN_INVALID')
  }
  if (url.protocol !== 'https:' || url.origin === 'null' || url.username || url.password
    || (url.pathname && url.pathname !== '/') || url.search || url.hash) {
    throw new FlowEmbedError('targetOrigin 必须是 HTTPS Origin', 'FLOW_EMBED_ORIGIN_INVALID')
  }
  return url.origin
}

/**
 * Embed URL 必须与显式 Origin 完全一致，并精确指向本次 launch 的公开入口。
 * Query/fragment 均被拒绝，保证一次性 code 永远不会进入 iframe URL。
 */
export function normalizeEmbedUrl(value, targetOrigin, launchId) {
  let url
  try {
    url = new URL(String(value || '').trim())
  } catch {
    throw new FlowEmbedError('embedUrl 无效', 'FLOW_EMBED_URL_INVALID')
  }
  const expectedPath = `/embed/v1/launches/${encodeURIComponent(launchId)}`
  if (url.protocol !== 'https:' || url.origin !== targetOrigin || url.username || url.password
    || url.pathname !== expectedPath || url.search || url.hash) {
    throw new FlowEmbedError('embedUrl 不属于受信任的 Embed 入口', 'FLOW_EMBED_URL_INVALID')
  }
  return url.href
}

export function validateCommandPayload(type, payload) {
  const source = payload === undefined || payload === null ? {} : payload
  if (!COMMAND_TYPES.has(type) || !isPlainRecord(source) || hasDangerousKeys(source)) return false
  if ([FLOW_EMBED_COMMANDS.REFRESH, FLOW_EMBED_COMMANDS.FOCUS, FLOW_EMBED_COMMANDS.DESTROY].includes(type)) {
    return Object.keys(source).length === 0
  }
  if (type === FLOW_EMBED_COMMANDS.SET_THEME) {
    return Object.keys(source).length === 1
      && ['light', 'dark', 'system'].includes(String(source.theme || '').toLowerCase())
  }
  return Object.keys(source).length === 1
    && /^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8}){0,2}$/.test(String(source.locale || ''))
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
  return isSafeText(value, FLOW_EMBED_SELECTION_CONSTRAINTS.recordIdMaxLength)
    && RECORD_ID_PATTERN.test(value)
}

function isClientValue(value) {
  if (value === null || typeof value === 'boolean') return true
  if (typeof value === 'number') return Number.isFinite(value)
  if (typeof value === 'string') {
    return value.length <= FLOW_EMBED_SELECTION_CONSTRAINTS.scalarStringMaxLength
  }
  return Array.isArray(value)
    && value.length <= FLOW_EMBED_SELECTION_CONSTRAINTS.clientArrayMaxItems
    && value.every(item => item === null
      || typeof item === 'boolean'
      || (typeof item === 'number' && Number.isFinite(item))
      || (typeof item === 'string'
        && item.length <= FLOW_EMBED_SELECTION_CONSTRAINTS.scalarStringMaxLength))
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

function isSafeJson(value, depth = 0) {
  if (depth > 8) return false
  if (value === null || typeof value === 'boolean') return true
  if (typeof value === 'number') return Number.isFinite(value)
  if (typeof value === 'string') return value.length <= MAX_MESSAGE_BYTES
  if (Array.isArray(value)) {
    return value.length <= 200 && value.every(item => isSafeJson(item, depth + 1))
  }
  return isPlainRecord(value) && Object.keys(value).length <= 200
    && !hasDangerousKeys(value)
    && Object.values(value).every(item => isSafeJson(item, depth + 1))
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

/** 对 iframe 事件逐类型校验；MessagePort 已认证也不能把任意对象交给宿主回调。 */
export function validateEventPayload(type, payload) {
  if (!EVENT_TYPES.has(type) || !isPlainRecord(payload) || hasDangerousKeys(payload)) return false
  switch (type) {
    case FLOW_EMBED_EVENTS.ACK:
      return hasExactShape(payload, ['command']) && COMMAND_TYPES.has(payload.command)
    case FLOW_EMBED_EVENTS.INITIALIZED:
      return hasExactShape(payload, ['viewKey', 'surfaceType', 'capabilities'])
        && VIEW_KEY_PATTERN.test(payload.viewKey)
        && ['LIST', 'FORM'].includes(payload.surfaceType)
        && Array.isArray(payload.capabilities) && payload.capabilities.length <= 32
        && payload.capabilities.every(value => V1_CAPABILITIES.has(value))
    case FLOW_EMBED_EVENTS.RESIZE:
      return hasExactShape(payload, ['height'])
        && Number.isFinite(payload.height) && payload.height >= 0 && payload.height <= 100000
    case FLOW_EMBED_EVENTS.SELECTION_CHANGED:
      return hasExactShape(payload, ['selection'])
        && Array.isArray(payload.selection) && payload.selection.length <= 1000
        && payload.selection.every(isRecordProjection)
    case FLOW_EMBED_EVENTS.ACTION_STARTED:
      return hasExactShape(payload, ['actionKey', 'clientMutationId'])
        && ACTION_KEY_PATTERN.test(payload.actionKey)
        && CLIENT_MUTATION_ID_PATTERN.test(payload.clientMutationId)
    case FLOW_EMBED_EVENTS.ACTION_COMPLETED:
      return hasExactShape(payload, ['actionKey', 'receiptId', 'result'])
        && ACTION_KEY_PATTERN.test(payload.actionKey)
        && RECEIPT_ID_PATTERN.test(payload.receiptId)
        && isSafeJson(payload.result)
    case FLOW_EMBED_EVENTS.FORM_SAVED:
      return hasExactShape(payload, ['receiptId', 'record', 'clientMutationId'])
        && RECEIPT_ID_PATTERN.test(payload.receiptId)
        && isRecordProjection(payload.record)
        && CLIENT_MUTATION_ID_PATTERN.test(payload.clientMutationId)
    case FLOW_EMBED_EVENTS.NAVIGATION_REQUEST:
      return hasExactShape(payload, ['target', 'recordId'])
        && NAVIGATION_TARGETS.has(payload.target)
        && (payload.recordId === null || isRecordId(payload.recordId))
    case FLOW_EMBED_EVENTS.CLOSE_REQUESTED:
      return hasExactShape(payload, ['reason']) && isSafeText(payload.reason, 128)
    case FLOW_EMBED_EVENTS.ERROR:
      return validateErrorPayload(payload)
    case FLOW_EMBED_EVENTS.SESSION_EXPIRED:
      return hasExactShape(payload, ['reason', 'relaunchRequired'])
        && ERROR_CODE_PATTERN.test(payload.reason)
        && payload.relaunchRequired === true
    default:
      return false
  }
}

export function isSupportedProtocol(value) {
  return value === FLOW_EMBED_PROTOCOL
}
