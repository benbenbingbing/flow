function recordId(item) {
  const value = item && typeof item === 'object' ? item.id : item
  return value == null ? '' : String(value)
}

export function normalizeRecordSelection(items = []) {
  const normalized = []
  const seen = new Set()
  for (const item of Array.isArray(items) ? items : []) {
    const id = recordId(item)
    if (!id || seen.has(id)) continue
    seen.add(id)
    normalized.push(item)
  }
  return normalized
}

export function reconcileRecordPageSelection(
  currentSelection = [],
  pageRows = [],
  selectedPageRows = []
) {
  const pageIds = new Set((pageRows || []).map(recordId).filter(Boolean))
  const selectedPageById = new Map(
    (selectedPageRows || [])
      .map(item => [recordId(item), item])
      .filter(([id]) => Boolean(id))
  )

  // 非当前页记录保留原对象；当前页仍选中的记录必须换成本次查询的新对象，
  // 让状态与 actionCapabilities 变化立即进入选择集按钮汇总。
  const retained = []
  for (const item of normalizeRecordSelection(currentSelection)) {
    const id = recordId(item)
    if (!pageIds.has(id)) {
      retained.push(item)
      continue
    }
    const freshItem = selectedPageById.get(id)
    if (freshItem) {
      retained.push(freshItem)
      selectedPageById.delete(id)
    }
  }
  const retainedIds = new Set(retained.map(recordId))

  for (const item of selectedPageById.values()) {
    const id = recordId(item)
    if (!id || retainedIds.has(id)) continue
    retainedIds.add(id)
    retained.push(item)
  }
  return retained
}

/**
 * 使用当前页最新查询结果刷新已选记录对象，同时保留其他页的选择。
 *
 * 表格恢复勾选时会屏蔽 selection-change，不能依赖组件事件替换旧对象；
 * 否则同一记录的状态或 actionCapabilities 已变化，工具栏仍会读取旧快照。
 */
export function refreshRecordPageSelection(
  currentSelection = [],
  pageRows = []
) {
  const selectedIds = new Set(recordSelectionIds(currentSelection))
  const selectedPageRows = (Array.isArray(pageRows) ? pageRows : [])
    .filter(item => selectedIds.has(recordId(item)))
  return reconcileRecordPageSelection(
    currentSelection,
    pageRows,
    selectedPageRows
  )
}

export function removeRecordSelection(items = [], itemOrId) {
  const removeId = recordId(itemOrId)
  return normalizeRecordSelection(items).filter(item =>
    recordId(item) !== removeId)
}

export function recordSelectionIds(items = []) {
  return normalizeRecordSelection(items).map(recordId)
}

export function recordSelectionValues(items = [], valueKey = 'id') {
  return normalizeRecordSelection(items)
    .map(item => {
      const value = item && typeof item === 'object'
        ? item[valueKey]
        : item
      return value == null ? '' : String(value)
    })
    .filter(Boolean)
}
