import request from '@/utils/request'

export const processApi = {
  // 获取流程分页列表
  getList(params = {}) {
    return request.get('/process', { params })
  },
  
  // 获取已发布的流程列表
  getPublishedList() {
    return request.get('/process/published')
  },

  // 获取未被实体绑定的流程列表（用于绑定流程）
  getUnboundList() {
    return request.get('/process/unbound')
  },
  
  // 获取所有可用于绑定的流程列表（包括当前已绑定的和未绑定的）
  getBindableList(currentProcessId = null) {
    // 只传递非空的 currentProcessId
    const params = (currentProcessId && currentProcessId.trim() !== '') 
      ? { currentProcessId } 
      : {}
    return request.get('/process/bindable', { params })
  },
  
  // 获取流程详情
  getById(id) {
    return request.get(`/process/${id}`)
  },
  
  // 创建流程
  create(data) {
    return request.post('/process', data)
  },
  
  // 更新流程
  update(id, data) {
    return request.post(`/process/${id}/update`, data)
  },
  
  // 删除流程
  delete(id) {
    return request.post(`/process/${id}/delete`)
  },
  
  // 发布流程（支持迁移清单和批次标记）
  publish(id, data = {}) {
    const payload = typeof data === 'string' ? { versionDescription: data } : data
    return request.post(`/process/${id}/publish`, payload)
  },
  
  // 禁用流程
  disable(id) {
    return request.post(`/process/${id}/disable`)
  },
  
  // 获取流程节点
  getNodes(processId) {
    return request.get(`/process/${processId}/nodes`)
  },
  
  // 保存节点
  saveNode(processId, data) {
    return request.post(`/process/${processId}/nodes`, data)
  },
  
  // 删除节点
  deleteNode(processId, id) {
    return request.post(`/process/${processId}/nodes/${id}`)
  },
  
  // ==================== 版本管理接口 ====================
  
  // 获取流程的所有版本历史
  getVersions(processId) {
    return request.get(`/process/${processId}/versions`)
  },
  
  // 获取指定版本的历史记录详情
  getVersionById(versionId) {
    return request.get(`/process/versions/${versionId}`)
  },
  
  // 回滚到指定版本
  rollbackToVersion(processId, versionId, reason) {
    return request.post(`/process/${processId}/rollback/${versionId}`, { reason })
  },
  
  // 删除版本
  deleteVersion(versionId) {
    return request.post(`/process/versions/${versionId}`)
  },
  
  // ==================== 流程实例接口 ====================
  
  /**
   * 获取流程实例的执行进度
   * @param processInstanceId 流程实例ID
   * @returns 流程进度信息，包含已完成节点、当前活动节点、BPMN XML等
   */
  getProcessProgress(processInstanceId) {
    return request.get(`/process-instance/${processInstanceId}/progress`)
  }
}
