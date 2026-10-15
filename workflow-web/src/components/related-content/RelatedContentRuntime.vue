<template>
  <div
    v-if="shouldRender"
    class="related-content-runtime"
    :class="{ 'is-compact': compact }"
    :style="relatedContentLayoutStyle(presentation)"
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
      <el-card shadow="never" class="related-content-runtime__card" :class="{ 'is-borderless': presentation.showBorder === false }">
        <template v-if="presentation.showTitle !== false || showTopActions" #header>
          <div class="related-content-runtime__header">
            <span v-if="presentation.showTitle !== false">{{ displayTitle }}</span>
            <RuntimeActions v-if="showTopActions" inline />
          </div>
        </template>
        <div :inert="targetSaveLoading" v-loading="targetSaveLoading"><RuntimeBody /></div>
        <RuntimeActions v-if="showBottomActions" inline bottom />
      </el-card>
    </template>

    <BuiltInActionButton
      v-else-if="showTrigger"
      action-key="open"
      :config="presentation.actionBar"
      :defaults="{ label: triggerLabel, buttonType: 'primary', buttonAppearance: 'LINK' }"
      :loading="loading"
      :disabled="!hasSourceContext"
      @click="openPresentation"
    />

    <el-dialog
      v-if="dialogPresentation"
      v-model="presentationVisible"
      :title="presentation.showTitle === false ? '' : displayTitle"
      :width="pagePresentation ? '96%' : '75%'"
      :fullscreen="pagePresentation"
      :before-close="beforePresentationClose"
      destroy-on-close
    >
      <RuntimeActions v-if="showTopActions" />
      <div :inert="targetSaveLoading" v-loading="targetSaveLoading"><RuntimeBody /></div>
      <RuntimeActions v-if="showBottomActions" bottom />
    </el-dialog>

    <el-drawer
      v-if="drawerPresentation"
      v-model="presentationVisible"
      :title="presentation.showTitle === false ? '' : displayTitle"
      size="75%"
      :before-close="beforePresentationClose"
      destroy-on-close
    >
      <RuntimeActions v-if="showTopActions" />
      <div :inert="targetSaveLoading" v-loading="targetSaveLoading"><RuntimeBody /></div>
      <RuntimeActions v-if="showBottomActions" bottom />
    </el-drawer>

  </div>
</template>

<script setup>
import BuiltInActionButton from '@/components/BuiltInActionButton.vue'
import { builtInActionBarHasPosition, builtInActionBarStyle, resolveBuiltInButton } from '@flow/workflow-core/builtin-button-appearance'
import { buildRuntimeContext } from '@flow/workflow-core/form-runtime/context'
import { uiExtensionRuntimeApi } from '@/api/uiConfig'
import {
  computed,
  defineAsyncComponent,
  defineComponent,
  h,
  inject,
  onBeforeUnmount,
  ref,
  watch
} from 'vue'
import { ElAlert, ElButton, ElEmpty, ElMessage, ElSkeleton } from 'element-plus'
import { relatedContentIsVisible, relatedContentLayoutStyle, relatedContentListProps } from '@/shared/related-content-presentation'
import { entityApi, entityDataApi } from '@/api/entity'
import { getFormRuntimeRelease } from '@/api/entityForm'
import { getEntityStatusList } from '@/api/entityStatus'
import { entityListRuntimeApi } from '@/api/entityListRuntime'
import { uiCompositionRuntimeApi } from '@/api/uiCompositionRuntime'
import { normalizeRuntimeFormRelease } from '@/shared/list-button-form-runtime'
import { mapPageParameters, resolvePageParameters, initializePageFields, pageParameterFields } from '@flow/workflow-core/page-parameters'
import { normalizeEntityRecordForForm, createFormDataSourceRuntime, applyRuntimeFieldDefaults, filterRuntimeFormSubmissionData } from '@flow/workflow-core/form-runtime'
import { useUnsavedChangesGuard } from '@/composables/useUnsavedChangesGuard'
import { relatedFormDraftGuardKey } from '@/shared/related-form-draft-guard'
import {
  buildEntityStatusMap,
  getEffectiveEntityStatusOptions
} from '@flow/workflow-core/entity-status-runtime'
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
} from '@/extensions/core/registries/customComponentRegistry.js'

