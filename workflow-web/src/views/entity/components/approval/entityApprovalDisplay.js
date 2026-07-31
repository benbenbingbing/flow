export function isFileUrl(value) {
  return typeof value === 'string'
    && /^(?:https?:\/\/|\/|blob:)/i.test(value)
}

export function isGroupedFileValue(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return false
  }
  const groups = Object.values(value)
  return groups.length > 0 && groups.every(group => {
    const items = Array.isArray(group) ? group : [group]
    return items.length > 0 && items.every(isFileUrl)
  })
}

export function fileName(value) {
  if (!isFileUrl(value)) return formatReadonlyValue(value)
  const path = value.split(/[?#]/, 1)[0]
  const name = path.split('/').filter(Boolean).pop() || value
  try {
    return decodeURIComponent(name)
  } catch {
    return name
  }
}

export function formatReadonlyValue(value) {
  if (value === null || value === undefined) return ''
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (typeof value === 'string') return value
  if (typeof value === 'number' || typeof value === 'bigint') {
    return String(value)
  }
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

export function hasRenderableApprovalForm(form) {
  return Boolean(form) && (
    Boolean(form.customComponent)
    || (Array.isArray(form.fields) && form.fields.length > 0)
    || (Array.isArray(form.nodes) && form.nodes.length > 0)
  )
}

export function resolveApprovalFormConfig(runtimeForm, defaultForm) {
  if (hasRenderableApprovalForm(runtimeForm)) return runtimeForm
  if (hasRenderableApprovalForm(defaultForm)) return defaultForm
  return runtimeForm || defaultForm || null
}

export function resolveApprovalFieldLabel(fieldCode, entityFields = []) {
  const field = entityFields.find(item =>
    String(item?.fieldCode || item?.fieldKey || '') === String(fieldCode)
  )
  return field?.fieldLabel || field?.fieldName || fieldCode
}
