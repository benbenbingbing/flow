import { resolveFormFieldKey } from '../form-field-uniqueness.js'

export const FORM_UNIQUE_PRECHECK_CONTEXT_KEY = Symbol(
  'form-unique-precheck-context'
)

const CHANGE_ONLY_UNIQUE_COMPONENTS = new Set([
  'RADIO',
  'SWITCH',
  'ENTITY',
  'ENTITY_SELECTOR',
  'REFERENCE',
  'MULTI_REFERENCE',
  'USER',
  'DEPT',
  'ROLE',
  'GROUP',
  'CUSTOM'
])
const CHANGE_ONLY_UNIQUE_FIELD_TYPES = new Set([
  'BOOLEAN',
  'ENTITY',
  'ENTITY_SELECTOR',
  'REFERENCE',
  'MULTI_REFERENCE',
  'USER',
  'DEPT',
  'ROLE',
  'GROUP',
  'CUSTOM'
])
const CHANGE_ONLY_REFERENCE_TYPES = new Set([
  'USER',
  'DEPT',
  'ROLE',
  'GROUP',
  'CUSTOM'
])

/** 没有稳定 blur 事件的选择控件用 change 触发其已配置的“失焦检查”。 */
export function resolveFormUniqueValidationTrigger(field = {}) {
  const componentType = String(field.componentType || '').trim().toUpperCase()
  const fieldType = String(field.fieldType || '').trim().toUpperCase()
  const referenceType = String(field.refEntityType || '').trim().toUpperCase()
  return CHANGE_ONLY_UNIQUE_COMPONENTS.has(componentType)
    || CHANGE_ONLY_UNIQUE_FIELD_TYPES.has(fieldType)
    || CHANGE_ONLY_REFERENCE_TYPES.has(referenceType)
    || Boolean(field.refEntityId)
    ? 'change'
    : 'blur'
}

/**
 * Element Plus 的失焦规则只负责把预检结果接入字段校验；变化防抖、条件字段
 * 监听和过期响应丢弃统一由表单级控制器处理。
 */
export function appendFormUniqueBlurRule(rules, field, context) {
  const rule = context?.resolveRule?.(field)
  if (!rule?.precheck?.enabled || rule.precheck.trigger !== 'BLUR') {
    return rules
  }
  rules.push({
    validator: (_validationRule, _value, callback) => {
      context.check(field, 'BLUR').then(result => {
        if (result?.available === false && result?.checked === true) {
          callback(new Error(result.message))
        } else {
          callback()
        }
      }).catch(() => callback())
    },
    trigger: resolveFormUniqueValidationTrigger(field)
  })
  return rules
}

/**
 * 为自定义整表单提供与内置字段渲染器等价的唯一预检入口。
 *
 * 自定义组件应在真实输入失焦时调用 `context.formUniqueness.onFieldBlur`
 * （参数可传字段对象或 fieldCode）。`checkField` 用于没有标准 blur 事件的
 * 复合控件；reason 仍严格遵循发布规则，不会绕过 CHANGE/BLUR/SUBMIT_ONLY。
 */
export function createFormUniquePrecheckRuntime({
  controller,
  getFields = () => [],
  getRecord = () => ({}),
  getErrors = () => ({})
}) {
  function resolveField(fieldOrCode) {
    if (fieldOrCode && typeof fieldOrCode === 'object') return fieldOrCode
    const fieldCode = String(fieldOrCode || '').trim()
    if (!fieldCode) return null
    return (getFields() || []).find(field =>
      resolveFormFieldKey(field) === fieldCode
    ) || null
  }

  async function checkField(fieldOrCode, reason = 'BLUR') {
    const field = resolveField(fieldOrCode)
    if (!field || typeof controller?.check !== 'function') {
      return { available: true, checked: false }
    }
    return controller.check(field, getRecord() || {}, { reason })
  }

  return {
    get errors() {
      return getErrors() || {}
    },
    checkField,
    onFieldBlur: fieldOrCode => checkField(fieldOrCode, 'BLUR')
  }
}
