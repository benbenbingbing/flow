import { safeParseConfig } from '@flow/workflow-core/config-runtime'
import { getFieldModelPath } from '@flow/workflow-core/form-runtime'
import { supportsEntityFieldQuery } from './list-query-policy.js'

/** 只有显式启用的实体字段参与全量排序；分页后生成的虚拟列无法提供正确的顺序。 */
export function isListColumnSortable(field) {
  return !!field?.fieldCode
    && field.showInList !== false
    && supportsEntityFieldQuery(field)
    && safeParseConfig(field.columnConfig).sortable === true
}

/**
 * 将表头事件转换为服务端排序参数；取消或无效列返回 null，以恢复列表默认排序。
 * Element Plus 的 prop 可能是 data.xxx 路径，必须从配置匹配回真实 fieldCode，
 * 不直接截取路径或透传任意属性，避免误排系统字段或把虚拟列交给服务端。
 */
export function resolveListColumnSort(fields, { prop, order } = {}) {
  const sortDirection = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : null
  if (!sortDirection || !prop) return null
  const field = (fields || []).find(item => isListColumnSortable(item)
    && (item.fieldCode === prop || getFieldModelPath(item.fieldCode) === prop))
  return field ? { sortField: field.fieldCode, sortDirection } : null
}