const AsyncEntityDataList = defineAsyncComponent(
  () => import('@/views/entity/EntityDataList.vue')
)
const AsyncFormPreviewLinkage = defineAsyncComponent(
  () => import('@/components/FormPreviewLinkage.vue')
)
defineOptions({ name: 'RelatedContentRuntime' })

const props = defineProps({
  composition: { type: Object, required: true },
  ownerType: { type: String, required: true },
  ownerId: { type: [String, Number], required: true },
  sourceRecordId: { type: [String, Number], default: '' },
  sourceData: { type: Object, default: () => ({}) },
  sourceParameters: { type: Object, default: () => ({}) },
  mode: { type: String, default: 'view' },
  compositionContext: { type: Object, default: () => null },
  configHash: { type: String, default: '' },
  runtimeContext: { type: Object, default: () => ({}) },
  compact: Boolean,
  showTrigger: { type: Boolean, default: true }
})

const emit = defineEmits(['target-saved'])
const resolved = ref(null)
const loading = ref(false)
const actionLoading = ref(false)
const errorMessage = ref('')
const actionCapabilityError = ref('')
const actionCapabilities = ref({})
const actionFailure = ref(null)
const presentationVisible = ref(false)
const targetEntity = ref(null)
const targetForm = ref(null)
const targetRecord = ref(null)
const targetParameters = ref({})
const targetEntityStatusOptions = ref(getEffectiveEntityStatusOptions())
const targetRendererRef = ref(null)
const targetSaveLoading = ref(false)
const targetDraftBaseline = ref('')
// 运行时闭包持有各自的快照，旧加载/保存的回包不能写入复用组件的新主记录。
let targetRuntimeState = null
const resolvedContextFingerprint = ref('')
const resolvedSourceIdentity = ref('')
const requestGate = createLatestRequestGate()
const targetDataSourceRuntime = ref(null)

