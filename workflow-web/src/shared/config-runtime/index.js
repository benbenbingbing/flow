const FORBIDDEN_KEYS = new Set(['__proto__', 'prototype', 'constructor'])

export function safeParseConfig(value, fallback = {}) {
  if (!value) return cloneValue(fallback)
  if (typeof value === 'object') return cloneValue(value)
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' ? parsed : cloneValue(fallback)
  } catch {
    return cloneValue(fallback)
  }
}

export function stringifyConfig(value) {
  if (!value || typeof value !== 'object' || Object.keys(value).length === 0) {
    return ''
  }
  return JSON.stringify(value)
}

export function normalizeExtensionDescriptor(name, component, metadata = {}) {
  if (name && typeof name === 'object' && !component) {
    const descriptor = name
    return normalizeExtensionDescriptor(
      descriptor.name || descriptor.value || descriptor.type,
      descriptor.component,
      descriptor
    )
  }
  if (!name || !component) {
    throw new Error('扩展组件必须提供 name 和 component')
  }
  return {
    name,
    value: name,
    label: metadata.label || name,
    description: metadata.description || '',
    component,
    configSchema: Array.isArray(metadata.configSchema) ? metadata.configSchema : [],
    capabilities: metadata.capabilities || {},
    supportedModes: metadata.supportedModes || [],
    supportedFieldTypes: metadata.supportedFieldTypes || []
  }
}

export function applySchemaDefaults(schema = [], value = {}) {
  const result = { ...value }
  schema.forEach((item) => {
    if (result[item.key] === undefined && item.defaultValue !== undefined) {
      result[item.key] = cloneValue(item.defaultValue)
    }
  })
  return result
}

export function sanitizeConfigObject(value, depth = 0) {
  if (depth > 8) return undefined
  if (Array.isArray(value)) {
    return value.slice(0, 500).map(item => sanitizeConfigObject(item, depth + 1))
  }
  if (value && typeof value === 'object') {
    const result = {}
    Object.entries(value).slice(0, 100).forEach(([key, item]) => {
      if (!FORBIDDEN_KEYS.has(key)) {
        result[key] = sanitizeConfigObject(item, depth + 1)
      }
    })
    return result
  }
  return value
}

export function getFieldModeAccess(field, mode = 'view') {
  const extension = safeParseConfig(field?.extensionConfig)
  const access = extension?.modes?.[mode] || {}
  return {
    visible: field?.isHidden !== 1 && field?.isHidden !== true && access.visible !== false,
    editable: access.editable !== false
  }
}

export function isFieldVisibleForMode(field, mode = 'view') {
  return getFieldModeAccess(field, mode).visible
}

export function isFieldReadonlyForMode(field, mode = 'view', forceReadonly = false) {
  const access = getFieldModeAccess(field, mode)
  return forceReadonly
    || access.editable === false
    || field?.isReadonly === 1
    || field?.isReadonly === true
}

export function buildRuntimeFieldRules(field, required, label) {
  const rules = []
  const displayLabel = label || field?.fieldLabel || field?.fieldName || '该字段'
  if (required) {
    rules.push({ required: true, message: `请输入${displayLabel}`, trigger: ['blur', 'change'] })
  }

  const config = safeParseConfig(field?.validationRules)
  if (config.minLength !== undefined || config.maxLength !== undefined) {
    const rule = { trigger: 'blur' }
    if (config.minLength !== undefined && config.minLength !== '') rule.min = Number(config.minLength)
    if (config.maxLength !== undefined && config.maxLength !== '') rule.max = Number(config.maxLength)
    rule.message = `${displayLabel}长度需在 ${rule.min ?? 0} 到 ${rule.max ?? '不限'} 之间`
    rules.push(rule)
  }
  if (config.min !== undefined || config.max !== undefined) {
    rules.push({
      validator: (_rule, value, callback) => {
        if (value === '' || value === null || value === undefined) return callback()
        const number = Number(value)
        if (Number.isNaN(number)) return callback(new Error(`${displayLabel}必须为数字`))
        if (config.min !== undefined && config.min !== '' && number < Number(config.min)) {
          return callback(new Error(`${displayLabel}不能小于 ${config.min}`))
        }
        if (config.max !== undefined && config.max !== '' && number > Number(config.max)) {
          return callback(new Error(`${displayLabel}不能大于 ${config.max}`))
        }
        callback()
      },
      trigger: ['blur', 'change']
    })
  }

  const formatPatterns = {
    EMAIL: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
    PHONE: /^1\d{10}$/,
    URL: /^https?:\/\/[^\s]+$/i
  }
  const format = String(config.format || '').toUpperCase()
  if (formatPatterns[format]) {
    const formatLabels = { EMAIL: '邮箱', PHONE: '手机号', URL: 'URL' }
    rules.push({
      pattern: formatPatterns[format],
      message: `${displayLabel}不是合法的${formatLabels[format]}`,
      trigger: 'blur'
    })
  }
  return rules
}

function cloneValue(value) {
  if (value === undefined) return undefined
  return JSON.parse(JSON.stringify(value))
}
