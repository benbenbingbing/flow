<template>
  <ExcelActionDialog ref="excelActionDialog" @imported="emit('success')" />
  <el-dialog
    v-model="presentedDialogVisible"
    width="75%"
    :class="[
      'entity-form-dialog',
      'entity-data-form-dialog',
      { 'entity-form-dialog--seamless': seamlessPresentation }
    ]"
    top="3vh"
    :fullscreen="seamlessPresentation"
    :modal="!seamlessPresentation"
    :lock-scroll="!seamlessPresentation"
    :close-on-click-modal="false"
    :before-close="beforeDialogClose"
    :inert="reloadPending || undefined"
    :aria-busy="reloadPending ? 'true' : undefined"
    @closed="handleDialogClosed"
  >
    <template #header="{ titleId, titleClass }">
      <RuntimeVersionDiagnostics
        ref="runtimeDiagnosticsRef"
        :entries="dialogRuntimeDiagnosticEntries"
        :copy-entries="dialogRuntimeDiagnosticCopyEntries"
        copy-title="业务表单运行版本排障信息"
        :reset-key="dialogRuntimeDiagnosticResetKey"
      >
        <span
          :id="titleId"
          :class="[titleClass, 'form-dialog-title']"
        >{{ dialogTitle }}</span>
      </RuntimeVersionDiagnostics>
    </template>
    <el-alert v-if="initialLoadError" :title="initialLoadError" type="error" :closable="false" show-icon />
    <el-skeleton v-else-if="initialLoading && !formReady" class="form-opening-skeleton" :rows="6" animated />
    <!-- 初始化会回填字段；完成前不挂载可交互表单，避免异步结果覆盖用户输入。 -->
    <div v-if="formReady" :key="openingId" class="form-opening-body" :inert="initialLoading || undefined" :aria-busy="initialLoading ? 'true' : undefined">
      <el-tabs v-if="showOuterTabs" v-model="activeTab" type="border-card" class="form-dialog-tabs">
        <el-tab-pane v-if="showBasicTab" label="基本信息" name="basic">
          <EntityDataFormFields
            ref="basicFormFieldsRef"
            v-model:formData="formData"
            :entityCode="entityCode"
            :entityDefinition="entityDefinition"
            :entityFields="entityFields"
            :defaultForm="runtimeForm"
            :isEdit="isEdit"
            :showStartProcess="canStartProcess"
            :excludedNodeIds="liftedRootNodeIds"
            :dataSourceRuntime="dataSourceRuntime"
            :skipDataSourcePrevalidation="true"
            :initialization-managed="true"
            :read-session="openingReadSession"
            :form-actions="formActions"
            :action-loading-key="actionLoadingKey"
            :entity-status-options="entityStatusOptions"
            :runtime-context="launchRuntimeContext"
            @form-action="handleFormAction"
          />
        </el-tab-pane>
        <el-tab-pane
          v-for="(tab, idx) in runtimeNodeTabs"
          :key="tab.name"
          :label="tab.label"
          :name="tab.name"
        >
          <EntityDataFormFields
            :ref="(instance) => setNodeFormFieldsRef(tab.name, instance)"
            v-model:formData="formData"
            :entityCode="entityCode"
            :entityDefinition="entityDefinition"
            :entityFields="entityFields"
            :defaultForm="runtimeForm"
            :isEdit="isEdit"
            :showStartProcess="canStartProcess && !showBasicTab && idx === 0"
            :nodeRootParentId="tab.rootParentId"
            :dataSourceRuntime="dataSourceRuntime"
            :skipDataSourcePrevalidation="true"
            :initialization-managed="true"
            :read-session="openingReadSession"
            :form-actions="formActions"
            :action-loading-key="actionLoadingKey"
            :entity-status-options="entityStatusOptions"
            :runtime-context="launchRuntimeContext"
            @form-action="handleFormAction"
          />
        </el-tab-pane>
        <!-- 延迟到页签可见后再创建 Viewer，避免在零尺寸隐藏容器中初始化。 -->
        <el-tab-pane
          v-if="hasProcessInfo"
          label="流程图"
          name="diagram"
          lazy
        >
          <EntityProcessRoundHistory view="diagram" :bpmn-xml="bpmnXml" :progress-data="progressData"
            :process-instance-id="processInstanceId" :process-history="processHistory" />
        </el-tab-pane>
        <el-tab-pane v-if="hasProcessInfo" label="审批历史" name="history">
          <EntityProcessRoundHistory view="history" :bpmn-xml="bpmnXml" :progress-data="progressData"
            :process-instance-id="processInstanceId" :process-history="processHistory" />
        </el-tab-pane>
        <el-tab-pane
          v-if="hasProcessInfo && userStore.isSuperAdmin"
          label="动作执行记录"
          name="actionExecutions"
        >
          <FlowActionExecutionLog
            :process-instance-id="processInstanceId"
            :active="activeTab === 'actionExecutions'"
          />
        </el-tab-pane>
      </el-tabs>

      <EntityDataFormFields
        v-else
        ref="formFieldsRef"
        v-model:formData="formData"
        :entityCode="entityCode"
        :entityDefinition="entityDefinition"
        :entityFields="entityFields"
        :defaultForm="runtimeForm"
        :isEdit="isEdit"
        :showStartProcess="canStartProcess"
        :dataSourceRuntime="dataSourceRuntime"
        :skipDataSourcePrevalidation="true"
        :initialization-managed="true"
        :read-session="openingReadSession"
        :form-actions="formActions"
        :action-loading-key="actionLoadingKey"
        :entity-status-options="entityStatusOptions"
        :runtime-context="launchRuntimeContext"
        @form-action="handleFormAction"
      />
    </div>
    <template #footer>
      <template v-if="formReady && !initialLoading">
        <span v-if="actionsLoading" role="status">正在加载操作…</span>
        <el-alert v-else-if="actionsError" :title="actionsError" type="error" :closable="false">
          <el-button link type="primary" @click="retryFormActions">重试操作加载</el-button>
        </el-alert>
        <FormActionBar v-else
          :actions="footerActions"
          :loading-key="actionLoadingKey"
          @action="handleFormAction"
        />
      </template>
    </template>
  </el-dialog>
  <FormButtonActionHost v-if="dialogVisible && formReady" ref="buttonActionHostRef" />