const config = computed(() => props.composition?.config || {})
const presentation = computed(() => config.value.presentation || {})
const visibilityAllows = computed(() => relatedContentIsVisible(presentation.value, props.sourceData, props.mode))
const specialHandling = computed(() => config.value.specialHandling || {})
// 动作接口仅属于独立扩展页面；旧普通关系发布配置不能再暴露接口调用入口。
const allowsActionExtensions = computed(() => ['SAME_RECORD', 'INTERFACE_SERVICE'].includes(
  String(config.value.relation?.type || '').toUpperCase()
))
const actionServices = computed(() => allowsActionExtensions.value
  ? specialHandling.value.actionServices || [] : [])
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
    || props.compositionContext
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
  actionServices.value
    .map(binding => String(binding?.actionKey || '').trim().toUpperCase())
    .filter(action => /^[A-Z][A-Z0-9_.-]{0,99}$/.test(action))
))
const actionServiceBindings = computed(() => new Map(
  actionServices.value
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
const compositionContext = computed(() => (
  resolved.value?.compositionContext
  || ''
))
const targetSaveAction = computed(() => resolved.value?.targetRecordId ? 'EDIT' : 'CREATE')
const canSaveTarget = computed(() =>
  resolved.value?.targetContentType === 'FORM'
    && Boolean(compositionContext.value)
    && Boolean(targetForm.value)
    && ![true, 1, '1'].includes(targetForm.value.isReadonly)
    && actions.value.has(targetSaveAction.value)
)
// 动作能力及处理函数由运行时固定提供；展示配置只能覆盖外观和排序，不能增加、隐藏或替换动作。
const toolbarActions = computed(() => {
  const result = []
  if (inlinePresentation.value && resolved.value?.targetContentType === 'LIST') {
    result.push({ key: 'refresh', label: '刷新', sort: 0, loading: loading.value || actionLoading.value, onClick: refresh })
  }
  if (canSaveTarget.value) {
    result.push({ key: 'save', label: '保存子表单', sort: 10, loading: targetSaveLoading.value, disabled: loading.value || actionLoading.value, onClick: saveTarget })
  }
  return result.sort((left, right) =>
    resolveBuiltInButton(presentation.value.actionBar, left.key, left).sort
      - resolveBuiltInButton(presentation.value.actionBar, right.key, right).sort)
})
const showTopActions = computed(() => toolbarActions.value.length > 0
  && builtInActionBarHasPosition(presentation.value.actionBar, 'TOP', 'TOP'))
const showBottomActions = computed(() => toolbarActions.value.length > 0
  && builtInActionBarHasPosition(presentation.value.actionBar, 'BOTTOM', 'TOP'))

/** 顶部、底部共用同一组固定动作及忙碌状态，双操作栏也会受原有保存防重入保护。 */
const RuntimeActions = defineComponent({
  props: { inline: Boolean, bottom: Boolean },
  setup(actionProps) {
    return () => {
      const defaultAlign = actionProps.inline && presentation.value.showTitle === false ? 'LEFT' : 'RIGHT'
      const explicitAlign = presentation.value.actionBar?.align && presentation.value.actionBar.align !== 'DEFAULT'
      return h('div', {
        class: [
          actionProps.inline ? 'related-content-runtime__actions' : 'related-content-runtime__presentation-actions',
          { 'is-bottom': actionProps.bottom, 'has-custom-alignment': explicitAlign, 'has-custom-gap': presentation.value.actionBar?.gap != null }
        ],
        style: builtInActionBarStyle(presentation.value.actionBar, defaultAlign, 4)
      }, toolbarActions.value.map(action => h(BuiltInActionButton, {
        key: action.key,
        actionKey: action.key,
        config: presentation.value.actionBar,
        defaults: { label: action.label, sort: action.sort, buttonType: 'primary', buttonAppearance: 'LINK' },
        loading: action.loading,
        disabled: action.disabled,
        onClick: action.onClick
      })))
    }
  }
})
const targetMode = computed(() => !canSaveTarget.value ? 'view'
  : targetSaveAction.value === 'EDIT' ? 'edit' : 'create')
const targetDraftDirty = computed(() => Boolean(targetDraftBaseline.value)
  && JSON.stringify(targetRecord.value) !== targetDraftBaseline.value)
useUnsavedChangesGuard(() => targetDraftDirty.value || targetSaveLoading.value, {
  message: '子表单有未保存的修改，离开后这些修改将丢失。'
})
const registerDraftGuard = inject(relatedFormDraftGuardKey, null)
const unregisterDraftGuard = registerDraftGuard?.({
  isDirty: () => targetDraftDirty.value,
  isBusy: () => targetSaveLoading.value,
  discard: discardTargetDraft
})
onBeforeUnmount(() => unregisterDraftGuard?.())
const shouldRender = computed(() =>
  config.value.enabled !== false
    && visibilityAllows.value
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
    return hasCustomFormComponent(name, version, artifactDigest, 'RELATED_CONTENT')
      ? getCustomFormComponent(name, version, artifactDigest, 'RELATED_CONTENT')
      : null
  }
  return hasCustomListComponent(name, version, artifactDigest, 'RELATED_CONTENT')
    ? getCustomListComponent(name, version, artifactDigest, 'RELATED_CONTENT')
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
  const inputFingerprint = parameterFingerprint()
  if (!force
    && resolved.value
    && resolvedContextFingerprint.value === inputFingerprint) {
    return resolved.value
  }
  const requestRevision = requestGate.begin()
  if (!input.recordId && !input.compositionContext) {
    loading.value = false
    clearResolvedState()
    // 新建宿主还没有记录 ID，属于正常空状态，由展示层提示先保存，不显示加载失败。
    errorMessage.value = ''
    return null
  }
  loading.value = true
  errorMessage.value = ''
  clearResolvedState()
  try {
    const parameters = mapPageParameters(config.value.parameterMappings, {
      data: props.sourceData, recordId: props.sourceRecordId, params: props.sourceParameters
    })
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
      || parameterFingerprint() !== inputFingerprint) {
      return null
    }
    targetParameters.value = targetState
      ? resolvePageParameters(targetState.form.viewConfig, parameters) : parameters
    resolved.value = value
    actionCapabilities.value = capabilityState.capabilities
    actionCapabilityError.value = capabilityState.error
    resolvedContextFingerprint.value = inputFingerprint
    resolvedSourceIdentity.value = JSON.stringify(input)
    if (value?.targetContentType === 'FORM') {
      targetEntity.value = targetState.entity
      targetForm.value = targetState.form
      // 缺少目标时仅在服务端允许 CREATE 的情况下建立新增草稿；它不进入宿主的数据模型。
      const initialValues = JSON.parse(JSON.stringify(capabilityState.capabilities.CREATE?.initialValues || {}))
      const draft = targetState.record || (canSaveTarget.value
        ? Object.assign(applyRuntimeFieldDefaults({}, targetState.form, targetState.entity.fields || []), initialValues)
        : null)
      targetState.record = draft && initializePageFields(draft, targetState.form.viewConfig,
        targetParameters.value, pageParameterFields([...(targetState.form.fields || []), ...(targetState.form.nodes || [])]),
        Object.keys(initialValues), targetMode.value)
      targetRecord.value = targetState.record
      targetRuntimeState = targetState
      targetEntityStatusOptions.value = targetState.statuses
      const runtimeMode = targetMode.value
      const runtime = createFormDataSourceRuntime({
        executeDataSource: uiExtensionRuntimeApi.execute,
        entityCode: value.targetEntityCode, getForm: () => targetState.form,
        getRecord: () => targetState.record || {}, getRecordId: () => value.targetRecordId,
        getMode: () => runtimeMode, getEntityDefinition: () => targetState.entity
      }).withContext({ params: targetParameters.value, compositionContext: value.compositionContext, context: { compositionContext: value.compositionContext } })
      targetDataSourceRuntime.value = runtime
      if (targetState.record) await runtime.initialize({ form: targetState.form,
        fields: targetState.form.fields, nodes: targetState.form.nodes,
        record: targetState.record, recordId: value.targetRecordId })
      if (!requestGate.isCurrent(requestRevision)) return null
      targetDraftBaseline.value = JSON.stringify(targetRecord.value)
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
  const sourceContext = value?.compositionContext
  if (!sourceContext) {
    return {
      capabilities: {},
      error: '服务端未返回可信动作凭证，已停止开放新增、编辑和关系操作。'
    }
  }
  try {
    const response = await uiCompositionRuntimeApi.capabilities(sourceContext)
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
    compositionContext: value.compositionContext
  }
  const [entity, release, statuses] = await Promise.all([
    entityApi.getByCode(value.targetEntityCode, traversalRuntimeContext),
    getFormRuntimeRelease(value.targetContentId, { compositionContext: value.compositionContext }),
    getEntityStatusList(value.targetEntityCode, traversalRuntimeContext)
  ])
  // 关联目标只使用本次加载的表单投影，清除其自带关联入口（包括字段和 Tab 挂载点）。
  // 主表与子表可以相互引用；继续渲染目标的关联内容会出现“子表返回主表再展开子表”的循环。
  // 不修改发布快照，目标表单独立打开时仍按自己的发布配置展示关联内容。
  const form = {
    ...normalizeRuntimeFormRelease(
      release,
      value.targetContentId,
      release.configHash || value.targetContentHash
    ),
    viewCompositions: []
  }
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
      configHash:
        form.configHash
        || value.targetContentHash,
      compositionContext: value.compositionContext
    }
  )
  return {
    entity,
    form,
    record: normalizeEntityRecordForForm(detail),
    statuses: getEffectiveEntityStatusOptions(statuses)
  }
}

