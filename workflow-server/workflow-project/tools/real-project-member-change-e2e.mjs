import assert from 'node:assert/strict'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const apiBase = process.env.WORKFLOW_API_BASE || 'http://127.0.0.1:8080/api'
const username = process.env.TEST_USERNAME || 'admin'
const password = process.env.TEST_PASSWORD

assert.ok(password, 'TEST_PASSWORD is required')

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const moduleRoot = path.resolve(scriptDir, '..')
const packagePath = path.join(
  moduleRoot,
  'src/main/resources/project-config/packages/project-f01-f07-v3.wfpack'
)
const evidenceDir = path.resolve(
  moduleRoot,
  '../../docs/project-member-change-e2e'
)
const runId = new Date()
  .toISOString()
  .replace(/[-:.TZ]/g, '')
  .slice(0, 14)

mkdirSync(evidenceDir, { recursive: true })

let token = ''
let currentUser = null
const evidence = {
  runId,
  apiBase,
  package: {},
  handlers: [],
  prerequisites: {},
  flows: {}
}

async function api(method, endpoint, body) {
  const multipart = body instanceof FormData
  const response = await fetch(apiBase + endpoint, {
    method,
    signal: AbortSignal.timeout(45000),
    headers: {
      ...(multipart ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body == null
      ? undefined
      : multipart
        ? body
        : JSON.stringify(body)
  })
  const text = await response.text()
  let payload
  try {
    payload = text ? JSON.parse(text) : null
  } catch {
    throw new Error(
      `${method} ${endpoint} returned non-json: HTTP ${response.status}`
    )
  }
  const applicationSuccess =
    payload?.code === 200
    || payload?.success === true
    || (payload?.code == null && response.ok)
  if (!response.ok || !applicationSuccess) {
    const message = payload?.message || payload?.msg || 'request failed'
    throw new Error(
      `${method} ${endpoint} failed: HTTP ${response.status}, ${message}`
    )
  }
  return payload?.data
}

async function waitFor(check, message, timeout = 45000) {
  const startedAt = Date.now()
  let lastValue
  let lastError
  while (Date.now() - startedAt < timeout) {
    try {
      lastValue = await check()
      if (lastValue) return lastValue
      lastError = null
    } catch (error) {
      lastError = error
    }
    await new Promise(resolve => setTimeout(resolve, 500))
  }
  if (lastError) {
    throw new Error(`${message}: ${lastError.message}`, { cause: lastError })
  }
  throw new Error(`${message}: ${JSON.stringify(lastValue)}`)
}

function rows(value) {
  if (Array.isArray(value)) return value
  return value?.records || value?.list || []
}

function localDate(offsetDays = 0) {
  const value = new Date()
  value.setDate(value.getDate() + offsetDays)
  return [
    value.getFullYear(),
    String(value.getMonth() + 1).padStart(2, '0'),
    String(value.getDate()).padStart(2, '0')
  ].join('-')
}

function localDateTime() {
  const value = new Date()
  return `${localDate(0)} ${[
    String(value.getHours()).padStart(2, '0'),
    String(value.getMinutes()).padStart(2, '0'),
    String(value.getSeconds()).padStart(2, '0')
  ].join(':')}`
}

async function listEntity(entityCode, filters = {}) {
  const params = new URLSearchParams({
    pageNum: '1',
    pageSize: '500'
  })
  for (const [key, value] of Object.entries(filters)) {
    if (value != null && value !== '') {
      params.set(key, String(value))
    }
  }
  return rows(await api(
    'GET',
    `/entity-data/entity/${entityCode}?${params.toString()}`
  ))
}

async function detail(entityCode, id) {
  return api('GET', `/entity-data/entity/${entityCode}/detail/${id}`)
}

async function createEntityData(
  entityCode,
  name,
  data,
  startProcess = false
) {
  return api('POST', '/entity-data', {
    entityCode,
    name,
    title: name,
    submitterId: currentUser.id,
    submitterName:
      currentUser.nickname
      || currentUser.username
      || username,
    deptId: currentUser.deptId || '2',
    startProcess,
    data
  })
}

async function updateEntityData(entityCode, id, data) {
  return api(
    'POST',
    `/entity-data/entity/${entityCode}/detail/${id}/update`,
    { data }
  )
}

async function prepareActionHandlers(bootstrap = false) {
  const plans = [
    {
      beanName: 'validateProjectMemberChangeHandler',
      displayName: '项目成员变更跨实体门禁',
      description: '校验成员、投入、权限和角色交接关系。',
      visibilityScope: 'ENTITY'
    },
    {
      beanName: 'captureProjectMemberManagerReviewHandler',
      displayName: '项目成员变更经理复核记录',
      description: '记录项目经理节点完成检查点。',
      visibilityScope: 'ENTITY'
    },
    {
      beanName: 'recordProjectMemberDecisionHandler',
      displayName: '项目成员变更决策连线记录',
      description: '记录最终批准顺序流的决策轨迹。',
      visibilityScope: 'ENTITY'
    },
    {
      beanName: 'applyProjectMemberChangeHandler',
      displayName: '项目成员变更批准后生效',
      description: '生效成员、投入、权限和角色交接结果。',
      visibilityScope: 'ENTITY'
    },
    {
      beanName: 'projectLifecycleAuditHandler',
      displayName: '项目生命周期全局审计',
      description: '记录可跨实体复用的项目生命周期审计摘要。',
      visibilityScope: 'GLOBAL'
    }
  ]
  const configs = await api('GET', '/process-action-handlers/configs')
  for (const plan of plans) {
    const current = configs.find(item => item.beanName === plan.beanName)
    assert.ok(current?.available, `动作处理器未注册: ${plan.beanName}`)
    const visibilityScope = bootstrap
      ? 'GLOBAL'
      : plan.visibilityScope
    const entityCodes = visibilityScope === 'ENTITY'
      ? [...new Set([
          ...(current?.entityCodes || []),
          'project_member_change_request'
        ])]
      : []
    const saved = await api(
      'POST',
      `/process-action-handlers/configs/${encodeURIComponent(plan.beanName)}`,
      {
        displayName: current?.displayName || plan.displayName,
        description: current?.description || plan.description,
        visibilityScope,
        entityCodes,
        enabled: true
      }
    )
    evidence.handlers.push({
      beanName: saved.beanName,
      definitionId: saved.definitionId,
      visibilityScope: saved.visibilityScope,
      entityCodes: saved.entityCodes || [],
      enabled: saved.enabled,
      available: saved.available,
      phase: bootstrap ? 'BOOTSTRAP_GLOBAL' : 'FINAL_SCOPE'
    })
  }

  const notification = configs.find(
    item => item.beanName === 'sendNotificationHandler'
  )
  assert.ok(
    notification?.configured && notification?.available && notification?.enabled,
    'sendNotificationHandler 必须已配置并启用'
  )
}

async function uploadAndAnalyzePackage() {
  const form = new FormData()
  form.append(
    'file',
    new Blob(
      [readFileSync(packagePath)],
      { type: 'application/octet-stream' }
    ),
    path.basename(packagePath)
  )
  form.append('sourceEnvironment', 'codex-f07-e2e')

  const imported = await api('POST', '/config-migration/imports', form)
  const importId =
    imported?.id
    || imported?.importPackageId
    || imported?.packageId
  assert.ok(importId, '配置包上传结果缺少导入批次ID')

  const analysis = imported?.status === 'PUBLISHED'
    ? { status: 'REUSED_PUBLISHED' }
    : await api(
        'POST',
        `/config-migration/imports/${importId}/analyze`
      )
  const items = await api(
    'GET',
    `/config-migration/imports/${importId}/items`
  )
  const entityItem = items.find(
    item => item.businessKey === 'project_member_change_request'
  )
  const memberEntityItem = items.find(
    item => item.businessKey === 'project_member'
  )
  const processItem = items.find(
    item => item.businessKey === 'project_member_change_process'
  )
  assert.ok(entityItem, '导入包缺少 F07 实体')
  assert.ok(memberEntityItem, '导入包缺少项目成员实体')
  assert.ok(processItem, '导入包缺少 F07 流程')
  assert.ok(
    [entityItem, memberEntityItem, processItem].every(
      item => item.mappingStatus !== 'UNRESOLVED'
    ),
    'F07 相关实体或流程存在未解析依赖'
  )
  evidence.package = {
    importId,
    packageFile: path.basename(packagePath),
    analysisStatus:
      analysis?.status
      || analysis?.validationStatus
      || 'ANALYZED',
    selectedItems: [
      entityItem,
      memberEntityItem,
      processItem
    ].map(item => ({
      id: item.id,
      assetType: item.assetType,
      businessKey: item.businessKey,
      comparisonStatus: item.comparisonStatus,
      mappingStatus: item.mappingStatus
    })),
    publishSteps: []
  }
  return {
    importId,
    items,
    entityItem,
    memberEntityItem,
    processItem
  }
}

async function publishF07Package(packageImport) {
  const published = await api(
    'POST',
    `/config-migration/imports/${packageImport.importId}/publish`
  )
  evidence.package.publishSteps.push({
    step: 'ATOMIC_PACKAGE',
    businessKeys: packageImport.items.map(item => item.businessKey),
    status:
      published?.status
      || published?.publishStatus
      || 'PUBLISHED'
  })
}

async function waitForMemberChangeEntity() {
  await waitFor(
    async () => {
      const entity = await api(
        'GET',
        '/entity/code/project_member_change_request'
      )
      return entity?.entityCode === 'project_member_change_request'
        ? entity
        : null
    },
    'F07 实体发布后不可用',
    60000
  )
}

function allocationByUser(members) {
  const activeStatuses = new Set([
    'PENDING_JOIN',
    'ACTIVE',
    'SUSPENDED',
    'PENDING_LEAVE'
  ])
  const totals = new Map()
  for (const member of members) {
    if (!activeStatuses.has(member.status)) continue
    const userId = String(member.data?.user_id || '')
    const allocation = Number(member.data?.allocation_percentage || 0)
    totals.set(userId, (totals.get(userId) || 0) + allocation)
  }
  return totals
}

async function createPrerequisites() {
  for (const entityCode of [
    'project',
    'project_member',
    'project_role_assignment',
    'project_role_catalog'
  ]) {
    const definition = await api('GET', `/entity/code/${entityCode}`)
    assert.equal(
      definition?.entityCode,
      entityCode,
      `缺少前置实体 ${entityCode}`
    )
  }

  const users = (await api('GET', '/system/user/list'))
    .filter(user => user.status === '0')
  const existingMembers = await listEntity('project_member')
  const allocations = allocationByUser(existingMembers)
  const availableUsers = users
    .map(user => ({
      user,
      available: 100 - (allocations.get(String(user.id)) || 0)
    }))
    .filter(item => item.available >= 1)
    .sort((left, right) => right.available - left.available)
  assert.ok(
    availableUsers.length >= 2,
    '真实验证至少需要两名仍有可用投入比例的启用用户'
  )

  const joinCandidate = availableUsers[0]
  const handoverCandidate = availableUsers.find(
    item => item.user.id !== joinCandidate.user.id
  )
  assert.ok(handoverCandidate, '缺少不同于加入人员的交接用户')

  const projectName = `F07统一客户运营平台成员治理-${runId}`
  const project = await createEntityData('project', projectName, {
    project_type: 'NEW_SYSTEM',
    project_level: 'ENTERPRISE',
    sponsor_dept_id: currentUser.deptId || '2',
    project_sponsor_id: currentUser.id,
    business_owner_id: currentUser.id,
    project_manager_id: currentUser.id,
    product_owner_id: currentUser.id,
    project_background: '模拟统一客户运营平台建设中的跨部门成员加入、生产权限审批和关键角色移交。',
    project_objective: '验证项目成员变更审批、条件路由、投入约束、权限门禁和角色交接闭环。',
    scope_in: '项目成员、项目角色、环境权限和审批审计。',
    scope_out: '薪酬、采购与外部合同管理。',
    expected_deliverables: '生效成员记录、审批轨迹、权限评审结果和角色移交记录。',
    success_metrics: '成员变更审批与生效数据一致，角色移交无悬空责任。',
    planned_start_date: localDate(0),
    planned_end_date: localDate(180),
    current_baseline_version: 1,
    priority: 'P1',
    risk_level: 'HIGH',
    cross_system_flag: true,
    security_involved_flag: true,
    security_requirements: '生产操作权限最小授权并留存审批轨迹。',
    data_involved_flag: true,
    data_scope: '客户主数据和运营标签。',
    data_classification: 'SENSITIVE',
    completion_percentage: 0,
    initialization_completed_flag: true,
    applicant_id: currentUser.id,
    applicant_dept_id: currentUser.deptId || '2',
    version: 1
  })
  await updateEntityData('project', project.id, { status: 'APPROVED' })
  const approvedProject = await detail('project', project.id)
  assert.equal(approvedProject.status, 'APPROVED')

  const handoverAllocation = Math.min(10, handoverCandidate.available)
  const handoverMember = await createEntityData(
    'project_member',
    `F07交接成员-${runId}`,
    {
      project_id: project.id,
      user_id: handoverCandidate.user.id,
      source_dept_id:
        handoverCandidate.user.deptId
        || currentUser.deptId
        || '2',
      employment_type: 'INTERNAL',
      join_date: localDate(0),
      allocation_percentage: handoverAllocation,
      join_reason: '作为关键角色交接接收人参与真实审批验证。',
      account_required_flag: false,
      environment_access_required_flag: false,
      environment_scope: [],
      access_revoked_flag: false,
      handover_completed_flag: false,
      source_process: 'F07_E2E_PREREQUISITE'
    }
  )
  await updateEntityData(
    'project_member',
    handoverMember.id,
    { status: 'ACTIVE' }
  )

  const roleCatalogs = await listEntity('project_role_catalog')
  const roleCatalog =
    roleCatalogs.find(item => item.data?.role_code === 'PROJECT_MANAGER')
    || roleCatalogs.find(item => item.status === 'ACTIVE')
  assert.ok(roleCatalog, '缺少可用项目角色目录')

  evidence.prerequisites = {
    projectId: project.id,
    projectCode: approvedProject.code,
    projectStatus: approvedProject.status,
    joinUserId: joinCandidate.user.id,
    joinAvailableAllocation: joinCandidate.available,
    handoverUserId: handoverCandidate.user.id,
    handoverMemberId: handoverMember.id,
    handoverAllocation,
    roleCatalogId: roleCatalog.id,
    roleCode: roleCatalog.data?.role_code
  }
  return {
    project: approvedProject,
    joinCandidate,
    handoverMember: await detail('project_member', handoverMember.id),
    roleCatalog
  }
}

async function currentTask(processInstanceId, expectedName) {
  return waitFor(async () => {
    const todo = await api(
      'GET',
      '/process-task/todo?pageNum=1&pageSize=500'
    )
    return rows(todo).find(
      item =>
        item.processInstanceId === processInstanceId
        && item.taskName === expectedName
    )
  }, `未找到待办 ${expectedName}`)
}

async function approveProcess(processInstanceId, taskNames) {
  const completed = []
  for (const taskName of taskNames) {
    const task = await currentTask(processInstanceId, taskName)
    await api('POST', '/process-task/complete', {
      taskId: task.taskId,
      action: 'approve',
      actionLabel: '通过',
      comment: `F07真实验收：${taskName}通过`
    })
    completed.push({
      taskId: task.taskId,
      taskName: task.taskName
    })
  }
  const progress = await waitFor(async () => {
    const value = await api(
      'GET',
      `/process-instance/${processInstanceId}/progress`
    )
    return ['COMPLETED', 'ENDED'].includes(value.status)
      ? value
      : null
  }, `流程 ${processInstanceId} 未正常结束`)
  return { completed, progress }
}

function assertConnectedBpmn(progress) {
  const xml = progress.bpmnXml || ''
  const sequenceFlowCount =
    (xml.match(/<(?:bpmn:)?sequenceFlow\b/g) || []).length
  const edgeCount =
    (xml.match(/<(?:bpmndi:)?BPMNEdge\b/g) || []).length
  const waypointCount =
    (xml.match(/<(?:di:)?waypoint\b/g) || []).length
  assert.equal(sequenceFlowCount, 20)
  assert.equal(edgeCount, 20)
  assert.ok(waypointCount >= 40)
  return { sequenceFlowCount, edgeCount, waypointCount }
}

async function waitForActionExecutions(processInstanceId) {
  const expectedHandlers = new Set([
    'validateProjectMemberChangeHandler',
    'captureProjectMemberManagerReviewHandler',
    'recordProjectMemberDecisionHandler',
    'applyProjectMemberChangeHandler',
    'projectLifecycleAuditHandler'
  ])
  const executions = await waitFor(async () => {
    const values = await api(
      'GET',
      `/process-action-executions/process/${processInstanceId}`
    )
    const successfulHandlers = new Set(
      values
        .filter(item => item.status === 'SUCCESS')
        .map(item => item.handlerName)
    )
    return [...expectedHandlers].every(
      handler => successfulHandlers.has(handler)
    )
      ? values
      : null
  }, 'F07 自定义动作未全部成功', 90000)
  return executions.map(item => ({
    id: item.id,
    actionName: item.actionName,
    handlerName: item.handlerName,
    scopeType: item.scopeType,
    elementId: item.elementId,
    triggerTiming: item.triggerTiming,
    status: item.status,
    result: item.result
  }))
}

function assertConditionalRoute(progress) {
  const executed = new Set(progress.executedSequenceFlows || [])
  for (const flowId of [
    'flow_need_access',
    'flow_need_security',
    'flow_pmo_approve'
  ]) {
    assert.ok(executed.has(flowId), `未执行条件连线 ${flowId}`)
  }
  assert.ok(!executed.has('flow_skip_access'))
  assert.ok(!executed.has('flow_skip_security'))
}

async function runJoin(prerequisites) {
  const allocation = Math.min(
    25,
    prerequisites.joinCandidate.available
  )
  const request = await createEntityData(
    'project_member_change_request',
    `F07生产权限成员加入-${runId}`,
    {
      operation_type: 'JOIN',
      project_id: prerequisites.project.id,
      target_user_id: prerequisites.joinCandidate.user.id,
      source_dept_id:
        prerequisites.joinCandidate.user.deptId
        || currentUser.deptId
        || '2',
      employment_type: 'INTERNAL',
      effective_date: localDate(1),
      planned_leave_date: null,
      new_allocation_percentage: allocation,
      change_reason: '统一客户运营平台进入联调阶段，需要新增开发负责人并申请生产操作权限。',
      account_required_flag: true,
      environment_access_required_flag: true,
      environment_scope: ['DEV', 'TEST', 'UAT', 'PROD_OPERATE'],
      sensitive_access_flag: true,
      handover_required_flag: false,
      access_review_required_flag: true,
      security_review_required_flag: true,
      transferred_role_count: 0,
      applicant_id: currentUser.id,
      applicant_dept_id: currentUser.deptId || '2',
      version: 1,
      submitted_at: localDateTime()
    },
    true
  )
  assert.ok(request.processInstanceId, 'F07 JOIN 未发起流程')

  const initialProgress = await waitFor(async () => {
    const value = await api(
      'GET',
      `/process-instance/${request.processInstanceId}/progress`
    )
    return value.activeNodes?.includes('project_manager_review')
      ? value
      : null
  }, 'F07 JOIN 未进入项目经理审核')
  const formConfig =
    initialProgress.formConfig
    || initialProgress.formConfigs?.[0]
  assert.equal(
    formConfig?.customComponent,
    'ProjectMemberChangeForm'
  )
  const bpmn = assertConnectedBpmn(initialProgress)

  const approval = await approveProcess(request.processInstanceId, [
    '项目经理审核',
    '人员部门负责人审核',
    '系统负责人权限审核',
    '安全负责人审核',
    'PMO最终审批'
  ])
  assertConditionalRoute(approval.progress)

  const effectiveRequest = await waitFor(async () => {
    const value = await detail(
      'project_member_change_request',
      request.id
    )
    return value.status === 'EFFECTIVE'
      && value.data?.effective_member_id
      ? value
      : null
  }, 'F07 JOIN 批准后未生效', 90000)
  const member = await detail(
    'project_member',
    effectiveRequest.data.effective_member_id
  )
  assert.equal(member.status, 'ACTIVE')
  assert.equal(
    member.data?.user_id,
    prerequisites.joinCandidate.user.id
  )
  assert.equal(member.data?.employment_type, 'INTERNAL')
  assert.equal(Number(member.data?.allocation_percentage), allocation)

  const executions = await waitForActionExecutions(
    request.processInstanceId
  )
  evidence.flows.JOIN = {
    requestId: request.id,
    processInstanceId: request.processInstanceId,
    completedTasks: approval.completed,
    executedSequenceFlows: approval.progress.executedSequenceFlows,
    customComponent: formConfig.customComponent,
    bpmn,
    finalRequestStatus: effectiveRequest.status,
    effectiveMemberId: member.id,
    memberStatus: member.status,
    allocation,
    accessReviewRequired:
      effectiveRequest.data?.access_review_required_flag,
    securityReviewRequired:
      effectiveRequest.data?.security_review_required_flag,
    implementationResult:
      effectiveRequest.data?.implementation_result,
    actionExecutions: executions
  }
  return member
}

async function createBlockingRole(
  prerequisites,
  member
) {
  const role = await createEntityData(
    'project_role_assignment',
    `F07关键项目经理角色-${runId}`,
    {
      project_id: prerequisites.project.id,
      member_id: member.id,
      user_id: member.data?.user_id,
      role_catalog_id: prerequisites.roleCatalog.id,
      role_code:
        prerequisites.roleCatalog.data?.role_code
        || 'PROJECT_MANAGER',
      role_scope: 'PROJECT',
      primary_flag: true,
      responsibility_description: '负责统一客户运营平台一期交付，离场时必须完成责任交接。',
      effective_from: localDate(1),
      predecessor_assignment_id: null,
      handover_required_flag: false,
      handover_completed_flag: false,
      source_process: 'F07_E2E_PREREQUISITE'
    }
  )
  await updateEntityData(
    'project_role_assignment',
    role.id,
    { status: 'ACTIVE' }
  )
  return detail('project_role_assignment', role.id)
}

async function runLeave(
  prerequisites,
  member,
  role
) {
  const request = await createEntityData(
    'project_member_change_request',
    `F07关键成员退出交接-${runId}`,
    {
      operation_type: 'LEAVE',
      project_id: prerequisites.project.id,
      project_member_id: member.id,
      target_user_id: member.data?.user_id,
      effective_date: localDate(2),
      change_reason: '成员转入其他重点项目，需完成项目经理职责、账号和生产权限交接。',
      account_required_flag: false,
      environment_access_required_flag: false,
      environment_scope: [],
      sensitive_access_flag: true,
      handover_required_flag: true,
      access_review_required_flag: true,
      security_review_required_flag: true,
      handover_member_id: prerequisites.handoverMember.id,
      handover_description: '移交项目计划、风险台账、发布窗口、生产权限审批记录及未结事项。',
      permission_revoke_deadline: localDate(3),
      transferred_role_count: 0,
      applicant_id: currentUser.id,
      applicant_dept_id: currentUser.deptId || '2',
      version: 1,
      submitted_at: localDateTime()
    },
    true
  )
  assert.ok(request.processInstanceId, 'F07 LEAVE 未发起流程')

  const approval = await approveProcess(request.processInstanceId, [
    '项目经理审核',
    '人员部门负责人审核',
    '系统负责人权限审核',
    '安全负责人审核',
    'PMO最终审批'
  ])
  assertConditionalRoute(approval.progress)
  const bpmn = assertConnectedBpmn(approval.progress)

  const effectiveRequest = await waitFor(async () => {
    const value = await detail(
      'project_member_change_request',
      request.id
    )
    return value.status === 'EFFECTIVE'
      && Number(value.data?.transferred_role_count) === 1
      ? value
      : null
  }, 'F07 LEAVE 批准后未完成角色交接', 90000)

  const leftMember = await detail('project_member', member.id)
  assert.equal(leftMember.status, 'LEFT')
  assert.equal(Number(leftMember.data?.allocation_percentage), 0)
  assert.equal(leftMember.data?.access_revoked_flag, true)
  assert.equal(leftMember.data?.handover_completed_flag, true)

  const revokedRole = await detail(
    'project_role_assignment',
    role.id
  )
  assert.equal(revokedRole.status, 'REVOKED')
  const handoverRoles = await listEntity(
    'project_role_assignment',
    {
      project_id: prerequisites.project.id,
      member_id: prerequisites.handoverMember.id
    }
  )
  const replacement = handoverRoles.find(
    item =>
      item.status === 'ACTIVE'
      && item.data?.predecessor_assignment_id === role.id
  )
  assert.ok(replacement, '未生成交接后的有效角色')

  const executions = await waitForActionExecutions(
    request.processInstanceId
  )
  evidence.flows.LEAVE = {
    requestId: request.id,
    processInstanceId: request.processInstanceId,
    completedTasks: approval.completed,
    executedSequenceFlows: approval.progress.executedSequenceFlows,
    bpmn,
    finalRequestStatus: effectiveRequest.status,
    leftMemberId: leftMember.id,
    leftMemberStatus: leftMember.status,
    revokedRoleId: revokedRole.id,
    revokedRoleStatus: revokedRole.status,
    replacementRoleId: replacement.id,
    replacementMemberId: replacement.data?.member_id,
    transferredRoleCount:
      effectiveRequest.data?.transferred_role_count,
    implementationResult:
      effectiveRequest.data?.implementation_result,
    actionExecutions: executions
  }
}

async function main() {
  const login = await api(
    'POST',
    '/auth/login',
    { username, password }
  )
  token = login.token
  currentUser = await api('GET', '/auth/current')
  assert.ok(currentUser?.id, '登录用户上下文缺少用户ID')

  await prepareActionHandlers(true)
  const packageImport = await uploadAndAnalyzePackage()
  await publishF07Package(packageImport)
  await waitForMemberChangeEntity()
  await prepareActionHandlers(false)
  const prerequisites = await createPrerequisites()
  const joinedMember = await runJoin(prerequisites)
  const role = await createBlockingRole(
    prerequisites,
    joinedMember
  )
  await runLeave(prerequisites, joinedMember, role)

  evidence.result = 'PASS'
  evidence.conclusion =
    'F07 JOIN/LEAVE 均完成真实审批；条件会签、完整BPMN连线、自定义表单、节点/连线/流程/全局动作、成员生效及角色交接全部通过。'
  const evidencePath = path.join(
    evidenceDir,
    `project-member-change-${runId}.json`
  )
  writeFileSync(
    evidencePath,
    JSON.stringify(evidence, null, 2),
    { mode: 0o600 }
  )
  console.log(JSON.stringify({
    result: evidence.result,
    evidencePath,
    packageImportId: evidence.package.importId,
    projectId: evidence.prerequisites.projectId,
    joinProcessInstanceId:
      evidence.flows.JOIN.processInstanceId,
    leaveProcessInstanceId:
      evidence.flows.LEAVE.processInstanceId,
    joinTasks:
      evidence.flows.JOIN.completedTasks.length,
    leaveTasks:
      evidence.flows.LEAVE.completedTasks.length,
    bpmnEdges:
      evidence.flows.JOIN.bpmn.edgeCount,
    transferredRoleCount:
      evidence.flows.LEAVE.transferredRoleCount
  }, null, 2))
}

main().catch(error => {
  console.error(
    `project member change e2e failed: ${error.message}`
  )
  process.exit(1)
})
