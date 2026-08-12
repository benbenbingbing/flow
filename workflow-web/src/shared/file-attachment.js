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
  return true
}

export function getMissingRequiredAttachmentItems(fileItems = [], value) {
  const requiredEntries = fileItems
    .map((item, index) => ({ item, index }))
    .filter(({ item }) =>
      item?.required === true || item?.required === 1 || item?.required === '1'
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
      const key = item.itemName || `附件项${index + 1}`
      return !hasAttachmentValue(groupedValue?.[key])
    })
    .map(({ item }) => item)
}