</template>

<script setup lang="ts">
import { buildRuntimeContext } from '@flow/workflow-core/form-runtime/context'
import FormButtonActionHost from '@/components/FormButtonActionHost.vue'
import { uiExtensionRuntimeApi } from '@/api/uiConfig'
import { resolvePageParameters, initializePageFields, pageParameterFields } from '@flow/workflow-core/page-parameters'
import { ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount, provide, shallowRef } from 'vue'
import { relatedFormDraftGuardKey } from '@/shared/related-form-draft-guard'
import { useRouter } from 'vue-router'
import { showRequestError } from '@/shared/request'
import { applyFormEffects, confirmFormAction } from '@/shared/form-event-effects'
import ExcelActionDialog from '@/components/excel/ExcelActionDialog.vue'
const excelActionDialog = ref(null)
import { ElMessage, ElMessageBox } from 'element-plus'
import { entityDataApi } from '@/api/entity'
import { uiEventBindingApi } from '@/api/uiConfig'
import { useUserStore } from '@/stores/user'
import { useProcessDetail } from '@/composables/useProcessDetail'
import {
  applyRuntimeFieldDefaults,
  createFormDataSourceRuntime,
  filterRuntimeFormSubmissionData,
  normalizeEntityRecordForForm,
  resolveRuntimeFormTabLayout
} from '@flow/workflow-core/form-runtime'
import EntityDataFormFields from './EntityDataFormFields.vue'
import EntityProcessRoundHistory from './approval/EntityProcessRoundHistory.vue'
import FlowActionExecutionLog from '@/components/FlowActionExecutionLog.vue'
import FormActionBar from '@/components/FormActionBar.vue'
import RuntimeVersionDiagnostics from '@/components/RuntimeVersionDiagnostics.vue'
import {
  runFormAction,
  executeCustomFormAction,
  resolveRuntimeFormActions
} from '@/shared/form-action-runtime'
import { footerFormActions } from '@flow/workflow-core/form-actions'
import { isWorkflowReady } from '@/shared/entity-design'
import { formatRuntimeCodeVersion } from '@/shared/runtime-diagnostics'
import { isEmbedDelegatedRequestEnabled } from '@/shared/request'
import { createRuntimeFormDiscardGuard } from '@/shared/runtime-form-discard'
import { useWorkspacePage } from '@/composables/useWorkspacePage'

const buttonActionHostRef = ref<any>()
const props = withDefaults(defineProps<{
  entityCode: string
  entityDefinition: any
  entityFields: any[]
  defaultForm: any
  listKey?: string
  listConfigHash?: string
  entityStatusOptions?: any[]
  submitTransport?: (submission: any) => Promise<any>
  reloadPending?: boolean
  formPresentation?: 'seamless' | 'dialog'
}>(), {
  formPresentation: 'dialog'
})

const emit = defineEmits<{
  success: [result?: any]
  closed: []
}>()

const router = useRouter()
const userStore = useUserStore()

const dialogVisible = ref(false)
const workspacePage = useWorkspacePage()
const presentedDialogVisible = workspacePage.visibleWhenActive(dialogVisible)
const seamlessPresentation = computed(() => props.formPresentation === 'seamless')
const dialogTitle = ref('')
const formActions = ref<any[]>([])
const initialLoading = ref(false)
const initialLoadError = ref('')
const formReady = ref(false)
const actionsLoading = ref(false)
const actionsError = ref('')
const openingId = ref(0)
const openingReadSession = shallowRef<any>(null)
let currentOpening: any = null
const actionLoadingKey = ref('')
const formFieldsRef = ref<InstanceType<typeof EntityDataFormFields>>()
const basicFormFieldsRef = ref<InstanceType<typeof EntityDataFormFields>>()
const nodeFormFieldsRefs = ref<Record<string, InstanceType<typeof EntityDataFormFields>>>({})
const runtimeDiagnosticsRef = ref<InstanceType<typeof RuntimeVersionDiagnostics>>()
const isEdit = ref(false)
const activeTab = ref('form')
const processInstanceId = ref('')
const currentProcessStatus = ref('')
const currentProcessName = ref('')
const processDetailsLoadedFor = ref('')
let processDetailsPendingFor = ''
// 打开表单后的基线供“重置”和未保存修改判断使用，提交成功前不随输入变化。
const resetSnapshot = ref<any>(null)
// 列表/关联内容入口传入的运行时上下文，后续事件、数据源和提交复用其中的参数与令牌；服务端仍需校验令牌。
const launchRuntimeContext = ref<Record<string, any>>({})
const activeForm = ref<any>(null)
// 每次打开生成不同初始化键，避免再次新增时复用上次 FORM_INIT 的去重状态。
let launchSequence = 0
const runtimeForm = computed(() => activeForm.value || props.defaultForm)
// 列表发布身份用于校验入口权限；表单发布身份用于提交字段及绑定，不能相互代替。
const listReleaseContext = computed(() => ({
  configHash:
    props.listConfigHash || undefined,
  compositionContext:
    launchRuntimeContext.value?.compositionContext || undefined
}))
const formReleaseContext = computed(() => ({
  runtimeContext: buildRuntimeContext(launchRuntimeContext.value, runtimeForm.value || {}),
  configHash:
    runtimeForm.value?.configHash || undefined,
  compositionContext:
    launchRuntimeContext.value?.compositionContext || undefined
}))

