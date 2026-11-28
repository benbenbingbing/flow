import { trimText } from '../../trimText/shared/trimText.js'

/** 仅在非输入法组词的 Enter 上格式化；按键事件为观察用途，不阻止浏览器默认行为。 */
export function trimTextOnEnter(context) {
  if (context.event.data.key === 'Enter' && !context.event.data.isComposing) trimText(context)
}
