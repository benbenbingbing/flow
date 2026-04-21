import request from '@/utils/request'

// 获取服务列表
export const getServiceList = (params) => {
  return request.get('/service-orchestration/list', { params })
}

// 获取服务详情
export const getServiceById = (id) => {
  return request.get(`/service-orchestration/${id}`)
}

// 获取服务完整配置
export const getServiceConfig = (id) => {
  return request.get(`/service-orchestration/${id}/config`)
}

// 保存服务
export const saveService = (data) => {
  return request.post('/service-orchestration/save', data)
}

// 删除服务
export const deleteService = (id) => {
  return request.delete(`/service-orchestration/${id}`)
}

// 执行服务
export const executeService = (id, params) => {
  return request.post(`/service-orchestration/${id}/execute`, params)
}

// 获取执行日志
export const getExecutionLogs = (id, params) => {
  return request.get(`/service-orchestration/${id}/logs`, { params })
}

// 获取服务分类
export const getServiceCategories = () => {
  return request.get('/service-orchestration/categories')
}
