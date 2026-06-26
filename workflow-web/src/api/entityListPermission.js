import request from '@/utils/request'

export const entityListPermissionApi = {
  // 查询某实体的所有权限规则
  getByEntityCode(entityCode) {
    return request.get(`/entity-list-permission/entity/${entityCode}`)
  },

  // 新增规则
  create(data) {
    return request.post('/entity-list-permission', data)
  },

  // 更新规则
  update(id, data) {
    return request.put(`/entity-list-permission/${id}`, data)
  },

  // 删除规则
  delete(id) {
    return request.delete(`/entity-list-permission/${id}`)
  },

  // 切换启用状态
  toggleEnabled(id) {
    return request.post(`/entity-list-permission/${id}/toggle`)
  },

  // 预览权限 SQL
  previewSql(entityCode, listConfigId) {
    return request.get('/entity-list-permission/preview-sql', {
      params: { entityCode, listConfigId }
    })
  }
}
