import { LinkageEngine } from '@flow/workflow-core/utils/linkageEngine'
import { safeParseConfig } from '@flow/workflow-core/config-runtime'
import { isFlowConditionGroupComplete, parseFlowConditionConfig } from '@flow/workflow-core/utils/flowConditionGroups'
import { getFormContextReferences, getLinkageExpressionError } from '@flow/workflow-core/utils/calcEngine'
import { formContextParameters } from './form-context-parameters.js'

const VALUE_KEYS = ['valueMapping', 'valueFormula', 'valueFormulaMode', 'calculationFormula', 'calculationPrecision', 'calculationEditable', 'contextCalculation']
const INITIAL_FORMULA_KEYS = VALUE_KEYS.filter(key => key !== 'valueMapping')
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

/** 旧初值从联动规则读取，编辑联动时将它保存在独立属性，避免覆盖初值。 */
export function readLegacyInitialFormula(field) {
  const rules = LinkageEngine.getFieldLinkageRules(field)
  return rules.valueFormulaMode === 'initial' ? rules.calculationFormula ?? rules.valueFormula ?? '' : ''
}

export function clearLegacyInitialFormula(field) {
  if (LinkageEngine.getFieldLinkageRules(field).valueFormulaMode === 'initial') {
    patchFieldLinkageRules(field, INITIAL_FORMULA_KEYS, {})
  }
}

/** 旧计算规则在值联动中编辑，保留原键和精度等参数；打开面板本身不迁移数据。 */
export function readFieldValueLinkage(field) {
  const rules = LinkageEngine.getFieldLinkageRules(field)
  const isReactive = rules.valueFormulaMode !== 'initial'
  const calculation = isReactive && Object.hasOwn(rules, 'calculationFormula')
  const valueFormula = isReactive && Object.hasOwn(rules, 'valueFormula')
  return {
    valueEnabled: calculation || valueFormula || !!rules.valueMapping,
    // 历史运行时最后应用计算字段，所以同时存在旧规则时展示实际生效的公式。
    sourceType: calculation || !rules.valueMapping && valueFormula ? 'formula' : 'field',
    formulaKey: calculation ? 'calculationFormula' : 'valueFormula',
    formula: isReactive ? rules.calculationFormula ?? rules.valueFormula ?? '' : '',
    calculationPrecision: calculation ? rules.calculationPrecision : undefined,
    calculationEditable: calculation ? rules.calculationEditable : undefined,
    contextModes: [...(rules.contextCalculation?.modes || ['create', 'edit', 'approve'])],
    sourceField: rules.valueMapping?.sourceField || '',
    mappings: clone(rules.valueMapping?.rules || []),
    fallback: clone(rules.valueMapping?.fallback ?? null),
    optionsEnabled: !!rules.optionsLinkage,
    optionsMode: rules.optionsLinkage?.mode || 'filter',
    parameterMappings: Array.isArray(rules.optionsLinkage?.parameterMappings) ? clone(rules.optionsLinkage.parameterMappings) : [],
    dependsOn: rules.optionsLinkage?.dependsOn || '',
    filters: Object.entries(rules.optionsLinkage?.filterRules || {}).map(([dependValue, allowedOptions]) => ({ dependValue, allowedOptions: clone(allowedOptions) }))
  }
}

/** 将一组内联输入同步到节点草稿；值和选项分别更新，互不清空。 */
export function updateFieldValueLinkage(field, model, group) {
  if (group === 'options') {
    const optionsLinkage = model.optionsMode === 'query'
      ? { mode: 'query', parameterMappings: model.parameterMappings }
      : { dependsOn: model.dependsOn, filterRules: Object.fromEntries(model.filters.map(row => [row.dependValue, row.allowedOptions])) }
    patchFieldLinkageRules(field, ['optionsLinkage'], model.optionsEnabled ? { optionsLinkage } : {})
    return
  }
  const initialFormula = readLegacyInitialFormula(field)
  if (initialFormula && field.defaultFormula == null) field.defaultFormula = initialFormula
  const patch = {}
  if (model.valueEnabled) {
    if (model.sourceType === 'field') {
      patch.valueMapping = { sourceField: model.sourceField, rules: model.mappings }
      if (model.fallback) patch.valueMapping.fallback = model.fallback
    } else {
      patch[model.formulaKey] = normalizeFormulaReferences(model.formula)
      if (model.formula.includes('${ctx.')) {
        patch.contextCalculation = { expressionVersion: 1, modes: model.contextModes }
      }
      if (model.formulaKey === 'calculationFormula') {
        // 切到其他方式会清除节点里的旧公式规则，切回时从编辑器草稿恢复历史计算设置。
        for (const key of ['calculationPrecision', 'calculationEditable']) {
          if (model[key] !== undefined) patch[key] = model[key]
        }
      }
    }
  }
  patchFieldLinkageRules(field, VALUE_KEYS, patch)
}

/** 兼容以裸字段名书写的旧公式；函数、字符串和显式字段引用保留原样。 */
function normalizeFormulaReferences(formula) {
  const tokens = /"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|[$#]\{[^}]*\}|\b[A-Za-z_]\w*\b/g
  return formula.replace(tokens, (token, index) => {
    if (!/^[A-Za-z_]/.test(token) || ['true', 'false', 'null'].includes(token)
        || /^\s*\(/.test(formula.slice(index + token.length))) return token
    return '${' + token + '}'
  })
}

/** 保存前检查未完成或互相覆盖的映射，空字符串本身可作为合法映射值。 */
export function getFieldValueLinkageError(model, { querySourceAvailable = true, fieldCodes } = {}) {
  if (model.valueEnabled) {
    if (model.sourceType === 'formula') {
      if (!model.formula.trim()) return '请填写公式，或将值联动方式设为“不启用”'
      const error = getLinkageExpressionError(model.formula)
      if (error) return '的计算公式有误（' + error + '）'
      const keys = getFormContextReferences(model.formula)
      if (keys.some(key => !formContextParameters.some(parameter => parameter.key === key))) return '引用了未知的上下文参数'
      if (keys.length && !model.contextModes.length) return '请选择上下文公式生效模式'
    }
    if (model.sourceType === 'field') {
      if (!model.sourceField || (!model.mappings.length && !model.fallback)) return '请填写源字段和映射规则，或将值联动方式设为“不启用”'
      if (new Set(model.mappings.map(row => String(row.sourceValue))).size !== model.mappings.length) return '值联动的源值不能重复'
    }
  }
  if (model.optionsEnabled) {
    if (model.optionsMode === 'query') {
      if (!querySourceAvailable) return '请绑定实体只读选项接口后再配置选项联动查询'
      const mappings = model.parameterMappings
      if (!mappings.length || mappings.some(row => !row?.fieldCode || !/^[A-Za-z_][A-Za-z0-9_]*$/.test(row.parameter || ''))) {
        return '请配置选项联动的依赖字段和参数名，参数名使用字母、数字或下划线，且以字母或下划线开头'
      }
      if (fieldCodes && mappings.some(row => !fieldCodes.includes(row.fieldCode)
          && !formContextParameters.some(parameter => row.fieldCode === 'ctx.' + parameter.key))) {
        return '选项联动引用了不可用的依赖字段或参数'
      }
      return new Set(mappings.map(row => row.parameter)).size === mappings.length ? '' : '选项联动的参数名不能重复'
    }
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
