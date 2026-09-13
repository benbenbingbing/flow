import { normalizeFormNodeType } from './form-node-hierarchy.js'
import { normalizeFormNodeFieldType } from './form-field-component-policy.js'
import {
  resolveFormContainerAppearance,
  supportsFormContainerAppearance
} from './form-container-appearance.js'
import {
  normalizeFormFieldUniqueness
} from './form-field-uniqueness.js'

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
const PATTERN_VALIDATION_FIELD_TYPES = new Set(['STRING', 'TEXT'])

// 这些结构节点始终独占整行。ACTION_SLOT 是可布局的叶子节点，不能放进
// 该集合，否则设计器虽然能保存 gridSpan，运行时仍会被强制成 24/24。
const FIXED_FULL_WIDTH_NODE_TYPES = new Set([
  'SECTION',
  'GRID',
  'TAB_SET',
  'TAB',
  'COLLAPSE',
  'TEXT'
])

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
  gridSpan = false,
  containerAppearance = false
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
  gridSpan,
  containerAppearance
})

export const FORM_NODE_PROPERTY_SCHEMAS = Object.freeze({
  SECTION: schema({
    editable: ['label', 'parentId', 'showPadding', 'showBorder'],
    configKeys: ['showPadding', 'showBorder'],
    containerAppearance: true
  }),
  GRID: schema({
    editable: [
      'parentId',
      'gutter',
      'defaultSpan',
      'showPadding',
      'showBorder'
    ],
    configKeys: ['gutter', 'defaultSpan', 'showPadding', 'showBorder'],
    containerAppearance: true
  }),
  TAB_SET: schema({
    editable: [
      'parentId',
      'tabPosition',
      'defaultActiveTabKey',
      'showPadding',
      'showBorder'
    ],
    configKeys: [
      'tabPosition',
      'defaultActiveTabKey',
      'showPadding',
      'showBorder'
    ],
    containerAppearance: true
  }),
  TAB: schema({
    editable: ['label', 'parentId', 'showPadding', 'showBorder'],
    configKeys: ['showPadding', 'showBorder'],
    containerAppearance: true
  }),
  COLLAPSE: schema({
    editable: [
      'label',
      'parentId',
      'defaultExpanded',
      'accordion',
      'showPadding',
      'showBorder'
    ],
    configKeys: [
      'defaultExpanded',
      'accordion',
      'showPadding',
      'showBorder'
    ],
    containerAppearance: true
  }),
  TEXT: schema({
    editable: ['parentId', 'text', 'textStyle'],
    configKeys: ['text', 'textStyle']
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
      'layout',
      'showPadding',
      'showBorder',
      'childFormRelease',
      'dataSource',
      'gridSpan',
      'template',
      'nodeExtension'
    ],
    configKeys: ['showPadding', 'showBorder'],
    nodeExtension: true,
    dataSourceUsages: SUBFORM_DATA_SOURCE_USAGES,
    binding: true,
    childForm: true,
    template: true,
    gridSpan: true,
    containerAppearance: true
  }),
  REPEATER: schema({
    editable: [
      'label',
      'parentId',
      'layout',
      'showPadding',
      'showBorder',
      'childFormRelease',
      'dataSource',
      'gridSpan',
      'template',
      'nodeExtension'
    ],
    configKeys: ['showPadding', 'showBorder'],
    nodeExtension: true,
    dataSourceUsages: SUBFORM_DATA_SOURCE_USAGES,
    binding: true,
    childForm: true,
    template: true,
    gridSpan: true,
    containerAppearance: true
  }),
  ACTION_SLOT: schema({
    editable: ['parentId', 'gridSpan'],
    configKeys: ['gridSpan'],
    gridSpan: true
  })
})

const UNKNOWN_FORM_NODE_PROPERTY_SCHEMA = schema({})

