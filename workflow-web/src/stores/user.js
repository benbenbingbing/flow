import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const TOKEN_STORAGE_KEY = 'auth.accessToken'
const TOKEN_EXPIRES_STORAGE_KEY = 'auth.tokenExpiresAt'
const AUTH_SYNC_STORAGE_KEY = 'auth.session.sync'
const AUTH_CHANNEL_NAME = 'flow-auth-session'

function sessionStorageValue(key) {
  try {
    return globalThis.sessionStorage?.getItem(key) || ''
  } catch {
    return ''
  }
}

function writeSessionStorage(key, value) {
  try {
    if (value) {
      globalThis.sessionStorage?.setItem(key, value)
    } else {
      globalThis.sessionStorage?.removeItem(key)
    }
  } catch {
    // 浏览器禁用存储时仍保留当前标签页内存状态。
  }
}

function removeLegacyAccessToken() {
  try {
    globalThis.localStorage?.removeItem('token')
  } catch {
    // 旧 Token 清理失败不阻止应用启动。
  }
}

function sanitizeUserInfo(info) {
  if (!info || typeof info !== 'object') return null
  const { token, tokenExpiresAt, ...safeInfo } = info
  return safeInfo
}

/**
 * 用户状态管理
 */
export const useUserStore = defineStore('user', () => {
  removeLegacyAccessToken()

  /** 当前标签页使用的短期 Access Token。 */
  const token = ref(sessionStorageValue(TOKEN_STORAGE_KEY))
  /** 当前 Access Token 的 ISO-8601 过期时间。 */
  const tokenExpiresAt = ref(
    sessionStorageValue(TOKEN_EXPIRES_STORAGE_KEY)
  )
  /** 当前登录用户的非敏感展示信息。 */
  const userInfo = ref(null)
  /** 当前用户的实时权限码集合。 */
  const permissions = ref([])

  // Getters
  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')
  const avatar = computed(() => userInfo.value?.avatar || '')
  const roles = computed(() => userInfo.value?.roles || [])
  const isSuperAdmin = computed(() => roles.value.some(role => {
    const code = typeof role === 'string' ? role : role?.roleCode
    return code === 'super_admin'
  }))

  // Actions
  /**
   * 保存后端签发的完整登录会话结果。
   */
  function applySession(session) {
    token.value = session?.token || ''
    tokenExpiresAt.value = session?.tokenExpiresAt || ''
    writeSessionStorage(TOKEN_STORAGE_KEY, token.value)
    writeSessionStorage(
      TOKEN_EXPIRES_STORAGE_KEY,
      tokenExpiresAt.value
    )
    setUserInfo(session)
  }

  /**
   * 兼容只更新 Access Token 的内部调用。
   */
  function setToken(newToken, expiresAt = '') {
    token.value = newToken || ''
    tokenExpiresAt.value = expiresAt || ''
    writeSessionStorage(TOKEN_STORAGE_KEY, token.value)
    writeSessionStorage(
      TOKEN_EXPIRES_STORAGE_KEY,
      tokenExpiresAt.value
    )
  }

  /**
   * 设置用户信息
   */
  function setUserInfo(info) {
    userInfo.value = sanitizeUserInfo(info)
    if (userInfo.value) {
      localStorage.setItem(
        'userInfo',
        JSON.stringify(userInfo.value)
      )
    } else {
      localStorage.removeItem('userInfo')
    }
  }

  /**
   * 设置权限码集合
   */
  function setPermissions(perms) {
    permissions.value = perms || []
    localStorage.setItem('permissions', JSON.stringify(permissions.value))
  }

  /**
   * 从 localStorage 恢复非敏感用户信息和权限。
   */
  function restoreUserInfo() {
    const stored = localStorage.getItem('userInfo')
    if (stored) {
      try {
        userInfo.value = sanitizeUserInfo(JSON.parse(stored))
        localStorage.setItem(
          'userInfo',
          JSON.stringify(userInfo.value)
        )
      } catch (e) {
        console.error('恢复用户信息失败:', e)
      }
    }
    const storedPerms = localStorage.getItem('permissions')
    if (storedPerms) {
      try {
        permissions.value = JSON.parse(storedPerms)
      } catch (e) {
        console.error('恢复权限信息失败:', e)
      }
    }
  }

  /**
   * 清除当前标签页的认证状态。
   */
  function clearAuth({
    broadcast = false,
    reason = 'invalidated'
  } = {}) {
    token.value = ''
    tokenExpiresAt.value = ''
    userInfo.value = null
    permissions.value = []
    writeSessionStorage(TOKEN_STORAGE_KEY, '')
    writeSessionStorage(TOKEN_EXPIRES_STORAGE_KEY, '')
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('permissions')
    if (broadcast) {
      publishSessionTermination(reason)
    }
  }

  /**
   * 主动退出当前浏览器会话，并通知其他标签页同步退出。
   */
  function logout() {
    clearAuth({
      broadcast: true,
      reason: 'logout'
    })
  }

  function handleRemoteTermination() {
    clearAuth({ broadcast: false })
    if (
      typeof window !== 'undefined'
      && window.location.pathname !== '/login'
    ) {
      window.location.href = '/login'
    }
  }

  let authChannel = null
  if (
    typeof window !== 'undefined'
    && typeof window.BroadcastChannel === 'function'
  ) {
    authChannel = new window.BroadcastChannel(
      AUTH_CHANNEL_NAME
    )
    authChannel.addEventListener(
      'message',
      handleRemoteTermination
    )
  }

  if (typeof window !== 'undefined') {
    window.addEventListener('storage', (event) => {
      if (
        event.key === AUTH_SYNC_STORAGE_KEY
        && event.newValue
      ) {
        handleRemoteTermination()
      }
    })
  }

  function publishSessionTermination(reason) {
    const message = {
      type: 'SESSION_TERMINATED',
      reason,
      timestamp: Date.now()
    }
    authChannel?.postMessage(message)
    try {
      localStorage.setItem(
        AUTH_SYNC_STORAGE_KEY,
        JSON.stringify(message)
      )
    } catch {
      // BroadcastChannel 可用时不依赖 storage；两者都不可用只影响跨标签同步。
    }
  }

  return {
    token,
    tokenExpiresAt,
    userInfo,
    permissions,
    isLoggedIn,
    username,
    nickname,
    avatar,
    roles,
    isSuperAdmin,
    applySession,
    setToken,
    setUserInfo,
    setPermissions,
    restoreUserInfo,
    logout,
    clearAuth
  }
})
