import { isSystemField } from '@/shared/form-runtime'
import {
  isEntityStatusField,
  resolveEntityStatusLabel
} from '@/shared/entity-status-runtime'

export function parseJsonOptions(optionsJson) {
  if (!optionsJson) return []
  try {
    const options = typeof optionsJson === 'string' ? JSON.parse(optionsJson) : optionsJson
    return Array.isArray(options) ? options : []
  } catch {
    return []
  }
}

export function parseDataSourceConfig(dataSourceConfig) {
  if (!dataSourceConfig) return {}
  try {
    return typeof dataSourceConfig === 'string' ? JSON.parse(dataSourceConfig) : dataSourceConfig
  } catch {
    return {}
  }
}

export function toRuntimeFieldKey(fieldCode = '') {
  return String(fieldCode).replace(/_([a-z0-9])/g, (_, char) => char.toUpperCase())
}

export function toListFilterFieldCode(filterKey = '') {
  const key = String(filterKey)
  for (const suffix of ['_start', '_end', '_op']) {
    if (key.endsWith(suffix)) {
      return key.slice(0, -suffix.length)
    }
  }
  return key
}

function hasFilterValue(value) {
  return value !== '' && value !== null && value !== undefined
}

export function buildListRequestFilters(
  queryForm = {},
  queryFields = [],
  fixedFilters = {}
) {
  const fields = queryFields || []
  const allowedFieldCodes = new Set(
    fields
      .map(field => String(field?.fieldCode || '').trim())
      .filter(Boolean)
  )
  const params = {}

  Object.entries(queryForm || {}).forEach(([key, value]) => {
    if (!allowedFieldCodes.has(toListFilterFieldCode(key))) return
    if (hasFilterValue(value)) {
      params[key] = value
    }
  })

  fields.forEach(field => {
    const code = String(field?.fieldCode || '').trim()
    if (!code || !field?.queryType) return
    const hasConfiguredValue = [code, `${code}_start`, `${code}_end`]
      .some(key => params[key] !== undefined)
    if (hasConfiguredValue) {
      params[`${code}_op`] = field.queryType
    }
  })

  Object.entries(fixedFilters || {}).forEach(([key, value]) => {
    if (hasFilterValue(value)) {
      params[key] = value
    }
  })
  return params
}

function getContainerValue(container, fieldCode) {
  if (!container || typeof container !== 'object') return undefined
  if (fieldCode in container) return container[fieldCode]
  const runtimeKey = toRuntimeFieldKey(fieldCode)
  return runtimeKey !== fieldCode && runtimeKey in container
    ? container[runtimeKey]
    : undefined
}

export function getCellValue(row, field, fallback = '') {
  const fieldCode = field?.fieldCode
  if (!fieldCode) return fallback
  const extValue = getContainerValue(row?.extData, fieldCode)
  if (extValue !== undefined) return extValue
  const dataValue = getContainerValue(row?.data, fieldCode)
  if (dataValue !== undefined) return dataValue
  const rowValue = getContainerValue(row, fieldCode)
  if (rowValue !== undefined) return rowValue
  return fallback
}

export function isDateFieldCode(fieldCode) {
  return ['createdAt', 'processStartTime', 'processEndTime', 'submitTime', 'updatedAt'].includes(fieldCode)
}

export function formatDateValue(date) {
  if (!date) return '-'
  const parsedDate = new Date(date)
  if (Number.isNaN(parsedDate.getTime())) return '-'
  return parsedDate.toLocaleString('zh-CN')
}

export function formatDateColumn(_row, _column, value) {
  return formatDateValue(value)
}

function normalizeMultipleValue(value) {
  if (Array.isArray(value)) return value
  if (typeof value !== 'string') return value

  try {
    const parsedValue = JSON.parse(value)
    if (Array.isArray(parsedValue)) return parsedValue
  } catch {}

  return value.split(',').map((item) => item.trim()).filter(Boolean)
}

const REFERENCE_FIELD_TYPES = Object.freeze([
  'REFERENCE',
  'MULTI_REFERENCE',
  'DEPT',
  'USER',
  'ROLE',
  'GROUP',
  'MENU',
  'DICT',
  'DICT_ITEM'
])

export function isReferenceListField(field = {}) {
  const fieldType = String(field.fieldType || '').toUpperCase()
  const refEntityType = String(field.refEntityType || '').toUpperCase()
  return REFERENCE_FIELD_TYPES.includes(fieldType)
    || REFERENCE_FIELD_TYPES.includes(refEntityType)
}

