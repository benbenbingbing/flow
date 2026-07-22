import request from '@/utils/request'

export const entityListRuntimeApi = {
  getSchema(entityCode, listKey, scene = 'PAGE') {
    return request.get(`/entity-lists/${entityCode}/${listKey}/schema`, {
      params: { scene }
    })
  },

  query(entityCode, listKey, data = {}) {
    return request.post(`/entity-lists/${entityCode}/${listKey}/query`, data)
  },

  simulate(entityCode, listKey, data = {}) {
    return request.post(`/entity-lists/${entityCode}/${listKey}/scope-simulation`, data)
  }
}
