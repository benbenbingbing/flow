const buttonEvents = ['TOOLBAR_BUTTON_CLICK', 'ROW_BUTTON_CLICK']
const normalize = value => String(value || '').toUpperCase()

export function isListButtonEvent(eventCode) {
  return buttonEvents.includes(normalize(eventCode))
}

/**
 * 列表按钮按位置和稳定编码区分；仅“业务接口”按钮会触发服务端点击事件。
 * 停用按钮仍可提前配置，其他执行方式不应生成不会执行的事件绑定。
 */
export function listButtonEventOptions(toolbarButtons = [], rowButtons = []) {
  return [
    ...toolbarButtons.map(button => ({ ...button, eventCode: buttonEvents[0] })),
    ...rowButtons.map(button => ({ ...button, eventCode: buttonEvents[1] }))
  ].filter(button => button.key
    && normalize(button.type) === 'CUSTOM'
    && normalize(button.customMode) === 'EVENT')
}

/** 公共事件与具体按钮是不同绑定目标；优先呈现按钮，避免误建全列表公共链。 */
export function listEventTargets(eventCode, buttons = []) {
  if (!isListButtonEvent(eventCode)) return [{ targetType: 'OWNER', targetKey: '' }]
  const position = normalize(eventCode) === buttonEvents[0] ? '工具栏按钮' : '操作列按钮'
  return [
    ...buttons.filter(button => normalize(button.eventCode) === normalize(eventCode))
      .map(button => ({
        targetType: 'BUTTON',
        targetKey: String(button.key),
        label: `${position}：${button.label || button.key} (${button.key})`
      })),
    { targetType: 'OWNER', targetKey: '', label: `所有${position}（公共默认）` }
  ]
}

/** 同一事件只在同一目标内去重；OWNER、不同按钮及不同行/工具栏事件互不占用。 */
export function findEventBinding(bindings, eventCode, target) {
  return bindings.find(row => normalize(row.eventCode) === normalize(eventCode)
    && normalize(row.targetType || 'OWNER') === normalize(target.targetType || 'OWNER')
    && String(row.targetKey || '') === String(target.targetKey || ''))
}
