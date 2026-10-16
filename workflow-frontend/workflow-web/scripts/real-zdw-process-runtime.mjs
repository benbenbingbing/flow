import assert from 'node:assert/strict'
import {
  existsSync,
  mkdirSync,
  readFileSync,
  unlinkSync,
  writeFileSync
} from 'node:fs'
import path from 'node:path'

const apiBase = process.env.WORKFLOW_API_BASE || 'http://127.0.0.1:8080/api'
const credentialFile = process.env.TEST_CREDENTIAL_FILE
  || '/private/tmp/workflow-zdw-process-runtime.credentials.json'
const evidenceDir = path.resolve('docs/zdw-process-runtime-e2e')

assert.ok(existsSync(credentialFile), `缺少一次性凭据文件: ${credentialFile}`)
mkdirSync(evidenceDir, { recursive: true })

let token = ''
let failureContext = {}
let createdParentId = null
let createdProcessInstanceId = null

async function request(method, url, body) {
  const response = await fetch(`${apiBase}${url}`, {
    method,
    signal: AbortSignal.timeout(30000),
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body == null ? undefined : JSON.stringify(body)
  })
  const payload = await response.json().catch(() => null)
  if (!response.ok
      || !payload
      || ![0, 200, '0', '200'].includes(payload.code)) {
    const error = new Error(
      payload?.message || `${method} ${url} failed: HTTP ${response.status}`
    )
    error.status = response.status
    error.errorCode = payload?.errorCode
    throw error
  }
  return payload.data
}

function parseObject(value) {
  if (!value) return {}
  if (typeof value === 'object' && !Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(value)
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
      ? parsed
      : {}
  } catch {
    return {}
  }
}

function pageRecords(page) {
  return page?.records || page?.list || page?.rows || []
}

function recordData(record) {
  return {
    ...(record?.data || {}),
    id: record?.id,
    name: record?.name,
    code: record?.code,
    status: record?.status
  }
}

function flattenNodes(nodes) {
  const result = []
  const visit = node => {
    if (!node || typeof node !== 'object') return
    result.push(node)
    for (const key of ['children', 'childNodes', 'nodes', 'items']) {
      if (Array.isArray(node[key])) node[key].forEach(visit)
    }
  }
  ;(nodes || []).forEach(visit)
  return result
}

function nodeFieldConfig(formConfig, fieldCode) {
  return flattenNodes(formConfig?.nodes).find(node => {
    const props = parseObject(node.propsDocument || node.props)
    return props.fieldCode === fieldCode
      || node.bindingRef === fieldCode
      || node.nodeKey === fieldCode
  })
}

function nodeModeAccess(formConfig, fieldCode, mode) {
  const node = nodeFieldConfig(formConfig, fieldCode)
  const rules = parseObject(node?.rulesDocument || node?.rules)
  return rules.extension?.modes?.[mode] || {}
}

function fieldConfig(formConfig, fieldCode) {
  return (formConfig?.fields || []).find(field => field.fieldCode === fieldCode)
}

function summarizeForm(formConfig) {
  const nodes = flattenNodes(formConfig?.nodes)
  return {
    entityFormId: formConfig?.entityFormId || formConfig?.formId,
    formKey: formConfig?.formKey,
    formName: formConfig?.formName,
    formReleaseId: formConfig?.formReleaseId,
    formReleaseVersion: formConfig?.formReleaseVersion,
    effectiveFormReleaseId: formConfig?.effectiveFormReleaseId,
    isReadonly: formConfig?.isReadonly,
    fieldCount: formConfig?.fields?.length || 0,
    nodeCount: nodes.length,
    nodeTypes: [...new Set(
      nodes.map(node => node.nodeType).filter(Boolean)
    )].sort()
  }
}

