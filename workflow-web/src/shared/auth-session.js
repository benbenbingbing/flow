/**
 * Access Token 续签提前量，避免请求在网络传输期间跨过过期点。
 */
export const ACCESS_REFRESH_SKEW_MS = 60_000

/**
 * 明确表示浏览器会话已经不能继续使用的认证错误。
 */
export const TERMINAL_AUTH_ERROR_CODES = new Set([
  'AUTH_ACCESS_INVALID',
  'AUTH_SESSION_REVOKED',
  'AUTH_ACCOUNT_DISABLED',
  'AUTH_REFRESH_MISSING',
  'AUTH_REFRESH_INVALID',
  'AUTH_REFRESH_IDLE_EXPIRED',
  'AUTH_REFRESH_ABSOLUTE_EXPIRED'
])

/**
 * 判断 Access Token 是否应该在发送业务请求前续签。
 */
export function shouldRefreshAccessToken(
  token,
  tokenExpiresAt,
  now = Date.now(),
  skewMs = ACCESS_REFRESH_SKEW_MS
) {
  if (!token) return false
  const expiresAt = Date.parse(tokenExpiresAt || '')
  return !Number.isFinite(expiresAt) || expiresAt - now <= skewMs
}

/**
 * 判断 Access Token 是否已经越过服务端返回的绝对过期时间。
 */
export function isAccessTokenExpired(
  tokenExpiresAt,
  now = Date.now()
) {
  const expiresAt = Date.parse(tokenExpiresAt || '')
  return !Number.isFinite(expiresAt) || expiresAt <= now
}

/**
 * 判断认证错误是否意味着必须终止当前浏览器会话。
 */
export function isTerminalAuthError(errorCode) {
  return TERMINAL_AUTH_ERROR_CODES.has(errorCode)
}

/**
 * 将同一标签页内的并发调用合并为一次执行。
 */
export function createSingleFlight(executor) {
  let inFlight = null
  return (...args) => {
    if (!inFlight) {
      inFlight = Promise.resolve()
        .then(() => executor(...args))
        .finally(() => {
          inFlight = null
        })
    }
    return inFlight
  }
}
