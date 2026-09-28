/** 管理员交接使用独立接口，不依赖节点对普通办理人的“允许转办”设置。 */
export function createTaskHandoverApi(transport) {
  return {
    users: (params = {}) => transport.get('/task-handover/users', { params }),
    tasks: params => transport.get('/task-handover/tasks', { params }),
    transfer: data => transport.post('/task-handover/transfer', data)
  }
}
