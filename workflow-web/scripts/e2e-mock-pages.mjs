import assert from 'node:assert/strict'
import { spawn } from 'node:child_process'
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import http from 'node:http'

const chromePath = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
const appPort = 3300
const debugPort = 9223
const baseUrl = `http://127.0.0.1:${appPort}`
const viewportWidth = Number(process.env.E2E_VIEWPORT_WIDTH || 1920)
const viewportHeight = Number(process.env.E2E_VIEWPORT_HEIGHT || 1080)
const userDataDir = mkdtempSync(path.join(tmpdir(), 'workflow-web-cdp-'))
const bpmnXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions
  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:flowable="http://flowable.org/bpmn"
  id="Definitions_1"
  targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="开始事件"/>
    <bpmn:userTask id="UserTask_1" name="用户审批"/>
    <bpmn:endEvent id="EndEvent_1" name="结束事件"/>
    <bpmn:sequenceFlow id="SequenceFlow_1" name="审批连线" sourceRef="StartEvent_1" targetRef="UserTask_1"/>
    <bpmn:serviceTask id="ServiceTask_1" name="服务任务"/>
    <bpmn:sendTask id="SendTask_1" name="发送任务"/>
    <bpmn:receiveTask id="ReceiveTask_1" name="接收任务"/>
    <bpmn:manualTask id="ManualTask_1" name="手动任务"/>
    <bpmn:businessRuleTask id="BusinessRuleTask_1" name="业务规则任务"/>
    <bpmn:scriptTask id="ScriptTask_1" name="脚本任务"/>
    <bpmn:callActivity id="CallActivity_1" name="调用活动" calledElement="demo_sub_process"/>
    <bpmn:subProcess id="SubProcess_1" name="内嵌子流程"/>
    <bpmn:exclusiveGateway id="ExclusiveGateway_1" name="排他网关"/>
    <bpmn:parallelGateway id="ParallelGateway_1" name="并行网关"/>
    <bpmn:inclusiveGateway id="InclusiveGateway_1" name="包容网关"/>
    <bpmn:eventBasedGateway id="EventBasedGateway_1" name="事件网关"/>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="100" y="90" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="UserTask_1_di" bpmnElement="UserTask_1">
        <dc:Bounds x="210" y="68" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="385" y="90" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="SequenceFlow_1_di" bpmnElement="SequenceFlow_1">
        <di:waypoint x="136" y="108"/>
        <di:waypoint x="210" y="108"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape id="ServiceTask_1_di" bpmnElement="ServiceTask_1">
        <dc:Bounds x="100" y="225" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="SendTask_1_di" bpmnElement="SendTask_1">
        <dc:Bounds x="240" y="225" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ReceiveTask_1_di" bpmnElement="ReceiveTask_1">
        <dc:Bounds x="380" y="225" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ManualTask_1_di" bpmnElement="ManualTask_1">
        <dc:Bounds x="520" y="225" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BusinessRuleTask_1_di" bpmnElement="BusinessRuleTask_1">
        <dc:Bounds x="660" y="225" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ScriptTask_1_di" bpmnElement="ScriptTask_1">
        <dc:Bounds x="800" y="225" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="CallActivity_1_di" bpmnElement="CallActivity_1">
        <dc:Bounds x="940" y="225" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="SubProcess_1_di" bpmnElement="SubProcess_1" isExpanded="true">
        <dc:Bounds x="1080" y="215" width="150" height="100"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ExclusiveGateway_1_di" bpmnElement="ExclusiveGateway_1" isMarkerVisible="true">
        <dc:Bounds x="160" y="410" width="50" height="50"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ParallelGateway_1_di" bpmnElement="ParallelGateway_1">
        <dc:Bounds x="360" y="410" width="50" height="50"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="InclusiveGateway_1_di" bpmnElement="InclusiveGateway_1">
        <dc:Bounds x="560" y="410" width="50" height="50"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EventBasedGateway_1_di" bpmnElement="EventBasedGateway_1">
        <dc:Bounds x="760" y="410" width="50" height="50"/>
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

