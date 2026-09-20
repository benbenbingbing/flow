/** 工具栏执行前的选择要求，与目标列表的 selectionMode（选择器模式）相互独立。 */
export const TOOLBAR_SELECTION_OPTIONS = Object.freeze([
  { value: 'NONE', label: '无需选择' },
  { value: 'SINGLE', label: '恰好一条' },
  { value: 'AT_LEAST_ONE', label: '至少一条' }
])

/** 需要唯一来源记录的打开动作不能通过配置放宽，否则参数映射会产生歧义。 */
export function requiresSingleSourceRecord(button = {}) {
  if (button.type === 'custom' && button.customMode === 'open-related-content') return true
  const opensPage = (button.type === 'custom' && ['open-list', 'open-form'].includes(button.customMode))
    || (button.type === 'built-in' && button.key === 'create')
  return opensPage && Array.isArray(button.parameterMappings)
    && button.parameterMappings.some(item => ['FIELD', 'RECORD_ID'].includes(item.sourceType))
}

/** 旧按钮没有新字段时保留原有语义；NONE 表示不限制数量，并非必须取消勾选。 */
export function toolbarSelectionRequirement(button = {}) {
  if (['batchDelete', 'exportSelected'].includes(button.key)) return 'AT_LEAST_ONE'
  if (requiresSingleSourceRecord(button)) return 'SINGLE'
  return button.type === 'custom' ? button.selectionRequirement || 'NONE' : 'NONE'
}

export function isSelectionToolbarButton(button) {
  return toolbarSelectionRequirement(button) !== 'NONE'
}

/** 返回执行前的数量提示；可执行时返回空字符串，未知配置按禁用处理。 */
export function toolbarSelectionReason(button, rows = []) {
  const requirement = toolbarSelectionRequirement(button)
  if (requirement === 'SINGLE') return rows.length === 1 ? '' : '请选择恰好一条数据'
  if (requirement === 'AT_LEAST_ONE') return rows.length > 0 ? '' : '请先选择数据'
  return requirement === 'NONE' ? '' : '按钮选择要求配置无效'
}

/** 普通列表只控制勾选入口；历史 SINGLE 在普通列表中映射为允许多选。 */
export function normalizeListSelectionMode(mode) {
  return ['SINGLE', 'MULTIPLE'].includes(mode) ? 'MULTIPLE' : 'NONE'
}
