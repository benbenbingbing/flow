import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 用户状态管理
 */
export const useUserStore = defineStore('user', () => {
  // State
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(null)
  const permissions = ref([])

  // Getters
  const isLoggedIn = computed(() => !!token.value)
  const username = computed(() => userInfo.value?.username || '')
  const nickname = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')
  const avatar = computed(() => userInfo.value?.avatar || '')
  const roles = computed(() => userInfo.value?.roles || [])

  // Actions
  /**
   * 设置token
   */
  function setToken(newToken) {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  /**
   * 设置用户信息
   */
  function setUserInfo(info) {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  /**
   * 设置权限码集合
   */
  function setPermissions(perms) {
    permissions.value = perms || []
    localStorage.setItem('permissions', JSON.stringify(permissions.value))
  }

  /**
   * 从localStorage恢复用户信息
   */
  function restoreUserInfo() {
    const stored = localStorage.getItem('userInfo')
    if (stored) {
      try {
        userInfo.value = JSON.parse(stored)
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
   * 退出登录
   */
  function logout() {
    token.value = ''
    userInfo.value = null
    permissions.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('permissions')
  }

  /**
   * 清除登录状态
   */
  function clearAuth() {
    logout()
  }

  return {
    token,
    userInfo,
    permissions,
    isLoggedIn,
    username,
    nickname,
    avatar,
    roles,
    setToken,
    setUserInfo,
    setPermissions,
    restoreUserInfo,
    logout,
    clearAuth
  }
})
