import { normalizeFormNodeType } from './form-node-hierarchy.js'
import { normalizeFormNodeFieldType } from './form-field-component-policy.js'

const FIELD_DATA_SOURCE_USAGES = Object.freeze([
  'FIELD_OPTIONS',
  'FIELD_DEFAULT',
  'FIELD_COMPUTE',
  'AFTER_LOAD',
  'BEFORE_SUBMIT'
])

const SUBFORM_DATA_SOURCE_USAGES = Object.freeze([
  'SUBFORM_ROWS',
  'AFTER_LOAD',
  'BEFORE_SUBMIT'
])

const LENGTH_VALIDATION_FIELD_TYPES = new Set(['STRING', 'TEXT'])
const RANGE_VALIDATION_FIELD_TYPES = new Set([
  'INTEGER',
  'LONG',
  'DECIMAL',
  'DOUBLE'
])
const FORMAT_VALIDATION_FIELD_TYPES = new Set(['STRING', 'TEXT'])

const schema = ({
  editable = [],
  configKeys = [],
  fieldProperties = false,
  nodeExtension = false,
  rules = false,
  dataSourceUsages = [],
  binding = false,
  childForm = false,
  template = false,
  gridSpan = false
}) => Object.freeze({
  editable: Object.freeze(editable),
  configKeys: Object.freeze(configKeys),
  fieldProperties,
  nodeExtension,
  rules,
  dataSourceUsages: Object.freeze(dataSourceUsages),
  binding,
  childForm,
  template,
  gridSpan
})

export const FORM_NODE_PROPERTY_SCHEMAS = Object.freeze({
  SECTION: schema({
    editable: ['label', 'parentId']
  }),
  GRID: schema({
    editable: ['parentId', 'gutter', 'defaultSpan'],
    configKeys: ['gutter', 'defaultSpan']
  }),
  TAB_SET: schema({
    editable: ['parentId', 'tabPosition'],
    configKeys: ['tabPosition']
  }),
  TAB: schema({
    editable: ['label', 'parentId']
  }),
  COLLAPSE: schema({
    editable: ['label', 'parentId', 'defaultExpanded', 'accordion'],
    configKeys: ['defaultExpanded', 'accordion']
  }),
  TEXT: schema({
    editable: ['parentId', 'text'],
    configKeys: ['text']
  }),
  FIELD: schema({
    editable: [
      'label',
      'parentId',
      'componentType',
      'required',
      'readonly',
      'hidden',
      'defaultValue',
      'placeholder',
      'dataSource',
      'componentProps',
      'validation',
      'modeAccess',
      'gridSpan',
      'events',
      'template',
      'nodeExtension'
    ],
    fieldProperties: true,
    nodeExtension: true,
    rules: true,
    dataSourceUsages: FIELD_DATA_SOURCE_USAGES,
    binding: true,
    template: true,
    gridSpan: true
  }),
  SUB_FORM: schema({
    editable: [
      'label',
      'parentId',
      'displayMode',
      'layout',
      'childFormRelease',
      'dataSource',
      'gridSpan',
      'template',
      'nodeExtension'
    ],
    nodeExtension: true,
    dataSourceUsages: SUBFORM_DATA_SOURCE_USAGES,
    binding: true,
    childForm: true,
    template: true,
    gridSpan: true
  }),
  REPEATER: schema({
    editable: [
      'label',
      'parentId',
      'displayMode',
      'layout',
      'childFormRelease',
      'dataSource',
      'gridSpan',
      'template',
      'nodeExtension'
    ],
    nodeExtension: true,
    dataSourceUsages: SUBFORM_DATA_SOURCE_USAGES,
    binding: true,
    childForm: true,
    template: true,
    gridSpan: true
  }),
  ACTION_SLOT: schema({
    editable: ['parentId']
  })
})

const FIELD_PROP_KEYS = Object.freeze([
  'fieldId',
  'fieldCode',
  'fieldName',
  'label',
  'fieldType',
  'componentType',
  'placeholder',
  'defaultValue',
  'gridSpan',
  'required',
  'readonly',
  'hidden',
  'componentProps'
])

