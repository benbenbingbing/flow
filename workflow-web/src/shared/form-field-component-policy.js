const freezeTypes = types => Object.freeze([...types])

export const FORM_FIELD_COMPONENT_SUPPORTED_TYPES = Object.freeze({
  input: freezeTypes(['STRING']),
  textarea: freezeTypes(['STRING', 'TEXT']),
  rich_text: freezeTypes(['TEXT', 'RICH_TEXT']),
  number: freezeTypes(['INTEGER', 'LONG', 'DECIMAL', 'DOUBLE']),
  date: freezeTypes(['DATE']),
  datetime: freezeTypes(['DATETIME']),
  select: freezeTypes(['SELECT', 'STRING']),
  select_multiple: freezeTypes(['MULTI_SELECT']),
  radio: freezeTypes(['RADIO', 'SELECT']),
  checkbox: freezeTypes(['CHECKBOX', 'MULTI_SELECT']),
  switch: freezeTypes(['BOOLEAN']),
  file: freezeTypes(['FILE']),
  image: freezeTypes(['IMAGE']),
  cascader: freezeTypes(['STRING', 'MULTI_SELECT']),
  reference: freezeTypes(['REFERENCE', 'USER', 'DEPT', 'ROLE', 'GROUP']),
  multi_reference: freezeTypes(['MULTI_REFERENCE']),
  sub_form: freezeTypes(['SUB_FORM', 'SUB_FORM_LIST']),
  section: freezeTypes(['SECTION'])
})

export const DEFAULT_FORM_FIELD_COMPONENTS = Object.freeze({
  STRING: 'input',
  TEXT: 'textarea',
  RICH_TEXT: 'rich_text',
  INTEGER: 'number',
  LONG: 'number',
  DOUBLE: 'number',
  DECIMAL: 'number',
  DATE: 'date',
  DATETIME: 'datetime',
  BOOLEAN: 'switch',
  FILE: 'file',
  IMAGE: 'image',
  USER: 'reference',
  DEPT: 'reference',
  ROLE: 'reference',
  GROUP: 'reference',
  SUB_FORM: 'sub_form',
  SUB_FORM_LIST: 'sub_form_list',
  REFERENCE: 'reference',
  MULTI_REFERENCE: 'multi_reference',
  SELECT: 'select',
  MULTI_SELECT: 'select_multiple',
  RADIO: 'radio',
  CHECKBOX: 'checkbox'
})

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