const formData = reactive({
  id: '',
  name: '',
  data: {} as Record<string, any>,
  startProcess: false
})

// 工作区关闭标签和 Embed 关闭表单都保护输入；会话撤销时容器直接释放旧页面。
const discardGuard = createRuntimeFormDiscardGuard({
  readValue: () => ({ name: formData.name, data: formData.data }),
  enabled: () => dialogVisible.value && formReady.value && !initialLoading.value && (Boolean(workspacePage.page) || isEmbedDelegatedRequestEnabled()),
  confirm: () => ElMessageBox.confirm(
    '当前表单有未保存的修改，关闭或刷新后这些修改将丢失。',
    '确认放弃修改？',
    {
      type: 'warning',
      confirmButtonText: '放弃修改',
      cancelButtonText: '继续填写',
      closeOnClickModal: false,
      closeOnPressEscape: false
    }
  )
})
// 普通关联区域独立保存，主表关闭、Embed 刷新和工作区退出也需保护它的未保存输入。
const relatedFormGuards = new Set<any>()
provide(relatedFormDraftGuardKey, (guard: any) => {
  relatedFormGuards.add(guard)
  return () => relatedFormGuards.delete(guard)
})
const hasUnsavedRelatedForms = () => [...relatedFormGuards].some(guard => guard.isDirty() || guard.isBusy())
workspacePage.registerGuard(() => discardGuard.isDirty() || hasUnsavedRelatedForms())

/** 供原生关闭和 Embed 刷新共同检查；提交中的表单不能被重新初始化。 */
async function confirmDiscardChanges() {
  if (props.reloadPending || actionLoadingKey.value) return false
  if ([...relatedFormGuards].some(guard => guard.isBusy())) return false
  if (hasUnsavedRelatedForms()) {
    try {
      await ElMessageBox.confirm('子表单有未保存的修改，关闭或刷新后这些修改将丢失。', '确认放弃修改？', {
        type: 'warning', confirmButtonText: '放弃修改', cancelButtonText: '继续填写',
        closeOnClickModal: false, closeOnPressEscape: false
      })
      // 同次确认覆盖整个弹窗的输入，避免主表和子表分别重复询问。
      relatedFormGuards.forEach(guard => guard.discard())
      return true
    } catch { return false }
  }
  return discardGuard.confirmDiscard()
}

async function beforeDialogClose(done: () => void) {
  const opening = currentOpening
  if (await confirmDiscardChanges()) {
    if (opening !== currentOpening) return
    // Element Plus 的 model 更新可能晚于关闭动画；确认关闭时就废弃续体，阻止动画中的迟到事件。
    opening?.cancel?.()
    currentOpening = null
    done()
  }
}

onMounted(() => globalThis.addEventListener?.('beforeunload', discardGuard.beforeUnload))
onBeforeUnmount(() => globalThis.removeEventListener?.('beforeunload', discardGuard.beforeUnload))

const hasProcessInfo = computed(() => !!processInstanceId.value)
const canStartProcess = computed(() => !hasProcessInfo.value)
const footerActions = computed(() => footerFormActions(formActions.value))
// 每次打开创建独立运行时，旧初始化只持有旧记录，不会沿用新记录的字段或去重状态。
const dataSourceRuntime = shallowRef<any>(null)

function isCurrentOpening(opening: any) {
  return Boolean(opening && opening === currentOpening && dialogVisible.value)
}

/** 立即呈现加载面板；会话令牌同时约束配置、事件、初始化、选项和按钮的异步续体。 */
function beginOpening(mode: string, options: any) {
  currentOpening?.cancel?.()
  const opening: any = { id: ++launchSequence, mode, record: {}, reads: new Map(), pendingAction: { value: '' }, start: performance.now() }
  opening.closed = new Promise(resolve => { opening.cancel = () => resolve(false) })
  currentOpening = opening
  openingId.value = opening.id
  formReady.value = false
  formActions.value = []
  actionLoadingKey.value = ''
  actionsLoading.value = false
  actionsError.value = ''
  resetSnapshot.value = null
  initialLoadError.value = ''
  initialLoading.value = true
  isEdit.value = mode === 'edit'
  activeForm.value = null
  formData.id = ''
  formData.name = ''
  formData.data = opening.record
  formData.startProcess = false
  launchRuntimeContext.value = { ...(options.context || {}), initializationKey: `${mode}:${opening.id}` }
  dialogTitle.value = mode === 'edit' ? '编辑数据' : '新增数据'
  dialogVisible.value = true
  markOpening(opening, 'visible')
  // 只合并本次打开中完全相同的只读请求；失败删除缓存，关闭后不跨用户或记录复用。
  openingReadSession.value = {
    isCurrent: () => isCurrentOpening(opening),
    read(key: string, load: () => Promise<any>) {
      if (!opening.reads.has(key)) {
        const promise = Promise.resolve().then(load)
        opening.reads.set(key, promise)
        promise.catch(() => { if (opening.reads.get(key) === promise) opening.reads.delete(key) })
      }
      return opening.reads.get(key)
    }
  }
  return opening
}

