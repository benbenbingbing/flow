import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    // 从 store 获取 token
    const userStore = useUserStore()
    const token = userStore.token
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const { code, message, data } = response.data
    if (code === 200) {
      return data
    }
    // 处理登录过期
    if (code === 401) {
      ElMessage.error(message || '登录已过期，请重新登录')
      const userStore = useUserStore()
      userStore.logout()
      window.location.href = '/login'
      return Promise.reject(new Error(message || '登录已过期'))
    }
    ElMessage.error(message || '请求失败')
    return Promise.reject(new Error(message))
  },
  (error) => {
    const { response } = error
    if (response) {
      // 处理 401 未授权
      if (response.status === 401) {
        ElMessage.error('登录已过期，请重新登录')
        const userStore = useUserStore()
        userStore.logout()
        window.location.href = '/login'
      } else {
        ElMessage.error(response.data?.message || '请求失败')
      }
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request