// 参数仅用于目标页面业务上下文，不进入服务端关系解析或权限凭证。
function parameterFingerprint() {
  return JSON.stringify([currentResolveInput(), sourceParameterFingerprint()])
}

// 只观察实际映射的值。无传参的关联不能因宿主任意字段输入而反复请求。
function sourceParameterFingerprint() {
  try {
    return JSON.stringify([config.value.parameterMappings, mapPageParameters(config.value.parameterMappings, {
      data: props.sourceData, recordId: props.sourceRecordId, params: props.sourceParameters
    })])
  } catch {
    // 不在 watch/render 抛异常，交给 resolveComposition 显示可重试的错误。
    return JSON.stringify(config.value.parameterMappings)
  }
}

function currentResolveInput() {
  return buildRelatedContentResolveInput({
    ownerType: props.ownerType,
    ownerId: props.ownerId,
    compositionKey: props.composition?.compositionKey,
    sourceRecordId: props.sourceRecordId,
    compositionContext: props.compositionContext,
    configHash: props.configHash,
    runtimeContext: buildRuntimeContext(props.runtimeContext)
  })
}

function clearResolvedState() {
  resolved.value = null
  resolvedContextFingerprint.value = ''
  resolvedSourceIdentity.value = ''
  targetEntity.value = null
  targetForm.value = null
  targetRecord.value = null
  targetDraftBaseline.value = ''
  targetRuntimeState = null
  targetRendererRef.value = null
  targetParameters.value = {}
  targetDataSourceRuntime.value = null
  targetEntityStatusOptions.value = getEffectiveEntityStatusOptions()
  actionCapabilities.value = {}
  actionCapabilityError.value = ''
  actionFailure.value = null
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
  if (targetSaveLoading.value) return
  if (targetDraftDirty.value) {
    ElMessage.warning('请先保存子表单，再重新加载')
    return
  }
  await resolveComposition({ force: true })
}

