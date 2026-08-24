<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${configLabel}发布`"
    width="920px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @opened="initialize"
  >
    <el-form label-width="96px">
      <el-form-item label="发布方式">
        <template #label>
          <ConfigHelpLabel
            label="发布方式"
            help-key="uiConfig.releaseMode"
          />
        </template>
        <el-radio-group
          v-if="configType === 'FORM'"
          v-model="form.releaseMode"
          @change="loadPreview"
        >
          <el-radio-button value="STANDARD">普通发布</el-radio-button>
          <el-radio-button value="HOTFIX" :disabled="!canHotfix">
            兼容热修复
          </el-radio-button>
        </el-radio-group>
        <el-tag v-else type="primary">普通发布</el-tag>
        <div class="publish-mode-help">
          <template v-if="form.releaseMode === 'STANDARD'">
            <span v-if="configType === 'FORM'">
              表单需要重新发布流程后生效；运行中实例继续使用原始版本。
            </span>
            <span v-else>
              列表发布后立即切换当前全局生效版本，所有列表页面同步生效。
            </span>
          </template>
          <template v-else>
            HOTFIX 必须登记原因、工单和发布窗口；REVIEW 风险由非申请人独立复核，批准后才可作用于当前可发起版本和运行中实例。
          </template>
        </div>
      </el-form-item>

      <el-form-item label="发布说明">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <template v-if="form.releaseMode === 'HOTFIX'">
        <el-form-item label="变更原因" required>
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="2"
            maxlength="1000"
            show-word-limit
            placeholder="说明故障现象、修复目的和预期结果"
          />
        </el-form-item>
        <el-form-item label="关联工单" required>
          <el-input
            v-model="form.ticketRef"
            maxlength="255"
            placeholder="例如 INC-2026-001"
          />
        </el-form-item>
        <el-form-item label="发布窗口" required>
          <div class="release-window">
            <el-date-picker
              v-model="form.windowStart"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="开始时间"
            />
            <span>至</span>
            <el-date-picker
              v-model="form.windowEnd"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="结束时间"
            />
          </div>
        </el-form-item>
      </template>
    </el-form>

    <el-alert
      v-if="configType === 'LIST'"
      title="列表运行时始终读取当前全局生效版本，发布后所有列表页面立即生效。"
      type="warning"
      :closable="false"
      show-icon
      class="publish-alert"
    />

    <div v-loading="previewLoading" class="publish-preview">
      <template v-if="preview">
        <div class="publish-summary">
          <el-tag :type="riskTagType(preview.riskLevel)">
            {{ riskLabel(preview.riskLevel) }}
          </el-tag>
          <span>变更 {{ preview.changedItems?.length || 0 }} 项</span>
          <span v-if="configType === 'FORM'">
            影响流程版本 {{ preview.processVersionCount || 0 }} 个
          </span>
          <span v-if="configType === 'FORM'">
            运行中实例 {{ preview.activeInstanceCount || 0 }} 个
          </span>
          <span v-if="configType === 'FORM'">
            跳过历史实例 {{ preview.skippedHistoricalInstanceCount || 0 }} 个
          </span>
        </div>

        <section
          v-if="form.releaseMode === 'HOTFIX' && displayedRequest"
          class="governance-card"
        >
          <div class="governance-card__header">
            <strong>HOTFIX 治理状态</strong>
            <el-tag :type="requestStatusType(displayedRequest.status)">
              {{ requestStatusLabel(displayedRequest.status) }}
            </el-tag>
            <el-tag v-if="!requestMatchesPreview" type="danger">
              审批快照已过期
            </el-tag>
          </div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="申请人">
              {{ displayedRequest.applicantName || displayedRequest.applicantId }}
            </el-descriptions-item>
            <el-descriptions-item label="关联工单">
              {{ displayedRequest.ticketRef }}
            </el-descriptions-item>
            <el-descriptions-item label="发布窗口">
              {{ displayedRequest.windowStart }} 至 {{ displayedRequest.windowEnd }}
            </el-descriptions-item>
            <el-descriptions-item label="复核人">
              {{ displayedRequest.reviewerName || displayedRequest.reviewerId || '待独立复核' }}
            </el-descriptions-item>
          </el-descriptions>
          <div class="governance-actions">
            <el-button
              v-if="displayedRequest.status === 'PENDING_REVIEW' && canReview"
              size="small"
              type="success"
              @click="reviewRequest(true)"
            >
              批准
            </el-button>
            <el-button
              v-if="displayedRequest.status === 'PENDING_REVIEW' && canReview"
              size="small"
              type="danger"
              @click="reviewRequest(false)"
            >
              驳回
            </el-button>
            <el-button
              v-if="canCancelRequest"
              size="small"
              type="warning"
              plain
              @click="cancelRequest"
            >
              取消申请
            </el-button>
          </div>
        </section>

        <el-alert
          v-if="preview.blockers?.length"
          :title="preview.blockers.join('；')"
          type="error"
          :closable="false"
          show-icon
          class="publish-alert"
        />

        <el-alert
          v-if="hasForcedTargets"
          :title="forceReviewText"
          type="warning"
          :closable="false"
          show-icon
          class="publish-alert"
        />

        <el-table
          v-if="preview.riskItems?.length"
          :data="preview.riskItems"
          size="small"
          max-height="220"
        >
          <el-table-column prop="section" label="区域" width="120" />
          <el-table-column label="修改项" min-width="220">
            <template #default="{ row }">
              <span :title="row.path">
                {{ publishPathLabel(row.path) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="riskLevel" label="风险" width="90">
            <template #default="{ row }">
              <el-tag :type="riskTagType(row.riskLevel)" size="small">
                {{ riskLabel(row.riskLevel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="说明" min-width="260" />
        </el-table>

        <el-table
          v-if="preview.targets?.length"
          :data="preview.targets"
          size="small"
          max-height="220"
          class="target-table"
        >
          <el-table-column prop="processName" label="流程" min-width="150" />
          <el-table-column prop="processVersion" label="版本" width="70" />
          <el-table-column prop="activeInstanceCount" label="运行中" width="80" />
          <el-table-column
            prop="skippedHistoricalInstanceCount"
            label="跳过历史"
            width="90"
          />
          <el-table-column label="应用方式" width="100">
            <template #default="{ row }">
              <el-tag :type="targetModeTagType(row)" size="small">
                {{ targetModeLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="220">
            <template #default="{ row }">
              {{
                row.blockers?.join('；')
                  || row.reviewNotes?.join('；')
                  || '可原子应用'
              }}
            </template>
          </el-table-column>
        </el-table>
      </template>
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="publishing"
        :disabled="!canSubmit"
        @click="submit"
      >
        {{ submitLabel }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import {
  previewFormPublish,
  publishForm
} from '@/api/entityForm'
import { entityListConfigApi } from '@/api/entityListConfig'
import { uiHotfixGovernanceApi } from '@/api/uiHotfixGovernance'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  modelValue: Boolean,
  configType: { type: String, required: true },
  configId: { type: String, required: true },
  configLabel: { type: String, default: 'UI 配置' }
})

const emit = defineEmits(['update:modelValue', 'published'])
const userStore = useUserStore()
const preview = ref(null)
const previewLoading = ref(false)
const publishing = ref(false)
const hotfixRequests = ref([])
const form = reactive({
  releaseMode: 'STANDARD',
  description: '',
  reason: '',
  ticketRef: '',
  windowStart: '',
  windowEnd: ''
})

const canHotfix = computed(() =>
  props.configType === 'FORM'
  && (userStore.isSuperAdmin
    || userStore.permissions.includes('entity:ui-config:hotfix'))
)
const canReview = computed(() =>
  userStore.isSuperAdmin
  || userStore.permissions.includes('entity:ui-config:hotfix:review')
)
const openRequest = computed(() =>
  hotfixRequests.value.find(item =>
    ['PENDING_REVIEW', 'APPROVED', 'PUBLISHING'].includes(item.status)
  ) || null
)
const matchingRequest = computed(() =>
  hotfixRequests.value.find(item =>
    ['PENDING_REVIEW', 'APPROVED', 'PUBLISHING'].includes(item.status)
    && matchesPreview(item)
  ) || null
)
const displayedRequest = computed(() =>
  matchingRequest.value
  || openRequest.value
  || hotfixRequests.value.find(item =>
    ['OBSERVING', 'OBSERVED_OK', 'OBSERVED_ALERT'].includes(item.status)
  )
  || null
)
const requestMatchesPreview = computed(() =>
  Boolean(displayedRequest.value && matchesPreview(displayedRequest.value))
)
const canCancelRequest = computed(() =>
  Boolean(openRequest.value
    && ['PENDING_REVIEW', 'APPROVED'].includes(openRequest.value.status))
)
const canSubmit = computed(() =>
  Boolean(preview.value?.changed
    && preview.value?.canPublish
    && (form.releaseMode !== 'HOTFIX'
      || (matchingRequest.value?.status === 'APPROVED')
      || (!openRequest.value && hotfixFieldsComplete.value)))
)
const hotfixFieldsComplete = computed(() =>
  Boolean(form.reason.trim()
    && form.ticketRef.trim()
    && form.windowStart
    && form.windowEnd
    && form.windowStart < form.windowEnd)
)
const forcedTargets = computed(() =>
  (preview.value?.targets || []).filter(target =>
    target.compatible && target.applicationMode === 'FULL_SNAPSHOT'
  )
)
const hasForcedTargets = computed(() =>
  form.releaseMode === 'HOTFIX' && forcedTargets.value.length > 0
)
const forceReviewText = computed(() =>
  `${forcedTargets.value.length} 个流程版本无法安全增量对齐，`
    + '将使用当前草稿的完整快照强制覆盖；新快照仍会完整校验并原子生效。'
)
const submitLabel = computed(() => {
  if (form.releaseMode !== 'HOTFIX') return '普通发布'
  if (matchingRequest.value?.status === 'PENDING_REVIEW') {
    return '等待独立复核'
  }
  if (matchingRequest.value?.status === 'APPROVED') {
    return '发布已批准热修复'
  }
  if (openRequest.value && !matchingRequest.value) {
    return '先取消过期申请'
  }
  return '提交热修复申请'
})

function requestPayload(includePreviewState = false) {
  return {
    releaseMode: form.releaseMode,
    rolloutScope: form.releaseMode === 'HOTFIX'
      ? 'ACTIVE_AND_FUTURE'
      : undefined,
    description: form.description,
    ...(includePreviewState && preview.value
      ? {
          expectedActiveReleaseId: preview.value.activeReleaseId,
          expectedDraftHash: preview.value.draftHash,
          impactToken: preview.value.impactToken,
          hotfixRequestId: matchingRequest.value?.id
        }
      : {})
  }
}

async function initialize() {
  form.releaseMode = 'STANDARD'
  form.description = ''
  form.reason = ''
  form.ticketRef = ''
  form.windowStart = localDateTime(0)
  form.windowEnd = localDateTime(120)
  hotfixRequests.value = []
  await loadPreview()
}

async function loadPreview() {
  previewLoading.value = true
  try {
    preview.value = props.configType === 'FORM'
      ? await previewFormPublish(props.configId, requestPayload())
      : await entityListConfigApi.previewPublish(
          props.configId,
          requestPayload()
        )
    if (form.releaseMode === 'HOTFIX') {
      await loadRequests()
      hydrateFromOpenRequest()
    }
  } catch (error) {
    preview.value = null
    ElMessage.error(error?.message || '发布预检失败')
  } finally {
    previewLoading.value = false
  }
}

async function submit() {
  if (form.releaseMode === 'HOTFIX') {
    await loadPreview()
  }
  if (!canSubmit.value) return
  publishing.value = true
  try {
    let authorization = matchingRequest.value
    if (form.releaseMode === 'HOTFIX' && !authorization) {
      authorization = await uiHotfixGovernanceApi.apply({
        configType: props.configType,
        configId: props.configId,
        expectedActiveReleaseId: preview.value.activeReleaseId,
        expectedDraftHash: preview.value.draftHash,
        impactToken: preview.value.impactToken,
        reason: form.reason.trim(),
        ticketRef: form.ticketRef.trim(),
        windowStart: form.windowStart,
        windowEnd: form.windowEnd
      })
      await loadRequests()
      if (authorization.status !== 'APPROVED') {
        ElMessage.success('HOTFIX 申请已提交，等待非申请人独立复核')
        return
      }
    }
    const payload = requestPayload(true)
    if (authorization?.id) payload.hotfixRequestId = authorization.id
    const release = props.configType === 'FORM'
      ? await publishForm(props.configId, payload)
      : await entityListConfigApi.publish(props.configId, payload)
    ElMessage.success(
      form.releaseMode === 'HOTFIX'
        ? '已批准热修复进入运行观察窗口'
        : '普通发布成功'
    )
    emit('published', release)
    emit('update:modelValue', false)
  } catch (error) {
    ElMessage.error(error?.message || '发布失败')
    if (error?.response?.status === 409 || error?.status === 409) {
      await loadPreview()
    }
  } finally {
    publishing.value = false
  }
}

async function loadRequests() {
  hotfixRequests.value = await uiHotfixGovernanceApi.list(
    props.configType,
    props.configId
  ) || []
}

function hydrateFromOpenRequest() {
  if (!openRequest.value) return
  form.reason = openRequest.value.reason || form.reason
  form.ticketRef = openRequest.value.ticketRef || form.ticketRef
  form.windowStart = openRequest.value.windowStart || form.windowStart
  form.windowEnd = openRequest.value.windowEnd || form.windowEnd
}

function matchesPreview(request) {
  return Boolean(preview.value
    && request?.draftHash === preview.value.draftHash
    && request?.activeReleaseId === preview.value.activeReleaseId
    && request?.targetHash === preview.value.targetHash)
}

async function reviewRequest(approved) {
  const { value } = await ElMessageBox.prompt(
    approved ? '确认批准该 HOTFIX 申请？' : '确认驳回该 HOTFIX 申请？',
    approved ? '独立复核批准' : '独立复核驳回',
    {
      inputPlaceholder: '请输入复核意见',
      inputValidator: text => Boolean(String(text || '').trim())
        || '复核意见不能为空'
    }
  )
  await uiHotfixGovernanceApi.review(
    displayedRequest.value.id,
    approved,
    value
  )
  await loadRequests()
  ElMessage.success(approved ? '复核已批准，可由发布人执行' : '申请已驳回')
}

async function cancelRequest() {
  const { value } = await ElMessageBox.prompt(
    '取消后将释放当前申请，可基于最新预检重新提交。',
    '取消 HOTFIX 申请',
    {
      type: 'warning',
      inputPlaceholder: '请输入取消原因',
      inputValidator: text => Boolean(String(text || '').trim())
        || '取消原因不能为空'
    }
  )
  await uiHotfixGovernanceApi.cancel(openRequest.value.id, value)
  await loadRequests()
  ElMessage.success('HOTFIX 申请已取消')
}

function localDateTime(offsetMinutes) {
  const value = new Date(Date.now() + offsetMinutes * 60 * 1000)
  value.setMinutes(value.getMinutes() - value.getTimezoneOffset())
  return value.toISOString().slice(0, 19)
}

function requestStatusLabel(status) {
  return {
    PENDING_REVIEW: '待独立复核',
    APPROVED: '已批准待发布',
    PUBLISHING: '发布中',
    OBSERVING: '观察中',
    OBSERVED_OK: '观察通过',
    OBSERVED_ALERT: '观察告警',
    REJECTED: '已驳回',
    ROLLED_BACK: '已回滚',
    CANCELLED: '已取消'
  }[status] || status || '未知'
}

function requestStatusType(status) {
  if (['APPROVED', 'OBSERVED_OK'].includes(status)) return 'success'
  if (['REJECTED', 'OBSERVED_ALERT', 'ROLLED_BACK'].includes(status)) return 'danger'
  if (['PENDING_REVIEW', 'OBSERVING', 'PUBLISHING'].includes(status)) return 'warning'
  return 'info'
}

function riskTagType(risk) {
  return {
    SAFE: 'success',
    REVIEW: 'warning',
    BLOCKED: 'warning'
  }[risk] || 'info'
}

function riskLabel(risk) {
  return {
    SAFE: '低风险',
    REVIEW: '需复核',
    BLOCKED: '需复核'
  }[risk] || '待评估'
}

function targetModeTagType(target) {
  if (!target?.compatible) return 'danger'
  return target.applicationMode === 'FULL_SNAPSHOT'
    ? 'warning'
    : 'success'
}

function targetModeLabel(target) {
  if (!target?.compatible) return '不可应用'
  return target.applicationMode === 'FULL_SNAPSHOT'
    ? '强制覆盖'
    : '增量'
}

function publishPathLabel(path) {
  const raw = String(path || '').trim()
  if (!raw) return '配置调整'
  const labels = [
    ['fieldInitializationMapping', '子字段初始化映射'],
    ['parameterMapping', '子表单运行参数映射'],
    ['parameterContract', '子表单参数传递契约'],
    ['inputParameterSchema', '子表单输入参数契约'],
    ['dataSourceBindingsDocument', '数据源绑定'],
    ['extensionConfig', '扩展配置'],
    ['viewConfig', '表单设置'],
    ['propsDocument', '节点属性']
  ]
  return labels.find(([key]) => raw.includes(key))?.[1] || raw
}
</script>

<style scoped>
.publish-mode-help {
  margin-top: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.publish-alert,
.target-table {
  margin-top: 12px;
}

.publish-preview {
  min-height: 96px;
}

.publish-summary {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
  color: var(--el-text-color-regular);
}

.release-window {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.governance-card {
  margin: 12px 0;
  padding: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  background: var(--el-fill-color-light);
}

.governance-card__header,
.governance-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.governance-actions {
  margin: 10px 0 0;
  justify-content: flex-end;
}

</style>
