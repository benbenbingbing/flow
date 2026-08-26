import request from '@/utils/request'

/**
 * “关联内容”后端契约集中在此处，设计器不感知资源路由和响应包装差异。
 * 后端资源名保留 ui-view-compositions，页面上始终使用“关联内容”。
 */
export const uiCompositionApi = {
  list(ownerType, ownerId) {
    return request.get(`/ui-view-compositions/${ownerType}/${ownerId}`)
  },

  create(ownerType, ownerId, data) {
    return request.post(`/ui-view-compositions/${ownerType}/${ownerId}`, data)
  },

  update(ownerType, ownerId, id, data) {
    return request.post(
      `/ui-view-compositions/${ownerType}/${ownerId}/${id}/update`,
      data
    )
  },

  remove(ownerType, ownerId, id, expectedRevision, expectedOwnerRevision) {
    return request.post(
      `/ui-view-compositions/${ownerType}/${ownerId}/${id}/delete`,
      { expectedRevision, expectedOwnerRevision }
    )
  },

  validate(ownerType, ownerId, config) {
    return request.post(
      `/ui-view-compositions/${ownerType}/${ownerId}/validate`,
      { config }
    )
  },

  test(ownerType, ownerId, config, sourceRecordId = '') {
    return request.post(
      `/ui-view-compositions/${ownerType}/${ownerId}/test`,
      { config, sourceRecordId: sourceRecordId || undefined }
    )
  }
}