/** 仅回写当前关联区域的草稿；组成子表单继续由宿主聚合提交。 */
function updateTargetDraft(value) {
  if (!canSaveTarget.value || targetSaveLoading.value) return
  const snapshot = JSON.stringify(value || {})
  // 原生渲染器会回传模型同步和联动结果；相同值不重新分配，避免父子深监听互相触发。
  if (snapshot === JSON.stringify(targetRecord.value || {})) return
  targetRecord.value = JSON.parse(snapshot)
  if (targetRuntimeState) targetRuntimeState.record = targetRecord.value
}

/** 宿主确认放弃后恢复加载基线，避免隐藏但未卸载的弹窗继续持有已放弃草稿。 */
function discardTargetDraft() {
  if (targetSaveLoading.value || !targetDraftBaseline.value) return
  targetRecord.value = JSON.parse(targetDraftBaseline.value)
  if (targetRuntimeState) targetRuntimeState.record = targetRecord.value
}

/**
 * 创建/更新复用原生发布表单提交接口，携带关联来源路径供服务端重新鉴权。
 * 校验或提交失败不重载草稿；提交期间锁定输入，并以来源身份隔离迟到的回包。
 */
async function saveTarget() {
  if (!canSaveTarget.value || loading.value || actionLoading.value || targetSaveLoading.value) return
  const value = resolved.value
  const form = targetForm.value
  const renderer = targetRendererRef.value
  const runtime = targetDataSourceRuntime.value
  const identity = JSON.stringify(currentResolveInput())
  const isCurrent = () => resolved.value === value && JSON.stringify(currentResolveInput()) === identity
  targetSaveLoading.value = true
  try {
    if (!renderer) throw new Error('子表单尚未加载完成，请稍后保存')
    if (renderer.validate && !(await renderer.validate())) return
    if (!isCurrent()) return
    await runtime?.prevalidateBeforeSubmit({ form, fields: form.fields, nodes: form.nodes })
    if (!isCurrent()) return
    const data = filterRuntimeFormSubmissionData(renderer.getData?.() || targetRecord.value, form, targetEntity.value?.fields || [])
    const payload = {
      data,
      formId: form.id,
      formConfigHash: form.configHash,
      compositionContext: compositionContext.value,

      startProcess: false
    }
    const result = value.targetRecordId
      ? await entityDataApi.update(value.targetEntityCode, value.targetRecordId, payload)
      : await entityDataApi.save({ ...payload, entityCode: value.targetEntityCode, name: data.name })
    if (!isCurrent()) return
    emit('target-saved', { ...value, result })
    targetDraftBaseline.value = JSON.stringify(targetRecord.value)
    ElMessage.success('子表单已保存')
    // 成功后重新解析关联，取得新记录身份及最新权限；刷新失败也不能重复提交已创建记录。
    const refreshed = await resolveComposition({ force: true })
    if (!refreshed) ElMessage.warning('子表单已保存，重新加载失败，请重新打开当前记录')
  } catch (error) {
    if (!isCurrent()) return
    await renderer?.applyServerValidationError?.(error)
    ElMessage.error(error?.message || '子表单保存失败')
  } finally {
    targetSaveLoading.value = false
  }
}

