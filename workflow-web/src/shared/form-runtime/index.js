export const SYSTEM_FIELD_CODES = [
  'id',
  'dataNo',
  'title',
  'name',
  'code',
  'status',
  'processInstanceId',
  'processStartTime',
  'processEndTime',
  'currentTaskId',
  'currentTaskName',
  'currentTaskAssignee',
  'submitterId',
  'submitterName',
  'deptId',
  'submitTime',
  'createdAt',
  'updatedAt',
  'createdBy',
  'updatedBy'
]

export const SYSTEM_FIELDS = new Set(SYSTEM_FIELD_CODES)

export function normalizeEntityRecordForForm(record = {}) {
  const result = {
    ...(record?.data && typeof record.data === 'object' ? record.data : {})
  }
  SYSTEM_FIELD_CODES.forEach((fieldCode) => {
    const value = record?.[fieldCode]
    if (value !== null && value !== undefined) {
      result[fieldCode] = value
    }
  })
  return result
}

export function getFieldKey(field) {
  return String(field?.fieldCode || field?.fieldKey || field?.fieldId || field?.id || '')
}

export function parseRuntimeDefaultValue(value) {
  if (value == null || typeof value !== 'string') return value
  const normalized = value.trim()
  if (!normalized) return value
  try {
    return JSON.parse(normalized)
  } catch {
    return value
  }
}

export function applyRuntimeFieldDefaults(target = {}, form = {}, entityFields = []) {
  const defaultsByField = new Map()
  const addFieldDefault = (field = {}) => {
    const fieldKey = getFieldKey(field)
    if (!fieldKey || field.defaultValue == null || field.defaultValue === '') return
    defaultsByField.set(fieldKey, field.defaultValue)
  }

  entityFields.forEach(addFieldDefault)
  ;(form?.fields || []).forEach(addFieldDefault)
  ;(form?.nodes || []).forEach(node => {
    const nodeType = String(node?.nodeType || '').toUpperCase()
    if (!['FIELD', 'SUB_FORM', 'REPEATER'].includes(nodeType)) return
    const props = typeof node?.propsDocument === 'string'
      ? parseRuntimeDefaultValue(node.propsDocument)
      : (node?.propsDocument || node?.props || {})
    if (!props || typeof props !== 'object' || Array.isArray(props)) return
    addFieldDefault(props)
  })

  defaultsByField.forEach((value, fieldKey) => {
    if (target[fieldKey] == null || target[fieldKey] === '') {
      target[fieldKey] = parseRuntimeDefaultValue(value)
    }
  })
  return target
}

export function isSystemField(fieldOrCode) {
  const fieldCode = typeof fieldOrCode === 'string' ? fieldOrCode : getFieldKey(fieldOrCode)
  return SYSTEM_FIELDS.has(fieldCode)
}

export function getFieldModelPath(fieldOrCode) {
  const fieldCode = typeof fieldOrCode === 'string' ? fieldOrCode : getFieldKey(fieldOrCode)
  return isSystemField(fieldCode) ? fieldCode : `data.${fieldCode}`
}

export function isRuntimeFormReadonly(form) {
  return form?.isReadonly === true || form?.isReadonly === 1 || form?.isReadonly === '1'
}

export function isRuntimeFieldReadonly(field, forceReadonly = false, mode = 'view') {
  return isFieldReadonlyForMode(field, mode, forceReadonly)
}

export function isRuntimeFieldVisible(field, mode = 'view') {
  return isFieldVisibleForMode(field, mode)
}

export { buildRuntimeFieldRules }
export {
  createFormDataSourceRuntime,
  getClientBeforeSubmitBindings,
  isClientPrevalidationBinding,
  getFormDataSourceBindings
} from './dataSourceRuntime.js'
export { resolveRuntimeFormTabLayout } from './runtimeFormTabs.js'

export function normalizeRuntimeFormConfigs(progressRes) {
  if (Array.isArray(progressRes?.formConfigs) && progressRes.formConfigs.length > 0) {
    return [progressRes.formConfigs[0]]
  }
  return progressRes?.formConfig ? [progressRes.formConfig] : []
}

export function mergeRuntimeFormConfigs(configs) {
  if (!configs || configs.length === 0) return null
  return configs[0]
}
import {
  buildRuntimeFieldRules,
  isFieldReadonlyForMode,
  isFieldVisibleForMode
} from '@/shared/config-runtime'
