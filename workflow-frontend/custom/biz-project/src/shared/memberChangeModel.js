export const MEMBER_CHANGE_OPERATIONS = [
  { value: 'JOIN', label: '成员加入' },
  { value: 'LEAVE', label: '成员退出' },
  { value: 'SUSPEND', label: '暂停参与' },
  { value: 'RESUME', label: '恢复参与' },
  { value: 'ALLOCATION_CHANGE', label: '调整投入' }
]

export const EMPLOYMENT_TYPES = [
  { value: 'INTERNAL', label: '内部员工' },
  { value: 'CONTRACTOR', label: '合同人员' },
  { value: 'VENDOR', label: '供应商人员' }
]

export const ENVIRONMENT_SCOPES = [
  { value: 'DEV', label: '开发环境' },
  { value: 'TEST', label: '测试环境' },
  { value: 'UAT', label: '验收环境' },
  { value: 'PROD_READ', label: '生产只读' },
  { value: 'PROD_OPERATE', label: '生产操作' }
]

const ACTIVE_MEMBER_STATUSES = new Set([
  'PENDING_JOIN',
  'ACTIVE',
  'SUSPENDED',
  'PENDING_LEAVE'
])

/** 将发布表单的历史值补齐为成员变更模型；保留未知业务字段，供 PC 和移动端一致回填与提交。 */
export function createMemberChangeValue(value = {}) {
  return {
    ...value,
    name: value.name || '',
    operation_type: value.operation_type || 'JOIN',
    project_id: value.project_id || '',
    applicant_id: value.applicant_id || '',
    applicant_dept_id: value.applicant_dept_id || '',
    project_member_id: value.project_member_id || '',
    target_user_id: value.target_user_id || '',
    source_dept_id: value.source_dept_id || '',
    employment_type: value.employment_type || 'INTERNAL',
    effective_date: value.effective_date || '',
    planned_leave_date: value.planned_leave_date || '',
    new_allocation_percentage: normalizeInputNumber(
      value.new_allocation_percentage,
      100
    ),
    change_reason: value.change_reason || '',
    account_required_flag: Boolean(value.account_required_flag),
    environment_access_required_flag: Boolean(value.environment_access_required_flag),
    environment_scope: normalizeArray(value.environment_scope),
    sensitive_access_flag: Boolean(value.sensitive_access_flag),
    handover_required_flag: Boolean(value.handover_required_flag),
    handover_member_id: value.handover_member_id || '',
    handover_description: value.handover_description || '',
    permission_revoke_deadline: value.permission_revoke_deadline || '',
    access_review_required_flag: Boolean(value.access_review_required_flag),
    security_review_required_flag: Boolean(value.security_review_required_flag)
  }
}

/** 兼容宿主实体接口的列表响应封装；仅在成功响应中取记录，查询错误由 API 层直接抛出。 */
export function normalizeRecords(response) {
  if (Array.isArray(response)) return response
  if (Array.isArray(response?.records)) return response.records
  if (Array.isArray(response?.list)) return response.list
  if (Array.isArray(response?.data)) return response.data
  if (Array.isArray(response?.data?.records)) return response.data.records
  if (Array.isArray(response?.data?.list)) return response.data.list
  return []
}

/** 合并内置兜底、实体约束和发布字段定义；优先使用发布配置，同时保留引用范围，避免选择器失去业务限制。 */
export function mergeMemberChangeField(
  fieldCode,
  formField,
  entityField,
  fallbackField
) {
  const merged = {
    ...(fallbackField || {}),
    ...(entityField || {}),
    ...(formField || {})
  }
  const componentProps = {
    ...(fallbackField?.componentProps || {}),
    ...(entityField?.componentProps || {}),
    ...(formField?.componentProps || {})
  }
  return {
    ...merged,
    fieldCode: merged.fieldCode || merged.fieldKey || fieldCode,
    fieldName: merged.fieldName || merged.label || fieldCode,
    fieldType: merged.fieldType || 'STRING',
    componentType: merged.componentType || 'input',
    refEntityCode: firstConfigured(
      formField?.refEntityCode,
      entityField?.refEntityCode,
      fallbackField?.refEntityCode
    ),
    refEntityId: firstConfigured(
      formField?.refEntityId,
      entityField?.refEntityId,
      fallbackField?.refEntityId
    ),
    refEntityType: firstConfigured(
      formField?.refEntityType,
      entityField?.refEntityType,
      fallbackField?.refEntityType
    ),
    refListKey: firstConfigured(
      formField?.refListKey,
      entityField?.refListKey,
      fallbackField?.refListKey
    ),
    ...(Object.keys(componentProps).length > 0 ? { componentProps } : {})
  }
}

