import assert from 'node:assert/strict'
import { randomUUID } from 'node:crypto'
import { spawn } from 'node:child_process'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import http from 'node:http'

const apiBase = process.env.WORKFLOW_API_BASE || 'http://localhost:8080/api'
const webBase = process.env.WORKFLOW_WEB_BASE || 'http://localhost:3000'
const testUsername = process.env.TEST_USERNAME
const testPassword = process.env.TEST_PASSWORD
const chromePath = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const debugPort = Number(process.env.FILE_UPLOAD_DEBUG_PORT || 9444)
const stamp = new Date().toISOString().replace(/[-:TZ.]/g, '').slice(2, 12)
const suffix = `${stamp}${randomUUID().replaceAll('-', '').slice(0, 8)}`
const entityCode = `upload_entity_${suffix}`
const entityName = `文件图片上传验收实体${suffix}`
const listKey = 'upload_records'
const listName = '文件图片上传验收列表'
const recordName = `上传验收记录${suffix}`
const evidenceDir = path.resolve('docs/file-upload-e2e')
const userDataDir = mkdtempSync(path.join(tmpdir(), 'workflow-upload-browser-'))
const fixtureDir = mkdtempSync(path.join(tmpdir(), 'workflow-upload-files-'))
const textName = `acceptance-${suffix}.txt`
const imageName = `acceptance-${suffix}.png`
const textPath = path.join(fixtureDir, textName)
const imagePath = path.join(fixtureDir, imageName)
const evidence = {
  apiBase,
  webBase,
  entityCode,
  entityName,
  steps: []
}

assert.ok(testUsername, 'TEST_USERNAME is required')
assert.ok(testPassword, 'TEST_PASSWORD is required')
mkdirSync(evidenceDir, { recursive: true })
writeFileSync(textPath, `real file upload acceptance ${suffix}\n`)
writeFileSync(
  imagePath,
  Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Wl2WQAAAABJRU5ErkJggg==',
    'base64'
  )
)

let token = ''

function record(name, data) {
  evidence.steps.push({ name, data })
  return data
}

async function api(method, url, body) {
  const response = await fetch(apiBase + url, {
    method,
    signal: AbortSignal.timeout(25000),
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body == null ? undefined : JSON.stringify(body)
  })
  const text = await response.text()
  let json
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    throw new Error(`${method} ${url} returned non-json: HTTP ${response.status}`)
  }
  if (!response.ok || (json?.code != null && ![0, 200].includes(Number(json.code)))) {
    throw new Error(`${method} ${url} failed: HTTP ${response.status}`)
  }
  return json?.data ?? json
}

function toList(page) {
  if (Array.isArray(page)) return page
  return page?.records || page?.list || page?.rows || []
}

function getField(fields, code) {
  const field = fields.find(item => item.fieldCode === code)
  assert.ok(field?.id, `实体字段 ${code} 应存在`)
  return field
}

function formField(fields, code, componentType) {
  const field = getField(fields, code)
  return {
    fieldId: field.id,
    fieldCode: code,
    fieldName: field.fieldName,
    fieldLabel: field.fieldName,
    fieldType: field.fieldType,
    componentType,
    isRequired: 1,
    isReadonly: 0,
    isHidden: 0,
    gridSpan: 24
  }
}