/** 性能面板区分弹窗出现、可填写和按钮就绪；只保留各模式最近一次指标，不含业务记录信息。 */
function markOpening(opening: any, stage: string) {
  const name = `entity-form:${opening.mode}:${stage}`
  performance.clearMeasures(name)
  performance.measure(name, { start: opening.start, end: performance.now() })
}

function initializeOpeningRuntime(opening: any) {
  const context = launchRuntimeContext.value
  const form = runtimeForm.value
  const reads = openingReadSession.value
  opening.record = formData.data
  dataSourceRuntime.value = createFormDataSourceRuntime({
    async executeDataSource(request: any) {
      if (!isCurrentOpening(opening)) throw new Error('表单会话已结束')
      // 多个页签/控件可能请求同一选项，按完整参数合并；生命周期与计算事件保持原执行次数。
      const result = request.bindingCode === 'FIELD_OPTIONS'
        ? await reads.read(`extension-options:${JSON.stringify(request)}`, () => uiExtensionRuntimeApi.execute(request))
        : await uiExtensionRuntimeApi.execute(request)
      if (!isCurrentOpening(opening)) throw new Error('表单会话已结束')
      return result
    },
    entityCode: props.entityCode,
    getRecord: () => isCurrentOpening(opening) ? formData.data : opening.record,
    getRecordId: () => opening.recordId,
    getListKey: () => props.listKey,
    getMode: () => opening.mode,
    getForm: () => form,
    getEntityDefinition: () => props.entityDefinition
  }).withContext({
    ...context,
    // 发布接口映射可从 context.* 读取入口参数、关联坐标及初值，保留原有映射路径。
    context: { ...(context.context || {}), ...context },
    params: context.params || context.parameters || {}
  })
}

// 外部关闭及事件关闭也废弃会话；原生关闭在 beforeDialogClose 中提前使续体失效。
watch(dialogVisible, visible => { if (!visible) { currentOpening?.cancel?.(); currentOpening = null; resetProcessDetail() } }, { flush: 'sync' })
onBeforeUnmount(() => { currentOpening?.cancel?.(); currentOpening = null })

const {
  bpmnXml,
  progressData,
  processHistory,
  processRuntimeMetadata,
  loadProcessDetail,
  resetProcessDetail
} = useProcessDetail()

/**
 * 排障信息必须描述当前真正渲染的表单；编辑已有流程数据时，再补充实例钉定的流程版本。
 */
const dialogRuntimeDiagnosticEntries = computed(() => {
  const process = processRuntimeMetadata.value || {}
  const diagnosticProcessInstanceId =
    process.processInstanceId || processInstanceId.value
  const form = runtimeForm.value
  return [
    ...(diagnosticProcessInstanceId
      ? [{
          label: '流程',
          value: formatRuntimeCodeVersion(
            process.processKey,
            process.processVersion
          )
        }]
      : []),
    {
      label: '表单',
      value: form
        ? form.formKey || form.name || '未记录运行表单'
        : '未记录运行表单'
    }
  ]
})
const dialogRuntimeDiagnosticCopyEntries = computed(() => [
  ...(props.listKey
    ? [{
        label: '列表',
        value: props.listKey
      }]
    : []),
  { label: '操作模式', value: isEdit.value ? '编辑' : '新增' },
  ...dialogRuntimeDiagnosticEntries.value,
  { label: '记录 ID', value: formData.id },
  {
    label: '流程实例 ID',
    value: processRuntimeMetadata.value?.processInstanceId
      || processInstanceId.value
  }
])
const dialogRuntimeDiagnosticResetKey = computed(() => {
  const form = runtimeForm.value
  return [
    dialogVisible.value ? 'open' : 'closed',
    isEdit.value ? 'edit' : 'create',
    launchRuntimeContext.value?.initializationKey || '',
    formData.id || '',
    processRuntimeMetadata.value?.processInstanceId
      || processInstanceId.value
      || '',
    form?.configHash || '',
  ].join(':')
})

function handleDialogClosed() {
  if (dialogVisible.value) return // 切换标签只是暂时隐藏，保留运行上下文及输入。
  launchRuntimeContext.value = {} // 真正关闭后使尚未返回的按钮/表单事件失效。
  runtimeDiagnosticsRef.value?.reset()
  processRuntimeMetadata.value = {}
  emit('closed')
}

const runtimeTabLayout = computed(() => resolveRuntimeFormTabLayout(runtimeForm.value, { data: formData.data, mode: isEdit.value ? 'edit' : 'create', context: launchRuntimeContext.value }))
const runtimeNodeTabs = computed(() => runtimeTabLayout.value.tabs)
const liftedRootNodeIds = computed(() => runtimeTabLayout.value.liftedRootNodeIds)
const hasRuntimeFormTabs = computed(() => runtimeNodeTabs.value.length > 0)
const showOuterTabs = computed(() => hasProcessInfo.value || hasRuntimeFormTabs.value)
const showBasicTab = computed(() =>
  runtimeTabLayout.value.hasBaseContent
  || !hasRuntimeFormTabs.value
  || (canStartProcess.value && runtimeNodeTabs.value.length === 0)
)
const firstFormTabName = computed(() => {
  if (showBasicTab.value) return 'basic'
  if (runtimeNodeTabs.value.length > 0) return runtimeNodeTabs.value[0].name
  return 'form'
})

// 当前页签被条件隐藏时回到可见页签，流程信息页不受节点条件影响。
watch([runtimeNodeTabs, showBasicTab], () => {
  if ((activeTab.value === 'basic' && !showBasicTab.value)
    || (String(activeTab.value).startsWith('form_tab_') && !runtimeNodeTabs.value.some(tab => tab.name === activeTab.value))) {
    activeTab.value = firstFormTabName.value
  }
})

