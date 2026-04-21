import request from '@/utils/request'

// 获取视图列表
export const getViewList = (params) => {
  return request.get('/view-engine/list', { params })
}

// 根据实体获取视图列表
export const getViewsByEntity = (entityCode) => {
  return request.get(`/view-engine/entity/${entityCode}`)
}

// 获取默认视图
export const getDefaultView = (entityCode) => {
  return request.get(`/view-engine/entity/${entityCode}/default`)
}

// 获取视图详情
export const getViewById = (id) => {
  return request.get(`/view-engine/${id}`)
}

// 获取视图完整配置
export const getViewConfig = (id) => {
  return request.get(`/view-engine/${id}/config`)
}

// 保存视图
export const saveView = (data) => {
  return request.post('/view-engine/save', data)
}

// 删除视图
export const deleteView = (id) => {
  return request.delete(`/view-engine/${id}`)
}

// 设置默认视图
export const setDefaultView = (id, entityCode) => {
  return request.post(`/view-engine/${id}/set-default?entityCode=${entityCode}`)
}

// 生成默认视图
export const generateDefaultView = (entityCode) => {
  return request.post(`/view-engine/generate-default/${entityCode}`)
}
