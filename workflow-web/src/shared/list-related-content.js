/** 关联入口使用标准列表按钮；关联定义只负责目标、数据关系和打开方式。 */
export function isRelatedContentButton(button) {
  return button?.type === 'custom' && button.customMode === 'open-related-content'
}

/** 兼容历史行/工具栏挂载定义，但两种按钮均可引用同一项弹窗、抽屉或页面内容。 */
export function isButtonRelatedContent(item) {
  return ['LIST_ACTION', 'ROW_ACTION', 'TOOLBAR_ACTION'].includes(item?.anchorType)
    && ['DIALOG', 'DRAWER', 'PAGE'].includes(item?.config?.presentation?.position)
}

export function findButtonRelatedContent(button, compositions = []) {
  if (!isRelatedContentButton(button)) return null
  return compositions.find(item => item.compositionKey === button.compositionKey
    && item.config?.enabled !== false && isButtonRelatedContent(item)) || null
}

/** 工具栏必须明确选中一条来源记录，不能静默取多选中的第一行。 */
export function relatedContentSelectionReason(rows = []) {
  return rows.length === 1 && rows[0]?.id
    ? ''
    : '请选择一条记录后打开关联内容'
}
