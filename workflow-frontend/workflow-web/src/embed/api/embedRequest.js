import { embedSession } from '../session/embedSession.js'

export const EMBED_API_BASE_PATH = '/api/embed/v1'
const API_SUCCESS_CODES = new Set([0, 200, 201, '0', '200', '201'])
const FORBIDDEN_CALLER_HEADERS = new Set([
  'authorization',
  'cookie',
  'host',
  'origin',
  'referer'
])

export class EmbedApiError extends Error {
  constructor(
    message,
    {
      errorCode = 'EMBED_REQUEST_FAILED',
      status,
      traceId,
      payload,
      cause
    } = {}
  ) {
    super(message || 'Embed 请求失败')
    this.name = 'EmbedApiError'
    this.errorCode = errorCode
    this.status = status
    this.traceId = traceId
    this.payload = payload
    if (cause) this.cause = cause
  }
}

/**
 * Embed client 只允许访问固定 API 前缀下的相对路径。
 * 这避免调用方把短 token 带往任意绝对 URL，或用 ../ 逃逸出受限前缀。
 */
export function buildEmbedApiUrl(path = '', basePath = EMBED_API_BASE_PATH) {
  const rawPath = String(path || '').trim()
  const normalizedBasePath = `/${String(basePath || '')
    .replace(/^\/+|\/+$/g, '')}`

  if (!rawPath || /^https?:/i.test(rawPath) || rawPath.startsWith('//')) {
    throw new EmbedApiError('Embed API 路径无效', {
      errorCode: 'EMBED_REQUEST_PATH_INVALID'
    })
  }

  const [pathname, search = ''] = rawPath.split('?')
  const rawSegments = pathname.split('/').filter(Boolean)
  if (!rawSegments.length) {
    throw new EmbedApiError('Embed API 路径无效', {
      errorCode: 'EMBED_REQUEST_PATH_INVALID'
    })
  }

  const decodedSegments = rawSegments.map(segment => {
    let decoded
    try {
      decoded = decodeURIComponent(segment)
    } catch {
      throw new EmbedApiError('Embed API 路径无效', {
        errorCode: 'EMBED_REQUEST_PATH_INVALID'
      })
    }
    if (!decoded || decoded === '.' || decoded === '..' || /[/\\\0]/.test(decoded)) {
      throw new EmbedApiError('Embed API 路径无效', {
        errorCode: 'EMBED_REQUEST_PATH_INVALID'
      })
    }
    return decoded
  })

  const encodedPath = decodedSegments
    .map(segment => encodeURIComponent(segment))
    .join('/')
  return `${normalizedBasePath}/${encodedPath}${search ? `?${search}` : ''}`
}

function copySafeHeaders(headers = {}) {
  const copied = {}
  for (const [key, value] of Object.entries(headers || {})) {
    const normalizedKey = String(key).toLowerCase()
    if (!normalizedKey || FORBIDDEN_CALLER_HEADERS.has(normalizedKey)) continue
    copied[key] = String(value)
  }
  return copied
}

function extractTraceId(response, payload) {
  return payload?.traceId
    || payload?.traceID
    || response?.headers?.get?.('x-trace-id')
    || response?.headers?.get?.('x-business-trace-key')
    || response?.headers?.get?.('x-request-id')
    || undefined
}

async function readResponsePayload(response) {
  if (response?.status === 204) return undefined
  const text = await response.text()
  if (!text) return undefined
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

function unwrapResponsePayload(payload, response) {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    return payload
  }

  if (typeof payload.code === 'undefined') return payload
  if (API_SUCCESS_CODES.has(payload.code)) return payload.data

  throw new EmbedApiError(payload.message || payload.msg || 'Embed 请求被拒绝', {
    errorCode: payload.errorCode || String(payload.code),
    status: response?.status,
    traceId: extractTraceId(response, payload),
    payload
  })
}

function createAbortContext(signal, timeoutMs, setTimeoutImpl, clearTimeoutImpl) {
  const AbortControllerImpl = globalThis.AbortController
  if (!AbortControllerImpl) {
    return {
      signal,
      cleanup() {},
      didTimeout: () => false
    }
  }

  const controller = new AbortControllerImpl()
  let timeoutId
  let timedOut = false
  const abortFromCaller = () => controller.abort(signal?.reason)

  if (signal?.aborted) abortFromCaller()
  else signal?.addEventListener?.('abort', abortFromCaller, { once: true })

  if (Number.isFinite(timeoutMs) && timeoutMs > 0) {
    timeoutId = setTimeoutImpl(() => {
      timedOut = true
      controller.abort(new Error('Embed request timeout'))
    }, timeoutMs)
  }

  return {
    signal: controller.signal,
    didTimeout: () => timedOut,
    cleanup() {
      if (timeoutId !== undefined) clearTimeoutImpl(timeoutId)
      signal?.removeEventListener?.('abort', abortFromCaller)
    }
  }
}