/** 汇总有效参与状态的跨项目投入；排除本次变更的原记录后计入新投入，返回可用量与超额标记供表单阻断提交。 */
export function summarizeAllocation(
  records,
  excludedMemberId,
  requestedAllocation
) {
  const current = normalizeRecords(records)
    .filter(item => ACTIVE_MEMBER_STATUSES.has(String(item?.status || '').toUpperCase()))
    .filter(item => String(item?.id || '') !== String(excludedMemberId || ''))
    .reduce((total, item) => {
      return total + numeric(item?.data?.allocation_percentage)
    }, 0)
  const requested = numeric(requestedAllocation)
  return {
    current,
    requested,
    total: current + requested,
    available: Math.max(0, 100 - current),
    exceeded: current + requested > 100
  }
}

/** 根据变更类型和服务端成员上下文推导交接、权限及安全审批标记；调用方不得依赖旧表单中保存的路由标记。 */
export function computeMemberChangeFlags(value, memberContext = {}) {
  const operation = String(value?.operation_type || '').toUpperCase()
  const environmentScope = normalizeArray(value?.environment_scope)
  const accountRequired =
    Boolean(value?.account_required_flag)
    || Boolean(memberContext.accountRequired)
  const environmentRequired =
    Boolean(value?.environment_access_required_flag)
    || Boolean(memberContext.environmentAccessRequired)
  const handoverRequired = operation === 'LEAVE' && (
    Number(memberContext.activeRoleCount || 0) > 0
    || accountRequired
    || environmentRequired
  )
  const accessReviewRequired =
    ['LEAVE', 'SUSPEND'].includes(operation)
    || accountRequired
    || environmentRequired
  const securityReviewRequired =
    Boolean(value?.sensitive_access_flag)
    || environmentScope.includes('PROD_OPERATE')
    || (
      ['LEAVE', 'SUSPEND'].includes(operation)
      && Boolean(memberContext.environmentAccessRequired)
    )
  return {
    handover_required_flag: handoverRequired,
    access_review_required_flag: accessReviewRequired,
    security_review_required_flag: securityReviewRequired
  }
}

/** 返回当前操作必须填写的业务字段；退出交接要求依赖成员角色和权限上下文，供两端生成同一校验规则。 */
export function requiredMemberChangeFields(value, memberContext = {}) {
  const operation = String(value?.operation_type || '').toUpperCase()
  const required = new Set([
    'operation_type',
    'project_id',
    'applicant_id',
    'applicant_dept_id',
    'effective_date',
    'change_reason'
  ])
  if (operation === 'JOIN') {
    required.add('target_user_id')
    required.add('source_dept_id')
    required.add('employment_type')
    required.add('new_allocation_percentage')
    if (['CONTRACTOR', 'VENDOR'].includes(
      String(value?.employment_type || '').toUpperCase()
    )) {
      required.add('planned_leave_date')
    }
    if (value?.environment_access_required_flag) {
      required.add('environment_scope')
    }
  } else {
    required.add('project_member_id')
  }
  if (operation === 'ALLOCATION_CHANGE') {
    required.add('new_allocation_percentage')
  }
  const flags = computeMemberChangeFlags(value, memberContext)
  if (flags.handover_required_flag) {
    required.add('handover_member_id')
    required.add('handover_description')
    required.add('permission_revoke_deadline')
  }
  return required
}

