import { normalizeTrustedOrigin } from '../bridge/embedBridge.js'
import { FLOW_EMBED_PROTOCOL_VERSION } from '../api/embedRuntimeApi.js'

const CHANNEL_ID_PATTERN = /^[A-Za-z0-9._:-]{16,128}$/
const LAUNCH_ID_PATTERN = /^lch_[A-Za-z0-9_-]{16,60}$/
const ENTRY_PATH_PATTERN = /^\/embed\/v1\/launches\/([^/]+)$/
const ENTRY_CONFIG_KEYS = new Set([
  'launchId',
  'expectedParentOrigin',
  'channelId',
  'protocolVersion'
])
const DANGEROUS_KEYS = new Set(['__proto__', 'constructor', 'prototype'])
const MAX_ENTRY_CONFIG_LENGTH = 16 * 1024

export class EmbedEntryConfigError extends Error {
  constructor(message, errorCode = 'EMBED_ENTRY_CONFIG_INVALID') {
    super(message)
    this.name = 'EmbedEntryConfigError'
    this.errorCode = errorCode
  }
}

function decodeBase64Url(value, atobImpl = globalThis.atob) {
  const encoded = String(value || '').trim()
  if (!encoded || typeof atobImpl !== 'function') {
    throw new EmbedEntryConfigError('Embed entry 配置缺失', 'EMBED_ENTRY_CONFIG_MISSING')
  }
  if (!/^[A-Za-z0-9_-]+$/.test(encoded)) {
    throw new EmbedEntryConfigError('Embed entry 配置编码无效', 'EMBED_ENTRY_CONFIG_ENCODING_INVALID')
  }
  if (encoded.length > MAX_ENTRY_CONFIG_LENGTH) {
    throw new EmbedEntryConfigError('Embed entry 配置过大', 'EMBED_ENTRY_CONFIG_TOO_LARGE')
  }

  const padded = `${encoded.replace(/-/g, '+').replace(/_/g, '/')}${'='.repeat((4 - encoded.length % 4) % 4)}`
  let binary
  try {
    binary = atobImpl(padded)
  } catch {
    throw new EmbedEntryConfigError('Embed entry 配置编码无效', 'EMBED_ENTRY_CONFIG_ENCODING_INVALID')
  }

  const bytes = Uint8Array.from(binary, char => char.charCodeAt(0))
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  } catch {
    throw new EmbedEntryConfigError('Embed entry 配置解码失败', 'EMBED_ENTRY_CONFIG_ENCODING_INVALID')
  }
}

function isPlainRecord(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const prototype = Object.getPrototypeOf(value)
  return prototype === Object.prototype || prototype === null
}

function hasDangerousKeys(value) {
  if (!value || typeof value !== 'object') return false
  if (Array.isArray(value)) return value.some(hasDangerousKeys)
  if (!isPlainRecord(value)) return true
  return Object.keys(value).some(key => DANGEROUS_KEYS.has(key))
    || Object.values(value).some(hasDangerousKeys)
}

/**
 * 从动态 Entry HTML 读取非秘密的绑定信息。
 * 该 meta 在 iframe 脚本执行前由服务端写入，使子页面能先校验父 origin，
 * 而不是收到任意 postMessage 后才决定信任对象。
 */
export function readEmbedEntryConfig(documentRef = globalThis.document) {
  const meta = documentRef?.querySelector?.('meta[name="flow-embed-entry"]')
  const content = meta?.getAttribute?.('content') || ''
  const text = decodeBase64Url(content)
  try {
    const parsed = JSON.parse(text)
    if (!isPlainRecord(parsed) || hasDangerousKeys(parsed)) {
      throw new Error('not an object')
    }
    return parsed
  } catch {
    throw new EmbedEntryConfigError('Embed entry 配置格式无效', 'EMBED_ENTRY_CONFIG_JSON_INVALID')
  }
}

