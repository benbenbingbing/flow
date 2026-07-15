import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import http from 'node:http'

const chromePath = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const appPort = 3300
const debugPort = 9223
const baseUrl = `http://127.0.0.1:${appPort}`
const userDataDir = mkdtempSync(path.join(tmpdir(), 'workflow-web-cdp-'))
const bpmnXml = `<?xml version="1.0" encoding="UTF-8"?><bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn"><bpmn:process id="Process_1" isExecutable="true"><bpmn:startEvent id="StartEvent_1" name="开始"/><bpmn:userTask id="Task_1" name="审批"/><bpmn:endEvent id="EndEvent_1" name="结束"/><bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_1"/><bpmn:sequenceFlow id="Flow_2" sourceRef="Task_1" targetRef="EndEvent_1"/></bpmn:process><bpmndi:BPMNDiagram id="BPMNDiagram_1"><bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1"><bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1"><dc:Bounds x="150" y="100" width="36" height="36"/></bpmndi:BPMNShape><bpmndi:BPMNShape id="Task_1_di" bpmnElement="Task_1"><dc:Bounds x="240" y="78" width="100" height="80"/></bpmndi:BPMNShape><bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1"><dc:Bounds x="400" y="100" width="36" height="36"/></bpmndi:BPMNShape><bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1"><di:waypoint x="186" y="118"/><di:waypoint x="240" y="118"/></bpmndi:BPMNEdge><bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2"><di:waypoint x="340" y="118"/><di:waypoint x="400" y="118"/></bpmndi:BPMNEdge></bpmndi:BPMNPlane></bpmndi:BPMNDiagram></bpmn:definitions>`

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function httpRequestJson(url, method = 'GET') {
  return new Promise((resolve, reject) => {
    const req = http.request(url, { method }, (res) => {
      let body = ''
      res.setEncoding('utf8')
      res.on('data', (chunk) => { body += chunk })
      res.on('end', () => {
        try {
          resolve(JSON.parse(body))
        } catch (error) {
          reject(new Error(`Invalid JSON from ${url}: ${body.slice(0, 200)}`))
        }
      })
    })
    req.on('error', reject)
    req.end()
  })
}

function httpRequestText(url) {
  return new Promise((resolve, reject) => {
    const req = http.request(url, (res) => {
      let body = ''
      res.setEncoding('utf8')
      res.on('data', (chunk) => { body += chunk })
      res.on('end', () => resolve(body))
    })
    req.on('error', reject)
    req.end()
  })
}

async function waitForHttp(url, timeoutMs = 30000, json = false) {
  const started = Date.now()
  while (Date.now() - started < timeoutMs) {
    try {
      if (json) await httpRequestJson(url)
      else await httpRequestText(url)
      return
    } catch {
      await delay(300)
    }
  }
  throw new Error(`Timed out waiting for ${url}`)
}

class CdpClient {
  constructor(wsUrl) {
    this.wsUrl = wsUrl
    this.nextId = 1
    this.pending = new Map()
    this.handlers = new Map()
  }

  async connect() {
    this.ws = new WebSocket(this.wsUrl)
    this.ws.addEventListener('message', (event) => {
      const message = JSON.parse(event.data)
      if (message.id && this.pending.has(message.id)) {
        const { resolve, reject } = this.pending.get(message.id)
        this.pending.delete(message.id)
        if (message.error) reject(new Error(`${message.error.message}: ${message.error.data || ''}`))
        else resolve(message.result || {})
        return
      }
      if (message.method && this.handlers.has(message.method)) {
        for (const handler of this.handlers.get(message.method)) handler(message.params || {})
      }
    })
    await new Promise((resolve, reject) => {
      this.ws.addEventListener('open', resolve, { once: true })
      this.ws.addEventListener('error', reject, { once: true })
    })
  }

  on(method, handler) {
    if (!this.handlers.has(method)) this.handlers.set(method, [])
    this.handlers.get(method).push(handler)
  }

  send(method, params = {}) {
    const id = this.nextId++
    this.ws.send(JSON.stringify({ id, method, params }))
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id)
          reject(new Error(`CDP timeout: ${method}`))
        }
      }, 10000)
    })
  }

  close() {
    this.ws?.close()
  }
}

