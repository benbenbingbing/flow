import assert from 'node:assert/strict'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const baseUrl = process.env.WORKFLOW_API_BASE || 'http://localhost:8080/api'
const stopAt = process.env.WORKFLOW_FORM_STOP_AT || ''
const stamp = new Date().toISOString().replace(/[-:TZ.]/g, '').slice(2, 12)
const suffix = `${stamp}${Math.random().toString(36).slice(2, 5)}`
const processKey = `form_matrix_${suffix}`
const entityCode = `form_entity_${suffix}`
const processName = `节点表单矩阵${suffix}`
const entityName = `节点表单实体${suffix}`
const evidenceDir = path.resolve('docs/node-form-matrix')
mkdirSync(evidenceDir, { recursive: true })

let token = ''
const evidence = {
  baseUrl,
  processKey,
  processName,
  entityCode,
  entityName,
  stopAt,
  steps: []
}

async function api(method, url, body) {
  const response = await fetch(baseUrl + url, {
    method,
    signal: AbortSignal.timeout(20000),
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
    throw new Error(`${method} ${url} returned non-json: ${text.slice(0, 300)}`)
  }
  if (!response.ok || (json?.code != null && ![0, 200].includes(Number(json.code)))) {
    throw new Error(`${method} ${url} failed: HTTP ${response.status}, body=${text.slice(0, 1200)}`)
  }
  return json?.data ?? json
}

function record(name, data) {
  evidence.steps.push({ name, data })
  return data
}

function toList(page) {
  if (Array.isArray(page)) return page
  return page?.records || page?.list || page?.rows || []
}

function fieldFlag(progress, fieldCode) {
  return progress.formConfig?.fields?.find(field => field.fieldCode === fieldCode)?.isReadonly
}

function assertReadonlyFlag(actual, expected, message) {
  assert.equal(Number(actual), expected, message)
}

function formField(entityFields, fieldCode, overrides = {}) {
  const entityField = entityFields.find(field => field.fieldCode === fieldCode)
  assert.ok(entityField?.id, `实体字段 ${fieldCode} 应存在`)
  return {
    fieldId: entityField.id,
    fieldCode,
    fieldName: entityField.fieldName,
    fieldLabel: entityField.fieldName,
    fieldType: entityField.fieldType,
    componentType: entityField.fieldType === 'DECIMAL' ? 'number' : 'input',
    isRequired: 0,
    isReadonly: 0,
    isHidden: 0,
    gridSpan: 24,
    ...overrides
  }
}

function bpmnXml(forms) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_${processKey}" targetNamespace="http://workflow.codex/node-form-matrix">
  <process id="${processKey}" name="${processName}" isExecutable="true">
    <startEvent id="StartEvent_1" name="开始"><outgoing>Flow_1</outgoing></startEvent>
    <userTask id="Task_Form_A" name="首节点混合读写" flowable:candidateUsers="admin">
      <extensionElements><flowable:properties>
        <flowable:property name="entityFormId" value="${forms.formA}" />
        <flowable:property name="entityFormReadonly" value="false" />
      </flowable:properties></extensionElements>
      <incoming>Flow_1</incoming><outgoing>Flow_2</outgoing>
    </userTask>
    <userTask id="Task_Default_Fallback" name="未配置表单回退" flowable:candidateUsers="admin">
      <incoming>Flow_2</incoming><outgoing>Flow_3</outgoing>
    </userTask>
    <userTask id="Task_Form_C_Readonly" name="末节点专属全只读" flowable:candidateUsers="admin">
      <extensionElements><flowable:properties>
        <flowable:property name="entityFormId" value="${forms.formC}" />
        <flowable:property name="entityFormReadonly" value="true" />
      </flowable:properties></extensionElements>
      <incoming>Flow_3</incoming><outgoing>Flow_4</outgoing>
    </userTask>
    <endEvent id="EndEvent_1" name="结束"><incoming>Flow_4</incoming></endEvent>
    <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_Form_A" />
    <sequenceFlow id="Flow_2" sourceRef="Task_Form_A" targetRef="Task_Default_Fallback" />
    <sequenceFlow id="Flow_3" sourceRef="Task_Default_Fallback" targetRef="Task_Form_C_Readonly" />
    <sequenceFlow id="Flow_4" sourceRef="Task_Form_C_Readonly" targetRef="EndEvent_1" />
  </process>
  <bpmndi:BPMNDiagram id="Diagram_${processKey}">
    <bpmndi:BPMNPlane id="Plane_${processKey}" bpmnElement="${processKey}">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1"><dc:Bounds x="40" y="100" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Form_A_di" bpmnElement="Task_Form_A"><dc:Bounds x="120" y="78" width="130" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Default_Fallback_di" bpmnElement="Task_Default_Fallback"><dc:Bounds x="300" y="78" width="130" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Form_C_Readonly_di" bpmnElement="Task_Form_C_Readonly"><dc:Bounds x="480" y="78" width="130" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1"><dc:Bounds x="660" y="100" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1"><di:waypoint x="76" y="118" /><di:waypoint x="120" y="118" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2"><di:waypoint x="250" y="118" /><di:waypoint x="300" y="118" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_3_di" bpmnElement="Flow_3"><di:waypoint x="430" y="118" /><di:waypoint x="480" y="118" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_4_di" bpmnElement="Flow_4"><di:waypoint x="610" y="118" /><di:waypoint x="660" y="118" /></bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`
}