const FIELD_PROP_KEYS = Object.freeze([
  'fieldId',
  'fieldCode',
  'fieldName',
  'label',
  'fieldType',
  'componentType',
  'componentExtensionType',
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
    format: FORMAT_VALIDATION_FIELD_TYPES.has(fieldType),
    pattern: PATTERN_VALIDATION_FIELD_TYPES.has(fieldType)
  })
}

export function normalizeFormFieldValidation(fieldType, value, fieldCode = '') {
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
  if (!capabilities.pattern) {
    delete normalized.pattern
  } else if (normalized.pattern == null
      || String(normalized.pattern).length === 0) {
    delete normalized.pattern
  } else {
    normalized.pattern = String(normalized.pattern)
  }
  if (normalized.uniqueness) {
    normalized.uniqueness = normalizeFormFieldUniqueness(
      normalized.uniqueness,
      fieldCode
    )
  }
  return normalized
}

export function getFormNodePropertySchema(value) {
  const nodeType = normalizeFormNodeType(value)
  return FORM_NODE_PROPERTY_SCHEMAS[nodeType]
    || UNKNOWN_FORM_NODE_PROPERTY_SCHEMA
}

export function formNodeSupports(value, capability) {
  return getFormNodePropertySchema(value)[capability] === true
}

export function getFormNodeDataSourceUsages(value) {
  return [...getFormNodePropertySchema(value).dataSourceUsages]
}

/**
 * 统一计算节点在表单行中的 24 栅格占位。
 *
 * ACTION_SLOT 在 grid 布局或显式 GRID 容器内读取 gridSpan；在 vertical /
 * horizontal 布局下继续保持历史整行行为，避免旧表单升级后按钮位置突变。
 */
export function resolveFormNodeLayoutSpan(
  node,
  layoutType = 'vertical',
  fallback = 24
) {
  const nodeType = normalizeFormNodeType(node)
  if (FIXED_FULL_WIDTH_NODE_TYPES.has(nodeType)) return 24

  const normalizedLayout = String(layoutType || 'vertical').toLowerCase()
  if (nodeType === 'ACTION_SLOT' && normalizedLayout !== 'grid') return 24
  if (normalizedLayout === 'vertical') return 24
  if (normalizedLayout === 'horizontal') return 12

  const props = node?.props && typeof node.props === 'object'
    ? node.props
    : {}
  const configured = node?.gridSpan ?? props.gridSpan ?? props.span
  return validGridSpan(configured, fallback)
}

function validGridSpan(value, fallback) {
  const number = Number(value)
  if (Number.isInteger(number) && number >= 1 && number <= 24) {
    return number
  }
  const fallbackNumber = Number(fallback)
  return Number.isInteger(fallbackNumber)
    && fallbackNumber >= 1
    && fallbackNumber <= 24
    ? fallbackNumber
    : 24
}

export function extractFormNodeComponentConfig(value, propsValue) {
  const nodeType = normalizeFormNodeType(value)
  const nodeSchema = getFormNodePropertySchema(nodeType)
  const props = parseObject(propsValue)
  const nested = parseObject(props.componentProps)
  // 历史节点没有外观字段；读取时补齐旧版视觉，避免设计器再次保存后意外换样式。
  const appearance = supportsFormContainerAppearance(nodeType)
    ? resolveFormContainerAppearance(nodeType, props)
    : {}
  if (nodeSchema.fieldProperties || nodeSchema.childForm) {
    return {
      ...nested,
      ...appearance
    }
  }
  if (nodeType === 'TEXT') {
    const text = props.text
      ?? props.content
      ?? nested.text
      ?? nested.content
    const textStyle = props.textStyle ?? nested.textStyle
    return cleanObject({ text, textStyle })
  }
  const config = nodeSchema.configKeys.reduce((result, key) => {
    if (props[key] !== undefined) {
      result[key] = props[key]
    } else if (nested[key] !== undefined) {
      result[key] = nested[key]
    }
    return result
  }, {})
  return {
    ...config,
    ...appearance
  }
}

