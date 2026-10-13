import { normalizeExtensionDescriptor } from '@flow/workflow-core/config-runtime'
import fieldDefinitions from '@flow/workflow-core/extensions/generated/field-definitions'

// 所有表单组件共用注册表；来源仅用于目录分组，不决定渲染或数据更新协议。
const components = new Map()
export const formFieldComponentMap = {}

/** 安装一个确定版本；发布配置按版本查找，不能被后续安装静默覆盖。 */
export function registerFormFieldComponent(type, component, metadata = {}) {
  const descriptor = normalizeExtensionDescriptor(type, component, metadata)
  descriptor.nodeTypes = (metadata.nodeTypes || ['FIELD']).map(value => String(value).toUpperCase())
  descriptor.supportedBindings = (metadata.supportedBindings || []).map(value => String(value).toUpperCase())
  const name = String(type).toLowerCase()
  const versions = components.get(name) || new Map()
  if (versions.has(descriptor.version)) throw new Error(`表单组件重复注册：${type}@${descriptor.version}`)
  versions.set(descriptor.version, descriptor)
  components.set(name, versions)
  if (metadata.origin === 'PLATFORM') {
    for (const alias of [type, ...(metadata.aliases || [])]) formFieldComponentMap[alias.toLowerCase()] = component
  }
}

/** 设计器省略版本时使用最新声明，运行时有版本则必须精确匹配。 */
export function getFormFieldComponentDescriptor(type, version) {
  const versions = components.get(String(type || '').toLowerCase())
  if (!versions) return undefined
  if (version !== undefined && version !== null && version !== '') return versions.get(Number(version))
  return [...versions.values()].sort((left, right) => right.version - left.version)[0]
}

export function getFormFieldComponent(type, version) {
  return getFormFieldComponentDescriptor(type, version)?.component
}

/** 是否需要服务端的自定义组件发布引用；系统组件由构建提供。 */
export function hasFormFieldComponent(type) {
  const descriptor = getFormFieldComponentDescriptor(type)
  return Boolean(descriptor && descriptor.origin !== 'PLATFORM')
}

/** 同一协议下检查宿主能力，防止将子表组件安装到不支持的数据绑定位置。 */
function supportsField(descriptor, field) {
  const nodeType = String(field.nodeType || 'FIELD').toUpperCase()
  const bindingType = String(field.bindingType || (field.fieldCode ? 'ENTITY_FIELD' : 'NONE')).toUpperCase()
  return (!descriptor.nodeTypes.length || descriptor.nodeTypes.includes(nodeType))
    && (!descriptor.supportedBindings.length || descriptor.supportedBindings.includes(bindingType))
    && (!descriptor.supportedFieldTypes.length || descriptor.supportedFieldTypes.map(value => String(value).toUpperCase()).includes(String(field.fieldType || '').toUpperCase()))
}

/** 显式自定义组件优先；未指定组件的实体字段仍按内置类型规则选择控件。 */
export function resolveFieldComponent(field) {
  const componentType = String(field?.componentType || '').toLowerCase()
  const fieldType = String(field?.fieldType || '').toLowerCase()
  const selected = getFormFieldComponentDescriptor(componentType, field?.componentVersion)
  if (selected?.origin !== 'PLATFORM' && components.has(componentType)) {
    return selected && supportsField(selected, field) ? selected.component : undefined
  }
  // 显式选择的实现缺失时必须报告不可用，不能悄悄换成字段类型对应的输入框。
  if (componentType && !formFieldComponentMap[componentType] && !components.has(componentType)) return undefined
  if (componentType === 'sub_list' || fieldType === 'sub_list') return formFieldComponentMap.sub_list
  if (componentType === 'sub_form' || fieldType === 'sub_form') return formFieldComponentMap.sub_form
  const refEntityType = String(field?.refEntityType || '').toUpperCase()
  if (['USER', 'DEPT', 'ROLE', 'GROUP'].includes(refEntityType)
      || (refEntityType === 'CUSTOM' && field?.refEntityId)) return formFieldComponentMap.reference
  const generic = new Set(['input', 'string', 'text', ''])
  const candidates = [componentType, fieldType].filter(type => !generic.has(type))
    .concat([componentType, fieldType].filter(type => generic.has(type)))
  for (const type of candidates) {
    if (formFieldComponentMap[type]) return formFieldComponentMap[type]
  }
  return undefined
}

/** 目录每个名称展示最新版本；具体发布版本由节点独立锁定。 */
export function getFormFieldComponentOptions() {
  return [...components.keys()].map(name => {
    const { component, ...descriptor } = getFormFieldComponentDescriptor(name)
    return descriptor
  })
}
export function getRegisteredFormFieldComponentOptions() {
  return getFormFieldComponentOptions().filter(item => item.origin !== 'PLATFORM')
}
export function getRegisteredFieldTypes() { return getRegisteredFormFieldComponentOptions().map(item => item.name.toLowerCase()) }
export function getBuiltInFormFieldComponentNames() { return fieldDefinitions.map(item => item.name) }