function setNodeFormFieldsRef(
  tabName: string,
  instance: InstanceType<typeof EntityDataFormFields> | null
) {
  if (instance) {
    nodeFormFieldsRefs.value[tabName] = instance
  } else {
    delete nodeFormFieldsRefs.value[tabName]
  }
}

function refreshFormLinkage() {
  if (!showOuterTabs.value) {
    formFieldsRef.value?.refreshLinkage()
    return
  }
  if (showBasicTab.value) {
    basicFormFieldsRef.value?.refreshLinkage()
    return
  }
  nodeFormFieldsRefs.value[firstFormTabName.value]?.refreshLinkage()
}

// 重置表单
const resetForm = () => {
  formData.id = ''
  formData.name = ''
  formData.data = {}
  formData.startProcess = false
  
  const fields = props.entityFields.filter((f: any) => f.runtimeReadable !== false)
  fields.forEach((field: any) => {
    formData.data[field.fieldCode] = ''
  })
  applyRuntimeFieldDefaults(formData.data, runtimeForm.value, fields)
}

/** 保存必要初始化与控件补值后的基线，供重置及未保存修改判断使用。 */
function captureResetSnapshot() {
  resetSnapshot.value = JSON.parse(JSON.stringify({
    id: formData.id,
    name: formData.name,
    data: formData.data,
    startProcess: false
  }))
  discardGuard.markSaved()
}

function restoreResetSnapshot() {
  const snapshot = resetSnapshot.value
  if (!snapshot) {
    resetForm()
    return
  }
  formData.id = snapshot.id || ''
  formData.name = snapshot.name || ''
  formData.data = JSON.parse(JSON.stringify(snapshot.data || {}))
  formData.startProcess = false
}

/** 按钮不阻塞填写；只接受当前会话的服务端解析结果，失败单独重试，不重放打开事件。 */
async function loadFormActions(opening = currentOpening) {
  if (!isCurrentOpening(opening)) return
  actionsLoading.value = true
  actionsError.value = ''
  try {
    const actions = await resolveRuntimeFormActions(runtimeForm.value, {
      entityCode: props.entityCode, listKey: props.listKey,
      mode: opening.mode, recordId: formData.id || undefined,
      workflowReady: isWorkflowReady(props.entityDefinition),
      hasProcessInstance: hasProcessInfo.value,
      systemEntity: props.entityDefinition?.storageMode === 'SYSTEM',
      compositionContext: launchRuntimeContext.value?.compositionContext || undefined,
      requireServerResolution: true
    })
    if (isCurrentOpening(opening)) { formActions.value = actions; markOpening(opening, 'actions-ready') }
  } catch (error: any) {
    if (isCurrentOpening(opening)) actionsError.value = error?.message || '加载表单操作失败'
  } finally {
    if (isCurrentOpening(opening)) actionsLoading.value = false
  }
}
function retryFormActions() { if (!actionsLoading.value) void loadFormActions() }

/**
 * 在当前表单发布版本上执行表单级事件；eventCode 标识打开等时机，
 * 请求携带尚未提交的 formData 与页面参数供服务端脚本映射使用。
 */
async function executeFormEvent(eventCode: string) {
  if (!runtimeForm.value?.id) return
  const opening = currentOpening
  const context = launchRuntimeContext.value
  const isCurrent = () => isCurrentOpening(opening) && launchRuntimeContext.value === context
  const result = await uiEventBindingApi.execute(eventCode, {
    configType: 'FORM',
    configId: String(runtimeForm.value.id),
    runtimeContext: formReleaseContext.value.runtimeContext,
    configHash:
      runtimeForm.value.configHash
      || launchRuntimeContext.value?.configHash
      || undefined,
    compositionContext:
      launchRuntimeContext.value?.compositionContext || undefined,
    entityCode: props.entityCode,
    listKey: props.listKey,
    targetType: 'OWNER',
    recordId: formData.id || undefined,
    input: {
      mode: isEdit.value ? 'edit' : 'create',
      form: formData.data,
      recordId: formData.id || undefined,
      params:
        launchRuntimeContext.value?.params
        || launchRuntimeContext.value?.parameters
        || {}
    },
    context: {
      ...launchRuntimeContext.value,
      formId: String(runtimeForm.value.id),
      listKey: props.listKey || '',
      mode: isEdit.value ? 'edit' : 'create'
    }
  })
  await applyFormEventResult(result, isCurrent)
  if (isCurrent() && result?.message) {
    ElMessage.success(result.message)
  }
}

/** 按服务端效果顺序回填或导航；FIELD_MAPPING 的覆盖策略决定是否保留用户现有值。 */
async function applyFormEventResult(result: any, isCurrent = () => true) {
  const context = launchRuntimeContext.value
  let closedByEffect = false
  await applyFormEffects(result, {
    excel: (effect: any) => excelActionDialog.value.open(effect),
    getRecord: () => formData.data,
    setField: (key: string, value: any) => { formData.data[key] = value },
    getFields: () => props.entityFields,
    // 事件自身关闭后仍须顺序执行 REFRESH_PARENT 等后续效果；用户关闭或切换记录则丢弃结果。
    isCurrent: () => isCurrent() || (closedByEffect && launchRuntimeContext.value === context),
    navigate: (effect: any) => router.push(effect.route),
    close: () => { closedByEffect = true; dialogVisible.value = false },
    refresh: () => emit('success')
  })
}