export function mergeFormNodeFieldMetadata(
  entityFieldsValue,
  legacyFieldValue,
  propsValue,
  nodeKeyValue
) {
  const entityFields = Array.isArray(entityFieldsValue)
    ? entityFieldsValue
    : []
  const legacyField = legacyFieldValue
    && typeof legacyFieldValue === 'object'
    && !Array.isArray(legacyFieldValue)
    ? legacyFieldValue
    : {}
  const props = parseObject(propsValue)
  const fieldIds = [props.fieldId, legacyField.fieldId]
    .filter(value => value != null && value !== '')
    .map(String)
  const fieldCodes = [
    props.fieldCode,
    legacyField.fieldCode,
    nodeKeyValue
  ].filter(Boolean)

  const entityField = entityFields.find(field =>
    fieldIds.some(id =>
      String(field?.id ?? field?.fieldId ?? '') === id
    )
  ) || entityFields.find(field =>
    fieldCodes.includes(field?.fieldCode)
  )

  return {
    ...(entityField || {}),
    ...legacyField
  }
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
    componentExtensionType: field.componentExtensionType,
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
    fieldType: 'SUB_FORM',
    componentType: 'sub_form',
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
  const nodeSchema = getFormNodePropertySchema(nodeType)
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
  nodeSchema.configKeys.forEach(key => {
    // 栅格滑块编辑的是节点顶层投影；它必须覆盖加载时保留在 componentProps
    // 里的旧值，否则 ACTION_SLOT 调整后保存仍会写回原宽度。
    const value = key === 'gridSpan' && nodeSchema.gridSpan
      ? (field.gridSpan ?? componentProps[key])
      : componentProps[key]
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
    if (!field.dataSourceOperationCode) {
      throw new Error('接口服务绑定缺少操作编码')
    }
    const existingBinding = existingBindings[usage]
    existingBindings[usage] = {
      ...(existingBinding
        && typeof existingBinding === 'object'
        && !Array.isArray(existingBinding)
        ? existingBinding
        : {}),
      serviceId: field.dataSourceId,
      operationCode: field.dataSourceOperationCode,
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

export function resolveFormNodeBinding(field, nodeTypeValue) {
  const nodeType = normalizeFormNodeType(
    nodeTypeValue || field?.nodeType || field?.fieldType
  )
  const nodeSchema = getFormNodePropertySchema(nodeType)
  if (!nodeSchema.binding) {
    return {
      bindingType: 'NONE',
      bindingRef: null
    }
  }

  const explicitBindingType = String(field?.bindingType || '')
    .trim()
    .toUpperCase()
  const explicitBindingRef = field?.bindingRef || null
  const relationRef = field?.relationCode
    || (explicitBindingType === 'RELATION' ? explicitBindingRef : null)

  // 子表单的数据语义来自实体关系；旧的 ENTITY_FIELD 绑定不能覆盖关系元数据。
  if (nodeType === 'SUB_FORM' || nodeType === 'REPEATER') {
    return relationRef
      ? {
          bindingType: 'RELATION',
          bindingRef: relationRef
        }
      : {
          bindingType: 'NONE',
          bindingRef: null
        }
  }

  const inferredBindingType = field?.relationCode
    ? 'RELATION'
    : (field?.fieldId ? 'ENTITY_FIELD' : 'NONE')
  const bindingType = explicitBindingType || inferredBindingType
  const inferredBindingRef = bindingType === 'RELATION'
    ? field?.relationCode
    : field?.fieldCode
  return {
    bindingType,
    bindingRef: bindingType === 'NONE'
      ? null
      : (explicitBindingRef || inferredBindingRef || null)
  }
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
  const { bindingType, bindingRef } =
    resolveFormNodeBinding(field, nodeType)
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
        field.validationRules,
        field.fieldCode || field.bindingRef || field.nodeKey
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