/** 检查成员归属、投入、日期和交接约束，返回字段级错误；服务端仍负责最终校验，前端不得用此结果绕过权限。 */
export function validateMemberChange(value, memberContext = {}) {
  const required = requiredMemberChangeFields(value, memberContext)
  const errors = []
  required.forEach((fieldCode) => {
    if (isEmpty(value?.[fieldCode])) {
      errors.push({
        fieldCode,
        message: `${fieldLabel(fieldCode)}不能为空`
      })
    }
  })
  const operation = String(value?.operation_type || '').toUpperCase()
  if (['JOIN', 'ALLOCATION_CHANGE'].includes(operation)) {
    const allocation = numeric(value?.new_allocation_percentage)
    if (allocation < 0.01 || allocation > 100) {
      errors.push({
        fieldCode: 'new_allocation_percentage',
        message: '投入比例必须在 0.01% 至 100% 之间'
      })
    }
    if (memberContext.allocation?.exceeded) {
      errors.push({
        fieldCode: 'new_allocation_percentage',
        message: '人员跨项目有效投入比例不能超过 100%'
      })
    }
  }
  if (
    value?.planned_leave_date
    && value?.effective_date
    && value.planned_leave_date < value.effective_date
  ) {
    errors.push({
      fieldCode: 'planned_leave_date',
      message: '计划退出日期不得早于生效日期'
    })
  }
  if (
    value?.permission_revoke_deadline
    && value?.effective_date
    && daysBetween(
      value.effective_date,
      value.permission_revoke_deadline
    ) > 1
  ) {
    errors.push({
      fieldCode: 'permission_revoke_deadline',
      message: '权限最迟须在退出后一个自然日内回收'
    })
  }
  if (
    operation === 'LEAVE'
    && value?.handover_member_id
    && String(value.handover_member_id)
      === String(value?.project_member_id || '')
  ) {
    errors.push({
      fieldCode: 'handover_member_id',
      message: '交接成员不能选择退出人员本人'
    })
  }
  if (
    memberContext.memberProjectMismatch
  ) {
    errors.push({
      fieldCode: 'project_member_id',
      message: '目标成员不属于所选项目'
    })
  }
  return errors
}

export function operationLabel(operation) {
  return MEMBER_CHANGE_OPERATIONS.find(
    item => item.value === operation
  )?.label || operation || '未选择'
}

/** 切换业务动作时清理互斥字段；审批和查看模式不得调用该转换。 */
export function changeMemberOperation(value) {
  const next = { ...value }
  if (next.operation_type === 'JOIN') {
    Object.assign(next, { project_member_id: '', handover_member_id: '', handover_description: '', permission_revoke_deadline: '' })
  } else {
    Object.assign(next, { target_user_id: '', source_dept_id: '', planned_leave_date: '', account_required_flag: false, environment_access_required_flag: false, environment_scope: [], sensitive_access_flag: false })
    if (next.operation_type !== 'ALLOCATION_CHANGE') next.new_allocation_percentage = 0
  }
  return next
}

/** 名称和审批路由标记由同一上下文推导，不能由各端维护不同的分支规则。 */
export function finalizeMemberChangeValue(value, memberContext) {
  return { ...value, ...computeMemberChangeFlags(value, memberContext), name: [operationLabel(value.operation_type), value.project_id || '未选项目', value.target_user_id || value.project_member_id || '未选人员'].join('-') }
}

function normalizeArray(value) {
  if (Array.isArray(value)) return value
  if (value === null || value === undefined || value === '') return []
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed : [value]
    } catch {
      return value.split(',').map(item => item.trim()).filter(Boolean)
    }
  }
  return [value]
}

function numeric(value) {
  const number = Number(value || 0)
  return Number.isFinite(number) ? number : 0
}

function normalizeInputNumber(value, fallback) {
  if (value === null || value === undefined || value === '') {
    return fallback
  }
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

function firstConfigured(...values) {
  return values.find(value =>
    value !== undefined && value !== null && value !== ''
  )
}

function isEmpty(value) {
  return value === null
    || value === undefined
    || value === ''
    || (Array.isArray(value) && value.length === 0)
}

function daysBetween(start, end) {
  const startDate = new Date(`${String(start).slice(0, 10)}T00:00:00`)
  const endDate = new Date(`${String(end).slice(0, 10)}T00:00:00`)
  return Math.round((endDate.getTime() - startDate.getTime()) / 86400000)
}

function fieldLabel(fieldCode) {
  return {
    operation_type: '变更类型',
    project_id: '所属项目',
    applicant_id: '申请人',
    applicant_dept_id: '申请部门',
    project_member_id: '目标项目成员',
    target_user_id: '加入人员',
    source_dept_id: '来源部门',
    employment_type: '人员类型',
    effective_date: '计划生效日期',
    planned_leave_date: '计划退出日期',
    new_allocation_percentage: '新投入比例',
    change_reason: '变更原因',
    environment_scope: '环境范围',
    handover_member_id: '交接成员',
    handover_description: '交接说明',
    permission_revoke_deadline: '权限回收截止日期'
  }[fieldCode] || fieldCode
}
