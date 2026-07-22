import request from '@/utils/request'

/**
 * 查询实体的状态列表
 */
export function getEntityStatusList(entityCode) {
  return request.get(`/entity-status/list/${entityCode}`)
}

/**
 * 根据分类查询
 */
export function getEntityStatusByCategory(entityCode, category) {
  return request.get(`/entity-status/list/${entityCode}/${category}`)
}

/**
 * 保存实体状态
 */
export function saveEntityStatus(data) {
  return request.post('/entity-status/save', data)
}

/**
 * 批量保存实体状态
 */
export function saveEntityStatusList(entityCode, statuses) {
  return request.post(`/entity-status/save-list/${entityCode}`, statuses)
}

/**
 * 删除实体状态
 */
export function deleteEntityStatus(id) {
  return request.post(`/entity-status/delete/${id}`)
}
