import request from '@/utils/request'

/**
 * 实体发布版本历史API
 */
export const entityPublishHistoryApi = {
  /**
   * 获取实体的版本历史列表
   */
  getVersionHistory(entityId) {
    return request.get(`/entity-publish-history/entity/${entityId}`)
  },

  /**
   * 分页获取实体版本历史，用于弹窗滚动加载。
   */
  getVersionHistoryPage(entityId, params = {}) {
    return request.get(`/entity-publish-history/entity/${entityId}/page`, {
      params: {
        pageNum: params.pageNum ?? 1,
        pageSize: params.pageSize ?? 5
      }
    })
  },

  /**
   * 获取实体的最新版本
   */
  getLatestVersion(entityId) {
    return request.get(`/entity-publish-history/entity/${entityId}/latest`)
  },

  /**
   * 获取版本详情
   */
  getVersionDetail(historyId) {
    return request.get(`/entity-publish-history/${historyId}`)
  },

  /**
   * 比较两个版本
   */
  compareVersions(version1, version2) {
    return request.get('/entity-publish-history/compare', {
      params: { version1, version2 }
    })
  }
}
