import request from '@/utils/request'

const legacyFallbackStatuses = new Set([404, 405])

async function withLegacyFallback(primary, fallback) {
  try {
    return await primary()
  } catch (error) {
    if (!legacyFallbackStatuses.has(Number(error?.status))) throw error
    return fallback()
  }
}

function pageParams(params = {}) {
  return {
    pageNum: params.pageNum ?? 1,
    pageSize: params.pageSize ?? 20,
    ...params
  }
}

export const entityVersionApi = {
  listConfigs(params = {}) {
    return request.get('/entity-versions/configs', { params })
  },
  getConfig(entityCode) {
    return withLegacyFallback(
      () => request.get(`/entity-versions/configs/${entityCode}/draft`, {
        silentError: true
      }),
      () => request.get(`/entity-versions/configs/${entityCode}`)
    )
  },
  getDraft(entityCode) {
    return this.getConfig(entityCode)
  },
  saveConfig(entityCode, data, revision = data?.revision) {
    return withLegacyFallback(
      () => request({
        url: `/entity-versions/configs/${entityCode}/draft`,
        method: 'PUT',
        data,
        headers: revision == null ? {} : { 'If-Match': String(revision) },
        silentError: true
      }),
      () => request.post(`/entity-versions/configs/${entityCode}/save`, data)
    )
  },
  saveDraft(entityCode, data, revision = data?.revision) {
    return this.saveConfig(entityCode, data, revision)
  },
  validateDraft(entityCode, data) {
    return request.post(`/entity-versions/configs/${entityCode}/validate`, data)
  },
  scopePreview(entityCode, draft, recordId = '') {
    return request.post(`/entity-versions/configs/${entityCode}/scope-preview`, {
      draft,
      ...(String(recordId || '').trim()
        ? { recordId: String(recordId).trim() }
        : {})
    })
  },
  publishConfig(entityCode, data = {}) {
    return withLegacyFallback(
      () => request.post(`/entity-versions/configs/${entityCode}/releases`, data, {
        headers: data?.revision == null ? {} : { 'If-Match': String(data.revision) },
        silentError: true
      }),
      () => request.post(`/entity-versions/configs/${entityCode}/publish`)
    )
  },
  releases(entityCode, params = {}) {
    return request.get(`/entity-versions/configs/${entityCode}/releases`, {
      params: pageParams(params)
    })
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
  captureRecordVersion(entityCode, recordId, data = {}, idempotencyKey) {
    return request.post(
      `/entity-versions/records/${entityCode}/${recordId}/captures`,
      data,
      {
        headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}
      }
    )
  },
  recordVersions(entityCode, recordId, params = {}) {
    return request.get(`/entity-versions/records/${entityCode}/${recordId}`, {
      params: pageParams(params)
    })
  },
  recordVersion(entityCode, recordId, versionNo) {
    return request.get(`/entity-versions/records/${entityCode}/${recordId}/${versionNo}`)
  },
  compareRecordVersions(entityCode, recordId, fromVersion, toVersion, params = {}) {
    return request.get(`/entity-versions/records/${entityCode}/${recordId}/compare`, {
      params: {
        from: fromVersion,
        to: toVersion,
        ...params
      }
    })
  },
  comparisonRows(entityCode, recordId, fromVersion, toVersion, nodeCode, params = {}) {
    return request.get(
      `/entity-versions/records/${entityCode}/${recordId}/compare/datasets/${nodeCode}/rows`,
      {
        params: {
          from: fromVersion,
          to: toVersion,
          ...pageParams(params)
        }
      }
    )
  },
  snapshotRows(entityCode, recordId, versionNo, nodeCode, params = {}) {
    return request.get(
      `/entity-versions/records/${entityCode}/${recordId}/${versionNo}/datasets/${nodeCode}/rows`,
      { params: pageParams(params) }
    )
  }
}
