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

export function withEntityStatusFieldOptions(field, statuses = []) {
  if (!isEntityStatusField(field)) return field
  const options = getEffectiveEntityStatusOptions(statuses)
  return {
    ...field,
    fieldType: 'SELECT',
    componentType: 'select',
    options,
    optionsJson: JSON.stringify(options)
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

    const nodeProps = parseDocument(node?.propsDocument || node?.props)
    const linkedField = resolveNodeField(node, nodeProps, fields)
    const fieldCode = nodeProps.fieldCode || linkedField?.fieldCode || node?.nodeKey
    if (String(fieldCode || '').toLowerCase() !== 'status') return node

    const runtimeProps = {
      ...nodeProps,
      fieldCode: 'status',
      fieldType: 'SELECT',
      componentType: 'select',
      options,
      optionsJson: JSON.stringify(options)
    }
    return {
      ...node,
      props: runtimeProps,
      propsDocument: JSON.stringify(runtimeProps)
    }
  })
}

export function withEntityStatusRuntimeForm(form, entityFields = [], statuses = []) {
  if (!form) return form
  const sourceFields = form.fields || []
  const runtimeFields = sourceFields.map(field =>
    withEntityStatusFieldOptions(field, statuses)
  )
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