const SUBFORM_PROP_KEYS = Object.freeze([
  'fieldId',
  'fieldCode',
  'fieldName',
  'label',
  'fieldType',
  'componentType',
  'gridSpan',
  'componentProps'
])

const cleanObject = value => {
  const result = {}
  Object.entries(value || {}).forEach(([key, item]) => {
    if (item !== undefined) result[key] = item
  })
  return result
}

const parseObject = value => {
  if (!value) return {}
  if (typeof value === 'object' && !Array.isArray(value)) return { ...value }
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed
      : {}
  } catch {
    return {}
  }
}

const hasEntries = value => Object.keys(value || {}).length > 0

const cloneValue = value => {
  if (Array.isArray(value)) return value.map(cloneValue)
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, cloneValue(item)])
    )
  }
  return value
}

const hasMeaningfulValue = value => {
  if (value == null || value === '') return false
  if (Array.isArray(value)) return value.some(hasMeaningfulValue)
  if (typeof value === 'object') {
    return Object.values(value).some(hasMeaningfulValue)
  }
  return true
}

export function getFormFieldValidationCapabilities(value) {
  const fieldType = String(value || '').trim().toUpperCase()
  return Object.freeze({
    length: LENGTH_VALIDATION_FIELD_TYPES.has(fieldType),
    range: RANGE_VALIDATION_FIELD_TYPES.has(fieldType),
    format: FORMAT_VALIDATION_FIELD_TYPES.has(fieldType)
  })
}

export function normalizeFormFieldValidation(fieldType, value) {
  const capabilities = getFormFieldValidationCapabilities(fieldType)
  const normalized = parseObject(value)
  if (!capabilities.length) {
    delete normalized.minLength
    delete normalized.maxLength
  }
  if (!capabilities.range) {
    delete normalized.min
    delete normalized.max
  }
  if (!capabilities.format) {
    delete normalized.format
  }
  return normalized
}

export function getFormNodePropertySchema(value) {
  const nodeType = normalizeFormNodeType(value)
  return FORM_NODE_PROPERTY_SCHEMAS[nodeType]
    || FORM_NODE_PROPERTY_SCHEMAS.FIELD
}

export function formNodeSupports(value, capability) {
  return getFormNodePropertySchema(value)[capability] === true
}

export function getFormNodeDataSourceUsages(value) {
  return [...getFormNodePropertySchema(value).dataSourceUsages]
}

export function extractFormNodeComponentConfig(value, propsValue) {
  const nodeType = normalizeFormNodeType(value)
  const nodeSchema = getFormNodePropertySchema(nodeType)
  const props = parseObject(propsValue)
  const nested = parseObject(props.componentProps)
  if (nodeSchema.fieldProperties || nodeSchema.childForm) {
    return nested
  }
  if (nodeType === 'TEXT') {
    const text = props.text
      ?? props.content
      ?? nested.text
      ?? nested.content
    return text === undefined ? {} : { text }
  }
  return nodeSchema.configKeys.reduce((result, key) => {
    if (props[key] !== undefined) {
      result[key] = props[key]
    } else if (nested[key] !== undefined) {
      result[key] = nested[key]
    }
    return result
  }, {})
}

function buildFieldProps(field, componentProps) {
  const source = {
    fieldId: field.fieldId,
    fieldCode: field.fieldCode,
    fieldName: field.fieldName,
    label: field.fieldLabel,
    fieldType: normalizeFormNodeFieldType(
      field.fieldType,
      field.componentType
    ),
    componentType: field.componentType,
    placeholder: field.placeholder,
    defaultValue: field.defaultValue,
    gridSpan: field.gridSpan,
    required: field.isRequired === 1,
    readonly: field.isReadonly === 1,
    hidden: field.isHidden === 1,
    componentProps
  }
  return cleanObject(
    Object.fromEntries(
      FIELD_PROP_KEYS.map(key => [key, source[key]])
    )
  )
}

