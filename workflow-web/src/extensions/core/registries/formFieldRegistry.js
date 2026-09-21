import { normalizeExtensionDescriptor } from '@flow/workflow-core/config-runtime'
import fieldDefinitions from '@flow/workflow-core/extensions/generated/field-definitions'

// 元数据在纯 JS 策略中可用；可执行组件只由统一安装器写入这里。
const registryKey = Symbol.for('workflow.formFieldExtensionRegistry')
const state = globalThis[registryKey] || { custom: new Map(), builtin: new Map(), aliases: {} }
globalThis[registryKey] = state
export const formFieldComponentMap = state.aliases

/** 安装字段；origin 来自受控清单目录，业务组件不能依靠加载顺序覆盖内置别名。 */
export function registerFormFieldComponent(type, component, metadata = {}) {
  const descriptor = normalizeExtensionDescriptor(type, component, metadata)
  if (metadata.origin === 'PLATFORM') {
    state.builtin.set(type.toLowerCase(), descriptor)
    for (const name of [type, ...(metadata.aliases || [])]) state.aliases[name.toLowerCase()] = component
  } else {
    state.custom.set(type.toLowerCase(), descriptor)
  }
}

/** 返回业务/通用扩展实现；内置组件继续通过默认映射参与原有解析优先级。 */
export function getFormFieldComponent(type) { return state.custom.get(String(type || '').toLowerCase())?.component }
export function hasFormFieldComponent(type) { return state.custom.has(String(type || '').toLowerCase()) }

/** 保留子表、实体引用优先和具体类型优先规则，避免已有配置因注册迁移改变控件。 */
export function resolveFieldComponent(field) {
  const componentType = (field?.componentType || '').toLowerCase()
  const fieldType = (field?.fieldType || '').toLowerCase()

  if (componentType === 'sub_list' || fieldType === 'sub_list') {
    return formFieldComponentMap.sub_list
  }
  if (componentType === 'sub_form' || fieldType === 'sub_form') {
    return formFieldComponentMap.sub_form
  }

  // 如果 refEntityType 是系统实体类型，直接判定为实体引用字段
  const refEntityType = (field?.refEntityType || '').toUpperCase()
  if (['USER', 'DEPT', 'ROLE', 'GROUP'].includes(refEntityType)) {
    return formFieldComponentMap.reference
  }
  // 自定义实体引用：只要 refEntityType 是 CUSTOM 且有关联实体ID，也直接判定为实体引用字段
  if (refEntityType === 'CUSTOM' && field?.refEntityId) {
    return formFieldComponentMap.reference
  }

  const genericTypes = ['input', 'string', 'text', '']
  const compIsGeneric = genericTypes.includes(componentType)
  const fieldIsGeneric = genericTypes.includes(fieldType)

  // 收集候选类型：优先非通用类型（更具体的语义）
  const typesToTry = []
  if (!compIsGeneric) typesToTry.push(componentType)
  if (!fieldIsGeneric) typesToTry.push(fieldType)
  if (compIsGeneric) typesToTry.push(componentType)
  if (fieldIsGeneric) typesToTry.push(fieldType)

  for (const type of typesToTry) {
    if (!type) continue
    if (hasFormFieldComponent(type)) {
      return getFormFieldComponent(type)
    }
    if (formFieldComponentMap[type]) {
      return formFieldComponentMap[type]
    }
  }

  return undefined
}


/** 已安装的非平台字段名称。 */
export function getRegisteredFieldTypes() { return [...state.custom.keys()] }
/** 设计器读取已安装字段元数据，不导入组件或执行注册。 */
export function getFormFieldComponentDescriptor(type) {
  const name = String(type || '').toLowerCase()
  return state.custom.get(name) || state.builtin.get(name)
}
export function getFormFieldComponentOptions() {
  return [...state.builtin.values(), ...state.custom.values()].map(({ component, ...item }) => item)
}
export function getRegisteredFormFieldComponentOptions() {
  return [...state.custom.values()].map(({ component, ...item }) => item)
}
export function getBuiltInFormFieldComponentNames() { return fieldDefinitions.map(item => item.name) }
