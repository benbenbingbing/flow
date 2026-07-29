import assert from 'node:assert/strict'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const apiBase = process.env.WORKFLOW_API_BASE || 'http://localhost:8080/api'
const testUsername = process.env.TEST_USERNAME || 'admin'
const testPassword = process.env.TEST_PASSWORD || 'admin'
const suffix = `${new Date().toISOString().replace(/[-:TZ.]/g, '').slice(2, 12)}${Math.random().toString(36).slice(2, 5)}`
const processKey = `action_timing_${suffix}`
const entityCode = `action_timing_entity_${suffix}`
const outputDir = path.resolve('docs/flow-action-timing-e2e')
const evidence = {
  processKey,
  entityCode,
  instances: {},
  actions: [],
  executions: {},
  handlers: {
    prepared: [],
    restored: []
  }
}
let token = ''
const handlerConfigBackups = new Map()
const handlerDefinitions = new Map()

mkdirSync(outputDir, { recursive: true })

async function api(method, endpoint, body) {
  const response = await fetch(apiBase + endpoint, {
    method,
    signal: AbortSignal.timeout(20000),
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body == null ? undefined : JSON.stringify(body)
  })
  const text = await response.text()
  let payload
  try {
    payload = text ? JSON.parse(text) : null
  } catch {
    throw new Error(`${method} ${endpoint} returned non-json: ${text.slice(0, 500)}`)
  }
  if (!response.ok || payload?.code !== 200) {
    throw new Error(`${method} ${endpoint} failed: HTTP ${response.status}, body=${text.slice(0, 1200)}`)
  }
  return payload.data
}

async function waitFor(check, message, timeout = 20000) {
  const started = Date.now()
  let lastValue
  while (Date.now() - started < timeout) {
    lastValue = await check()
    if (lastValue) return lastValue
    await new Promise(resolve => setTimeout(resolve, 400))
  }
  throw new Error(`${message}: ${JSON.stringify(lastValue)}`)
}

async function prepareActionHandlers() {
  const configs = await api('GET', '/process-action-handlers/configs')
  try {
    for (const beanName of ['demoSimpleActionHandler', 'demoFailingActionHandler']) {
      const current = configs.find(item => item.beanName === beanName)
      assert.ok(current?.configured, `测试处理器 ${beanName} 必须已加入动作目录`)
      assert.ok(current?.available, `测试处理器 ${beanName} 必须已注册`)
      const backup = {
        displayName: current.displayName,
        description: current.description,
        visibilityScope: current.visibilityScope,
        entityCodes: current.entityCodes || [],
        enabled: current.enabled
      }
      handlerConfigBackups.set(beanName, backup)
      const saved = await api(
        'POST',
        `/process-action-handlers/configs/${encodeURIComponent(beanName)}`,
        {
          ...backup,
          visibilityScope: 'ENTITY',
          entityCodes: [...new Set([...backup.entityCodes, entityCode])],
          enabled: true
        }
      )
      handlerDefinitions.set(beanName, saved)
      evidence.handlers.prepared.push({
        beanName,
        definitionId: saved.definitionId,
        visibilityScope: saved.visibilityScope,
        includesTestEntity: saved.entityCodes?.includes(entityCode),
        enabled: saved.enabled
      })
    }
  } catch (error) {
    const restoreErrors = await restoreActionHandlers()
    if (restoreErrors.length) {
      throw new Error(`${error.message}; 准备失败后的动作目录恢复也失败: ${restoreErrors.join('; ')}`, {
        cause: error
      })
    }
    throw error
  }
}

async function restoreActionHandlers() {
  const errors = []
  for (const [beanName, backup] of handlerConfigBackups) {
    try {
      const restored = await api(
        'POST',
        `/process-action-handlers/configs/${encodeURIComponent(beanName)}`,
        backup
      )
      evidence.handlers.restored.push({
        beanName,
        visibilityScope: restored.visibilityScope,
        entityCodes: restored.entityCodes || [],
        enabled: restored.enabled
      })
    } catch (error) {
      errors.push(`${beanName}: ${error.message}`)
    }
  }
  return errors
}

