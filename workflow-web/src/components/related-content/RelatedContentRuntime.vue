<template>
  <div
    v-if="shouldRender"
    class="related-content-runtime"
    :class="{ 'is-compact': compact }"
  >
    <el-alert
      v-if="resolved && actionCapabilityError"
      title="关联操作暂不可用"
      :description="actionCapabilityError"
      type="warning"
      :closable="false"
      show-icon
      class="related-content-runtime__action-warning"
    />
    <template v-if="inlinePresentation">
      <el-card shadow="never" class="related-content-runtime__card">
        <template #header>
          <div class="related-content-runtime__header">
            <span>{{ displayTitle }}</span>
            <div class="related-content-runtime__actions">
              <el-button
                v-if="resolved"
                link
                type="primary"
                :loading="loading || actionLoading"
                @click="refresh"
              >
                刷新
              </el-button>
              <el-button
                v-if="canEditTarget"
                link
                type="primary"
                :disabled="actionLoading"
                @click="editTarget"
              >
                编辑
              </el-button>
              <el-button
                v-if="canCreateTarget"
                link
                type="primary"
                :disabled="actionLoading"
                @click="createTarget"
              >
                新增
              </el-button>
              <el-button
                v-if="canPickLinkCandidate"
                link
                type="primary"
                :disabled="actionLoading"
                @click="openLinkCandidates"
              >
                选择并建立关联
              </el-button>
            </div>
          </div>
        </template>
        <RuntimeBody />
      </el-card>
    </template>

    <el-button
      v-else
      type="primary"
      link
      :loading="loading"
      :disabled="!hasSourceContext"
      @click="openPresentation"
    >
      {{ triggerLabel }}
    </el-button>

    <el-dialog
      v-if="dialogPresentation"
      v-model="presentationVisible"
      :title="displayTitle"
      :width="pagePresentation ? '96%' : '75%'"
      :fullscreen="pagePresentation"
      destroy-on-close
    >
      <div
        v-if="canEditTarget || canCreateTarget || canPickLinkCandidate"
        class="related-content-runtime__presentation-actions"
      >
        <el-button
          v-if="canEditTarget"
          type="primary"
          link
          :disabled="actionLoading"
          @click="editTarget"
        >
          编辑
        </el-button>
        <el-button
          v-if="canCreateTarget"
          type="primary"
          link
          :disabled="actionLoading"
          @click="createTarget"
        >
          新增
        </el-button>
        <el-button
          v-if="canPickLinkCandidate"
          type="primary"
          link
          :disabled="actionLoading"
          @click="openLinkCandidates"
        >
          选择并建立关联
        </el-button>
      </div>
      <RuntimeBody />
    </el-dialog>

    <el-drawer
      v-if="drawerPresentation"
      v-model="presentationVisible"
      :title="displayTitle"
      size="75%"
      destroy-on-close
    >
      <div
        v-if="canEditTarget || canCreateTarget || canPickLinkCandidate"
        class="related-content-runtime__presentation-actions"
      >
        <el-button
          v-if="canEditTarget"
          type="primary"
          link
          :disabled="actionLoading"
          @click="editTarget"
        >
          编辑
        </el-button>
        <el-button
          v-if="canCreateTarget"
          type="primary"
          link
          :disabled="actionLoading"
          @click="createTarget"
        >
          新增
        </el-button>
        <el-button
          v-if="canPickLinkCandidate"
          type="primary"
          link
          :disabled="actionLoading"
          @click="openLinkCandidates"
        >
          选择并建立关联
        </el-button>
      </div>
      <RuntimeBody />
    </el-drawer>

    <AsyncEntityDataFormDialog
      v-if="targetForm && targetEntity"
      ref="formDialogRef"
      :entity-code="resolved?.targetEntityCode || ''"
      :entity-definition="targetEntity"
      :entity-fields="targetEntity.fields || []"
      :default-form="targetForm"
      :entity-status-options="targetEntityStatusOptions"
      @success="handleTargetSaved"
    />

    <el-dialog
      v-model="candidateVisible"
      title="选择要关联的记录"
      width="82%"
      destroy-on-close
    >
      <el-skeleton v-if="candidateLoading" :rows="5" animated />
      <el-alert
        v-else-if="candidateError"
        title="候选记录加载失败"
        :description="candidateError"
        type="error"
        :closable="false"
        show-icon
      />
      <el-empty
        v-else-if="candidateContext?.matchNone"
        description="当前没有可建立关联的记录；单值关系已有记录时，请先解除原关联"
      />
      <AsyncEntityDataList
        v-else-if="candidateContext"
        :entity-code="candidateContext.targetEntityCode"
        :list-key="candidateContext.targetContentKey"
        :release-id="candidateContext.targetReleaseId"
        :release-version="candidateContext.targetReleaseVersion"
        :view-composition-context-token="candidateContext.candidateListContextToken"
        :view-composition-traversal-token="resolved?.traversalContextToken"
        :selection-mode="candidateSelectionMode"
        :selection-action-options="candidateActionOptions"
        scene="EMBEDDED"
        embedded
        :show-toolbar="false"
        :show-row-actions="false"
        :show-search="true"
        :show-pagination="true"
        @selection-action="executeCandidateSelectionAction"
      />
    </el-dialog>
  </div>
