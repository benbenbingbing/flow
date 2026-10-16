import fieldDefinitions from '../generated/field-definitions.js'

/** 平台字段清单生成的类型策略；不维护第二份可手工修改的映射。 */
export const FORM_FIELD_COMPONENT_SUPPORTED_TYPES = Object.freeze(Object.fromEntries(
  fieldDefinitions.map(item => [item.name, Object.freeze([...item.supportedFieldTypes])])
))
export const DEFAULT_FORM_FIELD_COMPONENTS = Object.freeze(Object.fromEntries(
  fieldDefinitions.flatMap(item => item.defaultForFieldTypes.map(type => [type, item.name]))
))

export function getBuiltInFormFieldSupportedTypes(componentType) {
  const normalizedType = String(componentType || '').trim().toLowerCase()
  return [...(FORM_FIELD_COMPONENT_SUPPORTED_TYPES[normalizedType] || [])]
}

export function getDefaultFormFieldComponentType(fieldType) {
  const normalizedType = String(fieldType || '').trim().toUpperCase()
  return DEFAULT_FORM_FIELD_COMPONENTS[normalizedType] || 'input'
}

export function isBuiltInFormFieldComponentCompatible(
  fieldType,
  componentType
) {
  const normalizedFieldType = String(fieldType || '').trim().toUpperCase()
  return getBuiltInFormFieldSupportedTypes(componentType)
    .includes(normalizedFieldType)
}

export function normalizeFormNodeFieldType(fieldType, componentType) {
  const normalizedFieldType = String(fieldType || '').trim().toUpperCase()
  const normalizedComponentType = String(componentType || '')
    .trim()
    .toLowerCase()
  if (normalizedFieldType === 'RICH_TEXT'
      && normalizedComponentType === 'rich_text') {
    return 'TEXT'
  }
  return normalizedFieldType
}
