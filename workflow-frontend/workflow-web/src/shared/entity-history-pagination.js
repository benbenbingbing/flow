/**
 * 判断滚动容器是否进入历史分页的触底阈值。
 *
 * @param {Pick<HTMLElement, 'scrollHeight' | 'scrollTop' | 'clientHeight'> | null} target 滚动容器
 * @param {number} threshold 距底部阈值（像素）
 * @returns {boolean} 是否应加载下一页
 */
export function isEntityHistoryNearBottom(target, threshold = 48) {
  if (!target) return false
  const distance = Number(target.scrollHeight)
    - Number(target.scrollTop)
    - Number(target.clientHeight)
  return Number.isFinite(distance) && distance <= threshold
}

/**
 * BPMN 之外的实体版本从 V1 起连续递增，因此仅 V1 没有上一版本。
 */
export function hasPreviousEntityVersion(item) {
  return Number(item?.version || 0) > 1
}

/**
 * 合并滚动分页结果，并按历史 ID 去重，避免网络重试造成重复卡片。
 */
export function mergeEntityHistoryPage(current, incoming, reset = false) {
  const base = reset ? [] : (Array.isArray(current) ? current : [])
  const additions = Array.isArray(incoming) ? incoming : []
  const seenIds = new Set(base.map(item => item?.id).filter(Boolean))
  return [
    ...base,
    ...additions.filter((item) => {
      if (!item?.id || !seenIds.has(item.id)) {
        if (item?.id) seenIds.add(item.id)
        return true
      }
      return false
    })
  ]
}
