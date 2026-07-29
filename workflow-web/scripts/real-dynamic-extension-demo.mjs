import assert from 'node:assert/strict'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const baseUrl = process.env.WORKFLOW_API_BASE || 'http://localhost:8080/api'
const testUsername = process.env.TEST_USERNAME || 'admin'
const testPassword = process.env.TEST_PASSWORD || 'admin'
const stamp = new Date().toISOString().replace(/[-:TZ.]/g, '').slice(2, 12)
const suffix = `${stamp}${Math.random().toString(36).slice(2, 5)}`
const processKey = `demo_extension_flow_${suffix}`
const processName = `Demo扩展验证流程${suffix}`
const entityCode = `demo_extension_entity_${suffix}`
const entityName = `Demo扩展验证实体${suffix}`
const listKey = 'demo_cards'
const evidenceDir = path.resolve('docs/dynamic-extension-demo')
mkdirSync(evidenceDir, { recursive: true })

let token = ''
const evidence = {
  baseUrl,
  processKey,
  processName,
  entityCode,
  entityName,
  extensions: {
    listCell: 'DemoRiskProgressCell',
    customList: 'DemoProjectCardList',
    customForm: 'DemoProjectForm'
  },
  steps: []
}

async function api(method, url, body) {
  const response = await fetch(baseUrl + url, {
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

function record(name, data) {
  evidence.steps.push({ name, data })
  return data
}

function toList(page) {
  if (Array.isArray(page)) return page
  return page?.records || page?.list || page?.rows || []
}

function getEntityField(fields, code) {
  const field = fields.find(item => item.fieldCode === code)
  assert.ok(field?.id, `实体字段 ${code} 应存在`)
  return field
}

async function ensureUiExtension({
  extensionType,
  extensionKey,
  displayName,
  supportedModes = [],
  capabilities = {}
}) {
  const query = new URLSearchParams({
    extensionType,
    extensionKey
  })
  const definitions = await api('GET', `/ui-extensions?${query}`)
  const existing = definitions.find(item => Number(item.version) === 1)
  if (existing) {
    assert.equal(existing.status, 'ACTIVE', `${extensionKey}@1 必须处于 ACTIVE 状态`)
    assert.ok(Number(existing.snapshotVersion) >= 1, `${extensionKey}@1 快照版本必须有效`)
    return existing
  }
  return api('POST', '/ui-extensions', {
    extensionType,
    extensionKey,
    displayName,
    version: 1,
    snapshotVersion: 1,
    supportedModes,
    supportedNodeTypes: [],
    supportedBindings: [],
    configSchema: [],
    capabilities,
    status: 'ACTIVE'
  })
}

function formField(fields, code, componentType, overrides = {}) {
  const field = getEntityField(fields, code)
  return {
    fieldId: field.id,
    fieldCode: code,
    fieldName: field.fieldName,
    fieldLabel: field.fieldName,
    fieldType: field.fieldType,
    componentType,
    isRequired: ['projectName', 'code', 'ownerName', 'budget'].includes(code) ? 1 : 0,
    isReadonly: 0,
    isHidden: 0,
    gridSpan: 12,
    ...overrides
  }
}

function listField(fields, code, overrides = {}) {
  const field = getEntityField(fields, code)
  return {
    fieldId: field.id,
    fieldCode: code,
    fieldName: field.fieldName,
    showInList: true,
    isQuery: ['projectName', 'ownerName', 'status'].includes(code),
    queryType: code === 'projectName' ? 'LIKE' : 'EQ',
    width: 0,
    align: code === 'riskScore' ? 'center' : 'left',
    dataSourceType: 'ENTITY_FIELD',
    dataSourceConfig: '',
    renderComponent: '',
    formatter: '',
    columnConfig: JSON.stringify({
      minWidth: code === 'projectName' ? 180 : 120,
      showOverflowTooltip: true
    }),
    queryConfig: '',
    renderConfig: '',
    ...overrides
  }
}

function bpmnXml(formId) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_${processKey}" targetNamespace="http://workflow.codex/dynamic-extension-demo">
  <process id="${processKey}" name="${processName}" isExecutable="true">
    <startEvent id="StartEvent_1" name="开始"><outgoing>Flow_1</outgoing></startEvent>
    <userTask id="Task_Demo_Review" name="Demo项目审批" flowable:assignee="${testUsername}">
      <extensionElements><flowable:properties>
        <flowable:property name="entityFormId" value="${formId}" />
        <flowable:property name="entityFormReadonly" value="false" />
      </flowable:properties></extensionElements>
      <incoming>Flow_1</incoming><outgoing>Flow_2</outgoing>
    </userTask>
    <endEvent id="EndEvent_1" name="结束"><incoming>Flow_2</incoming></endEvent>
    <sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_Demo_Review" />
    <sequenceFlow id="Flow_2" sourceRef="Task_Demo_Review" targetRef="EndEvent_1" />
  </process>
  <bpmndi:BPMNDiagram id="Diagram_${processKey}">
    <bpmndi:BPMNPlane id="Plane_${processKey}" bpmnElement="${processKey}">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1"><dc:Bounds x="60" y="100" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Demo_Review_di" bpmnElement="Task_Demo_Review"><dc:Bounds x="150" y="78" width="140" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1"><dc:Bounds x="360" y="100" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1"><di:waypoint x="96" y="118" /><di:waypoint x="150" y="118" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_2_di" bpmnElement="Flow_2"><di:waypoint x="290" y="118" /><di:waypoint x="360" y="118" /></bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>`
}

async function currentTodo(processInstanceId) {
  const page = await api('GET', '/process-task/todo?pageNum=1&pageSize=500')
  return toList(page).find(task => task.processInstanceId === processInstanceId)
}

function writeEvidence(result, error) {
  evidence.result = result
  if (error) evidence.error = error instanceof Error ? error.name : 'UnknownError'
  const fileName = `dynamic-extension-demo-${suffix}${result === 'PASS' ? '' : '-failed'}.json`
  const evidencePath = path.join(evidenceDir, fileName)
  const report = {
    result,
    conclusion: evidence.conclusion,
    error: evidence.error,
    entityCode,
    stepNames: evidence.steps.map(step => step.name)
  }
  writeFileSync(evidencePath, JSON.stringify(report, null, 2), { mode: 0o600 })
  writeFileSync(path.join(evidenceDir, 'latest.json'), JSON.stringify(report, null, 2), { mode: 0o600 })
  return evidencePath
}

async function main() {
  const login = await api('POST', '/auth/login', {
    username: testUsername,
    password: testPassword
  })
  token = login.token
  record('login', { id: login.id, username: login.username })

  const entity = await api('POST', '/entity', {
    entityCode,
    entityName,
    description: '验证列表字段扩展、自定义列表组件、自定义表单组件',
    fields: [
      { fieldCode: 'projectName', fieldName: '项目名称', fieldType: 'STRING', fieldLength: 200, isRequired: true, isUnique: false, editable: true, sortOrder: 10 },
      { fieldCode: 'ownerName', fieldName: '负责人', fieldType: 'STRING', fieldLength: 100, isRequired: true, isUnique: false, editable: true, sortOrder: 20 },
      { fieldCode: 'budget', fieldName: '项目预算', fieldType: 'DECIMAL', fieldPrecision: 2, isRequired: true, isUnique: false, editable: true, sortOrder: 30 },
      { fieldCode: 'riskScore', fieldName: '风险评分', fieldType: 'INTEGER', isRequired: false, isUnique: false, defaultValue: '20', editable: true, sortOrder: 40 },
      { fieldCode: 'description', fieldName: '项目说明', fieldType: 'TEXT', isRequired: false, isUnique: false, editable: true, sortOrder: 50 }
    ]
  })
  await api('POST', `/entity/${entity.id}/publish`)
  const entityDetail = await api('GET', `/entity/${entity.id}`)
  const fields = entityDetail.fields || []
  record('createAndPublishEntity', {
    id: entity.id,
    entityCode,
    fields: fields.map(field => ({ id: field.id, code: field.fieldCode, type: field.fieldType }))
  })

  const formExtension = await ensureUiExtension({
    extensionType: 'FORM',
    extensionKey: 'DemoProjectForm',
    displayName: 'Demo·项目定制表单',
    supportedModes: ['CREATE', 'EDIT', 'APPROVE', 'VIEW'],
    capabilities: {
      modes: ['create', 'edit', 'approve', 'view'],
      hotfixCompatible: true
    }
  })
  const listExtension = await ensureUiExtension({
    extensionType: 'LIST',
    extensionKey: 'DemoProjectCardList',
    displayName: 'Demo·项目卡片列表',
    capabilities: {
      layout: 'card',
      reusesPlatformActions: true,
      hotfixCompatible: true
    }
  })
  record('ensureUiExtensions', {
    form: {
      id: formExtension.id,
      version: formExtension.version,
      snapshotVersion: formExtension.snapshotVersion,
      status: formExtension.status
    },
    list: {
      id: listExtension.id,
      version: listExtension.version,
      snapshotVersion: listExtension.snapshotVersion,
      status: listExtension.status
    }
  })

  const form = await api('POST', '/entity-form', {
    entityId: entity.id,
    formName: 'Demo项目定制表单',
    formKey: `demo_project_form_${suffix}`,
    description: 'src/demo/forms/DemoProjectForm.vue 真实配置',
    layoutType: 'grid',
    isDefault: true,
    status: 1,
    customComponent: 'DemoProjectForm',
    customComponentVersion: formExtension.version,
    customComponentSnapshotVersion: formExtension.snapshotVersion,
    viewConfig: JSON.stringify({
      labelWidth: 110,
      customComponentProps: {
        subtitle: '真实实体与流程验证表单',
        accentColor: '#7c3aed',
        showRiskHint: true
      }
    }),
    fields: [
      formField(fields, 'projectName', 'input'),
      formField(fields, 'code', 'input'),
      formField(fields, 'ownerName', 'input'),
      formField(fields, 'budget', 'number'),
      formField(fields, 'riskScore', 'number', {
        validationRules: JSON.stringify({ min: 0, max: 100 })
      }),
      formField(fields, 'description', 'textarea', { gridSpan: 24 })
    ]
  })
  record('createCustomForm', {
    id: form.id,
    customComponent: form.customComponent,
    viewConfig: form.viewConfig
  })
  const formRelease = await api('POST', `/entity-forms/${form.id}/publish`, {
    description: 'Demo项目定制表单正式发布'
  })
  assert.equal(formRelease.status, 'ACTIVE')
  record('publishCustomForm', {
    releaseId: formRelease.id,
    version: formRelease.version,
    status: formRelease.status
  })

  const workflowProcess = await api('POST', '/process', {
    processKey,
    processName,
    description: 'Demo自定义表单审批验证',
    category: 'dynamic-extension-demo',
    bpmnXml: bpmnXml(form.id)
  })
  const publishedProcess = await api('POST', `/process/${workflowProcess.id}/publish`, {
    versionDescription: 'Demo扩展真实验证'
  })
  await api('POST', `/entity/${entity.id}/workflow-binding/update`, {
    processDefinitionId: workflowProcess.id
  })
  record('createPublishBindProcess', {
    processId: workflowProcess.id,
    status: publishedProcess.status,
    processKey
  })
  assert.equal(publishedProcess.status, 'PUBLISHED')

  const listConfig = await api('POST', '/entity-list-config/save', {
    entityId: entity.id,
    entityCode,
    listKey,
    listName: 'Demo项目卡片列表',
    description: 'src/demo/lists/DemoProjectCardList.vue 真实配置',
    isDefault: true,
    customComponent: 'DemoProjectCardList',
    viewConfig: {
      search: { defaultVisibleCount: 3, collapsible: true, labelWidth: 100 },
      table: { stripe: true, border: false, showIndex: false, size: 'default' },
      pagination: { pageSize: 10, pageSizes: [10, 20, 50] },
      customComponentProps: {
        columns: 3,
        compact: false,
        showDescription: true,
        searchPlaceholder: '搜索Demo项目'
      }
    },
    fields: [
      listField(fields, 'projectName', { width: 200 }),
      listField(fields, 'ownerName', { width: 120 }),
      listField(fields, 'budget', { align: 'right' }),
      listField(fields, 'riskScore', {
        width: 230,
        renderComponent: 'DemoRiskProgressCell',
        renderConfig: JSON.stringify({
          warningAt: 40,
          dangerAt: 70,
          showText: true,
          showLevel: true
        })
      }),
      listField(fields, 'description', { isQuery: false, width: 260 }),
      listField(fields, 'status', { width: 110, align: 'center' })
    ]
  })
  record('createCustomList', {
    id: listConfig.id,
    customComponent: listConfig.customComponent,
    riskRenderer: listConfig.fields.find(field => field.fieldCode === 'riskScore')?.renderComponent
  })
  assert.equal(listConfig.customComponent, 'DemoProjectCardList')
  assert.equal(
    listConfig.fields.find(field => field.fieldCode === 'riskScore')?.renderComponent,
    'DemoRiskProgressCell')
  const listRelease = await api('POST', `/entity-list-config/${listConfig.id}/publish`, {
    description: 'Demo项目卡片列表正式发布'
  })
  assert.equal(listRelease.status, 'ACTIVE')
  record('publishCustomList', {
    releaseId: listRelease.id,
    version: listRelease.version,
    status: listRelease.status
  })

  const newDataForm = await api('GET', `/entity-form-resolve/new-data/${entityCode}`)
  record('resolveNewDataForm', {
    id: newDataForm.id,
    customComponent: newDataForm.customComponent
  })
  assert.equal(newDataForm.customComponent, 'DemoProjectForm')

  const data = await api('POST', '/entity-data', {
    entityCode,
    name: `Demo项目${suffix}`,
    data: {
      name: `Demo项目${suffix}`,
      code: `DEMO-${suffix.toUpperCase()}`,
      projectName: `Demo动态扩展项目${suffix}`,
      ownerName: '超级管理员',
      budget: 280000,
      riskScore: 58,
      description: '验证风险单元格、项目卡片列表和定制表单'
    },
    startProcess: true
  })
  record('createDataAndStartProcess', {
    id: data.id,
    processInstanceId: data.processInstanceId,
    currentTaskName: data.currentTaskName
  })
  assert.ok(data.processInstanceId)
  assert.equal(data.currentTaskName, 'Demo项目审批')

  const configuredRows = await api(
    'GET',
    `/entity-data/entity/${entityCode}/list-with-config?listKey=${listKey}&projectName=Demo动态扩展&projectName_op=LIKE`)
  const demoRow = toList(configuredRows).find(row => row.id === data.id)
  record('queryCustomListData', {
    count: toList(configuredRows).length,
    row: demoRow && {
      id: demoRow.id,
      projectName: demoRow.data?.projectName,
      riskScore: demoRow.data?.riskScore
    }
  })
  assert.ok(demoRow)
  assert.equal(Number(demoRow.data.riskScore), 58)

  const progress = await api('GET', `/process-instance/${data.processInstanceId}/progress`)
  record('progressWithCustomForm', {
    status: progress.status,
    activeNodes: progress.activeNodes,
    formId: progress.formConfig?.formId,
    customComponent: progress.formConfig?.customComponent,
    fields: progress.formConfig?.fields?.map(field => field.fieldCode)
  })
  assert.equal(progress.formConfig?.customComponent, 'DemoProjectForm')

  const todo = await currentTodo(data.processInstanceId)
  assert.ok(todo)
  await api('POST', '/process-task/complete', {
    taskId: todo.taskId,
    action: 'approve',
    actionLabel: 'Demo验证通过',
    comment: '列表字段扩展、自定义列表、自定义表单验证通过',
    formData: {
      projectName: demoRow.data.projectName,
      ownerName: demoRow.data.ownerName,
      budget: demoRow.data.budget,
      riskScore: 35,
      description: demoRow.data.description
    }
  })

  const completed = await api('GET', `/entity-data/entity/${entityCode}/detail/${data.id}`)
  const completedProgress = await api('GET', `/process-instance/${data.processInstanceId}/progress`)
  record('completeDemoProcess', {
    status: completed.status,
    riskScore: completed.data?.riskScore,
    processStatus: completedProgress.status
  })
  assert.equal(completedProgress.status, 'COMPLETED')
  assert.equal(Number(completed.data.riskScore), 35)

  evidence.fixture = {
    entityId: entity.id,
    entityCode,
    processId: workflowProcess.id,
    processKey,
    formId: form.id,
    listConfigId: listConfig.id,
    listKey,
    dataId: data.id,
    processInstanceId: data.processInstanceId,
    listRoute: `/entity-list/${entityCode}/${listKey}`
  }
  evidence.conclusion = 'PASS: 三个 src/demo 扩展已由真实流程、实体、表单、列表和业务数据验证'
  const evidencePath = writeEvidence('PASS')
  console.log(`dynamic extension demo passed: ${evidencePath}`)
}

main().catch(error => {
  const evidencePath = writeEvidence('FAIL', error)
  console.error(`dynamic extension demo failed: ${evidencePath}`)
  console.error('dynamic extension demo failed; see the sanitized evidence file')
  process.exitCode = 1
})
