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

  getExtensionOptions() {
    return request.get('/entity-list-config/extension-options')
  },

  /**
   * 保存/更新列表配置
   */
  save(data) {
    return request.post('/entity-list-config/save', data)
  },

  patchMetadata(id, data) {
    return request.post(`/entity-list-config/${id}`, data)
  },

  createField(id, field) {
    return request.post(`/entity-list-config/${id}/fields`, { field })
  },

  patchField(id, fieldId, expectedRevision, field) {
    const clearFields = [
      'dataSourceId',
      'templateId',
      'templateVersion',
      'localOverridesDocument'
    ].filter(key => field[key] == null)
    return request.post(`/entity-list-config/${id}/fields/${fieldId}/patch`, {
      expectedRevision,
      field,
      clearFields
    })
  },

  reorderField(id, fieldId, data) {
    return request.post(`/entity-list-config/${id}/fields/${fieldId}/order`, data)
  },

  deleteField(id, fieldId, expectedRevision) {
    return request.post(`/entity-list-config/${id}/fields/${fieldId}/delete`, {
      params: { expectedRevision }
    })
  },

  getDiff(id) {
    return request.get(`/entity-list-config/${id}/diff`)
  },

  publish(id, description = '') {
    return request.post(`/entity-list-config/${id}/publish`, { description })
  },

  getReleases(id) {
    return request.get(`/entity-list-config/${id}/releases`)
  },

  activateRelease(id, releaseId) {
    return request.post(`/entity-list-config/${id}/releases/${releaseId}/activate`)
  },

  createAction(id, data) {
    return request.post(`/entity-list-config/${id}/actions`, data)
  },

  patchAction(id, actionId, data) {
    return request.post(`/entity-list-config/${id}/actions/${actionId}`, data)
  },

  deleteAction(id, actionId, expectedRevision) {
    return request.post(`/entity-list-config/${id}/actions/${actionId}`, {
      params: { expectedRevision }
    })
  },

  createScene(id, data) {
    return request.post(`/entity-list-config/${id}/scenes`, data)
  },

  getScenes(id) {
    return request.get(`/entity-list-config/${id}/scenes`)
  },

  patchScene(id, sceneId, data) {
    return request.post(`/entity-list-config/${id}/scenes/${sceneId}`, data)
  },

  deleteScene(id, sceneId, expectedRevision) {
    return request.post(`/entity-list-config/${id}/scenes/${sceneId}`, {
      params: { expectedRevision }
    })
  },

  previewActionRule(id, data) {
    return request.post(`/entity-list-config/${id}/action-rule/preview`, data)
  },

  /**
   * 删除列表配置
   */
  delete(id) {
    return request.post(`/entity-list-config/delete/${id}`)
  }
}
