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

/** 共用工具栏/行的选择结果处理器；同时支持 rows 与 selectedRows 宿主契约。 */
export function projectAcceptanceSelectionAction(context = {}) {
  const rows = context.rows || context.selectedRows || []
  console.info(LOG_PREFIX, '列表选择结果处理器执行', {
    entityCode: context.entityCode,
    rowId: context.row?.id,
    selectedIds: rows.map(row => row?.id).filter(Boolean)
  })
  ElMessage.success(`已接收 ${rows.length} 条列表选择结果`)
}
