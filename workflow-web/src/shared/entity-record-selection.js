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
  const selectedPageIds = new Set(
    (selectedPageRows || []).map(recordId).filter(Boolean)
  )

  const retained = normalizeRecordSelection(currentSelection).filter(item => {
    const id = recordId(item)
    return !pageIds.has(id) || selectedPageIds.has(id)
  })
  const retainedIds = new Set(retained.map(recordId))

  for (const item of selectedPageRows || []) {
    const id = recordId(item)
    if (!id || retainedIds.has(id)) continue
    retainedIds.add(id)
    retained.push(item)
  }
  return retained
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
