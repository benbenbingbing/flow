import {
  normalizeEntityStatusOptions,
  resolveEntityStatusLabel,
  resolveProcessStatusLabel
} from '../../../../shared/entity-status-runtime.js'

/**
 * 查看、审批标题使用已加载的 biz 实体状态，避免历史实例或引擎状态覆盖当前记录。
 * 实体状态名称优先使用入口配置，其次使用详情携带的名称（首页入口没有实体状态配置）。
 * 缺失状态不推断生命周期，只展示已有信息。
 */
export function resolveApprovalDialogTitle(name, entityData, entityStatusOptions = []) {
  const processStatus = resolveProcessStatusLabel(entityData?.processStatus ?? entityData?.process_status)
  const status = entityData?.status
  const configuredStatus = normalizeEntityStatusOptions(entityStatusOptions)
    .find(option => option.value === String(status ?? ''))
  const entityStatus = status == null || status === '' ? ''
    : configuredStatus?.label || entityData?._statusText || resolveEntityStatusLabel(status)
  const statusText = [processStatus, entityStatus].filter(Boolean).join('-')
  return `${name || '任务审批'}${statusText ? `（${statusText}）` : ''}`
}

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

export function resolveApprovalEntityCode(configuredCode, entityData, task) {
  return [
    configuredCode,
    entityData?.entityCode,
    task?.entityCode
  ]
    .map(value => String(value || '').trim())
    .find(Boolean) || ''
}

export function resolveApprovalFieldLabel(fieldCode, entityFields = []) {
  const field = entityFields.find(item =>
    String(item?.fieldCode || item?.fieldKey || '') === String(fieldCode)
  )
  return field?.fieldLabel || field?.fieldName || fieldCode
}