/** 统一分发保存、重置及自定义按钮；会话内 pendingAction 覆盖确认等待期，避免旧动作释放新会话的锁。 */
async function handleFormAction(action: any) {
  if (!formReady.value || initialLoading.value || initialLoadError.value || actionsLoading.value || actionsError.value) return
  const opening = currentOpening
  const context = launchRuntimeContext.value
  const isCurrent = () => isCurrentOpening(opening) && launchRuntimeContext.value === context
  const actionPendingKey = opening.pendingAction
  try {
    await runFormAction(action, {
      loadingState: actionPendingKey,
      confirm: confirmFormAction,
      isCurrent,
      async validate() {
        if (action.type !== 'custom' || await validateRuntimeForms()) return true
        ElMessage.warning('请先完成表单必填项')
        return false
      },
      async execute() {
        if (action.key === 'close') {
          if (await confirmDiscardChanges()) dialogVisible.value = false
          return
        }
        actionLoadingKey.value = String(action.runtimeKey || action.key || '')
        if (action.key === 'reset') return handleReset()
        if (['save', 'saveAndStart', 'restartProcess'].includes(action.key)) {
          return handleSubmit(action.key === 'saveAndStart', action.key === 'restartProcess')
        }
        if (action.type !== 'custom') return
        return executeCustomFormAction(action, runtimeForm.value, {
          isCurrent,
          executeClientAction: (...args: any[]) => buttonActionHostRef.value.execute(...args),
          entityDefinition: props.entityDefinition,
          entityFields: props.entityFields,
          parameters: context?.parameters || {},
          updateFormData: (value: any) => { if (isCurrent()) formData.data = value },
          entityCode: props.entityCode,
          listKey: props.listKey,
          mode: isEdit.value ? 'edit' : 'create',
          recordId: formData.id || undefined,
          formData: formData.data,
          processInstanceId: processInstanceId.value || undefined,
          compositionContext: context?.compositionContext || undefined
        })
      },
      async applyResult(result: any) {
        if (action.type !== 'custom') return
        await applyFormEventResult(result, isCurrent)
        if (isCurrent() && result?.message) ElMessage.success(result.message)
      },
      settled: () => { if (launchRuntimeContext.value === context) actionLoadingKey.value = '' }
    })
  } catch (error: any) {
    await showServerValidationErrors(error)
    showRequestError(error, '按钮操作执行失败')
  }
}









async function handleReset() {
  restoreResetSnapshot()
  try {
    await executeFormEvent('FORM_RESET')
  } catch (error: any) {
    showRequestError(error, '表单重置事件执行失败')
  }
  nextTick(() => refreshFormLinkage())
}

/** 参数初始化先于打开事件，各类业务字段仅填空值，可信关联初值和已有记录均优先。 */
function initializeParameterFields() {
  if ([true, 1, '1'].includes(runtimeForm.value?.isReadonly)) return
  formData.data = initializePageFields(formData.data, runtimeForm.value?.viewConfig,
    launchRuntimeContext.value.params, pageParameterFields([...(runtimeForm.value?.fields || []), ...(runtimeForm.value?.nodes || [])]), [], isEdit.value ? 'edit' : 'create')
}

/** 新增沿用默认值、参数、打开事件与入口初值的优先级，必要回填完成后才允许输入。 */
const openCreate = async (options: any = {}) => {
  runtimeDiagnosticsRef.value?.reset()
  resetProcessDetail()
  return openForm('create', null, options)
}

/**
 * 统一首屏生命周期。DETAIL_LOAD/FORM_OPEN 可能产生业务效果，失败不自动重放；
 * 按钮和只读选项不影响表单可填写，保存仍经过原有发布身份、权限与字段校验。
 */
function openForm(mode: string, row: any, options: any) {
  const opening = beginOpening(mode, options)
  // 关闭即释放列表入口的等待锁；底层事件不自动重放，迟到的结果由会话校验丢弃。
  return Promise.race([initializeOpenForm(opening, row, options), opening.closed])
}

