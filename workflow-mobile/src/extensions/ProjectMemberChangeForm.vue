<template>
  <BusinessFormFields ref="fieldsRef" v-bind="$props" :readonly="disabled" :fields="displayFields" :model-value="value" :external-errors="errors" @update:model-value="update" />
  <p v-if="loading" class="context-status">正在核对成员、角色与投入比例…</p>
  <p v-if="contextError" class="context-error" role="alert">{{ contextError }}<VanButton size="small" plain @click="loadContext">重试</VanButton></p>
  <VanNoticeBar v-if="memberContext.allocation" :color="memberContext.allocation.exceeded ? '#b04432' : 'var(--flow-mobile-accent-text)'" :background="memberContext.allocation.exceeded ? '#fff3ec' : 'var(--flow-mobile-accent-soft)'" wrapable :scrollable="false">跨项目投入合计 {{ memberContext.allocation.total }}%，可用 {{ memberContext.allocation.available }}%</VanNoticeBar>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { Button as VanButton, NoticeBar as VanNoticeBar } from 'vant'
import { createMemberChangeValue, requiredMemberChangeFields, validateMemberChange, computeMemberChangeFlags, changeMemberOperation, finalizeMemberChangeValue, MEMBER_CHANGE_OPERATIONS, EMPLOYMENT_TYPES, ENVIRONMENT_SCOPES } from '@flow/workflow-core/business/project/memberChangeModel'
import { businessFormProps, mergeBusinessField } from './formContract.js'
import BusinessFormFields from './BusinessFormFields.vue'
const props = defineProps(businessFormProps), emit = defineEmits(['update:modelValue'])
const value = ref(createMemberChangeValue(props.modelValue)), memberContext = ref({}), fieldsRef = ref(), loading = ref(false), contextError = ref(''), errors = ref({})
const disabled = computed(() => props.readonly || ['view', 'approve'].includes(props.mode))
const flags = computed(() => computeMemberChangeFlags(value.value, memberContext.value))
const required = computed(() => requiredMemberChangeFields(value.value, memberContext.value))
let generation = 0
const definitions = [
  ['operation_type', '变更类型', 'select', { options: MEMBER_CHANGE_OPERATIONS }], ['project_id', '所属项目', 'reference', {}, 'CUSTOM', 'project'], ['effective_date', '计划生效日期', 'date'],
  ['applicant_id', '申请人', 'reference', {}, 'USER'], ['applicant_dept_id', '申请部门', 'reference', {}, 'DEPT'],
  ['target_user_id', '加入人员', 'reference', {}, 'USER'], ['source_dept_id', '来源部门', 'reference', {}, 'DEPT'], ['employment_type', '人员类型', 'select', { options: EMPLOYMENT_TYPES }],
  ['project_member_id', '目标项目成员', 'reference', {}, 'CUSTOM', 'project_member'], ['planned_leave_date', '计划退出日期', 'date'],
  ['new_allocation_percentage', '新投入比例（%）', 'number', { min: 0.01, max: 100, precision: 2 }],
  ['account_required_flag', '需要账号', 'switch'], ['environment_access_required_flag', '需要环境权限', 'switch'], ['environment_scope', '环境范围', 'checkbox', { options: ENVIRONMENT_SCOPES }], ['sensitive_access_flag', '涉及敏感权限', 'switch'],
  ['handover_member_id', '交接成员', 'reference', {}, 'CUSTOM', 'project_member'], ['handover_description', '交接说明', 'textarea'], ['permission_revoke_deadline', '权限回收截止日期', 'date'], ['change_reason', '变更原因', 'textarea']
]
function businessVisible(code) {
  const join = value.value.operation_type === 'JOIN'
  if (['target_user_id', 'source_dept_id', 'employment_type', 'account_required_flag', 'environment_access_required_flag', 'sensitive_access_flag'].includes(code)) return join
  if (code === 'project_member_id') return !join
  if (code === 'planned_leave_date') return join && ['CONTRACTOR', 'VENDOR'].includes(value.value.employment_type)
  if (code === 'environment_scope') return join && value.value.environment_access_required_flag
  if (code === 'new_allocation_percentage') return ['JOIN', 'ALLOCATION_CHANGE'].includes(value.value.operation_type)
  if (['handover_member_id', 'handover_description', 'permission_revoke_deadline'].includes(code)) return value.value.operation_type === 'LEAVE'
  return true
}
const displayFields = computed(() => definitions.filter(([code]) => businessVisible(code)).map(([fieldCode, fieldName, componentType, componentProps = {}, refEntityType, refEntityCode]) => {
  const field = mergeBusinessField({ fieldCode, fieldName, componentType, componentProps, fieldType: componentType === 'reference' ? 'REFERENCE' : componentType === 'number' ? 'DECIMAL' : componentType === 'switch' ? 'BOOLEAN' : 'STRING', refEntityType, refEntityCode }, props)
  field.isRequired = required.value.has(fieldCode) || field.isRequired === true || field.isRequired === 1
  return field
}))
watch(() => props.modelValue, next => { value.value = createMemberChangeValue(next) }, { deep: true })
function publish() { if (disabled.value) return; value.value = finalizeMemberChangeValue(value.value, memberContext.value); emit('update:modelValue', value.value) }
function update(next) {
  if (disabled.value) return
  value.value = next.operation_type !== value.value.operation_type ? changeMemberOperation(next) : next
  errors.value = {}; publish()
}
/** 查询失败和过期响应均不能产出审批路由标记；提交前必须有当前选择的上下文。 */
async function loadContext() {
  const version = ++generation
  memberContext.value = {}; contextError.value = ''; loading.value = false
  if (!value.value.project_id || !(value.value.project_member_id || value.value.target_user_id)) return
  loading.value = true
  try {
    if (!props.services.loadMemberChangeContext) throw new Error('成员上下文服务不可用')
    const join = value.value.operation_type === 'JOIN'
    const result = await props.services.loadMemberChangeContext({ projectId: value.value.project_id, memberId: join ? '' : value.value.project_member_id, targetUserId: join ? value.value.target_user_id : '', requestedAllocation: ['JOIN', 'ALLOCATION_CHANGE'].includes(value.value.operation_type) ? value.value.new_allocation_percentage : 0 })
    if (version !== generation) return
    memberContext.value = result; publish()
  } catch (cause) { if (version === generation) contextError.value = cause.message || '成员上下文加载失败' }
  finally { if (version === generation) loading.value = false }
}
watch(() => [value.value.operation_type, value.value.project_id, value.value.project_member_id, value.value.target_user_id, value.value.new_allocation_percentage], loadContext, { immediate: true })
onBeforeUnmount(() => generation++)
async function validate() {
  if (disabled.value) return true
  if (loading.value || contextError.value) return false
  errors.value = Object.fromEntries(validateMemberChange(value.value, memberContext.value).map(item => [item.fieldCode, item.message]))
  const valid = await fieldsRef.value?.validate()
  if (valid && !Object.keys(errors.value).length) { publish(); return true }
  return false
}
defineExpose({ validate })
</script>
<style scoped>.context-status { color: var(--flow-mobile-muted); font-size: 12px; }.context-error { color: #bc4738; font-size: 12px; }.context-error button { margin-left: 10px; }</style>
