<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="680px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @update:model-value="emit('update:modelValue', $event)"
    @open="initialize"
  >
    <el-alert
      title="任职变更只影响尚未创建的后续审批任务，已生成任务不会自动改派。"
      type="info"
      :closable="false"
      show-icon
      class="assignment-dialog__impact"
    />

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="104px"
    >
      <el-form-item label="任职用户" prop="userIds">
        <div v-if="lockedUser" class="assignment-dialog__locked-value">
          <el-tag>{{ userLabel(lockedUser) }}</el-tag>
        </div>
        <UserSelector
          v-else
          v-model="form.userIds"
          multiple
          value-key="id"
          placeholder="请选择任职用户"
          title="选择任职用户"
          @change="clearPrecheck"
        />
        <div class="assignment-dialog__tip">单次最多选择 200 人。</div>
      </el-form-item>

      <el-form-item label="职务" prop="positionCode">
        <el-select
          v-model="form.positionCode"
          filterable
          placeholder="请选择启用职务"
          style="width: 100%"
          :disabled="Boolean(lockedPosition)"
          @change="handlePositionChange"
        >
          <el-option
            v-for="position in selectablePositionOptions"
            :key="position.positionCode"
            :label="position.positionName"
            :value="position.positionCode"
          >
            <span>{{ position.positionName }}</span>
            <span class="assignment-dialog__option-code">{{ position.positionCode }}</span>
          </el-option>
        </el-select>
      </el-form-item>

      <el-form-item label="组织节点" prop="organizationUnitId">
        <div v-if="lockedUnit" class="assignment-dialog__locked-value">
          <el-tag type="success">{{ unitLabel(lockedUnit) }}</el-tag>
        </div>
        <el-tree-select
          v-else
          v-model="form.organizationUnitId"
          :data="filteredOrganizationTree"
          :props="organizationTreeProps"
          node-key="id"
          check-strictly
          filterable
          clearable
          default-expand-all
          placeholder="请选择职务作用的组织或部门"
          style="width: 100%"
          @change="handleOrganizationUnitChange"
        />
        <div class="assignment-dialog__tip">组织节点是任职作用域，不能只选择职务而省略。</div>
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="生效时间" prop="effectiveFromLocal">
            <el-date-picker
              v-model="form.effectiveFromLocal"
              type="datetime"
              placeholder="请选择生效时间"
              format="YYYY-MM-DD HH:mm"
              style="width: 100%"
              @change="clearPrecheck"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="结束时间" prop="effectiveToLocal">
            <el-date-picker
              v-model="form.effectiveToLocal"
              type="datetime"
              placeholder="长期有效"
              format="YYYY-MM-DD HH:mm"
              clearable
              style="width: 100%"
              @change="clearPrecheck"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="主职">
            <el-switch v-model="form.isPrimary" @change="clearPrecheck" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="排序">
            <el-input-number
              v-model="form.sortOrder"
              :min="0"
              :max="9999"
              controls-position="right"
              style="width: 100%"
              @change="clearPrecheck"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item v-if="selectedPosition?.holderMode === 'SINGLE'" label="占用处理">
        <el-checkbox v-model="form.replaceExisting" @change="clearPrecheck">
          已有任职人时转任，并结束原任职
        </el-checkbox>
      </el-form-item>

      <el-form-item label="任命原因" prop="reason">
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          placeholder="请输入任命或转任原因，便于审计追溯"
          @input="clearPrecheck"
        />
      </el-form-item>
    </el-form>

    <el-alert
      v-if="precheckState === 'passed'"
      :title="precheckSummary"
      type="success"
      :closable="false"
      show-icon
    />
    <el-alert
      v-else-if="precheckState === 'failed'"
      :title="precheckSummary"
      type="error"
      :closable="false"
      show-icon
    />

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button :loading="prechecking" @click="runPrecheck">预检</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="precheckState !== 'passed'"
        @click="submit"
      >
        {{ form.replaceExisting ? '确认转任' : '确认任命' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import UserSelector from '@/components/UserSelector.vue'
import { getOrgTree } from '@/api/system/org'
import { createIdempotentSubmissionKeyTracker } from '@/shared/idempotent-submission'
import {
  batchAssignPositions,
  getEnabledPositions,
  precheckPositionAssignments
} from '@/api/system/position'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  lockedUser: { type: Object, default: null },
  lockedUnit: { type: Object, default: null },
  lockedPosition: { type: Object, default: null },
  transfer: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'saved'])