/** 规范化并校验动态 Entry 配置，配置中不得出现 handoff code 或 access token。 */
export function validateEmbedEntryConfig(rawConfig, {
  launchId,
  allowInsecureParentOrigin = false
} = {}) {
  if (!isPlainRecord(rawConfig) || hasDangerousKeys(rawConfig)) {
    throw new EmbedEntryConfigError('Embed entry 配置无效')
  }
  if (Object.keys(rawConfig).some(key => !ENTRY_CONFIG_KEYS.has(key))) {
    throw new EmbedEntryConfigError('Embed entry 包含未知配置', 'EMBED_ENTRY_CONFIG_KEY_FORBIDDEN')
  }
  const configuredLaunchId = String(rawConfig.launchId || '').trim()
  const expectedLaunchId = String(launchId || '').trim()
  if (!LAUNCH_ID_PATTERN.test(configuredLaunchId)
    || !LAUNCH_ID_PATTERN.test(expectedLaunchId)
    || configuredLaunchId !== expectedLaunchId) {
    throw new EmbedEntryConfigError('Embed launch 不匹配', 'EMBED_ENTRY_CONFIG_LAUNCH_MISMATCH')
  }
  const channelId = String(rawConfig.channelId || '').trim()
  if (!CHANNEL_ID_PATTERN.test(channelId)) {
    throw new EmbedEntryConfigError('Embed channel 无效', 'EMBED_ENTRY_CONFIG_CHANNEL_INVALID')
  }

  const protocolVersion = String(rawConfig.protocolVersion || '').trim()
  if (protocolVersion !== FLOW_EMBED_PROTOCOL_VERSION) {
    throw new EmbedEntryConfigError(
      'Embed 协议版本不受支持',
      'EMBED_ENTRY_CONFIG_PROTOCOL_UNSUPPORTED'
    )
  }

  return Object.freeze({
    launchId: configuredLaunchId,
    expectedParentOrigin: normalizeTrustedOrigin(
      rawConfig.expectedParentOrigin,
      { allowInsecure: allowInsecureParentOrigin }
    ),
    channelId,
    protocolVersion
  })
}

/** 从严格的 Embed Entry 路径提取 launchId，拒绝额外路径和查询参数。 */
export function readEmbedLaunchId(locationRef = globalThis.location) {
  const pathname = String(locationRef?.pathname || '')
  if (String(locationRef?.search || '')) {
    throw new EmbedEntryConfigError('Embed entry 不接受查询参数', 'EMBED_ENTRY_QUERY_FORBIDDEN')
  }
  const match = pathname.match(ENTRY_PATH_PATTERN)
  if (!match) {
    throw new EmbedEntryConfigError('Embed entry 路径无效', 'EMBED_ENTRY_PATH_INVALID')
  }
  let launchId
  try {
    launchId = decodeURIComponent(match[1])
  } catch {
    throw new EmbedEntryConfigError('Embed launch 编码无效', 'EMBED_ENTRY_PATH_INVALID')
  }
  if (!LAUNCH_ID_PATTERN.test(launchId)) {
    throw new EmbedEntryConfigError('Embed launch 无效', 'EMBED_ENTRY_PATH_INVALID')
  }
  return launchId
}

/** 一次完成路径、meta 和协议校验，供独立入口在启动任何请求前调用。 */
export function resolveEmbedEntryConfig({
  documentRef = globalThis.document,
  locationRef = globalThis.location,
  allowInsecureParentOrigin = false
} = {}) {
  const launchId = readEmbedLaunchId(locationRef)
  return validateEmbedEntryConfig(readEmbedEntryConfig(documentRef), {
    launchId,
    allowInsecureParentOrigin
  })
}

/** URL fragment 可能被手工接入方误放凭据；页面启动时只负责立即清除，不消费它。 */
export function clearEmbedEntryFragment({
  locationRef = globalThis.location,
  historyRef = globalThis.history
} = {}) {
  if (!locationRef?.hash) return false
  if (typeof historyRef?.replaceState !== 'function') {
    throw new EmbedEntryConfigError('无法清除 Embed URL fragment', 'EMBED_ENTRY_FRAGMENT_CLEAR_FAILED')
  }
  historyRef.replaceState(
    historyRef.state ?? null,
    '',
    `${locationRef.pathname || ''}${locationRef.search || ''}`
  )
  return true
}
