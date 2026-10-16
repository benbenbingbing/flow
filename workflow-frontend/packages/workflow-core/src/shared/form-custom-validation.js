import { getCustomValidator, isCustomValidatorApplicable } from '../extensions/core/registries/validatorRegistry.js'
import { validateCustomValue } from '../extensions/contracts/validation.js'

export const CUSTOM_VALIDATION_VERSION = 1
export const CUSTOM_VALIDATION_MAX_RULES = 20
export const CUSTOM_VALIDATION_CONTEXT_KEY = Symbol('form-custom-validation')
const NAME_PATTERN = /^[A-Za-z][A-Za-z0-9_.:-]{0,99}$/

/** 规则读取保留无效形状，让设计器和运行时显式报错，不能把坏配置吞成空数组。 */
export function getCustomValidationConfig(field) {
  const raw = field?.validationRules ?? field?.validateRules
  if (typeof raw === 'string') return JSON.parse(raw || '{}')?.customValidators
  return raw?.customValidators
}

/** 获取参数默认值的独立副本；写入字段绑定后不依赖其他表单的参数。 */
export function customValidatorDefaults(descriptor) {
  return Object.fromEntries((descriptor?.configSchema || [])
    .filter(item => item.defaultValue !== undefined)
    .map(item => [item.key, cloneValidationValue(item.defaultValue)]))
}

/** 校验参数形状与 Schema；运行前同样检查，避免手工修改 JSON 绕过设计器。 */
function parameterError(params, descriptor) {
  if (!params || typeof params !== 'object' || Array.isArray(params)) return '参数必须为对象'
  let count = 0
  const jsonValue = (value, depth = 0) => {
    if (++count > 1000 || depth > 12) return false
    if (value === null || typeof value === 'boolean') return true
    if (typeof value === 'string') return value.length <= 10000
    if (typeof value === 'number') return Number.isFinite(value)
    if (Array.isArray(value)) return value.every(item => jsonValue(item, depth + 1))
    if (value && typeof value === 'object' && Object.getPrototypeOf(value) === Object.prototype) {
      return Object.entries(value).every(([key, item]) => key.length <= 100 && jsonValue(item, depth + 1))
    }
    return false
  }
  if (!jsonValue(params)) return '参数必须是有限大小的 JSON 数据'
  const schema = descriptor.configSchema
  if (Object.keys(params).some(key => !schema.some(item => item.key === key))) return '包含未声明的校验参数'
  const values = { ...customValidatorDefaults(descriptor), ...params }
  for (const item of schema) {
    const value = values[item.key]
    const label = item.label || item.key
    if (value === undefined || value === null || value === '') {
      if (item.required) return `${label}不能为空`
      continue
    }
    if (item.type === 'number' && (typeof value !== 'number' || !Number.isFinite(value)
        || (item.min !== undefined && value < item.min) || (item.max !== undefined && value > item.max))) {
      return `${label}必须是允许范围内的数字`
    }
    if (item.type === 'boolean' && typeof value !== 'boolean') return `${label}必须是布尔值`
    if (['text', 'textarea'].includes(item.type) && typeof value !== 'string') return `${label}必须是文本`
    if (item.type === 'select') {
      const values = item.multiple ? value : [value]
      if (!Array.isArray(values) || values.some(entry => !(item.options || []).some(option => option.value === entry))) return `${label}不在候选值中`
    }
    if (item.type === 'json' && (typeof value !== 'object' || value === null
        || (item.jsonShape === 'array' && !Array.isArray(value))
        || (item.jsonShape === 'object' && Array.isArray(value)))) return `${label}的 JSON 结构不正确`
  }
  return ''
}

/**
 * 检查字段绑定结构、精确版本、实体范围、字段类型和参数。
 * undefined 表示尚未配置；清空保存 { version: 1, rules: [] }，防止旧投影复活。
 * @returns {string[]} 无错误时 []，不执行业务校验方法、不修改配置。
 */
