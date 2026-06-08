import { isSystemField } from '@/shared/form-runtime'

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

export function getCellValue(row, field, fallback = '') {
  const fieldCode = field?.fieldCode
  if (!fieldCode) return fallback
  if (row?.extData && fieldCode in row.extData) return row.extData[fieldCode]
  if (row?.data && fieldCode in row.data) return row.data[fieldCode]
  if (row && fieldCode in row) return row[fieldCode]
  return fallback
}

export function isDateFieldCode(fieldCode) {
  return ['createdAt', 'processStartTime', 'processEndTime', 'submitTime', 'updatedAt'].includes(fieldCode)
}

export function formatDateValue(date) {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

export function formatListFieldValue(row, field, refNameMap = {}) {
  const fieldCode = field?.fieldCode
  if (!fieldCode) return '-'

  if (isSystemField(fieldCode)) {
    return row?.[fieldCode] ?? '-'
  }

  const value = row?.data?.[fieldCode]
  if (value === null || value === undefined) return '-'

  const fieldType = (field.fieldType || '').toUpperCase()
  const componentType = field.componentType || ''

  if (['SUB_FORM', 'SUB_FORM_LIST'].includes(fieldType)) {
    return Array.isArray(value) && value.length > 0 ? `${value.length} 行` : '-'
  }

  if (['SELECT', 'RADIO', 'MULTI_SELECT', 'CHECKBOX'].includes(fieldType)) {
    const options = parseJsonOptions(field.optionsJson)
    const isMultiple = componentType === 'select_multiple' || ['MULTI_SELECT', 'CHECKBOX'].includes(fieldType)
    if (isMultiple) {
      if (!Array.isArray(value)) return value
      return value.map((v) => options.find((option) => option.value === v)?.label || v).join(', ') || '-'
    }
    const option = options.find((item) => item.value === value)
    return option?.label || value
  }

  if (fieldType === 'REFERENCE') {
    const groupKey = `${field.refEntityType || 'CUSTOM'}:${field.refEntityId || ''}`
    return refNameMap[`${groupKey}:${value}`] || value
  }

  if (fieldType === 'MULTI_REFERENCE') {
    const groupKey = `${field.refEntityType || 'CUSTOM'}:${field.refEntityId || ''}`
    let ids = value
    if (typeof ids === 'string') {
      try {
        ids = JSON.parse(ids)
      } catch {
        ids = ids.split(',').filter(Boolean)
      }
    }
    if (!Array.isArray(ids) || !ids.length) return value || '-'
    return ids.map((id) => refNameMap[`${groupKey}:${id}`] || id).join(', ') || '-'
  }

  return value ?? '-'
}
