import request from '@/utils/request'

// ==================== 待办任务 ====================

export const getTodoList = (params) => {
  return request.get('/process-center/todo/list', { params })
}

export const markTaskAsRead = (taskId) => {
  return request.post(`/process-center/todo/read/${taskId}`)
}

// ==================== 已办任务 ====================

export const getDoneList = (params) => {
  return request.get('/process-center/done/list', { params })
}

// ==================== 抄送/知会 ====================

export const getCcList = (params) => {
  return request.get('/process-center/cc/list', { params })
}

export const markCcAsRead = (id) => {
  return request.post(`/process-center/cc/read/${id}`)
}

// ==================== 常用意见 ====================

export const getCommonOpinions = (params) => {
  return request.get('/process-center/common-opinions', { params })
}

export const saveCommonOpinion = (data) => {
  return request.post('/process-center/common-opinions', data)
}

export const deleteCommonOpinion = (id) => {
  return request.delete(`/process-center/common-opinions/${id}`)
}

export const useCommonOpinion = (id) => {
  return request.post(`/process-center/common-opinions/use/${id}`)
}

// ==================== 草稿箱 ====================

export const getDraftList = (params) => {
  return request.get('/process-center/draft/list', { params })
}

export const saveDraft = (data) => {
  return request.post('/process-center/draft/save', data)
}

export const deleteDraft = (id) => {
  return request.delete(`/process-center/draft/${id}`)
}

export const submitDraft = (id) => {
  return request.post(`/process-center/draft/submit/${id}`)
}

// ==================== 统计 ====================

export const getStatistics = () => {
  return request.get('/process-center/statistics')
}