export function validateCustomValidationConfig(config, field = {}, entityCode = '') {
  if (config === undefined) return []
  if (!config || config.version !== 1 || !Array.isArray(config.rules)
      || Object.keys(config).some(key => !['version', 'rules'].includes(key))) return ['自定义校验配置必须包含 version: 1 和 rules 数组']
  if (config.rules.length > CUSTOM_VALIDATION_MAX_RULES) return ['每个字段最多配置 20 个自定义校验器']
  const errors = []
  const seen = new Set()
  for (const rule of config.rules) {
    if (!rule || typeof rule.name !== 'string' || !NAME_PATTERN.test(rule.name) || !Number.isSafeInteger(rule.version) || rule.version < 1
        || Object.keys(rule).some(key => !['name', 'version', 'params', 'triggers'].includes(key))) {
      errors.push('请选择有效的校验器和版本')
      continue
    }
    const key = `${rule.name}@${rule.version}`
    if (seen.has(key)) errors.push(`校验器 ${key} 重复配置`)
    seen.add(key)
    if (!Array.isArray(rule.triggers) || rule.triggers.some(trigger => !['BLUR', 'CHANGE'].includes(trigger))
        || new Set(rule.triggers).size !== rule.triggers.length) errors.push(`${key} 的触发时机不合法`)
    const descriptor = getCustomValidator(rule.name, rule.version)
    if (!descriptor) errors.push(`校验器 ${key} 未安装`)
    else if (!isCustomValidatorApplicable(descriptor, entityCode, field.fieldType)) errors.push(`校验器 ${key} 不适用于当前实体或字段类型`)
    else {
      const error = parameterError(rule.params, descriptor)
      if (error) errors.push(`${descriptor.label}：${error}`)
    }
  }
  return errors
}

/** 复制表单/参数数据供校验器读取，保留 0、false、NaN 和 Date，不修改宿主响应式对象。 */
export function cloneValidationValue(value) {
  if (value instanceof Date) return new Date(value)
  if (Array.isArray(value)) return value.map(cloneValidationValue)
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, cloneValidationValue(item)]))
  return value
}

/**
 * 判断当前交互是否有需要执行的规则。配置错误仍进入校验流程并报错，
 * 合法但未匹配的交互不能清空其他时机的错误，也不能取消正在执行的校验。
 */
export function hasCustomValidationTrigger(field, trigger, entityCode) {
  try {
    const config = getCustomValidationConfig(field)
    if (validateCustomValidationConfig(config, field, entityCode).length) return true
    return trigger === 'SUBMIT' || (config?.rules || []).some(rule => matchesTrigger(rule, trigger))
  } catch {
    return true // 非法 JSON 由执行入口转换为可读错误，不能作为“未配置”跳过。
  }
}

/** 严格遵守配置；复合控件也不能用 change 冒充 blur，未提供 blur 时由提交兜底。 */
function matchesTrigger(rule, trigger) {
  return trigger === 'SUBMIT' || rule.triggers.includes(trigger)
}

/**
 * 执行一个字段的已发布绑定。SUBMIT 始终执行全部规则，triggers 只控制输入反馈。
 * 校验器异常返回失败提示；字段隐藏、只读等跳过策略由表单控制器统一决定。
 */
export async function evaluateCustomValidators(field, value, context, trigger, signal) {
  const config = getCustomValidationConfig(field)
  const errors = validateCustomValidationConfig(config, field, context.entityCode)
  if (errors.length) return { valid: false, message: errors[0] }
  for (const rule of config?.rules || []) {
    if (!matchesTrigger(rule, trigger)) continue
    const descriptor = getCustomValidator(rule.name, rule.version)
    try {
      signal?.throwIfAborted()
      const result = await validateCustomValue(descriptor.validator, cloneValidationValue(value), {
        ...cloneValidationValue(context), field: cloneValidationValue(field), fieldCode: field.fieldCode,
        params: { ...customValidatorDefaults(descriptor), ...cloneValidationValue(rule.params) }, trigger, signal
      })
      if (!result.valid) return { ...result, ruleName: rule.name }
    } catch (error) {
      if (signal?.aborted) throw error
      return { valid: false, message: `${descriptor.label}执行失败：${error?.message || '请重试'}`, ruleName: rule.name }
    }
  }
  return { valid: true, message: '' }
}
