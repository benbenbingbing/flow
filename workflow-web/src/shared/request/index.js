import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  createSingleFlight,
  isAccessTokenExpired,
  isTerminalAuthError,
  shouldRefreshAccessToken
} from '@/shared/auth-session'

export const API_SUCCESS_CODES = new Set([0, 200, '0', '200'])
export const BUSINESS_TRACE_HEADER = 'X-Business-Trace-Key'

const API_BASE_URL =
  import.meta.env?.VITE_API_BASE_URL || '/api'
const ACCESS_EXPIRED_ERROR_CODE = 'AUTH_ACCESS_EXPIRED'
const NO_AUTH_RETRY = Symbol('NO_AUTH_RETRY')

let authTerminationHandled = false
let bootstrapPromise = null

export function createBusinessTraceKey() {
  if (globalThis.crypto?.randomUUID) {
    return `ui_${globalThis.crypto.randomUUID()}`
  }
  return `ui_${Date.now().toString(36)}_${Math.random().toString(36).slice(2)}`
}

export function ensureBusinessTraceHeader(config = {}) {
  const method = String(config.method || 'get').toLowerCase()
  if (!['post', 'put', 'patch', 'delete'].includes(method)) {
    return config
  }
  config.headers ||= {}
  const existing = typeof config.headers.get === 'function'
    ? config.headers.get(BUSINESS_TRACE_HEADER)
    : config.headers[BUSINESS_TRACE_HEADER]
      || config.headers[BUSINESS_TRACE_HEADER.toLowerCase()]
  if (!existing) {
    const traceKey = createBusinessTraceKey()
    if (typeof config.headers.set === 'function') {
      config.headers.set(BUSINESS_TRACE_HEADER, traceKey)
    } else {
      config.headers[BUSINESS_TRACE_HEADER] = traceKey
    }
  }
  return config
}

export function toPageParams(page = {}) {
  const pageNum = page.pageNum ?? page.currentPage ?? page.page ?? 1
  const pageSize = page.pageSize ?? page.size ?? page.limit ?? 10
  return { pageNum, pageSize }
}

export function normalizePageResult(payload) {
  if (!payload || typeof payload !== 'object') {
    return { list: [], total: 0, pageNum: 1, pageSize: 10 }
  }

  const list = payload.list ?? payload.records ?? payload.rows ?? payload.data ?? []
  const total = payload.total ?? payload.count ?? 0
  const pageNum = payload.pageNum ?? payload.current ?? payload.currentPage ?? payload.page ?? 1
  const pageSize = payload.pageSize ?? payload.size ?? payload.limit ?? list.length

  return { ...payload, list, total, pageNum, pageSize }
}

export function isPageResult(payload) {
  return Boolean(payload && typeof payload === 'object' && (
    Array.isArray(payload.list) ||
    Array.isArray(payload.records) ||
    Array.isArray(payload.rows) ||
    typeof payload.total !== 'undefined'
  ))
}

export function normalizeApiResponse(payload) {
  if (!payload || typeof payload !== 'object' || typeof payload.code === 'undefined') {
    return payload
  }

  const data = payload.data
  return isPageResult(data) ? normalizePageResult(data) : data
}

export function getApiErrorMessage(payload, fallback = '请求失败') {
  return payload?.message || payload?.msg || fallback
}

function createApiError(
  message,
  source,
  status
) {
  const error = new Error(message || '请求失败')
  error.source = source
  error.errorCode = source?.errorCode
  error.currentData = source?.data
  error.status = status ?? (Number(source?.code) || undefined)
  return error
}

function isAuthLifecycleRequest(config = {}) {
  const url = String(config.url || '')
  return [
    '/auth/login',
    '/auth/refresh',
    '/auth/logout'
  ].some(path => url.endsWith(path))
}

function isTrustedApiRequest(config = {}) {
  const url = String(config.url || '')
  if (!/^https?:\/\//i.test(url)) {
    return true
  }
  if (typeof window === 'undefined') {
    return false
  }
  const apiOrigin = new URL(
    API_BASE_URL,
    window.location.origin
  ).origin
  return new URL(url).origin === apiOrigin
}

function setAuthorizationHeader(config, token) {
  config.headers ||= {}
  if (typeof config.headers.set === 'function') {
    if (token) {
      config.headers.set('Authorization', `Bearer ${token}`)
    } else {
      config.headers.delete?.('Authorization')
    }
    return
  }
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  } else {
    delete config.headers.Authorization
  }
}

function terminateAuthSession(
  message,
  {
    notify = true,
    broadcast = true
  } = {}
) {
  const userStore = useUserStore()
  userStore.clearAuth({
    broadcast,
    reason: 'invalidated'
  })
  if (authTerminationHandled) return

  authTerminationHandled = true
  if (notify) {
    ElMessage.error(message || '登录已过期，请重新登录')
  }
  if (
    typeof window !== 'undefined'
    && window.location.pathname !== '/login'
  ) {
    window.location.href = '/login'
  }
}

const refreshClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

