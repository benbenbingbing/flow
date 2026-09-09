import request from '@/utils/request'

const LEGACY_SAVE_FALLBACK_STATUSES = new Set([404, 405])

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
    return request.get(`/entity-versions/configs/${entityCode}/current`)
  },
  /**
   * 使用当前 revision 保存唯一配置并立即生效。
   * 滚动部署期间仅在新 PUT 路由不存在时兼容旧 Controller；确认 N 版已覆盖
   * 所有环境后，应在 N+1 删除此前端 fallback，并在 active release 完成最终
   * 投影与对账后停止兼容读取；后端仍继续兼容写入。
   * N+2 改为 config-only 读写并保留旧 schema，完成滚动且等待所有 N+1 Pod 和
   * 在途事务退出；N+3 才物理 contract，删除旧表、旧字段和发布权限。
   */
  async saveConfig(entityCode, data, revision = data?.revision) {
    const expectedRevision = revision ?? 0
    try {
      return await request({
        url: `/entity-versions/configs/${entityCode}/current`,
        method: 'PUT',
        data,
        headers: { 'If-Match': String(expectedRevision) },
        silentError: true
      })
    } catch (error) {
      if (!LEGACY_SAVE_FALLBACK_STATUSES.has(Number(error?.status))) {
        throw error
      }
    }

    const saved = await request({
      url: `/entity-versions/configs/${entityCode}/draft`,
      method: 'POST',
      data,
      headers: { 'If-Match': String(expectedRevision) },
      silentError: true
    })
    if (saved?.revision == null) {
      throw new Error('旧版配置保存未返回 revision，无法安全切换运行配置')
    }
    return request({
      url: `/entity-versions/configs/${entityCode}/releases`,
      method: 'POST',
      headers: { 'If-Match': String(saved.revision) },
      silentError: true
    })
  },
  validateConfig(entityCode, data) {
    return request.post(`/entity-versions/configs/${entityCode}/validate`, data)
  },
  scopePreview(entityCode, configuration, recordId = '') {
    return request.post(`/entity-versions/configs/${entityCode}/scope-preview`, {
      ...(configuration || {}),
      ...(String(recordId || '').trim()
        ? { recordId: String(recordId).trim() }
        : {})
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
  recordCapabilities(entityCode) {
    return request.get(
      `/entity-versions/records/${entityCode}/capabilities`,
      { silentError: true }
    )
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
