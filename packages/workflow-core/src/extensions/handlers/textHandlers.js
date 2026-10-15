/** 可选择的公共处理函数；返回值不会自动回填，必须通过 ctx 的受控 helper 写入。 */
export function trimText({ value, setValue }) {
  if (typeof value === 'string') setValue(value.trim())
}

/** 仅在非输入法组词的 Enter 上格式化；按键事件为观察用途，不阻止浏览器默认行为。 */
export function trimTextOnEnter(context) {
  if (context.event.data.key === 'Enter' && !context.event.data.isComposing) trimText(context)
}
