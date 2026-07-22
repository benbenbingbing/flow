import assert from 'node:assert/strict'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const baseUrl = process.env.API_BASE || process.env.WORKFLOW_API_BASE || 'http://localhost:8080/api'
const stamp = new Date().toISOString().replace(/[-:TZ.]/g, '').slice(2, 12)
const suffix = `${stamp}${Math.random().toString(36).slice(2, 5)}`
const processKey = `cfg_${suffix}`
const entityCode = `cfg_entity_${suffix}`
const firstApprover = `cfg_l1_${suffix}`
const secondApprover = `cfg_l2_${suffix}`
const approverRoleCode = `cfg_approver_${suffix}`
const processName = `Codex配置闭环${suffix}`
const entityName = `Codex配置实体${suffix}`
const evidenceDir = path.resolve('docs/workflow-closure')
mkdirSync(evidenceDir, { recursive: true })

const tokens = {}
let currentUser = ''
const evidence = {
  baseUrl,
  processKey,
  entityCode,
  steps: []
}

async function api(method, url, body, username = currentUser) {
  const token = tokens[username]
  const response = await fetch(baseUrl + url, {
    method,
    signal: AbortSignal.timeout(15000),
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
    throw new Error(`${method} ${url} returned non-json: ${text.slice(0, 200)}`)
  }
  if (!response.ok || (json && json.code != null && json.code !== 200)) {
    throw new Error(`${method} ${url} failed: HTTP ${response.status}, body=${text.slice(0, 1000)}`)
  }
  return json?.data ?? json
}

async function login(username, password) {
  const result = await api('POST', '/auth/login', { username, password }, '')
  tokens[username] = result.token
  record(`login:${username}`, { id: result.id, username: result.username, nickname: result.nickname })
}

async function createApprover(username, nickname, roleId) {
  const user = await api('POST', '/system/user', {
    username,
    nickname,
    email: `${username}@example.test`,
    status: '0',
    roleIds: [roleId]
  })
  await api('PUT', `/system/user/${user.id}/reset-password`)
  record(`createApprover:${username}`, { id: user.id, username, nickname })
}

function useUser(username) {
  currentUser = username
}

function record(name, data) {
  evidence.steps.push({ name, data })
  return data
}

function toList(page) {
  if (Array.isArray(page)) return page
  return page?.records || page?.list || []
}

function findTodo(page, processInstanceId) {
  return toList(page).find(task => task.processInstanceId === processInstanceId)
}

function flattenTree(items) {
  return (items || []).flatMap(item => [
    item,
    ...flattenTree(item.children)
  ])
}

function bpmnXml() {
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_${processKey}" targetNamespace="http://workflow.codex/config-test">
  <process id="${processKey}" name="${processName}" isExecutable="true">
    <startEvent id="StartEvent_1" name="开始"><outgoing>Flow_1</outgoing></startEvent>
    <userTask id="Task_Level1_Review" name="一级审批" flowable:assignee="${firstApprover}"><incoming>Flow_1</incoming><outgoing>Flow_2</outgoing></userTask>
    <userTask id="Task_Level2_Review" name="二级审批" flowable:assignee="${secondApprover}"><incoming>Flow_2</incoming><outgoing>Flow_3</outgoing></userTask>
    <endEvent id="EndEvent_1" name="结束"><incoming>Flow_3</incoming></endEvent>
    <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_Level1_Review" />
    <sequenceFlow id="Flow_2" sourceRef="Task_Level1_Review" targetRef="Task_Level2_Review" />
    <sequenceFlow id="Flow_3" sourceRef="Task_Level2_Review" targetRef="EndEvent_1" />
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_${processKey}">
    <bpmndi:BPMNPlane id="BPMNPlane_${processKey}" bpmnElement="${processKey}">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1"><dc:Bounds x="60" y="100" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Level1_Review_di" bpmnElement="Task_Level1_Review"><dc:Bounds x="150" y="78" width="120" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Level2_Review_di" bpmnElement="Task_Level2_Review"><dc:Bounds x="330" y="78" width="120" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1"><dc:Bounds x="510" y="100" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1"><di:waypoint x="96" y="118" /><di:waypoint x="150" y="118" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2"><di:waypoint x="270" y="118" /><di:waypoint x="330" y="118" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3"><di:waypoint x="450" y="118" /><di:waypoint x="510" y="118" /></bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`
}

async function main() {
  await login('admin', 'admin')
  useUser('admin')
  const approverRole = await api('POST', '/system/role', {
    roleCode: approverRoleCode,
    roleName: `Codex配置审批角色${suffix}`,
    description: '真实流程配置闭环专用最小审批角色',
    status: '0',
    sort: 999
  })
  record('createApproverRole', {
    id: approverRole.id,
    roleCode: approverRole.roleCode
  })
  await createApprover(firstApprover, 'Codex一级审批人', approverRole.id)
  await createApprover(secondApprover, 'Codex二级审批人', approverRole.id)
  await login(firstApprover, '123456')
  await login(secondApprover, '123456')

  const process = await api('POST', '/process', {
    processKey,
    processName,
    description: 'Codex真实配置闭环：管理员发起，两个独立账号逐级审批',
    category: 'codex-config-test',
    bpmnXml: bpmnXml()
  })
  record('createProcess', { id: process.id, processKey: process.processKey, status: process.status })

  const published = await api('POST', `/process/${process.id}/publish`, {
    versionDescription: 'Codex真实配置闭环发布'
  })
  record('publishProcess', {
    id: published.id,
    status: published.status,
    version: published.version
  })
  assert.equal(published.status, 'PUBLISHED')

  const nodes = await api('GET', `/process/${process.id}/nodes`)
  record('publishedNodes', nodes)
  const level1Node = nodes.find(node => node.nodeId === 'Task_Level1_Review')
  const level2Node = nodes.find(node => node.nodeId === 'Task_Level2_Review')
  assert.ok(level1Node?.assignees?.some(item => item.assigneeValue === firstApprover))
  assert.ok(level2Node?.assignees?.some(item => item.assigneeValue === secondApprover))

  const entity = await api('POST', '/entity', {
    entityCode,
    entityName,
    description: 'Codex状态映射与跨用户审批真实测试实体',
    fields: [
      {
        fieldCode: 'amount',
        fieldName: '金额',
        fieldType: 'DECIMAL',
        isRequired: true,
        isUnique: false,
        editable: true,
        sortOrder: 10
      },
      {
        fieldCode: 'remark',
        fieldName: '备注',
        fieldType: 'TEXT',
        isRequired: false,
        isUnique: false,
        editable: true,
        sortOrder: 20
      }
    ]
  })
  record('createEntity', { id: entity.id, entityCode: entity.entityCode })

  await api('POST', `/entity/${entity.id}/publish`)
  const boundEntity = await api('PUT', `/entity/${entity.id}/workflow-binding`, {
    processDefinitionId: process.id
  })
  record('publishAndBindEntity', {
    entityId: boundEntity.id,
    processDefinitionId: boundEntity.processDefinitionId,
    lifecycleMode: boundEntity.lifecycleMode,
    workflowBindingStatus: boundEntity.workflowBindingStatus
  })

  const approvePermission = `entity:${entityCode}:approve`
  const menuTree = await api('GET', '/system/role/menu-tree')
  const approveMenu = flattenTree(menuTree).find(menu => menu.perm === approvePermission)
  assert.ok(approveMenu, `实体绑定后应生成审批权限 ${approvePermission}`)
  await api('PUT', `/system/role/${approverRole.id}/menus`, [approveMenu.id])
  record('grantApproverPermission', {
    roleId: approverRole.id,
    menuId: approveMenu.id,
    permission: approvePermission
  })

  await api('POST', `/entity-status/save-list/${entityCode}`, [
    {
      statusCode: 'NEW_DRAFT',
      statusName: '新建',
      statusCategory: 'NEW',
      color: '#909399',
      description: '新建默认状态'
    },
    {
      statusCode: 'IN_REVIEW',
      statusName: '审批中',
      statusCategory: 'PROCESSING',
      color: '#409eff',
      description: '流程发起默认状态'
    },
    {
      statusCode: 'LEVEL1_APPROVED',
      statusName: '初审通过',
      statusCategory: 'PROCESSING',
      color: '#e6a23c',
      description: '一级审批通过后的配置状态'
    },
    {
      statusCode: 'COMPLETED_DEFAULT',
      statusName: '默认完成',
      statusCategory: 'COMPLETED',
      color: '#67c23a',
      description: '流程结束监听器的默认完成状态'
    },
    {
      statusCode: 'FINAL_SPECIAL',
      statusName: '终审特别通过',
      statusCategory: 'COMPLETED',
      color: '#2f9e44',
      description: '最终连线显式配置的完成状态'
    }
  ])

  await api('PUT', `/process-entity-status-mappings/process/${process.id}`, {
    processKey,
    entityCode,
    mappings: [
      {
        sequenceFlowId: 'Flow_2',
        sourceNodeId: 'Task_Level1_Review',
        sourceNodeName: '一级审批',
        targetNodeId: 'Task_Level2_Review',
        targetNodeName: '二级审批',
        entityStatusCode: 'LEVEL1_APPROVED',
        statusCategory: 'PROCESSING',
        sortOrder: 0,
        description: '初审通过后更新状态'
      },
      {
        sequenceFlowId: 'Flow_3',
        sourceNodeId: 'Task_Level2_Review',
        sourceNodeName: '二级审批',
        targetNodeId: 'EndEvent_1',
        targetNodeName: '结束',
        entityStatusCode: 'FINAL_SPECIAL',
        statusCategory: 'COMPLETED',
        sortOrder: 1,
        description: '终审通过后更新最终状态'
      }
    ]
  })

  const mappings = await api('GET', `/process-entity-status-mappings/process/${process.id}`)
  record('savedStatusMappings', mappings)
  assert.equal(mappings.length, 2)
  assert.equal(mappings[0].entityStatus, mappings[0].entityStatusCode)
  assert.equal(mappings[1].entityStatus, mappings[1].entityStatusCode)

  const dataName = `Codex配置数据${suffix}`
  const saved = await api('POST', '/entity-data', {
    entityCode,
    name: dataName,
    title: dataName,
    data: {
      name: dataName,
      amount: 456.78,
      remark: '管理员发起，两个独立账号逐级审批'
    },
    startProcess: true
  })
  record('startProcess', {
    id: saved.id,
    status: saved.status,
    processInstanceId: saved.processInstanceId,
    currentTaskName: saved.currentTaskName
  })
  assert.equal(saved.status, 'IN_REVIEW')
  assert.equal(saved.currentTaskName, '一级审批')

  useUser(firstApprover)
  const level1Todos = await api('GET', '/process-task/todo?pageNum=1&pageSize=100')
  const level1Todo = findTodo(level1Todos, saved.processInstanceId)
  record('level1Todo', level1Todo)
  assert.ok(level1Todo, '一级审批人应收到待办')
  assert.equal(level1Todo.taskName, '一级审批')

  await api('POST', '/process-task/complete', {
    taskId: level1Todo.taskId,
    action: 'approve',
    actionLabel: '初审通过',
    comment: '一级审批人完成真实审批'
  })

  useUser('admin')
  const afterLevel1 = await api('GET', `/entity-data/entity/${entityCode}/detail/${saved.id}`)
  record('entityAfterLevel1Approval', {
    status: afterLevel1.status,
    currentTaskName: afterLevel1.currentTaskName,
    currentTaskAssignee: afterLevel1.currentTaskAssignee
  })
  assert.equal(afterLevel1.status, 'LEVEL1_APPROVED')
  assert.equal(afterLevel1.currentTaskName, '二级审批')

  useUser(secondApprover)
  const level2Todos = await api('GET', '/process-task/todo?pageNum=1&pageSize=100')
  const level2Todo = findTodo(level2Todos, saved.processInstanceId)
  record('level2Todo', level2Todo)
  assert.ok(level2Todo, '二级审批人应收到待办')
  assert.equal(level2Todo.taskName, '二级审批')

  await api('POST', '/process-task/complete', {
    taskId: level2Todo.taskId,
    action: 'approve',
    actionLabel: '终审特别通过',
    comment: '二级审批人完成真实审批'
  })

  useUser('admin')
  const progress = await api('GET', `/process-instance/${saved.processInstanceId}/progress`)
  record('completedProgress', {
    status: progress.status,
    completedNodes: progress.completedNodes,
    nodeHistory: progress.nodeHistory?.map(item => ({
      nodeId: item.nodeId,
      nodeName: item.nodeName,
      action: item.action,
      actionLabel: item.actionLabel,
      comment: item.comment,
      assignee: item.assignee
    }))
  })
  assert.equal(progress.status, 'COMPLETED')
  assert.ok(progress.completedNodes?.includes('Task_Level1_Review'))
  assert.ok(progress.completedNodes?.includes('Task_Level2_Review'))

  const completed = await api('GET', `/entity-data/entity/${entityCode}/detail/${saved.id}`)
  record('entityAfterFinalApproval', {
    status: completed.status,
    currentTaskId: completed.currentTaskId,
    currentTaskName: completed.currentTaskName,
    processEndTime: completed.processEndTime
  })
  assert.equal(completed.status, 'FINAL_SPECIAL', '最终连线配置的状态应保留，不应被默认完成状态覆盖')
  assert.equal(completed.currentTaskId, null)
  assert.equal(completed.currentTaskName, null)
  assert.ok(completed.processEndTime)

  const evidencePath = path.join(evidenceDir, `config-closure-${suffix}.json`)
  evidence.conclusion = 'PASS: BPMN执行人、实体绑定、状态字典、节点状态映射、跨用户待办和审批历史全部按配置生效'
  writeFileSync(evidencePath, JSON.stringify(evidence, null, 2))
  console.log(`real workflow config closure passed: ${evidencePath}`)
}

main().catch(error => {
  evidence.error = error.stack || String(error)
  const evidencePath = path.join(evidenceDir, `config-closure-${suffix}-failed.json`)
  writeFileSync(evidencePath, JSON.stringify(evidence, null, 2))
  console.error(error)
  console.error(`evidence written: ${evidencePath}`)
  process.exit(1)
})