const commonTab = { label: '常用', marker: '节点名称' }
const collaborationTab = { label: '协同', marker: '启用知会' }
const actionTab = { label: '流程动作', marker: '添加动作' }
const advancedTab = { label: '高级', marker: '异步执行' }
const processNodePanelPlans = [
  {
    id: 'StartEvent_1',
    name: '开始事件',
    type: '开始事件',
    tabs: [commonTab, actionTab]
  },
  {
    id: 'EndEvent_1',
    name: '结束事件',
    type: '结束事件',
    tabs: [commonTab, actionTab]
  },
  {
    id: 'UserTask_1',
    name: '用户审批',
    type: '用户任务',
    tabs: [commonTab, collaborationTab, advancedTab, actionTab]
  },
  {
    id: 'ServiceTask_1',
    name: '服务任务',
    type: '服务任务',
    tabs: [commonTab, collaborationTab, advancedTab, actionTab]
  },
  {
    id: 'SendTask_1',
    name: '发送任务',
    type: '发送任务',
    tabs: [commonTab, collaborationTab, advancedTab, actionTab]
  },
  {
    id: 'ReceiveTask_1',
    name: '接收任务',
    type: '接收任务',
    tabs: [commonTab, advancedTab, actionTab]
  },
  {
    id: 'ManualTask_1',
    name: '手动任务',
    type: '手动任务',
    tabs: [commonTab, advancedTab, actionTab]
  },
  {
    id: 'BusinessRuleTask_1',
    name: '业务规则任务',
    type: '业务规则任务',
    tabs: [commonTab, advancedTab, actionTab]
  },
  {
    id: 'ScriptTask_1',
    name: '脚本任务',
    type: '脚本任务（已禁用）',
    tabs: [commonTab, advancedTab, actionTab]
  },
  {
    id: 'CallActivity_1',
    name: '调用活动',
    type: '调用活动',
    tabs: [commonTab, advancedTab, actionTab]
  },
  {
    id: 'SubProcess_1',
    name: '内嵌子流程',
    type: '子流程',
    tabs: [commonTab, actionTab]
  },
  {
    id: 'ExclusiveGateway_1',
    name: '排他网关',
    type: '排他网关',
    tabs: [commonTab, actionTab, advancedTab]
  },
  {
    id: 'ParallelGateway_1',
    name: '并行网关',
    type: '并行网关',
    tabs: [commonTab, actionTab, advancedTab]
  },
  {
    id: 'InclusiveGateway_1',
    name: '包容网关',
    type: '包容网关',
    tabs: [commonTab, actionTab, advancedTab]
  },
  {
    id: 'EventBasedGateway_1',
    name: '事件网关',
    type: '事件网关',
    tabs: [commonTab, actionTab, advancedTab]
  },
  {
    id: 'SequenceFlow_1',
    name: '审批连线',
    type: '顺序流',
    tabs: [commonTab, actionTab]
  }
]

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
  const process = {
    id: 'e2e-process',
    name: '全节点配置验收流程',
    processName: '全节点配置验收流程',
    processKey: 'demo_process',
    status: 'DRAFT',
    bpmnXml,
    version: 1,
    createdAt: '2026-07-15T08:00:00Z'
  }
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
      { id: 'm-dict', title: '字典设置', menuName: '字典设置', path: '/system/dict', status: '0', visible: '0' },
      { id: 'm-extensions', title: '扩展管理', menuName: '扩展管理', path: '/system/extensions', status: '0', visible: '0' }
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
  if (pathname === '/extension-catalog/manage') {
    return {
      list: [
        {
          capabilityType: 'FLOW_ACTION',
          key: 'notify-owner',
          displayName: '通知业务负责人',
          description: '向当前业务负责人发送流程通知',
          sourceType: 'SPRING',
          sourceName: 'notifyOwnerFlowAction',
          status: 'ACTIVE',
          configured: true,
          available: true,
          enabled: true,
          visibilityScope: 'GLOBAL',
          extraParamSchema: { properties: { channel: { type: 'string' } } },
          dynamicExtraParams: true
        },
        {
          capabilityType: 'PERSON_RESOLVER',
          key: 'process-initiator',
          displayName: '流程发起人',
          description: '返回当前流程实例发起人',
          sourceType: 'SPRING',
          sourceName: 'processInitiatorPersonResolver',
          status: 'ACTIVE',
          configured: true,
          available: true,
          enabled: true,
          supportedUsages: ['ASSIGNEE', 'MULTI_INSTANCE', 'CC'],
          extraParamSchema: {},
          dynamicExtraParams: false
        }
      ],
      total: 2,
      pageNum: 1,
      pageSize: 20
    }
  }
  if (pathname === '/ui-extensions') return []

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
  if (pathname === '/entity-lists/project/default/schema') {
    return { id: 'e2e-list', listName: '默认列表', listKey: 'default', fields: listFields, toolbarButtons: [], rowActionButtons: [] }
  }
  if (pathname === '/entity-lists/project/default/query') {
    return { list: [row], total: 1, pageNum: 1, pageSize: 10 }
  }
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
  return [...expectedRouteText.keys()]
}

