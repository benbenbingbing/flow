import { applyRuntimeFieldEffects } from './fieldEvents.js'

/**
 * 按服务端给定顺序消费表单事件结果。字段映射共用字段事件的覆盖规则，其他效果
 * 由宿主注入 UI/导航能力，避免 PC 与移动端各自解释同一协议。
 * handlers.isCurrent 在异步确认和效果之间检查所属编辑会话；过期结果不会继续执行。
 * 缺少必要适配时抛错，不能将服务端要求的操作静默当成成功。
 */
export async function applyRuntimeEventEffects(result, handlers) {
  const isCurrent = handlers.isCurrent || (() => true)
  const effects = Array.isArray(result?.effects) ? result.effects : []
  if (!effects.length) {
    if (isCurrent()) await applyRuntimeFieldEffects(result, handlers)
    return
  }
  const actions = {
    MESSAGE: 'message', OPEN_ROUTE: 'navigate', CLOSE_FORM: 'close',
    REFRESH_PARENT: 'refresh', DOWNLOAD_TASK: 'download'
  }
  for (const effect of effects) {
    if (!isCurrent()) return
    const type = String(effect?.type || '').toUpperCase()
    if (type === 'FIELD_MAPPING') {
      await applyRuntimeFieldEffects({ effects: [{ ...effect, type }] }, handlers)
      continue
    }
    if ((type === 'MESSAGE' && !effect.message) || (type === 'OPEN_ROUTE' && !effect.route)) continue
    const action = actions[type]
    if (!action || typeof handlers[action] !== 'function') {
      throw new Error(`当前页面不支持事件效果：${type || '未知类型'}`)
    }
    await handlers[action](effect)
  }
}
