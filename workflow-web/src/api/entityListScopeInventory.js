import request from '@/utils/request'

/** 查询存量列表数据范围盘点清单。 */
export function getEntityListScopeInventory(params) {
  return request({
    url: '/entity-list-scope-inventory',
    method: 'get',
    params
  })
}

/** 扫描尚未纳入盘点的 OBSERVE 列表。 */
export function refreshEntityListScopeInventory() {
  return request({
    url: '/entity-list-scope-inventory/refresh',
    method: 'post'
  })
}

/** 原子批量确认盘点策略。 */
export function batchConfirmEntityListScopeInventory(items) {
  return request({
    url: '/entity-list-scope-inventory/batch-confirm',
    method: 'post',
    data: { items }
  })
}
