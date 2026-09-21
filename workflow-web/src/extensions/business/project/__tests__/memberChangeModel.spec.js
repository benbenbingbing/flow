import assert from 'node:assert/strict'
import {
  computeMemberChangeFlags,
  createMemberChangeValue,
  mergeMemberChangeField,
  requiredMemberChangeFields,
  summarizeAllocation,
  validateMemberChange
} from '../memberChangeModel.js'

const defaults = createMemberChangeValue()
assert.equal(defaults.operation_type, 'JOIN')
assert.equal(defaults.new_allocation_percentage, 100)
assert.deepEqual(defaults.environment_scope, [])

const normalized = createMemberChangeValue({
  new_allocation_percentage: '',
  environment_scope: 'DEV,TEST',
  account_required_flag: 0
})
assert.equal(normalized.new_allocation_percentage, 100)
assert.deepEqual(normalized.environment_scope, ['DEV', 'TEST'])
assert.equal(normalized.account_required_flag, false)
assert.equal(
  createMemberChangeValue({
    new_allocation_percentage: '25.5'
  }).new_allocation_percentage,
  25.5
)

const mergedProjectField = mergeMemberChangeField(
  'project_id',
  {
    fieldCode: 'project_id',
    fieldName: '所属项目',
    componentType: 'reference'
  },
  {
    fieldCode: 'project_id',
    fieldType: 'REFERENCE',
    refEntityType: 'CUSTOM',
    refEntityCode: 'project',
    componentProps: {
      displayField: 'project_name'
    }
  },
  {
    fieldCode: 'project_id',
    refEntityCode: 'fallback_project'
  }
)
assert.equal(mergedProjectField.refEntityCode, 'project')
assert.equal(mergedProjectField.refEntityType, 'CUSTOM')
assert.equal(
  mergedProjectField.componentProps.displayField,
  'project_name'
)

const allocation = summarizeAllocation([
  {
    id: 'MEM-A',
    status: 'ACTIVE',
    data: { allocation_percentage: 60 }
  },
  {
    id: 'MEM-B',
    status: 'LEFT',
    data: { allocation_percentage: 100 }
  }
], '', 50)
assert.equal(allocation.current, 60)
assert.equal(allocation.total, 110)
assert.equal(allocation.exceeded, true)

const joinFlags = computeMemberChangeFlags({
  operation_type: 'JOIN',
  account_required_flag: true,
  environment_access_required_flag: true,
  environment_scope: ['PROD_OPERATE']
})
assert.equal(joinFlags.access_review_required_flag, true)
assert.equal(joinFlags.security_review_required_flag, true)
assert.equal(joinFlags.handover_required_flag, false)

const leaveFlags = computeMemberChangeFlags({
  operation_type: 'LEAVE'
}, {
  activeRoleCount: 2,
  environmentAccessRequired: true
})
assert.equal(leaveFlags.handover_required_flag, true)
assert.equal(leaveFlags.access_review_required_flag, true)
assert.equal(leaveFlags.security_review_required_flag, true)

const leaveRequired = requiredMemberChangeFields({
  operation_type: 'LEAVE'
}, {
  activeRoleCount: 1
})
assert.equal(leaveRequired.has('handover_member_id'), true)
assert.equal(leaveRequired.has('handover_description'), true)
assert.equal(leaveRequired.has('permission_revoke_deadline'), true)

const errors = validateMemberChange({
  operation_type: 'JOIN',
  project_id: 'PRJ-1',
  applicant_id: 'USER-ADMIN',
  applicant_dept_id: 'DEPT-PMO',
  target_user_id: 'USER-1',
  source_dept_id: 'DEPT-1',
  employment_type: 'VENDOR',
  effective_date: '2026-08-10',
  planned_leave_date: '',
  new_allocation_percentage: 50,
  change_reason: '供应商开发资源加入',
  environment_access_required_flag: false
}, {
  allocation: {
    exceeded: false
  }
})
assert.equal(
  errors.some(error =>
    error.fieldCode === 'planned_leave_date'
  ),
  true
)

const leaveErrors = validateMemberChange({
  operation_type: 'LEAVE',
  project_id: 'PRJ-1',
  applicant_id: 'USER-ADMIN',
  applicant_dept_id: 'DEPT-PMO',
  project_member_id: 'MEM-1',
  effective_date: '2026-08-20',
  change_reason: '完成阶段任务后退出',
  handover_member_id: 'MEM-1',
  handover_description: '已交接',
  permission_revoke_deadline: '2026-08-23'
}, {
  activeRoleCount: 1
})
assert.equal(
  leaveErrors.some(error =>
    error.message.includes('不能选择退出人员本人')
  ),
  true
)
assert.equal(
  leaveErrors.some(error =>
    error.message.includes('一个自然日')
  ),
  true
)

console.log('project member change model tests passed')
