import { ElMessage } from 'element-plus'

const LOG_PREFIX = '[ProjectExtensionAcceptance]'

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
