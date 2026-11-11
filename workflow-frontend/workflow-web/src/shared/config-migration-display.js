/** 配置迁移页面的状态文案与标签样式映射。 */
export const statusType = (status) => ({
  UPLOADED: 'info',
  ANALYZED: 'success',
  BLOCKED: 'danger',
  PUBLISHED: 'success',
  ROLLED_BACK: 'warning'
}[status] || 'info')

export const statusText = (status) => ({
  UPLOADED: '已上传',
  ANALYZED: '分析通过',
  BLOCKED: '已阻断',
  PUBLISHED: '已发布',
  ROLLED_BACK: '已回滚'
}[status] || '未知状态')

export const compareStatusType = (status) => ({
  NEW: 'success',
  CONSISTENT: 'success',
  SOURCE_NEWER: 'primary',
  LOCAL_CHANGED: 'warning',
  CONFLICT: 'danger',
  MISSING: 'danger',
  FAILED: 'danger'
}[status] || 'info')

export const compareStatusText = (status) => ({
  NEW: '生产新增',
  CONSISTENT: '一致',
  SOURCE_NEWER: '来源更新',
  LOCAL_CHANGED: '生产已修改',
  CONFLICT: '双向冲突',
  MISSING: '生产缺失',
  FAILED: '失败'
}[status] || '未知状态')

export const publishStatusText = (status) => ({
  PENDING: '待发布',
  PUBLISHING: '发布中',
  SUCCESS: '发布成功',
  FAILED: '发布失败',
  ROLLED_BACK: '已回滚'
}[status] || '未知状态')

export const assetTypeLabel = (assetType) => ({
  ENTITY: '实体',
  SYSTEM_ENTITY_UI: '系统实体 UI',
  PROCESS: '流程',
  DICTIONARY: '数据字典',
  WORK_CALENDAR: '工作日历',
  TASK_SLA_POLICY: 'SLA 策略'
}[assetType] || assetType || '未知')

export const assetTypeTagType = (assetType) => ({
  ENTITY: 'primary',
  SYSTEM_ENTITY_UI: 'warning',
  PROCESS: 'success',
  DICTIONARY: 'primary',
  WORK_CALENDAR: 'info',
  TASK_SLA_POLICY: 'danger'
}[assetType] || 'info')

/** 将正文引用问题和依赖缺失合并为可操作说明，保留后端给出的配置位置与修复建议。 */
export function migrationValidationIssues(report, itemId) {
  const item = (report?.items || []).find(value => value.itemId === itemId)
  if (!item) return []
  return [
    ...(item.risks || []).map(risk => ({
      ...risk,
      path: risk.path || risk.fieldCode || '',
      suggestion: risk.suggestion || '请根据问题位置修正源配置，重新发布导出后再分析导入包。'
    })),
    ...(['CONFLICT', 'LOCAL_CHANGED'].includes(item.comparisonStatus) ? [{
      level: 'BLOCKING',
      message: '目标配置存在本地修改或双向冲突。',
      suggestion: '请先备份并核对目标修改，将需要保留的修改合并到源配置后重新发布导出，再更新目标基线并重新分析。'
    }] : []),
    ...(item.missingDependencies || []).map(dependency => ({
      level: 'BLOCKING',
      message: `目标依赖 ${dependency.type}/${dependency.targetKey || dependency.key}：${dependency.reason || '未找到对应配置'}`,
      path: dependency.source || '',
      suggestion: '请在目标系统补齐对应的目录或配置，或在“环境依赖映射”中填写实际目标编码，然后重新分析。'
    }))
  ]
}