const expectedRouteText = new Map([
  ['/login', ['流程配置系统']],
  ['/home', ['首页']],
  ['/process', ['流程管理']],
  ['/process/design/e2e-process', ['全节点配置验收流程', '全局动作', '查看 XML']],
  ['/entity', ['实体管理']],
  ['/entity/design/e2e-entity', ['项目立项', '业务字段']],
  ['/entity/data/project', ['新增数据', '导出全部']],
  ['/entity-list/project/default', ['演示数据', '优先级']],
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
  ['/system/extensions', ['扩展类型', '流程动作', '通知业务负责人']],
  ['/system/dev-guide', ['表单与列表配置扩展']],
  ['/system/custom-list-guide', ['自定义列表组件']],
  ['/system/custom-form-guide', ['自定义表单组件']]
])

const interactionPlans = new Map([
  ['/process', [{ click: '新建流程', expect: ['新建流程'] }]],
  ['/entity', [{ click: '新建实体', expect: ['实体名称'] }]],
  ['/entity-list/project/default', [{ click: '新增数据', expect: ['新增数据', '名称'] }]],
  ['/entity-list-config/design/e2e-list', [
    { click: '工具栏按钮', expect: ['工具栏按钮'] },
    { click: '操作列按钮', expect: ['操作列按钮'] }
  ]],
  ['/entity-form/design/e2e-form', [
    {
      click: '表单设置',
      expect: ['基本与布局', '按钮与操作', '数据与事件', '渲染与扩展']
    },
    {
      click: '按钮与操作',
      expect: [
        '先确定按钮在哪些模式和位置出现',
        '稳定编码',
        '权限码',
        '事件链'
      ]
    },
    {
      click: '新增按钮',
      expect: ['自定义按钮']
    },
    {
      click: '更多',
      expect: [
        '自定义按钮设置',
        '图标',
        '按钮样式',
        '执行前校验',
        '二次确认',
        '适用条件'
      ]
    }
  ]],
  ['/system/menu', [{ click: '创建顶级菜单', expect: ['菜单名称'] }]],
  ['/system/user', [{ click: '新增用户', expect: ['用户名'] }]],
  ['/system/role', [{ click: '新增角色', expect: ['角色名称'] }]],
  ['/system/group', [{ click: '新增用户组', expect: ['组名称'] }]],
  ['/system/dict', [{ click: '新增字典', expect: ['字典名称'] }]],
  ['/system/extensions', [{ click: '展开', expect: ['目录状态'] }]]
])