function buildSubFormProps(field, componentProps) {
  const nodeType = normalizeFormNodeType(field?.nodeType || field?.fieldType)
  const childFormId = field.childFormId || field.refFormId || ''
  const childFormReleaseId = field.childFormReleaseId || ''
  const childFormReleaseVersion = field.childFormReleaseVersion == null
    ? null
    : Number(field.childFormReleaseVersion)
  const source = {
    fieldId: field.fieldId,
    fieldCode: field.fieldCode,
    fieldName: field.fieldName,
    label: field.fieldLabel,
    fieldType: nodeType === 'REPEATER' ? 'SUB_FORM_LIST' : 'SUB_FORM',
    componentType: nodeType === 'REPEATER' ? 'sub_form_list' : 'sub_form',
    gridSpan: field.gridSpan,
    componentProps
  }
  return cleanObject({
    ...Object.fromEntries(
      SUBFORM_PROP_KEYS.map(key => [key, source[key]])
    ),
    ...(childFormId
      ? {
          childFormId,
          refFormId: childFormId,
          publishedFormId: childFormId
        }
      : {}),
    ...(childFormReleaseId
      ? {
          childFormReleaseId,
          refFormReleaseId: childFormReleaseId,
          publishedFormReleaseId: childFormReleaseId
        }
      : {}),
    ...(childFormReleaseVersion == null
      ? {}
      : {
          childFormReleaseVersion,
          refFormReleaseVersion: childFormReleaseVersion,
          publishedFormReleaseVersion: childFormReleaseVersion
        })
  })
}

export function buildFormNodeProps(field, componentPropsValue = {}) {
  const nodeType = normalizeFormNodeType(field?.nodeType || field?.fieldType)
  const parsedComponentProps = parseObject(componentPropsValue)
  const componentProps = nodeType === 'TEXT'
    ? extractFormNodeComponentConfig(nodeType, parsedComponentProps)
    : parsedComponentProps
  if (nodeType === 'FIELD') {
    return buildFieldProps(field, componentProps)
  }
  if (nodeType === 'SUB_FORM' || nodeType === 'REPEATER') {
    return buildSubFormProps(field, componentProps)
  }
  const label = field.fieldLabel || field.fieldName || field.fieldCode || field.nodeKey
  const props = { label }
  getFormNodePropertySchema(nodeType).configKeys.forEach(key => {
    const value = componentProps[key]
    if (value !== undefined) props[key] = value
  })
  return cleanObject(props)
}

function buildDataSourceBindings(field, allowedUsages) {
  const allowed = new Set(allowedUsages)
  const existingBindings = Object.entries(
    parseObject(field.dataSourceBindings)
  ).reduce((result, [key, binding]) => {
    const usage = String(key || '').trim().toUpperCase()
    if (allowed.has(usage) && hasMeaningfulValue(binding)) {
      result[usage] = cloneValue(binding)
    }
    return result
  }, {})
  const requestedUsage = String(field.dataSourceUsage || '').toUpperCase()
  const usage = allowed.has(requestedUsage) ? requestedUsage : ''
  if (usage && field.dataSourceId) {
    const existingBinding = existingBindings[usage]
    existingBindings[usage] = {
      ...(existingBinding
        && typeof existingBinding === 'object'
        && !Array.isArray(existingBinding)
        ? existingBinding
        : {}),
      sourceId: field.dataSourceId,
      inputMapping: parseObject(field.dataSourceInputMappingText),
      outputMapping: parseObject(field.dataSourceOutputMappingText)
    }
  } else if (usage) {
    delete existingBindings[usage]
  }
  return existingBindings
}

