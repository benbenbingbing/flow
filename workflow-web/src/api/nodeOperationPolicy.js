import request from '@/utils/request'

/** 获取后端权威计算的任务可用动作。 */
export function getTaskAvailableActions(taskId) {
  return request({
    url: `/api/tasks/${taskId}/available-actions`,
    method: 'get'
  })
}

/** 使用草稿矩阵和模拟身份预览单个操作。 */
export function simulateNodeOperationPolicy(data) {
  return request({
    url: '/api/node-operation-policy/simulate',
    method: 'post',
    data
  })
}

/** 根据矩阵自动生成统一配置测试中心覆盖模板。 */
export function generateNodeOperationCoverage(policyJson) {
  return request({
    url: '/api/node-operation-policy/coverage',
    method: 'post',
    data: { policyJson }
  })
}