</template>

<script setup>
import {
  computed,
  defineAsyncComponent,
  defineComponent,
  h,
  ref,
  watch
} from 'vue'
import { ElAlert, ElButton, ElEmpty, ElMessage, ElSkeleton } from 'element-plus'
import { entityApi, entityDataApi } from '@/api/entity'
import { getFormRuntimeRelease } from '@/api/entityForm'
import { getEntityStatusList } from '@/api/entityStatus'
import { entityListRuntimeApi } from '@/api/entityListRuntime'
import { uiCompositionRuntimeApi } from '@/api/uiCompositionRuntime'
import { normalizeRuntimeFormRelease } from '@/shared/list-button-form-runtime'
import { normalizeEntityRecordForForm } from '@/shared/form-runtime'
import {
  buildEntityStatusMap,
  getEffectiveEntityStatusOptions
} from '@/shared/entity-status-runtime'
import {
  assertRelatedContentResolveContract,
  buildRelatedContentResolveInput,
  createLatestRequestGate,
  normalizeRelatedContentRuntimeActions
} from '@/shared/related-content-runtime'
import {
  getCustomFormComponent,
  getCustomListComponent,
  hasCustomFormComponent,
  hasCustomListComponent
} from '@/utils/customComponentRegistry'

const AsyncEntityDataList = defineAsyncComponent(
  () => import('@/views/entity/EntityDataList.vue')
)
const AsyncFormPreviewLinkage = defineAsyncComponent(
  () => import('@/components/FormPreviewLinkage.vue')
)
const AsyncEntityDataFormDialog = defineAsyncComponent(
  () => import('@/views/entity/components/EntityDataFormDialog.vue')
)

defineOptions({ name: 'RelatedContentRuntime' })

const props = defineProps({
  composition: { type: Object, required: true },
  ownerType: { type: String, required: true },
  ownerId: { type: [String, Number], required: true },
  releaseId: { type: String, required: true },
  releaseVersion: { type: Number, required: true },
  sourceRecordId: { type: [String, Number], default: '' },
  hostReadonly: { type: Boolean, default: false },
  rowContextToken: { type: String, default: '' },
  traversalContextToken: { type: String, default: '' },
  releaseResolutionToken: { type: String, default: '' },
  compact: Boolean
})

const emit = defineEmits(['target-saved', 'source-patch'])
const resolved = ref(null)
const loading = ref(false)
const actionLoading = ref(false)
const errorMessage = ref('')
const actionCapabilityError = ref('')
const actionCapabilities = ref({})
const actionFailure = ref(null)
const presentationVisible = ref(false)
const candidateVisible = ref(false)
const candidateLoading = ref(false)
const candidateError = ref('')
const candidateContext = ref(null)
const targetEntity = ref(null)
const targetForm = ref(null)
const targetRecord = ref(null)
const targetEntityStatusOptions = ref(getEffectiveEntityStatusOptions())
const formDialogRef = ref(null)
const resolvedContextFingerprint = ref('')
const requestGate = createLatestRequestGate()

