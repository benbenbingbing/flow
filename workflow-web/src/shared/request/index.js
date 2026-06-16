import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

export const API_SUCCESS_CODES = new Set([0, 200, '0', '200'])

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

function redirectToLogin(message) {
  ElMessage.error(message || '登录已过期，请重新登录')
  const userStore = useUserStore()
  userStore.logout()
  window.location.href = '/login'
}

function rejectWithMessage(message, source) {
  const error = new Error(message || '请求失败')
  error.source = source
  return Promise.reject(error)
}

function handleApiPayload(payload) {
  if (!payload || typeof payload.code === 'undefined') {
    return payload
  }

  const { code } = payload
  if (API_SUCCESS_CODES.has(code)) {
    return normalizeApiResponse(payload)
  }

  const message = getApiErrorMessage(payload)
  if (Number(code) === 401) {
    redirectToLogin(message)
    return rejectWithMessage(message, payload)
  }

  ElMessage.error(message)
  return rejectWithMessage(message, payload)
}

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
})

request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    const token = userStore.token
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => {
    const payload = response.data

    // Blob 响应：如果后端返回的是 JSON 错误，先解析再按统一逻辑处理
    if (response.config && response.config.responseType === 'blob') {
      const contentType = (response.headers && response.headers['content-type']) || ''
      if (contentType.includes('application/json')) {
        return payload.text().then((text) => {
          try {
            const json = JSON.parse(text)
            return handleApiPayload(json)
          } catch (e) {
            return payload
          }
        })
      }
      return payload
    }

    return handleApiPayload(payload)
  },
  (error) => {
    const { response } = error
    if (response?.status === 401) {
      redirectToLogin(getApiErrorMessage(response.data, '登录已过期，请重新登录'))
      return Promise.reject(error)
    }

    const message = response
      ? getApiErrorMessage(response.data)
      : error.message || '网络错误'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
