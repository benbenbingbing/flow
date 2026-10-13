/** 宿主注入请求实例；所有接口仅使用 GET/POST，执行令牌不出现在 URL。 */
export function createEntityExcelApi(request) {
  const base = code => `/entities/${encodeURIComponent(code)}`
  const scheme = (code, key) => `${base(code)}/excel-schemes/${encodeURIComponent(key)}`
  /** 文件接口的非 2xx JSON 也会成为 Blob；还原错误文案供操作弹窗展示，避免把过期误报成下载成功。 */
  async function fileRequest(execute) {
    try { return await execute() }
    catch (error) {
      const payload = error?.response?.data
      if (typeof Blob !== 'undefined' && payload instanceof Blob) {
        try {
          const data = JSON.parse(await payload.text())
          const message = data.message || data.detail
          if (typeof message === 'string' && message.trim()) error.message = message
        } catch { /* 非 JSON 错误保留请求层文案，不能当作工作簿下载。 */ }
      }
      if (error?.response?.status === 410 && (!error.message || error.message === '请求失败')) error.message = '任务文件已超过 24 小时保留期，请重新执行'
      throw error
    }
  }
  return {
    settings: code => request.get(`${base(code)}/excel-settings`),
    saveSettings: (code, data) => request.post(`${base(code)}/excel-settings/save`, data),
    publishSettings: (code, data) => request.post(`${base(code)}/excel-settings/publish`, data),
    schemes: (code, params = {}) => request.get(`${base(code)}/excel-schemes`, { params }),
    scheme: (code, key) => request.get(scheme(code, key)),
    saveScheme: (code, data) => request.post(`${base(code)}/excel-schemes/save`, data),
    publishScheme: (code, key, data) => request.post(`${scheme(code, key)}/publish`, data),
    status: (code, key, data) => request.post(`${scheme(code, key)}/status`, data),
    references: (code, key) => request.get(`${scheme(code, key)}/references`),
    remove: (code, key, data) => request.post(`${scheme(code, key)}/delete`, data),
    prepare: data => request.post('/entity-excel/actions/prepare', data),
    template: data => fileRequest(() => request.post('/entity-excel/templates/download', data, { responseType: 'blob', silentError: true })),
    /** 网络重试复用 requestId；选择新文件或改变校验/保存操作后生成新标识。 */
    importFile(file, actionContextToken, requestId, checkOnly = false) {
      const data = new FormData()
      data.append('file', file)
      data.append('actionContextToken', actionContextToken)
      data.append('requestId', requestId)
      return request.post(checkOnly ? '/entity-excel/import-checks' : '/entity-excel/imports', data, {
        headers: { 'Content-Type': undefined }, timeout: 120000
      })
    },
    export: data => request.post('/entity-excel/exports', data, { timeout: 120000 }),
    job: id => request.get(`/entity-excel-jobs/${encodeURIComponent(id)}`),
    errors: (id, params) => request.get(`/entity-excel-jobs/${encodeURIComponent(id)}/errors`, { params }),
    download: (id, kind) => fileRequest(() => request.get(`/entity-excel-jobs/${encodeURIComponent(id)}/files/${encodeURIComponent(kind)}`, { responseType: 'blob', silentError: true }))
  }
}