async function loadTaskCase(task) {
  const detail = await request('GET', `/process-task/detail/${task.taskId}`)
  const progress = await request(
    'GET',
    `/process-instance/${task.processInstanceId}/progress`
  )
  const formConfigs = detail.formConfigs || []
  const progressForms = progress.formConfigs || []
  assert.equal(formConfigs.length, 1, `${task.taskName} 必须只返回一个办理表单`)
  assert.equal(progressForms.length, 1, `${task.taskName} 进度接口必须只返回一个办理表单`)
  assert.equal(
    detail.formConfig?.entityFormId,
    formConfigs[0]?.entityFormId,
    `${task.taskName} formConfig 与 formConfigs 不一致`
  )
  assert.equal(
    progress.formConfig?.formId,
    progressForms[0]?.formId,
    `${task.taskName} 进度表单兼容字段不一致`
  )
  assert.equal(
    progress.formConfig?.formId,
    detail.formConfig?.entityFormId,
    `${task.taskName} 任务详情与进度接口表单不一致`
  )
  assert.ok(
    detail.formConfig?.effectiveFormReleaseId,
    `${task.taskName} 缺少实际生效表单发布版本`
  )
  assert.ok(detail.formConfig?.nodes?.length, `${task.taskName} 未返回递归表单节点`)
  return {
    detail,
    summary: {
      task: {
        taskId: task.taskId,
        taskName: task.taskName,
        processInstanceId: task.processInstanceId,
        entityDataId: task.entityDataId
      },
      detailForm: summarizeForm(detail.formConfig),
      progressForm: summarizeForm(progress.formConfig),
      entityDataKeys: Object.keys(detail.entityData || {}).sort()
    }
  }
}

async function findTodo(processInstanceId, taskName) {
  const todoPage = await request(
    'GET',
    '/process-task/todo?pageNum=1&pageSize=100'
  )
  return pageRecords(todoPage).find(task => (
    task.processInstanceId === processInstanceId
      && (!taskName || task.taskName === taskName)
  ))
}

async function prepareTask(task) {
  if (task?.claimRequired) {
    await request('POST', `/process-task/claim/${task.taskId}`)
  }
  return task
}

async function cleanupRuntimeData() {
  const errors = []
  if (createdProcessInstanceId) {
    try {
      await request(
        'POST',
        '/process-task/withdraw',
        {
          processInstanceId: createdProcessInstanceId,
          reason: 'Codex ZDWREQ 流程表单回归清理'
        }
      )
    } catch (error) {
      try {
        await request(
          'POST',
          `/process-instance/${createdProcessInstanceId}/terminate`,
          { reason: 'Codex ZDWREQ 流程表单回归清理' }
        )
      } catch (terminateError) {
        errors.push({
          action: 'withdrawOrTerminateProcess',
          message: `${error.message}; ${terminateError.message}`,
          status: terminateError.status,
          errorCode: terminateError.errorCode
        })
      }
    }
    createdProcessInstanceId = null
  }
  if (createdParentId) {
    try {
      await request(
        'POST',
        `/entity-data/entity/ZDWREQ/detail/${createdParentId}/delete`
      )
    } catch (error) {
      errors.push({
        action: 'deleteEntityData',
        message: error.message,
        status: error.status,
        errorCode: error.errorCode
      })
    }
    createdParentId = null
  }
  return errors
}

