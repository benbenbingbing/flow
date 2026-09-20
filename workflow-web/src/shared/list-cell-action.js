/** 组件按钮自行管理交互，不能通过平台的行按钮分发入口执行。 */
export function supportsCellAction(button) {
  if (!button?.key) return false
  if (button.type === 'built-in') return ['view', 'edit', 'approve', 'delete'].includes(button.key)
  return button.type === 'custom'
    && ['', 'handler', 'event', 'open-form', 'open-list', 'open-related-content'].includes(button.customMode || '')
}

/** 功能映射使用字段编码，改动字段显示名称不会断开映射。 */
export function mappedFieldCode(button) {
  return typeof button?.mappedFieldCode === 'string' ? button.mappedFieldCode.trim() : ''
}

/**
 * 为已显示的列建立唯一动作索引。配置选项不按 showInList 过滤；运行时只为实际列创建入口。
 * 冲突配置不猜测执行哪个按钮，保留原操作列入口，避免误触发另一个动作。
 */
export function buildCellActionMap(buttons = [], fields = []) {
  const displayedCodes = new Set(fields.filter(field => field.showInList !== false).map(field => field.fieldCode))
  const mapped = new Map()
  for (const button of buttons) {
    const code = mappedFieldCode(button)
    if (!code || button.enabled === false || !supportsCellAction(button) || !displayedCodes.has(code)) continue
    mapped.set(code, mapped.has(code) ? null : button)
  }
  return new Map([...mapped].filter(([, button]) => button))
}

/** 只隐藏已产生单元格入口的按钮；字段未显示、解绑或映射失效时恢复原入口。 */
export function hidesMappedRowButton(button, actionMap) {
  return button.hideWhenMapped === true && actionMap.get(mappedFieldCode(button)) === button
}

/** 编辑时标识被其他按钮占用的字段，包括暂时停用的按钮，防止重新启用后产生歧义。 */
export function cellMappingConflict(button, buttons = []) {
  const code = mappedFieldCode(button)
  return code ? buttons.find(other => other !== button
    && !(button.id && other.id === button.id)
    && mappedFieldCode(other) === code) || null : null
}
