import { safeParseConfig, isFieldReadonlyForMode } from './config-runtime/index.js'
import { normalizeInputParameterSchema, normalizeInputParameterDefaultValue, validateSubFormParameters, isEmptySubFormValue } from './subform-parameter-contract.js'

const own = (value, key) => Object.prototype.hasOwnProperty.call(value || {}, key)
const clone = value => value === undefined ? undefined : JSON.parse(JSON.stringify(value))
const blocked = new Set(['__proto__', 'constructor', 'prototype'])

/** 仅复制声明的来源字段；表单调用方传最新编辑值，列表调用方传当前行，均不改变记录身份或授权。 */
export function mapPageParameters(mappings = [], source = {}) {
  const result = {}
  const seen = new Set()
  for (const mapping of mappings || []) {
    const key = String(mapping.parameter || '')
    if (!/^[A-Za-z][A-Za-z0-9_]{0,99}$/.test(key) || blocked.has(key)) throw new Error('目标参数编码不合法')
    if (seen.has(key)) throw new Error(`参数 ${key} 重复配置`)
    seen.add(key)
    let value
    if (mapping.sourceType === 'FIELD') {
      const data = source.data || {}
      const field = mapping.sourceField
      value = own(data, field) ? data[field]
        : own(data.extData, field) ? data.extData[field] : own(data.data, field) ? data.data[field] : undefined
    } else if (mapping.sourceType === 'RECORD_ID') value = source.recordId
    else if (mapping.sourceType === 'PARAMETER') value = source.params?.[mapping.sourceField]
    else if (mapping.sourceType === 'LITERAL') value = mapping.value
    else throw new Error(`参数 ${key} 的来源不合法`)
    // 每次打开时生成独立快照，目标编辑不能反向修改来源数据。
    if (value !== undefined) result[key] = clone(value)
  }
  return result
}

/** 根据目标页面的发布契约补默认值并校验类型；未声明参数不能进入页面业务上下文。 */
export function resolvePageParameters(viewConfig, supplied = {}) {
  const config = safeParseConfig(viewConfig)
  if (!config.inputParameterSchema) return clone(supplied || {})
  const schema = normalizeInputParameterSchema(config.inputParameterSchema)
  const result = {}
  for (const [key, definition] of Object.entries(schema.properties)) {
    if (blocked.has(key)) throw new Error('输入参数编码不合法')
    const value = own(supplied, key) && supplied[key] !== undefined ? supplied[key] : definition.default
    if (definition.type === 'string' && value !== null && typeof value === 'object') throw new Error(`页面输入参数 ${key} 需要文本值`)
    if (value !== undefined) result[key] = normalizeInputParameterDefaultValue(value, definition.type)
  }
  const errors = validateSubFormParameters(result, schema)
  if (errors.length) throw new Error(`页面输入参数：${errors.join('；')}`)
  return clone(result)
}

/** 目标页面决定如何使用参数。初始化只填可编辑空字段，不覆盖记录身份、关联外键或已有值。 */
export function initializePageFields(record, viewConfig, parameters, fields = [], blockedFields = [], mode = 'create') {
  const result = clone(record || {})
  const denied = new Set(['id', ...blocked, ...blockedFields])
  for (const binding of safeParseConfig(viewConfig).inputParameterBindings || []) {
    if (binding.usage !== 'INITIALIZE' || denied.has(binding.targetField)) continue
    const field = fields.find(item => (item.fieldCode || item.fieldKey) === binding.targetField)
    if (!field || isFieldReadonlyForMode(field, mode) || field.isReadonly === '1' || field.isReadonly === true || field.isReadonly === 1 || field.readonly === true
      || field.isSystem === true || field.isSystem === 1 || field.isSystem === '1' || field.props?.readonly === true) continue
    if (isEmptySubFormValue(result[binding.targetField]) && parameters?.[binding.parameter] !== undefined) {
      result[binding.targetField] = clone(parameters[binding.parameter])
    }
  }
  return result
}

/** 配置时检查来源字段和目标声明，发布前及运行时仍需各自复核。 */
export function validatePageParameterMappings(mappings = [], schemaValue, sourceFields = []) {
  const schema = normalizeInputParameterSchema(schemaValue)
  const keys = new Set()
  for (const mapping of mappings || []) {
    if (!own(schema.properties, mapping.parameter)) return `目标页面未声明参数 ${mapping.parameter || '（未选择）'}`
    if (keys.has(mapping.parameter)) return `参数 ${mapping.parameter} 重复配置`
    keys.add(mapping.parameter)
    if (mapping.sourceType === 'FIELD' && !sourceFields.some(field => field.fieldCode === mapping.sourceField)) return `来源字段 ${mapping.sourceField || '（未选择）'} 不存在`
    if (mapping.sourceType === 'PARAMETER' && !mapping.sourceField) return '请选择来源参数'
  }
  return ''
}

/** 将字段和实体绑定节点统一成参数用途可选字段，保留节点的只读限制。 */
export function pageParameterFields(fields = []) {
  return fields.flatMap(field => {
    const props = safeParseConfig(field.propsDocument || field.props || field.legacyPropsDocument)
    const code = field.fieldCode || props.fieldCode || (field.bindingType === 'ENTITY_FIELD' ? field.bindingRef : '')
    return [...(code ? [{ ...field, ...props, fieldCode: code }] : []), ...pageParameterFields(field.children || [])]
  })
}
