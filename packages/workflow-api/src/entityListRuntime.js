/** 为宿主注入的请求客户端创建已发布列表查询接口。 */
export function createEntityListRuntimeApi(request) {

const entityListRuntimeApi = {
  getSchema(entityCode, listKey, scene = 'PAGE', release = {}) {
    return request.get(`/entity-lists/${entityCode}/${listKey}/schema`, {
      params: {
        scene,
        configHash:
          release.configHash || undefined,
        compositionContext:
          release.compositionContext || undefined,
        runtimeContext: release.runtimeContext || undefined,
        candidateAction: release.candidateAction || undefined
      }
    })
  },

  query(entityCode, listKey, data = {}) {
    return request.post(`/entity-lists/${entityCode}/${listKey}/query`, data)
  },

  simulate(entityCode, listKey, data = {}) {
    return request.post(`/entity-lists/${entityCode}/${listKey}/scope-simulation`, data)
  }
}

return entityListRuntimeApi
}
