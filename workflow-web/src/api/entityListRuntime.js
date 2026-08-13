import request from '@/utils/request'

export const entityListRuntimeApi = {
  getSchema(entityCode, listKey, scene = 'PAGE', release = {}) {
    return request.get(`/entity-lists/${entityCode}/${listKey}/schema`, {
      params: {
        scene,
        releaseId: release.releaseId || undefined,
        releaseVersion: release.releaseVersion ?? undefined,
        releaseResolutionToken:
          release.releaseResolutionToken || undefined
      }
    })
  },

  query(entityCode, listKey, data = {}) {
    return request.post(`/entity-lists/${entityCode}/${listKey}/query`, data)
  },

  simulate(entityCode, listKey, data = {}) {
    return request.post(`/entity-lists/${entityCode}/${listKey}/scope-simulation`, data)
  }
}
