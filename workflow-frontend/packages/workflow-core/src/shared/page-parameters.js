import { safeParseConfig } from './config-runtime/index.js'
import { normalizeInputParameterSchema, normalizeInputParameterDefaultValue, validateSubFormParameters, isEmptySubFormValue } from './subform-parameter-contract.js'

const own = (value, key) => Object.prototype.hasOwnProperty.call(value || {}, key)
const clone = value => value === undefined ? undefined : JSON.parse(JSON.stringify(value))
const blocked = new Set(['__proto__', 'constructor', 'prototype'])
// 旧发布字段只带 readonly、不带 isSystem；与后端 PublishedFormRecordView 的系统维护范围一致，
// 放开业务只读字段时仍保护审计和流程状态。name/code/deptId 等业务初值不在此范围内。
const systemManagedFields = new Set([
  'id', 'status', 'processStatus', 'processInstanceId', 'processStartTime', 'processEndTime',
  'currentTaskId', 'currentTaskName', 'currentTaskAssignee', 'submitterId', 'submitterName',
  'submitTime', 'create_time', 'update_time', 'create_by', 'update_by', 'deleted'
])

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

/**
 * 按目标表单配置为各类字段填充初值，包含只读展示字段；参数值按原类型复制。
 * 仅新增/编辑时填空值，blockedFields 用于保护入口指定的关联字段，不覆盖身份或已有值。
 */
export function initializePageFields(record, viewConfig, parameters, fields = [], blockedFields = [], mode = 'create') {
  const result = clone(record || {})
  if (!['create', 'edit'].includes(mode)) return result
  const denied = new Set(['id', ...blocked, ...blockedFields])
  const availableFields = new Set(pageParameterInitializationFields(fields).map(field => field.fieldCode))
  for (const binding of safeParseConfig(viewConfig).inputParameterBindings || []) {
    if (binding.usage !== 'INITIALIZE' || denied.has(binding.targetField)) continue
    if (!availableFields.has(binding.targetField)) continue
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

/**
 * 将表单/列表字段与发布节点统一成参数用途字段，不按字段类型或是否绑定实体筛选。
 * 自定义字段使用运行时同样的 nodeKey；布局节点仅递归收集其字段，不成为赋值目标。
 */
export function pageParameterFields(fields = []) {
  return fields.flatMap(field => {
    const props = safeParseConfig(field.propsDocument || field.props || field.legacyPropsDocument)
    const isValueNode = !field.nodeType || ['FIELD', 'SUB_FORM', 'REPEATER'].includes(String(field.nodeType).toUpperCase())
    const code = field.fieldCode || props.fieldCode || field.fieldKey
      || (field.bindingType === 'ENTITY_FIELD' ? field.bindingRef : '') || field.nodeKey
    return [...(isValueNode && code ? [{
      ...field, ...props, fieldCode: code,
      fieldLabel: field.fieldLabel || props.label || props.fieldLabel || field.fieldName || props.fieldName || code
    }] : []), ...pageParameterFields(field.children || [])]
  })
}

/**
 * 列表参数是隐藏的附加筛选，不受查询区开关或列可见性影响。
 * 调用方传入合并后的完整实体列；分页后计算的虚拟/扩展值不能用于数据库筛选。
 */
export function pageParameterFilterFields(fields = []) {
  return pageParameterFields(fields).filter(field =>
    (!field.dataSourceType || String(field.dataSourceType).trim().toUpperCase() === 'ENTITY_FIELD')
    && !field.interfaceExtensionId
    && !String(field.fieldId || '').startsWith('virtual_'))
}

/**
 * 设计器候选和运行时赋值共用同一范围：只读影响手工编辑，不阻止参数提供初值。
 * 身份和系统维护字段仍由服务端管理；同一字段的元数据与节点投影只展示一次。
 */
export function pageParameterInitializationFields(fields = []) {
  const normalized = pageParameterFields(fields)
  // name/code/deptId 也标记为 isSystem，但实体明确允许编辑，属于可初始化的业务字段。
  // 发布节点可能省略系统标记，不能用重复的节点投影绕过其余系统维护字段的保护。
  const systemFields = new Set(normalized.filter(field => [true, 1, '1'].includes(field.isSystem)
    && ![true, 1, '1'].includes(field.editable)).map(field => field.fieldCode))
  const seen = new Set()
  return normalized.filter(field => {
    if (systemManagedFields.has(field.fieldCode) || blocked.has(field.fieldCode)
      || systemFields.has(field.fieldCode) || seen.has(field.fieldCode)) return false
    seen.add(field.fieldCode)
    return true
  })
}
