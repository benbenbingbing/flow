import { safeParseConfig } from '@flow/workflow-core/config-runtime'

/** 保存、发布前拒绝旧候选配置；运行时的代码表和动态选项通过独立 options 参数传递。 */
export function assertNoEmbeddedSelectionOptions(field) {
  const types = [field.componentType, field.fieldType].map(type => String(type || '').toUpperCase())
  if (!types.some(type => ['SELECT', 'SELECT_MULTIPLE', 'MULTI_SELECT', 'RADIO', 'CHECKBOX'].includes(type))) return
  const configured = value => Array.isArray(value) ? value.length > 0 : value != null && String(value).trim() !== ''
  const component = safeParseConfig(field.componentProps)
  if ([field.options, field.optionsJson, component.options, component.optionsJson].some(configured)) {
    throw new Error(`${field.fieldLabel || field.fieldName || field.fieldCode || '选择字段'}不再支持内嵌选项，请移除旧配置并改用代码表或动态数据源`)
  }
}