const config = computed(() => props.composition?.config || {})
const presentation = computed(() => config.value.presentation || {})
const specialHandling = computed(() => config.value.specialHandling || {})
const customComponentConfig = computed(
  () => specialHandling.value.customComponent || {}
)
const position = computed(() =>
  String(presentation.value.position || 'INLINE').toUpperCase()
)
const inlinePresentation = computed(() =>
  ['INLINE', 'TAB', 'ROW_EXPAND'].includes(position.value)
)
const drawerPresentation = computed(() => position.value === 'DRAWER')
const pagePresentation = computed(() => position.value === 'PAGE')
const dialogPresentation = computed(() =>
  position.value === 'DIALOG' || pagePresentation.value
)
const loadImmediately = computed(() =>
  String(presentation.value.loadMode || 'ON_DEMAND').toUpperCase()
    === 'IMMEDIATE'
)
const failurePolicy = computed(() =>
  String(specialHandling.value.failurePolicy || 'ERROR').toUpperCase()
)
const hasSourceContext = computed(() => Boolean(
  String(props.sourceRecordId || '').trim()
    || String(props.rowContextToken || '').trim()
))
const displayTitle = computed(() =>
  presentation.value.title
    || config.value.name
    || config.value.target?.contentName
    || '关联内容'
)
const triggerLabel = computed(() =>
  position.value === 'PAGE'
    ? `打开${displayTitle.value}`
    : `查看${displayTitle.value}`
)
const publishedActionServiceKeys = computed(() => new Set(
  (specialHandling.value.actionServices || [])
    .map(binding => String(binding?.actionKey || '').trim().toUpperCase())
    .filter(action => /^[A-Z][A-Z0-9_.-]{0,99}$/.test(action))
))
const actionServiceBindings = computed(() => new Map(
  (specialHandling.value.actionServices || [])
    .map(binding => [
      String(binding?.actionKey || '').trim().toUpperCase(),
      binding
    ])
    .filter(([actionKey]) => actionKey)
))
const actions = computed(() => {
  const available = Object.entries(actionCapabilities.value || {})
    .filter(([, capability]) => capability?.available === true)
    .map(([action]) => String(action || '').trim().toUpperCase())
  const standard = normalizeRelatedContentRuntimeActions(available)
  const pinnedServices = available.filter(action =>
    publishedActionServiceKeys.value.has(action))
  return new Set([...standard, ...pinnedServices])
})
const actionContextToken = computed(() => String(
  resolved.value?.actionContextToken
  || resolved.value?.rowContextToken
  || ''
))
const selectSettings = computed(() => config.value.actionSettings?.select || {})
const selectionActionOptions = computed(() => {
  const result = []
  const selectResult = String(selectSettings.value.result || '').toUpperCase()
  if (!props.hostReadonly
    && actions.value.has('SELECT')
    && selectResult !== 'LINK') {
    result.push({
      action: 'SELECT',
      label: '确认选择',
      type: 'primary'
    })
  }
  if (actions.value.has('UNLINK')) {
    result.push({ action: 'UNLINK', label: '解除关联', type: 'danger' })
  }
  return result
})
const linkCandidateAction = computed(() => {
  const selectResult = String(selectSettings.value.result || '').toUpperCase()
  if (actions.value.has('SELECT')
    && actions.value.has('LINK')
    && selectResult === 'LINK') return 'SELECT'
  return actions.value.has('LINK') ? 'LINK' : ''
})
const canPickLinkCandidate = computed(() =>
  resolved.value?.targetContentType === 'LIST'
    && Boolean(linkCandidateAction.value)
)
const candidateSelectionMode = computed(() => {
  if (linkCandidateAction.value === 'SELECT') {
    return String(selectSettings.value.mode || 'SINGLE').toUpperCase()
  }
  return String(config.value.relation?.type || '').toUpperCase()
    === 'REFERENCE_FIELD' ? 'SINGLE' : 'MULTIPLE'
})
const candidateActionOptions = computed(() => linkCandidateAction.value
  ? [{
      action: linkCandidateAction.value,
      label: '选择并建立关联',
      type: 'primary'
    }]
  : []
)
const runtimeSelectionMode = computed(() => {
  if (!selectionActionOptions.value.length) return 'NONE'
  if (actions.value.has('SELECT')) {
    return String(selectSettings.value.mode || 'SINGLE').toUpperCase()
  }
  return String(config.value.relation?.type || '').toUpperCase()
    === 'REFERENCE_FIELD' ? 'SINGLE' : 'MULTIPLE'
})
const canEditTarget = computed(() =>
  resolved.value?.targetContentType === 'FORM'
    && Boolean(resolved.value?.targetRecordId)
    && actions.value.has('EDIT')
)
const canCreateTarget = computed(() =>
  resolved.value?.targetContentType === 'FORM'
    && actions.value.has('CREATE')
)
const shouldRender = computed(() =>
  config.value.enabled !== false
    && !(errorMessage.value && failurePolicy.value === 'HIDE')
    && actionFailure.value?.policy !== 'HIDE'
)
const isCustomMode = computed(() =>
  ['CUSTOM_COMPONENT', 'BOTH'].includes(
    String(specialHandling.value.mode || 'NONE').toUpperCase()
  )
)
const customComponent = computed(() => {
  if (!isCustomMode.value) return null
  const name = customComponentConfig.value.name
  const version = customComponentConfig.value.version
  const artifactDigest = customComponentConfig.value.artifactDigest
  if (resolved.value?.targetContentType === 'FORM') {
    return hasCustomFormComponent(name, version, artifactDigest)
      ? getCustomFormComponent(name, version, artifactDigest)
      : null
  }
  return hasCustomListComponent(name, version, artifactDigest)
    ? getCustomListComponent(name, version, artifactDigest)
    : null
})