function listField(fields, code, width) {
  const field = getField(fields, code)
  return {
    fieldId: field.id,
    fieldCode: code,
    fieldName: field.fieldName,
    showInList: true,
    isQuery: code === 'name',
    queryType: code === 'name' ? 'LIKE' : 'EQ',
    width,
    align: 'left',
    dataSourceType: 'ENTITY_FIELD',
    dataSourceConfig: '',
    renderComponent: '',
    formatter: '',
    columnConfig: JSON.stringify({ minWidth: width, showOverflowTooltip: true }),
    queryConfig: '{}',
    renderConfig: '{}'
  }
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

async function waitFor(check, message, timeout = 20000) {
  const start = Date.now()
  while (Date.now() - start < timeout) {
    if (await check()) return
    await delay(150)
  }
  throw new Error(`timeout waiting for ${message}`)
}

function requestJson(url, method = 'GET') {
  return new Promise((resolve, reject) => {
    const req = http.request(url, { method }, res => {
      let body = ''
      res.on('data', chunk => { body += chunk })
      res.on('end', () => {
        try {
          resolve(JSON.parse(body))
        } catch {
          reject(new Error(body.slice(0, 300)))
        }
      })
    })
    req.on('error', reject)
    req.end()
  })
}

class Cdp {
  constructor(wsUrl) {
    this.wsUrl = wsUrl
    this.id = 1
    this.pending = new Map()
    this.handlers = new Map()
  }

  async connect() {
    this.ws = new WebSocket(this.wsUrl)
    this.ws.addEventListener('message', event => {
      const message = JSON.parse(event.data)
      if (message.id && this.pending.has(message.id)) {
        const pending = this.pending.get(message.id)
        this.pending.delete(message.id)
        message.error
          ? pending.reject(new Error(message.error.message))
          : pending.resolve(message.result || {})
      } else if (message.method && this.handlers.has(message.method)) {
        this.handlers.get(message.method).forEach(handler => handler(message.params || {}))
      }
    })
    await new Promise((resolve, reject) => {
      this.ws.addEventListener('open', resolve, { once: true })
      this.ws.addEventListener('error', reject, { once: true })
    })
  }

  send(method, params = {}) {
    const id = this.id++
    this.ws.send(JSON.stringify({ id, method, params }))
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      setTimeout(() => {
        if (!this.pending.has(id)) return
        this.pending.delete(id)
        reject(new Error(`timeout ${method}`))
      }, 15000)
    })
  }

  on(method, handler) {
    if (!this.handlers.has(method)) {
      this.handlers.set(method, [])
    }
    this.handlers.get(method).push(handler)
  }

  close() {
    this.ws?.close()
  }
}

async function evaluate(cdp, expression) {
  const response = await cdp.send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true
  })
  if (response.exceptionDetails) {
    throw new Error(response.exceptionDetails.text || 'browser evaluation failed')
  }
  return response.result.value
}

async function navigate(cdp, route) {
  await cdp.send('Page.navigate', { url: webBase + route })
  await delay(1800)
}

async function setFile(cdp, index, file) {
  const document = await cdp.send('DOM.getDocument', { depth: -1, pierce: true })
  const inputs = await cdp.send('DOM.querySelectorAll', {
    nodeId: document.root.nodeId,
    selector: '.entity-form-dialog input[type="file"]'
  })
  assert.ok(inputs.nodeIds.length > index, `上传输入框数量不足: ${inputs.nodeIds.length}`)
  await cdp.send('DOM.setFileInputFiles', {
    nodeId: inputs.nodeIds[index],
    files: [file]
  })
}

