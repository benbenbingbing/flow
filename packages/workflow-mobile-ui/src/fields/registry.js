import { markRaw } from 'vue'
import { getDefaultFormFieldComponentType } from '@flow/workflow-core/extensions/core/fieldPolicy'

const extensions = new Map()
const builtin = new Set(['input', 'password', 'textarea', 'number', 'date', 'datetime', 'time', 'select', 'select_multiple', 'radio', 'checkbox', 'switch', 'cascader', 'user', 'dept', 'role', 'group', 'entity', 'file', 'image', 'rich_text', 'section', 'sub_form', 'sub_list'])

/** 按稳定身份和版本安装移动实现，不接受 PC 组件作为默认回退。 */
export function registerMobileExtension({ type = 'FIELD', name, version = 1, component, readonly = true, editable = false, validate = false, nodeTypes = ['FIELD'], supportedBindings = [], supportedFieldTypes = [] }) {
  const key = `${type}:${name}@${version}`
  if (!['FIELD', 'FORM'].includes(type) || !name || !Number.isSafeInteger(version) || version < 1 || !component || extensions.has(key)) throw new Error(`移动扩展注册无效或重复：${key}`)
  extensions.set(key, Object.freeze({ type, name, version, component: markRaw(component), readonly, editable, validate, nodeTypes, supportedBindings, supportedFieldTypes }))
}
export function getMobileExtension(type, name, version = 1) { return extensions.get(`${type}:${name}@${version}`) }
export function mobileFieldType(field) {
  const raw = String(field.componentType || getDefaultFormFieldComponentType(field.fieldType)), normalized = raw.toLowerCase()
  return ({ reference: 'entity', multi_reference: 'entity', entity_selector: 'entity' })[normalized] || (builtin.has(normalized) ? normalized : raw)
}
/** 与 PC 使用相同的组件绑定约束，不允许同名组件绕过节点/实体字段类型限制。 */
export function getMobileFieldExtension(field) {
  const descriptor = getMobileExtension('FIELD', mobileFieldType(field), field.componentVersion || 1)
  if (!descriptor) return undefined
  const nodeType = String(field.nodeType || 'FIELD').toUpperCase()
  const binding = String(field.bindingType || (field.fieldCode ? 'ENTITY_FIELD' : 'NONE')).toUpperCase()
  const supports = (values, value) => !values.length || values.map(item => String(item).toUpperCase()).includes(value)
  return supports(descriptor.nodeTypes, nodeType)
    && supports(descriptor.supportedBindings, binding)
    && supports(descriptor.supportedFieldTypes, String(field.fieldType || '').toUpperCase()) ? descriptor : undefined
}
export function mobileFieldCapability(field) {
  const name = mobileFieldType(field)
  const descriptor = getMobileFieldExtension(field)
  if (descriptor) return descriptor
  if (builtin.has(mobileFieldType({ componentType: name }))) return { readonly: true, editable: true, validate: true }
  return { readonly: false, editable: false, validate: false, reason: `字段“${field.fieldLabel || field.fieldName || field.fieldCode}”暂不支持在手机展示` }
}
