import request from '@/utils/request'

/**
 * 保存流程状态映射配置
 */
export function saveStatusMappings(processConfigId, data) {
  return request.post(`/entity-flow-status/save/${processConfigId}`, data)
}

/**
 * 查询流程的状态映射配置
 */
export function getStatusMappings(processConfigId) {
  return request.get(`/entity-flow-status/list/${processConfigId}`)
}

/**
 * 根据流程标识查询
 */
export function getStatusMappingsByProcessKey(processKey) {
  return request.get(`/entity-flow-status/list/by-key/${processKey}`)
}

/**
 * 删除流程的状态映射配置
 */
export function deleteStatusMappings(processConfigId) {
  return request.delete(`/entity-flow-status/delete/${processConfigId}`)
}
