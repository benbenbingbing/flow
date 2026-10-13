import test from 'node:test'
import assert from 'node:assert/strict'
import { createEntityExcelApi } from '../src/entityExcel.js'
const harness = () => {
  const calls = []
  const api = createEntityExcelApi({ get: (...args) => calls.push(['GET', ...args]), post: (...args) => calls.push(['POST', ...args]) })
  return { calls, api }
}
test('Excel API只使用GET/POST，配置编码作为路径段编码', async () => {
  const { api, calls } = harness()
  api.schemes('a/b'); api.saveScheme('a/b', { config: {} }); api.status('a/b','main',{ status: 'DISABLED', expectedRevision: 2 }); api.remove('a/b','main',{ expectedRevision: 2 })
  assert.equal(calls[0][1], '/entities/a%2Fb/excel-schemes')
  assert.deepEqual(calls.map(c => c[0]), ['GET','POST','POST','POST'])
})
test('校验和导入分别上传multipart，调用者重试保留requestId，令牌不入URL', async () => {
  const { api, calls } = harness()
  const file = new File(['content'], 'data.xlsx')
  api.importFile(file, 'private-token', 'request-1', true)
  api.importFile(file, 'private-token', 'request-2', false)
  api.importFile(file, 'private-token', 'request-2', false)
  assert.deepEqual(calls.map(c => c[1]), ['/entity-excel/import-checks','/entity-excel/imports','/entity-excel/imports'])
  assert.equal(calls[0][2].get('actionContextToken'), 'private-token')
  assert.equal(calls[1][2].get('requestId'), calls[2][2].get('requestId'))
  assert.equal(calls[0][2].get('file').name, 'data.xlsx')
  assert.equal(calls[0][3].headers['Content-Type'], undefined)
})
test('文件过期的 HTTP JSON Blob 还原业务原因，下载错误不作为文件返回', async () => {
  const error = Object.assign(new Error('请求失败'), { response: { status: 410, data: new Blob([JSON.stringify({ message: '任务文件已超过24小时保留期，请重新执行' })], { type: 'application/json' }) } })
  const api = createEntityExcelApi({ get: async (_, options) => { assert.equal(options.responseType, 'blob'); assert.equal(options.silentError, true); throw error } })
  await assert.rejects(api.download('job', 'ERROR_REPORT'), failure => failure === error && failure.message === '任务文件已超过24小时保留期，请重新执行')
})