async function prepareFixture() {
  const login = await api('POST', '/auth/login', {
    username: testUsername,
    password: testPassword
  })
  token = login.token
  record('login', { id: login.id, username: login.username })

  const entity = await api('POST', '/entity', {
    entityCode,
    entityName,
    description: 'FILE 与 IMAGE 浏览器真实上传验收',
    fields: [
      {
        fieldCode: 'attachment',
        fieldName: '验收附件',
        fieldType: 'FILE',
        isRequired: true,
        isUnique: false,
        editable: true,
        sortOrder: 10
      },
      {
        fieldCode: 'siteImage',
        fieldName: '验收图片',
        fieldType: 'IMAGE',
        isRequired: true,
        isUnique: false,
        editable: true,
        sortOrder: 20
      }
    ]
  })
  await api('POST', `/entity/${entity.id}/publish`)
  const detail = await api('GET', `/entity/${entity.id}`)
  const fields = detail.fields || entity.fields || []
  record('createAndPublishEntity', {
    id: entity.id,
    fields: fields.map(field => ({
      id: field.id,
      code: field.fieldCode,
      type: field.fieldType
    }))
  })

  const form = await api('POST', '/entity-form', {
    entityId: entity.id,
    formName: '文件图片上传验收表单',
    formKey: `upload_form_${suffix}`,
    description: '浏览器真实选择文件、保存和回显',
    layoutType: 'vertical',
    isDefault: true,
    status: 1,
    fields: [
      formField(fields, 'name', 'input'),
      formField(fields, 'attachment', 'file'),
      formField(fields, 'siteImage', 'image')
    ]
  })
  const formRelease = await api('POST', `/entity-forms/${form.id}/publish`, {
    description: '文件图片上传验收表单发布'
  })
  assert.equal(formRelease.status, 'ACTIVE')
  record('createAndPublishForm', {
    id: form.id,
    releaseId: formRelease.id,
    version: formRelease.version
  })

  const list = await api('POST', '/entity-list-config/save', {
    entityId: entity.id,
    entityCode,
    listKey,
    listName,
    description: '浏览器上传回显验收',
    isDefault: true,
    viewConfig: {
      search: { defaultVisibleCount: 2, collapsible: true, labelWidth: 100 },
      table: { stripe: true, border: true, showIndex: true, size: 'default' },
      pagination: { pageSize: 10, pageSizes: [10, 20, 50] }
    },
    fields: [
      listField(fields, 'name', 220),
      listField(fields, 'attachment', 260),
      listField(fields, 'siteImage', 220)
    ]
  })
  const listRelease = await api('POST', `/entity-list-config/${list.id}/publish`, {
    description: '文件图片上传验收列表发布'
  })
  assert.equal(listRelease.status, 'ACTIVE')
  record('createAndPublishList', {
    id: list.id,
    releaseId: listRelease.id,
    version: listRelease.version
  })

  return {
    entityId: entity.id,
    formId: form.id,
    listConfigId: list.id,
    route: `/entity-list/${entityCode}/${listKey}`
  }
}

