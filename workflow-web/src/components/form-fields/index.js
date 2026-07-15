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
import SectionField from './components/SectionField.vue'
import { normalizeExtensionDescriptor } from '@/shared/config-runtime'

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
  CascaderField,
  SectionField
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
  cascader: CascaderField,

  // 节/分组标题
  section: SectionField,
  SECTION: SectionField
}

// ========== 扩展注册机制 ==========

const extensionRegistry = new Map()

const builtInDescriptors = [
  descriptor('input', '文本输入', TextField, ['STRING'], [
    { key: 'maxlength', label: '最大长度', type: 'number', min: 1, max: 10000 },
    { key: 'showWordLimit', label: '显示字数', type: 'boolean', defaultValue: true }
  ]),
  descriptor('textarea', '多行文本', TextField, ['STRING', 'TEXT'], [
    { key: 'rows', label: '显示行数', type: 'number', min: 2, max: 20, defaultValue: 3 },
    { key: 'maxlength', label: '最大长度', type: 'number', min: 1, max: 20000 }
  ]),
  descriptor('rich_text', '富文本', RichTextField, ['TEXT'], [
    { key: 'height', label: '编辑器高度', type: 'number', min: 120, max: 1000, defaultValue: 200 }
  ]),
  descriptor('number', '数字', NumberField, ['INTEGER', 'LONG', 'DECIMAL', 'DOUBLE'], [
    { key: 'min', label: '最小值', type: 'number' },
    { key: 'max', label: '最大值', type: 'number' },
    { key: 'precision', label: '小数位', type: 'number', min: 0, max: 10, defaultValue: 0 },
    { key: 'step', label: '步长', type: 'number', defaultValue: 1 },
    { key: 'controls', label: '显示控制器', type: 'boolean', defaultValue: true }
  ]),
  descriptor('date', '日期', DateField, ['DATE']),
  descriptor('datetime', '日期时间', DateField, ['DATETIME']),
  descriptor('select', '下拉单选', SelectField, ['SELECT', 'STRING']),
  descriptor('select_multiple', '下拉多选', SelectField, ['MULTI_SELECT']),
  descriptor('radio', '单选框', RadioField, ['RADIO', 'SELECT']),
  descriptor('checkbox', '复选框', CheckboxField, ['CHECKBOX', 'MULTI_SELECT']),
  descriptor('switch', '开关', SwitchField, ['BOOLEAN'], [
    { key: 'activeText', label: '开启文本', type: 'text' },
    { key: 'inactiveText', label: '关闭文本', type: 'text' }
  ]),
  descriptor('file', '文件上传', FileField, ['FILE']),
  descriptor('image', '图片上传', FileField, ['IMAGE']),
  descriptor('cascader', '级联选择', CascaderField, ['STRING', 'MULTI_SELECT'], [
    { key: 'cascaderOptions', label: '级联选项', type: 'json' }
  ]),
  descriptor('reference', '实体引用单选', EntityField, ['REFERENCE', 'USER', 'DEPT', 'ROLE', 'GROUP']),
  descriptor('multi_reference', '实体引用多选', EntityField, ['MULTI_REFERENCE']),
  descriptor('sub_form', '子表单', SubFormField, ['SUB_FORM', 'SUB_FORM_LIST']),
  descriptor('section', '分组标题', SectionField, ['SECTION'])
]

function descriptor(type, label, component, supportedFieldTypes = [], configSchema = []) {
  return normalizeExtensionDescriptor(type, component, {
    label,
    supportedFieldTypes,
    configSchema
  })
}

/**
 * 注册扩展字段组件
 * @param {string} type - 字段类型标识
 * @param {Component} component - Vue 组件
 */
export function registerFormFieldComponent(type, component, metadata = {}) {
  const extension = normalizeExtensionDescriptor(type, component, metadata)
  extensionRegistry.set(extension.name.toLowerCase(), extension)
}

/**
 * 获取扩展字段组件
 * @param {string} type - 字段类型标识
 * @returns {Component|undefined}
 */
export function getFormFieldComponent(type) {
  return extensionRegistry.get(String(type || '').toLowerCase())?.component
}

/**
 * 判断扩展字段组件是否已注册
 * @param {string} type - 字段类型标识
 * @returns {boolean}
 */
export function hasFormFieldComponent(type) {
  return extensionRegistry.has(String(type || '').toLowerCase())
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

  if (['sub_form', 'sub_form_list'].includes(componentType) || ['sub_form', 'sub_form_list'].includes(fieldType)) {
    return SubFormField
  }

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

export function getFormFieldComponentDescriptor(type) {
  const normalizedType = String(type || '').toLowerCase()
  return extensionRegistry.get(normalizedType)
    || builtInDescriptors.find(item => item.name === normalizedType)
}

export function getFormFieldComponentOptions() {
  const merged = new Map()
  builtInDescriptors.forEach(item => merged.set(item.name, item))
  extensionRegistry.forEach((item, key) => merged.set(key, item))
  return Array.from(merged.values()).map(({ component, ...item }) => item)
}
