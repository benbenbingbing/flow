import request from '@/utils/request'

export const entityListScopeApi = {
  getConfiguration(entityCode) {
    return request.get(`/entity-list-scopes/${entityCode}`)
  },

  createPolicy(data) {
    return request.post('/entity-list-scopes/policies', data)
  },

  updatePolicy(id, data) {
    return request.post(`/entity-list-scopes/policies/${id}/update`, data)
  },

  deletePolicy(id) {
    return request.post(`/entity-list-scopes/policies/${id}/delete`)
  },

  createBinding(data) {
    return request.post('/entity-list-scopes/bindings', data)
  },

  updateBinding(id, data) {
    return request.post(`/entity-list-scopes/bindings/${id}/update`, data)
  },

  deleteBinding(id) {
    return request.post(`/entity-list-scopes/bindings/${id}/delete`)
  },

  publish(entityCode, description = '') {
    return request.post(`/entity-list-scopes/${entityCode}/publish`, { description })
  },

  activateRelease(entityCode, version) {
    return request.post(`/entity-list-scopes/${entityCode}/releases/${version}/activate`)
  }
}
