import { safeParseConfig } from './config-runtime/index.js'

export const FORM_LABEL_POSITION_OPTIONS = [
  { value: 'top', label: '顶部' },
  { value: 'left', label: '左对齐' },
  { value: 'right', label: '右对齐' }
]

/** 子表单的名称在内容区作为节标题展示，外层 FormItem 不再占用标签列；兼容旧字段配置。 */
export function isSubFormLayoutField(field) {
  const nodeType = String(field?.nodeType || '').toUpperCase()
  const componentType = String(field?.componentType || field?.fieldType || '').toUpperCase()
  return ['SUB_FORM', 'REPEATER'].includes(nodeType) || componentType === 'SUB_FORM'
}

/**
 * 标签位置独立于栅格宽度；未配置时沿用历史布局的标签位置。
 * 只读取配置，不给历史快照补写默认值，避免打开设计器即产生草稿变更。
 */
export function resolveFormLabelPosition(form, viewConfig = form?.viewConfig) {
  const position = safeParseConfig(viewConfig).labelPosition
  if (FORM_LABEL_POSITION_OPTIONS.some(option => option.value === position)) {
    return position
  }
  return form?.layoutType === 'vertical' ? 'top' : 'right'
}

/** 顶部标签不占横向宽度；左右标签在设计器和运行时共用同一宽度配置。 */
export function resolveFormLabelWidth(form, viewConfig = form?.viewConfig) {
  if (resolveFormLabelPosition(form, viewConfig) === 'top') return 'auto'
  const width = Number(safeParseConfig(viewConfig).labelWidth)
  return `${Number.isFinite(width) && width > 0 ? width : 120}px`
}
