import request from '@/utils/request'

/**
 * 获取待办列表
 * @param {Object} params - 查询参数 {pageNum, pageSize}
 */
export function getTodoList(params) {
  return request.get('/process-task/todo', { params })
}

/**
 * 获取已办列表
 * @param {Object} params - 查询参数 {pageNum, pageSize}
 */
export function getDoneList(params) {
  return request.get('/process-task/done', { params })
}

/**
 * 获取任务统计
 */
export function getStatistics() {
  return request.get('/process-task/statistics')
}

/**
 * 获取任务详情
 * @param {string} taskId - 任务ID
 */
export function getTaskDetail(taskId) {
  return request.get(`/process-task/detail/${taskId}`)
}

/**
 * 完成任务（审批）
 * @param {Object} data - 审批参数 {taskId, action, comment, transferTo}
 */
export function completeTask(data) {
  return request.post('/process-task/complete', data)
}

/**
 * 撤回流程
 * @param {Object} data - 撤回参数 {processInstanceId, reason}
 */
export function withdrawProcess(data) {
  return request.post('/process-task/withdraw', data)
}

/**
 * 获取流程历史
 * @param {string} processInstanceId - 流程实例ID
 */
export function getProcessHistory(processInstanceId) {
  return request.get(`/process-task/history/${processInstanceId}`)
}

/**
 * 获取我发起的流程列表
 * @param {Object} params - 查询参数 {pageNum, pageSize, processName}
 */
export function getMyStartedList(params) {
  return request.get('/process-instance/my-started', { params })
}

/**
 * 终止流程实例
 * @param {string} processInstanceId - 流程实例ID
 * @param {string} reason - 终止原因（可选）
 */
export function terminateProcess(processInstanceId, reason) {
  return request.post(`/process-instance/${processInstanceId}/terminate`, { reason })
}

// 统一导出
export const processTaskApi = {
  getTodoList,
  getDoneList,
  getStatistics,
  getTaskDetail,
  completeTask,
  withdrawProcess,
  getProcessHistory,
  getMyStartedList,
  terminateProcess
}
