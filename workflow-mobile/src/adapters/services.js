import { reactive } from 'vue'
import { showFailToast } from 'vant'
import {
  createRequestRuntime, createAuthApi, createProcessTaskApi, createEntityApi,
  createFileApi, createUiConfigApi, createUiCompositionRuntimeApi, createFormRuntimeApi,
  createEntityStatusApi
} from '@flow/workflow-api'

const state = reactive({ token: '', tokenExpiresAt: '', userInfo: null, permissions: [] })
const listeners = new Set()
let channel

/** 移动端访问令牌只保存在内存中；刷新页面通过 HttpOnly Cookie 恢复。 */
export const session = Object.assign(state, {
  applySession(value = {}) {
    const { token = '', tokenExpiresAt = '', ...userInfo } = value
    Object.assign(state, { token, tokenExpiresAt, userInfo })
  },
  clearAuth({ broadcast = false } = {}) {
    Object.assign(state, { token: '', tokenExpiresAt: '', userInfo: null, permissions: [] })
    listeners.forEach(listener => listener())
    if (broadcast) {
      const message = { type: 'SESSION_TERMINATED', reason: 'logout', timestamp: Date.now() }
      channel?.postMessage(message)
      try { localStorage.setItem('auth.session.sync', JSON.stringify(message)) } catch { /* 存储被禁用时仍可通过频道同步。 */ }
    }
  }
})

function goToLogin() {
  if (globalThis.location?.pathname !== '/m/login') globalThis.location?.assign('/m/login')
}
export const transport = createRequestRuntime({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api', getSession: () => session,
  notifyError: message => showFailToast(message), getOrigin: () => globalThis.location?.origin,
  onAuthExpired: goToLogin,
  onPasswordResetRequired: () => { state.userInfo = { ...state.userInfo, passwordResetRequired: true }; goToLogin() }
})
export const request = transport.request
export const auth = createAuthApi(request)
export const tasks = createProcessTaskApi(request)
export const entities = createEntityApi(request)
export const files = createFileApi(request).fileApi
export const ui = createUiConfigApi(request)
export const compositions = createUiCompositionRuntimeApi(request).uiCompositionRuntimeApi
export const forms = createFormRuntimeApi(request)
export const statuses = createEntityStatusApi(request)

/** 权限恢复失败时不保留旧权限；由页面错误状态提供重试，不扩大操作范围。 */
export async function loadIdentity() {
  state.permissions = []
  const [user, permissions] = await Promise.all([auth.getCurrentUser(), auth.getPermissions()])
  state.userInfo = user
  state.permissions = Array.isArray(permissions) ? permissions : []
}
export async function login(credentials) {
  session.applySession(await auth.login(credentials))
  if (!state.userInfo?.passwordResetRequired) await loadIdentity()
}
export async function logout() {
  try { await auth.logout() } finally { session.clearAuth({ broadcast: true }) }
}
export function onSessionCleared(listener) { listeners.add(listener); return () => listeners.delete(listener) }
if (typeof BroadcastChannel !== 'undefined') {
  channel = new BroadcastChannel('flow-auth-session')
  channel.addEventListener('message', event => {
    if (event.data?.type !== 'SESSION_TERMINATED') return
    session.clearAuth(); goToLogin()
  })
}
if (typeof window !== 'undefined') window.addEventListener('storage', event => {
  if (event.key === 'auth.session.sync' && event.newValue) { session.clearAuth(); goToLogin() }
})
