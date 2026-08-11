import { ElMessage } from 'element-plus'

const LOG_PREFIX = '[ProjectExtensionAcceptance]'

function currentUserName(context = {}) {
  const user = context.userStore?.userInfo
    || context.userStore?.user
    || {}
  return user.nickname || user.username || '超级管理员'
}

export async function projectAcceptanceInitializer(config = {}, context = {}) {
  const defaults = {
    acceptance_scene: config.scene || 'FULL_EXTENSION',
    acceptance_score: Number(config.defaultScore ?? 65),
    owner_name: currentUserName(context),
    provider_trace: '前端自定义初始化器已执行',
    extension_summary: '等待节点扩展和统一数据源执行'
  }
  console.info(LOG_PREFIX, '表单初始化器执行', {
    entityCode: context.entityCode,
    routeQueryKeys: Object.keys(context.routeQuery || {}),
    resultKeys: Object.keys(defaults)
  })
  return defaults
}

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
