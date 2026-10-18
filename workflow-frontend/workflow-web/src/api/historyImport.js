import request from '@/utils/request'

const root = '/history-import'
export const historyImportApi = {
  catalog: () => request.get(`${root}/catalog`),
  list: page => request.get(`${root}/batches`, { params: { page, size: 20 } }),
  create: plan => request.post(`${root}/batches`, plan),
  get: id => request.get(`${root}/batches/${id}`),
  save: (id, revision, plan) => request.post(`${root}/batches/${id}/plan`, { revision, plan }),
  template: (id, kind) => request.get(`${root}/batches/${id}/templates/${kind}`, { responseType: 'blob' }),
  upload(id, revision, kind, file) {
    const data = new FormData()
    data.append('file', file)
    data.append('revision', revision)
    return request.post(`${root}/batches/${id}/files/${kind}`, data)
  },
  validate: (id, revision) => request.post(`${root}/batches/${id}/validate`, { revision }),
  // 请求绑定预检版本和具体提交组，网络响应丢失时重试也不会误执行下一组。
  execute: (id, validationHash, groupKey, retry) => request.post(`${root}/batches/${id}/execute`,
    { validationHash, groupKey, retry }, { timeout: 90000 }),
  archives: (entityCode, recordId) => request.get('/process-archives', { params: { entityCode, recordId } }),
  archive: id => request.get(`/process-archives/${id}`)
}
