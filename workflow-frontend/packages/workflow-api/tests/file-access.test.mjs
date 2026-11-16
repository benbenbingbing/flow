import test from 'node:test'
import assert from 'node:assert/strict'
import { createFileApi, createBrowserFileAccess } from '../src/file.js'
import { createRequestRuntime } from '../src/request.js'

const session = { token: 'login-token', tokenExpiresAt: new Date(Date.now() + 3600000).toISOString() }
const blob = new Blob(['file content'], { type: 'application/pdf' })

test('文件 ID 经应用接口鉴权，中文原名和文件流完整返回', async () => {
  for (const delegated of [false, true]) {
    const seen = []
    const runtime = createRequestRuntime({ getSession: () => session, adapter: async config => {
      seen.push(config)
      return { config, status: 200, data: blob, headers: {
        'content-type': 'application/pdf',
        'content-disposition': `${config.url.endsWith('/preview') ? 'inline' : 'attachment'}; filename*=UTF-8''${encodeURIComponent('项目 合同.pdf')}`
      } }
    } })
    if (delegated) runtime.configureEmbedDelegatedRequest({ getAccessToken: () => 'embed-token' })
    const api = createFileApi(runtime.request).fileApi
    for (const url of ['file-id', 'remote-file-id', 'local-file-id']) {
      for (const method of ['preview', 'download']) {
        const file = await api[method](url)
        assert.equal(file.blob, blob)
        assert.equal(file.filename, '项目 合同.pdf')
        assert.equal(file.inline, method === 'preview')
        assert.equal(seen.at(-1).url, `/file/${method}`)
        assert.deepEqual(seen.at(-1).params, { fileId: url })
        assert.equal(seen.at(-1).headers.get('Authorization'), `Bearer ${delegated ? 'embed-token' : 'login-token'}`)
        if (delegated) assert.equal(seen.at(-1).withCredentials, false)
      }
    }
  }
})

test('JSON 附件保留为文件，403 JSON Blob 还原错误并阻止后续下载', async () => {
  let forbidden = false
  const json = new Blob(['{"name":"example"}'], { type: 'application/json' })
  const runtime = createRequestRuntime({ getSession: () => session, adapter: async config => {
    if (forbidden) throw Object.assign(new Error('HTTP 403'), { config, response: { status: 403, data: new Blob([
      JSON.stringify({ code: 403, message: '无权访问该文件' })
    ], { type: 'application/json' }) } })
    return { config, status: 200, data: json, headers: { 'content-type': 'application/json', 'content-disposition': 'attachment; filename="example.json"' } }
  } })
  const api = createFileApi(runtime.request).fileApi
  assert.equal((await api.download('file-id')).blob, json)
  forbidden = true
  await assert.rejects(api.preview('file-id'), error => error.status === 403 && error.message === '无权访问该文件')
})

test('文本文件 Blob 保留响应 charset，避免浏览器按本地编码猜测中文内容', async () => {
  const data = new Blob(['中文内容'], { type: 'text/plain' })
  const api = createFileApi({ get: async () => ({ data, headers: {
    'content-type': 'text/plain;charset=UTF-8', 'content-disposition': 'inline; filename="readme.txt"'
  } }) }).fileApi
  const result = await api.preview('file-id')
  assert.equal(result.blob.type, 'text/plain;charset=utf-8')
  assert.equal(await result.blob.text(), '中文内容')
})

test('文件读取遇到访问令牌过期时续期重试，仍返回文件流及响应头', async () => {
  const state = { ...session, applySession(value) { Object.assign(this, value) } }
  const seen = []
  const runtime = createRequestRuntime({ getSession: () => state, adapter: async config => {
    seen.push(config.url)
    if (config.url === '/auth/refresh') return { config, status: 200, headers: {}, data: {
      code: 200, data: { token: 'refreshed-token', tokenExpiresAt: session.tokenExpiresAt }
    } }
    if (!config._authRetried) throw Object.assign(new Error('expired'), { config, response: {
      status: 401, data: new Blob([JSON.stringify({ code: 401, errorCode: 'AUTH_ACCESS_EXPIRED' })], { type: 'application/json' })
    } })
    assert.equal(config.headers.get('Authorization'), 'Bearer refreshed-token')
    return { config, status: 200, data: blob, headers: { 'content-disposition': 'inline; filename="report.pdf"' } }
  } })
  const result = await createFileApi(runtime.request).fileApi.preview('file-id')
  assert.equal(result.blob, blob)
  assert.equal(result.inline, true)
  assert.deepEqual(seen, ['/file/preview', '/auth/refresh', '/file/preview'])
})

