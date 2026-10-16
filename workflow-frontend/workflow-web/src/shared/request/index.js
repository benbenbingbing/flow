import { createRequestRuntime, notifyRequestError } from '@flow/workflow-api/request'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { buildAuthRedirectUrl } from '@/shared/login-redirect'

/** PC 与 Embed 的宿主适配；共享 transport 不依赖桌面 UI 或应用 store。 */
const runtime = createRequestRuntime({
  baseURL: import.meta.env?.VITE_API_BASE_URL || '/api',
  getSession: () => useUserStore(),
  notifyError: message => ElMessage.error(message),
  getOrigin: () => globalThis.location?.origin,
  onAuthExpired: () => {
    const target = buildAuthRedirectUrl(globalThis.location)
    if (target) globalThis.location.href = target
  },
  onPasswordResetRequired: () => {
    const target = buildAuthRedirectUrl(globalThis.location, '/change-password')
    if (target) globalThis.location.href = target
  }
})
export const { configureEmbedDelegatedRequest, resetEmbedDelegatedRequest, isEmbedDelegatedRequestEnabled, refreshAuthSession, restoreAuthSession } = runtime
export * from '@flow/workflow-api/request'
/** 页面仍可展示本地校验/运行时异常，已由 transport 提示的失败不重复弹出。 */
export function showRequestError(error, fallback) {
  notifyRequestError(error, message => ElMessage.error(message), fallback)
}
export default runtime.request
