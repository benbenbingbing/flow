import request from '@/utils/request'

export const configReferenceApi = {
  forward(params) {
    return request.get('/api/config-references/forward', { params })
  },
  reverse(params) {
    return request.get('/api/config-references/reverse', { params })
  },
  impact(params) {
    return request.get('/api/config-references/impact', { params })
  }
}

export const configCollaborationApi = {
  list() {
    return request.get('/api/config-collaboration/workspaces')
  },
  get(id) {
    return request.get(`/api/config-collaboration/workspaces/${id}`)
  },
  save(data) {
    return request.post('/api/config-collaboration/workspaces', data)
  },
  createBranch(id, data) {
    return request.post(`/api/config-collaboration/workspaces/${id}/branches`, data)
  },
  saveBranch(id, data) {
    return request.post(`/api/config-collaboration/branches/${id}`, data)
  },
  merge(id, data) {
    return request.post(`/api/config-collaboration/branches/${id}/merge`, data)
  },
  comment(id, data) {
    return request.post(`/api/config-collaboration/workspaces/${id}/comments`, data)
  },
  requestReview(id, data = {}) {
    return request.post(`/api/config-collaboration/workspaces/${id}/reviews`, data)
  },
  decideReview(id, data) {
    return request.post(`/api/config-collaboration/reviews/${id}/decision`, data)
  },
  schedule(id, data) {
    return request.post(`/api/config-collaboration/workspaces/${id}/schedules`, data)
  }
}

export const processInstanceMigrationApi = {
  list() {
    return request.get('/api/process-instance-migrations')
  },
  get(id) {
    return request.get(`/api/process-instance-migrations/${id}`)
  },
  create(data) {
    return request.post('/api/process-instance-migrations', data)
  },
  dryRun(id) {
    return request.post(`/api/process-instance-migrations/${id}/dry-run`)
  },
  execute(id, data) {
    return request.post(`/api/process-instance-migrations/${id}/execute`, data)
  },
  pause(id) {
    return request.post(`/api/process-instance-migrations/${id}/pause`)
  },
  retry(id, data) {
    return request.post(`/api/process-instance-migrations/${id}/retry`, data)
  },
  rollbackItem(id) {
    return request.post(`/api/process-instance-migrations/items/${id}/rollback`)
  }
}