async function currentTodo(processInstanceId) {
  const page = await api('GET', '/process-task/todo?pageNum=1&pageSize=500')
  return toList(page).find(task => task.processInstanceId === processInstanceId)
}

function writeEvidence(result) {
  const name = `node-form-matrix-${suffix}${result === 'PASS' ? '' : '-failed'}.json`
  const evidencePath = path.join(evidenceDir, name)
  writeFileSync(evidencePath, JSON.stringify(evidence, null, 2))
  const latestName = stopAt ? 'latest-fixture.json' : 'latest.json'
  writeFileSync(path.join(evidenceDir, latestName), JSON.stringify(evidence, null, 2))
  return evidencePath
}

async function main() {
  const login = await api('POST', '/auth/login', { username: 'admin', password: 'admin' })
  token = login.token
  record('login', { username: login.username, id: login.id })

  const entity = await api('POST', '/entity', {
    entityCode,
    entityName,
    description: '节点不同表单、字段级读写、默认回退真实验收',
    fields: [
      { fieldCode: 'amount', fieldName: '可编辑金额', fieldType: 'DECIMAL', isRequired: false, isUnique: false, editable: true, sortOrder: 10 },
      { fieldCode: 'lockedNote', fieldName: '锁定说明', fieldType: 'STRING', isRequired: false, isUnique: false, editable: true, sortOrder: 20 },
      { fieldCode: 'defaultMemo', fieldName: '默认表单备注', fieldType: 'STRING', isRequired: false, isUnique: false, editable: true, sortOrder: 30 },
      { fieldCode: 'finalNote', fieldName: '末节点说明', fieldType: 'STRING', isRequired: false, isUnique: false, editable: true, sortOrder: 40 }
    ]
  })
  await api('POST', `/entity/${entity.id}/publish`)
  const entityDetail = await api('GET', `/entity/${entity.id}`)
  const entityFields = entityDetail.fields || entity.fields || []
  record('createAndPublishEntity', { id: entity.id, fields: entityFields.map(field => ({ id: field.id, code: field.fieldCode })) })

  const defaultForm = await api('POST', '/entity-form', {
    entityId: entity.id,
    formName: '默认回退表单',
    formKey: `default_${suffix}`,
    layoutType: 'vertical',
    isDefault: true,
    status: 1,
    fields: [
      formField(entityFields, 'defaultMemo'),
      formField(entityFields, 'lockedNote', { isReadonly: 1 })
    ]
  })
  const formA = await api('POST', '/entity-form', {
    entityId: entity.id,
    formName: '首节点混合读写表单',
    formKey: `first_${suffix}`,
    layoutType: 'vertical',
    isDefault: false,
    status: 1,
    fields: [
      formField(entityFields, 'amount'),
      formField(entityFields, 'lockedNote', { isReadonly: 1 })
    ]
  })
  const formC = await api('POST', '/entity-form', {
    entityId: entity.id,
    formName: '末节点专属表单',
    formKey: `final_${suffix}`,
    layoutType: 'vertical',
    isDefault: false,
    status: 1,
    fields: [
      formField(entityFields, 'finalNote'),
      formField(entityFields, 'amount')
    ]
  })
  record('createForms', {
    defaultForm: { id: defaultForm.id, name: defaultForm.formName },
    formA: { id: formA.id, name: formA.formName },
    formC: { id: formC.id, name: formC.formName }
  })

  const process = await api('POST', '/process', {
    processKey,
    processName,
    description: '首节点指定表单 -> 未配置回退默认 -> 末节点指定全只读表单',
    category: 'codex-node-form-test',
    bpmnXml: bpmnXml({ formA: formA.id, formC: formC.id })
  })
  await api('POST', `/process/${process.id}/publish`, {
    versionDescription: '节点表单矩阵发布'
  })
  await api('PUT', `/entity/${entity.id}/workflow-binding`, {
    processDefinitionId: process.id
  })
  record('createPublishBindProcess', { processId: process.id, processKey })

  const bindings = await api('GET', `/process-node-form/process/${process.id}`)
  record('publishedNodeFormBindings', bindings.map(binding => ({
    nodeId: binding.nodeId,
    formId: binding.formId,
    isReadonly: binding.isReadonly,
    sortOrder: binding.sortOrder
  })))
  assert.ok(bindings.some(binding => binding.nodeId === 'Task_Form_A' && binding.formId === formA.id))
  assert.ok(!bindings.some(binding => binding.nodeId === 'Task_Default_Fallback'))
  assert.ok(bindings.some(binding => binding.nodeId === 'Task_Form_C_Readonly' && binding.formId === formC.id && Number(binding.isReadonly) === 1))

  const newDataForm = await api('GET', `/entity-form-resolve/new-data/${entityCode}`)
  record('resolveNewDataForm', {
    id: newDataForm?.id,
    name: newDataForm?.formName,
    fields: newDataForm?.fields?.map(field => ({ code: field.fieldCode, isReadonly: field.isReadonly }))
  })
  assert.equal(newDataForm.id, formA.id, '新增数据必须使用首个流程节点配置的表单')

  const saved = await api('POST', '/entity-data', {
    entityCode,
    name: `节点表单数据${suffix}`,
    data: {
      amount: 10,
      lockedNote: 'LOCKED_ORIGINAL',
      defaultMemo: 'DEFAULT_ORIGINAL',
      finalNote: 'FINAL_ORIGINAL'
    },
    startProcess: true
  })
  record('startProcess', {
    dataId: saved.id,
    processInstanceId: saved.processInstanceId,
    currentTaskId: saved.currentTaskId,
    currentTaskName: saved.currentTaskName
  })
  assert.ok(saved.processInstanceId)

  const node1Progress = await api('GET', `/process-instance/${saved.processInstanceId}/progress`)
  record('node1Progress', {
    activeNodes: node1Progress.activeNodes,
    formId: node1Progress.formConfig?.formId,
    formName: node1Progress.formConfig?.formName,
    isReadonly: node1Progress.formConfig?.isReadonly,
    fields: node1Progress.formConfig?.fields?.map(field => ({ code: field.fieldCode, isReadonly: field.isReadonly }))
  })
  assert.equal(node1Progress.formConfig?.formId, formA.id)
  assertReadonlyFlag(fieldFlag(node1Progress, 'amount'), 0, '首节点金额应可编辑')
  assertReadonlyFlag(fieldFlag(node1Progress, 'lockedNote'), 1, '首节点锁定说明应只读')
  const node1ViewForm = await api('GET', `/entity-form-resolve/view-data/${entityCode}/${saved.id}`)
  record('node1ViewForm', { id: node1ViewForm?.id, name: node1ViewForm?.formName })
  assert.equal(node1ViewForm?.id, formA.id)

  evidence.fixture = {
    entityId: entity.id,
    entityCode,
    processId: process.id,
    processInstanceId: saved.processInstanceId,
    dataId: saved.id,
    formIds: { defaultForm: defaultForm.id, formA: formA.id, formC: formC.id }
  }
  if (stopAt === 'node1') {
    evidence.conclusion = 'FIXTURE_READY: 已停在首节点，供浏览器可视化验收'
    const evidencePath = writeEvidence('PASS')
    console.log(`node form fixture ready: ${evidencePath}`)
    return
  }

  const node1Todo = await currentTodo(saved.processInstanceId)
  assert.equal(node1Todo?.taskName, '首节点混合读写')
  await api('POST', '/process-task/complete', {
    taskId: node1Todo.taskId,
    action: 'approve',
    actionLabel: '通过',
    comment: '首节点保存可编辑金额',
    formData: {
      amount: 88,
      lockedNote: 'LOCKED_TAMPER_NODE1'
    }
  })
  const detailAfterNode1 = await api('GET', `/entity-data/entity/${entityCode}/detail/${saved.id}`)
  record('detailAfterNode1', detailAfterNode1)
  assert.equal(Number(detailAfterNode1.data.amount), 88)
  assert.equal(detailAfterNode1.data.lockedNote, 'LOCKED_ORIGINAL')

  const node2Progress = await api('GET', `/process-instance/${saved.processInstanceId}/progress`)
  record('node2Progress', {
    activeNodes: node2Progress.activeNodes,
    formId: node2Progress.formConfig?.formId,
    formName: node2Progress.formConfig?.formName,
    fields: node2Progress.formConfig?.fields?.map(field => ({ code: field.fieldCode, isReadonly: field.isReadonly }))
  })
  assert.equal(node2Progress.formConfig?.formId, defaultForm.id, '未配置节点必须回退实体默认表单')
  assertReadonlyFlag(fieldFlag(node2Progress, 'defaultMemo'), 0, '默认表单备注应可编辑')
  assertReadonlyFlag(fieldFlag(node2Progress, 'lockedNote'), 1, '默认表单锁定说明应只读')
  const node2ViewForm = await api('GET', `/entity-form-resolve/view-data/${entityCode}/${saved.id}`)
  record('node2ViewForm', { id: node2ViewForm?.id, name: node2ViewForm?.formName })
  assert.equal(node2ViewForm?.id, defaultForm.id)

  const node2Todo = await currentTodo(saved.processInstanceId)
  assert.equal(node2Todo?.taskName, '未配置表单回退')
  await api('POST', '/process-task/complete', {
    taskId: node2Todo.taskId,
    action: 'approve',
    actionLabel: '通过',
    comment: '默认表单回退节点保存备注',
    formData: {
      defaultMemo: 'DEFAULT_UPDATED',
      lockedNote: 'LOCKED_TAMPER_NODE2'
    }
  })
  const detailAfterNode2 = await api('GET', `/entity-data/entity/${entityCode}/detail/${saved.id}`)
  record('detailAfterNode2', detailAfterNode2)
  assert.equal(detailAfterNode2.data.defaultMemo, 'DEFAULT_UPDATED')
  assert.equal(detailAfterNode2.data.lockedNote, 'LOCKED_ORIGINAL')

  const node3Progress = await api('GET', `/process-instance/${saved.processInstanceId}/progress`)
  record('node3Progress', {
    activeNodes: node3Progress.activeNodes,
    formId: node3Progress.formConfig?.formId,
    formName: node3Progress.formConfig?.formName,
    isReadonly: node3Progress.formConfig?.isReadonly,
    fields: node3Progress.formConfig?.fields?.map(field => ({ code: field.fieldCode, isReadonly: field.isReadonly }))
  })
  assert.equal(node3Progress.formConfig?.formId, formC.id)
  assert.equal(node3Progress.formConfig?.isReadonly, true)
  for (const field of node3Progress.formConfig?.fields || []) {
    assertReadonlyFlag(field.isReadonly, 1, `末节点字段 ${field.fieldCode} 应被节点配置强制只读`)
  }
  const node3ViewForm = await api('GET', `/entity-form-resolve/view-data/${entityCode}/${saved.id}`)
  record('node3ViewForm', { id: node3ViewForm?.id, name: node3ViewForm?.formName })
  assert.equal(node3ViewForm?.id, formC.id)

  const node3Todo = await currentTodo(saved.processInstanceId)
  assert.equal(node3Todo?.taskName, '末节点专属全只读')
  await api('POST', '/process-task/complete', {
    taskId: node3Todo.taskId,
    action: 'approve',
    actionLabel: '通过',
    comment: '全只读节点提交篡改值应被忽略',
    formData: {
      finalNote: 'FINAL_TAMPER',
      amount: 999
    }
  })
  const finalDetail = await api('GET', `/entity-data/entity/${entityCode}/detail/${saved.id}`)
  record('finalDetail', finalDetail)
  assert.equal(finalDetail.data.finalNote, 'FINAL_ORIGINAL')
  assert.equal(Number(finalDetail.data.amount), 88)

  const completedProgress = await api('GET', `/process-instance/${saved.processInstanceId}/progress`)
  record('completedProgress', {
    status: completedProgress.status,
    completedNodes: completedProgress.completedNodes,
    activeNodes: completedProgress.activeNodes
  })
  assert.ok(['COMPLETED', 'ENDED'].includes(completedProgress.status))

  evidence.conclusion = 'PASS: 首节点表单、节点专属表单、默认回退、字段级读写、节点全只读和数据落库均与配置一致'
  const evidencePath = writeEvidence('PASS')
  console.log(`real node form matrix passed: ${evidencePath}`)
}

main().catch(error => {
  evidence.error = error.stack || String(error)
  evidence.conclusion = 'FAIL'
  const evidencePath = writeEvidence('FAIL')
  console.error(error)
  console.error(`evidence written: ${evidencePath}`)
  process.exit(1)
})
