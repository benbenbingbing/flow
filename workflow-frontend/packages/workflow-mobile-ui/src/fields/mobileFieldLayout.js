import { safeParseConfig } from '@flow/workflow-core/config-runtime'

/**
 * 普通字段读取当前表单的发布配置；子表传入自己的 form，不能沿用父表位置。
 * 移动端保留顶部布局，水平标签统一左对齐；只调整展示，不改写 PC 的右对齐配置。
 * 未配置位置的历史表单仍由 layoutType 决定上下或左右布局。
 * 富文本、图片和文件由专用组件保持移动端的上下布局，不使用此配置。
 */
export function resolveMobileLabelPosition(form) {
  const position = safeParseConfig(form?.viewConfig).labelPosition
  if (position === 'top') return 'top'
  if (['left', 'right'].includes(position)) return 'left'
  return form?.layoutType === 'vertical' ? 'top' : 'left'
}
