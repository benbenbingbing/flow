import request from '@/utils/request'

/**
 * 获取实体数据新增时使用的表单
 * @param entityCode 实体编码
 * @returns 表单信息，包含表单字段
 */
export function getFormForNewData(entityCode) {
  return request.get(`/entity-form-resolve/new-data/${entityCode}`)
}

/**
 * 根据实体数据ID获取当前流程节点对应的表单
 * 用于查看数据详情时，根据数据所在流程节点显示不同的表单
 * @param entityCode 实体编码
 * @param entityDataId 实体数据ID
 * @returns 表单信息，包含当前任务节点信息
 */
export function getFormForViewData(entityCode, entityDataId) {
  return request.get(`/entity-form-resolve/view-data/${entityCode}/${entityDataId}`)
}
