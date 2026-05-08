import request from '@/utils/request'

/**
 * 实体列表配置API
 */
export const entityListConfigApi = {
  /**
   * 查询实体的所有列表配置
   */
  getByEntityId(entityId) {
    return request.get(`/entity-list-config/entity/${entityId}`)
  },

  /**
   * 根据ID查询配置（含字段）
   */
  getById(id) {
    return request.get(`/entity-list-config/${id}`)
  },

  /**
   * 保存/更新列表配置
   */
  save(data) {
    return request.post('/entity-list-config/save', data)
  },

  /**
   * 删除列表配置
   */
  delete(id) {
    return request.delete(`/entity-list-config/delete/${id}`)
  }
}
