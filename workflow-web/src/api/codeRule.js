import request from '@/utils/request'

export const codeRuleApi = {
  /**
   * 获取实体的编码规则
   */
  getByEntityCode(entityCode) {
    return request.get(`/entity-code-rule/${entityCode}`)
  },

  /**
   * 保存编码规则
   */
  save(data) {
    return request.post('/entity-code-rule', data)
  },

  /**
   * 预览编码
   */
  preview(data) {
    return request.post('/entity-code-rule/preview', data)
  }
}
