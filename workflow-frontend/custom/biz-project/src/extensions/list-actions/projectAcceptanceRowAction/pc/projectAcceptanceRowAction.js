import { ElMessage } from 'element-plus'

const LOG_PREFIX = '[ProjectExtensionAcceptance]'

/** 验收行动作演示：使用宿主传入的当前行，不重新请求实体数据。 */
export function projectAcceptanceRowAction(context = {}) {
  const identity = context.row?.code
    || context.row?.id
    || '-'
  console.info(LOG_PREFIX, '行自定义处理器执行', {
    entityCode: context.entityCode,
    rowId: context.row?.id,
    buttonKey: context.config?.key
  })
  ElMessage.success(`行扩展已执行：${identity}`)
}
