/** Embed token 的最长长度，防止异常握手消息占用长期内存。 */
const MAX_ACCESS_TOKEN_LENGTH = 8192

export class EmbedSessionError extends Error {
  constructor(message, errorCode = 'EMBED_SESSION_INVALID') {
    super(message)
    this.name = 'EmbedSessionError'
    this.errorCode = errorCode
  }
}

/**
 * 将服务端的 ISO 时间、毫秒时间戳或秒时间戳统一为毫秒。
 * 过期时间是 Embed token 的必填安全约束，不能以“永久 token”兜底。
 */
export function parseEmbedSessionExpiry(value) {
  if (value === null || value === undefined || value === '') return 0

  if (typeof value === 'number' && Number.isFinite(value)) {
    return value > 100000000000 ? Math.floor(value) : Math.floor(value * 1000)
  }

  const text = String(value).trim()
  if (!text) return 0
  if (/^\d+(?:\.\d+)?$/.test(text)) {
    return parseEmbedSessionExpiry(Number(text))
  }

  const parsed = Date.parse(text)
  return Number.isFinite(parsed) ? parsed : 0
}

function normalizeToken(value) {
  const token = String(value || '').trim()
  if (!token || token.length > MAX_ACCESS_TOKEN_LENGTH || /\s/.test(token)) {
    throw new EmbedSessionError('Embed access token 无效', 'EMBED_SESSION_TOKEN_INVALID')
  }
  return token
}

function normalizeLaunchId(value) {
  const launchId = String(value || '').trim()
  if (!launchId || launchId.length > 256) {
    throw new EmbedSessionError('Embed launch 标识无效', 'EMBED_SESSION_LAUNCH_INVALID')
  }
  return launchId
}

/**
 * 创建只驻留内存的 Embed 会话。
 *
 * 不读写 localStorage、sessionStorage 或 cookie；页面刷新、iframe 销毁和
 * token 过期都会自然失效，避免第三方页面残留可复用的凭据。
 */