function apiData(pathname) {
  const entity = {
    id: 'e2e-entity',
    entityCode: 'project',
    entityName: '项目立项',
    name: '项目立项',
    status: 'PUBLISHED',
    fields: [
      { id: 'f-name', fieldCode: 'name', fieldName: '名称', fieldType: 'STRING', componentType: 'input', isRequired: true },
      { id: 'f-priority', fieldCode: 'priority', fieldName: '优先级', fieldType: 'SELECT', componentType: 'select', optionsJson: '[{"label":"高","value":"HIGH"},{"label":"低","value":"LOW"}]' },
      { id: 'f-date', fieldCode: 'planDate', fieldName: '计划日期', fieldType: 'DATE', componentType: 'date' }
    ]
  }
  const row = { id: 'row-1', dataNo: 'D-001', name: '演示数据', status: 'DRAFT', createdAt: '2026-07-15T08:00:00Z', data: { name: '演示数据', priority: 'HIGH', planDate: '2026-07-15' } }
  const listFields = [
    { fieldCode: 'name', fieldName: '名称', fieldType: 'STRING', showInList: true, isQuery: true, queryType: 'LIKE', width: 160, align: 'left' },
    { fieldCode: 'priority', fieldName: '优先级', fieldType: 'SELECT', showInList: true, isQuery: true, queryType: 'EQ', optionsJson: '[{"label":"高","value":"HIGH"},{"label":"低","value":"LOW"}]', width: 100, align: 'center' }
  ]
  const process = { id: 'e2e-process', name: '演示流程', processKey: 'demo_process', status: 'DRAFT', bpmnXml, version: 1, createdAt: '2026-07-15T08:00:00Z' }
  const menus = [
    { id: 'm-home', title: '首页', menuName: '首页', path: '/home', icon: 'HomeFilled', status: '0', visible: '0' },
    { id: 'm-process', title: '流程管理', menuName: '流程管理', path: '/process', icon: 'Share', status: '0', visible: '0' },
    { id: 'm-entity', title: '实体管理', menuName: '实体管理', path: '/entity', icon: 'Box', status: '0', visible: '0' },
    { id: 'm-system', title: '系统管理', menuName: '系统管理', path: '/system', icon: 'Setting', status: '0', visible: '0', children: [
      { id: 'm-menu', title: '菜单管理', menuName: '菜单管理', path: '/system/menu', status: '0', visible: '0' },
      { id: 'm-user', title: '用户管理', menuName: '用户管理', path: '/system/user', status: '0', visible: '0' },
      { id: 'm-role', title: '角色管理', menuName: '角色管理', path: '/system/role', status: '0', visible: '0' },
      { id: 'm-group', title: '用户组管理', menuName: '用户组管理', path: '/system/group', status: '0', visible: '0' },
      { id: 'm-org', title: '组织部门管理', menuName: '组织部门管理', path: '/system/org', status: '0', visible: '0' },
      { id: 'm-dict', title: '字典设置', menuName: '字典设置', path: '/system/dict', status: '0', visible: '0' }
    ] }
  ]

  if (pathname === '/auth/current') return { id: 'u1', username: 'admin', nickname: '管理员', roles: ['admin'] }
  if (pathname === '/auth/permissions') return ['*', 'entity:add', 'entity:view', 'entity:edit', 'entity:delete']
  if (pathname === '/system/menu/sidebar-tree' || pathname === '/system/menu/tree' || pathname === '/system/role/menu-tree') return menus
  if (pathname.includes('/type-options')) return [{ label: '目录', value: 'M' }, { label: '菜单', value: 'C' }, { label: '按钮', value: 'F' }]
  if (pathname.includes('/enabled')) return [{ id: '1', name: '默认项', label: '默认项' }]
  if (pathname.endsWith('/roles')) return [{ id: 'r1', roleName: '管理员' }]
  if (pathname.endsWith('/users')) return [{ id: 'u1', username: 'admin', nickname: '管理员' }]
  if (pathname.includes('/system/user/list')) return [{ id: 'u1', username: 'admin', nickname: '管理员', status: '0' }]
  if (pathname.includes('/system/role/list')) return [{ id: 'r1', roleName: '管理员', roleCode: 'admin', status: '0' }]
  if (pathname.includes('/system/group')) return [{ id: 'g1', groupName: '默认用户组', status: '0' }]
  if (pathname.includes('/system/org/tree')) return [{ id: 'o1', orgName: '总部', name: '总部', children: [] }]
  if (pathname.includes('/system/dict')) return [{ id: 'd1', dictName: '状态', dictCode: 'status', status: '0', items: [] }]

  if (pathname === '/process' || pathname === '/process/published' || pathname === '/process/unbound' || pathname === '/process/bindable') return [process]
  if (pathname === '/process/e2e-process' || pathname === '/process/mock-process') return process
  if (pathname.includes('/process/') && pathname.endsWith('/nodes')) return [{ id: 'StartEvent_1', nodeId: 'StartEvent_1', nodeName: '开始' }]
  if (pathname.includes('/process/') && pathname.endsWith('/versions')) return [{ id: 'v1', version: 1, bpmnXml }]
  if (pathname.includes('/process-instance/') && pathname.endsWith('/progress')) return { processName: '演示流程', status: 'RUNNING', bpmnXml, completedNodes: ['StartEvent_1'], activeNodes: ['Task_1'], executedSequenceFlows: ['Flow_1'], nodeHistory: [], tasks: [], nodeAssigneeMap: {} }
  if (pathname.includes('/process-task/statistics')) return { todo: 1, done: 1, myStarted: 1 }
  if (pathname.includes('/process-task/') || pathname.includes('/process-instance/my-started')) return { list: [{ id: 'task-1', processName: '演示流程', taskName: '审批', status: 'PENDING' }], total: 1 }

  if (pathname === '/entity') return [entity]
  if (pathname === '/entity/e2e-entity' || pathname === '/entity/code/project') return entity
  if (pathname.includes('/entity-form/entity/e2e-entity/default')) return { id: 'e2e-form', formName: '默认表单', fields: entity.fields }
  if (pathname.includes('/entity-form/entity/e2e-entity/fields')) return entity.fields
  if (pathname.includes('/entity-form/entity/e2e-entity')) return [{ id: 'e2e-form', formName: '默认表单', isDefault: true, fields: entity.fields }]
  if (pathname === '/entity-form/e2e-form' || pathname.includes('/entity-form/e2e-form/fields')) return pathname.endsWith('/fields') ? entity.fields : { id: 'e2e-form', formName: '默认表单', fields: entity.fields }
  if (pathname.includes('/entity-list-config/entity/e2e-entity')) return [{ id: 'e2e-list', listName: '默认列表', listKey: 'default', isDefault: true }]
  if (pathname === '/entity-list-config/e2e-list') return { id: 'e2e-list', listName: '默认列表', listKey: 'default', fields: listFields, toolbarButtons: [], rowActionButtons: [] }
  if (pathname.includes('/entity-data/entity/project/list-with-config') || pathname.includes('/entity-data/entity/project')) return [row]
  if (pathname.includes('/entity-status')) return [{ statusCode: 'DRAFT', statusName: '草稿' }, { statusCode: 'PENDING', statusName: '审批中' }]
  if (pathname.includes('/entity-code-rule')) return { prefix: 'D', enabled: true }

  return []
}

