import request from '@/utils/request'

/**
 * 登录
 * @param {Object} data - 登录参数 {username, password}
 */
export function login(data) {
  return request.post('/auth/login', data)
}

/**
 * 获取当前登录用户信息
 */
export function getCurrentUser() {
  return request.get('/auth/current')
}

/**
 * 退出登录
 */
export function logout() {
  return request.post('/auth/logout')
}
