import { applySchemaDefaults, sanitizeConfigObject } from '@flow/workflow-core/config-runtime'
import { normalizeCascaderOptions } from '@flow/workflow-core/cascader-runtime'

/**
 * 级联树使用自己的节点白名单和规模校验。通用配置的深度/数组裁剪会截断合法树，
 * 因此仅对清单声明的级联选项单独规范化；编辑其他参数时也要完整保留已有树。
 */
export function sanitizeComponentConfig(schema, value) {
  if (!schema.some(item => item.key === 'cascaderOptions' && item.type === 'json')) {
    return sanitizeConfigObject(value)
  }
  const { cascaderOptions, ...rest } = value
  const result = sanitizeConfigObject(rest)
  if (cascaderOptions != null) result.cascaderOptions = normalizeCascaderOptions(cascaderOptions)
  return result
}

/** 返回首个尚未填写的可见必填参数，用于在组件选择旁提示。 */
export function getMissingComponentConfig(schema, config) {
  const values = applySchemaDefaults(schema, config)
  return schema.find(item => {
    if (!item.required || !isSchemaItemVisible(item.visibleWhen, values)) return false
    const value = values[item.key]
    return value == null || (typeof value === 'string' && !value.trim()) || (Array.isArray(value) && !value.length)
  })
}

/** 配置编辑器和配置状态共用可见规则，隐藏参数不参与必填提示。 */
export function isSchemaItemVisible(rule, values) {
  if (rule === undefined || rule === null) return true
  if (typeof rule === 'boolean') return rule
  if (typeof rule === 'string') return Boolean(getValueByPath(values, rule))
  if (typeof rule === 'function') {
    try {
      return rule(values) !== false
    } catch {
      return false
    }
  }
  if (Array.isArray(rule)) {
    return rule.every(item => isSchemaItemVisible(item, values))
  }
  if (typeof rule !== 'object') return Boolean(rule)

  const allRules = rule.all || rule.and
  if (Array.isArray(allRules) && !allRules.every(item => isSchemaItemVisible(item, values))) {
    return false
  }
  const anyRules = rule.any || rule.or
  if (Array.isArray(anyRules) && !anyRules.some(item => isSchemaItemVisible(item, values))) {
    return false
  }
  if (rule.not !== undefined && isSchemaItemVisible(rule.not, values)) {
    return false
  }

  const path = rule.key || rule.field || rule.path
  if (path) {
    return matchVisibleValue(getValueByPath(values, path), rule)
  }

  const reservedKeys = new Set(['all', 'and', 'any', 'or', 'not'])
  return Object.entries(rule)
    .filter(([key]) => !reservedKeys.has(key))
    .every(([key, expected]) => valuesEqual(getValueByPath(values, key), expected))
}

function matchVisibleValue(actual, rule) {
  if (Object.prototype.hasOwnProperty.call(rule, 'equals')) {
    return valuesEqual(actual, rule.equals)
  }
  if (Object.prototype.hasOwnProperty.call(rule, 'notEquals')) {
    return !valuesEqual(actual, rule.notEquals)
  }
  if (Array.isArray(rule.in)) return rule.in.some(item => valuesEqual(actual, item))
  if (Array.isArray(rule.notIn)) return !rule.notIn.some(item => valuesEqual(actual, item))
  if (Object.prototype.hasOwnProperty.call(rule, 'includes')) {
    return Array.isArray(actual) || typeof actual === 'string'
      ? actual.includes(rule.includes)
      : false
  }
  if (Object.prototype.hasOwnProperty.call(rule, 'exists')) {
    const exists = actual !== undefined && actual !== null
    return rule.exists ? exists : !exists
  }
  if (rule.truthy === true) return Boolean(actual)
  if (rule.falsy === true) return !actual

  const expected = Object.prototype.hasOwnProperty.call(rule, 'value')
    ? rule.value
    : rule.is
  switch (String(rule.operator || '').toLowerCase()) {
    case 'ne':
    case '!=':
    case 'notequals':
      return !valuesEqual(actual, expected)
    case 'in':
      return Array.isArray(expected) && expected.some(item => valuesEqual(actual, item))
    case 'notin':
      return Array.isArray(expected) && !expected.some(item => valuesEqual(actual, item))
    case 'includes':
      return Array.isArray(actual) || typeof actual === 'string'
        ? actual.includes(expected)
        : false
    case 'truthy':
      return Boolean(actual)
    case 'falsy':
      return !actual
    case 'exists':
      return actual !== undefined && actual !== null
    default:
      return expected === undefined ? Boolean(actual) : valuesEqual(actual, expected)
  }
}

function getValueByPath(source, path) {
  return String(path)
    .split('.')
    .filter(Boolean)
    .reduce((value, key) => value?.[key], source)
}

function valuesEqual(left, right) {
  if (Object.is(left, right)) return true
  if (
    left && right
    && typeof left === 'object'
    && typeof right === 'object'
  ) {
    try {
      return JSON.stringify(left) === JSON.stringify(right)
    } catch {
      return false
    }
  }
  return false
}