export function createEmbedSession({ now = () => Date.now() } = {}) {
  let accessToken = ''
  let absoluteExpiresAt = 0
  let idleExpiresAt = 0
  let launchId = ''
  let clearReason = 'empty'
  const listeners = new Set()

  function effectiveExpiresAt() {
    if (!absoluteExpiresAt) return 0
    return idleExpiresAt
      ? Math.min(absoluteExpiresAt, idleExpiresAt)
      : absoluteExpiresAt
  }

  function snapshot() {
    // 快照刻意不暴露 token，调用方只能通过 getAccessToken 获取短暂使用权。
    return Object.freeze({
      active: Boolean(accessToken) && effectiveExpiresAt() > now(),
      expiresAt: absoluteExpiresAt,
      absoluteExpiresAt,
      idleExpiresAt,
      launchId,
      clearReason
    })
  }

  function notify() {
    const current = snapshot()
    for (const listener of listeners) {
      try {
        listener(current)
      } catch {
        // 订阅者错误不能阻止会话清理或其他订阅者收到失效通知。
      }
    }
  }

  function clear(reason = 'cleared') {
    accessToken = ''
    absoluteExpiresAt = 0
    idleExpiresAt = 0
    launchId = ''
    clearReason = String(reason || 'cleared')
    notify()
  }

  function isExpired() {
    return !accessToken || !effectiveExpiresAt() || effectiveExpiresAt() <= now()
  }

  function getAccessToken({ required = false } = {}) {
    if (isExpired()) {
      if (accessToken) clear('expired')
      if (required) {
        throw new EmbedSessionError('Embed 会话已失效', 'EMBED_SESSION_EXPIRED')
      }
      return ''
    }
    return accessToken
  }

  /**
   * 保存一次服务端 exchange 响应。仅接受带未来过期时间的短 token，
   * 且当服务端回传 launchId 时必须与当前 iframe 路由一致。
   */
  function setExchange(exchange = {}, { expectedLaunchId = '' } = {}) {
    const nextToken = normalizeToken(exchange.accessToken ?? exchange.token)
    const expected = expectedLaunchId ? normalizeLaunchId(expectedLaunchId) : ''
    const responseLaunchId = exchange.launchId ? normalizeLaunchId(exchange.launchId) : ''
    if (expected && responseLaunchId && expected !== responseLaunchId) {
      throw new EmbedSessionError('Embed launch 不匹配', 'EMBED_SESSION_LAUNCH_MISMATCH')
    }

    let nextAbsoluteExpiresAt = parseEmbedSessionExpiry(
      exchange.absoluteExpiresAt
        ?? exchange.expiresAt
        ?? exchange.expireAt
        ?? exchange.expiration
    )
    if (!nextAbsoluteExpiresAt && Number.isFinite(Number(exchange.expiresIn))) {
      nextAbsoluteExpiresAt = now() + Number(exchange.expiresIn) * 1000
    }
    const nextIdleExpiresAt = parseEmbedSessionExpiry(exchange.idleExpiresAt)
      || nextAbsoluteExpiresAt
    if (!nextAbsoluteExpiresAt || nextAbsoluteExpiresAt <= now()
      || nextIdleExpiresAt <= now()) {
      throw new EmbedSessionError('Embed token 已过期', 'EMBED_SESSION_EXPIRY_INVALID')
    }

    accessToken = nextToken
    absoluteExpiresAt = Math.floor(nextAbsoluteExpiresAt)
    idleExpiresAt = Math.min(Math.floor(nextIdleExpiresAt), absoluteExpiresAt)
    launchId = responseLaunchId || expected
    clearReason = ''
    notify()
    return snapshot()
  }

  /**
   * 应用心跳返回的空闲到期时间。绝对到期时间只能缩短不能延长，避免客户端
   * 因错误响应把服务端签发的会话寿命扩展到原始上限之外。
   */
  function applyHeartbeat(heartbeat = {}) {
    if (isExpired()) {
      clear('expired')
      throw new EmbedSessionError('Embed 会话已失效', 'EMBED_SESSION_EXPIRED')
    }
    const status = String(heartbeat.status || 'ACTIVE').toUpperCase()
    if (status !== 'ACTIVE') {
      clear(status === 'LOGGED_OUT' ? 'logged_out' : 'revoked')
      throw new EmbedSessionError('Embed 会话已失效', 'EMBED_SESSION_EXPIRED')
    }

    const responseAbsolute = parseEmbedSessionExpiry(
      heartbeat.absoluteExpiresAt ?? heartbeat.expiresAt
    )
    if (responseAbsolute) {
      absoluteExpiresAt = Math.min(absoluteExpiresAt, responseAbsolute)
    }
    const responseIdle = parseEmbedSessionExpiry(heartbeat.idleExpiresAt)
    if (!responseIdle || responseIdle <= now()) {
      clear('expired')
      throw new EmbedSessionError('Embed 心跳到期时间无效', 'EMBED_SESSION_EXPIRY_INVALID')
    }
    idleExpiresAt = Math.min(responseIdle, absoluteExpiresAt)
    if (effectiveExpiresAt() <= now()) {
      clear('expired')
      throw new EmbedSessionError('Embed 会话已失效', 'EMBED_SESSION_EXPIRED')
    }
    notify()
    return snapshot()
  }

  function subscribe(listener) {
    if (typeof listener !== 'function') {
      throw new TypeError('Embed session listener 必须是函数')
    }
    listeners.add(listener)
    return () => listeners.delete(listener)
  }

  return Object.freeze({
    clear,
    getAccessToken,
    getSnapshot: snapshot,
    isExpired,
    applyHeartbeat,
    setExchange,
    subscribe
  })
}

/** 每个 Embed 应用实例默认使用的内存会话；绝不持久化到浏览器存储。 */
export const embedSession = createEmbedSession()
