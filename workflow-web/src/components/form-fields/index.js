/**
 * 表单字段组件库
 *
 * 为实体表单提供独立的字段渲染组件，每个组件封装一种（或一组相似）字段类型的
 * 渲染与交互逻辑，统一支持 v-model、验证、联动状态、自定义事件脚本。
 *
 * 使用方式：
 *   import { TextField, formFieldComponentMap } from '@/components/form-fields'
 *   const component = formFieldComponentMap['input']
 */

import TextField from './components/TextField.vue'
import RichTextField from './components/RichTextField.vue'
import NumberField from './components/NumberField.vue'
import DateField from './components/DateField.vue'
import SelectField from './components/SelectField.vue'
import RadioField from './components/RadioField.vue'
import CheckboxField from './components/CheckboxField.vue'
import SwitchField from './components/SwitchField.vue'
import FileField from './components/FileField.vue'
import EntityField from './components/EntityField.vue'
import SubFormField from './components/SubFormField.vue'
import CascaderField from './components/CascaderField.vue'

// ========== 组件导出 ==========

export {
  TextField,
  RichTextField,
  NumberField,
  DateField,
  SelectField,
  RadioField,
  CheckboxField,
  SwitchField,
  FileField,
  EntityField,
  SubFormField,
  CascaderField
}

// ========== 组件映射表 ==========

/**
 * componentType / fieldType -> Vue Component 映射
 * 用于动态渲染器根据字段类型自动选择对应组件
 */
export const formFieldComponentMap = {
  // 文本类
  input: TextField,
  string: TextField,
  textarea: TextField,
  text: TextField,

  // 富文本
  rich_text: RichTextField,

  // 数字类
  number: NumberField,
  integer: NumberField,
  long: NumberField,
  decimal: NumberField,
  double: NumberField,

  // 日期类
  date: DateField,
  datetime: DateField,

  // 选择类
  select: SelectField,
  multi_select: SelectField,
  select_multiple: SelectField,

  // 单选/多选
  radio: RadioField,
  checkbox: CheckboxField,

  // 开关
  switch: SwitchField,
  boolean: SwitchField,

  // 文件
  file: FileField,
  image: FileField,

  // 实体引用
  user: EntityField,
  dept: EntityField,
  reference: EntityField,
  multi_reference: EntityField,

  // 子表单
  sub_form: SubFormField,
  sub_form_list: SubFormField,

  // 级联
  cascader: CascaderField
}

// ========== 扩展注册机制 ==========

const extensionRegistry = new Map()

/**
 * 注册扩展字段组件
 * @param {string} type - 字段类型标识
 * @param {Component} component - Vue 组件
 */
export function registerFormFieldComponent(type, component) {
  extensionRegistry.set(type, component)
}

/**
 * 获取扩展字段组件
 * @param {string} type - 字段类型标识
 * @returns {Component|undefined}
 */
export function getFormFieldComponent(type) {
  return extensionRegistry.get(type)
}

/**
 * 判断扩展字段组件是否已注册
 * @param {string} type - 字段类型标识
 * @returns {boolean}
 */
export function hasFormFieldComponent(type) {
  return extensionRegistry.has(type)
}

/**
 * 解析字段对应的组件
 * 优先查找扩展注册表，其次查找内置映射表
 *
 * @param {Object} field - 字段配置对象
 * @returns {Component|undefined}
 */
export function resolveFieldComponent(field) {
  const componentType = (field?.componentType || '').toLowerCase()
  const fieldType = (field?.fieldType || '').toLowerCase()

  // 如果 refEntityType 是系统实体类型，直接判定为实体引用字段
  const refEntityType = (field?.refEntityType || '').toUpperCase()
  if (['USER', 'DEPT', 'ROLE', 'GROUP'].includes(refEntityType)) {
    return EntityField
  }
  // 自定义实体引用：只要 refEntityType 是 CUSTOM 且有关联实体ID，也直接判定为实体引用字段
  if (refEntityType === 'CUSTOM' && field?.refEntityId) {
    return EntityField
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

/**
 * 获取所有已注册的扩展字段类型
 * @returns {string[]}
 */
export function getRegisteredFieldTypes() {
  return Array.from(extensionRegistry.keys())
}