/**
 * 解析失败与扩展缺失共享同一显式失败策略。HIDE/PLACEHOLDER 不能因为错误
 * 来源不同而退化为告警；只有 ERROR 才向用户展示错误详情。
 */
function renderFailureState(title, description) {
  if (failurePolicy.value === 'HIDE') return null
  if (failurePolicy.value === 'PLACEHOLDER') {
    return h(ElEmpty, { description })
  }
  return h(ElAlert, {
    title,
    description,
    type: 'error',
    closable: false,
    showIcon: true
  })
}

/**
 * 所有目标身份、筛选条件和发布版本都来自服务端解析结果。解析失败时严格
 * 遵循显式失败策略，绝不改用目标当前版本或无条件列表查询。
 */
async function resolveComposition({ force = false } = {}) {
  const input = currentResolveInput()
  const inputFingerprint = JSON.stringify(input)
  if (!force
    && resolved.value
    && resolvedContextFingerprint.value === inputFingerprint) {
    return resolved.value
  }
  const requestRevision = requestGate.begin()
  if (!input.recordId && !input.rowContextToken) {
    loading.value = false
    clearResolvedState()
    errorMessage.value = '请先保存当前记录，再加载关联内容。'
    return null
  }
  loading.value = true
  errorMessage.value = ''
  clearResolvedState()
  try {
    const value = assertRelatedContentResolveContract(
      await uiCompositionRuntimeApi.resolve(input)
    )
    const [targetState, capabilityState] = await Promise.all([
      value.targetContentType === 'FORM'
        ? loadTargetForm(value)
        : Promise.resolve(null),
      loadActionCapabilities(value)
    ])

    // 表单弹窗会复用组件。切换记录时旧请求可能后返回，只有仍对应当前
    // 宿主上下文的最后一次请求可以提交结果，避免展示或编辑上一条记录。
    if (!requestGate.isCurrent(requestRevision)
      || JSON.stringify(currentResolveInput()) !== inputFingerprint) {
      return null
    }
    resolved.value = value
    actionCapabilities.value = capabilityState.capabilities
    actionCapabilityError.value = capabilityState.error
    resolvedContextFingerprint.value = inputFingerprint
    if (value?.targetContentType === 'FORM') {
      targetEntity.value = targetState.entity
      targetForm.value = targetState.form
      targetRecord.value = targetState.record
      targetEntityStatusOptions.value = targetState.statuses
    }
    return value
  } catch (error) {
    if (!requestGate.isCurrent(requestRevision)) return null
    clearResolvedState()
    errorMessage.value = error?.message || '关联内容解析失败'
    return null
  } finally {
    if (requestGate.isCurrent(requestRevision)) {
      loading.value = false
    }
  }
}