async function runBrowser(fixture) {
  const chrome = spawn(chromePath, [
    '--headless=new',
    `--remote-debugging-port=${debugPort}`,
    `--user-data-dir=${userDataDir}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-gpu',
    'about:blank'
  ], { stdio: ['ignore', 'ignore', 'ignore'] })

  try {
    await waitFor(
      async () => {
        try {
          await requestJson(`http://127.0.0.1:${debugPort}/json/version`)
          return true
        } catch {
          return false
        }
      },
      'Chrome DevTools'
    )
    const target = await requestJson(
      `http://127.0.0.1:${debugPort}/json/new?${encodeURIComponent(webBase + '/login')}`,
      'PUT'
    )
    const cdp = new Cdp(target.webSocketDebuggerUrl)
    await cdp.connect()
    await cdp.send('Page.enable')
    await cdp.send('Runtime.enable')
    await cdp.send('DOM.enable')
    await cdp.send('Network.enable')
    await cdp.send('Emulation.setDeviceMetricsOverride', {
      width: 1440,
      height: 1000,
      deviceScaleFactor: 1,
      mobile: false
    })
    const uploadRequestIds = new Set()
    const uploadResults = []
    cdp.on('Network.requestWillBeSent', ({ requestId, request }) => {
      if (request.url.endsWith('/api/file/upload')) {
        uploadRequestIds.add(requestId)
      }
    })
    cdp.on('Network.loadingFinished', ({ requestId }) => {
      if (!uploadRequestIds.has(requestId)) return
      uploadRequestIds.delete(requestId)
      cdp.send('Network.getResponseBody', { requestId })
        .then(({ body }) => {
          uploadResults.push(JSON.parse(body))
        })
        .catch(error => {
          uploadResults.push({ code: -1, message: error.message })
        })
    })

    await delay(1000)
    await evaluate(cdp, `(() => {
      const inputs = [...document.querySelectorAll('input')]
      const set = (element, value) => {
        element.value = value
        element.dispatchEvent(new Event('input', { bubbles: true }))
        element.dispatchEvent(new Event('change', { bubbles: true }))
      }
      set(inputs.find(input => input.placeholder?.includes('用户名')) || inputs[0], ${JSON.stringify(testUsername)})
      set(inputs.find(input => input.placeholder?.includes('密码')) || inputs[1], ${JSON.stringify(testPassword)})
      ;[...document.querySelectorAll('button')]
        .find(button => button.textContent.replace(/\\s+/g, '').includes('登录'))
        ?.click()
      return true
    })()`)
    await waitFor(
      () => evaluate(cdp, `location.pathname !== '/login' && Boolean(localStorage.getItem('token'))`),
      'browser login'
    )

    await navigate(cdp, fixture.route)
    await waitFor(
      () => evaluate(
        cdp,
        `document.body.innerText.includes('新增数据')
          && !document.body.innerText.includes('业务列表加载失败')`
      ),
      'real upload list'
    )
    await evaluate(cdp, `(() => {
      const button = [...document.querySelectorAll('button')]
        .find(item => item.textContent.replace(/\\s+/g, '').includes('新增数据'))
      if (!button) throw new Error('新增数据按钮不存在')
      button.click()
      return true
    })()`)
    await waitFor(
      () => evaluate(cdp, `Boolean(document.querySelector('.entity-form-dialog'))`),
      'create data dialog'
    )
    await evaluate(cdp, `(() => {
      const dialog = document.querySelector('.entity-form-dialog')
      const input = [...dialog.querySelectorAll('input:not([type="file"])')]
        .find(item => !item.disabled && item.type !== 'hidden')
      if (!input) throw new Error('名称输入框不存在')
      input.value = ${JSON.stringify(recordName)}
      input.dispatchEvent(new Event('input', { bubbles: true }))
      input.dispatchEvent(new Event('change', { bubbles: true }))
      return true
    })()`)

    await setFile(cdp, 0, textPath)
    await waitFor(
      () => uploadResults.length >= 1,
      'file upload success'
    )
    assert.equal(Number(uploadResults[0]?.code), 200, JSON.stringify(uploadResults[0]))
    await setFile(cdp, 1, imagePath)
    await waitFor(
      () => uploadResults.length >= 2,
      'image upload success'
    )
    assert.equal(Number(uploadResults[1]?.code), 200, JSON.stringify(uploadResults[1]))
    await evaluate(cdp, `(() => {
      const button = [...document.querySelectorAll('.entity-form-dialog button')]
        .find(item => item.textContent.replace(/\\s+/g, '').includes('创建数据'))
      if (!button) throw new Error('创建数据按钮不存在')
      button.click()
      return true
    })()`)
    await waitFor(
      () => evaluate(cdp, `(() => {
        const dialog = document.querySelector('.entity-form-dialog')
        return !dialog || dialog.getClientRects().length === 0
      })()`),
      'data creation'
    )
    await waitFor(
      () => evaluate(cdp, `document.body.innerText.includes(${JSON.stringify(recordName)})`),
      'created row'
    )

    await evaluate(cdp, `(() => {
      const row = [...document.querySelectorAll('.el-table__row')]
        .find(item => item.innerText.includes(${JSON.stringify(recordName)}))
      const button = row && [...row.querySelectorAll('button')]
        .find(item => item.textContent.replace(/\\s+/g, '').includes('编辑'))
      if (!button) throw new Error('编辑按钮不存在')
      button.click()
      return true
    })()`)
    await waitFor(
      () => evaluate(
        cdp,
        `(() => {
          const dialog = document.querySelector('.entity-form-dialog')
          return Boolean(dialog)
            && dialog.getClientRects().length > 0
            && dialog.querySelectorAll('.file-list .file-item').length >= 1
            && dialog.querySelectorAll('.el-upload-list__item').length >= 1
        })()`
      ),
      'persisted upload values'
    )
    const shot = await cdp.send('Page.captureScreenshot', {
      format: 'png',
      captureBeyondViewport: true
    })
    const screenshot = path.join(evidenceDir, 'real-file-image-upload.png')
    writeFileSync(screenshot, Buffer.from(shot.data, 'base64'))
    cdp.close()
    record('browserUploadAndEditRecall', {
      route: fixture.route,
      fileName: textName,
      imageName,
      uploadUrls: uploadResults.map(result => result.data?.url),
      screenshot
    })
  } finally {
    chrome.kill('SIGTERM')
    await delay(500)
  }
}