const formRef = ref()
const positionOptions = ref<any[]>([])
const organizationTree = ref<any[]>([])
const prechecking = ref(false)
const submitting = ref(false)
const precheckState = ref<'idle' | 'passed' | 'failed'>('idle')
const precheckSummary = ref('')
const submissionKeyTracker = createIdempotentSubmissionKeyTracker()

const form = reactive({
  userIds: [] as string[],
  positionCode: '',
  organizationUnitId: '',
  effectiveFromLocal: new Date(),
  effectiveToLocal: null as Date | null,
  isPrimary: true,
  sortOrder: 0,
  replaceExisting: false,
  reason: ''
})

const rules = {
  userIds: [{
    validator: (_rule: unknown, value: string[], callback: (error?: Error) => void) => {
      const values = effectiveUserIds.value
      if (!values.length) return callback(new Error('请选择任职用户'))
      if (values.length > 200) return callback(new Error('单次最多任命 200 人'))
      if (selectedPosition.value?.holderMode === 'SINGLE' && values.length > 1) {
        return callback(new Error('单人职务一次只能任命一名用户'))
      }
      callback()
    },
    trigger: 'change'
  }],
  positionCode: [{ required: true, message: '请选择职务', trigger: 'change' }],
  organizationUnitId: [{ required: true, message: '请选择组织节点', trigger: 'change' }],
  effectiveFromLocal: [{ required: true, message: '请选择生效时间', trigger: 'change' }],
  effectiveToLocal: [{
    validator: (_rule: unknown, value: Date | null, callback: (error?: Error) => void) => {
      if (value && form.effectiveFromLocal
          && new Date(value).getTime() <= new Date(form.effectiveFromLocal).getTime()) {
        callback(new Error('结束时间必须晚于生效时间'))
        return
      }
      callback()
    },
    trigger: 'change'
  }],
  reason: [{ required: true, message: '请输入任命原因', trigger: 'blur' }]
}

const dialogTitle = computed(() => props.transfer ? '转任职务' : '新增任职')
const effectiveUserIds = computed(() => props.lockedUser?.id
  ? [String(props.lockedUser.id)]
  : form.userIds.map(String).filter(Boolean))
const selectedPosition = computed(() => positionOptions.value.find(item =>
  item.positionCode === form.positionCode) || props.lockedPosition)

const flatOrganizationUnits = computed(() => {
  const result: any[] = []
  const walk = (nodes: any[]) => (nodes || []).forEach(node => {
    result.push(node)
    walk(node.children || [])
  })
  walk(organizationTree.value)
  return result
})
const selectedOrganizationUnit = computed(() => props.lockedUnit
  || flatOrganizationUnits.value.find(unit => String(unit.id) === String(form.organizationUnitId)))
const selectablePositionOptions = computed(() => {
  const unitType = String(selectedOrganizationUnit.value?.type || '').toUpperCase()
  if (!unitType) return positionOptions.value
  return positionOptions.value.filter(position =>
    position.applicableUnitType === 'ANY'
    || position.applicableUnitType === unitType)
})

const organizationTreeProps = {
  label: 'orgName',
  value: 'id',
  children: 'children',
  disabled: 'selectionDisabled'
}

const filteredOrganizationTree = computed(() => {
  const applicableType = selectedPosition.value?.applicableUnitType || 'ANY'
  const decorate = (nodes: any[]): any[] => (nodes || []).map(node => ({
    ...node,
    selectionDisabled: applicableType !== 'ANY'
      && String(node.type || '').toUpperCase() !== applicableType,
    children: decorate(node.children || [])
  }))
  return decorate(organizationTree.value)
})

function userLabel(user: any) {
  return user?.nickname || user?.username || user?.name || user?.id || '当前用户'
}

function unitLabel(unit: any) {
  return unit?.displayPath || unit?.orgName || unit?.name || unit?.id || '当前组织节点'
}

function clearPrecheck() {
  precheckState.value = 'idle'
  precheckSummary.value = ''
  submissionKeyTracker.clear()
}