async function initializeOpenForm(opening: any, row: any, options: any) {
  const mode = opening.mode
  processDetailsLoadedFor.value = ''
  processDetailsPendingFor = ''
  processInstanceId.value = ''
  currentProcessStatus.value = ''
  currentProcessName.value = ''
  let stage = '表单配置'
  try {
    const form = options.loadForm ? await options.loadForm() : options.form || props.defaultForm
    if (!isCurrentOpening(opening)) return false
    activeForm.value = form || null
    const params = resolvePageParameters(runtimeForm.value?.viewConfig, {
      ...(options.context?.parameters || {}), ...(options.context?.params || {}), ...(options.parameters || {})
    })
    const initialData = mode === 'create' && options.initialData && typeof options.initialData === 'object' ? options.initialData : {}
    launchRuntimeContext.value = { ...launchRuntimeContext.value, params, parameters: params, initialData }
    if (mode === 'create') {
      resetForm()
      dialogTitle.value = runtimeForm.value?.formName
        ? `新增数据 - ${runtimeForm.value.formName}${runtimeForm.value.formKey ? `（${runtimeForm.value.formKey}）` : ''}` : '新增数据'
      applyCreateInitialData(initialData)
    } else {
      stage = '实体详情'
      const detail = await entityDataApi.getDetail(props.entityCode, row.id, props.listKey,
        runtimeForm.value?.id, listReleaseContext.value, formReleaseContext.value)
      if (!isCurrentOpening(opening)) return false
      formData.id = detail.id
      formData.name = detail.name
      formData.data = normalizeEntityRecordForForm(detail)
      processInstanceId.value = detail.processInstanceId || ''
      currentProcessStatus.value = detail.processStatus || ''
    }
    initializeParameterFields()
    stage = '表单打开事件'
    await executeFormEvent('FORM_OPEN')
    if (!isCurrentOpening(opening)) return false
    if (mode === 'create') applyCreateInitialData(initialData)
    opening.recordId = formData.id || undefined
    initializeOpeningRuntime(opening)
    stage = '字段初始化'
    await dataSourceRuntime.value.initialize({
      form: runtimeForm.value, fields: runtimeForm.value?.fields || [], nodes: runtimeForm.value?.nodes || [],
      record: formData.data, initializationKey: launchRuntimeContext.value.initializationKey,
      runtimeContext: launchRuntimeContext.value
    })
    if (!isCurrentOpening(opening)) return false
    if (mode === 'create') applyCreateInitialData(initialData)
    activeTab.value = firstFormTabName.value
    formReady.value = true
    await nextTick()
    if (!isCurrentOpening(opening)) return false
    refreshFormLinkage()
    await nextTick()
    if (!isCurrentOpening(opening)) return false
    // 控件同步补值和必要初始化一起构成重置基线；可编辑之后不再移动基线。
    captureResetSnapshot()
    initialLoading.value = false
    markOpening(opening, 'ready')
    void loadFormActions(opening)
    return true
  } catch (error: any) {
    if (isCurrentOpening(opening)) {
      formReady.value = false
      initialLoadError.value = `${stage}加载失败：${error?.message || '请关闭后重试'}`
    }
    return false
  } finally {
    if (isCurrentOpening(opening)) initialLoading.value = false
  }
}

// 图与历史按需加载；失败保留可重试状态，切换实体时不沿用旧实例内容。
watch([activeTab, processInstanceId, dialogVisible], async ([tab, instanceId, visible]) => {
  if (!visible || !instanceId || !['diagram', 'history'].includes(String(tab))
      || processDetailsLoadedFor.value === instanceId || processDetailsPendingFor === instanceId) return
  processDetailsPendingFor = String(instanceId)
  try {
    const loaded = await loadProcessDetail(String(instanceId))
    if (loaded && processInstanceId.value === instanceId) processDetailsLoadedFor.value = String(instanceId)
  } finally {
    if (processDetailsPendingFor === instanceId) processDetailsPendingFor = ''
  }
})

/** 编辑数据必须由服务端详情返回；行摘要仅提供坐标，关闭后不得继续执行打开事件。 */
const openEdit = async (row: any, options: any = {}) => {
  runtimeDiagnosticsRef.value?.reset()
  resetProcessDetail()
  return openForm('edit', row, options)
}

/** 把服务端字段错误分发到所有表单页签，并定位第一个可操作的错误。 */
async function showServerValidationErrors(error: any) {
  let firstTab = ''
  for (const target of runtimeValidationTargets()) {
    if (await target.instance.applyServerValidationError?.(error)) {
      firstTab ||= target.tabName || ''
    }
  }
  if (firstTab) activeTab.value = firstTab
}

/** 收集当前已挂载的全部表单实例，保留所属页签以便定位错误。 */
function runtimeValidationTargets() {
  const formRefs: Array<{
    instance: InstanceType<typeof EntityDataFormFields>
    tabName?: string
  }> = []
  if (!showOuterTabs.value) {
    if (formFieldsRef.value) {
      formRefs.push({ instance: formFieldsRef.value })
    }
  } else {
    if (showBasicTab.value && basicFormFieldsRef.value) {
      formRefs.push({
        instance: basicFormFieldsRef.value,
        tabName: 'basic'
      })
    }
    runtimeNodeTabs.value.forEach(tab => {
      const formRef = nodeFormFieldsRefs.value[tab.name]
      if (formRef) {
        formRefs.push({ instance: formRef, tabName: tab.name })
      }
    })
  }

  return formRefs
}

/** 校验所有已挂载页签并执行无副作用预校验；服务端在最终提交时仍会权威重验。 */
async function validateRuntimeForms() {
  for (const formRef of runtimeValidationTargets()) {
    if ((await formRef.instance.validate()) === false) {
      // 唯一性或普通字段错误可能位于未激活页签，切过去才能让用户看到就地提示。
      if (formRef.tabName) activeTab.value = formRef.tabName
      return false
    }
  }

  await dataSourceRuntime.value.prevalidateBeforeSubmit({
    form: runtimeForm.value,
    fields: runtimeForm.value?.fields || [],
    nodes: runtimeForm.value?.nodes || []
  })
  return true
}

/**
 * 提交当前表单。startProcess 控制保存后是否发起流程；提交体只包含发布表单
 * 允许的字段，并带上配置摘要及业务来源供服务端实时鉴权、校验配置是否变化。
 */