async function loadActionCapabilities(value) {
  const token = String(value?.actionContextToken || value?.rowContextToken || '')
  if (!token) {
    return {
      capabilities: {},
      error: '服务端未返回可信动作凭证，已停止开放新增、编辑和关系操作。'
    }
  }
  try {
    const response = await uiCompositionRuntimeApi.capabilities(token)
    return {
      capabilities: response?.actionCapabilities || {},
      error: ''
    }
  } catch (error) {
    return {
      capabilities: {},
      error: error?.message || '无法确认当前用户的关联操作权限。'
    }
  }
}

async function loadTargetForm(value) {
  const traversalRuntimeContext = {
    viewCompositionTraversalToken: value.traversalContextToken
  }
  const [entity, release, statuses] = await Promise.all([
    entityApi.getByCode(value.targetEntityCode, traversalRuntimeContext),
    getFormRuntimeRelease(
      value.targetContentId,
      value.targetReleaseId,
      value.targetReleaseVersion,
      value.targetReleaseResolutionToken
    ),
    getEntityStatusList(value.targetEntityCode, traversalRuntimeContext)
  ])
  const form = normalizeRuntimeFormRelease(
    release,
    value.targetContentId,
    release.releaseResolutionToken || value.targetReleaseResolutionToken
  )
  if (!value.targetRecordId) {
    return {
      entity,
      form,
      record: null,
      statuses: getEffectiveEntityStatusOptions(statuses)
    }
  }
  const detail = await entityDataApi.getDetail(
    value.targetEntityCode,
    value.targetRecordId,
    null,
    value.targetContentId,
    {},
    {
      releaseId: value.targetReleaseId,
      releaseVersion: value.targetReleaseVersion,
      releaseResolutionToken:
        form.releaseResolutionToken
        || value.targetReleaseResolutionToken,
      viewCompositionTraversalToken: value.traversalContextToken
    }
  )
  return {
    entity,
    form,
    record: normalizeEntityRecordForForm(detail),
    statuses: getEffectiveEntityStatusOptions(statuses)
  }
}

function currentResolveInput() {
  return buildRelatedContentResolveInput({
    ownerType: props.ownerType,
    ownerId: props.ownerId,
    releaseId: props.releaseId,
    releaseVersion: props.releaseVersion,
    compositionKey: props.composition?.compositionKey,
    sourceRecordId: props.sourceRecordId,
    rowContextToken: props.rowContextToken,
    traversalContextToken: props.traversalContextToken,
    releaseResolutionToken: props.releaseResolutionToken
  })
}

function clearResolvedState() {
  resolved.value = null
  resolvedContextFingerprint.value = ''
  targetEntity.value = null
  targetForm.value = null
  targetRecord.value = null
  targetEntityStatusOptions.value = getEffectiveEntityStatusOptions()
  actionCapabilities.value = {}
  actionCapabilityError.value = ''
  actionFailure.value = null
  candidateVisible.value = false
  candidateLoading.value = false
  candidateError.value = ''
  candidateContext.value = null
}

/**
 * 动作失败策略只影响错误的呈现，绝不把失败伪装成成功。WRITE 在发布期被
 * 强制为 ERROR；READ 可选择隐藏关联内容或显示占位，但 Promise 仍保持失败，
 * 让调用组件明确停止后续操作。
 */
function handleActionFailure(action, error) {
  const actionKey = String(action || '').trim().toUpperCase()
  const binding = actionServiceBindings.value.get(actionKey) || {}
  const policy = String(binding.failurePolicy || 'ERROR').toUpperCase()
  const message = error?.message || '关联操作失败'
  if (policy === 'HIDE' || policy === 'PLACEHOLDER') {
    actionFailure.value = { actionKey, policy, message }
  } else {
    actionFailure.value = null
    ElMessage.error(message)
  }
  throw error
}

async function openPresentation() {
  const value = await resolveComposition()
  if (!value && failurePolicy.value === 'HIDE') return
  presentationVisible.value = true
}

async function refresh() {
  await resolveComposition({ force: true })
}

