import { builtInActionBarError, normalizeBuiltInActionBar } from '@flow/workflow-core/builtin-button-appearance'
import { evaluateFlowConditionGroup, isFlowConditionGroupComplete, parseFlowConditionConfig } from '@flow/workflow-core/utils/flowConditionGroups'

/** 展示设置只控制区域呈现，不能改变查询范围、目标发布配置或操作权限。 */
export function relatedContentIsVisible(presentation = {}, data = {}, mode = 'view') {
  if (presentation.visibleModes?.[mode] === false) return false
  if (!presentation.visibilityCondition) return true
  const root = parseFlowConditionConfig(presentation.visibilityCondition)
  return !!root && isFlowConditionGroupComplete(root) && evaluateFlowConditionGroup(root, data)
}

/** 外层高度独立于列表内部最大高度；仅生成受限数值样式，不接受任意 CSS。 */
export function relatedContentLayoutStyle(presentation = {}) {
  const layout = presentation.layout || {}
  const result = {}
  if (layout.width != null) result.width = `${layout.width}%`
  if (layout.height != null) { result.height = `${layout.height}px`; result.overflow = 'auto' }
  if (layout.padding != null) result.padding = `${layout.padding}px`
  result.boxSizing = 'border-box'
  return result
}

const relatedContentButtonKeys = contentType => contentType === 'LIST' ? ['refresh', 'link', 'open'] : ['save', 'open']

/** 主动切换目标类型时清除不再适用的按钮，保留共用入口外观及操作栏布局。 */
export function normalizeRelatedContentActionBar(value, contentType) {
  return normalizeBuiltInActionBar(value, relatedContentButtonKeys(contentType))
}

export function relatedContentPresentationError(presentation = {}, contentType) {
  const actionBarError = builtInActionBarError(presentation.actionBar, relatedContentButtonKeys(contentType))
  if (actionBarError) return actionBarError
  const ranges = { width: [10, 100], height: [120, 3000], padding: [0, 80] }
  for (const [key, value] of Object.entries(presentation.layout || {})) {
    const range = ranges[key]
    if (!range || !Number.isInteger(value) || value < range[0] || value > range[1]) return '外层布局参数超出允许范围'
  }
  if (presentation.visibilityCondition) {
    const root = parseFlowConditionConfig(presentation.visibilityCondition)
    if (!root || !isFlowConditionGroupComplete(root)) return '条件显示尚未填写完整'
  }
  for (const [mode, visible] of Object.entries(presentation.visibleModes || {})) {
    if (!['create', 'edit', 'view', 'approve'].includes(mode) || typeof visible !== 'boolean') return '运行模式显示配置无效'
  }
  const options = presentation.listOptions
  if (options && contentType !== 'LIST') return '子列表选项只能用于列表目标'
  for (const [key, value] of Object.entries(options || {})) {
    if (['showSearch', 'showPagination', 'showToolbar', 'showRowActions'].includes(key)) {
      if (typeof value !== 'boolean') return '子列表显示选项必须为开关值'
    } else {
      const range = key === 'pageSize' ? [1, 200] : key === 'maxHeight' ? [120, 2000] : null
      if (!range || !Number.isInteger(value) || value < range[0] || value > range[1]) return '子列表参数超出允许范围'
    }
  }
  return ''
}

/** 保留历史默认值，并与既有动作约束相交，不能借“显示操作列”开放编辑权限。 */
export function relatedContentListProps(presentation = {}, actions = []) {
  const options = presentation.listOptions || {}
  return {
    showSearch: options.showSearch !== false,
    showPagination: options.showPagination !== false,
    showToolbar: options.showToolbar !== false,
    showRowActions: options.showRowActions !== false && (actions.includes('VIEW') || actions.includes('EDIT')),
    ...(options.pageSize != null ? { pageSize: options.pageSize } : {}),
    ...(options.maxHeight != null ? { maxHeight: options.maxHeight } : {})
  }
}