const handleSubmit = async (startProcess = false, restartProcess = false) => {
  const opening = currentOpening
  try {
    const valid = await validateRuntimeForms()
    if (!valid || !isCurrentOpening(opening)) return
    formData.startProcess = startProcess
    const submittedData = filterRuntimeFormSubmissionData(
      formData.data,
      runtimeForm.value,
      props.entityFields
    )
    const data = {
      entityCode: props.entityCode,
      listKey: props.listKey,
      formId: runtimeForm.value?.id,


      runtimeContext: formReleaseContext.value.runtimeContext,
      formConfigHash:
        formReleaseContext.value.configHash,
      compositionContext:
        launchRuntimeContext.value?.compositionContext
        || undefined,
      id: formData.id,
      name: submittedData?.name || formData.name,
      data: submittedData,
      startProcess: formData.startProcess
    }

    let result
    if (props.submitTransport) {
      // Embed 等受控宿主可替换“最终提交”传输，但字段渲染、校验、联动和按钮
      // 始终走本组件的同一原生运行时，避免形成第二套逐组件兼容实现。
      result = await props.submitTransport({
        actionKey: restartProcess ? 'restartProcess' : formData.startProcess ? 'saveAndStart' : 'save',
        previousProcessInstanceId: restartProcess ? processInstanceId.value : undefined,
        data: submittedData,
        entityCode: props.entityCode,
        formId: runtimeForm.value?.id,


        runtimeContext: formReleaseContext.value.runtimeContext,
      formConfigHash:
          formReleaseContext.value.configHash
      })
    } else if (formData.id) {
      result = await entityDataApi.update(
        props.entityCode,
        formData.id,
        {
          data: submittedData,
          formId: runtimeForm.value?.id,


          runtimeContext: formReleaseContext.value.runtimeContext,
      formConfigHash:
            formReleaseContext.value.configHash,
          compositionContext:
            launchRuntimeContext.value?.compositionContext
            || undefined,
          startProcess: formData.startProcess,
          restartProcess,
          previousProcessInstanceId: restartProcess ? processInstanceId.value : undefined
        },
        formData.startProcess,
        props.listKey,
        listReleaseContext.value
      )
      if (!isCurrentOpening(opening)) return
      ElMessage.success(restartProcess ? '已保存并重新发起' : '更新成功')
    } else {
      result = await entityDataApi.save(
        data,
        data.startProcess,
        listReleaseContext.value
      )
      if (!isCurrentOpening(opening)) return
      ElMessage.success('创建成功')
    }

    if (!isCurrentOpening(opening)) return
    // 保存主表不会提交普通关联草稿；保留弹窗，让用户继续独立保存子表单。
    if (hasUnsavedRelatedForms()) {
      captureResetSnapshot()
      ElMessage.info('主表已保存，子表单仍有未保存的修改')
    } else {
      dialogVisible.value = false
    }
    emit('success', result)
  } catch (error: any) {
    if (!isCurrentOpening(opening)) return
    await showServerValidationErrors(error)
    showRequestError(error, '操作失败')
  }
}

defineExpose({
  openCreate,
  openEdit,
  confirmDiscardChanges
})

function cloneRuntimeValue(value: any) {
  if (value === undefined) return undefined
  if (typeof structuredClone === 'function') {
    try {
      return structuredClone(value)
    } catch {
      // Fall through for values unsupported by structuredClone.
    }
  }
  if (value && typeof value === 'object') {
    return JSON.parse(JSON.stringify(value))
  }
  return value
}

function applyCreateInitialData(initialData: Record<string, any>) {
  Object.entries(initialData || {}).forEach(([key, value]) => {
    formData.data[key] = cloneRuntimeValue(value)
  })
}
</script>

<style scoped lang="scss">
.form-dialog-title {
  display: inline-block;
  max-width: min(720px, 72vw);
  overflow: hidden;
  color: var(--el-text-color-primary);
  font-size: var(--el-dialog-title-font-size);
  line-height: var(--el-dialog-font-line-height);
  text-overflow: ellipsis;
  vertical-align: top;
  white-space: nowrap;
}

.entity-form-dialog {
  margin-top: 15px !important;
  margin-bottom: 15px !important;
  height: 94vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.entity-form-dialog :deep(.el-dialog__body) {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.form-opening-body,
.form-dialog-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}
.form-dialog-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}
.form-dialog-tabs :deep(.el-tab-pane) {
  height: 100%;
}

// 窄 iframe 与手机采用同一断点；Dialog Teleport 到 body 后仍需要此全局选择器。
@media (max-width: 900px) {
  :global(.el-dialog.entity-data-form-dialog:not(.entity-form-dialog--seamless)) {
    width: calc(100vw - 24px) !important;
    margin: 12px auto !important;
    height: calc(100vh - 24px);
    height: calc(100dvh - 24px);
    display: flex;
    flex-direction: column;
    overflow: hidden;
    box-sizing: border-box;
  }

  :global(.entity-data-form-dialog .el-dialog__header),
  :global(.entity-data-form-dialog .el-dialog__footer) {
    flex: 0 0 auto;
  }

  :global(.entity-data-form-dialog .el-dialog__body) {
    flex: 1 1 auto;
    min-height: 0;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
  }

  :global(.entity-data-form-dialog .el-dialog__footer) {
    padding-bottom: calc(16px + env(safe-area-inset-bottom));
    background: var(--el-bg-color);
  }
}

// Embed seamless 模式只改变最外层表单容器；MessageBox、Popper 等二级浮层保持原样。
:global(.el-dialog.entity-data-form-dialog.entity-form-dialog--seamless) {
  box-sizing: border-box;
  width: 100% !important;
  max-width: none;
  height: 100vh;
  height: 100dvh;
  max-height: none;
  margin: 0 !important;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 0;
  box-shadow: none;
}

:global(.entity-data-form-dialog.entity-form-dialog--seamless .el-dialog__header),
:global(.entity-data-form-dialog.entity-form-dialog--seamless .el-dialog__footer) {
  flex: 0 0 auto;
}

:global(.entity-data-form-dialog.entity-form-dialog--seamless .el-dialog__body) {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
}

:global(.entity-data-form-dialog.entity-form-dialog--seamless .el-dialog__footer) {
  padding-bottom: calc(16px + env(safe-area-inset-bottom));
  background: var(--el-bg-color);
}
</style>
