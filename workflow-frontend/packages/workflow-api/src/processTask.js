
/** 为指定 transport 创建接口集合，不在包内持有应用单例。 */
export function createProcessTaskApi(request) {

/**
 * 获取待办列表
 * @param {Object} params - 查询参数 {pageNum, pageSize}
 */
function getTodoList(params) {
  return request.get('/process-task/todo', { params })
}

/**
 * 获取已办列表
 * @param {Object} params - 查询参数 {pageNum, pageSize}
 */
function getDoneList(params) {
  return request.get('/process-task/done', { params })
}

/**
 * 获取任务统计
 */
function getStatistics() {
  return request.get('/process-task/statistics')
}

/**
 * 获取任务详情
 * @param {string} taskId - 任务ID
 */
function getTaskDetail(taskId) {
  return request.get(`/process-task/detail/${taskId}`)
}

/**
 * 完成任务（审批）
 * @param {Object} data - 审批参数 {taskId, action, comment, transferTo}
 */
function completeTask(data, config = {}) {
  return request.post('/process-task/complete', data, config)
}

/**
 * 按当前审批动作和尚未提交的表单数据预览实际命中的下一审批节点。
 */
function previewNextApproval(taskId, data = {}, config = {}) {
  return request.post(
    `/process-task/${encodeURIComponent(taskId)}/next-approval-preview`,
    data,
    { silentError: true, ...config }
  )
}

/**
 * 查询预览返回的受控人员范围。scopeKey 只能来自 preview 响应。
 */
function getNextApproverOptions(taskId, data = {}) {
  return request.post(
    `/process-task/${encodeURIComponent(taskId)}/next-approver-options`,
    data,
    { silentError: true }
  )
}

/**
 * 认领候选任务
 * @param {string} taskId - 任务ID
 */
function claimTask(taskId) {
  return request.post(`/process-task/claim/${taskId}`)
}

function getTaskSla(taskId) {
  return request.get(`/tasks/${taskId}/sla`)
}

function acknowledgeTask(taskId) {
  return request.post(`/tasks/${taskId}/acknowledge`)
}

function pauseTaskSla(taskId, data) {
  return request.post(`/tasks/${taskId}/sla/pause`, data)
}

function resumeTaskSla(taskId) {
  return request.post(`/tasks/${taskId}/sla/resume`)
}

function getTaskOperations(taskId) {
  return request.get(`/tasks/${taskId}/operations`)
}

/** 实例级能力不要求发起人同时是当前任务办理人。 */
function getProcessOperations(processInstanceId) {
  return request.get(`/process-instance/${encodeURIComponent(processInstanceId)}/operations`)
}

function previewAddSign(taskId, userIds, type = 'PARALLEL') {
  return request.get(`/tasks/${taskId}/add-sign-preview`, { params: { userIds, type } })
}

function addSignTask(taskId, data) {
  return request.post(`/tasks/${taskId}/add-sign`, data)
}

function cancelAddSign(addSignId) {
  return request.post(`/add-sign/${addSignId}/cancel`)
}

function ccTask(taskId, data) {
  return request.post(`/tasks/${taskId}/cc`, data)
}

function getMyCcList(params) {
  return request.get('/process-cc/my-cc', { params })
}

function markCcRead(ccId) {
  return request.post(`/process-cc/read/${ccId}`)
}

/**
 * 撤回流程
 * @param {Object} data - 撤回参数 {processInstanceId, reason}
 */
function withdrawProcess(data) {
  return request.post('/process-task/withdraw', data)
}

/**
 * 获取流程历史
 * @param {string} processInstanceId - 流程实例ID
 */
function getProcessHistory(processInstanceId) {
  return request.get(`/process-task/history/${processInstanceId}`)
}

/**
 * 获取我发起的流程列表
 * @param {Object} params - 查询参数 {pageNum, pageSize, processName, startDate, endDate}
 */
function getMyStartedList(params) {
  return request.get('/process-instance/my-started', { params, silentError: true })
}

/**
 * 终止流程实例
 * @param {string} processInstanceId - 流程实例ID
 * @param {string} reason - 终止原因（可选）
 */
function terminateProcess(processInstanceId, reason) {
  return request.post(`/process-instance/${processInstanceId}/terminate`, { reason })
}

/**
 * 驳回任务（驳回到发起人）
 * @param {string} taskId - 任务ID
 * @param {Object} data - {comment: "驳回原因"}
 */
function rejectTask(taskId, data) {
  return request.post(`/process-rollback/reject/${taskId}`, data)
}

/**
 * 重新提交流程（发起人在被驳回后使用）
 * @param {string} processInstanceId - 流程实例ID
 * @param {Object} data - {formData: {}, comment: "重新提交备注"}
 */
function resubmitProcess(processInstanceId, data) {
  return request.post(`/process-rollback/resubmit/${processInstanceId}`, data)
}

/**
 * 检查流程是否被驳回
 * @param {string} processInstanceId - 流程实例ID
 */
function checkRejectedStatus(processInstanceId) {
  return request.get(`/process-rollback/rejected-status/${processInstanceId}`)
}

// 统一导出
const processTaskApi = {
  getTodoList,
  getDoneList,
  getStatistics,
  getTaskDetail,
  completeTask,
  previewNextApproval,
  getNextApproverOptions,
  claimTask,
  getTaskSla,
  acknowledgeTask,
  pauseTaskSla,
  resumeTaskSla,
  getTaskOperations, getProcessOperations,
  previewAddSign,
  addSignTask,
  cancelAddSign,
  ccTask,
  getMyCcList,
  markCcRead,
  withdrawProcess,
  getProcessHistory,
  getMyStartedList,
  terminateProcess,
  rejectTask,
  resubmitProcess,
  checkRejectedStatus
}

return { getTodoList, getDoneList, getStatistics, getTaskDetail, completeTask, previewNextApproval, getNextApproverOptions, claimTask, getTaskSla, acknowledgeTask, pauseTaskSla, resumeTaskSla, getTaskOperations, getProcessOperations, previewAddSign, addSignTask, cancelAddSign, ccTask, getMyCcList, markCcRead, withdrawProcess, getProcessHistory, getMyStartedList, terminateProcess, rejectTask, resubmitProcess, checkRejectedStatus, processTaskApi }
}
