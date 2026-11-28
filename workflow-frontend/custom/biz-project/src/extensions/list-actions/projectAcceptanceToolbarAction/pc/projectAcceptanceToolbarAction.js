import { ElMessage } from 'element-plus'

const LOG_PREFIX = '[ProjectExtensionAcceptance]'

/** 验收工具栏演示：读取当前选择，报告已执行；不修改记录。 */
export function projectAcceptanceToolbarAction(context = {}) {
  const selectedCount = context.selectedRows?.length || 0
  console.info(LOG_PREFIX, '工具栏自定义处理器执行', {
    entityCode: context.entityCode,
    selectedCount,
    buttonKey: context.config?.key
  })
  ElMessage.success(`工具栏扩展已执行，当前选择 ${selectedCount} 条`)
}