function normalizeMethod(method = 'GET') {
  const normalized = String(method || 'GET').toUpperCase()
  if (!['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].includes(normalized)) {
    throw new EmbedApiError('Embed 请求方法无效', {
      errorCode: 'EMBED_REQUEST_METHOD_INVALID'
    })
  }
  return normalized
}

/** 判断调用失败是否意味着 iframe 应清空自己的内存会话。 */
export function isEmbedSessionFailure(error) {
  return [
    'EMBED_SESSION_MISSING',
    'EMBED_SESSION_INVALID',
    'EMBED_SESSION_EXPIRED',
    'EMBED_SESSION_REVOKED'
  ].includes(error?.errorCode)
    || error?.status === 401
}

/**
 * 创建完全独立的 Embed HTTP client。
 *
 * 它故意不复用 axios、普通用户刷新逻辑或应用 router：credentials omit
 * 防止同源时也附带后台 session cookie，redirect error 防止失败被带到登录页。
 */
export function createEmbedRequest({
  fetchImpl = globalThis.fetch,
  session = embedSession,
  basePath = EMBED_API_BASE_PATH,
  timeoutMs = 15000,
  setTimeoutImpl = globalThis.setTimeout,
  clearTimeoutImpl = globalThis.clearTimeout
} = {}) {
  if (typeof fetchImpl !== 'function') {
    throw new EmbedApiError('当前浏览器不支持 Fetch API', {
      errorCode: 'EMBED_FETCH_UNAVAILABLE'
    })
  }

  async function request(path, {
    method = 'GET',
    body,
    headers,
    signal,
    auth = true,
    keepalive = false
  } = {}) {
    const normalizedMethod = normalizeMethod(method)
    const url = buildEmbedApiUrl(path, basePath)
    if (normalizedMethod === 'GET' && body !== undefined) {
      throw new EmbedApiError('GET 请求不能携带 body', {
        errorCode: 'EMBED_REQUEST_BODY_INVALID'
      })
    }

    const requestHeaders = {
      Accept: 'application/json',
      ...copySafeHeaders(headers)
    }
    if (body !== undefined) {
      requestHeaders['Content-Type'] = 'application/json;charset=UTF-8'
    }

    if (auth) {
      const token = session?.getAccessToken?.({ required: false }) || ''
      if (!token) {
        throw new EmbedApiError('Embed 会话不可用', {
          errorCode: 'EMBED_SESSION_MISSING'
        })
      }
      requestHeaders.Authorization = `Bearer ${token}`
    }

    const abortContext = createAbortContext(
      signal,
      timeoutMs,
      setTimeoutImpl,
      clearTimeoutImpl
    )

    let response
    try {
      response = await fetchImpl(url, {
        method: normalizedMethod,
        headers: requestHeaders,
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: abortContext.signal,
        credentials: 'omit',
        cache: 'no-store',
        redirect: 'error',
        referrerPolicy: 'no-referrer',
        keepalive: keepalive === true
      })
    } catch (cause) {
      const errorCode = abortContext.didTimeout()
        ? 'EMBED_REQUEST_TIMEOUT'
        : abortContext.signal?.aborted
          ? 'EMBED_REQUEST_ABORTED'
          : 'EMBED_NETWORK_ERROR'
      throw new EmbedApiError('Embed 网络请求失败', { errorCode, cause })
    } finally {
      abortContext.cleanup()
    }

    const payload = await readResponsePayload(response)
    const traceId = extractTraceId(response, payload)
    if (!response?.ok) {
      throw new EmbedApiError(
        payload?.message || payload?.msg || 'Embed 请求失败',
        {
          errorCode: payload?.errorCode || `HTTP_${response?.status || 0}`,
          status: response?.status,
          traceId,
          payload
        }
      )
    }

    return unwrapResponsePayload(payload, response)
  }

  return Object.freeze({
    get(path, options = {}) {
      return request(path, { ...options, method: 'GET' })
    },
    post(path, body, options = {}) {
      return request(path, { ...options, method: 'POST', body })
    },
    delete(path, options = {}) {
      return request(path, { ...options, method: 'DELETE' })
    },
    request
  })
}
