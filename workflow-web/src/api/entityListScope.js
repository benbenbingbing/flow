import request from '@/utils/request'

export const entityListScopeApi = {
  getConfiguration(entityCode) {
    return request.get(`/entity-list-scopes/${entityCode}`)
  },

  createPolicy(data) {
    return request.post('/entity-list-scopes/policies', data)
  },

  updatePolicy(id, data) {
    return request.put(`/entity-list-scopes/policies/${id}`, data)
  },

  deletePolicy(id) {
    return request.delete(`/entity-list-scopes/policies/${id}`)
  },

  createBinding(data) {
    return request.post('/entity-list-scopes/bindings', data)
  },

  updateBinding(id, data) {
    return request.put(`/entity-list-scopes/bindings/${id}`, data)
  },

  deleteBinding(id) {
    return request.delete(`/entity-list-scopes/bindings/${id}`)
  },

  publish(entityCode, description = '') {
    return request.post(`/entity-list-scopes/${entityCode}/publish`, { description })
  },

  activateRelease(entityCode, version) {
    return request.post(`/entity-list-scopes/${entityCode}/releases/${version}/activate`)
  }
}