async function initialize() {
  Object.assign(form, {
    userIds: props.lockedUser?.id ? [String(props.lockedUser.id)] : [],
    positionCode: props.lockedPosition?.positionCode || '',
    organizationUnitId: props.lockedUnit?.id || '',
    effectiveFromLocal: new Date(),
    effectiveToLocal: null,
    isPrimary: true,
    sortOrder: 0,
    replaceExisting: props.transfer,
    reason: ''
  })
  clearPrecheck()
  const [positions, organizations] = await Promise.all([
    getEnabledPositions(),
    getOrgTree()
  ])
  positionOptions.value = Array.isArray(positions)
    ? positions
    : positions?.records || positions?.list || []
  if (props.lockedPosition?.positionCode
      && !positionOptions.value.some(item =>
        item.positionCode === props.lockedPosition.positionCode)) {
    positionOptions.value.unshift(props.lockedPosition)
  }
  organizationTree.value = Array.isArray(organizations)
    ? organizations
    : organizations?.records || organizations?.list || []
}

function handlePositionChange() {
  const selected = selectedPosition.value
  const unitType = String(selectedOrganizationUnit.value?.type || '').toUpperCase()
  if (selected && unitType && selected.applicableUnitType !== 'ANY'
      && selected.applicableUnitType !== unitType) {
    form.positionCode = ''
    ElMessage.warning('所选职务不适用于当前组织节点类型')
  }
  clearPrecheck()
}

function handleOrganizationUnitChange() {
  const selected = selectedPosition.value
  const unitType = String(selectedOrganizationUnit.value?.type || '').toUpperCase()
  if (selected && unitType && selected.applicableUnitType !== 'ANY'
      && selected.applicableUnitType !== unitType) {
    form.positionCode = ''
    ElMessage.warning('当前组织节点类型不适用于已选职务，请重新选择')
  }
  clearPrecheck()
}

function buildRequest() {
  const effectiveFrom = new Date(form.effectiveFromLocal).toISOString()
  const effectiveTo = form.effectiveToLocal
    ? new Date(form.effectiveToLocal).toISOString()
    : null
  return {
    atomic: true,
    reason: form.reason.trim(),
    items: effectiveUserIds.value.map(userId => ({
      positionCode: form.positionCode,
      organizationUnitId: form.organizationUnitId,
      userId,
      effectiveFrom,
      effectiveTo,
      isPrimary: form.isPrimary,
      sortOrder: form.sortOrder,
      replaceExisting: form.replaceExisting
    }))
  }
}

function normalizePrecheck(result: any) {
  const items = Array.isArray(result)
    ? result
    : result?.items || result?.results || []
  const explicitlyValid = result?.valid ?? result?.passed ?? result?.success
  const errors = items.filter((item: any) =>
    item?.valid === false || item?.passed === false || item?.success === false)
  return {
    valid: explicitlyValid === false ? false : errors.length === 0,
    items,
    errors
  }
}

async function runPrecheck() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return false
  // 主动重新预检表示开始新的提交周期，后续重试复用本次通过后生成的键。
  submissionKeyTracker.clear()
  const request = buildRequest()
  prechecking.value = true
  try {
    const normalized = normalizePrecheck(
      await precheckPositionAssignments(request)
    )
    precheckState.value = normalized.valid ? 'passed' : 'failed'
    precheckSummary.value = normalized.valid
      ? `预检通过，将处理 ${effectiveUserIds.value.length} 条任职。`
      : normalized.errors.map((item: any) =>
          item.message || item.errorMessage || item.errorCode || '任职冲突'
        ).join('；') || '预检未通过，请检查任职范围和有效期。'
    if (normalized.valid) submissionKeyTracker.keyFor(request)
    return normalized.valid
  } catch (error: any) {
    precheckState.value = 'failed'
    precheckSummary.value = error?.message || '预检失败，请重试。'
    return false
  } finally {
    prechecking.value = false
  }
}

async function submit() {
  if (precheckState.value !== 'passed') return
  submitting.value = true
  try {
    const request = buildRequest()
    await batchAssignPositions(request, submissionKeyTracker.keyFor(request))
    ElMessage.success(form.replaceExisting ? '职务转任成功' : '职务任命成功')
    submissionKeyTracker.clear()
    emit('saved')
    emit('update:modelValue', false)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.assignment-dialog__impact {
  margin-bottom: 16px;
}

.assignment-dialog__locked-value {
  min-height: 32px;
  display: flex;
  align-items: center;
}

.assignment-dialog__tip,
.assignment-dialog__option-code {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.assignment-dialog__tip {
  margin-top: 4px;
}

.assignment-dialog__option-code {
  margin-left: 10px;
}

@media (max-width: 760px) {
  :deep(.el-dialog) {
    width: 94vw !important;
  }
}
</style>