export function formatListFieldValue(
  row,
  field,
  refNameMap = {},
  entityStatusMap = {}
) {
  const fieldCode = field?.fieldCode
  if (!fieldCode) return '-'

  const value = getCellValue(row, field, undefined)
  if (value === null || value === undefined) return '-'
  const displayValue = getCellValue(
    row,
    { fieldCode: `${fieldCode}_display` },
    undefined
  )
  if (displayValue !== null && displayValue !== undefined && displayValue !== '') {
    return displayValue
  }

  if (isEntityStatusField(field)) {
    return resolveEntityStatusLabel(value, entityStatusMap)
  }

  const fieldType = (field.fieldType || '').toUpperCase()
  const componentType = field.componentType || ''

  // 实体引用字段（含 DEPT/USER/ROLE/GROUP 等系统实体和自定义引用）
  if (isReferenceListField(field)) {
    const entityType = field.refEntityType || field.fieldType || 'CUSTOM'
    const refEntityId = field.refEntityId || ''
    const groupKey = `${entityType}:${refEntityId}`

    if (fieldType === 'MULTI_REFERENCE') {
      const resolvedOptions = row?.extData?.[`${fieldCode}Options`]
      if (Array.isArray(resolvedOptions) && resolvedOptions.length) {
        return resolvedOptions.map((option) => option.label || option.value).join(', ')
      }
      let ids = value
      if (typeof ids === 'string') {
        try {
          ids = JSON.parse(ids)
        } catch {
          ids = ids.split(',').filter(Boolean)
        }
      }
      if (!Array.isArray(ids) || !ids.length) return '-'
      const names = ids
        .map((id) => refNameMap[`${groupKey}:${id}`])
        .filter(Boolean)
      return names.join(', ') || '-'
    }

    return refNameMap[`${groupKey}:${value}`] || '-'
  }

  // 选项类字段
  if (['SELECT', 'RADIO', 'MULTI_SELECT', 'CHECKBOX'].includes(fieldType)) {
    const resolvedOptions = row?.extData?.[`${fieldCode}Options`]
    const options = Array.isArray(resolvedOptions) && resolvedOptions.length
      ? resolvedOptions
      : parseJsonOptions(field.optionsJson)
    const isMultiple = componentType === 'select_multiple' || ['MULTI_SELECT', 'CHECKBOX'].includes(fieldType)
    if (isMultiple) {
      const values = normalizeMultipleValue(value)
      if (!Array.isArray(values)) return value
      return values.map((v) => options.find((option) => option.value === v)?.label || v).join(', ') || '-'
    }
    const option = options.find((item) => item.value === value)
    return option?.label || value
  }

  if (fieldType === 'DATE') {
    return String(value).slice(0, 10) || '-'
  }
  if (['DATETIME', 'TIMESTAMP'].includes(fieldType)) {
    return formatDateValue(value)
  }

  if (['FILE', 'IMAGE'].includes(fieldType)) {
    const values = normalizeFileValues(value)
    const names = values.map(getFileDisplayName).filter(Boolean)
    return names.join(', ') || '-'
  }

  // 子表单数据由父表单聚合保存；子列表是独立列表展示，不读取父记录字段值。
  if (fieldType === 'SUB_FORM') {
    return Array.isArray(value) && value.length > 0 ? `${value.length} 行` : '-'
  }
  if (fieldType === 'SUB_LIST') return '-'

  // 普通系统字段兜底（无特殊转换的）
  if (isSystemField(fieldCode)) {
    return value ?? '-'
  }

  return value ?? '-'
}

function normalizeFileValues(value) {
  if (Array.isArray(value)) return value
  if (value && typeof value === 'object') {
    return Object.values(value).flatMap(item => Array.isArray(item) ? item : [item])
  }
  if (typeof value !== 'string') return value == null ? [] : [value]
  try {
    const parsed = JSON.parse(value)
    return normalizeFileValues(parsed)
  } catch {
    return value ? [value] : []
  }
}

function getFileDisplayName(item) {
  if (!item) return ''
  if (typeof item === 'object') {
    return item.name || item.originalName || getFileDisplayName(item.url || item.path || item.fileUrl)
  }
  const parts = String(item).split(/[\\/]/)
  return parts[parts.length - 1] || ''
}
