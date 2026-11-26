/** 固定系统枚举，不受实体业务状态配置影响。 */
export const PROCESS_STATUS_OPTIONS = Object.freeze([
  { value: 'NOT_STARTED', label: '未发起' },
  { value: 'RUNNING', label: '运行中' },
  { value: 'COMPLETED', label: '已完成' }
])

// 与平台扩展目录的稳定 ID 对应，供草稿预览和旧版回显判断选项来源。
export const ENTITY_STATUS_OPTIONS_EXTENSION_ID = 'platform_entity_status_options'

export function isProcessStatusField(field) {
  return ['processStatus', 'process_status'].includes(field?.fieldCode || field?.fieldKey || '')
}

export function resolveProcessStatusLabel(value) {
  return PROCESS_STATUS_OPTIONS.find(option => option.value === value)?.label || value || ''
}

const BUILT_IN_STATUS_OPTIONS = Object.freeze([
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '处理中' },
  { value: 'APPROVED', label: '已完成' },
  { value: 'REJECTED', label: '已驳回' },
  { value: 'TERMINATED', label: '已终止' },
  { value: 'WITHDRAWN', label: '已撤回' },
  { value: 'COMPLETED', label: '已完成' }
])

function parseDocument(value) {
  if (!value) return {}
  if (typeof value === 'object') return value
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' ? parsed : {}
  } catch {
    return {}
  }
}

function statusCodeOf(status) {
  return String(status?.statusCode ?? status?.value ?? '').trim()
}

function statusNameOf(status, statusCode) {
  return String(status?.statusName ?? status?.label ?? statusCode).trim()
}

export function normalizeEntityStatusOptions(statuses = []) {
  const seen = new Set()
  return (Array.isArray(statuses) ? statuses : [])
    .map(status => {
      const value = statusCodeOf(status)
      if (!value || seen.has(value)) return null
      seen.add(value)
      return {
        value,
        label: statusNameOf(status, value)
      }
    })
    .filter(Boolean)
}

export function getEffectiveEntityStatusOptions(statuses = []) {
  const configured = normalizeEntityStatusOptions(statuses)
  return configured.length > 0
    ? configured
    : BUILT_IN_STATUS_OPTIONS.map(option => ({ ...option }))
}

export function buildEntityStatusMap(statuses = []) {
  return Object.fromEntries(
    getEffectiveEntityStatusOptions(statuses)
      .map(option => [String(option.value), option.label])
  )
}

export function resolveEntityStatusLabel(value, statusesOrMap = {}) {
  if (value === null || value === undefined || value === '') return ''
  const key = String(value)
  const statusMap = Array.isArray(statusesOrMap)
    ? buildEntityStatusMap(statusesOrMap)
    : statusesOrMap
  if (statusMap && statusMap[key]) return statusMap[key]
  return buildEntityStatusMap()[key] || key
}

export function isEntityStatusField(field) {
  const fieldCode = field?.fieldCode || field?.fieldKey || ''
  return String(fieldCode).trim().toLowerCase() === 'status'
}

/** 仅给实体目录中的标准状态字段自动绑定平台数据源。 */
export function isSystemEntityStatusField(field) {
  return [true, 1, '1'].includes(field?.isSystem) && field.fieldCode === 'status'
}

function hasPublishedStatusOptions(node) {
  const bindings = parseDocument(node?.dataSourceBindingsDocument || node?.dataSourceBindings)
  return bindings.FIELD_OPTIONS?.extensionId === ENTITY_STATUS_OPTIONS_EXTENSION_ID
    && bindings.FIELD_OPTIONS?.enabled !== false
}

