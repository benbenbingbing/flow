import assert from 'node:assert/strict'
import { writeFileSync, mkdirSync } from 'node:fs'
import path from 'node:path'

const baseUrl = process.env.WORKFLOW_API_BASE || 'http://localhost:8080/api'
const stamp = new Date().toISOString().replace(/[-:TZ.]/g, '').slice(2, 12)
const suffix = `${stamp}${Math.random().toString(36).slice(2, 5)}`
const processKey = `cf_${suffix}`
const entityCode = `ce_${suffix}`
const processName = `Codex流程闭环${suffix}`
const entityName = `Codex实体闭环${suffix}`
const evidenceDir = path.resolve('docs/workflow-closure')
mkdirSync(evidenceDir, { recursive: true })

let token = ''
const evidence = {
  baseUrl,
  processKey,
  entityCode,
  steps: []
}

async function api(method, url, body) {
  const res = await fetch(baseUrl + url, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body == null ? undefined : JSON.stringify(body)
  })
  const text = await res.text()
  let json
  try { json = text ? JSON.parse(text) : null } catch { throw new Error(`${method} ${url} returned non-json: ${text.slice(0, 200)}`) }
  if (!res.ok || (json && json.code != null && json.code !== 200)) {
    throw new Error(`${method} ${url} failed: HTTP ${res.status}, body=${text.slice(0, 1000)}`)
  }
  return json?.data ?? json
}

function record(name, data) {
  evidence.steps.push({ name, data })
  return data
}

function bpmnXml() {
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_${processKey}" targetNamespace="http://workflow.codex/test">
  <process id="${processKey}" name="${processName}" isExecutable="true">
    <startEvent id="StartEvent_1" name="开始"><outgoing>Flow_1</outgoing></startEvent>
    <userTask id="Task_Admin_Review" name="Codex管理员审批" flowable:candidateUsers="admin"><incoming>Flow_1</incoming><outgoing>Flow_2</outgoing></userTask>
    <endEvent id="EndEvent_1" name="结束"><incoming>Flow_2</incoming></endEvent>
    <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_Admin_Review" />
    <sequenceFlow id="Flow_2" sourceRef="Task_Admin_Review" targetRef="EndEvent_1" />
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_${processKey}">
    <bpmndi:BPMNPlane id="BPMNPlane_${processKey}" bpmnElement="${processKey}">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1"><dc:Bounds x="80" y="100" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Admin_Review_di" bpmnElement="Task_Admin_Review"><dc:Bounds x="180" y="78" width="120" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1"><dc:Bounds x="380" y="100" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1"><di:waypoint x="116" y="118" /><di:waypoint x="180" y="118" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2"><di:waypoint x="300" y="118" /><di:waypoint x="380" y="118" /></bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`
}

