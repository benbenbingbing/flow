import { safeParseConfig, stringifyConfig } from '@flow/workflow-core/config-runtime'
import { getLinkageExpressionError } from '@flow/workflow-core/utils/calcEngine'
import { resolveInterfaceExtensionId } from '../components/ui-config/interfaceExtensionModel.js'
import { parseJsonConfig } from '../utils/jsonConfig.js'
import { clearLegacyInitialFormula, readLegacyInitialFormula } from './form-field-linkage.js'

export function readFieldDefaultFormula(field) {
  return field.defaultFormula ?? readLegacyInitialFormula(field)
}

/** 旧表单可同时配置固定值和接口回退。打开新入口时只推断展示来源，保留历史执行顺序。 */
export function readFieldDefaultMode(field) {
  if (field.defaultValueMode) return field.defaultValueMode
  if (safeParseConfig(field.dataSourceBindings).FIELD_DEFAULT) return 'dataSource'
  if (field.defaultFormula != null || readFieldDefaultFormula(field)) return 'formula'
  return field.defaultValue == null || field.defaultValue === '' ? 'inherit' : 'fixed'
}

export function readFieldDefaultDataSourceDraft(field, interfaces = []) {
  if (field.defaultDataSourceDraft) return field.defaultDataSourceDraft
  const binding = safeParseConfig(field.dataSourceBindings).FIELD_DEFAULT || {}
  return {
    extensionId: resolveInterfaceExtensionId(binding, interfaces),
    inputMappingText: stringifyConfig(binding.inputMapping || {}),
    outputMappingText: stringifyConfig(binding.outputMapping || {})
  }
}

/** 主动切换来源时清理旧的取值配置，实时联动与其他用途的数据源可以继续使用。 */
export function changeFieldDefaultMode(field, mode) {
  const formula = readFieldDefaultFormula(field)
  field.defaultValueMode = mode
  field.defaultValue = mode === 'fixed' ? field.defaultValue : undefined
  field.defaultFormula = mode === 'formula' ? formula : undefined
  clearLegacyInitialFormula(field)
  if (mode !== 'dataSource') {
    const { FIELD_DEFAULT: omittedDefault, ...bindings } = safeParseConfig(field.dataSourceBindings)
    field.dataSourceBindings = bindings
    field.defaultDataSourceDraft = undefined
  }
}

export function writeFieldDefaultFormula(field, formula) {
  field.defaultFormula = formula
  clearLegacyInitialFormula(field)
}

/** 保存前校验新入口的草稿，避免把输入中的无效 JSON 当作空映射保存。 */
export function getFieldDefaultError(field) {
  if (field.defaultValueMode === 'fixed' && (field.defaultValue == null || field.defaultValue === '')) {
    return '请填写固定默认值或选择其他来源'
  }
  const formula = readFieldDefaultFormula(field).trim()
  if (formula || readFieldDefaultMode(field) === 'formula') {
    if (!formula) return '请填写默认值公式'
    const error = getLinkageExpressionError(formula)
    if (error) return '的默认值公式有误（' + error + '）'
  }
  const draft = field.defaultDataSourceDraft
  if (field.defaultValueMode === 'dataSource' && !readFieldDefaultDataSourceDraft(field).extensionId) {
    return '请为默认值选择扩展接口'
  }
  if (draft?.extensionId) {
    try {
      parseJsonConfig(draft.inputMappingText, { fieldName: '默认值输入映射' })
      parseJsonConfig(draft.outputMappingText, { fieldName: '默认值输出映射' })
    } catch (error) {
      return error.message
    }
  }
  return ''
}