/** 关联弹窗关闭后保留本地草稿，真正离开由宿主/工作区的草稿守卫保护。 */
function beforePresentationClose(done) {
  if (targetSaveLoading.value) return
  done()
}

function createOperationId(action) {
  const suffix = globalThis.crypto?.randomUUID?.()
    || `${Date.now()}-${Math.random().toString(36).slice(2)}`
  // 同一个安全格式 operationId 同时贯穿请求 Trace、系统审计和实体变更回执。
  return `related-content_${String(action || '').toLowerCase()}_${suffix}`
}

/**
 * 平台只向审核过的一方可信组件提供宿主发布快照已声明的 actionKey；这层
 * bridge 不是任意第三方代码的强沙箱。payload 中的 extensionId、input 等接口
 * 执行字段全部忽略，服务端从已发布宿主配置恢复声明的接口和字段映射。
 */
async function executeBoundAction(action, payload = {}) {
  const actionKey = String(action || '').trim().toUpperCase()
  if (!allowsActionExtensions.value || !actionKey || !actions.value.has(actionKey)) {
    ElMessage.error('当前发布版本未开放该操作')
    return null
  }
  if (!compositionContext.value || actionLoading.value) return null
  const targetRecordIds = Array.from(new Set(
    (payload.targetRecordIds || [])
      .map(id => String(id || '').trim())
      .filter(Boolean)
  ))
  actionLoading.value = true
  actionFailure.value = null
  try {
    const result = await uiCompositionRuntimeApi.executeAction({
      compositionContext: compositionContext.value,
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
      compositionContext: value.compositionContext,
      context: { parameters: targetParameters.value },
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
      if (resolved.value.matchNone && !canSaveTarget.value) {
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
          ref: targetRendererRef,
          form: targetForm.value,
          dataSourceRuntime: targetDataSourceRuntime.value,
          modelValue: targetRecord.value || {},
          'onUpdate:modelValue': updateTargetDraft,
          readonly: resolved.value.targetContentType === 'FORM'
            ? !canSaveTarget.value : !actions.value.has('EDIT'),
          mode: targetMode.value,
          fields: targetForm.value?.fields || [],
          entityCode: resolved.value.targetEntityCode,
          entityDefinition: targetEntity.value,
          config: customComponentConfig.value.props || {},
          context: {
            params: targetParameters.value,
            parameters: targetParameters.value,
            compositionKey: resolved.value.compositionKey,
            entityStatusOptions: targetEntityStatusOptions.value,
            entityStatusMap:
              buildEntityStatusMap(targetEntityStatusOptions.value),
            compositionContext:
              resolved.value.compositionContext
          },
          runtime: {
            refresh,
            query: queryTarget,
            ...(allowsActionExtensions.value ? { dispatch: executeBoundAction } : {}),
            capabilities: Array.from(actions.value),
            actionCapabilities: actionCapabilities.value
          }
        })
      }
      if (resolved.value.targetContentType === 'LIST') {
        return h(AsyncEntityDataList, {
          context: { params: targetParameters.value, parameters: targetParameters.value },
          entityCode: resolved.value.targetEntityCode,
          listKey: resolved.value.targetContentKey,
          compositionContext: resolved.value.compositionContext,

          relatedContentActions: Array.from(actions.value),
          // 列表目标复用其发布工具栏；EntityDataList 仍执行列表按钮权限与能力校验。
          showTargetListToolbarActions: true,
          scene: 'EMBEDDED',
          embedded: true,
          ...relatedContentListProps(presentation.value, Array.from(actions.value))
        })
      }
      if (targetForm.value && targetRecord.value) {
        return h(AsyncFormPreviewLinkage, {
          ref: targetRendererRef,
          form: targetForm.value,
          dataSourceRuntime: targetDataSourceRuntime.value,
          modelValue: targetRecord.value,
          'onUpdate:modelValue': updateTargetDraft,
          // 表单在当前区域编辑，但只通过“保存子表单”独立提交；主表数据模型不持有这份草稿。
          readonly: !canSaveTarget.value,
          mode: targetMode.value,
          showHeader: false,
          height: 'auto',
          entityCode: resolved.value.targetEntityCode,
          entityDefinition: targetEntity.value,
          entityFields: targetEntity.value?.fields || [],
          context: {
            params: targetParameters.value,
            parameters: targetParameters.value,
            record: {
              id: resolved.value.targetRecordId,
              data: targetRecord.value
            },
            configHash:
              targetForm.value.configHash,
            entityStatusOptions: targetEntityStatusOptions.value,
            entityStatusMap:
              buildEntityStatusMap(targetEntityStatusOptions.value),
            compositionContext:
              resolved.value.compositionContext
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
    visibilityAllows.value,
    presentation.value.position,
    presentation.value.loadMode,
    props.ownerType,
    props.ownerId,
    props.composition?.compositionKey,
    props.sourceRecordId,
    props.compositionContext,
    props.configHash,
    sourceParameterFingerprint()
  ],
  () => {
    // 主表映射参数变化时保留正在编辑的关联草稿；保存后再用最新参数重新加载。
    // 真正切换主记录或发布身份时仍清理旧上下文，不把旧子记录用于新主记录的保存。
    if (resolved.value && (targetDraftDirty.value || targetSaveLoading.value)
      && JSON.stringify(currentResolveInput()) === resolvedSourceIdentity.value) return
    const reloadInline = visibilityAllows.value && inlinePresentation.value && (loadImmediately.value || Boolean(resolved.value))
    requestGate.invalidate()
    loading.value = false
    errorMessage.value = ''
    clearResolvedState()
    if (reloadInline) {
      resolveComposition()
    }
  },
  { immediate: true }
)
// 列表按钮复用解析与展示能力，按钮本身由列表的统一渲染器管理。
defineExpose({ open: openPresentation })
</script>

<style scoped>
.related-content-runtime {
  width: 100%;
}

.related-content-runtime__card {
  margin: 12px 0;
}

/* 仅显式关闭时移除子页面外框和标题分隔线；旧配置保留默认边框，内部字段样式不变。 */
.related-content-runtime__card.is-borderless {
  border: 0;
  margin: 0;
}
.related-content-runtime__card.is-borderless :deep(> .el-card__body) {
  padding: 0;
}
.related-content-runtime__card.is-borderless :deep(> .el-card__header) {
  border-bottom: 0;
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

/* 显式对齐才占满标题剩余空间；未配置时保留旧版隐藏标题后靠左的布局。 */
.related-content-runtime__actions.has-custom-alignment {
  flex: 1;
}
.related-content-runtime__actions,
.related-content-runtime__presentation-actions {
  flex-wrap: wrap;
}
.related-content-runtime__actions.has-custom-gap :deep(.el-button + .el-button),
.related-content-runtime__presentation-actions.has-custom-gap :deep(.el-button + .el-button) {
  margin-left: 0;
}
.related-content-runtime__actions.is-bottom,
.related-content-runtime__presentation-actions.is-bottom {
  margin-top: 12px;
  margin-bottom: 0;
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
