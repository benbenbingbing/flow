import assert from 'node:assert/strict'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const apiBase = process.env.WORKFLOW_API_BASE || 'http://localhost:8080/api'
const username = process.env.TEST_USERNAME || 'admin'
const password = process.env.TEST_PASSWORD || '123456'
const runId = new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)
const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const evidenceDir = path.resolve(scriptDir, '../../../docs/project-governance-e2e')

mkdirSync(evidenceDir, { recursive: true })

let token = ''
const evidence = {
  runId,
  apiBase,
  flows: {},
  crossEntityChecks: {}
}

async function api(method, endpoint, body) {
  const response = await fetch(apiBase + endpoint, {
    method,
    signal: AbortSignal.timeout(30000),
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
    throw new Error(`${method} ${endpoint} returned non-json: HTTP ${response.status}`)
  }
  if (!response.ok || payload?.code !== 200) {
    throw new Error(`${method} ${endpoint} failed: HTTP ${response.status}`)
  }
  return payload.data
}

async function waitFor(check, message, timeout = 30000) {
  const startedAt = Date.now()
  let lastValue
  while (Date.now() - startedAt < timeout) {
    lastValue = await check()
    if (lastValue) return lastValue
    await new Promise(resolve => setTimeout(resolve, 400))
  }
  throw new Error(`${message}: ${JSON.stringify(lastValue)}`)
}

function rows(value) {
  if (Array.isArray(value)) return value
  return value?.records || value?.list || []
}

async function listEntity(entityCode, params = '') {
  const suffix = params ? `&${params}` : ''
  const result = await api(
    'GET',
    `/entity-data/entity/${entityCode}?pageNum=1&pageSize=200${suffix}`
  )
  return rows(result)
}

async function detail(entityCode, id) {
  return api('GET', `/entity-data/entity/${entityCode}/detail/${id}`)
}

async function createEntityData(entityCode, name, data, startProcess = false) {
  return api('POST', '/entity-data', {
    entityCode,
    name,
    title: name,
    submitterId: '1',
    submitterName: '超级管理员',
    deptId: '2',
    startProcess,
    data
  })
}

async function updateEntityData(entityCode, id, formData) {
  return api(
    'POST',
    `/entity-data/entity/${entityCode}/detail/${id}/update`,
    formData
  )
}

async function approveProcess(processInstanceId, expectedTaskNames) {
  const completed = []
  for (const expectedName of expectedTaskNames) {
    const todo = await waitFor(async () => {
      const result = await api('GET', '/process-task/todo?pageNum=1&pageSize=200')
      return rows(result).find(item => item.processInstanceId === processInstanceId)
    }, `未找到流程 ${processInstanceId} 的待办 ${expectedName}`)

    assert.equal(todo.taskName, expectedName)
    await api('POST', '/process-task/complete', {
      taskId: todo.taskId,
      action: 'approve',
      actionLabel: '通过',
      comment: `项目治理真实验收：${expectedName}通过`
    })
    completed.push({ taskId: todo.taskId, taskName: todo.taskName })
  }

  const progress = await waitFor(async () => {
    const current = await api('GET', `/process-instance/${processInstanceId}/progress`)
    return ['COMPLETED', 'ENDED'].includes(current.status) ? current : null
  }, `流程 ${processInstanceId} 未正常结束`)

  return { completed, progress }
}

async function main() {
  const login = await api('POST', '/auth/login', { username, password })
  token = login.token

  const requirements = await listEntity('requirement')
  const existingRequirementLinks = await listEntity('requirement_project_link')
  const requirementCandidates = requirements
    .filter(item => ['BACKLOG', 'PLANNED'].includes(item.status))
    .map(item => {
      const allocatedPercentage = existingRequirementLinks
        .filter(link =>
          link.data?.requirement_id === item.id
          && link.status !== 'CANCELLED'
        )
        .reduce(
          (total, link) =>
            total + Number(link.data?.allocation_percentage || 0),
          0
        )
      return {
        requirement: item,
        allocatedPercentage,
        availablePercentage: 100 - allocatedPercentage
      }
    })
    .filter(item => item.availablePercentage > 0)
    .sort((left, right) => {
      const leftSplit = left.allocatedPercentage > 0 ? 1 : 0
      const rightSplit = right.allocatedPercentage > 0 ? 1 : 0
      return rightSplit - leftSplit
        || right.availablePercentage - left.availablePercentage
    })
  const requirementScope = requirementCandidates[0]
  assert.ok(requirementScope, '至少需要一条仍有可分配比例的 BACKLOG/PLANNED 需求')
  const requirement = requirementScope.requirement
  const allocationPercentage = Math.min(
    60,
    requirementScope.availablePercentage
  )

  const systems = await listEntity('system_asset')
  const sourceSystem = systems.find(item => item.status !== 'RETIRED')
  assert.ok(sourceSystem, '至少需要一个未退役系统')

  const projectName = `统一客户运营平台建设-${runId}`
  const project = await createEntityData(
    'project',
    projectName,
    {
      project_type: 'NEW_SYSTEM',
      project_level: 'ENTERPRISE',
      sponsor_dept_id: '2',
      project_sponsor_id: '1',
      business_owner_id: '1',
      project_manager_id: '1',
      product_owner_id: '1',
      project_background: '统一客户运营平台需要通过项目治理流程完成跨系统、数据和安全评审。',
      project_objective: '建立客户画像、标签和统一运营能力，并形成可审计的项目治理关系。',
      scope_in: '客户主数据、标签服务、画像查询、系统集成和权限审计。',
      scope_out: '线下营销执行和非客户域系统改造。',
      expected_deliverables: '项目基线、客户画像平台、数据接口、安全方案和上线交付物。',
      success_metrics: '客户匹配准确率不低于98%，核心接口可用性不低于99.9%。',
      planned_start_date: '2026-08-01',
      planned_end_date: '2026-12-31',
      current_baseline_version: 0,
      priority: 'P1',
      risk_level: 'HIGH',
      cross_system_flag: true,
      security_involved_flag: true,
      security_requirements: '敏感客户数据加密、脱敏、最小权限和完整审计。',
      data_involved_flag: true,
      data_scope: '客户主数据、交易摘要、服务记录和标签数据。',
      data_classification: 'SENSITIVE',
      completion_percentage: 0,
      initialization_completed_flag: false,
      applicant_id: '1',
      applicant_dept_id: '2',
      version: 1,
      initial_requirement_links: [
        {
          requirement_id: requirement.id,
          relation_role: 'PRIMARY',
          delivery_scope: '一期交付客户主数据、核心标签和客户360视图。',
          allocation_percentage: allocationPercentage,
          planned_start_date: '2026-08-01',
          planned_end_date: '2026-11-30',
          completion_percentage: 0,
          status: 'PROPOSED'
        }
      ],
      initial_system_links: [
        {
          system_id: sourceSystem.id,
          construction_mode: 'NEW_BUILD',
          relation_reason: '作为统一客户运营平台的核心客户画像系统。',
          affected_modules: '客户主数据、标签服务、画像查询。',
          interface_impact: '新增 CRM、订单、客服数据接入接口。',
          data_impact: '新增敏感客户数据汇聚和标签加工。',
          deployment_impact: '新增生产集群和发布流水线。',
          target_system_version: '1.0.0',
          risk_level: 'HIGH',
          planned_start_date: '2026-08-01',
          planned_end_date: '2026-12-15',
          status: 'PROPOSED'
        }
      ]
    },
    true
  )
  assert.ok(project.processInstanceId, 'F03 应成功发起流程实例')

  const f03Approval = await approveProcess(project.processInstanceId, [
    '业务负责人审核',
    'PMO立项评审',
    '关联系统负责人会签',
    '企业架构评审',
    '安全专项评审',
    '数据专项评审',
    '项目发起人最终批准'
  ])

  const approvedProject = await waitFor(async () => {
    const current = await detail('project', project.id)
    return current.status === 'APPROVED'
      && current.data?.initialization_completed_flag
      ? current
      : null
  }, 'F03 批准后项目治理关系未完成初始化', 45000)

  const requirementLinks = await listEntity(
    'requirement_project_link',
    `project_id=${encodeURIComponent(project.id)}`
  )
  const systemLinks = await listEntity(
    'project_system_link',
    `project_id=${encodeURIComponent(project.id)}`
  )
  const members = await listEntity(
    'project_member',
    `project_id=${encodeURIComponent(project.id)}`
  )
  const roleAssignments = await listEntity(
    'project_role_assignment',
    `project_id=${encodeURIComponent(project.id)}`
  )

  assert.equal(requirementLinks.length, 1)
  assert.equal(requirementLinks[0].status, 'APPROVED')
  assert.equal(systemLinks.length, 1)
  assert.equal(systemLinks[0].status, 'ACTIVE')
  assert.ok(members.length >= 1)
  assert.ok(members.every(item => item.status === 'ACTIVE'))
  assert.equal(roleAssignments.filter(item => item.status === 'ACTIVE').length, 3)

  evidence.flows.F03 = {
    projectId: project.id,
    projectCode: approvedProject.code,
    processInstanceId: project.processInstanceId,
    completedTasks: f03Approval.completed,
    finalStatus: approvedProject.status,
    initializationSummary: approvedProject.data.initialization_summary,
    requirementLinkIds: requirementLinks.map(item => item.id),
    requirementAllocation: {
      requirementId: requirement.id,
      allocatedBefore: requirementScope.allocatedPercentage,
      allocatedByProject: allocationPercentage,
      allocatedAfter:
        requirementScope.allocatedPercentage + allocationPercentage
    },
    systemLinkIds: systemLinks.map(item => item.id),
    memberIds: members.map(item => item.id),
    roleAssignmentIds: roleAssignments.map(item => item.id)
  }

  const targetSystem = await createEntityData(
    'system_asset',
    `客户运营规则引擎-${runId}`,
    {
      asset_status: 'PROPOSED',
      system_abbreviation: `CORE${runId.slice(-4)}`,
      system_type: 'BUSINESS_APPLICATION',
      business_domain: '客户运营',
      owner_dept_id: '2',
      system_owner_id: '1',
      technical_owner_id: '1',
      ops_owner_id: '1',
      criticality_level: 'L2',
      data_classification: 'SENSITIVE',
      security_level: 'LEVEL_3',
      deployment_mode: 'PRIVATE_CLOUD',
      availability_target: 99.9,
      rto_minutes: 60,
      rpo_minutes: 15,
      planned_go_live_date: '2026-12-20'
    }
  )
  const primaryMember = members[0]

  const change = await createEntityData(
    'project_system_change_request',
    `项目系统新增申请-${runId}`,
    {
      operation_type: 'ADD',
      project_id: project.id,
      system_id: targetSystem.id,
      change_reason: '一期新增客户运营规则引擎，承接实时分群和触达规则计算。',
      proposed_change: '将规则引擎纳入项目范围并建立项目内系统与技术责任人。',
      construction_mode: 'INTEGRATION',
      relation_reason: '支撑客户分群和实时运营规则。',
      affected_modules: '客户分群、触达编排、规则计算。',
      interface_impact: '新增画像平台和消息平台接口。',
      data_impact: '读取敏感客户标签与运营规则。',
      deployment_impact: '新增生产服务和独立扩缩容策略。',
      target_system_version: '1.0.0',
      new_project_system_lead_id: primaryMember.id,
      new_technical_lead_id: primaryMember.id,
      security_involved_flag: true,
      data_involved_flag: true,
      risk_level: 'HIGH',
      rollback_plan: '停止新接口流量，回滚部署版本并删除新增路由配置。',
      schedule_impact_days: 5,
      rebaseline_required_flag: true,
      planned_start_date: '2026-09-01',
      planned_end_date: '2026-12-10',
      planned_effective_date: '2026-09-01',
      applicant_id: '1',
      applicant_dept_id: '2',
      version: 1
    },
    true
  )
  assert.ok(change.processInstanceId, 'F06 ADD 应成功发起流程实例')

  const f06Approval = await approveProcess(change.processInstanceId, [
    '项目经理确认',
    '系统负责人审批',
    '技术负责人审批',
    '架构影响评审',
    '安全影响评审',
    '数据影响评审',
    'PMO纳入基线审批'
  ])

  const effectiveChange = await waitFor(async () => {
    const current = await detail('project_system_change_request', change.id)
    return current.status === 'EFFECTIVE' && current.data?.effective_link_id
      ? current
      : null
  }, 'F06 ADD 批准后项目系统关系未生效', 45000)
  const effectiveLink = await detail(
    'project_system_link',
    effectiveChange.data.effective_link_id
  )
  assert.equal(effectiveLink.status, 'ACTIVE')
  assert.equal(effectiveLink.data.project_id, project.id)
  assert.equal(effectiveLink.data.system_id, targetSystem.id)

  evidence.flows.F06_ADD = {
    requestId: change.id,
    processInstanceId: change.processInstanceId,
    completedTasks: f06Approval.completed,
    finalStatus: effectiveChange.status,
    effectiveLinkId: effectiveLink.id,
    linkStatus: effectiveLink.status
  }

  const roleCatalogs = await listEntity('project_role_catalog')
  const roleCatalog = roleCatalogs.find(item => item.data?.role_code === 'PROJECT_MANAGER')
  assert.ok(roleCatalog, 'F03 应初始化项目经理角色目录')

  const blockingRole = await createEntityData(
    'project_role_assignment',
    `移除门禁系统角色-${runId}`,
    {
      project_id: project.id,
      system_id: targetSystem.id,
      member_id: primaryMember.id,
      user_id: '1',
      role_catalog_id: roleCatalog.id,
      role_code: 'PROJECT_MANAGER',
      role_scope: 'SYSTEM',
      primary_flag: false,
      responsibility_description: '用于验证 F06 移除关系时的系统级角色阻断。',
      effective_from: '2026-09-01',
      handover_required_flag: false,
      handover_completed_flag: false
    }
  )
  await updateEntityData('project_role_assignment', blockingRole.id, {
    data: {
      status: 'ACTIVE'
    }
  })

  let removalError
  try {
    await createEntityData(
      'project_system_change_request',
      `项目系统移除阻断申请-${runId}`,
      {
        operation_type: 'REMOVE',
        project_id: project.id,
        system_id: targetSystem.id,
        project_system_link_id: effectiveLink.id,
        change_reason: '模拟尝试移除仍承担有效系统级角色的项目系统关系。',
        proposed_change: '失效项目系统关系。',
        security_involved_flag: false,
        data_involved_flag: false,
        risk_level: 'HIGH',
        rollback_plan: '恢复项目系统关系并重新挂接责任角色。',
        schedule_impact_days: 0,
        rebaseline_required_flag: true,
        planned_effective_date: '2026-10-01',
        applicant_id: '1',
        applicant_dept_id: '2',
        version: 1
      },
      true
    )
  } catch (error) {
    removalError = error
  }

  assert.ok(removalError, '存在有效系统级角色时，F06 REMOVE 必须被拦截')
  const removalMessage = String(removalError.message)
  assert.match(removalMessage, /PROJECT_SYSTEM_REMOVAL_BLOCKED|有效系统级项目角色/)

  evidence.crossEntityChecks.F06_REMOVE_BLOCKED = {
    blockingRoleId: blockingRole.id,
    effectiveLinkId: effectiveLink.id,
    result: 'BLOCKED',
    error: removalError instanceof Error ? removalError.name : 'UnknownError'
  }

  evidence.conclusion =
    'PASS: F03/F06 配置流程真实运行，批准后跨实体动作生效，移除关系代码门禁正确阻断。'
  const evidencePath = path.join(evidenceDir, `project-governance-${runId}.json`)
  writeFileSync(evidencePath, JSON.stringify({
    result: 'PASS',
    conclusion: evidence.conclusion,
    runId,
    completed: true
  }, null, 2), { mode: 0o600 })
  console.log(JSON.stringify({
    result: 'PASS',
    evidencePath,
    projectId: project.id,
    f03Tasks: f03Approval.completed.length,
    f06Tasks: f06Approval.completed.length,
    removalBlocked: true
  }, null, 2))
}

main().catch(error => {
  evidence.error = error instanceof Error ? error.name : 'UnknownError'
  const evidencePath = path.join(evidenceDir, `project-governance-${runId}-failed.json`)
  writeFileSync(evidencePath, JSON.stringify({
    result: 'FAIL',
    error: evidence.error,
    runId
  }, null, 2), { mode: 0o600 })
  console.error(`project governance e2e failed: ${evidence.error}`)
  console.error(`evidence written: ${evidencePath}`)
  process.exit(1)
})