const unexpectedRouteText = new Map([
  ['/system/extensions', ['实体引用单选', '富文本', '下拉单选', '分组标题']]
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

async function runLayoutChecks(client, routePath) {
  if (routePath !== '/entity-list-config/design/e2e-list') return []

  await client.send('Runtime.evaluate', {
    expression: `(() => {
      const tabs = [...document.querySelectorAll('.el-tabs__item')];
      tabs.find((tab) => (tab.textContent || '').trim() === '字段配置')?.click();
    })()`
  })
  await delay(500)
  const result = await client.send('Runtime.evaluate', {
    expression: `(() => {
      const isVisible = (el) => {
        if (!el) return false;
        const rect = el.getBoundingClientRect();
        const style = getComputedStyle(el);
        return rect.width > 0 && rect.height > 0 && style.display !== 'none' && style.visibility !== 'hidden';
      };
      const table = document.querySelector('.field-config-table');
      const headers = [...document.querySelectorAll('.field-config-table th')].filter(isVisible);
      const findRightmost = (label) => headers
        .filter((header) => (header.textContent || '').replace(/\\s+/g, '').includes(label))
        .sort((left, right) => right.getBoundingClientRect().left - left.getBoundingClientRect().left)[0];
      const current = findRightmost('当前配置');
      const operation = findRightmost('操作');
      if (!table || !current || !operation) {
        return [{ name: '字段配置表格列布局', passed: false, detail: '未找到表格或目标列' }];
      }
      const tableRect = table.getBoundingClientRect();
      const currentRect = current.getBoundingClientRect();
      const operationRect = operation.getBoundingClientRect();
      const columnGap = Math.max(0, operationRect.left - currentRect.right);
      const rightGap = Math.max(0, tableRect.right - operationRect.right);
      return [
        {
          name: '当前配置列占满剩余宽度',
          passed: currentRect.width >= 320 && columnGap <= 2,
          detail: JSON.stringify({ currentWidth: currentRect.width, columnGap })
        },
        {
          name: '操作列贴合表格右侧',
          passed: rightGap <= 2,
          detail: JSON.stringify({ rightGap, tableWidth: tableRect.width })
        }
      ];
    })()`,
    returnByValue: true
  })
  return result.result.value || []
}

async function runFormDesignerChecks(client, routePath) {
  if (routePath !== '/entity-form/design/e2e-form') return []

  const hoverResult = await client.send('Runtime.evaluate', {
    expression: `(() => {
      const target = document.querySelector(
        'button[aria-label="查看执行前校验配置说明"]'
      );
      if (!target) return { found: false };
      target.dispatchEvent(new MouseEvent('mouseenter', {
        bubbles: true,
        cancelable: true,
        view: window
      }));
      target.dispatchEvent(new MouseEvent('mouseover', {
        bubbles: true,
        cancelable: true,
        view: window
      }));
      return { found: true };
    })()`,
    returnByValue: true
  })
  await delay(350)

  const result = await client.send('Runtime.evaluate', {
    expression: `(() => {
      const isVisible = (el) => {
        if (!el) return false;
        const rect = el.getBoundingClientRect();
        const style = getComputedStyle(el);
        return rect.width > 0
          && rect.height > 0
          && style.display !== 'none'
          && style.visibility !== 'hidden';
      };
      const fitsViewport = (el) => {
        if (!isVisible(el)) return false;
        const rect = el.getBoundingClientRect();
        return rect.left >= 0
          && rect.top >= 0
          && rect.right <= window.innerWidth
          && rect.bottom <= window.innerHeight;
      };
      const drawer = [...document.querySelectorAll('.el-drawer')]
        .find(isVisible);
      const dialog = [...document.querySelectorAll('.el-dialog')]
        .find(isVisible);
      const helpButtons = [...document.querySelectorAll(
        '.config-help-label__button'
      )].filter(isVisible);
      const tooltip = [...document.querySelectorAll(
        '[role="tooltip"], .el-popper'
      )].filter(isVisible).find((el) =>
        (el.textContent || '').includes('先执行当前表单的必填和格式校验')
      );
      return {
        drawerFits: fitsViewport(drawer),
        dialogFits: fitsViewport(dialog),
        helpButtonCount: helpButtons.length,
        tooltipVisible: Boolean(tooltip),
        viewport: { width: window.innerWidth, height: window.innerHeight }
      };
    })()`,
    returnByValue: true
  })
  const state = result.result.value || {}
  return [
    {
      name: '表单设置抽屉与按钮弹窗完整显示',
      passed: state.drawerFits && state.dialogFits,
      detail: JSON.stringify(state)
    },
    {
      name: '自定义按钮问号悬浮说明',
      passed: Boolean(hoverResult.result.value?.found)
        && state.helpButtonCount >= 5
        && state.tooltipVisible,
      detail: JSON.stringify({
        hoverTargetFound: hoverResult.result.value?.found,
        helpButtonCount: state.helpButtonCount,
        tooltipVisible: state.tooltipVisible
      })
    }
  ]
}

async function runProcessNodePanelChecks(client, routePath) {
  if (routePath !== '/process/design/e2e-process') return []

  const checks = []
  for (const plan of processNodePanelPlans) {
    const clickResult = await client.send('Runtime.evaluate', {
      expression: `(() => {
        const id = ${JSON.stringify(plan.id)};
        const target = document.querySelector('.djs-element[data-element-id="' + CSS.escape(id) + '"] .djs-visual')
          || document.querySelector('.djs-element[data-element-id="' + CSS.escape(id) + '"]');
        if (!target) return { clicked: false, available: [...document.querySelectorAll('.djs-element[data-element-id]')].map(el => el.getAttribute('data-element-id')) };
        target.dispatchEvent(new MouseEvent('mousedown', { bubbles: true, cancelable: true, view: window }));
        target.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, cancelable: true, view: window }));
        target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
        return { clicked: true };
      })()`,
      returnByValue: true
    })
    await delay(350)

    const panelResult = await client.send('Runtime.evaluate', {
      expression: `(() => {
        const normalize = (value) => (value || '').replace(/\\s+/g, '').trim();
        const panel = document.querySelector('aside.node-config-panel');
        const header = panel?.querySelector('.node-config-panel__header strong')?.textContent || '';
        const type = panel?.querySelector('.node-config-panel__meta .el-tag')?.textContent || '';
        const selected = document.querySelector('.djs-element.selected')?.getAttribute('data-element-id') || '';
        const tabs = panel
          ? [...panel.querySelectorAll('.config-tabs > .config-tab-nav [role="tab"]')].map(el => normalize(el.textContent))
          : [];
        return { exists: Boolean(panel), header: normalize(header), type: normalize(type), selected, tabs };
      })()`,
      returnByValue: true
    })
    const panel = panelResult.result.value || {}
    const expectedTabs = plan.tabs.map((tab) => tab.label)
    const missingTabs = expectedTabs.filter((tab) => !panel.tabs?.includes(tab))
    const unexpectedTabs = (panel.tabs || []).filter((tab) => !expectedTabs.includes(tab))
    const selectionPassed = Boolean(clickResult.result.value?.clicked)
      && panel.exists
      && panel.selected === plan.id
      && panel.header === `节点配置·${plan.name}`
      && panel.type === plan.type
      && missingTabs.length === 0
      && unexpectedTabs.length === 0
    checks.push({
      name: `${plan.type}面板与标签集合`,
      passed: selectionPassed,
      detail: JSON.stringify({
        id: plan.id,
        clicked: clickResult.result.value?.clicked,
        selected: panel.selected,
        header: panel.header,
        type: panel.type,
        tabs: panel.tabs,
        missingTabs,
        unexpectedTabs,
        available: clickResult.result.value?.available
      })
    })

    if (!selectionPassed) continue

    for (const tab of plan.tabs) {
      const tabClickResult = await client.send('Runtime.evaluate', {
        expression: `(() => {
          const label = ${JSON.stringify(tab.label)};
          const normalize = (value) => (value || '').replace(/\\s+/g, '').trim();
          const panel = document.querySelector('aside.node-config-panel');
          const target = panel
            ? [...panel.querySelectorAll('.config-tabs > .config-tab-nav [role="tab"]')]
              .find(el => normalize(el.textContent) === normalize(label))
            : null;
          if (!target) return { clicked: false };
          target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, view: window }));
          return { clicked: true };
        })()`,
        returnByValue: true
      })
      await delay(180)
      const tabResult = await client.send('Runtime.evaluate', {
        expression: `(() => {
          const normalize = (value) => (value || '').replace(/\\s+/g, '').trim();
          const panel = document.querySelector('aside.node-config-panel');
          const activeTab = panel?.querySelector('.config-tabs > .config-tab-nav .config-tab-button.active');
          const content = panel?.querySelector('.config-tabs > .config-tab-content');
          return {
            activeTab: normalize(activeTab?.textContent),
            activePaneText: normalize(content?.textContent)
          };
        })()`,
        returnByValue: true
      })
      const tabState = tabResult.result.value || {}
      checks.push({
        name: `${plan.type}·${tab.label}属性页`,
        passed: Boolean(tabClickResult.result.value?.clicked)
          && tabState.activeTab === tab.label
          && tabState.activePaneText?.includes(tab.marker),
        detail: JSON.stringify({
          clicked: tabClickResult.result.value?.clicked,
          activeTab: tabState.activeTab,
          marker: tab.marker,
          activePaneText: tabState.activePaneText?.slice(0, 240)
        })
      })

      if (plan.id === 'UserTask_1' && tab.label === '常用') {
        const toggleResult = await client.send('Runtime.evaluate', {
          expression: `(() => {
            const normalize = (value) => (value || '').replace(/\\s+/g, '').trim();
            const panel = document.querySelector('aside.node-config-panel');
            const content = panel?.querySelector('.config-tabs > .config-tab-content');
            const label = content
              ? [...content.querySelectorAll('.el-form-item__label')]
                .find(el => normalize(el.textContent) === '启用多实例')
              : null;
            const control = label?.closest('.el-form-item')?.querySelector('.el-switch');
            if (control && !control.classList.contains('is-checked')) control.click();
            return { found: Boolean(control) };
          })()`,
          returnByValue: true
        })
        await delay(300)
        const scrollResult = await client.send('Runtime.evaluate', {
          expression: `(() => {
            const normalize = (value) => (value || '').replace(/\\s+/g, '').trim();
            const panel = document.querySelector('aside.node-config-panel');
            const content = panel?.querySelector('.config-tabs > .config-tab-content');
            if (!content) return { exists: false };
            const previousStyles = {
              flex: content.style.flex,
              height: content.style.height
            };
            content.style.flex = '0 0 320px';
            content.style.height = '320px';
            const maxScroll = Math.max(0, content.scrollHeight - content.clientHeight);
            content.scrollTop = maxScroll;
            const result = {
              exists: true,
              clientHeight: content.clientHeight,
              scrollHeight: content.scrollHeight,
              maxScroll,
              scrollTop: content.scrollTop,
              overflowY: getComputedStyle(content).overflowY,
              lowerConfigRendered: normalize(content.textContent).includes('元素变量')
            };
            content.style.flex = previousStyles.flex;
            content.style.height = previousStyles.height;
            return result;
          })()`,
          returnByValue: true
        })
        const scrollState = scrollResult.result.value || {}
        checks.push({
          name: '用户任务·常用纵向滚动',
          passed: Boolean(toggleResult.result.value?.found)
            && scrollState.exists
            && scrollState.maxScroll > 0
            && scrollState.scrollTop > 0
            && ['auto', 'scroll'].includes(scrollState.overflowY)
            && scrollState.lowerConfigRendered,
          detail: JSON.stringify({
            toggleFound: toggleResult.result.value?.found,
            ...scrollState
          })
        })
      }

      if (
        process.env.E2E_SCREENSHOT_PATH
        && process.env.E2E_SCREENSHOT_NODE_ID === plan.id
        && (!process.env.E2E_SCREENSHOT_TAB || process.env.E2E_SCREENSHOT_TAB === tab.label)
      ) {
        const screenshot = await client.send('Page.captureScreenshot', {
          format: 'png',
          captureBeyondViewport: false
        })
        writeFileSync(process.env.E2E_SCREENSHOT_PATH, Buffer.from(screenshot.data, 'base64'))
      }
    }
  }
  return checks
}

async function createPage(chrome, routePath) {
  const target = await httpRequestJson(`http://127.0.0.1:${debugPort}/json/new?about:blank`, 'PUT')
  const client = new CdpClient(target.webSocketDebuggerUrl)
  await client.connect()
  const errors = []
  let scriptRequests = 0
  client.on('Runtime.exceptionThrown', (params) => {
    const details = params.exceptionDetails || {}
    errors.push([
      details.text || 'runtime exception',
      details.exception?.description,
      details.url ? `${details.url}:${Number(details.lineNumber || 0) + 1}:${Number(details.columnNumber || 0) + 1}` : ''
    ].filter(Boolean).join('\n'))
  })
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
  await client.send('Emulation.setDeviceMetricsOverride', {
    width: viewportWidth,
    height: viewportHeight,
    deviceScaleFactor: 1,
    mobile: false
  })
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
  const layoutChecks = await runLayoutChecks(client, routePath)
  const processNodePanelChecks = await runProcessNodePanelChecks(client, routePath)
  if (process.env.E2E_SCREENSHOT_PATH && !process.env.E2E_SCREENSHOT_NODE_ID) {
    const screenshot = await client.send('Page.captureScreenshot', {
      format: 'png',
      captureBeyondViewport: false
    })
    writeFileSync(process.env.E2E_SCREENSHOT_PATH, Buffer.from(screenshot.data, 'base64'))
  }
  const interactions = await runInteractionPlan(client, routePath)
  const formDesignerChecks = await runFormDesignerChecks(client, routePath)
  if (
    process.env.E2E_SCREENSHOT_PATH
    && process.env.E2E_SCREENSHOT_AFTER_INTERACTIONS === '1'
  ) {
    const screenshot = await client.send('Page.captureScreenshot', {
      format: 'png',
      captureBeyondViewport: false
    })
    writeFileSync(process.env.E2E_SCREENSHOT_PATH, Buffer.from(screenshot.data, 'base64'))
  }
  const result = await client.send('Runtime.evaluate', {
    expression: `(() => ({ path: location.pathname, text: (document.body.textContent || '').trim().slice(0, 1000), title: document.title, errorMessage: document.querySelector('.el-message--error')?.textContent || '', html: document.body.innerHTML.slice(0, 300), appHtml: document.querySelector('#app')?.innerHTML.slice(0, 300) || '' }))()`,
    returnByValue: true
  })
  client.close()
  return {
    routePath,
    ...result.result.value,
    scriptRequests,
    layoutChecks,
    formDesignerChecks,
    processNodePanelChecks,
    interactions,
    errors: errors.filter((error) => !String(error).includes('ResizeObserver'))
  }
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
  const requestedRoute = process.env.E2E_ROUTE?.trim()
  const routes = getRoutes().filter((routePath) =>
    !requestedRoute || routePath === requestedRoute)
  const results = []
  for (const routePath of routes) {
    results.push(await createPage(chrome, routePath))
  }
  const failures = results.filter((result) => {
    const expectedTexts = expectedRouteText.get(result.routePath) || []
    const missingTexts = expectedTexts.filter((text) => !result.text.includes(text))
    const unexpectedTexts = (unexpectedRouteText.get(result.routePath) || [])
      .filter((text) => result.text.includes(text))
    result.missingTexts = missingTexts
    result.unexpectedTexts = unexpectedTexts
    result.failedLayoutChecks = (result.layoutChecks || []).filter((check) => !check.passed)
    result.failedFormDesignerChecks = (result.formDesignerChecks || [])
      .filter((check) => !check.passed)
    result.failedProcessNodePanelChecks = (result.processNodePanelChecks || []).filter((check) => !check.passed)
    const failedInteractions = (result.interactions || []).filter((interaction) => !interaction.clicked || interaction.missingTexts.length > 0)
    result.failedInteractions = failedInteractions
    return result.errors.length > 0
      || !result.text
      || result.errorMessage
      || missingTexts.length > 0
      || unexpectedTexts.length > 0
      || result.failedLayoutChecks.length > 0
      || result.failedFormDesignerChecks.length > 0
      || result.failedProcessNodePanelChecks.length > 0
      || failedInteractions.length > 0
  })
  if (failures.length) {
    console.log(JSON.stringify(failures, null, 2))
  }
  assert.equal(failures.length, 0, 'mock page E2E found route failures')
  const interactionCount = results.reduce((sum, result) => sum + (result.interactions?.length || 0), 0)
  const layoutCheckCount = results.reduce((sum, result) => sum + (result.layoutChecks?.length || 0), 0)
  const formDesignerCheckCount = results.reduce((sum, result) =>
    sum + (result.formDesignerChecks?.length || 0), 0)
  const processNodePanelCheckCount = results.reduce((sum, result) => sum + (result.processNodePanelChecks?.length || 0), 0)
  console.log(`mock page e2e passed: ${results.length} routes, ${interactionCount} interactions, ${layoutCheckCount} layout checks, ${formDesignerCheckCount} form designer checks, ${processNodePanelCheckCount} process node panel checks`)
} finally {
  vite.kill('SIGTERM')
  chrome.kill('SIGTERM')
  await delay(500)
  try {
    rmSync(userDataDir, { recursive: true, force: true, maxRetries: 3, retryDelay: 200 })
  } catch {}
}
