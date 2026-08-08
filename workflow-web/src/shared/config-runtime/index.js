const FORBIDDEN_KEYS = new Set(['__proto__', 'prototype', 'constructor'])
const RUNTIME_REGEX_MAX_LENGTH = 500

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
    supportedFieldTypes: metadata.supportedFieldTypes || [],
    version: Number(metadata.version || 1),
    snapshotVersion: Number(metadata.snapshotVersion || 1)
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
    editable: mode !== 'view' && access.editable !== false
  }
}

export function resolveRuntimeNodeFieldRules(field = {}, nodeRulesValue = {}) {
  const nodeRules = safeParseConfig(nodeRulesValue)
  const fieldValidation = safeParseConfig(field.validationRules)
  const nodeValidation = safeParseConfig(nodeRules.validation)
  const fieldExtension = safeParseConfig(field.extensionConfig)
  const nodeExtension = safeParseConfig(nodeRules.extension)
  const fieldModes = safeParseConfig(fieldExtension.modes)
  const nodeModes = safeParseConfig(nodeExtension.modes)
  const modes = {
    ...fieldModes,
    ...Object.fromEntries(
      Object.entries(nodeModes).map(([mode, access]) => [
        mode,
        {
          ...safeParseConfig(fieldModes[mode]),
          ...safeParseConfig(access)
        }
      ])
    )
  }
  return {
    validationRules: {
      ...fieldValidation,
      ...nodeValidation
    },
    extensionConfig: {
      ...fieldExtension,
      ...nodeExtension,
      ...(Object.keys(modes).length ? { modes } : {})
    }
  }
}

export function resolveTextFieldMaxLength(field = {}, componentPropsValue = {}) {
  const validation = safeParseConfig(
    field?.validationRules ?? field?.validateRules
  )
  const componentProps = safeParseConfig(componentPropsValue)
  const candidates = [
    validation.maxLength,
    componentProps.maxlength,
    field?.fieldLength
  ]
  for (const candidate of candidates) {
    if (candidate === undefined || candidate === null || candidate === '') continue
    const normalized = Number(candidate)
    if (Number.isFinite(normalized) && normalized >= 0) return normalized
  }
  return undefined
}

export function getRuntimeRegexPatternError(value) {
  if (value === undefined || value === null || value === '') return ''
  if (typeof value !== 'string') return '正则表达式必须是字符串'
  if (value.length > RUNTIME_REGEX_MAX_LENGTH) {
    return `正则表达式不能超过 ${RUNTIME_REGEX_MAX_LENGTH} 个字符`
  }
  try {
    new RegExp(value)
    return ''
  } catch {
    return '正则表达式语法不正确'
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

  const config = safeParseConfig(
    field?.validationRules ?? field?.validateRules
  )
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
  if (config.pattern !== undefined
      && config.pattern !== null
      && config.pattern !== '') {
    const patternError = getRuntimeRegexPatternError(config.pattern)
    if (patternError) {
      rules.push({
        validator: (_rule, _value, callback) => {
          callback(new Error(`${displayLabel}的${patternError}`))
        },
        trigger: 'blur'
      })
    } else {
      const pattern = new RegExp(config.pattern)
      rules.push({
        validator: (_rule, value, callback) => {
          if (value === '' || value === null || value === undefined) {
            return callback()
          }
          pattern.lastIndex = 0
          return pattern.test(String(value))
            ? callback()
            : callback(new Error(`${displayLabel}格式不符合正则要求`))
        },
        trigger: 'blur'
      })
    }
  }
  return rules
}

function cloneValue(value) {
  if (value === undefined) return undefined
  return JSON.parse(JSON.stringify(value))
}
