import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const TOKEN_STORAGE_KEY = 'auth.accessToken'
const TOKEN_EXPIRES_STORAGE_KEY = 'auth.tokenExpiresAt'
const AUTH_SYNC_STORAGE_KEY = 'auth.session.sync'
const AUTH_CHANNEL_NAME = 'flow-auth-session'
let ephemeralUserStoreRuntime = false

/**
 * Embed 独立入口在任何 store 实例化之前调用。该开关单向生效，确保 iframe 中
 * 即使复用普通 userStore，也不会读取、删除或写入管理端认证存储。
 */
export function enableEphemeralUserStoreRuntime() {
  ephemeralUserStoreRuntime = true
}

function sessionStorageValue(key) {
  if (ephemeralUserStoreRuntime) return ''
  try {
    return globalThis.sessionStorage?.getItem(key) || ''
  } catch {
    return ''
  }
}

function writeSessionStorage(key, value) {
  if (ephemeralUserStoreRuntime) return
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
  if (ephemeralUserStoreRuntime) return
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
  /** Embed 原生运行时的只读映射身份，仅存在于 iframe 当前 Pinia 内存。 */
  const ephemeralRuntimeIdentity = ref(false)
  const ephemeralRuntimeIsSuperAdmin = ref(false)

  // Getters
  const isLoggedIn = computed(() => !!token.value || ephemeralRuntimeIdentity.value)
  const username = computed(() => userInfo.value?.username || '')
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')
  const avatar = computed(() => userInfo.value?.avatar || '')
  const roles = computed(() => userInfo.value?.roles || [])
  const isSuperAdmin = computed(() => ephemeralRuntimeIsSuperAdmin.value || roles.value.some(role => {
    const code = typeof role === 'string' ? role : role?.roleCode
    return code === 'super_admin'
  }))

  // Actions
  /**
   * 保存后端签发的完整登录会话结果。
   */
  function applySession(session) {
    ephemeralRuntimeIdentity.value = false
    ephemeralRuntimeIsSuperAdmin.value = false
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
    ephemeralRuntimeIdentity.value = false
    ephemeralRuntimeIsSuperAdmin.value = false
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
    ephemeralRuntimeIdentity.value = false
    ephemeralRuntimeIsSuperAdmin.value = false
    userInfo.value = sanitizeUserInfo(info)
    if (ephemeralUserStoreRuntime) return
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
    ephemeralRuntimeIdentity.value = false
    permissions.value = perms || []
    if (ephemeralUserStoreRuntime) return
    localStorage.setItem('permissions', JSON.stringify(permissions.value))
  }

  /**
   * 初始化 Embed 映射用户。该方法刻意不调用普通登录 action，也不访问任何
   * Web Storage；opaque Embed token 仍只归共享 request transport 持有。
   */
  function applyEphemeralRuntimeIdentity(identity = {}) {
    token.value = ''
    tokenExpiresAt.value = ''
    userInfo.value = sanitizeUserInfo({
      username: identity.username || '',
      nickname: identity.nickname || identity.displayName || identity.username || '',
      displayName: identity.displayName || identity.nickname || identity.username || '',
      roles: Array.isArray(identity.roles) ? [...identity.roles] : []
    })
    permissions.value = Array.isArray(identity.permissions)
      ? [...identity.permissions]
      : []
    ephemeralRuntimeIsSuperAdmin.value = identity.isSuperAdmin === true
    ephemeralRuntimeIdentity.value = true
  }

  /** 只清理由上一个方法建立的内存身份，绝不破坏普通管理端登录存储。 */
  function clearEphemeralRuntimeIdentity() {
    if (!ephemeralRuntimeIdentity.value) return
    token.value = ''
    tokenExpiresAt.value = ''
    userInfo.value = null
    permissions.value = []
    ephemeralRuntimeIsSuperAdmin.value = false
    ephemeralRuntimeIdentity.value = false
  }

  /**
   * 从 localStorage 恢复非敏感用户信息和权限。
   */
  function restoreUserInfo() {
    if (ephemeralUserStoreRuntime) return
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
    ephemeralRuntimeIdentity.value = false
    ephemeralRuntimeIsSuperAdmin.value = false
    if (ephemeralUserStoreRuntime) return
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
    !ephemeralUserStoreRuntime
    && typeof window !== 'undefined'
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

  if (!ephemeralUserStoreRuntime && typeof window !== 'undefined') {
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
    if (ephemeralUserStoreRuntime) return
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
    ephemeralRuntimeIdentity,
    applySession,
    applyEphemeralRuntimeIdentity,
    clearEphemeralRuntimeIdentity,
    setToken,
    setUserInfo,
    setPermissions,
    restoreUserInfo,
    logout,
    clearAuth
  }
})