async function editTarget() {
  if (!actions.value.has('EDIT')
    || !actionContextToken.value
    || !targetForm.value
    || !resolved.value?.targetRecordId) return
  await formDialogRef.value?.openEdit(
    { id: resolved.value.targetRecordId },
    {
      form: targetForm.value,
      context: {
        viewCompositionActionContextToken: actionContextToken.value,
        viewCompositionTraversalToken:
          resolved.value.traversalContextToken
      }
    }
  )
}

async function createTarget() {
  if (!actions.value.has('CREATE') || !actionContextToken.value) return
  if (!targetForm.value) {
    const value = await resolveComposition()
    if (!value || !targetForm.value) return
  }
  await formDialogRef.value?.openCreate({
    form: targetForm.value,
    initialData:
      actionCapabilities.value?.CREATE?.initialValues || {},
    context: {
      viewCompositionActionContextToken: actionContextToken.value,
      viewCompositionTraversalToken:
        resolved.value.traversalContextToken
    }
  })
}

/**
 * 候选弹窗只请求服务端根据已发布关系签发的专用列表上下文，不向接口提交
 * entityCode、listKey 或筛选条件。这样“当前已关联列表”和“可新建关联候选”
 * 始终是两个用途隔离的可信范围。
 */
async function openLinkCandidates() {
  if (!canPickLinkCandidate.value
    || !actionContextToken.value
    || candidateLoading.value) return
  candidateVisible.value = true
  candidateLoading.value = true
  candidateError.value = ''
  candidateContext.value = null
  try {
    candidateContext.value = await uiCompositionRuntimeApi.linkCandidates(
      actionContextToken.value,
      linkCandidateAction.value
    )
  } catch (error) {
    candidateError.value = error?.message || '无法加载可建立关联的候选记录'
  } finally {
    candidateLoading.value = false
  }
}

async function handleTargetSaved() {
  await refresh()
  emit('target-saved', resolved.value)
}

function createOperationId(action) {
  const suffix = globalThis.crypto?.randomUUID?.()
    || `${Date.now()}-${Math.random().toString(36).slice(2)}`
  // 同一个安全格式 operationId 同时贯穿请求 Trace、系统审计和实体变更回执。
  return `related-content_${String(action || '').toLowerCase()}_${suffix}`
}

/**
 * 浏览器只传签名上下文和选中记录 ID。服务端会重新读取记录、验证目标列表
 * 范围并应用发布时固定的映射；前端行数据绝不会直接写入宿主或关系字段。
 */
async function executeSelectionAction(
  action,
  rows = [],
  candidateListContextToken = ''
) {
  if (!actionContextToken.value || actionLoading.value) return null
  const targetRecordIds = rows
    .map(row => String(row?.id || '').trim())
    .filter(Boolean)
  if (!targetRecordIds.length) {
    ElMessage.warning('请先选择目标记录')
    return null
  }
  actionLoading.value = true
  actionFailure.value = null
  try {
    const result = await uiCompositionRuntimeApi.executeAction({
      actionContextToken: actionContextToken.value,
      candidateListContextToken: candidateListContextToken || undefined,
      action,
      targetRecordIds,
      input: {},
      operationId: createOperationId(action)
    })
    if (result?.actionCapabilities) {
      actionCapabilities.value = result.actionCapabilities
    }
    if (result?.sourcePatch
      && Object.keys(result.sourcePatch).length > 0) {
      emit('source-patch', result.sourcePatch)
    }
    if (Array.isArray(result?.changedReferences)
      && result.changedReferences.length > 0) {
      await refresh()
      emit('target-saved', resolved.value)
      candidateVisible.value = false
      candidateContext.value = null
    }
    ElMessage.success(
      String(action).toUpperCase() === 'UNLINK'
        ? '关联已解除'
        : String(action).toUpperCase() === 'LINK'
          ? '关联已建立'
          : '选择结果已处理'
    )
    if (!inlinePresentation.value) presentationVisible.value = false
    return result
  } catch (error) {
    return handleActionFailure(action, error)
  } finally {
    actionLoading.value = false
  }
}

