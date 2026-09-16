import { LinkageEngine } from '../utils/linkageEngine.js'
import { safeParseConfig } from './config-runtime/index.js'
import { isFlowConditionGroupComplete, parseFlowConditionConfig } from '../utils/flowConditionGroups.js'

const VALUE_KEYS = ['valueMapping', 'valueFormula', 'calculationFormula', 'calculationPrecision', 'calculationEditable']
const clone = value => JSON.parse(JSON.stringify(value))

/** 只替换当前编辑器负责的键，保留状态条件和其他组件参数，移除废弃接口规则。 */
export function patchFieldLinkageRules(field, keys, patch) {
  if (!field) return
  const rules = { ...LinkageEngine.getFieldLinkageRules(field) }
  const componentProps = { ...safeParseConfig(field.componentProps) }
  delete field.valueApi
  for (const key of keys) {
    delete rules[key]
    delete field[key]
  }
  Object.assign(rules, clone(patch))
  for (const key of keys) {
    if (Object.hasOwn(rules, key)) field[key] = rules[key]
  }
  // 附件条件存在历史顶层副本；更新时必须同步，否则运行时优先读取旧副本。
  if (keys.includes('attachmentItemRequiredRules')) {
    delete componentProps.attachmentItemRequiredRules
    if (rules.attachmentItemRequiredRules) componentProps.attachmentItemRequiredRules = rules.attachmentItemRequiredRules
  }
  field.linkageRules = rules
  field.componentProps = JSON.stringify({ ...componentProps, linkageRules: rules })
}

export function fieldValueLinkageSignature(field) {
  const rules = LinkageEngine.getFieldLinkageRules(field)
  return JSON.stringify([...VALUE_KEYS, 'optionsLinkage'].map(key => rules[key]))
}

/** 旧计算规则在值联动中编辑，保留原键和精度等参数；打开面板本身不迁移数据。 */
export function readFieldValueLinkage(field) {
  const rules = LinkageEngine.getFieldLinkageRules(field)
  const calculation = Object.hasOwn(rules, 'calculationFormula')
  return {
    valueEnabled: calculation || Object.hasOwn(rules, 'valueFormula') || !!rules.valueMapping,
    // 历史运行时最后应用计算字段，所以同时存在旧规则时展示实际生效的公式。
    sourceType: calculation || !rules.valueMapping && Object.hasOwn(rules, 'valueFormula') ? 'formula' : 'field',
    formulaKey: calculation ? 'calculationFormula' : 'valueFormula',
    formula: rules.calculationFormula ?? rules.valueFormula ?? '',
    sourceField: rules.valueMapping?.sourceField || '',
    mappings: clone(rules.valueMapping?.rules || []),
    optionsEnabled: !!rules.optionsLinkage,
    dependsOn: rules.optionsLinkage?.dependsOn || '',
    filters: Object.entries(rules.optionsLinkage?.filterRules || {}).map(([dependValue, allowedOptions]) => ({ dependValue, allowedOptions: clone(allowedOptions) }))
  }
}

/** 将一组内联输入同步到节点草稿；值和选项分别更新，互不清空。 */
export function updateFieldValueLinkage(field, model, group) {
  if (group === 'options') {
    patchFieldLinkageRules(field, ['optionsLinkage'], model.optionsEnabled ? {
      optionsLinkage: { dependsOn: model.dependsOn, filterRules: Object.fromEntries(model.filters.map(row => [row.dependValue, row.allowedOptions])) }
    } : {})
    return
  }
  const patch = {}
  if (model.valueEnabled) {
    if (model.sourceType === 'field') {
      patch.valueMapping = { sourceField: model.sourceField, rules: model.mappings }
    } else {
      // 保持历史公式字段引用的输入兼容；显式 ${...} 表达式不作改写。
      patch[model.formulaKey] = model.formula.includes('${') ? model.formula : model.formula.replace(/\b([a-zA-Z_][a-zA-Z0-9_]*)\b/g, '${$1}')
      if (model.formulaKey === 'calculationFormula') {
        const current = LinkageEngine.getFieldLinkageRules(field)
        for (const key of ['calculationPrecision', 'calculationEditable']) {
          if (Object.hasOwn(current, key)) patch[key] = current[key]
        }
      }
    }
  }
  patchFieldLinkageRules(field, VALUE_KEYS, patch)
}

/** 保存前检查未完成或互相覆盖的映射，空字符串本身可作为合法映射值。 */
export function getFieldValueLinkageError(model) {
  if (model.valueEnabled) {
    if (model.sourceType === 'formula' && !model.formula.trim()) return '请填写值联动的计算公式或关闭值联动'
    if (model.sourceType === 'field') {
      if (!model.sourceField || !model.mappings.length) return '请填写值联动的源字段和映射规则，或关闭值联动'
      if (new Set(model.mappings.map(row => String(row.sourceValue))).size !== model.mappings.length) return '值联动的源值不能重复'
    }
  }
  if (model.optionsEnabled) {
    if (!model.dependsOn || !model.filters.length) return '请填写选项联动的依赖字段和过滤规则，或关闭选项联动'
    if (new Set(model.filters.map(row => String(row.dependValue))).size !== model.filters.length) return '选项联动的依赖值不能重复'
  }
  return ''
}

export function getAttachmentConditionError(field, attachmentItems) {
  const items = LinkageEngine.getFieldLinkageRules(field).attachmentItemRequiredRules?.items || []
  for (const item of items) {
    const attachment = attachmentItems.find(candidate => candidate.itemKey === item.itemKey)
    if (!attachment || [true, 1, '1'].includes(attachment.required)) continue
    if (!isFlowConditionGroupComplete(parseFlowConditionConfig(item.requiredConditionConfig))) {
      return `附件项“${attachment.itemName || item.itemKey}”的必填条件尚未填写完整`
    }
  }
  return ''
}
