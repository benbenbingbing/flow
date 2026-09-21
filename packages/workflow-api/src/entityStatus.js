
/** 为指定 transport 创建接口集合，不在包内持有应用单例。 */
export function createEntityStatusApi(request) {

/**
 * 查询实体的状态列表
 */
function getEntityStatusList(entityCode, runtimeContext = {}) {
  return request.get(`/entity-status/list/${entityCode}`, {
    params: {
      viewCompositionTraversalToken:
        runtimeContext.viewCompositionTraversalToken || undefined
    }
  })
}

/**
 * 根据分类查询
 */
function getEntityStatusByCategory(
  entityCode,
  category,
  runtimeContext = {}
) {
  return request.get(`/entity-status/list/${entityCode}/${category}`, {
    params: {
      viewCompositionTraversalToken:
        runtimeContext.viewCompositionTraversalToken || undefined
    }
  })
}

/**
 * 保存实体状态
 */
function saveEntityStatus(data) {
  return request.post('/entity-status/save', data)
}

/**
 * 批量保存实体状态
 */
function saveEntityStatusList(entityCode, statuses) {
  return request.post(`/entity-status/save-list/${entityCode}`, statuses)
}

/**
 * 删除实体状态
 */
function deleteEntityStatus(id) {
  return request.post(`/entity-status/delete/${id}`)
}

return { getEntityStatusList, getEntityStatusByCategory, saveEntityStatus, saveEntityStatusList, deleteEntityStatus }
}
