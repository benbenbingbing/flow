const selectionTypes = new Set(['SELECT', 'MULTI_SELECT', 'SELECT_MULTIPLE', 'RADIO', 'CHECKBOX'])
const configured = value => Array.isArray(value) ? value.length > 0 : value != null && String(value).trim() !== ''

/** 源模板和构建包共用约束；构建工具不得把旧选项转换成组件属性后继续发布。 */
export function assertSupportedEntityOptions(entity) {
  for (const field of entity.fields || []) {
    if (configured(field.optionsJson) || configured(field.options)) throw new Error(`${entity.businessKey}.${field.fieldCode}: 不再支持内嵌选项`)
    if (selectionTypes.has(String(field.fieldType).toUpperCase()) && !field.dictType) throw new Error(`${entity.businessKey}.${field.fieldCode}: 选择字段必须绑定代码表`)
  }
  for (const form of entity.forms || []) {
    for (const field of form.fields || []) {
      const props = typeof field.componentProps === 'string' ? JSON.parse(field.componentProps) : field.componentProps || {}
      if (![field.componentType, field.fieldType].some(type => selectionTypes.has(String(type).toUpperCase()))) continue
      if ([field.optionsJson, field.options, props.options, props.optionsJson].some(configured)) throw new Error(`${entity.businessKey}/${form.formKey}.${field.fieldCode}: 不再支持表单内嵌选项`)
    }
  }
}
