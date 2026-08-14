import request from '@/utils/request'

const base = '/entity-mutation-policies/configs'

export const entityMutationPolicyApi = {
  list(params = {}) {
    return request.get(base, { params })
  },
  draft(entityCode) {
    return request.get(`${base}/${entityCode}/draft`)
  },
  saveDraft(entityCode, data, revision = data?.revision) {
    return request({
      url: `${base}/${entityCode}/draft`,
      method: 'PUT',
      data,
      headers: revision == null ? {} : { 'If-Match': String(revision) }
    })
  },
  releases(entityCode) {
    return request.get(`${base}/${entityCode}/releases`)
  },
  publish(entityCode, revision) {
    return request.post(`${base}/${entityCode}/releases`, { revision }, {
      headers: revision == null ? {} : { 'If-Match': String(revision) }
    })
  },
  catalog() {
    return request.get('/entity-mutation-policies/catalog')
  },
  catalogOptions(type, params = {}) {
    return request.get('/entity-mutation-policies/catalog/options', {
      params: { type, ...params }
    })
  }
}
