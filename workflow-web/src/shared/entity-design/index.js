export const ENTITY_FIELD_TYPES = [
  { value: 'STRING', label: '文本', icon: 'Document' },
  { value: 'TEXT', label: '长文本', icon: 'Tickets' },
  { value: 'RICH_TEXT', label: '富文本', icon: 'DocumentCopy' },
  { value: 'INTEGER', label: '整数', icon: 'Sort' },
  { value: 'DECIMAL', label: '小数', icon: 'Money' },
  { value: 'DATE', label: '日期', icon: 'Calendar' },
  { value: 'DATETIME', label: '日期时间', icon: 'Timer' },
  { value: 'BOOLEAN', label: '布尔', icon: 'Check' },
  { value: 'SELECT', label: '选择', icon: 'ArrowDown' },
  { value: 'MULTI_SELECT', label: '选择（多选）', icon: 'Collection' },
  { value: 'RADIO', label: '选择（单选框）', icon: 'CircleCheck' },
  { value: 'CHECKBOX', label: '选择（复选框）', icon: 'Checked' },
  { value: 'FILE', label: '文件', icon: 'DocumentChecked' },
  { value: 'IMAGE', label: '图片', icon: 'Picture' },
  { value: 'USER', label: '用户', icon: 'User' },
  { value: 'DEPT', label: '部门', icon: 'OfficeBuilding' },
  { value: 'REFERENCE', label: '单选实体', icon: 'Connection' },
  { value: 'MULTI_REFERENCE', label: '多选实体', icon: 'Share' },
  { value: 'SUB_FORM', label: '子表单', icon: 'Grid' },
  { value: 'SUB_FORM_LIST', label: '子表单列表', icon: 'List' }
]

export function getEntityFieldTypeLabel(type) {
  const found = ENTITY_FIELD_TYPES.find((item) => item.value === type)
  return found?.label || type
}

export function getEntityFieldTypeTag(type) {
  const tags = {
    STRING: '',
    TEXT: 'info',
    INTEGER: 'success',
    DECIMAL: 'success',
    DATE: 'warning',
    DATETIME: 'warning',
    REFERENCE: 'primary',
    MULTI_REFERENCE: 'primary'
  }
  return tags[type] || ''
}