async function main() {
  const login = await api('POST', '/auth/login', { username: 'admin', password: 'admin' })
  token = login.token
  record('login', { user: login.username, id: login.id })

  const process = await api('POST', '/process', {
    processKey,
    processName,
    description: 'Codex真实闭环测试流程：开始 -> 管理员审批 -> 结束',
    category: 'codex-test',
    bpmnXml: bpmnXml()
  })
  record('createProcess', { id: process.id, processKey: process.processKey, status: process.status })

  const published = await api('POST', `/process/${process.id}/publish`, { versionDescription: 'Codex真实闭环测试发布' })
  record('publishProcess', { id: published.id, processKey: published.processKey, status: published.status, version: published.version })
  assert.equal(published.status, 'PUBLISHED')

  const nodes = await api('GET', `/process/${process.id}/nodes`)
  record('publishedNodes', nodes)
  const reviewNode = nodes.find(n => n.nodeId === 'Task_Admin_Review')
  assert.ok(reviewNode, '发布后应解析出审批节点')
  assert.equal(reviewNode.nodeName, 'Codex管理员审批')
  assert.ok(reviewNode.assignees?.some(a => a.assigneeValue === 'admin'), '审批节点候选人配置应生效为 admin')

  const entity = await api('POST', '/entity', {
    entityCode,
    entityName,
    description: 'Codex真实流程数据闭环测试实体',
    fields: [
      { fieldCode: 'amount', fieldName: '金额', fieldType: 'DECIMAL', isRequired: true, isUnique: false, editable: true, sortOrder: 10 },
      { fieldCode: 'remark', fieldName: '备注', fieldType: 'TEXT', isRequired: false, isUnique: false, editable: true, sortOrder: 20 }
    ]
  })
  record('createEntity', { id: entity.id, entityCode: entity.entityCode, status: entity.status })

  const entityPublished = await api('POST', `/entity/${entity.id}/publish`)
  record('publishEntity', { id: entityPublished.id, status: entityPublished.status })
  assert.equal(entityPublished.status, 'PUBLISHED')

  const boundEntity = await api('PUT', `/entity/${entity.id}/workflow-binding`, {
    processDefinitionId: process.id
  })
  record('bindProcess', {
    entityId: boundEntity.id,
    processDefinitionId: boundEntity.processDefinitionId,
    processName: boundEntity.processName,
    lifecycleMode: boundEntity.lifecycleMode,
    workflowBindingStatus: boundEntity.workflowBindingStatus
  })
  assert.equal(boundEntity.processDefinitionId, process.id)
  assert.equal(boundEntity.lifecycleMode, 'WORKFLOW')

  const dataName = `Codex闭环数据${suffix}`
  const saved = await api('POST', '/entity-data', {
    entityCode,
    name: dataName,
    title: dataName,
    data: { name: dataName, amount: 123.45, remark: '提交后应自动进入 Codex管理员审批' },
    startProcess: true
  })
  record('submitEntityDataStartProcess', {
    id: saved.id,
    entityCode: saved.entityCode,
    name: saved.name,
    status: saved.status,
    processInstanceId: saved.processInstanceId,
    currentTaskId: saved.currentTaskId,
    currentTaskName: saved.currentTaskName,
    currentTaskAssignee: saved.currentTaskAssignee
  })
  assert.ok(saved.id, '应创建实体数据')
  assert.ok(saved.processInstanceId, 'startProcess=true 应发起流程实例')
  assert.equal(saved.currentTaskName, 'Codex管理员审批')

  const detail = await api('GET', `/entity-data/entity/${entityCode}/detail/${saved.id}`)
  record('entityDataDetailAfterStart', {
    id: detail.id,
    status: detail.status,
    processInstanceId: detail.processInstanceId,
    currentTaskId: detail.currentTaskId,
    currentTaskName: detail.currentTaskName,
    currentTaskAssignee: detail.currentTaskAssignee
  })
  assert.equal(detail.processInstanceId, saved.processInstanceId)
  assert.equal(detail.currentTaskName, 'Codex管理员审批')

  const todos = await api('GET', '/process-task/todo?pageNum=1&pageSize=100')
  const todoList = Array.isArray(todos) ? todos : (todos.records || todos.list || [])
  const todo = todoList.find(t => t.processInstanceId === saved.processInstanceId || t.businessKey === saved.id)
  record('todoAfterStart', todo)
  assert.ok(todo, '发起后应生成当前登录用户 admin 的待办')
  assert.equal(todo.taskName, 'Codex管理员审批')
  assert.equal(todo.entityCode, entityCode)

  const progress = await api('GET', `/process-instance/${saved.processInstanceId}/progress`)
  record('progressAfterStart', {
    processName: progress.processName,
    status: progress.status,
    activeNodes: progress.activeNodes,
    completedNodes: progress.completedNodes,
    entityData: progress.entityData && {
      id: progress.entityData.id,
      status: progress.entityData.status,
      currentTaskName: progress.entityData.currentTaskName,
      processInstanceId: progress.entityData.processInstanceId
    }
  })
  assert.ok(progress.activeNodes?.includes('Task_Admin_Review'), '流程进度应停留在配置的审批节点')

  await api('POST', '/process-task/complete', {
    taskId: todo.taskId,
    action: 'approve',
    actionLabel: '通过',
    comment: 'Codex闭环测试审批通过'
  })
  record('completeTask', { taskId: todo.taskId, action: 'approve' })

  const progressDone = await api('GET', `/process-instance/${saved.processInstanceId}/progress`)
  record('progressAfterApprove', {
    processName: progressDone.processName,
    status: progressDone.status,
    activeNodes: progressDone.activeNodes,
    completedNodes: progressDone.completedNodes,
    executedSequenceFlows: progressDone.executedSequenceFlows,
    nodeHistory: progressDone.nodeHistory?.map(h => ({ nodeId: h.nodeId, nodeName: h.nodeName, action: h.action, actionLabel: h.actionLabel, comment: h.comment, status: h.status }))
  })
  assert.ok(['COMPLETED', 'ENDED'].includes(progressDone.status), `审批后流程应结束，实际 ${progressDone.status}`)
  assert.ok(progressDone.completedNodes?.includes('Task_Admin_Review'), '审批节点应进入已完成节点')

  const detailDone = await api('GET', `/entity-data/entity/${entityCode}/detail/${saved.id}`)
  record('entityDataDetailAfterApprove', {
    id: detailDone.id,
    status: detailDone.status,
    processInstanceId: detailDone.processInstanceId,
    currentTaskId: detailDone.currentTaskId,
    currentTaskName: detailDone.currentTaskName,
    currentTaskAssignee: detailDone.currentTaskAssignee,
    processEndTime: detailDone.processEndTime
  })
  assert.equal(detailDone.processInstanceId, saved.processInstanceId)

  const evidencePath = path.join(evidenceDir, `closure-${suffix}.json`)
  evidence.conclusion = 'PASS: 流程配置、实体绑定、业务数据发起、待办生成、审批完成均按配置生效'
  writeFileSync(evidencePath, JSON.stringify(evidence, null, 2))
  console.log(`real workflow closure passed: ${evidencePath}`)
}

main().catch(error => {
  evidence.error = error.stack || String(error)
  const evidencePath = path.join(evidenceDir, `closure-${suffix}-failed.json`)
  writeFileSync(evidencePath, JSON.stringify(evidence, null, 2))
  console.error(error)
  console.error(`evidence written: ${evidencePath}`)
  process.exit(1)
})