function bpmnXml() {
  return `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  xmlns:flowable="http://flowable.org/bpmn"
  id="Definitions_ActionTiming"
  targetNamespace="http://workflow.codex/action-timing">
  <bpmn:process id="${processKey}" name="流程动作时机闭环${suffix}" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="开始"><bpmn:outgoing>Flow_Start_Review</bpmn:outgoing></bpmn:startEvent>
    <bpmn:userTask id="Task_Review" name="动作时机审批" flowable:assignee="${testUsername}">
      <bpmn:incoming>Flow_Start_Review</bpmn:incoming><bpmn:outgoing>Flow_Review_Confirm</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:userTask id="Task_Confirm" name="动作时机确认" flowable:assignee="${testUsername}">
      <bpmn:incoming>Flow_Review_Confirm</bpmn:incoming><bpmn:outgoing>Flow_Confirm_End</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:endEvent id="EndEvent_1" name="结束"><bpmn:incoming>Flow_Confirm_End</bpmn:incoming></bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_Start_Review" sourceRef="StartEvent_1" targetRef="Task_Review" />
    <bpmn:sequenceFlow id="Flow_Review_Confirm" sourceRef="Task_Review" targetRef="Task_Confirm" />
    <bpmn:sequenceFlow id="Flow_Confirm_End" sourceRef="Task_Confirm" targetRef="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_ActionTiming">
    <bpmndi:BPMNPlane id="BPMNPlane_ActionTiming" bpmnElement="${processKey}">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1"><dc:Bounds x="60" y="100" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Review_di" bpmnElement="Task_Review"><dc:Bounds x="150" y="78" width="120" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Confirm_di" bpmnElement="Task_Confirm"><dc:Bounds x="340" y="78" width="120" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1"><dc:Bounds x="530" y="100" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_Start_Review_di" bpmnElement="Flow_Start_Review"><di:waypoint x="96" y="118" /><di:waypoint x="150" y="118" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Review_Confirm_di" bpmnElement="Flow_Review_Confirm"><di:waypoint x="270" y="118" /><di:waypoint x="340" y="118" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Flow_Confirm_End_di" bpmnElement="Flow_Confirm_End"><di:waypoint x="460" y="118" /><di:waypoint x="530" y="118" /></bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
}

async function saveAction(processId, config) {
  const interfaceName = config.interfaceName || 'demoSimpleActionHandler'
  const definition = handlerDefinitions.get(interfaceName)
  assert.ok(definition?.definitionId, `动作处理器 ${interfaceName} 缺少目录定义`)
  const action = await api('POST', '/process-actions', {
    processConfigId: processId,
    scopeType: config.scopeType,
    elementId: config.elementId || null,
    sequenceFlowId: config.elementId || '__PROCESS__',
    triggerTiming: config.triggerTiming,
    executionMode: config.executionMode,
    failurePolicy: config.failurePolicy,
    actionName: config.actionName || config.triggerTiming,
    description: `E2E ${config.triggerTiming}`,
    interfaceName,
    methodName: 'execute',
    paramsJson: JSON.stringify({ timing: config.triggerTiming, message: config.message || '' }),
    retryConfig: config.retryConfig ? JSON.stringify(config.retryConfig) : null,
    actionDefinitionId: definition.definitionId,
    enabled: true,
    sortOrder: evidence.actions.length
  })
  evidence.actions.push(action)
}

async function createInstance(name) {
  const data = await api('POST', '/entity-data', {
    entityCode,
    name,
    data: { name, amount: 100, remark: name },
    startProcess: true
  })
  assert.ok(data.processInstanceId)
  return data
}

async function currentTask(processInstanceId, taskName) {
  return waitFor(async () => {
    const page = await api('GET', '/process-task/todo?pageNum=1&pageSize=1000')
    const records = page?.records || page?.list || []
    return records.find(item => item.processInstanceId === processInstanceId
      && (!taskName || item.taskName === taskName))
  }, `未找到待办 ${taskName || ''}`)
}

async function completeTask(task, comment) {
  await api('POST', '/process-task/complete', {
    taskId: task.taskId,
    action: 'approve',
    actionLabel: '通过',
    comment
  })
}

async function executions(processInstanceId) {
  return api('GET', `/process-action-executions/process/${processInstanceId}`)
}

async function waitForTiming(processInstanceId, timing, status = 'SUCCESS') {
  return waitFor(async () => {
    const records = await executions(processInstanceId)
    return records.find(item => item.triggerTiming === timing && item.status === status) || null
  }, `动作时机 ${timing} 未进入 ${status}`)
}

async function main() {
  const login = await api('POST', '/auth/login', {
    username: testUsername,
    password: testPassword
  })
  token = login.token

  const workflowProcess = await api('POST', '/process', {
    processKey,
    processName: `流程动作时机闭环${suffix}`,
    description: '验证十个标准流程动作时机、Outbox 与死信',
    category: 'codex-action-timing',
    bpmnXml: bpmnXml()
  })

  const entity = await api('POST', '/entity', {
    entityCode,
    entityName: `流程动作时机实体${suffix}`,
    description: '流程动作真实闭环测试实体',
    fields: [
      { fieldCode: 'amount', fieldName: '金额', fieldType: 'DECIMAL', isRequired: true, isUnique: false, sortOrder: 10 },
      { fieldCode: 'remark', fieldName: '备注', fieldType: 'TEXT', isRequired: false, isUnique: false, sortOrder: 20 }
    ]
  })
  await api('POST', `/entity/${entity.id}/publish`)
  await api('POST', `/entity/${entity.id}/workflow-binding/update`, {
    processDefinitionId: workflowProcess.id
  })
  await api('POST', `/entity-status/save-list/${entityCode}`, [
    { statusCode: 'DRAFT', statusName: '草稿', statusCategory: 'NEW', color: '#909399' },
    { statusCode: 'IN_REVIEW', statusName: '审批中', statusCategory: 'PROCESSING', color: '#409eff' },
    { statusCode: 'APPROVED', statusName: '已完成', statusCategory: 'COMPLETED', color: '#67c23a' },
    { statusCode: 'WITHDRAWN', statusName: '已撤回', statusCategory: 'WITHDRAWN', color: '#e6a23c' },
    { statusCode: 'TERMINATED', statusName: '已终止', statusCategory: 'TERMINATED', color: '#f56c6c' }
  ])

  await prepareActionHandlers()
  let primaryError = null
  let completedInstance
  let withdrawnInstance
  let terminatedInstance
  let observedTimings
  try {
    const sync = { executionMode: 'IN_TRANSACTION', failurePolicy: 'ROLLBACK' }
    const asyncRetry = { executionMode: 'AFTER_COMMIT', failurePolicy: 'RETRY', retryConfig: { maxRetries: 3 } }
    await saveAction(workflowProcess.id, { scopeType: 'PROCESS', triggerTiming: 'PROCESS_STARTED', ...sync })
    await saveAction(workflowProcess.id, { scopeType: 'PROCESS', triggerTiming: 'PROCESS_COMPLETED', ...asyncRetry })
    await saveAction(workflowProcess.id, { scopeType: 'PROCESS', triggerTiming: 'PROCESS_WITHDRAWN', ...asyncRetry })
    await saveAction(workflowProcess.id, { scopeType: 'PROCESS', triggerTiming: 'PROCESS_TERMINATED', ...asyncRetry })
    await saveAction(workflowProcess.id, { scopeType: 'NODE', elementId: 'Task_Review', triggerTiming: 'NODE_ENTERED', ...sync })
    await saveAction(workflowProcess.id, { scopeType: 'NODE', elementId: 'Task_Review', triggerTiming: 'NODE_COMPLETED', ...sync })
    await saveAction(workflowProcess.id, { scopeType: 'NODE', elementId: 'Task_Review', triggerTiming: 'TASK_CREATED', ...asyncRetry })
    await saveAction(workflowProcess.id, { scopeType: 'NODE', elementId: 'Task_Review', triggerTiming: 'TASK_ASSIGNED', ...asyncRetry })
    await saveAction(workflowProcess.id, { scopeType: 'NODE', elementId: 'Task_Review', triggerTiming: 'TASK_COMPLETING', ...sync })
    await saveAction(workflowProcess.id, { scopeType: 'SEQUENCE_FLOW', elementId: 'Flow_Start_Review', triggerTiming: 'TRANSITION_TAKEN', ...sync })
    await saveAction(workflowProcess.id, {
      scopeType: 'PROCESS',
      triggerTiming: 'PROCESS_COMPLETED',
      ...asyncRetry,
      actionName: '流程完成后失败动作',
      interfaceName: 'demoFailingActionHandler',
      retryConfig: { maxRetries: 1 },
      message: 'E2E 预期失败'
    })

    const published = await api('POST', `/process/${workflowProcess.id}/publish`, {
      versionDescription: `流程动作时机真实闭环 ${suffix}`
    })
    assert.equal(published.status, 'PUBLISHED')

    completedInstance = await createInstance(`动作完成实例${suffix}`)
    evidence.instances.completed = completedInstance
    const firstTask = await currentTask(completedInstance.processInstanceId, '动作时机审批')
    await completeTask(firstTask, '一级通过')
    const secondTask = await currentTask(completedInstance.processInstanceId, '动作时机确认')
    await completeTask(secondTask, '二级通过')

    for (const timing of [
      'PROCESS_STARTED',
      'NODE_ENTERED',
      'NODE_COMPLETED',
      'TASK_CREATED',
      'TASK_ASSIGNED',
      'TASK_COMPLETING',
      'TRANSITION_TAKEN',
      'PROCESS_COMPLETED'
    ]) {
      await waitForTiming(completedInstance.processInstanceId, timing)
    }
    await waitForTiming(completedInstance.processInstanceId, 'PROCESS_COMPLETED', 'DEAD')
    evidence.executions.completed = await executions(completedInstance.processInstanceId)

    withdrawnInstance = await createInstance(`动作撤回实例${suffix}`)
    evidence.instances.withdrawn = withdrawnInstance
    await api('POST', `/process-instance/${withdrawnInstance.processInstanceId}/terminate`, {
      reason: `发起人撤回: E2E ${suffix}`
    })
    await waitForTiming(withdrawnInstance.processInstanceId, 'PROCESS_WITHDRAWN')
    evidence.executions.withdrawn = await executions(withdrawnInstance.processInstanceId)

    terminatedInstance = await createInstance(`动作终止实例${suffix}`)
    evidence.instances.terminated = terminatedInstance
    await api('POST', `/process-instance/${terminatedInstance.processInstanceId}/terminate`, {
      reason: `E2E 主动终止 ${suffix}`
    })
    await waitForTiming(terminatedInstance.processInstanceId, 'PROCESS_TERMINATED')
    evidence.executions.terminated = await executions(terminatedInstance.processInstanceId)

    observedTimings = new Set([
      ...evidence.executions.completed,
      ...evidence.executions.withdrawn,
      ...evidence.executions.terminated
    ].map(item => item.triggerTiming))
    const standardTimings = [
      'PROCESS_STARTED',
      'PROCESS_COMPLETED',
      'PROCESS_WITHDRAWN',
      'PROCESS_TERMINATED',
      'NODE_ENTERED',
      'NODE_COMPLETED',
      'TASK_CREATED',
      'TASK_ASSIGNED',
      'TASK_COMPLETING',
      'TRANSITION_TAKEN'
    ]
    standardTimings.forEach(timing => assert.ok(observedTimings.has(timing), `缺少时机 ${timing}`))
  } catch (error) {
    primaryError = error
  }

  const restoreErrors = await restoreActionHandlers()
  if (primaryError) {
    if (restoreErrors.length) {
      throw new Error(`${primaryError.message}; 恢复动作目录失败: ${restoreErrors.join('; ')}`, {
        cause: primaryError
      })
    }
    throw primaryError
  }
  assert.deepEqual(restoreErrors, [], '测试结束后必须恢复动作处理器目录配置')

  const evidenceFile = path.join(outputDir, `flow-action-timing-${suffix}.json`)
  writeFileSync(evidenceFile, JSON.stringify({
    result: 'PASS',
    processKey,
    entityCode,
    expectedTimings: standardTimings
  }, null, 2), { mode: 0o600 })
  console.log(JSON.stringify({
    processId: workflowProcess.id,
    processKey,
    entityCode,
    completedProcessInstanceId: completedInstance.processInstanceId,
    withdrawnProcessInstanceId: withdrawnInstance.processInstanceId,
    terminatedProcessInstanceId: terminatedInstance.processInstanceId,
    observedTimings: [...observedTimings].sort(),
    evidenceFile
  }, null, 2))
}

main().catch(error => {
  console.error(error)
  process.exitCode = 1
})
