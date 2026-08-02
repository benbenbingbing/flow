<template>
  <div class="member-change-form">
    <header class="form-heading">
      <div>
        <p class="eyebrow">F07 · 项目资源治理</p>
        <h2>{{ config.title || '项目成员变更申请' }}</h2>
        <p>成员、投入、角色交接与环境权限在同一申请中完成校验。</p>
      </div>
      <div class="heading-meta">
        <el-tag :type="modeTagType" effect="plain">{{ modeText }}</el-tag>
        <span v-if="context?.record?.code">{{ context.record.code }}</span>
      </div>
    </header>

    <el-form
      ref="formRef"
      :model="localValue"
      :rules="rules"
      label-position="top"
      status-icon
    >
      <div class="form-layout">
        <main>
          <section class="form-section">
            <div class="section-heading">
              <span class="section-index">01</span>
              <div>
                <h3>变更范围</h3>
                <p>先确定业务动作和所属项目，后续字段会随动作切换。</p>
              </div>
            </div>

            <el-form-item
              label="变更类型"
              prop="operation_type"
              required
            >
              <el-radio-group
                v-model="localValue.operation_type"
                :disabled="disabled"
                class="operation-switch"
                @change="handleOperationChange"
              >
                <el-radio-button
                  v-for="item in MEMBER_CHANGE_OPERATIONS"
                  :key="item.value"
                  :value="item.value"
                >
                  {{ item.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>

            <div class="field-grid">
              <el-form-item
                label="所属项目"
                prop="project_id"
                required
              >
                <FormFieldRendererLinkage
                  v-model="localValue.project_id"
                  :field="fieldFor('project_id')"
                  :disabled="isDisabled('project_id')"
                  :context="fieldContext"
                  :data-source-runtime="dataSourceRuntime"
                  @change="syncAndLoad"
                />
              </el-form-item>
              <el-form-item
                label="计划生效日期"
                prop="effective_date"
                required
              >
                <el-date-picker
                  v-model="localValue.effective_date"
                  :disabled="isDisabled('effective_date')"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="选择日期"
                  style="width: 100%"
                  @change="syncValue"
                />
              </el-form-item>
              <el-form-item
                label="申请人"
                prop="applicant_id"
                required
              >
                <FormFieldRendererLinkage
                  v-model="localValue.applicant_id"
                  :field="fieldFor('applicant_id')"
                  :disabled="isDisabled('applicant_id')"
                  :context="fieldContext"
                  :data-source-runtime="dataSourceRuntime"
                  @change="syncValue"
                />
              </el-form-item>
              <el-form-item
                label="申请部门"
                prop="applicant_dept_id"
                required
              >
                <FormFieldRendererLinkage
                  v-model="localValue.applicant_dept_id"
                  :field="fieldFor('applicant_dept_id')"
                  :disabled="isDisabled('applicant_dept_id')"
                  :context="fieldContext"
                  :data-source-runtime="dataSourceRuntime"
                  @change="syncValue"
                />
              </el-form-item>
            </div>
          </section>

          <section class="form-section">
            <div class="section-heading">
              <span class="section-index">02</span>
              <div>
                <h3>{{ operationSectionTitle }}</h3>
                <p>{{ operationSectionDescription }}</p>
              </div>
            </div>

            <template v-if="isJoin">
              <div class="field-grid">
                <el-form-item
                  label="加入人员"
                  prop="target_user_id"
                  required
                >
                  <FormFieldRendererLinkage
                    v-model="localValue.target_user_id"
                    :field="fieldFor('target_user_id')"
                    :disabled="isDisabled('target_user_id')"
                    :context="fieldContext"
                    :data-source-runtime="dataSourceRuntime"
                    @change="syncAndLoad"
                  />
                </el-form-item>
                <el-form-item
                  label="来源部门"
                  prop="source_dept_id"
                  required
                >
                  <FormFieldRendererLinkage
                    v-model="localValue.source_dept_id"
                    :field="fieldFor('source_dept_id')"
                    :disabled="isDisabled('source_dept_id')"
                    :context="fieldContext"
                    :data-source-runtime="dataSourceRuntime"
                    @change="syncValue"
                  />
                </el-form-item>
                <el-form-item
                  label="人员类型"
                  prop="employment_type"
                  required
                >
                  <el-select
                    v-model="localValue.employment_type"
                    :disabled="isDisabled('employment_type')"
                    style="width: 100%"
                    @change="syncValue"
                  >
                    <el-option
                      v-for="item in EMPLOYMENT_TYPES"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item
                  label="计划退出日期"
                  prop="planned_leave_date"
                  :required="requiresPlannedLeave"
                >
                  <el-date-picker
                    v-model="localValue.planned_leave_date"
                    :disabled="isDisabled('planned_leave_date')"
                    type="date"
                    value-format="YYYY-MM-DD"
                    placeholder="长期成员可不填"
                    style="width: 100%"
                    @change="syncValue"
                  />
                </el-form-item>
              </div>
            </template>

            <template v-else>
              <el-form-item
                label="目标项目成员"
                prop="project_member_id"
                required
              >
                <FormFieldRendererLinkage
                  v-model="localValue.project_member_id"
                  :field="fieldFor('project_member_id')"
                  :disabled="isDisabled('project_member_id')"
                  :context="fieldContext"
                  :data-source-runtime="dataSourceRuntime"
                  @change="syncAndLoad"
                />
              </el-form-item>
              <el-alert
                v-if="memberContext.memberProjectMismatch"
                title="所选成员不属于当前项目，请重新选择。"
                type="error"
                :closable="false"
                show-icon
              />
            </template>

            <div
              v-if="showsAllocation"
              class="allocation-row"
            >
              <el-form-item
                label="新投入比例"
                prop="new_allocation_percentage"
                required
              >
                <el-input-number
                  v-model="localValue.new_allocation_percentage"
                  :disabled="isDisabled('new_allocation_percentage')"
                  :min="0.01"
                  :max="100"
                  :precision="2"
                  :step="5"
                  controls-position="right"
                  style="width: 100%"
                  @change="syncAndLoad"
                />
              </el-form-item>
              <div
                class="allocation-meter"
                :class="{ exceeded: memberContext.allocation?.exceeded }"
              >
                <div>
                  <span>跨项目合计</span>
                  <strong>{{ allocationTotal }}%</strong>
                </div>
                <el-progress
                  :percentage="allocationPercentage"
                  :status="memberContext.allocation?.exceeded ? 'exception' : undefined"
                  :stroke-width="8"
                />
                <p>
                  现有 {{ allocationCurrent }}%，本次 {{ allocationRequested }}%，
                  可用 {{ allocationAvailable }}%。
                </p>
              </div>
            </div>
          </section>

          <section
            v-if="isJoin || showsAccessChange"
            class="form-section"
          >
            <div class="section-heading">
              <span class="section-index">03</span>
              <div>
                <h3>账号与环境权限</h3>
                <p>权限范围决定是否增加系统负责人和安全负责人审批。</p>
              </div>
            </div>

            <div class="switch-list">
              <div class="switch-item">
                <div>
                  <strong>项目账号</strong>
                  <span>创建项目协作、代码仓库或管理工具账号</span>
                </div>
                <el-switch
                  v-model="localValue.account_required_flag"
                  :disabled="isDisabled('account_required_flag') || !isJoin"
                  @change="syncValue"
                />
              </div>
              <div class="switch-item">
                <div>
                  <strong>环境访问</strong>
                  <span>申请开发、测试、验收或生产环境权限</span>
                </div>
                <el-switch
                  v-model="localValue.environment_access_required_flag"
                  :disabled="isDisabled('environment_access_required_flag') || !isJoin"
                  @change="syncValue"
                />
              </div>
              <div class="switch-item">
                <div>
                  <strong>敏感权限</strong>
                  <span>涉及敏感数据、特权操作或高权限账号</span>
                </div>
                <el-switch
                  v-model="localValue.sensitive_access_flag"
                  :disabled="isDisabled('sensitive_access_flag') || !isJoin"
                  @change="syncValue"
                />
              </div>
            </div>

            <el-form-item
              v-if="localValue.environment_access_required_flag"
              label="环境范围"
              prop="environment_scope"
              required
              class="environment-field"
            >
              <el-checkbox-group
                v-model="localValue.environment_scope"
                :disabled="isDisabled('environment_scope')"
                @change="syncValue"
              >
                <el-checkbox
                  v-for="item in ENVIRONMENT_SCOPES"
                  :key="item.value"
                  :value="item.value"
                  :label="item.value"
                  border
                >
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </el-form-item>
          </section>

          <section
            v-if="isLeave"
            class="form-section"
          >
            <div class="section-heading">
              <span class="section-index">04</span>
              <div>
                <h3>工作与角色交接</h3>
                <p>有效角色或账号权限存在时，必须明确同项目交接人。</p>
              </div>
            </div>

            <el-alert
              :title="handoverAlertTitle"
              :type="routeFlags.handover_required_flag ? 'warning' : 'info'"
              :closable="false"
              show-icon
              class="section-alert"
            />
            <div class="field-grid">
              <el-form-item
                label="交接成员"
                prop="handover_member_id"
                :required="routeFlags.handover_required_flag"
              >
                <FormFieldRendererLinkage
                  v-model="localValue.handover_member_id"
                  :field="fieldFor('handover_member_id')"
                  :disabled="isDisabled('handover_member_id')"
                  :context="fieldContext"
                  :data-source-runtime="dataSourceRuntime"
                  @change="syncValue"
                />
              </el-form-item>
              <el-form-item
                label="权限回收截止日期"
                prop="permission_revoke_deadline"
                :required="routeFlags.handover_required_flag"
              >
                <el-date-picker
                  v-model="localValue.permission_revoke_deadline"
                  :disabled="isDisabled('permission_revoke_deadline')"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="不晚于退出后 1 天"
                  style="width: 100%"
                  @change="syncValue"
                />
              </el-form-item>
            </div>
            <el-form-item
              label="交接说明"
              prop="handover_description"
              :required="routeFlags.handover_required_flag"
            >
              <el-input
                v-model="localValue.handover_description"
                :disabled="isDisabled('handover_description')"
                type="textarea"
                :rows="4"
                maxlength="2000"
                show-word-limit
                placeholder="说明工作项、角色职责、文档、风险与未完成事项的交接结果"
                @input="syncValue"
              />
            </el-form-item>
          </section>

          <section class="form-section final-section">
            <div class="section-heading">
              <span class="section-index">{{ isLeave ? '05' : '04' }}</span>
              <div>
                <h3>变更说明</h3>
                <p>说明业务原因和预期影响，审批记录将作为项目人员历史保留。</p>
              </div>
            </div>
            <el-form-item
              label="变更原因"
              prop="change_reason"
              required
            >
              <el-input
                v-model="localValue.change_reason"
                :disabled="isDisabled('change_reason')"
                type="textarea"
                :rows="4"
                maxlength="2000"
                show-word-limit
                placeholder="请输入成员变更原因、工作安排和风险说明"
                @input="syncValue"
              />
            </el-form-item>

            <el-alert
              v-if="validationMessages.length"
              :title="validationMessages[0]"
              type="error"
              :closable="false"
              show-icon
            >
              <template #default>
                <ul v-if="validationMessages.length > 1" class="error-list">
                  <li
                    v-for="message in validationMessages.slice(1)"
                    :key="message"
                  >
                    {{ message }}
                  </li>
                </ul>
              </template>
            </el-alert>
            <el-alert
              v-else-if="contextError"
              :title="contextError"
              type="warning"
              :closable="false"
              show-icon
            />
          </section>
        </main>

        <aside v-if="config.showRoutePreview !== false" class="route-panel">
          <div class="route-heading">
            <div>
              <span>审批路径</span>
              <strong>{{ operationLabel(localValue.operation_type) }}</strong>
            </div>
            <el-tag
              :type="routeFlags.security_review_required_flag ? 'danger' : 'success'"
              effect="dark"
              size="small"
            >
              {{ routeRiskLabel }}
            </el-tag>
          </div>

          <ol class="route-list">
            <li class="active">
              <span>1</span>
              <div>
                <strong>项目经理审核</strong>
                <small>项目资源与计划影响</small>
              </div>
            </li>
            <li class="active">
              <span>2</span>
              <div>
                <strong>人员部门负责人</strong>
                <small>组织安排与投入确认</small>
              </div>
            </li>
            <li :class="{ skipped: !routeFlags.access_review_required_flag }">
              <span>3</span>
              <div>
                <strong>系统负责人权限审核</strong>
                <small>{{ routeFlags.access_review_required_flag ? '需要审核' : '条件不满足，自动跳过' }}</small>
              </div>
            </li>
            <li :class="{ skipped: !routeFlags.security_review_required_flag }">
              <span>4</span>
              <div>
                <strong>安全负责人审核</strong>
                <small>{{ routeFlags.security_review_required_flag ? '需要审核' : '条件不满足，自动跳过' }}</small>
              </div>
            </li>
            <li class="active">
              <span>5</span>
              <div>
                <strong>PMO 最终审批</strong>
                <small>审批通过后自动生效</small>
              </div>
            </li>
          </ol>

          <dl class="context-facts">
            <div>
              <dt>有效角色</dt>
              <dd>{{ memberContext.activeRoleCount || 0 }}</dd>
            </div>
            <div>
              <dt>交接要求</dt>
              <dd>{{ routeFlags.handover_required_flag ? '必须' : '按需' }}</dd>
            </div>
            <div>
              <dt>投入余量</dt>
              <dd>{{ allocationAvailable }}%</dd>
            </div>
          </dl>

          <div v-if="loadingContext" class="context-loading">
            <el-icon class="is-loading"><Loading /></el-icon>
            正在核对成员、角色和投入
          </div>
        </aside>
      </div>
    </el-form>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import FormFieldRendererLinkage from '@/components/FormFieldRendererLinkage.vue'
import { isFieldReadonlyForMode } from '@/shared/config-runtime'
import { loadMemberChangeContext } from '../api/memberChange.js'
import {
  MEMBER_CHANGE_OPERATIONS,
  EMPLOYMENT_TYPES,
  ENVIRONMENT_SCOPES,
  computeMemberChangeFlags,
  createMemberChangeValue,
  mergeMemberChangeField,
  operationLabel,
  requiredMemberChangeFields,
  validateMemberChange
} from '../memberChangeModel.js'

const props = defineProps({
  form: { type: Object, default: () => ({}) },
  modelValue: { type: Object, default: () => ({}) },
  readonly: Boolean,
  fields: { type: Array, default: () => [] },
  linkageState: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'view' },
  config: { type: Object, default: () => ({}) },
  context: { type: Object, default: () => ({}) },
  entityCode: String,
  entityDefinition: { type: Object, default: () => ({}) },
  entityFields: { type: Array, default: () => [] },
  dataSourceRuntime: { type: Object, default: null }
})

const emit = defineEmits(['update:modelValue'])
const formRef = ref()
const loadingContext = ref(false)
const contextError = ref('')
const validationMessages = ref([])
const localValue = reactive(createMemberChangeValue(props.modelValue))
const memberContext = reactive({
  member: null,
  activeRoleCount: 0,
  activeRoles: [],
  accountRequired: false,
  environmentAccessRequired: false,
  memberProjectMismatch: false,
  allocation: {
    current: 0,
    requested: Number(localValue.new_allocation_percentage || 0),
    total: Number(localValue.new_allocation_percentage || 0),
    available: 100,
    exceeded: false
  }
})
let contextRequestSequence = 0

const disabled = computed(() =>
  props.readonly || ['approve', 'view'].includes(props.mode)
)
const isJoin = computed(() => localValue.operation_type === 'JOIN')
const isLeave = computed(() => localValue.operation_type === 'LEAVE')
const showsAllocation = computed(() =>
  ['JOIN', 'ALLOCATION_CHANGE'].includes(localValue.operation_type)
)
const showsAccessChange = computed(() =>
  ['LEAVE', 'SUSPEND', 'RESUME'].includes(localValue.operation_type)
)
const requiresPlannedLeave = computed(() =>
  ['CONTRACTOR', 'VENDOR'].includes(localValue.employment_type)
)
const routeFlags = computed(() =>
  computeMemberChangeFlags(localValue, memberContext)
)
const requiredFields = computed(() =>
  requiredMemberChangeFields(localValue, memberContext)
)
const rules = computed(() => {
  const result = {}
  requiredFields.value.forEach((fieldCode) => {
    result[fieldCode] = [{
      required: true,
      message: `${fieldFor(fieldCode).fieldName || fieldCode}不能为空`,
      trigger: ['blur', 'change']
    }]
  })
  return result
})
const fieldContext = computed(() => ({
  entityCode: props.entityCode,
  entityDefinition: props.entityDefinition,
  form: props.form,
  mode: props.mode,
  record: props.context?.record || { data: localValue },
  releaseResolutionToken: props.form?.releaseResolutionToken
}))
const modeText = computed(() => ({
  create: '新增',
  edit: '编辑',
  approve: '审批',
  view: '查看'
})[props.mode] || props.mode)
const modeTagType = computed(() => ({
  create: 'success',
  edit: 'primary',
  approve: 'warning',
  view: 'info'
})[props.mode] || 'info')
const operationSectionTitle = computed(() => ({
  JOIN: '加入信息',
  LEAVE: '退出人员',
  SUSPEND: '暂停人员',
  RESUME: '恢复人员',
  ALLOCATION_CHANGE: '投入调整'
})[localValue.operation_type] || '成员信息')
const operationSectionDescription = computed(() => ({
  JOIN: '核对人员归属、用工类型和跨项目投入比例。',
  LEAVE: '核对成员状态、有效角色、权限回收和交接安排。',
  SUSPEND: '暂停后成员及其当前有效项目角色一并暂停。',
  RESUME: '恢复成员参与状态及此前暂停的项目角色。',
  ALLOCATION_CHANGE: '调整当前项目投入，同时校验跨项目总投入。'
})[localValue.operation_type] || '')
const routeRiskLabel = computed(() => {
  if (routeFlags.value.security_review_required_flag) return '安全加签'
  if (routeFlags.value.access_review_required_flag) return '权限加签'
  return '标准路径'
})
const handoverAlertTitle = computed(() =>
  routeFlags.value.handover_required_flag
    ? `当前成员有 ${memberContext.activeRoleCount || 0} 个有效角色或账号权限，必须完成交接。`
    : '当前未识别到有效角色或账号权限，交接信息可按实际情况补充。'
)
const allocationCurrent = computed(() =>
  formatNumber(memberContext.allocation?.current)
)
const allocationRequested = computed(() =>
  formatNumber(localValue.new_allocation_percentage)
)
const allocationTotal = computed(() =>
  formatNumber(memberContext.allocation?.total)
)
const allocationAvailable = computed(() =>
  formatNumber(memberContext.allocation?.available)
)
const allocationPercentage = computed(() =>
  Math.min(100, Math.max(0, Number(memberContext.allocation?.total || 0)))
)

watch(
  () => props.modelValue,
  value => {
    Object.assign(localValue, createMemberChangeValue(value))
  },
  { deep: true }
)

watch(
  () => [
    localValue.project_id,
    localValue.project_member_id,
    localValue.target_user_id
  ],
  () => loadContext(),
  { immediate: true }
)

function handleOperationChange() {
  validationMessages.value = []
  if (isJoin.value) {
    localValue.project_member_id = ''
    localValue.handover_member_id = ''
    localValue.handover_description = ''
    localValue.permission_revoke_deadline = ''
  } else {
    localValue.target_user_id = ''
    localValue.source_dept_id = ''
    localValue.planned_leave_date = ''
    localValue.account_required_flag = false
    localValue.environment_access_required_flag = false
    localValue.environment_scope = []
    localValue.sensitive_access_flag = false
    if (!showsAllocation.value) {
      localValue.new_allocation_percentage = 0
    }
  }
  syncAndLoad()
}

function syncAndLoad() {
  syncValue()
  loadContext()
}

function syncValue() {
  Object.assign(localValue, routeFlags.value)
  localValue.name = [
    operationLabel(localValue.operation_type),
    localValue.project_id || '未选项目',
    localValue.target_user_id || localValue.project_member_id || '未选人员'
  ].join('-')
  emit('update:modelValue', {
    ...props.modelValue,
    ...localValue,
    ...routeFlags.value
  })
}

async function loadContext() {
  const sequence = ++contextRequestSequence
  const hasSelection = Boolean(
    localValue.project_id
    && (localValue.project_member_id || localValue.target_user_id)
  )
  if (!hasSelection) {
    resetMemberContext()
    syncValue()
    return
  }
  loadingContext.value = true
  contextError.value = ''
  try {
    const result = await loadMemberChangeContext({
      projectId: localValue.project_id,
      memberId: isJoin.value ? '' : localValue.project_member_id,
      targetUserId: isJoin.value ? localValue.target_user_id : '',
      requestedAllocation: showsAllocation.value
        ? localValue.new_allocation_percentage
        : 0
    })
    if (sequence !== contextRequestSequence) return
    Object.assign(memberContext, result)
    syncValue()
  } catch (error) {
    if (sequence !== contextRequestSequence) return
    contextError.value =
      error?.message || '成员、角色或投入信息查询失败，提交时仍会由后端重新校验。'
  } finally {
    if (sequence === contextRequestSequence) {
      loadingContext.value = false
    }
  }
}

function resetMemberContext() {
  Object.assign(memberContext, {
    member: null,
    activeRoleCount: 0,
    activeRoles: [],
    accountRequired: false,
    environmentAccessRequired: false,
    memberProjectMismatch: false,
    allocation: {
      current: 0,
      requested: Number(localValue.new_allocation_percentage || 0),
      total: Number(localValue.new_allocation_percentage || 0),
      available: 100,
      exceeded: false
    }
  })
}

function fieldFor(fieldCode) {
  const formField = (props.fields || [])
    .find(field => (field.fieldCode || field.fieldKey) === fieldCode)
  const entityField = (props.entityFields || [])
    .find(field => (field.fieldCode || field.fieldKey) === fieldCode)
  return mergeMemberChangeField(
    fieldCode,
    formField,
    entityField,
    fallbackFields[fieldCode]
  )
}

function isDisabled(fieldCode) {
  if (disabled.value || props.linkageState?.disabled?.[fieldCode] === true) {
    return true
  }
  return isFieldReadonlyForMode(fieldFor(fieldCode), props.mode, props.readonly)
}

async function validate() {
  if (disabled.value) return true
  validationMessages.value = []
  let platformValid = true
  try {
    await formRef.value?.validate()
  } catch {
    platformValid = false
  }
  const businessErrors =
    validateMemberChange(localValue, memberContext)
  validationMessages.value =
    businessErrors.map(item => item.message)
  if (!platformValid || businessErrors.length > 0) {
    return false
  }
  syncValue()
  return true
}

function formatNumber(value) {
  const number = Number(value || 0)
  return Number.isInteger(number)
    ? String(number)
    : number.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
}

const fallbackFields = {
  project_id: referenceField(
    'project_id', '所属项目', 'project'
  ),
  project_member_id: referenceField(
    'project_member_id', '目标项目成员', 'project_member'
  ),
  target_user_id: systemField(
    'target_user_id', '加入人员', 'USER'
  ),
  applicant_id: systemField(
    'applicant_id', '申请人', 'USER'
  ),
  applicant_dept_id: systemField(
    'applicant_dept_id', '申请部门', 'DEPT'
  ),
  source_dept_id: systemField(
    'source_dept_id', '来源部门', 'DEPT'
  ),
  handover_member_id: referenceField(
    'handover_member_id', '交接成员', 'project_member'
  )
}

function referenceField(fieldCode, fieldName, refEntityCode) {
  return {
    fieldCode,
    fieldName,
    fieldType: 'REFERENCE',
    componentType: 'reference',
    refEntityType: 'CUSTOM',
    refEntityCode
  }
}

function systemField(fieldCode, fieldName, refEntityType) {
  return {
    fieldCode,
    fieldName,
    fieldType: refEntityType,
    componentType: refEntityType.toLowerCase(),
    refEntityType
  }
}

defineExpose({ validate })
</script>

<style scoped>
.member-change-form {
  color: #1f2937;
  background: #fff;
}

.form-heading {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 4px 0 20px;
  border-bottom: 1px solid #d9dee7;
}

.form-heading h2 {
  margin: 3px 0 6px;
  font-size: 22px;
  line-height: 1.35;
  letter-spacing: 0;
}

.form-heading p {
  margin: 0;
  color: #667085;
  font-size: 13px;
}

.eyebrow {
  color: #b54708 !important;
  font-size: 12px !important;
  font-weight: 700;
}

.heading-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  min-width: 120px;
  color: #667085;
  font-size: 12px;
}

