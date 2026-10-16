import { ElMessage, ElMessageBox } from 'element-plus'
import { applyRuntimeEventEffects } from '@flow/workflow-core/form-runtime/eventEffects'

/** PC 只适配提示及覆盖确认，事件顺序、路径和空值规则由共享运行时处理。 */
export function applyFormEffects(result, { getFields = () => [], ...handlers }) {
  return applyRuntimeEventEffects(result, {
    ...handlers,
    async confirmOverwrite(path) {
      const code = path.split('.')[0]
      const field = getFields().find(item => String(item.fieldCode) === code)
      try {
        await ElMessageBox.confirm(
          `字段“${field?.fieldName || field?.fieldLabel || code}”已有值，是否覆盖？`,
          '确认回填', { type: 'warning' }
        )
        return true
      } catch {
        return false
      }
    },
    message: effect => ElMessage({ type: effect.level || 'success', message: effect.message }),
    download: effect => ElMessage.success(effect.message || '下载任务已创建')
  })
}

/** 确认被取消时返回 false，让动作运行时释放锁且不生成执行请求。 */
export async function confirmFormAction(action) {
  try {
    await ElMessageBox.confirm(action.confirm.message || `确认执行“${action.label}”？`, '操作确认', { type: 'warning' })
    return true
  } catch {
    return false
  }
}
