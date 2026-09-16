import { LinkageEngine } from '../utils/linkageEngine.js'
import {
  buildFlowConditionExpression,
  createFlowConditionConfig,
  createFlowConditionGroup,
  isFlowConditionGroupComplete,
  parseFlowConditionConfig,
  parseFlowConditionExpression
} from '../utils/flowConditionGroups.js'
import { safeParseConfig } from './config-runtime/index.js'

export const FIELD_STATE_CONDITIONS = [
  { name: 'visibility', title: '条件显示', configKey: 'visibilityConditionConfig', expressionKey: 'visibilityRule', description: '满足条件时显示当前字段；关闭时不附加显示条件。' },
  { name: 'disabled', title: '条件禁用', configKey: 'disabledConditionConfig', expressionKey: 'disabledRule', description: '满足条件时禁用当前字段，使字段可见但不可编辑。' },
  { name: 'required', title: '条件必填', configKey: 'requiredConditionConfig', expressionKey: 'requiredRule', description: '满足条件时要求填写当前字段；默认必填仍独立生效。' }
]

/** 读取独立的编辑副本；无法解析的历史表达式原样保留，直到用户主动清空。 */
export function readFieldStateConditions(field) {
  const rules = LinkageEngine.getFieldLinkageRules(field)
  return Object.fromEntries(FIELD_STATE_CONDITIONS.map(definition => {
    const original = {}
    for (const key of [definition.configKey, definition.expressionKey]) {
      if (rules[key] != null) original[key] = rules[key]
    }
    const enabled = Boolean(rules[definition.configKey] || rules[definition.expressionKey])
    const root = parseFlowConditionConfig(rules[definition.configKey])
      || parseFlowConditionExpression(rules[definition.expressionKey])
    return [definition.name, {
      enabled,
      root: root || createFlowConditionGroup(),
      original,
      parseWarning: enabled && !root
        ? '原配置会继续保留且不会被自动覆盖。若要使用条件组，请先清空原配置。'
        : ''
    }]
  }))
}

/** 仅用于检测外部回填，避免编辑其他组件参数时重置条件输入光标或未完成的条件。 */
export function fieldStateConditionSignature(field) {
  const rules = LinkageEngine.getFieldLinkageRules(field)
  return JSON.stringify(FIELD_STATE_CONDITIONS.map(({ configKey, expressionKey }) =>
    [rules[configKey], rules[expressionKey]]))
}

/**
 * 将一类条件写入当前节点草稿，保留其他条件及值、选项、计算联动。
 * 未完成条件也写入草稿内存，确保切换节点后可继续编辑；持久化前统一检查完整性。
 */
export function updateFieldStateCondition(field, name, state, getFieldType) {
  const definition = FIELD_STATE_CONDITIONS.find(item => item.name === name)
  if (!field || !definition) return
  const rules = { ...LinkageEngine.getFieldLinkageRules(field) }
  // 旧自由接口规则已下线，编辑状态条件时也清理其根属性副本。
  delete field.valueApi
  const keys = [definition.configKey, definition.expressionKey]
  keys.forEach(key => { delete rules[key] })
  if (state.enabled) {
    if (state.parseWarning) {
      Object.assign(rules, state.original)
    } else {
      rules[definition.configKey] = createFlowConditionConfig(state.root)
      // 不为未完成条件生成可执行表达式，避免残留旧表达式继续生效。
      if (isFlowConditionGroupComplete(state.root)) {
        rules[definition.expressionKey] = buildFlowConditionExpression(state.root, getFieldType)
      }
    }
  }
  keys.forEach(key => {
    delete field[key]
    if (Object.hasOwn(rules, key)) field[key] = rules[key]
  })
  field.linkageRules = rules
  field.componentProps = JSON.stringify({ ...safeParseConfig(field.componentProps), linkageRules: rules })
}

/** 节点保存与整表保存共用校验；无法转换的历史规则继续按原配置保存。 */
export function getFieldStateConditionError(field) {
  const states = readFieldStateConditions(field)
  const invalid = FIELD_STATE_CONDITIONS.find(({ name }) => {
    const state = states[name]
    return state.enabled && !state.parseWarning && !isFlowConditionGroupComplete(state.root)
  })
  return invalid ? `${invalid.title}尚未填写完整，请补全条件或关闭该条件` : ''
}