function browserHarness(api) {
  const opened = [], links = [], scheduled = [], revoked = [], errors = [], created = []
  const popup = { closed: false, opener: {}, location: { replace: value => opened.push(value) }, close() { this.closed = true } }
  const access = createBrowserFileAccess(api, {
    browser: { open: (...args) => { opened.push(args); return popup } },
    document: { body: { appendChild() {} }, createElement: () => {
      const link = { click() { links.push({ href: this.href, download: this.download }) }, remove() {} }; return link
    } },
    urls: { createObjectURL: value => { created.push(value); return 'blob:authenticated-file' }, revokeObjectURL: value => revoked.push(value) },
    schedule: (callback, delay) => scheduled.push({ callback, delay }), notifyError: error => errors.push(error)
  })
  return { access, popup, opened, links, scheduled, revoked, errors, created }
}

test('预览在等待请求前预留无 opener 窗口，成功后只导航 Blob，关闭后释放资源', async () => {
  let finish
  const h = browserHarness({ preview: url => {
    assert.equal(url, 'file-id')
    assert.equal(h.popup.opener, null)
    return new Promise(resolve => { finish = resolve })
  } })
  const pending = h.access.preview('file-id')
  assert.deepEqual(h.opened, [['about:blank', '_blank']])
  finish({ blob, filename: '合同.pdf', inline: true })
  assert.equal(await pending, true)
  assert.equal(h.opened.at(-1), 'blob:authenticated-file')
  assert.equal(h.links.length, 0)
  h.popup.closed = true
  h.scheduled[0].callback()
  assert.deepEqual(h.revoked, ['blob:authenticated-file'])
})

test('下载使用服务端原名；不支持预览的文件关闭空窗口并按附件保存', async () => {
  for (const mode of ['preview', 'download']) {
    const h = browserHarness({ [mode]: async () => ({ blob, filename: '项目 合同.docx', inline: false }) })
    assert.equal(await h.access[mode]('remote-file-id'), true)
    assert.deepEqual(h.links, [{ href: 'blob:authenticated-file', download: '项目 合同.docx' }])
    assert.equal(h.opened.some(value => typeof value === 'string' && value.startsWith('http')), false)
    assert.equal(h.scheduled[0].delay, 60000)
    h.scheduled[0].callback()
    assert.equal(h.revoked.length, 1)
    if (mode === 'preview') assert.equal(h.popup.closed, true)
  }
})

test('权限失败关闭空窗口、提示一次，不生成 Blob 或下载链接', async () => {
  const error = new Error('无权访问该文件')
  const h = browserHarness({ preview: async () => { throw error } })
  assert.equal(await h.access.preview('file-id'), false)
  assert.equal(h.popup.closed, true)
  assert.deepEqual(h.errors, [error])
  assert.equal(h.created.length, 0)
  assert.equal(h.links.length, 0)
})

test('删除将 fileId 放在查询参数并保留登录鉴权，不把存储地址放入请求体', async () => {
  let seen
  const runtime = createRequestRuntime({ getSession: () => session, adapter: async config => {
    seen = config
    return { config, status: 200, headers: {}, data: { code: 200, data: null } }
  } })
  await createFileApi(runtime.request).fileApi.delete('file-delete-id')
  assert.equal(seen.url, '/file')
  assert.equal(seen.method, 'post')
  assert.deepEqual(seen.params, { fileId: 'file-delete-id' })
  assert.equal(seen.headers.get('Authorization'), 'Bearer login-token')
  assert.equal(typeof seen.data === 'string' ? JSON.parse(seen.data) : seen.data, null)
})

test('浏览器入口拒绝旧存储地址，不发起请求或打开窗口', async () => {
  const h = browserHarness({ preview: () => { throw new Error('不应请求') } })
  assert.equal(await h.access.preview('s3://files/old.pdf'), false)
  assert.equal(await h.access.preview({ url: '/uploads/old.pdf' }), false)
  assert.equal(h.opened.length, 0)
})
