import request from '@/utils/request'

// 获取报表列表
export const getReportList = (params) => {
  return request.get('/report-engine/list', { params })
}

// 获取报表详情
export const getReportById = (id) => {
  return request.get(`/report-engine/${id}`)
}

// 获取报表完整配置
export const getReportConfig = (id) => {
  return request.get(`/report-engine/${id}/config`)
}

// 保存报表
export const saveReport = (data) => {
  return request.post('/report-engine/save', data)
}

// 删除报表
export const deleteReport = (id) => {
  return request.delete(`/report-engine/${id}`)
}

// 获取报表分类
export const getReportCategories = () => {
  return request.get('/report-engine/categories')
}

// 获取报表数据
export const getReportData = (id, params) => {
  return request.post(`/report-engine/${id}/data`, params)
}

// 执行SQL查询（测试）
export const executeSql = (data) => {
  return request.post('/report-engine/execute-sql', data)
}
