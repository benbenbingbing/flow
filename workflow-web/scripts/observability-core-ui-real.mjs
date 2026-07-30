import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import http from 'node:http'
import { tmpdir } from 'node:os'
import path from 'node:path'

const chromePath = process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const baseUrl = process.env.WORKFLOW_WEB_BASE || 'http://127.0.0.1:18081'
const username = process.env.TEST_USERNAME?.trim()
const password = process.env.TEST_PASSWORD
const debugPort = Number(process.env.WORKFLOW_UI_DEBUG_PORT || 9344)
const outDir = path.resolve(process.env.WORKFLOW_UI_RESULT_DIR || '.codex-artifacts/production-observability/ui')
const userDataDir = path.join(tmpdir(), `flow-observability-ui-${Date.now()}`)

assert.ok(username, 'TEST_USERNAME is required')
assert.ok(password, 'TEST_PASSWORD is required')
mkdirSync(outDir, { recursive: true })

const pages = [
  ['home', '/home', ['流程配置系统', '待办任务']],
  ['entity', '/entity', ['实体管理']],
  ['process', '/process', ['流程名称']],
  ['user', '/system/user', ['用户管理']],
  ['audit', '/system/audit-logs', ['系统日志']]
]

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms))
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
          reject(new Error(body.slice(0, 200)))
        }
      })
    })
    req.on('error', reject)
    req.end()
  })
}

async function waitJson(url, timeout = 30000) {
  const start = Date.now()
  while (Date.now() - start < timeout) {
    try {
      return await requestJson(url)
    } catch {
      await delay(300)
    }
  }
  throw new Error(`timeout waiting for ${url}`)
}

class Cdp {
  constructor(wsUrl) {
    this.wsUrl = wsUrl
    this.id = 1
    this.pending = new Map()
  }

  async connect() {
    this.ws = new WebSocket(this.wsUrl)
    this.ws.addEventListener('message', event => {
      const message = JSON.parse(event.data)
      if (!message.id || !this.pending.has(message.id)) return
      const pending = this.pending.get(message.id)
      this.pending.delete(message.id)
      if (message.error) {
        pending.reject(new Error(message.error.message))
      } else {
        pending.resolve(message.result || {})
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

  close() {
    this.ws?.close()
  }
}

async function createClient() {
  const target = await requestJson(
    `http://127.0.0.1:${debugPort}/json/new?${encodeURIComponent(`${baseUrl}/login`)}`,
    'PUT'
  )
  const cdp = new Cdp(target.webSocketDebuggerUrl)
  await cdp.connect()
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Network.enable')
  await cdp.send('Emulation.setDeviceMetricsOverride', {
    width: 1440,
    height: 1000,
    deviceScaleFactor: 1,
    mobile: false
  })
  return cdp
}

async function evalValue(cdp, expression) {
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

async function waitFor(cdp, expression, label, timeout = 15000) {
  const start = Date.now()
  while (Date.now() - start < timeout) {
    if (await evalValue(cdp, expression)) return
    await delay(300)
  }
  throw new Error(`timeout waiting for ${label}`)
}

async function navigate(cdp, route) {
  await cdp.send('Page.navigate', { url: `${baseUrl}${route}` })
  await delay(2200)
}

async function screenshot(cdp, name) {
  const shot = await cdp.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const file = path.join(outDir, `${name}.png`)
  writeFileSync(file, Buffer.from(shot.data, 'base64'))
  return file
}

const chrome = spawn(chromePath, [
  '--headless=new',
  `--remote-debugging-port=${debugPort}`,
  `--user-data-dir=${userDataDir}`,
  '--no-first-run',
  '--no-default-browser-check',
  '--disable-gpu',
  'about:blank'
], { stdio: ['ignore', 'ignore', 'ignore'] })

const results = []
let cdp
try {
  await waitJson(`http://127.0.0.1:${debugPort}/json/version`)
  cdp = await createClient()
  await delay(1200)

  await waitFor(cdp, `document.body.innerText.includes('流程配置系统')`, 'login page')
  await evalValue(cdp, `(() => {
    const inputs = [...document.querySelectorAll('input')]
    const set = (element, value) => {
      element.value = value
      element.dispatchEvent(new Event('input', { bubbles: true }))
      element.dispatchEvent(new Event('change', { bubbles: true }))
    }
    set(inputs.find(input => input.placeholder?.includes('用户名')) || inputs[0], ${JSON.stringify(username)})
    set(inputs.find(input => input.placeholder?.includes('密码')) || inputs[1], ${JSON.stringify(password)})
    ;[...document.querySelectorAll('button')]
      .find(button => button.textContent.replace(/\\s+/g, '').includes('登录'))
      ?.click()
    return true
  })()`)
  await waitFor(
    cdp,
    `location.pathname !== '/login' && Boolean(localStorage.getItem('token'))`,
    'authenticated home'
  )

  for (const [name, route, expected] of pages) {
    await navigate(cdp, route)
    const page = await evalValue(cdp, `(() => ({
      url: location.href,
      text: document.body.innerText.slice(0, 4000),
      errorMessages: [...document.querySelectorAll('.el-message--error')].map(element => element.innerText)
    }))()`)
    const missing = expected.filter(item => !page.text.includes(item))
    const file = await screenshot(cdp, name)
    results.push({
      name,
      route,
      url: page.url,
      screenshot: file,
      missing,
      errorMessages: page.errorMessages
    })
  }

  const failures = results.filter(result => result.missing.length || result.errorMessages.length)
  writeFileSync(path.join(outDir, 'core-ui-results.json'), JSON.stringify(results, null, 2))
  if (failures.length) {
    console.log(JSON.stringify(failures, null, 2))
  }
  assert.equal(failures.length, 0, 'core UI acceptance failed')
  console.log(`observability core UI acceptance passed: ${results.length} pages, screenshots in ${outDir}`)
} finally {
  cdp?.close()
  chrome.kill('SIGTERM')
  await delay(500)
  try {
    rmSync(userDataDir, { recursive: true, force: true })
  } catch {}
}