export function withEntityStatusFieldOptions(
  field,
  statuses = [],
  runtimeOptions = {}
) {
  if (isProcessStatusField(field)) {
    return { ...field, fieldType: 'SELECT',
      componentType: runtimeOptions.allowMultiple === true
        && ['select_multiple', 'multi_select'].includes(field?.componentType) ? 'select_multiple' : 'select', editable: false,
      readonly: true, options: PROCESS_STATUS_OPTIONS, runtimeOptions: PROCESS_STATUS_OPTIONS }
  }
  if (!isEntityStatusField(field) || [false, 0, '0'].includes(field?.isSystem)) return field
  const statusOptions = getEffectiveEntityStatusOptions(statuses)
  const configuredComponent = String(field?.componentType || '')
    .trim()
    .toLowerCase()
  const componentType = runtimeOptions.allowMultiple === true
    && ['select_multiple', 'multi_select'].includes(configuredComponent)
    ? 'select_multiple'
    : 'select'
  return {
    ...field,
    fieldType: 'SELECT',
    componentType,
    options: statusOptions,
    runtimeOptions: statusOptions
  }
}

function resolveNodeField(node, nodeProps, fields) {
  const bindingRef = node?.bindingRef
    || nodeProps?.fieldCode
    || nodeProps?.fieldId
    || node?.nodeKey
  return (fields || []).find(field =>
    String(field?.id) === String(bindingRef)
      || String(field?.fieldId) === String(bindingRef)
      || field?.fieldCode === bindingRef
  )
}

export function withEntityStatusRuntimeNodes(nodes = [], fields = [], statuses = []) {
  const options = getEffectiveEntityStatusOptions(statuses)
  return (nodes || []).map(node => {
    const nodeType = String(node?.nodeType || '').toUpperCase()
    if (!['FIELD', 'SUB_FORM', 'REPEATER'].includes(nodeType)) return node
    // 新快照由通用下拉按发布绑定查询；旧回显注入候选会遮住该绑定。
    if (hasPublishedStatusOptions(node)) return node

    const nodeProps = parseDocument(node?.propsDocument || node?.props)
    const linkedField = resolveNodeField(node, nodeProps, fields)
    const fieldCode = nodeProps.fieldCode || linkedField?.fieldCode || node?.nodeKey
    const processStatus = ['processStatus', 'process_status'].includes(fieldCode)
    if (!processStatus && String(fieldCode || '').toLowerCase() !== 'status') return node
    if (!processStatus && (node.bindingType === 'NONE' || [false, 0, '0'].includes(linkedField?.isSystem))) return node

    const runtimeProps = {
      ...nodeProps,
      fieldCode: processStatus ? fieldCode : 'status',
      fieldType: 'SELECT',
      componentType: 'select',
      ...(processStatus ? { readonly: true, disabled: true } : {})
    }
    return {
      ...node,
      // 系统状态候选是本次请求的解析结果，不写进节点配置，避免形成新的内嵌来源。
      runtimeOptions: processStatus ? PROCESS_STATUS_OPTIONS : options,
      props: runtimeProps,
      propsDocument: JSON.stringify(runtimeProps)
    }
  })
}

export function withEntityStatusRuntimeForm(form, entityFields = [], statuses = []) {
  if (!form) return form
  const sourceFields = form.fields || []
  const providerNodes = (form.nodes || []).filter(hasPublishedStatusOptions)
  const runtimeFields = sourceFields.map(field => {
    const usesProvider = isEntityStatusField(field) && providerNodes.some(node =>
      resolveNodeField(node, parseDocument(node.propsDocument || node.props), [field]))
    // 字段列表与节点列表共存时也不为已绑定的数据源预填候选。
    return usesProvider ? { ...field, options: undefined, runtimeOptions: undefined }
      : withEntityStatusFieldOptions(field, statuses)
  })
  const statusAwareEntityFields = (entityFields || []).map(field =>
    withEntityStatusFieldOptions(field, statuses)
  )
  const nodeFields = runtimeFields.length > 0
    ? runtimeFields
    : statusAwareEntityFields
  return {
    ...form,
    fields: runtimeFields,
    nodes: withEntityStatusRuntimeNodes(
      form.nodes || [],
      [...nodeFields, ...statusAwareEntityFields],
      statuses
    )
  }
}
