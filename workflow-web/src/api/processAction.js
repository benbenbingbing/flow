import request from '@/utils/request'

/**
 * 流程动作管理API
 */
export const processActionApi = {
  /**
   * 查询流程配置下所有草稿动作
   */
  findDraftActions(processConfigId) {
    return request.get(`/process-actions/process/${processConfigId}`)
  },

  /**
   * 查询顺序流下所有草稿动作
   */
  findDraftActionsBySequenceFlow(processConfigId, sequenceFlowId) {
    return request.get(`/process-actions/process/${processConfigId}/flow/${sequenceFlowId}`)
  },

  findDraftActionsByBinding(processConfigId, scopeType, elementId) {
    return request.get(`/process-actions/process/${processConfigId}/binding`, {
      params: { scopeType, elementId }
    })
  },

  timingOptions(scopeType, bpmnType) {
    return request.get('/process-actions/timing-options', {
      params: { scopeType, bpmnType }
    })
  },

  /**
   * 查询版本下所有已发布动作
   */
  findPublishedActions(versionId) {
    return request.get(`/process-actions/version/${versionId}`)
  },

  /**
   * 查询版本下特定顺序流的动作
   */
  findPublishedActionsBySequenceFlow(versionId, sequenceFlowId) {
    return request.get(`/process-actions/version/${versionId}/flow/${sequenceFlowId}`)
  },

  /**
   * 保存动作
   */
  saveAction(data) {
    return request.post('/process-actions', data)
  },

  /**
   * 删除动作
   */
  deleteAction(actionId) {
    return request.post(`/process-actions/${actionId}`)
  },

  /**
   * 更新排序
   */
  updateSortOrder(actionIds) {
    return request.post('/process-actions/sort', actionIds)
  },

  /**
   * 切换启用状态
   */
  toggleEnabled(actionId) {
    return request.post(`/process-actions/${actionId}/toggle`)
  },

  /**
   * 获取已注册的流程动作处理器列表
   */
  listHandlers(processConfigId) {
    return request.get('/process-action-handlers', {
      params: { processConfigId }
    })
  },

  listHandlerConfigs() {
    return request.get('/process-action-handlers/configs')
  },

  saveHandlerConfig(beanName, data) {
    return request.post(`/process-action-handlers/configs/${encodeURIComponent(beanName)}`, data)
  },

  findExecutions(processInstanceId) {
    return request.get(`/process-action-executions/process/${processInstanceId}`)
  },

  retryExecution(executionId) {
    return request.post(`/process-action-executions/${executionId}/retry`)
  }
}
