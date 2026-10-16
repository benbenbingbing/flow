import request from '@/utils/request'

/** 实体物理结构发布操作 API。 */
export const schemaOperationApi = {
  latest(entityId) {
    return request.get(`/entity-schema-operation/${entityId}/latest`)
  },
  retry(entityId) {
    return request.post(`/entity-schema-operation/${entityId}/retry`)
  },
  terminate(entityId) {
    return request.post(`/entity-schema-operation/${entityId}/terminate`)
  }
}