function mockApiResponse(requestUrl) {
  const url = new URL(requestUrl)
  const pathname = url.pathname.replace(/^\/api/, '') || '/'
  return { code: 0, data: apiData(pathname), message: 'ok' }
}

function getRoutes() {
  const source = readFileSync('src/router/index.js', 'utf8')
  return [...source.matchAll(/path:\s*'([^']+)'/g)]
    .map((match) => match[1])
    .filter((routePath) => routePath.startsWith('/') && !routePath.includes('pathMatch') && routePath !== '/')
    .map((routePath) => routePath
      .replace(':id?', 'e2e-process')
      .replace(':id', routePath.includes('entity-form') ? 'e2e-form' : routePath.includes('entity-list-config') ? 'e2e-list' : routePath.includes('entity') ? 'e2e-entity' : 'e2e-process')
      .replace(':nodeId', 'StartEvent_1')
      .replace(':code', 'project')
      .replace(':entityCode', 'project')
      .replace(':entityId', 'e2e-entity')
      .replace(':instanceId', 'e2e-instance'))
}

const expectedRouteText = new Map([
  ['/login', ['流程配置系统', '默认账号']],
  ['/home', ['首页']],
  ['/process', ['流程管理']],
  ['/process/design/e2e-process', ['新建流程', '节点配置']],
  ['/process/form/StartEvent_1', ['表单设计']],
  ['/entity', ['实体管理']],
  ['/entity/design/e2e-entity', ['项目立项', '字段列表']],
  ['/entity/data/project', ['数据管理']],
  ['/entity/list/project', ['演示数据', '优先级']],
  ['/entity-list-config/e2e-entity', ['实体列表配置']],
  ['/entity-list-config/design/e2e-list', ['列表配置设计']],
  ['/entity-form/list-by-entity/e2e-entity', ['表单管理', '默认表单']],
  ['/entity-form/design/e2e-form', ['表单设计']],
  ['/process/progress/e2e-instance', ['演示流程', '流程信息']],
  ['/system/menu', ['菜单管理']],
  ['/system/user', ['用户管理']],
  ['/system/role', ['角色管理']],
  ['/system/group', ['用户组管理']],
  ['/system/org', ['组织部门管理']],
  ['/system/dict', ['字典设置']],
  ['/system/dev-guide', ['列表字段扩展']],
  ['/system/custom-list-guide', ['自定义列表组件']],
  ['/system/custom-form-guide', ['自定义表单组件']]
])

