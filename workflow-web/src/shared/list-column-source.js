import { applySchemaDefaults, safeParseConfig } from '@flow/workflow-core/config-runtime'

// 三种取值方式互斥；业务计算统一绑定扩展接口，不再把 Provider 类型作为第四种入口。
export const listColumnSourceOptions = [
  { value: 'ENTITY_FIELD', label: '实体字段' },
  { value: 'FIELD_TEMPLATE', label: '字段组合' },
  { value: 'INTERFACE', label: '扩展接口' }
]

/** 只有字段组合需要列级来源参数；扩展接口的实现参数在扩展管理中维护。 */
export function getListColumnSourceSchema(type, sourceOptions = []) {
  if (type !== 'FIELD_TEMPLATE') return []
  const configured = sourceOptions.find(option => option.value === 'FIELD_TEMPLATE')?.configSchema
  const schema = configured?.length ? configured : [{
    key: 'template', label: '组合模板', type: 'textarea', required: true,
    defaultValue: '${code} - ${name}'
  }]
  return schema.map(item => item.key === 'template'
    ? { ...item, helpKey: 'entityList.fieldTemplate' }
    : item)
}

/**
 * 切换取值方式时清除上一种方式的接口和参数，避免隐藏配置继续生效。
 * field 为编辑草稿，dataSourceConfig 写成对象供 Schema 编辑器使用；列保存时统一序列化。
 */
export function changeListColumnSource(field, type, sourceOptions = []) {
  field.dataSourceType = type
  field.interfaceExtensionId = ''
  field.dataSourceConfig = applySchemaDefaults(getListColumnSourceSchema(type, sourceOptions), {})
  if (type !== 'ENTITY_FIELD') field.isQuery = false
}

/** 保存前校验取值方式和必要参数；返回可直接展示的错误文案，空字符串表示有效。 */
export function validateListColumnSource(field, interfaces = [], { virtual } = {}) {
  const type = field?.dataSourceType || 'ENTITY_FIELD'
  if (!listColumnSourceOptions.some(option => option.value === type)) return '请选择取值方式'
  if (type === 'INTERFACE') {
    if (!field.interfaceExtensionId) return '请选择扩展接口'
    if (!interfaces.some(item => (item.extensionId || item.id) === field.interfaceExtensionId)) {
      return '请选择当前列表可用的扩展接口'
    }
    return ''
  }
  if (field.interfaceExtensionId) return '只有“扩展接口”取值方式可以绑定接口，请重新选择取值方式'
  if (type === 'ENTITY_FIELD') {
    const isVirtual = virtual ?? String(field.fieldId || '').startsWith('virtual_')
    return isVirtual ? '虚拟列没有实体字段，请选择字段组合或扩展接口' : ''
  }
  const template = safeParseConfig(field.dataSourceConfig).template
  return typeof template === 'string' && template.trim() ? '' : '请填写组合模板'
}