/**
 * 平台只向审核过的一方可信组件提供宿主发布快照已声明的 actionKey；这层
 * bridge 不是任意第三方代码的强沙箱。payload 中的 serviceId、operationCode、
 * input 等字段全部忽略，服务端从签名宿主版本恢复钉定接口和字段映射。
 */
async function executeBoundAction(action, payload = {}) {
  const actionKey = String(action || '').trim().toUpperCase()
  if (!actionKey || !actions.value.has(actionKey)) {
    ElMessage.error('当前发布版本未开放该操作')
    return null
  }
  if (['SELECT', 'LINK', 'UNLINK'].includes(actionKey)) {
    return executeSelectionAction(
      actionKey,
      (payload.targetRecordIds || []).map(id => ({ id }))
    )
  }
  if (!actionContextToken.value || actionLoading.value) return null
  const targetRecordIds = Array.from(new Set(
    (payload.targetRecordIds || [])
      .map(id => String(id || '').trim())
      .filter(Boolean)
  ))
  actionLoading.value = true
  actionFailure.value = null
  try {
    const result = await uiCompositionRuntimeApi.executeAction({
      actionContextToken: actionContextToken.value,
      action: actionKey,
      targetRecordIds,
      input: {},
      operationId: createOperationId(actionKey)
    })
    if (result?.actionCapabilities) {
      actionCapabilities.value = result.actionCapabilities
    }
    ElMessage.success('操作已完成')
    return result
  } catch (error) {
    return handleActionFailure(actionKey, error)
  } finally {
    actionLoading.value = false
  }
}

async function executeCandidateSelectionAction(action, rows = []) {
  const token = String(
    candidateContext.value?.candidateListContextToken || ''
  )
  if (!token || action !== linkCandidateAction.value) {
    ElMessage.error('候选列表上下文已失效，请重新打开')
    return null
  }
  return executeSelectionAction(action, rows, token)
}

/**
 * 平台提供的受控 bridge 只查询当前已钉定列表，不下发固定筛选、连接器配置
 * 或凭据，也不把失败降级为普通查询。组件本身仍须是一方审核代码；此处不
 * 宣称对不受信任 JavaScript 提供强沙箱隔离。
 */
async function queryTarget(input = {}) {
  const value = assertRelatedContentResolveContract(resolved.value)
  if (value.targetContentType !== 'LIST') {
    throw new Error('当前关联内容不是列表，不能执行列表查询')
  }
  return entityListRuntimeApi.query(
    value.targetEntityCode,
    value.targetContentKey,
    {
      pageNum: Number(input.pageNum || 1),
      pageSize: Number(input.pageSize || 20),
      scene: 'EMBEDDED',
      releaseId: value.targetReleaseId,
      releaseVersion: value.targetReleaseVersion,
      viewCompositionContextToken: value.listContextToken,
      filters: input.filters || {}
    }
  )
}