const interactionPlans = new Map([
  ['/process', [{ click: '新建流程', expect: ['新建流程'] }]],
  ['/entity', [{ click: '新建实体', expect: ['实体名称'] }]],
  ['/entity/list/project', [{ click: '新增数据', expect: ['新增数据', '名称'] }]],
  ['/entity-list-config/design/e2e-list', [
    { click: '工具栏按钮', expect: ['工具栏按钮'] },
    { click: '操作列按钮', expect: ['操作列按钮'] }
  ]],
  ['/system/menu', [{ click: '新增顶级菜单', expect: ['菜单名称'] }]],
  ['/system/user', [{ click: '新增用户', expect: ['用户名'] }]],
  ['/system/role', [{ click: '新增角色', expect: ['角色名称'] }]],
  ['/system/group', [{ click: '新增用户组', expect: ['组名称'] }]],
  ['/system/dict', [{ click: '新增', expect: ['字典名称'] }]]
])

async function runInteractionPlan(client, routePath) {
  const plan = interactionPlans.get(routePath) || []
  const interactionResults = []
  for (const step of plan) {
    const clickResult = await client.send('Runtime.evaluate', {
      expression: `(() => {
        const label = ${JSON.stringify(step.click)};
        const isVisible = (el) => {
          const style = getComputedStyle(el);
          const rect = el.getBoundingClientRect();
          return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
        };
        const normalize = (text) => (text || '').replace(/\s+/g, '');
        const selectors = ['button', '.el-button', '.el-tabs__item', '[role="button"]'];
        const candidates = [...document.querySelectorAll(selectors.join(','))]
          .filter(isVisible)
          .filter((el) => normalize(el.textContent).includes(normalize(label)));
        const target = candidates[0];
        if (!target) return { clicked: false, text: document.body.textContent || '' };
        target.click();
        return { clicked: true, text: document.body.textContent || '' };
      })()`,
      returnByValue: true
    })
    await delay(800)
    const textResult = await client.send('Runtime.evaluate', {
      expression: `document.body.textContent || ''`,
      returnByValue: true
    })
    const text = textResult.result.value || ''
    const missingTexts = (step.expect || []).filter((expected) => !text.includes(expected))
    interactionResults.push({ click: step.click, clicked: clickResult.result.value.clicked, missingTexts })
  }
  return interactionResults
}