.form-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 292px;
  gap: 28px;
  align-items: start;
}

.form-section {
  padding: 24px 0;
  border-bottom: 1px solid #e4e7ed;
}

.final-section {
  border-bottom: 0;
}

.section-heading {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 18px;
}

.section-heading h3 {
  margin: 0 0 4px;
  font-size: 16px;
  line-height: 1.4;
  letter-spacing: 0;
}

.section-heading p {
  margin: 0;
  color: #667085;
  font-size: 13px;
  line-height: 1.5;
}

.section-index {
  display: inline-flex;
  flex: 0 0 30px;
  align-items: center;
  justify-content: center;
  height: 24px;
  border: 1px solid #d0d5dd;
  border-radius: 4px;
  color: #475467;
  font-size: 11px;
  font-weight: 700;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 18px;
}

.operation-switch {
  display: flex;
  flex-wrap: wrap;
}

.allocation-row {
  display: grid;
  grid-template-columns: minmax(200px, 0.42fr) minmax(280px, 0.58fr);
  gap: 18px;
  align-items: start;
}

.allocation-meter {
  min-height: 72px;
  margin-top: 30px;
  padding: 10px 12px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  background: #f8fafc;
}

.allocation-meter.exceeded {
  border-color: #f04438;
  background: #fff5f4;
}