async function verifySavedData() {
  const page = await api(
    'GET',
    `/entity-data/entity/${entityCode}/list-with-config?listKey=${listKey}&name=${encodeURIComponent(recordName)}&name_op=EQ`
  )
  const row = toList(page).find(item => item.name === recordName || item.data?.name === recordName)
  assert.ok(row?.id, '浏览器创建的数据应能从真实列表查询到')
  const detail = await api('GET', `/entity-data/entity/${entityCode}/detail/${row.id}?listKey=${listKey}`)
  const attachmentValue = detail.data?.attachment
  const siteImageValue = detail.data?.siteImage
  const attachment = Array.isArray(attachmentValue) ? attachmentValue[0] : attachmentValue
  const siteImage = Array.isArray(siteImageValue) ? siteImageValue[0] : siteImageValue
  assert.match(String(attachment), /^\/uploads\//)
  assert.match(String(siteImage), /^\/uploads\//)

  const origin = apiBase.replace(/\/api$/, '')
  const fileResponse = await fetch(origin + attachment, { signal: AbortSignal.timeout(10000) })
  const imageResponse = await fetch(origin + siteImage, { signal: AbortSignal.timeout(10000) })
  assert.equal(fileResponse.status, 200)
  assert.equal(imageResponse.status, 200)
  assert.equal(await fileResponse.text(), readFileSync(textPath, 'utf8'))
  assert.ok((await imageResponse.arrayBuffer()).byteLength > 0)
  record('verifySavedDataAndStaticAccess', {
    dataId: detail.id,
    attachment,
    siteImage,
    fileStatus: fileResponse.status,
    imageStatus: imageResponse.status
  })
}

function writeEvidence(result) {
  evidence.result = result
  evidence.conclusion = result === 'PASS'
    ? 'PASS: FILE 与 IMAGE 已通过浏览器真实上传、保存、编辑回显和静态访问验证'
    : 'FAIL: FILE/IMAGE 真实上传验收未完成'
  const file = path.join(
    evidenceDir,
    `file-upload-${suffix}${result === 'PASS' ? '' : '-failed'}.json`
  )
  const report = {
    result: evidence.result,
    conclusion: evidence.conclusion,
    entityCode,
    stepNames: evidence.steps.map(step => step.name)
  }
  writeFileSync(file, JSON.stringify(report, null, 2), { mode: 0o600 })
  writeFileSync(path.join(evidenceDir, 'latest.json'), JSON.stringify(report, null, 2), { mode: 0o600 })
  return file
}

let fixture
try {
  fixture = await prepareFixture()
  await runBrowser(fixture)
  await verifySavedData()
  const file = writeEvidence('PASS')
  console.log(`real file/image upload passed: ${file}`)
} catch (error) {
  const file = writeEvidence('FAIL')
  console.error(`real file/image upload failed: ${file}`)
  throw error
} finally {
  rmSync(userDataDir, { recursive: true, force: true })
  rmSync(fixtureDir, { recursive: true, force: true })
}
