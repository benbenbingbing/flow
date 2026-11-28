import assert from 'node:assert/strict'
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs'
import path from 'node:path'
import { createServer } from 'vite'
import { chromium } from '@playwright/test'

// 挂载真实只读字段和上传控件，文件响应由隔离接口模拟，不访问业务数据库或真实对象存储。
const fixture = mkdtempSync(path.resolve('.file-access-fixture-'))
const harness = `
import { createApp, h, reactive } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import FileField from '/@fs${path.resolve('../packages/workflow-extensions/src/extensions/platform/fields/file/pc/FileField.vue').split(path.sep).join('/')}'
import FileUploader from '/src/components/FileUploader.vue'
import { fileApi } from '/src/api/file.js'
import { createFileApi, createRequestRuntime } from '@flow/workflow-api'
const runtime = createRequestRuntime({ getSession: () => ({ token: 'browser-login', tokenExpiresAt: new Date(Date.now()+3600000).toISOString() }) })
const api = createFileApi(runtime.request).fileApi
fileApi.preview = api.preview; fileApi.download = api.download
const state = reactive({ fileId: 'private-file-id' })
const field = { fieldCode: 'files', fieldType: 'FILE', componentType: 'file', fileMaxCount: 2 }
createApp({ setup: () => () => h('main', { style: 'margin:24px;width:700px' }, [
  h('section', { id: 'readonly' }, h(FileField, { field, disabled: true, modelValue: { 合同: [{ fileId: state.fileId, name: '项目 合同.txt' }] } })),
  h('section', { id: 'editable' }, h(FileUploader, { field, modelValue: [{ fileId: state.fileId, name: '项目 合同.txt' }] }))
]) }).use(createPinia()).use(ElementPlus).mount('#app')
window.fileAccessTest = { state, delegate() { runtime.configureEmbedDelegatedRequest({ getAccessToken: () => 'browser-delegated' }) } }
`
writeFileSync(path.join(fixture, 'index.html'), '<html><body><div id="app"></div><script type="module" src="./main.js"></script></body></html>')
writeFileSync(path.join(fixture, 'main.js'), harness)
let server, browser
try {
  server = await createServer({
    cacheDir: path.join(fixture, 'cache'),
    optimizeDeps: { include: ['axios', 'async-validator'], entries: [path.join(fixture, 'index.html')] },
    server: { host: '127.0.0.1', port: 0 }
  })
  await server.listen()
  browser = await chromium.launch({ executablePath: process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome', headless: true })
  const context = await browser.newContext({ acceptDownloads: true })
  const page = await context.newPage()
  const requests = [], external = [], errors = []
  let forbidden = false, unsupported = false
  page.on('pageerror', error => errors.push(error.message))
  context.on('request', request => { if (/storage\.example/.test(request.url())) external.push(request.url()) })
  await page.route('**/api/file/**', async route => {
    const request = route.request(), url = new URL(request.url())
    requests.push({ path: url.pathname, source: url.searchParams.get('fileId'), headers: request.headers() })
    if (forbidden) {
      await route.fulfill({ status: 403, contentType: 'application/json', body: JSON.stringify({ code: 403, message: '无权访问该文件' }) })
    } else {
      const inline = url.pathname.endsWith('/preview') && !unsupported
      await route.fulfill({ status: 200, headers: {
        'Content-Type': inline ? 'text/plain;charset=UTF-8' : 'application/octet-stream',
        'Content-Disposition': (inline ? 'inline' : 'attachment') + "; filename*=UTF-8''" + encodeURIComponent('项目 合同.txt')
      }, body: '已鉴权的文件内容' })
    }
  })
  const address = server.httpServer.address()
  await page.goto(`http://127.0.0.1:${address.port}/${path.basename(fixture)}/`)
  await page.waitForFunction(() => Boolean(window.fileAccessTest))
  for (const section of ['readonly', 'editable']) {
    const previewReady = context.waitForEvent('page')
    await page.locator(`#${section}`).getByRole('button', { name: '预览', exact: true }).click()
    const preview = await previewReady
    await preview.waitForURL('blob:**')
    assert.match(await preview.locator('body').innerText(), /已鉴权的文件内容/)
    assert.equal(await preview.evaluate(() => window.opener === null), true)
    await preview.close()
    const downloadReady = page.waitForEvent('download')
    await page.locator(`#${section}`).getByRole('button', { name: '下载', exact: true }).click()
    const download = await downloadReady
    assert.equal(download.suggestedFilename(), '项目 合同.txt')
    assert.equal(await download.failure(), null)
  }
  assert.deepEqual(requests.map(request => request.path), ['/api/file/preview', '/api/file/download', '/api/file/preview', '/api/file/download'])
  assert.ok(requests.every(request => request.source === 'private-file-id' && request.headers.authorization === 'Bearer browser-login'))

  await page.evaluate(() => { window.fileAccessTest.delegate(); window.fileAccessTest.state.fileId = 'delegated-file-id' })
  unsupported = true
  const fallbackReady = page.waitForEvent('download')
  await page.locator('#readonly').getByRole('button', { name: '预览', exact: true }).click()
  assert.equal((await fallbackReady).suggestedFilename(), '项目 合同.txt')
  assert.equal(requests.at(-1).headers.authorization, 'Bearer browser-delegated')
  assert.equal(requests.at(-1).headers['x-flow-embed-protocol'], '1')
  assert.equal(requests.at(-1).source, 'delegated-file-id')

  forbidden = true
  const deniedReady = context.waitForEvent('page')
  await page.locator('#readonly').getByRole('button', { name: '预览', exact: true }).click()
  const denied = await deniedReady
  if (!denied.isClosed()) await denied.waitForEvent('close')
  await page.getByText('无权访问该文件', { exact: true }).waitFor()
  assert.deepEqual(external, [], '文件操作不能直接访问对象存储地址')
  assert.deepEqual(errors, [])
  console.log('file-access-browser-test: passed (authenticated preview/download, original filename, delegated session, fallback, forbidden)')
} finally {
  await browser?.close()
  await server?.close()
  rmSync(fixture, { recursive: true, force: true })
}
