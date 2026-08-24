import { ElMessage } from 'element-plus'

const LOG_PREFIX = '[ProjectExtensionAcceptance]'

export function projectAcceptanceToolbarAction(context = {}) {
  const selectedCount = context.selectedRows?.length || 0
  console.info(LOG_PREFIX, '工具栏自定义处理器执行', {
    entityCode: context.entityCode,
    selectedCount,
    buttonKey: context.config?.key
  })
  ElMessage.success(`工具栏扩展已执行，当前选择 ${selectedCount} 条`)
}

export function projectAcceptanceRowAction(context = {}) {
  const identity = context.row?.dataNo
    || context.row?.code
    || context.row?.id
    || '-'
  console.info(LOG_PREFIX, '行自定义处理器执行', {
    entityCode: context.entityCode,
    rowId: context.row?.id,
    buttonKey: context.config?.key
  })
  ElMessage.success(`行扩展已执行：${identity}`)
}

export function projectAcceptanceSelectionAction(context = {}) {
  const rows = context.rows || context.selectedRows || []
  console.info(LOG_PREFIX, '列表选择结果处理器执行', {
    entityCode: context.entityCode,
    rowId: context.row?.id,
    selectedIds: rows.map(row => row?.id).filter(Boolean)
  })
  ElMessage.success(`已接收 ${rows.length} 条列表选择结果`)
}