const RuntimeBody = defineComponent({
  name: 'RelatedContentRuntimeBody',
  setup() {
    return () => {
      if (loading.value) {
        return h(ElSkeleton, { rows: 4, animated: true })
      }
      if (errorMessage.value) {
        return renderFailureState(
          '关联内容加载失败',
          errorMessage.value
        )
      }
      if (actionFailure.value?.policy === 'PLACEHOLDER') {
        return h(ElEmpty, {
          description: actionFailure.value.message || '关联操作暂不可用'
        })
      }
      if (!resolved.value) {
        if (!hasSourceContext.value) {
          return h(ElEmpty, { description: '请先保存当前记录，再加载关联内容。' })
        }
        return h('div', { class: 'related-content-runtime__load' }, [
          h(ElButton, {
            type: 'primary',
            onClick: () => resolveComposition()
          }, () => '加载关联内容')
        ])
      }
      if (resolved.value.matchNone) {
        return h(ElEmpty, {
          description: presentation.value.emptyText || '未找到关联数据'
        })
      }
      if (isCustomMode.value && !customComponent.value) {
        return renderFailureState(
          '自定义组件不可用',
          `未注册组件 ${customComponentConfig.value.name || '-'} v${customComponentConfig.value.version || '-'}`
        )
      }
      if (customComponent.value) {
        return h(customComponent.value, {
          form: targetForm.value,
          modelValue: targetRecord.value || {},
          readonly: !actions.value.has('EDIT'),
          fields: targetForm.value?.fields || [],
          entityCode: resolved.value.targetEntityCode,
          entityDefinition: targetEntity.value,
          config: customComponentConfig.value.props || {},
          context: {
            compositionKey: resolved.value.compositionKey,
            targetReleaseId: resolved.value.targetReleaseId,
            targetReleaseVersion: resolved.value.targetReleaseVersion,
            entityStatusOptions: targetEntityStatusOptions.value,
            entityStatusMap:
              buildEntityStatusMap(targetEntityStatusOptions.value),
            viewCompositionTraversalToken:
              resolved.value.traversalContextToken
          },
          runtime: {
            refresh,
            query: queryTarget,
            dispatch: executeBoundAction,
            openLinkCandidates,
            capabilities: Array.from(actions.value),
            actionCapabilities: actionCapabilities.value
          }
        })
      }
      if (resolved.value.targetContentType === 'LIST') {
        return h(AsyncEntityDataList, {
          entityCode: resolved.value.targetEntityCode,
          listKey: resolved.value.targetContentKey,
          releaseId: resolved.value.targetReleaseId,
          releaseVersion: resolved.value.targetReleaseVersion,
          viewCompositionContextToken: resolved.value.listContextToken,
          viewCompositionTraversalToken:
            resolved.value.traversalContextToken,
          relatedContentActions: Array.from(actions.value),
          selectionMode: runtimeSelectionMode.value,
          selectionActionOptions: selectionActionOptions.value,
          scene: 'EMBEDDED',
          embedded: true,
          showToolbar: actions.value.has('CREATE'),
          showRowActions:
            actions.value.has('VIEW') || actions.value.has('EDIT'),
          showSearch: true,
          showPagination: true,
          onSelectionAction: executeSelectionAction
        })
      }
      if (targetForm.value && targetRecord.value) {
        return h(AsyncFormPreviewLinkage, {
          form: targetForm.value,
          modelValue: targetRecord.value,
          // 内嵌表单始终只读；编辑通过独立目标表单保存，避免用户误认为
          // 目标数据会跟随宿主表单一并提交。
          readonly: true,
          mode: 'view',
          showHeader: false,
          height: 'auto',
          entityCode: resolved.value.targetEntityCode,
          entityDefinition: targetEntity.value,
          entityFields: targetEntity.value?.fields || [],
          context: {
            record: {
              id: resolved.value.targetRecordId,
              data: targetRecord.value
            },
            releaseResolutionToken:
              targetForm.value.releaseResolutionToken,
            entityStatusOptions: targetEntityStatusOptions.value,
            entityStatusMap:
              buildEntityStatusMap(targetEntityStatusOptions.value),
            viewCompositionTraversalToken:
              resolved.value.traversalContextToken
          }
        })
      }
      return h(ElEmpty, {
        description: presentation.value.emptyText || '未找到关联数据'
      })
    }
  }
})

watch(
  () => [
    props.ownerType,
    props.ownerId,
    props.releaseId,
    props.releaseVersion,
    props.composition?.compositionKey,
    props.sourceRecordId,
    props.rowContextToken,
    props.traversalContextToken,
    props.releaseResolutionToken
  ],
  () => {
    requestGate.invalidate()
    loading.value = false
    errorMessage.value = ''
    clearResolvedState()
    if (inlinePresentation.value && loadImmediately.value) {
      resolveComposition()
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.related-content-runtime {
  width: 100%;
}

.related-content-runtime__card {
  margin: 12px 0;
}

.related-content-runtime__action-warning {
  margin-bottom: 8px;
}

.related-content-runtime__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
}

.related-content-runtime__actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.related-content-runtime__presentation-actions {
  display: flex;
  justify-content: flex-end;
  gap: 4px;
  margin-bottom: 8px;
}

.related-content-runtime__load {
  display: flex;
  justify-content: center;
  padding: 24px;
}

.related-content-runtime.is-compact .related-content-runtime__card {
  margin: 6px 0;
}

.related-content-runtime.is-compact {
  display: inline-block;
  width: auto;
}
</style>
