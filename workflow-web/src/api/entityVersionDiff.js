import request from '@/utils/request'

/**
 * 实体版本差异对比API
 */
export const entityVersionDiffApi = {
  /**
   * 获取即将发布的版本差异预览
   */
  getPendingPublishDiff(entityId) {
    return request.get(`/entity-version-diff/pending/${entityId}`)
  },

  /**
   * 比较两个版本之间的差异
   */
  compareVersions(entityId, versionFrom, versionTo) {
    return request.get(`/entity-version-diff/compare/${entityId}`, {
      params: { versionFrom, versionTo }
    })
  },

  /**
   * 比较指定版本与上一版本的差异
   */
  compareWithPrevious(entityId, version) {
    return request.get(`/entity-version-diff/compare/${entityId}/${version}`)
  }
}