.allocation-meter > div {
  display: flex;
  justify-content: space-between;
  margin-bottom: 7px;
  font-size: 12px;
}

.allocation-meter strong {
  color: #101828;
}

.allocation-meter p {
  margin: 7px 0 0;
  color: #667085;
  font-size: 12px;
}

.switch-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.switch-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 72px;
  padding: 12px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
}

.switch-item strong,
.switch-item span {
  display: block;
}

.switch-item strong {
  margin-bottom: 4px;
  font-size: 13px;
}

.switch-item span {
  color: #667085;
  font-size: 11px;
  line-height: 1.45;
}

.environment-field :deep(.el-checkbox-group) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.environment-field :deep(.el-checkbox.is-bordered) {
  margin: 0;
}

.section-alert {
  margin-bottom: 18px;
}

.route-panel {
  position: sticky;
  top: 12px;
  margin-top: 24px;
  border: 1px solid #cfd5df;
  border-radius: 8px;
  background: #fafbfc;
  overflow: hidden;
}

.route-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 16px;
  border-bottom: 1px solid #d9dee7;
  background: #fff;
}

.route-heading span,
.route-heading strong {
  display: block;
}

.route-heading span {
  margin-bottom: 3px;
  color: #667085;
  font-size: 11px;
}

.route-heading strong {
  font-size: 14px;
}

