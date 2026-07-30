import request from '@/utils/request'

export const entityVersionApi = {
  listConfigs(params = {}) {
    return request.get('/entity-versions/configs', { params })
  },
  getConfig(entityCode) {
    return request.get(`/entity-versions/configs/${entityCode}`)
  },
  saveConfig(entityCode, data) {
    return request.post(`/entity-versions/configs/${entityCode}/save`, data)
  },
  publishConfig(entityCode) {
    return request.post(`/entity-versions/configs/${entityCode}/publish`)
  },
  releases(entityCode) {
    return request.get(`/entity-versions/configs/${entityCode}/releases`)
  },
  simulate(entityCode, data) {
    return request.post(`/entity-versions/configs/${entityCode}/simulate`, data)
  },
  mutationCatalog() {
    return request.get('/entity-versions/mutation-catalog')
  },
  mutationCatalogOptions(type, params = {}) {
    return request.get('/entity-versions/mutation-catalog/options', {
      params: { type, ...params }
    })
  },
  recordVersions(entityCode, recordId) {
    return request.get(`/entity-versions/records/${entityCode}/${recordId}`)
  },
  recordVersion(entityCode, recordId, versionNo) {
    return request.get(`/entity-versions/records/${entityCode}/${recordId}/${versionNo}`)
  },
  compareRecordVersions(entityCode, recordId, fromVersion, toVersion) {
    return request.get(`/entity-versions/records/${entityCode}/${recordId}/compare`, {
      params: { from: fromVersion, to: toVersion }
    })
  }
}
