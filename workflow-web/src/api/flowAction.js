import request from '@/utils/request'

/**
 * 流程动作管理API
 */
export const flowActionApi = {
  /**
   * 查询流程配置下所有草稿动作
   */
  findDraftActions(processConfigId) {
    return request.get(`/flow-actions/process/${processConfigId}`)
  },

  /**
   * 查询顺序流下所有草稿动作
   */
  findDraftActionsBySequenceFlow(processConfigId, sequenceFlowId) {
    return request.get(`/flow-actions/process/${processConfigId}/flow/${sequenceFlowId}`)
  },

  /**
   * 查询版本下所有已发布动作
   */
  findPublishedActions(versionId) {
    return request.get(`/flow-actions/version/${versionId}`)
  },

  /**
   * 查询版本下特定顺序流的动作
   */
  findPublishedActionsBySequenceFlow(versionId, sequenceFlowId) {
    return request.get(`/flow-actions/version/${versionId}/flow/${sequenceFlowId}`)
  },

  /**
   * 保存动作
   */
  saveAction(data) {
    return request.post('/flow-actions', data)
  },

  /**
   * 删除动作
   */
  deleteAction(actionId) {
    return request.delete(`/flow-actions/${actionId}`)
  },

  /**
   * 更新排序
   */
  updateSortOrder(actionIds) {
    return request.post('/flow-actions/sort', actionIds)
  },

  /**
   * 切换启用状态
   */
  toggleEnabled(actionId) {
    return request.post(`/flow-actions/${actionId}/toggle`)
  }
}
