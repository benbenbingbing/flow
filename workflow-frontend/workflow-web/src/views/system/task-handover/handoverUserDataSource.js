import { isEligibleTarget } from './useTaskHandover.js'

/**
 * 将交接专用人员范围接入统一选择弹窗，避免普通用户接口漏掉禁用、已删除的来源人员。
 * 后端返回最多 200 个搜索结果，弹窗在结果内分页；姓名回显沿用所选记录，不再请求普通人员接口。
 */
export function createHandoverUserDataSource(api, { targetOnly = false, getSourceUserId = () => '' } = {}) {
  const knownUsers = new Map()
  let searchVersion = 0
  return {
    async list({ keyword = '', pageNum = 1, pageSize = 10 }) {
      const version = ++searchVersion
      const result = await api.users({ keyword: String(keyword).trim(), targetOnly })
      const rows = (Array.isArray(result) ? result : [])
        .filter(user => !targetOnly || isEligibleTarget(user, getSourceUserId()))
        .map(user => ({
          ...user,
          id: String(user.id),
          name: user.nickname || user.username || String(user.id),
          code: user.username || String(user.id),
          entityType: 'USER'
        }))
      // 旧搜索可以结束，但不能覆盖最新人员快照；已选择的人在后续搜索后仍能显示姓名。
      if (version === searchVersion) {
        for (const user of rows) knownUsers.set(user.id, user)
      }
      const start = (pageNum - 1) * pageSize
      return { records: rows.slice(start, start + pageSize), total: rows.length }
    },
    async batch(values, valueKey = 'id') {
      return values.map(value => valueKey === 'id'
        ? knownUsers.get(String(value))
        : [...knownUsers.values()].find(user => user.code === String(value))).filter(Boolean)
    }
  }
}
