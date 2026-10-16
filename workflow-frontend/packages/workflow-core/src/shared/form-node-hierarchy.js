export const FORM_NODE_MAX_DEPTH = 8
export const FORM_NODE_ORDER_STEP = 1_000_000

export const FORM_NODE_TYPE_LABELS = Object.freeze({
  SECTION: '区块',
  GRID: '栅格',
  TAB_SET: 'Tab 集合',
  TAB: 'Tab 页',
  COLLAPSE: '折叠面板',
  TEXT: '说明文本',
  FIELD: '实体字段',
  SUB_FORM: '子表单',
  REPEATER: '明细表',
  ACTION_SLOT: '动作插槽'
})

const standardContainerChildren = Object.freeze([
  'SECTION',
  'GRID',
  'TAB_SET',
  'COLLAPSE',
  'TEXT',
  'FIELD',
  'SUB_FORM',
  'REPEATER',
  'ACTION_SLOT'
])

export const FORM_NODE_ALLOWED_CHILD_TYPES = Object.freeze({
  SECTION: standardContainerChildren,
  GRID: standardContainerChildren,
  TAB_SET: Object.freeze(['TAB']),
  TAB: standardContainerChildren,
  COLLAPSE: standardContainerChildren,
  SUB_FORM: standardContainerChildren,
  REPEATER: standardContainerChildren
})

export function normalizeFormNodeType(value, fallback = 'FIELD') {
  const source = typeof value === 'object'
    ? (value?.nodeType || value?.fieldType || value?.componentType)
    : value
  return String(source || fallback).toUpperCase()
}

export function formNodeTypeLabel(value) {
  const nodeType = normalizeFormNodeType(value)
  return FORM_NODE_TYPE_LABELS[nodeType] || nodeType
}

export function isFormNodeContainer(value) {
  return Object.hasOwn(
    FORM_NODE_ALLOWED_CHILD_TYPES,
    normalizeFormNodeType(value)
  )
}

export function canContainFormNode(parent, child) {
  const parentType = normalizeFormNodeType(parent)
  const childType = normalizeFormNodeType(child)
  return FORM_NODE_ALLOWED_CHILD_TYPES[parentType]?.includes(childType) === true
}

export function canPlaceFormNodeAtRoot(value) {
  return normalizeFormNodeType(value) !== 'TAB'
}
