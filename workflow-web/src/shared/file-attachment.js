function splitFileTypeValues(value) {
  const values = Array.isArray(value) ? value : [value]
  return values.flatMap(item =>
    String(item || '')
      .split(/[,，;；\s]+/)
      .map(token => token.trim())
      .filter(Boolean)
  )
}

export function normalizeAttachmentFileTypes(value) {
  return [...new Set(
    splitFileTypeValues(value)
      .map(token => token.toLowerCase())
      .map(token => token.startsWith('.') ? token : `.${token}`)
      .filter(token => /^\.[a-z0-9][a-z0-9._+-]*$/i.test(token))
  )]
}

export function attachmentFileTypesToString(value) {
  return normalizeAttachmentFileTypes(value).join(',')
}

export function isAttachmentFileTypeAllowed(file, value) {
  const allowedTypes = normalizeAttachmentFileTypes(value)
  if (allowedTypes.length === 0) return true
  const fileName = String(file?.name || file || '').toLowerCase()
  return allowedTypes.some(type => fileName.endsWith(type))
}

export function hasAttachmentValue(value) {
  if (value === null || value === undefined) return false
  if (Array.isArray(value)) return value.some(hasAttachmentValue)
  if (typeof value === 'string') return value.trim() !== ''
  if (typeof value === 'object') {
    const fileUrlKeys = ['url', 'path', 'fileUrl']
    const configuredUrlKeys = fileUrlKeys.filter(key =>
      Object.prototype.hasOwnProperty.call(value, key)
    )
    if (configuredUrlKeys.length > 0) {
      return configuredUrlKeys.some(key =>
        hasAttachmentValue(value[key])
      )
    }
    if (['name', 'originalName', 'size', 'type', 'uid', 'status']
      .some(key => Object.prototype.hasOwnProperty.call(value, key))) {
      return false
    }
    return Object.values(value).some(hasAttachmentValue)
  }
  return false
}

function hasAttachmentFileValue(value) {
  if (value === null || value === undefined) return false
  if (Array.isArray(value)) return value.some(hasAttachmentFileValue)
  if (typeof value === 'string') return value.trim() !== ''
  if (typeof value !== 'object') return false
  return ['url', 'path', 'fileUrl']
    .filter(key => Object.prototype.hasOwnProperty.call(value, key))
    .some(key => hasAttachmentFileValue(value[key]))
}

export function normalizeAttachmentItemAliases(value) {
  if (Array.isArray(value)) {
    return [...new Set(value.map(item => String(item || '').trim()).filter(Boolean))]
  }
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return normalizeAttachmentItemAliases(parsed)
  } catch {
    return []
  }
}

export function resolveAttachmentItems(field) {
  let componentProps = {}
  try {
    componentProps = typeof field?.componentProps === 'string'
      ? JSON.parse(field.componentProps)
      : (field?.componentProps || {})
  } catch {
    componentProps = {}
  }
  const items = Array.isArray(componentProps?.fileItems)
    && componentProps.fileItems.length
    ? componentProps.fileItems
    : (Array.isArray(field?.fileItems) ? field.fileItems : [])
  const entityItems = Array.isArray(field?.entityFileItems)
    ? field.entityFileItems
    : []
  return items.map(item => {
    const normalized = {
      ...item,
      nameAliases: normalizeAttachmentItemAliases(item?.nameAliases)
    }
    const entityItem = findMatchingAttachmentItem(
      normalized,
      entityItems
    )
    const storageItemName = entityItem?.itemName || normalized.itemName
    const compatibleNames = [
      normalized.itemName,
      ...normalized.nameAliases,
      entityItem?.itemName,
      ...normalizeAttachmentItemAliases(entityItem?.nameAliases)
    ].filter(name => name && name !== storageItemName)
    return {
      ...normalized,
      storageItemName,
      nameAliases: [...new Set(compatibleNames)],
      ...(isAttachmentItemRequired(entityItem) ? { required: true } : {})
    }
  })
}

function findMatchingAttachmentItem(item, candidates) {
  if (item?.itemKey) {
    const byKey = candidates.find(candidate =>
      candidate?.itemKey === item.itemKey)
    if (byKey) return byKey
  }
  const names = new Set([
    item?.itemName,
    ...normalizeAttachmentItemAliases(item?.nameAliases)
  ].filter(Boolean))
  return candidates.find(candidate => [
    candidate?.itemName,
    ...normalizeAttachmentItemAliases(candidate?.nameAliases)
  ].some(name => name && names.has(name)))
}

export function getAttachmentItemValue(item, index, value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return undefined
  }
  const keys = [
    item?.storageItemName,
    item?.itemName,
    ...normalizeAttachmentItemAliases(item?.nameAliases),
    item?.itemKey,
    `附件项${index + 1}`
  ].filter(Boolean)
  for (const key of keys) {
    if (Object.prototype.hasOwnProperty.call(value, key)) {
      return value[key]
    }
  }
  return undefined
}

export function setAttachmentItemValue(value, item, index, itemValue) {
  const result = value && typeof value === 'object' && !Array.isArray(value)
    ? { ...value }
    : {}
  const currentKey = item?.storageItemName
    || item?.itemName
    || `附件项${index + 1}`
  const compatibleKeys = [
    item?.itemName,
    ...normalizeAttachmentItemAliases(item?.nameAliases),
    item?.itemKey,
    `附件项${index + 1}`
  ].filter(key => key && key !== currentKey)
  compatibleKeys.forEach(key => delete result[key])
  result[currentKey] = itemValue
  return result
}

export function isAttachmentItemRequired(item) {
  return item?.required === true
    || item?.required === 1
    || item?.required === '1'
}

export function getAttachmentItemRequiredState(fileItems = [], rules, formData = {}, evaluateCondition) {
  const configuredItems = Array.isArray(rules?.items) ? rules.items : []
  const rulesByKey = new Map(configuredItems
    .filter(item => item?.itemKey)
    .map(item => [item.itemKey, item]))
  return Object.fromEntries(fileItems
    .filter(item => item?.itemKey)
    .map(item => {
      const configured = rulesByKey.get(item.itemKey)
      const conditional = configured?.requiredConditionConfig && evaluateCondition
        ? Boolean(evaluateCondition(configured.requiredConditionConfig, formData))
        : false
      return [item.itemKey, isAttachmentItemRequired(item) || conditional]
    }))
}

export function getMissingRequiredAttachmentItems(fileItems = [], value, requiredState = {}) {
  const requiredEntries = fileItems
    .map((item, index) => ({ item, index }))
    .filter(({ item }) =>
      isAttachmentItemRequired(item)
        || Boolean(item?.itemKey && requiredState[item.itemKey])
    )
  if (requiredEntries.length === 0) return []

  const groupedValue = value && typeof value === 'object' && !Array.isArray(value)
    ? value
    : null
  if (!groupedValue && requiredEntries.length === 1) {
    return hasAttachmentValue(value) ? [] : [requiredEntries[0].item]
  }

  return requiredEntries
    .filter(({ item, index }) => {
      return !hasAttachmentFileValue(
        getAttachmentItemValue(item, index, groupedValue)
      )
    })
    .map(({ item }) => item)
}
