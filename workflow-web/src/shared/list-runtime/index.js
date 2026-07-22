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
  const parsedDate = new Date(date)
  if (Number.isNaN(parsedDate.getTime())) return '-'
  return parsedDate.toLocaleString('zh-CN')
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

export function formatListFieldValue(row, field, refNameMap = {}) {
  const fieldCode = field?.fieldCode
  if (!fieldCode) return '-'

  // 获取字段原始值：自定义字段优先从 row.data 读取，系统字段从 row 顶层读取
  let value
  if (row?.data && fieldCode in row.data) {
    value = row.data[fieldCode]
  } else if (row && fieldCode in row) {
    value = row[fieldCode]
  }
  if (value === null || value === undefined) return '-'

  const fieldType = (field.fieldType || '').toUpperCase()
  const componentType = field.componentType || ''

  // 实体引用字段（含 DEPT/USER/ROLE/GROUP 等系统实体和自定义引用）
  if (['REFERENCE', 'MULTI_REFERENCE', 'DEPT', 'USER', 'ROLE', 'GROUP'].includes(fieldType)) {
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
      if (!Array.isArray(ids) || !ids.length) return value || '-'
      return ids.map((id) => refNameMap[`${groupKey}:${id}`] || id).join(', ') || '-'
    }

    return refNameMap[`${groupKey}:${value}`] || value
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

  // 子表单
  if (['SUB_FORM', 'SUB_FORM_LIST'].includes(fieldType)) {
    return Array.isArray(value) && value.length > 0 ? `${value.length} 行` : '-'
  }

  // 普通系统字段兜底（无特殊转换的）
  if (isSystemField(fieldCode)) {
    return value ?? '-'
  }

  return value ?? '-'
}