function buildClearFields(
  nodeSchema,
  payload,
  bindingType,
  clearParentId
) {
  const clearFields = new Set()
  if (clearParentId) clearFields.add('parentId')
  if (!nodeSchema.nodeExtension || !payload.componentName) {
    clearFields.add('componentName')
    clearFields.add('componentVersion')
    clearFields.add('snapshotVersion')
  }
  if (!nodeSchema.rules) clearFields.add('rules')
  if (!nodeSchema.dataSourceUsages.length || !payload.dataSourceBindings) {
    clearFields.add('dataSourceBindings')
  }
  if (!nodeSchema.childForm || !payload.childFormId) {
    clearFields.add('childFormId')
  }
  if (!nodeSchema.childForm || !payload.childFormReleaseId) {
    clearFields.add('childFormReleaseId')
    clearFields.add('childFormReleaseVersion')
  }
  if (!nodeSchema.template || !payload.templateId) {
    clearFields.add('templateId')
    clearFields.add('templateVersion')
    clearFields.add('localOverrides')
  }
  if (!nodeSchema.binding || bindingType === 'NONE') {
    clearFields.add('bindingRef')
  }
  return [...clearFields]
}

export function buildFormNodePayload(
  field,
  {
    componentProps = {},
    forPatch = false
  } = {}
) {
  const nodeType = normalizeFormNodeType(field?.nodeType || field?.fieldType)
  const nodeSchema = getFormNodePropertySchema(nodeType)
  const inferredBindingType = field.relationCode
    ? 'RELATION'
    : (field.fieldId ? 'ENTITY_FIELD' : 'NONE')
  const explicitBindingType = String(field.bindingType || '').toUpperCase()
  const bindingType = nodeSchema.binding
    ? (explicitBindingType || inferredBindingType)
    : 'NONE'
  const inferredBindingRef = field.relationCode || field.fieldCode || null
  const bindingRef = bindingType === 'NONE'
    ? null
    : (field.bindingRef || inferredBindingRef)
  const childFormId = nodeSchema.childForm
    ? (field.childFormId || field.refFormId || '')
    : ''
  const childFormReleaseId = nodeSchema.childForm
    ? (field.childFormReleaseId || '')
    : ''
  const childFormReleaseVersion =
    nodeSchema.childForm && field.childFormReleaseVersion != null
      ? Number(field.childFormReleaseVersion)
      : null
  const dataSourceBindings = buildDataSourceBindings(
    field,
    nodeSchema.dataSourceUsages
  )
  const hasParentId = Object.prototype.hasOwnProperty.call(field, 'parentId')
  const payload = {
    props: buildFormNodeProps(field, componentProps),
    orderKey: field.orderKey
  }
  if (!forPatch || hasParentId) {
    payload.parentId = field.parentId || null
  }
  if (!forPatch) {
    Object.assign(payload, {
      id: field.id,
      nodeKey: field.nodeKey || field.fieldCode || `node_${field.id}`,
      nodeType,
      bindingType,
      bindingRef,
      legacyProps: field.legacyProps || {}
    })
  }
  if (nodeSchema.nodeExtension && field.componentName) {
    payload.componentName = field.componentName
    payload.componentVersion = field.componentVersion || 1
    payload.snapshotVersion = field.snapshotVersion || 1
  }
  if (nodeSchema.rules) {
    const rules = {
      validation: normalizeFormFieldValidation(
        field.fieldType,
        field.validationRules
      ),
      extension: parseObject(field.extensionConfig)
    }
    if (hasMeaningfulValue(rules)) {
      payload.rules = rules
    }
  }
  if (hasEntries(dataSourceBindings)) {
    payload.dataSourceBindings = dataSourceBindings
  }
  if (nodeSchema.childForm && childFormId) {
    payload.childFormId = childFormId
  }
  if (nodeSchema.childForm && childFormReleaseId) {
    payload.childFormReleaseId = childFormReleaseId
    if (childFormReleaseVersion != null) {
      payload.childFormReleaseVersion = childFormReleaseVersion
    }
  }
  if (nodeSchema.template && field.templateId) {
    payload.templateId = field.templateId
    payload.templateVersion = field.templateVersion || 1
    payload.localOverrides = field.localOverrides || {}
  }
  if (forPatch) {
    payload.clearFields = buildClearFields(
      nodeSchema,
      payload,
      bindingType,
      hasParentId && payload.parentId == null
    )
    if (nodeSchema.rules && !payload.rules) {
      payload.clearFields.push('rules')
    }
  }
  return payload
}