async function executeRefresh() {
  let response
  try {
    response = await refreshClient.post('/auth/refresh')
  } catch (error) {
    if (error.response) {
      throw createApiError(
        getApiErrorMessage(
          error.response.data,
          '登录会话刷新失败'
        ),
        error.response.data,
        error.response.status
      )
    }
    throw error
  }
  const payload = response.data
  if (
    !payload
    || typeof payload.code === 'undefined'
    || !API_SUCCESS_CODES.has(payload.code)
  ) {
    throw createApiError(
      getApiErrorMessage(payload, '登录会话刷新失败'),
      payload,
      response.status
    )
  }

  const session = normalizeApiResponse(payload)
  const userStore = useUserStore()
  userStore.applySession(session)
  authTerminationHandled = false
  return session
}

/**
 * 同一标签页内所有请求共用一次 Refresh 调用。
 */
export const refreshAuthSession =
  createSingleFlight(executeRefresh)

/**
 * 路由首次初始化时尝试通过 HttpOnly Cookie 恢复会话。
 */
export function restoreAuthSession() {
  if (!bootstrapPromise) {
    bootstrapPromise = refreshAuthSession()
      .then(() => true)
      .catch((error) => {
        if (isTerminalAuthError(error.errorCode)) {
          terminateAuthSession(error.message, {
            notify: false,
            broadcast: false
          })
          return false
        }
        return Boolean(useUserStore().token)
      })
  }
  return bootstrapPromise
}

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

request.interceptors.request.use(
  async (config) => {
    ensureBusinessTraceHeader(config)
    const userStore = useUserStore()
    if (userStore.token && authTerminationHandled) {
      authTerminationHandled = false
    }

    const trustedApiRequest = isTrustedApiRequest(config)
    if (!trustedApiRequest) {
      config.skipAuthRefresh = true
      config.withCredentials = false
      setAuthorizationHeader(config, '')
      return config
    }

    const mayRefresh = !config.skipAuthRefresh
      && !isAuthLifecycleRequest(config)
    if (
      mayRefresh
      && shouldRefreshAccessToken(
        userStore.token,
        userStore.tokenExpiresAt
      )
    ) {
      try {
        await refreshAuthSession()
      } catch (error) {
        if (isTerminalAuthError(error.errorCode)) {
          terminateAuthSession(error.message)
          throw error
        }
        if (isAccessTokenExpired(userStore.tokenExpiresAt)) {
          throw error
        }
      }
    }

    setAuthorizationHeader(config, userStore.token)
    return config
  },
  (error) => Promise.reject(error)
)

async function retryAfterRefresh(config, payload) {
  if (
    config?.skipAuthRefresh
    || config?._authRetried
    || payload?.errorCode !== ACCESS_EXPIRED_ERROR_CODE
  ) {
    return NO_AUTH_RETRY
  }

  try {
    await refreshAuthSession()
    return request({
      ...config,
      _authRetried: true
    })
  } catch (error) {
    if (isTerminalAuthError(error.errorCode)) {
      terminateAuthSession(error.message)
    }
    throw error
  }
}

async function handleApiPayload(
  payload,
  config = {},
  status
) {
  if (!payload || typeof payload.code === 'undefined') {
    return payload
  }

  if (API_SUCCESS_CODES.has(payload.code)) {
    return normalizeApiResponse(payload)
  }

  if (Number(payload.code) === 401) {
    const retried = await retryAfterRefresh(config, payload)
    if (retried !== NO_AUTH_RETRY) return retried
    if (
      !config.skipAuthRefresh
      && isTerminalAuthError(payload.errorCode)
    ) {
      const message = getApiErrorMessage(
        payload,
        '登录已过期，请重新登录'
      )
      terminateAuthSession(message)
      throw createApiError(message, payload, status)
    }
  }

  const message = getApiErrorMessage(payload)
  if (!config.silentError) {
    ElMessage.error(message)
  }
  throw createApiError(message, payload, status)
}

request.interceptors.response.use(
  async (response) => {
    const payload = response.data

    if (response.config?.responseType === 'blob') {
      const contentType =
        response.headers?.['content-type'] || ''
      if (contentType.includes('application/json')) {
        const text = await payload.text()
        try {
          return await handleApiPayload(
            JSON.parse(text),
            response.config,
            response.status
          )
        } catch (error) {
          if (error instanceof SyntaxError) {
            return payload
          }
          throw error
        }
      }
      return payload
    }

    return handleApiPayload(
      payload,
      response.config || {},
      response.status
    )
  },
  async (error) => {
    const { response, config = {} } = error
    if (response?.status === 401) {
      let payload = response.data
      if (
        config.responseType === 'blob'
        && payload
        && typeof payload.text === 'function'
      ) {
        try {
          payload = JSON.parse(await payload.text())
        } catch {
          payload = response.data
        }
      }
      const retried = await retryAfterRefresh(
        config,
        payload
      )
      if (retried !== NO_AUTH_RETRY) return retried
      if (
        !config.skipAuthRefresh
        && isTerminalAuthError(payload?.errorCode)
      ) {
        const message = getApiErrorMessage(
          payload,
          '登录已过期，请重新登录'
        )
        terminateAuthSession(message)
        error.message = message
        error.errorCode = payload.errorCode
        error.currentData = payload.data
        error.status = response.status
        return Promise.reject(error)
      }
    }
    if (
      response?.status === 428
      && window.location.pathname !== '/change-password'
    ) {
      window.location.href = '/change-password'
    }

    const message = response
      ? getApiErrorMessage(response.data)
      : error.message || '网络错误'
    error.message = message
    error.errorCode = response?.data?.errorCode
    error.currentData = response?.data?.data
    error.status = response?.status
    if (!config.silentError) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default request
