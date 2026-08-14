import request from '@/utils/request'

/** 独立实体关系管理 API。 */
export const entityRelationApi = {
  list(entityId) {
    return request.get(`/entity/${entityId}/relations`)
  },

  get(entityId, relationId) {
    return request.get(`/entity/${entityId}/relations/${relationId}`)
  },

  create(entityId, data) {
    return request.post(`/entity/${entityId}/relations`, data)
  },

  update(entityId, relationId, data) {
    return request({
      url: `/entity/${entityId}/relations/${relationId}`,
      method: 'PUT',
      data
    })
  },

  delete(entityId, relationId) {
    return request({
      url: `/entity/${entityId}/relations/${relationId}`,
      method: 'DELETE'
    })
  }
}