async function createPage(chrome, routePath) {
  const target = await httpRequestJson(`http://127.0.0.1:${debugPort}/json/new?about:blank`, 'PUT')
  const client = new CdpClient(target.webSocketDebuggerUrl)
  await client.connect()
  const errors = []
  let scriptRequests = 0
  client.on('Runtime.exceptionThrown', (params) => errors.push(params.exceptionDetails?.text || 'runtime exception'))
  client.on('Runtime.consoleAPICalled', (params) => {
    if (params.type === 'error') errors.push(params.args?.map((arg) => arg.value || arg.description).join(' ') || 'console error')
  })
  client.on('Network.loadingFailed', (params) => {
    if (params.errorText && !params.errorText.includes('net::ERR_ABORTED')) errors.push(`network failed: ${params.errorText}`)
  })
  client.on('Network.requestWillBeSent', (params) => {
    if (params.request?.url?.includes('/src/main.js')) scriptRequests++
  })
  client.on('Fetch.requestPaused', async (params) => {
    const body = Buffer.from(JSON.stringify(mockApiResponse(params.request.url))).toString('base64')
    await client.send('Fetch.fulfillRequest', {
      requestId: params.requestId,
      responseCode: 200,
      responseHeaders: [{ name: 'Content-Type', value: 'application/json;charset=utf-8' }],
      body
    }).catch((error) => errors.push(error.message))
  })
  await client.send('Page.enable')
  await client.send('Page.setLifecycleEventsEnabled', { enabled: true })
  await client.send('Network.enable')
  await client.send('Runtime.enable')
  await client.send('Fetch.enable', { patterns: [{ urlPattern: '*://*/api/*' }] })
  if (routePath !== '/login') {
    await client.send('Page.addScriptToEvaluateOnNewDocument', {
      source: `localStorage.setItem('token','mock-token');localStorage.setItem('userInfo',JSON.stringify({id:'u1',username:'admin',nickname:'管理员',roles:['admin']}));localStorage.setItem('permissions',JSON.stringify(['*','entity:add','entity:view','entity:edit','entity:delete']));localStorage.setItem('disabled_menu_paths','[]');`
    })
  }
  const loadPromise = new Promise((resolve) => client.on('Page.loadEventFired', resolve))
  await client.send('Page.navigate', { url: `${baseUrl}${routePath}` })
  await Promise.race([loadPromise, delay(5000)])
  const mounted = await client.send('Runtime.evaluate', {
    expression: `Boolean(document.querySelector('#app')?.children.length)`,
    returnByValue: true
  })
  if (!mounted.result.value) {
    await client.send('Runtime.evaluate', {
      expression: `import(document.querySelector('script[type="module"]')?.src || '/src/main.js')`,
      awaitPromise: true
    }).catch((error) => errors.push(`manual entry import failed: ${error.message}`))
  }
  await delay(routePath.includes('/process/design') || routePath.includes('/process/progress') ? 3500 : 1800)
  const interactions = await runInteractionPlan(client, routePath)
  const result = await client.send('Runtime.evaluate', {
    expression: `(() => ({ path: location.pathname, text: (document.body.textContent || '').trim().slice(0, 1000), title: document.title, errorMessage: document.querySelector('.el-message--error')?.textContent || '', html: document.body.innerHTML.slice(0, 300), appHtml: document.querySelector('#app')?.innerHTML.slice(0, 300) || '' }))()`,
    returnByValue: true
  })
  client.close()
  return { routePath, ...result.result.value, scriptRequests, interactions, errors: errors.filter((error) => !String(error).includes('ResizeObserver')) }
}

const vite = spawn('npx', ['vite', 'preview', '--host', '127.0.0.1', '--port', String(appPort), '--strictPort'], { stdio: ['ignore', 'pipe', 'pipe'] })
const chrome = spawn(chromePath, [
  '--headless=new',
  `--remote-debugging-port=${debugPort}`,
  `--user-data-dir=${userDataDir}`,
  '--no-first-run',
  '--no-default-browser-check',
  '--disable-gpu',
  'about:blank'
], { stdio: ['ignore', 'ignore', 'pipe'] })

try {
  await waitForHttp(`${baseUrl}/`, 30000)
  await waitForHttp(`http://127.0.0.1:${debugPort}/json/version`, 30000, true)
  const routes = getRoutes()
  const results = []
  for (const routePath of routes) {
    results.push(await createPage(chrome, routePath))
  }
  const failures = results.filter((result) => {
    const expectedTexts = expectedRouteText.get(result.routePath) || []
    const missingTexts = expectedTexts.filter((text) => !result.text.includes(text))
    result.missingTexts = missingTexts
    const failedInteractions = (result.interactions || []).filter((interaction) => !interaction.clicked || interaction.missingTexts.length > 0)
    result.failedInteractions = failedInteractions
    return result.errors.length > 0 || !result.text || result.errorMessage || missingTexts.length > 0 || failedInteractions.length > 0
  })
  if (failures.length) {
    console.log(JSON.stringify(failures, null, 2))
  }
  assert.equal(failures.length, 0, 'mock page E2E found route failures')
  const interactionCount = results.reduce((sum, result) => sum + (result.interactions?.length || 0), 0)
  console.log(`mock page e2e passed: ${results.length} routes, ${interactionCount} interactions`)
} finally {
  vite.kill('SIGTERM')
  chrome.kill('SIGTERM')
  await delay(500)
  try {
    rmSync(userDataDir, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
  } catch {}
}
