const normalize = value => String(value || '').trim().toUpperCase()
const REFERENCE_TYPES = new Set(['REFERENCE', 'MULTI_REFERENCE', 'ENTITY', 'ENTITY_SELECTOR', 'USER', 'DEPT', 'ROLE', 'GROUP'])
const STRUCTURAL_TYPES = new Set(['SECTION', 'GRID', 'TAB_SET', 'TAB', 'TAB_PANE', 'COLLAPSE', 'TEXT', 'ACTION_SLOT', 'SUB_LIST', 'REPEATER'])

function componentProps(field) {
  if (typeof field?.componentProps !== 'string') return field?.componentProps || {}
  try { return JSON.parse(field.componentProps) || {} } catch { return {} }
}

/** 实体字段与组件都可能声明引用类型，兼容历史 refConfig 中的关联配置。 */
export function isEntitySelectionEventField(field) {
  if (!field) return false
  const types = [field.fieldType, field.componentType].map(normalize)
  // 子表也会带 refEntityId，但它表示子页面的数据实体，不是实体选择控件。
  if (types.some(type => ['SUB_FORM', 'SUB_LIST'].includes(type))
      || (field.nodeType && normalize(field.nodeType) !== 'FIELD')) return false
  const ref = componentProps(field).refConfig || {}
  return types.some(type => REFERENCE_TYPES.has(type))
    || ['USER', 'DEPT', 'ROLE', 'GROUP'].includes(normalize(field.refEntityType || ref.refEntityType))
    || Boolean(field.refEntityId || ref.refEntityId)
}

export function isSingleEntitySelectionEventField(field) {
  return isEntitySelectionEventField(field)
    && ![field?.fieldType, field?.componentType].some(type => normalize(type) === 'MULTI_REFERENCE')
    && componentProps(field).multiple !== true
}

/**
 * 返回当前字段不能配置事件的原因，空字符串表示可用。
 * 未提供具体字段时保留 OWNER 公共事件目录；字段按钮由注册组件显式声明，
 * 不能把实体选择器的下拉按钮误认为 FIELD_BUTTON_CLICK。
 */
export function fieldEventDisabledReason(field, eventCode, capabilities = {}) {
  if (!field) return ''
  const nodeType = normalize(field.nodeType)
  const types = [field.fieldType, field.componentType, nodeType].map(normalize)
  const subform = types.includes('SUB_FORM')
  const structural = (nodeType && nodeType !== 'FIELD' && !subform)
    || (!nodeType && STRUCTURAL_TYPES.has(normalize(field.componentType)) && !field.fieldCode)
  if (structural) return '当前布局节点不支持字段事件'
  switch (normalize(eventCode)) {
    case 'ENTITY_SELECTED':
      return isEntitySelectionEventField(field) && !subform ? '' : '仅实体选择字段支持选择实体后事件'
    case 'FIELD_BUTTON_CLICK':
      return capabilities.supportedEvents?.includes('FIELD_BUTTON_CLICK')
        ? '' : '当前组件没有独立的字段按钮事件，选择器下拉按钮不属于此事件'
    case 'SUBFORM_LOAD':
    case 'SUBFORM_SAVE':
      return subform ? '' : '仅子表单字段支持此事件'
    case 'FIELD_CHANGE':
      return types.includes('SUB_LIST') ? '子列表不支持字段值变化事件' : ''
    default:
      return ''
  }
}
