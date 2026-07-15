import assert from 'node:assert/strict'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const apiBase = 'http://localhost:8080/api'
const entityCode = 'codex_flow_e2e'
const processKey = 'codex-flow-e2e'
const outputDir = path.resolve('docs/real-workflow-e2e')
const runId = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)

mkdirSync(outputDir, { recursive: true })

let token = ''

async function request(endpoint, options = {}) {
  const response = await fetch(`${apiBase}${endpoint}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  })
  const payload = await response.json()
  if (!response.ok || payload.code !== 200) {
    const error = new Error(payload.message || `${response.status} ${response.statusText}`)
    error.payload = payload
    throw error
  }
  return payload.data
}

async function findOrNull(endpoint) {
  try {
    return await request(endpoint)
  } catch {
    return null
  }
}

async function waitFor(check, message, timeout = 10000) {
  const startedAt = Date.now()
  let lastValue
  while (Date.now() - startedAt < timeout) {
    lastValue = await check()
    if (lastValue) return lastValue
    await new Promise(resolve => setTimeout(resolve, 300))
  }
  throw new Error(`${message}: ${JSON.stringify(lastValue)}`)
}

function buildBpmnXml() {
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:flowable="http://flowable.org/bpmn"
  id="Definitions_CodexFlowE2E"
  targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="${processKey}" name="Codex真实流程闭环" isExecutable="true">
    <bpmn:startEvent id="StartEvent_Config" name="提交申请">
      <bpmn:outgoing>Flow_Start_Review</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_ConfigReview" name="配置校验审批" flowable:assignee="admin">
      <bpmn:incoming>Flow_Start_Review</bpmn:incoming>
      <bpmn:outgoing>Flow_Review_Finance</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:userTask id="Task_ConfigFinance" name="财务复核审批" flowable:assignee="admin">
      <bpmn:incoming>Flow_Review_Finance</bpmn:incoming>
      <bpmn:outgoing>Flow_Finance_End</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:endEvent id="EndEvent_Config" name="审批完成">
      <bpmn:incoming>Flow_Finance_End</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_Start_Review" sourceRef="StartEvent_Config" targetRef="Task_ConfigReview">
      <bpmn:extensionElements>
        <flowable:properties>
          <flowable:property name="entityStatusCode" value="IN_REVIEW" />
        </flowable:properties>
      </bpmn:extensionElements>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_Review_Finance" sourceRef="Task_ConfigReview" targetRef="Task_ConfigFinance">
      <bpmn:extensionElements>
        <flowable:properties>
          <flowable:property name="entityStatusCode" value="FINANCE_REVIEW" />
        </flowable:properties>
      </bpmn:extensionElements>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_Finance_End" sourceRef="Task_ConfigFinance" targetRef="EndEvent_Config">
      <bpmn:extensionElements>
        <flowable:properties>
          <flowable:property name="entityStatusCode" value="APPROVED" />
        </flowable:properties>
      </bpmn:extensionElements>
    </bpmn:sequenceFlow>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_CodexFlowE2E">
    <bpmndi:BPMNPlane id="BPMNPlane_CodexFlowE2E" bpmnElement="${processKey}">
      <bpmndi:BPMNShape id="StartEvent_Config_di" bpmnElement="StartEvent_Config">
        <dc:Bounds x="80" y="122" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_ConfigReview_di" bpmnElement="Task_ConfigReview">
        <dc:Bounds x="180" y="100" width="120" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_ConfigFinance_di" bpmnElement="Task_ConfigFinance">
        <dc:Bounds x="370" y="100" width="120" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_Config_di" bpmnElement="EndEvent_Config">
        <dc:Bounds x="560" y="122" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_Start_Review_di" bpmnElement="Flow_Start_Review">
        <di:waypoint x="116" y="140" />
        <di:waypoint x="180" y="140" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Review_Finance_di" bpmnElement="Flow_Review_Finance">
        <di:waypoint x="300" y="140" />
        <di:waypoint x="370" y="140" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Finance_End_di" bpmnElement="Flow_Finance_End">
        <di:waypoint x="490" y="140" />
        <di:waypoint x="560" y="140" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
}

const login = await request('/auth/login', {
  method: 'POST',
  body: JSON.stringify({ username: 'admin', password: 'admin' })
})
token = login.token

let entity = await findOrNull(`/entity/code/${entityCode}`)
if (!entity) {
  entity = await request('/entity', {
    method: 'POST',
    body: JSON.stringify({
      entityCode,
      entityName: 'Codex流程闭环测试实体',
      description: '用于真实流程配置和数据流转验收',
      fields: [
        {
          fieldCode: 'amount',
          fieldName: '申请金额',
          fieldType: 'DECIMAL',
          isRequired: true,
          isUnique: false,
          sortOrder: 1
        },
        {
          fieldCode: 'remark',
          fieldName: '申请说明',
          fieldType: 'TEXT',
          isRequired: false,
          isUnique: false,
          sortOrder: 2
        }
      ]
    })
  })
}
if (entity.status !== 'PUBLISHED') {
  entity = await request(`/entity/${entity.id}/publish`, { method: 'POST' })
}

await request(`/entity-status/save-list/${entityCode}`, {
  method: 'POST',
  body: JSON.stringify([
    { statusCode: 'DRAFT', statusName: '草稿', statusCategory: 'NEW', color: '#909399' },
    { statusCode: 'IN_REVIEW', statusName: '配置校验中', statusCategory: 'PROCESSING', color: '#409eff' },
    { statusCode: 'FINANCE_REVIEW', statusName: '财务复核中', statusCategory: 'PROCESSING', color: '#e6a23c' },
    { statusCode: 'APPROVED', statusName: '已审批', statusCategory: 'COMPLETED', color: '#67c23a' },
    { statusCode: 'TERMINATED', statusName: '已终止', statusCategory: 'TERMINATED', color: '#f56c6c' }
  ])
})

const bpmnXml = buildBpmnXml()
let process = await findOrNull(`/process/key/${processKey}`)
if (!process) {
  process = await request('/process', {
    method: 'POST',
    body: JSON.stringify({
      processKey,
      processName: 'Codex真实流程闭环',
      description: '验证节点名称、审批人、状态映射和流程历史',
      category: 'E2E',
      bpmnXml
    })
  })
} else {
  process = await request(`/process/${process.id}`, {
    method: 'PUT',
    body: JSON.stringify({
      ...process,
      processName: 'Codex真实流程闭环',
      description: '验证节点名称、审批人、状态映射和流程历史',
      category: 'E2E',
      bpmnXml
    })
  })
}

entity = await request(`/entity/${entity.id}/bind-process/${process.id}`, { method: 'POST' })
process = await request(`/process/${process.id}/publish`, {
  method: 'POST',
  body: JSON.stringify({ versionDescription: `真实流程闭环 ${runId}` })
})

const nodes = await request(`/process/${process.id}/nodes`)
const reviewNode = nodes.find(node => node.nodeId === 'Task_ConfigReview')
const financeNode = nodes.find(node => node.nodeId === 'Task_ConfigFinance')
assert.equal(reviewNode?.nodeName, '配置校验审批')
assert.equal(reviewNode?.assignees?.[0]?.assigneeValue, 'admin')
assert.equal(financeNode?.nodeName, '财务复核审批')
assert.equal(financeNode?.assignees?.[0]?.assigneeValue, 'admin')

const mappings = await request(`/entity-flow-status/list/${process.id}`)
assert.equal(mappings.find(item => item.sequenceFlowId === 'Flow_Review_Finance')?.entityStatusCode, 'FINANCE_REVIEW')
assert.equal(mappings.find(item => item.sequenceFlowId === 'Flow_Finance_End')?.entityStatusCode, 'APPROVED')

const dataName = `Codex流程闭环-${runId}`
const created = await request('/entity-data', {
  method: 'POST',
  body: JSON.stringify({
    entityCode,
    name: dataName,
    data: {
      name: dataName,
      amount: 1288.66,
      remark: '验证流程配置是否真实进入运行态'
    },
    startProcess: true
  })
})

assert.ok(created.id)
assert.ok(created.processInstanceId)
assert.equal(created.currentTaskName, '配置校验审批')
assert.equal(created.currentTaskAssignee, 'admin')
assert.equal(created.status, 'IN_REVIEW')

const firstTask = await waitFor(async () => {
  const todo = await request('/process-task/todo?pageNum=1&pageSize=1000')
  return todo.records.find(item => item.processInstanceId === created.processInstanceId)
}, '未生成第一节点待办')

assert.equal(firstTask.taskName, '配置校验审批')
assert.equal(firstTask.assignee, 'admin')

await request('/process-task/complete', {
  method: 'POST',
  body: JSON.stringify({
    taskId: firstTask.taskId,
    action: 'approve',
    actionLabel: '通过',
    comment: '配置校验审批通过'
  })
})

const afterFirstApproval = await waitFor(async () => {
  const detail = await request(`/entity-data/entity/${entityCode}/detail/${created.id}`)
  return detail.currentTaskName === '财务复核审批' ? detail : null
}, '流程未进入第二节点')

assert.equal(afterFirstApproval.currentTaskAssignee, 'admin')
assert.equal(afterFirstApproval.status, 'FINANCE_REVIEW')

const secondTask = await waitFor(async () => {
  const todo = await request('/process-task/todo?pageNum=1&pageSize=1000')
  return todo.records.find(item =>
    item.processInstanceId === created.processInstanceId &&
    item.taskName === '财务复核审批'
  )
}, '未生成第二节点待办')

await request('/process-task/complete', {
  method: 'POST',
  body: JSON.stringify({
    taskId: secondTask.taskId,
    action: 'approve',
    actionLabel: '通过',
    comment: '财务复核审批通过'
  })
})

const completed = await waitFor(async () => {
  const detail = await request(`/entity-data/entity/${entityCode}/detail/${created.id}`)
  return detail.processEndTime ? detail : null
}, '流程未完成')

assert.equal(completed.status, 'APPROVED')
assert.equal(completed.currentTaskId, null)
assert.equal(completed.currentTaskName, null)
assert.equal(completed.currentTaskAssignee, null)

const progress = await request(`/process-instance/${created.processInstanceId}/progress`)
assert.equal(progress.status, 'COMPLETED')
assert.ok(progress.completedNodes.includes('Task_ConfigReview'))
assert.ok(progress.completedNodes.includes('Task_ConfigFinance'))

const history = await request(`/process-task/history/${created.processInstanceId}`)
assert.ok(history.some(item => item.taskName === '配置校验审批' && item.comment === '配置校验审批通过'))
assert.ok(history.some(item => item.taskName === '财务复核审批' && item.comment === '财务复核审批通过'))

const result = {
  runId,
  process: {
    id: process.id,
    processKey,
    version: process.version,
    configuredNodes: nodes,
    statusMappings: mappings
  },
  entity: {
    id: entity.id,
    entityCode,
    dataId: created.id,
    dataName
  },
  runtime: {
    processInstanceId: created.processInstanceId,
    initial: {
      taskName: created.currentTaskName,
      assignee: created.currentTaskAssignee,
      status: created.status
    },
    afterFirstApproval: {
      taskName: afterFirstApproval.currentTaskName,
      assignee: afterFirstApproval.currentTaskAssignee,
      status: afterFirstApproval.status
    },
    completed: {
      status: completed.status,
      processEndTime: completed.processEndTime
    },
    history,
    progress
  }
}

const resultFile = path.join(outputDir, `workflow-e2e-${runId}.json`)
writeFileSync(resultFile, JSON.stringify(result, null, 2))
writeFileSync(path.join(outputDir, 'latest.json'), JSON.stringify(result, null, 2))

console.log(`real workflow e2e passed: ${resultFile}`)
