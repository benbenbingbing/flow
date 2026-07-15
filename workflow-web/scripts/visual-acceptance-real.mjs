import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import http from 'node:http'

const chromePath = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const baseUrl = 'http://localhost:3000'
const debugPort = 9333
const outDir = path.resolve('docs/visual-acceptance')
const userDataDir = path.join(tmpdir(), `workflow-visual-${Date.now()}`)
mkdirSync(outDir, { recursive: true })

const preLoginRoute = ['01-login', '/login', ['流程配置系统', 'Workflow Configuration System', '登 录']]
const routes = [
  ['02-home', '/home', ['待办任务', '已办任务', '我发起的']],
  ['03-entity', '/entity', ['实体管理', '新建实体']],
  ['04-process', '/process', ['流程列表', '新建流程']],
  ['05-project-list', '/entity/list/project_nitiation', ['立项管理', '新增数据']],
  ['06-req-list', '/entity/list/req01', ['需求申请', '新增数据']],
  ['07-user', '/system/user', ['用户管理', '新增用户']],
  ['08-role', '/system/role', ['角色管理', '新增角色']],
  ['09-group', '/system/group', ['用户组管理', '新增用户组']],
  ['10-org', '/system/org', ['组织部门管理', '新增']],
  ['11-menu', '/system/menu', ['菜单管理', '新增顶级菜单']],
  ['12-dict', '/system/dict', ['字典设置', '字典类型']],
  ['13-dev-guide', '/system/dev-guide', ['列表字段扩展']],
  ['14-custom-list-guide', '/system/custom-list-guide', ['自定义列表组件']],
  ['15-custom-form-guide', '/system/custom-form-guide', ['自定义表单组件']]
]

function delay(ms) { return new Promise(resolve => setTimeout(resolve, ms)) }
function requestJson(url, method = 'GET') {
  return new Promise((resolve, reject) => {
    const req = http.request(url, { method }, res => {
      let body = ''
      res.on('data', chunk => { body += chunk })
      res.on('end', () => {
        try { resolve(JSON.parse(body)) } catch (e) { reject(new Error(body.slice(0, 200))) }
      })
    })
    req.on('error', reject)
    req.end()
  })
}
async function waitJson(url, timeout = 30000) {
  const start = Date.now()
  while (Date.now() - start < timeout) {
    try { return await requestJson(url) } catch { await delay(300) }
  }
  throw new Error(`timeout ${url}`)
}
class Cdp {
  constructor(wsUrl) { this.wsUrl = wsUrl; this.id = 1; this.pending = new Map(); this.handlers = new Map() }
  async connect() {
    this.ws = new WebSocket(this.wsUrl)
    this.ws.addEventListener('message', event => {
      const msg = JSON.parse(event.data)
      if (msg.id && this.pending.has(msg.id)) {
        const p = this.pending.get(msg.id); this.pending.delete(msg.id)
        msg.error ? p.reject(new Error(msg.error.message)) : p.resolve(msg.result || {})
      } else if (msg.method && this.handlers.has(msg.method)) {
        this.handlers.get(msg.method).forEach(h => h(msg.params || {}))
      }
    })
    await new Promise((resolve, reject) => { this.ws.addEventListener('open', resolve, { once: true }); this.ws.addEventListener('error', reject, { once: true }) })
  }
  on(method, handler) { if (!this.handlers.has(method)) this.handlers.set(method, []); this.handlers.get(method).push(handler) }
  send(method, params = {}) {
    const id = this.id++
    this.ws.send(JSON.stringify({ id, method, params }))
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      setTimeout(() => { if (this.pending.has(id)) { this.pending.delete(id); reject(new Error(`timeout ${method}`)) } }, 15000)
    })
  }
  close() { this.ws?.close() }
}
async function createClient() {
  const target = await requestJson(`http://127.0.0.1:${debugPort}/json/new?${encodeURIComponent(baseUrl + '/login')}`, 'PUT')
  const cdp = new Cdp(target.webSocketDebuggerUrl)
  await cdp.connect()
  await cdp.send('Page.enable')
  await cdp.send('Runtime.enable')
  await cdp.send('Network.enable')
  await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
  return cdp
}
async function evalValue(cdp, expression) {
  const res = await cdp.send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true })
  if (res.exceptionDetails) throw new Error(res.exceptionDetails.text || 'evaluate failed')
  return res.result.value
}
async function navigate(cdp, route) {
  await cdp.send('Page.navigate', { url: baseUrl + route })
  await delay(2500)
}
async function screenshot(cdp, name) {
  const shot = await cdp.send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true })
  const file = path.join(outDir, `${name}.png`)
  writeFileSync(file, Buffer.from(shot.data, 'base64'))
  return file
}

const chrome = spawn(chromePath, [
  '--headless=new', `--remote-debugging-port=${debugPort}`, `--user-data-dir=${userDataDir}`,
  '--no-first-run', '--no-default-browser-check', '--disable-gpu', 'about:blank'
], { stdio: ['ignore', 'ignore', 'ignore'] })

const results = []
try {
  await waitJson(`http://127.0.0.1:${debugPort}/json/version`)
  const cdp = await createClient()
  await delay(1500)
  {
    const [name, route, expected] = preLoginRoute
    await navigate(cdp, route)
    const page = await evalValue(cdp, `(() => ({
      url: location.href,
      text: document.body.innerText.slice(0, 3000),
      messages: [...document.querySelectorAll('.el-message')].map(e => e.innerText),
      errors: [...document.querySelectorAll('.el-message--error')].map(e => e.innerText)
    }))()`)
    const missing = expected.filter(item => !page.text.includes(item))
    const file = await screenshot(cdp, name)
    results.push({ name, route, file, missing, messages: page.messages, errors: page.errors })
  }
  await evalValue(cdp, `(() => {
    const inputs = [...document.querySelectorAll('input')];
    const set = (el, value) => { el.value = value; el.dispatchEvent(new Event('input', { bubbles: true })); el.dispatchEvent(new Event('change', { bubbles: true })); };
    set(inputs.find(i => i.placeholder?.includes('用户名')) || inputs[0], 'admin');
    set(inputs.find(i => i.placeholder?.includes('密码')) || inputs[1], 'admin');
    [...document.querySelectorAll('button')].find(b => b.textContent.replace(/\\s+/g,'').includes('登录'))?.click();
    return true;
  })()`)
  await delay(2500)
  for (const [name, route, expected] of routes) {
    await navigate(cdp, route)
    const page = await evalValue(cdp, `(() => ({
      url: location.href,
      text: document.body.innerText.slice(0, 3000),
      messages: [...document.querySelectorAll('.el-message')].map(e => e.innerText),
      errors: [...document.querySelectorAll('.el-message--error')].map(e => e.innerText)
    }))()`)
    const missing = expected.filter(item => !page.text.includes(item))
    const file = await screenshot(cdp, name)
    results.push({ name, route, file, missing, messages: page.messages, errors: page.errors })
  }
  cdp.close()
  const failures = results.filter(r => r.missing.length || r.errors.length)
  writeFileSync(path.join(outDir, 'visual-acceptance-results.json'), JSON.stringify(results, null, 2))
  if (failures.length) console.log(JSON.stringify(failures, null, 2))
  assert.equal(failures.length, 0, 'visual acceptance failures')
  console.log(`visual acceptance passed: ${results.length} pages, screenshots in ${outDir}`)
} finally {
  chrome.kill('SIGTERM')
  await delay(500)
  try { rmSync(userDataDir, { recursive: true, force: true }) } catch {}
}
