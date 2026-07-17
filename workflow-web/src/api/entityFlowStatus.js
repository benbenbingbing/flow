import request from '@/utils/request'

/**
 * 保存流程状态映射配置
 */
export function saveStatusMappings(processConfigId, data) {
  return request.put(`/process-entity-status-mappings/process/${processConfigId}`, data)
}

/**
 * 查询流程的状态映射配置
 */
export function getStatusMappings(processConfigId) {
  return request.get(`/process-entity-status-mappings/process/${processConfigId}`)
}

/**
 * 根据流程标识查询
 */
export function getStatusMappingsByProcessKey(processKey) {
  return request.get(`/process-entity-status-mappings/process-key/${processKey}`)
}

/**
 * 删除流程的状态映射配置
 */
export function deleteStatusMappings(processConfigId) {
  return request.delete(`/process-entity-status-mappings/process/${processConfigId}`)
}