async function main() {
  const credentials = JSON.parse(readFileSync(credentialFile, 'utf8'))
  unlinkSync(credentialFile)
  const login = await request('POST', '/auth/login', credentials)
  token = login.token

  const parent = await request('GET', '/entity/code/ZDWREQ')
  const forms = await request('GET', `/entity-form/entity/${parent.id}`)
  const formList = Array.isArray(forms) ? forms : pageRecords(forms)
  const defaultForm = formList.find(form => form.formKey === 'form001')
  assert.ok(defaultForm?.id, '未找到 ZDWREQ 的 form001')
  assert.ok(parent.processDefinitionId, 'ZDWREQ 未绑定流程')

  const processDefinition = await request(
    'GET',
    `/process/${parent.processDefinitionId}`
  )
  const publishedProcess = await request(
    'POST',
    `/process/${parent.processDefinitionId}/publish`,
    { versionDescription: 'ZDWREQ 最新节点表单与审批权限真实回归' }
  )
  assert.equal(publishedProcess.status, 'PUBLISHED', '流程发布失败')

  const marker = `${Date.now()}-${Math.random().toString(16).slice(2, 8)}`
  const initialData = {
    name: `流程表单回归-${marker}`,
    contactEmail: `flow-${marker}@example.com`,
    plainNote: `审批隐藏字段原值-${marker}`,
    expectedDate: '2026-09-15',
    ownerDept: '2'
  }
  const created = await request('POST', '/entity-data', {
    entityCode: parent.entityCode,
    name: initialData.name,
    formId: defaultForm.id,
    startProcess: true,
    data: initialData
  })
  createdParentId = created.id
  createdProcessInstanceId = created.processInstanceId
  assert.ok(createdParentId, '新增流程测试数据未返回 ID')
  assert.ok(createdProcessInstanceId, '新增流程测试数据未发起流程')
  assert.equal(created.currentTaskName, '组长审批', '流程未进入组长审批')

  const groupTask = await prepareTask(
    await findTodo(createdProcessInstanceId, '组长审批')
  )
  assert.ok(groupTask?.taskId, '未找到 ZDWREQ 的组长审批待办')

  const groupCase = await loadTaskCase(groupTask)
  assert.equal(
    groupCase.summary.detailForm.entityFormId,
    defaultForm.id,
    '组长审批未绑定 form001'
  )
  assert.ok(
    [
      groupCase.summary.detailForm.formReleaseId,
      groupCase.summary.detailForm.effectiveFormReleaseId
    ].includes(defaultForm.activeReleaseId),
    '组长审批未使用 form001 当前已发布版本'
  )

  const groupForm = groupCase.detail.formConfig
  failureContext = {
    groupForm: summarizeForm(groupForm),
    contactEmailNode: nodeFieldConfig(groupForm, 'contactEmail'),
    expectedDateNode: nodeFieldConfig(groupForm, 'expectedDate'),
    plainNoteNode: nodeFieldConfig(groupForm, 'plainNote'),
    ownerDeptNode: nodeFieldConfig(groupForm, 'ownerDept'),
    contactEmailField: fieldConfig(groupForm, 'contactEmail'),
    expectedDateField: fieldConfig(groupForm, 'expectedDate'),
    plainNoteField: fieldConfig(groupForm, 'plainNote'),
    ownerDeptField: fieldConfig(groupForm, 'ownerDept'),
    nodeSamples: flattenNodes(groupForm.nodes).slice(0, 8)
  }
  assert.deepEqual(
    nodeModeAccess(groupForm, 'contactEmail', 'approve'),
    { visible: true, editable: false },
    '联系邮箱的审批模式应为显示且只读'
  )
  assert.deepEqual(
    nodeModeAccess(groupForm, 'expectedDate', 'approve'),
    { visible: true, editable: true },
    '期望日期的审批模式应为显示且可编辑'
  )
  assert.deepEqual(
    nodeModeAccess(groupForm, 'plainNote', 'approve'),
    { visible: false, editable: false },
    '普通备注在审批模式应隐藏'
  )
  assert.equal(
    parseObject(nodeFieldConfig(groupForm, 'ownerDept')?.propsDocument).readonly,
    true,
    '负责部门应为全模式只读'
  )
  assert.equal(
    parseObject(nodeFieldConfig(groupForm, 'status')?.propsDocument).hidden,
    true,
    '状态系统字段应保持隐藏'
  )
  const nodeTypes = new Set(
    flattenNodes(groupForm.nodes).map(node => node.nodeType)
  )
  for (const requiredType of ['SUB_FORM', 'REPEATER', 'ACTION_SLOT']) {
    assert.ok(nodeTypes.has(requiredType), `组长审批运行时缺少 ${requiredType} 节点`)
  }

  const beforeApproval = recordData(
    await request(
      'GET',
      `/entity-data/entity/ZDWREQ/detail/${createdParentId}`
    )
  )
  const tamperedData = {
    contactEmail: `tampered-${marker}@example.com`,
    expectedDate: '2026-10-10',
    plainNote: `审批隐藏字段篡改-${marker}`,
    ownerDept: '999999999'
  }
  await request('POST', '/process-task/complete', {
    taskId: groupTask.taskId,
    action: 'approve',
    actionLabel: '组长审批通过',
    comment: '验证审批模式字段白名单',
    formData: tamperedData
  })
  const afterGroupApproval = recordData(
    await request(
      'GET',
      `/entity-data/entity/ZDWREQ/detail/${createdParentId}`
    )
  )
  assert.equal(
    afterGroupApproval.expectedDate,
    tamperedData.expectedDate,
    '审批可编辑字段未持久化'
  )
  assert.equal(
    afterGroupApproval.contactEmail,
    beforeApproval.contactEmail,
    '审批只读字段被伪造提交修改'
  )
  assert.equal(
    afterGroupApproval.plainNote,
    beforeApproval.plainNote,
    '审批隐藏字段被伪造提交修改'
  )
  assert.equal(
    String(afterGroupApproval.ownerDept),
    String(beforeApproval.ownerDept),
    '全模式只读字段被伪造提交修改'
  )

  const managerTask = await prepareTask(
    await findTodo(createdProcessInstanceId, '经理审批')
  )
  assert.ok(managerTask?.taskId, '组长审批后未进入经理审批')
  const managerCase = await loadTaskCase(managerTask)
  assert.notEqual(
    managerCase.summary.detailForm.entityFormId,
    groupCase.summary.detailForm.entityFormId,
    '经理审批应绑定与组长审批不同的表单'
  )
  assert.ok(
    formList.some(form => form.id === managerCase.summary.detailForm.entityFormId),
    '经理审批绑定的表单不属于 ZDWREQ'
  )

  const runtimeIds = {
    entityDataId: createdParentId,
    processInstanceId: createdProcessInstanceId,
    groupTaskId: groupTask.taskId,
    managerTaskId: managerTask.taskId
  }
  const cleanupErrors = await cleanupRuntimeData()
  assert.deepEqual(cleanupErrors, [], '流程测试数据清理失败')

  const evidence = {
    generatedAt: new Date().toISOString(),
    apiBase,
    result: 'PASS',
    entity: {
      id: parent.id,
      entityCode: parent.entityCode,
      entityName: parent.entityName,
      processDefinitionId: parent.processDefinitionId
    },
    configuredForms: formList.map(form => ({
      id: form.id,
      formKey: form.formKey,
      formName: form.formName,
      activeReleaseId: form.activeReleaseId
    })),
    cases: {
      group: groupCase.summary,
      manager: managerCase.summary
    },
    process: {
      id: processDefinition.id,
      processKey: processDefinition.processKey,
      publishedVersion: publishedProcess.version
    },
    modeAccess: {
      contactEmail: nodeModeAccess(groupForm, 'contactEmail', 'approve'),
      expectedDate: nodeModeAccess(groupForm, 'expectedDate', 'approve'),
      plainNote: nodeModeAccess(groupForm, 'plainNote', 'approve'),
      ownerDeptReadonly: parseObject(
        nodeFieldConfig(groupForm, 'ownerDept')?.propsDocument
      ).readonly,
      statusHidden: parseObject(
        nodeFieldConfig(groupForm, 'status')?.propsDocument
      ).hidden
    },
    submissionGuard: {
      submitted: tamperedData,
      before: {
        contactEmail: beforeApproval.contactEmail,
        expectedDate: beforeApproval.expectedDate,
        plainNote: beforeApproval.plainNote,
        ownerDept: beforeApproval.ownerDept
      },
      after: {
        contactEmail: afterGroupApproval.contactEmail,
        expectedDate: afterGroupApproval.expectedDate,
        plainNote: afterGroupApproval.plainNote,
        ownerDept: afterGroupApproval.ownerDept
      }
    },
    runtime: {
      ...runtimeIds,
      cleaned: true
    }
  }
  const evidencePath = path.join(evidenceDir, 'latest.json')
  writeFileSync(evidencePath, JSON.stringify(evidence, null, 2), {
    mode: 0o600
  })
  console.log(`PASS ${evidencePath}`)
}

main().catch(async error => {
  const cleanupErrors = await cleanupRuntimeData()
  const evidencePath = path.join(evidenceDir, 'latest-failed.json')
  writeFileSync(evidencePath, JSON.stringify({
    generatedAt: new Date().toISOString(),
    apiBase,
    result: 'FAIL',
    error: {
      message: error.message,
      status: error.status,
      errorCode: error.errorCode,
      stack: error.stack
    },
    cleanupErrors,
    context: failureContext
  }, null, 2), { mode: 0o600 })
  console.error(error)
  process.exitCode = 1
})