.route-list {
  margin: 0;
  padding: 14px 16px;
  list-style: none;
}

.route-list li {
  position: relative;
  display: flex;
  gap: 10px;
  min-height: 58px;
  color: #344054;
}

.route-list li::after {
  position: absolute;
  top: 28px;
  left: 12px;
  width: 1px;
  height: 28px;
  background: #cbd5e1;
  content: '';
}

.route-list li:last-child::after {
  display: none;
}

.route-list li > span {
  display: inline-flex;
  z-index: 1;
  flex: 0 0 25px;
  align-items: center;
  justify-content: center;
  height: 25px;
  border: 1px solid #98a2b3;
  border-radius: 50%;
  background: #fff;
  font-size: 11px;
  font-weight: 700;
}

.route-list li.active > span {
  border-color: #1570ef;
  color: #1570ef;
}

.route-list li.skipped {
  color: #98a2b3;
}

.route-list strong,
.route-list small {
  display: block;
}

.route-list strong {
  margin: 2px 0 3px;
  font-size: 13px;
}

.route-list small {
  font-size: 11px;
  line-height: 1.4;
}

.context-facts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin: 0;
  padding: 14px 16px;
  border-top: 1px solid #d9dee7;
  background: #fff;
}

.context-facts div {
  min-width: 0;
  text-align: center;
}

.context-facts dt {
  color: #667085;
  font-size: 10px;
}

.context-facts dd {
  margin: 4px 0 0;
  color: #101828;
  font-size: 13px;
  font-weight: 700;
}

.context-loading {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 10px 16px;
  border-top: 1px solid #d9dee7;
  color: #667085;
  font-size: 11px;
}

.error-list {
  margin: 6px 0 0;
  padding-left: 18px;
}

@media (max-width: 1100px) {
  .form-layout {
    grid-template-columns: 1fr;
  }

  .route-panel {
    position: static;
    margin-top: 0;
    margin-bottom: 24px;
  }
}

@media (max-width: 720px) {
  .form-heading {
    flex-direction: column;
  }

  .heading-meta {
    align-items: flex-start;
  }

  .field-grid,
  .allocation-row,
  .switch-list {
    grid-template-columns: 1fr;
  }

  .allocation-meter {
    margin-top: 0;
  }

  .operation-switch {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    width: 100%;
  }

  .operation-switch :deep(.el-radio-button__inner) {
    width: 100%;
  }
}
</style>
