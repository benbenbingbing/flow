function text(value) {
  return value == null ? '' : String(value).trim()
}

export function normalizeEntitySelectionValues(modelValue, multiple) {
  const values = multiple
    ? (Array.isArray(modelValue) ? modelValue : [])
    : [modelValue]
  return [...new Set(values.map(text).filter(Boolean))]
}

export function entitySelectionKey(item, valueKey = 'entityCode') {
  return text(item?.[valueKey])
}

export function reconcileEntitySelection(values, resolvedItems, valueKey = 'entityCode') {
  const resolvedMap = new Map(
    (resolvedItems || [])
      .map(item => [entitySelectionKey(item, valueKey).toLowerCase(), item])
      .filter(([key]) => key)
  )
  return (values || []).map(value => {
    const normalized = text(value)
    return resolvedMap.get(normalized.toLowerCase()) || {
      [valueKey]: normalized,
      entityName: '实体已不存在',
      missing: true
    }
  })
}

export function toggleEntitySelection(items, item, options = {}) {
  const valueKey = options.valueKey || 'entityCode'
  const multiple = options.multiple !== false
  const key = entitySelectionKey(item, valueKey)
  if (!key) return [...(items || [])]
  if (!multiple) return [item]

  const exists = (items || []).some(current =>
    entitySelectionKey(current, valueKey) === key)
  return exists
    ? items.filter(current => entitySelectionKey(current, valueKey) !== key)
    : [...(items || []), item]
}

export function serializeEntitySelection(items, options = {}) {
  const valueKey = options.valueKey || 'entityCode'
  const multiple = options.multiple !== false
  const valueCase = options.valueCase || 'preserve'
  const values = (items || [])
    .map(item => entitySelectionKey(item, valueKey))
    .filter(Boolean)
    .map(value => {
      if (valueCase === 'lower') return value.toLowerCase()
      if (valueCase === 'upper') return value.toUpperCase()
      return value
    })
  return multiple ? values : (values[0] || '')
}
