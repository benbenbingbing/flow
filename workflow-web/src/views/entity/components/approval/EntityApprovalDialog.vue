<template>
  <el-dialog
    v-model="processDialogVisible"
    width="75%"
    :class="[
      'entity-form-dialog',
      'entity-approval-dialog',
      { 'entity-form-dialog--seamless': seamlessPresentation }
    ]"
    top="3vh"
    :fullscreen="seamlessPresentation"
    :modal="!seamlessPresentation"
    :lock-scroll="!seamlessPresentation"
    :close-on-click-modal="false"
    @closed="handleDialogClosed"
  >
    <template #header="{ titleId, titleClass }">
      <RuntimeVersionDiagnostics
        ref="runtimeDiagnosticsRef"
        :entries="dialogRuntimeDiagnosticEntries"
        :copy-entries="dialogRuntimeDiagnosticCopyEntries"
        copy-title="业务数据运行版本排障信息"
        :reset-key="dialogRuntimeDiagnosticResetKey"
      >
        <span
          :id="titleId"
          :class="[titleClass, 'approval-dialog-title']"
        >{{ approvalDialogTitle }}</span>
      </RuntimeVersionDiagnostics>
    </template>
    <div class="approval-dialog-body">
      <el-tabs v-model="activeDialogTab" type="border-card" class="approval-tabs">
        <el-tab-pane v-if="approvalShowBasicTab" label="基本信息" name="basic">
          <EntityApprovalBasicInfo
            ref="basicInfoRef"
            v-model:entityData="entityData"
            :approvalNormalForm="approvalNormalForm"
            :formReadonly="approvalFormReadonly"
            :mode="approvalRuntimeMode"
            :entityCode="effectiveEntityCode"
            :entityDefinition="entityDefinition"
            :entityFields="entityFields"
            :context="approvalRuntimeContext"
            :dataSourceRuntime="dataSourceRuntime"
            :excludedNodeIds="approvalLiftedRootNodeIds"
            :form-actions="formActions"
            :action-loading-key="actionLoadingKey"
            :entity-status-options="entityStatusOptions"
            @form-action="handleFormAction"
          />
        </el-tab-pane>

        <el-tab-pane
          v-for="tab in approvalNodeTabs"
          :key="tab.name"
          :label="tab.label"
          :name="tab.name"
        >
          <EntityApprovalBasicInfo
            :ref="(instance) => setNodeTabRef(tab.name, instance)"
            v-model:entityData="entityData"
            :approvalNormalForm="approvalNormalForm"
            :formReadonly="approvalFormReadonly"
            :mode="approvalRuntimeMode"
            :entityCode="effectiveEntityCode"
            :entityDefinition="entityDefinition"
            :entityFields="entityFields"
            :context="approvalRuntimeContext"
            :dataSourceRuntime="dataSourceRuntime"
            :nodeRootParentId="tab.rootParentId"
            :form-actions="formActions"
            :action-loading-key="actionLoadingKey"
            :entity-status-options="entityStatusOptions"
            @form-action="handleFormAction"
          />
        </el-tab-pane>

        <!-- 延迟到页签可见后再创建 Viewer，避免在零尺寸隐藏容器中初始化。 -->
        <el-tab-pane
          v-if="currentTask?.processInstanceId"
          label="流程图"
          name="diagram"
          lazy
        >
          <EntityApprovalDiagram
            :bpmnXml="bpmnXml"
            :progressData="progressData"
            :processInstanceId="currentTask.processInstanceId"
          />
        </el-tab-pane>

        <!-- 审批历史（仅在有流程实例时显示） -->
        <el-tab-pane v-if="currentTask?.processInstanceId" label="审批历史" name="history">
          <EntityApprovalHistory :processHistory="processHistory" />
        </el-tab-pane>

        <el-tab-pane
          v-if="currentTask?.processInstanceId && userStore.isSuperAdmin"
          label="动作执行记录"
          name="actionExecutions"
        >
          <FlowActionExecutionLog
            :process-instance-id="currentTask.processInstanceId"
            :active="activeDialogTab === 'actionExecutions'"
          />
        </el-tab-pane>
      </el-tabs>

      <ApprovalDecisionPanel
        v-if="showApprovalDecisionSection"
        ref="approvalDecisionRef"
        v-model:action="approveForm.action"
        v-model:comment="approveForm.comment"
        v-model:expanded="approvalDecisionExpanded"
        :approval-config="effectiveApprovalConfig"
        :preview="nextApproverPreview"
        :loading="nextApproverPreviewLoading"
        :task-id="currentTask?.taskId || ''"
        :action-label="selectedApprovalOption?.label || ''"
        :form-data="entityData || {}"
      />
    </div>

    <template #footer>
      <div class="approval-dialog-footer">
        <FormActionBar
          :actions="footerActions"
          :loading-key="actionLoadingKey"
          @action="handleFormAction"
        />
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { entityDataApi } from '@/api/entity'
import { completeTask } from '@/api/processTask'
import FormActionBar from '@/components/FormActionBar.vue'
import {
  isRuntimeFieldVisible,
  isRuntimeFormReadonly,
  createFormDataSourceRuntime,
  normalizeEntityRecordForForm,
  resolveRuntimeFormTabLayout
} from '@/shared/form-runtime'
import { useProcessDetail } from '@/composables/useProcessDetail'
import { useNextApproverPreview } from '@/composables/useNextApproverPreview'
import { useUserStore } from '@/stores/user'
import EntityApprovalBasicInfo from './EntityApprovalBasicInfo.vue'
import EntityApprovalHistory from './EntityApprovalHistory.vue'
import EntityApprovalDiagram from './EntityApprovalDiagram.vue'
import ApprovalDecisionPanel from './ApprovalDecisionPanel.vue'
import FlowActionExecutionLog from '@/components/FlowActionExecutionLog.vue'
import RuntimeVersionDiagnostics from '@/components/RuntimeVersionDiagnostics.vue'
import {
  resolveApprovalEntityCode,
  resolveApprovalFormConfig
} from './entityApprovalDisplay.js'
import {
  executeCustomFormAction,
  resolveRuntimeFormActions
} from '@/shared/form-action-runtime'
import { footerFormActions } from '@/shared/form-actions'
import { isWorkflowReady } from '@/shared/entity-design'
import {
  buildEntityStatusMap,
  withEntityStatusRuntimeForm
} from '@/shared/entity-status-runtime'
import {
  hasNextApproverPresentation,
  normalizeNextApproverPreview
} from '@/shared/next-approver'
import { BUSINESS_TRACE_HEADER } from '@/shared/request'
import { resolveActionableTaskId } from '@/utils/listButtonPermission'
import { formatRuntimeCodeVersion } from '@/shared/runtime-diagnostics'

const props = withDefaults(defineProps<{
  entityCode?: string
  defaultForm?: any
  entityDefinition?: any
  entityFields?: any[]
  listKey?: string
  listReleaseId?: string
  listReleaseVersion?: number | null
  listReleaseResolutionToken?: string
  entityStatusOptions?: any[]
  formPresentation?: 'seamless' | 'dialog'
}>(), {
  entityCode: '',
  defaultForm: null,
  entityDefinition: () => ({}),
  entityFields: () => [],
  listKey: '',
  listReleaseId: '',
  listReleaseVersion: null,
  listReleaseResolutionToken: '',
  entityStatusOptions: () => [],
  formPresentation: 'dialog'
})

const emit = defineEmits<{
  success: []
  closed: []
}>()
const userStore = useUserStore()
const router = useRouter()
const launchRuntimeContext = ref<Record<string, any>>({})
const listReleaseContext = computed(() => ({
  releaseId: props.listReleaseId || undefined,
  releaseVersion: props.listReleaseVersion ?? undefined,
  releaseResolutionToken:
    props.listReleaseResolutionToken || undefined,
  viewCompositionTraversalToken:
    launchRuntimeContext.value?.viewCompositionTraversalToken || undefined
}))

const processDialogVisible = ref(false)
const seamlessPresentation = computed(() => props.formPresentation === 'seamless')
const activeDialogTab = ref('basic')
const approveSubmitLoading = ref(false)
const formActions = ref<any[]>([])
const actionLoadingKey = ref('')
const currentTask = ref<any>(null)
const isViewMode = ref(false)
const overrideForm = ref<any>(null)
const runtimeDiagnosticsRef = ref<InstanceType<typeof RuntimeVersionDiagnostics>>()
const formReleaseContext = computed(() => ({
  releaseId:
    overrideForm.value?.runtimeReleaseId
    || overrideForm.value?.formReleaseId
    || undefined,
  releaseVersion:
    overrideForm.value?.runtimeReleaseVersion
    ?? overrideForm.value?.formReleaseVersion
    ?? undefined,
  releaseResolutionToken:
    overrideForm.value?.releaseResolutionToken || undefined,
  viewCompositionTraversalToken:
    launchRuntimeContext.value?.viewCompositionTraversalToken || undefined
}))
const basicInfoRef = ref<any>()
const nodeTabRefs = ref<Record<string, any>>({})
const approvalDecisionRef = ref<any>()
const approvalDecisionExpanded = ref(true)

const approveForm = reactive({
  action: 'approve',
  comment: '',
  transferTo: ''
})

const {
  bpmnXml,
  progressData,
  processHistory,
  entityData,
  formConfig,
  formConfigs,
  approvalConfig,
  processRuntimeMetadata,
  getProcessStatusText,
  loadProcessDetail
} = useProcessDetail()

const approvalDialogTitle = computed(() => {
  const status = currentTask.value?.processStatus
  const statusText = status ? `（${getProcessStatusText(status)}）` : ''
  return `${currentTask.value?.name || '任务审批'}${statusText}`
})

// 计算属性：获取当前有效的审批配置
const effectiveApprovalConfig = computed(() => {
  if (approvalConfig.value) {
    return approvalConfig.value
  }
  return {
    enabled: true,
    commentLabel: '审批意见',
    options: [
      { value: 'approve', label: '通过', type: 'primary', showComment: true },
      { value: 'reject', label: '驳回', type: 'danger', showComment: true }
    ]
  }
})

const workflowFormConfig = computed(() =>
  resolveApprovalFormConfig(formConfig.value, props.defaultForm)
)
const effectiveFormConfig = computed(() =>
  overrideForm.value || workflowFormConfig.value
)
const approvalFormReadonly = computed(() => {
  return isViewMode.value
    || isRuntimeFormReadonly(workflowFormConfig.value)
    || isRuntimeFormReadonly(effectiveFormConfig.value)
})
const statusAwareFormConfig = computed(() =>
  withEntityStatusRuntimeForm(
    effectiveFormConfig.value,
    props.entityFields,
    props.entityStatusOptions
  )
)
const approvalRuntimeMode = computed(() => isViewMode.value ? 'view' : 'approve')
const effectiveEntityCode = computed(() =>
  resolveApprovalEntityCode(
    props.entityCode,
    entityData.value,
    currentTask.value
  )
)
const approvalRuntimeContext = computed(() => ({
  ...launchRuntimeContext.value,
  entityCode: effectiveEntityCode.value,
  mode: approvalRuntimeMode.value,
  record: entityData.value,
  task: currentTask.value,
  processInstanceId: currentTask.value?.processInstanceId,
  entityStatusMap: buildEntityStatusMap(props.entityStatusOptions),
  entityStatusOptions: props.entityStatusOptions,
  releaseResolutionToken: effectiveFormConfig.value?.releaseResolutionToken
}))
const dataSourceRuntime = createFormDataSourceRuntime({
  getRecord: () => entityData.value || {},
  getRecordId: () => entityData.value?.id,
  getListKey: () => props.listKey,
  getMode: () => approvalRuntimeMode.value,
  getForm: () => approvalNormalForm.value
})

const approvalNormalForm = computed(() => {
  const sourceForm = statusAwareFormConfig.value
  if (!sourceForm) return null
  const fields = (sourceForm.fields || [])
    .filter((f: any) => isRuntimeFieldVisible(f, approvalRuntimeMode.value))
  return {
    ...sourceForm,
    fields
  }
})
const resolvedDiagnosticForm = computed(() =>
  approvalNormalForm.value || effectiveFormConfig.value
)
const dialogRuntimeDiagnosticEntries = computed(() => {
  const process = processRuntimeMetadata.value || {}
  const form = resolvedDiagnosticForm.value
  const processValue = process.processInstanceId
    ? formatRuntimeCodeVersion(process.processKey, process.processVersion)
    : '未关联流程'
  const formVersion = form?.runtimeReleaseVersion ?? form?.formReleaseVersion
  const hotfixSuffix = form?.hotfixApplied === true ? '（已应用热修复）' : ''
  const formValue = form
    ? `${formatRuntimeCodeVersion(form.formKey, formVersion)}${hotfixSuffix}`
    : '未记录运行表单'
  return [
    ...(process.processInstanceId
      ? [{ label: '流程', value: processValue }]
      : []),
    { label: '表单', value: formValue }
  ]
})
const dialogRuntimeDiagnosticCopyEntries = computed(() => {
  const process = processRuntimeMetadata.value || {}
  return [
    ...(props.listKey
      ? [{ label: '列表', value: formatRuntimeCodeVersion(
        props.listKey,
        props.listReleaseVersion
      ) }]
      : []),
    ...dialogRuntimeDiagnosticEntries.value,
    { label: '记录 ID', value: entityData.value?.id },
    { label: '流程实例 ID', value: process.processInstanceId }
  ]
})
const dialogRuntimeDiagnosticResetKey = computed(() => [
  processDialogVisible.value ? 'open' : 'closed',
  isViewMode.value ? 'view' : 'approve',
  currentTask.value?.taskId || '',
  entityData.value?.id || '',
  processRuntimeMetadata.value?.processInstanceId || '',
  resolvedDiagnosticForm.value?.runtimeReleaseId
    || resolvedDiagnosticForm.value?.formReleaseId
    || '',
  resolvedDiagnosticForm.value?.runtimeReleaseVersion
    ?? resolvedDiagnosticForm.value?.formReleaseVersion
    ?? ''
].join(':'))

function handleDialogClosed() {
  runtimeDiagnosticsRef.value?.reset()
  processRuntimeMetadata.value = {}
  emit('closed')
}
const approvalTabLayout = computed(() =>
  resolveRuntimeFormTabLayout(approvalNormalForm.value)
)
const approvalNodeTabs = computed(() => approvalTabLayout.value.tabs)
const approvalLiftedRootNodeIds = computed(() =>
  approvalTabLayout.value.liftedRootNodeIds
)
const approvalHasFormTabs = computed(() => approvalNodeTabs.value.length > 0)
const approvalShowBasicTab = computed(() =>
  approvalTabLayout.value.hasBaseContent || !approvalHasFormTabs.value
)
const approvalFormTabNames = computed(() => [
  ...(approvalShowBasicTab.value ? ['basic'] : []),
  ...approvalNodeTabs.value.map(tab => tab.name)
])
const firstApprovalFormTabName = computed(() =>
  approvalFormTabNames.value[0] || 'basic'
)
const isApprovalFormTab = computed(() =>
  approvalFormTabNames.value.includes(activeDialogTab.value)
)
const selectedApprovalOption = computed(() =>
  effectiveApprovalConfig.value.options?.find(
    (option: any) => option.value === approveForm.action
  )
)
const {
  preview: nextApproverPreview,
  loading: nextApproverPreviewLoading,
  reset: resetNextApproverPreview,
  refresh: refreshNextApproverPreview,
  schedule: scheduleNextApproverPreview,
  ensureCurrent: ensureNextApproverPreviewCurrent,
  getCurrentTraceKey: getNextApproverPreviewTraceKey
} = useNextApproverPreview({
  getTaskId: () => currentTask.value?.taskId,
  getAction: () => approveForm.action,
  getActionLabel: () => selectedApprovalOption.value?.label,
  getComment: () => approveForm.comment,
  getFormData: () => entityData.value,
  isEnabled: () => !isViewMode.value && processDialogVisible.value
})
const showApprovalDecisionSection = computed(() =>
  !isViewMode.value
  && isApprovalFormTab.value
  && (
    effectiveApprovalConfig.value.enabled !== false
    || hasNextApproverPresentation(
      nextApproverPreview.value,
      nextApproverPreviewLoading.value
    )
  )
)
const footerActions = computed(() =>
  footerFormActions(formActions.value).filter(action =>
    action.key !== 'submitApproval' || isApprovalFormTab.value
  )
)
const runtimeForms = computed(() => {
  if (overrideForm.value) {
    return [approvalNormalForm.value].filter(Boolean)
  }
  const configured = Array.isArray(formConfigs.value)
    ? formConfigs.value.filter(Boolean)
    : []
  return configured.length
    ? configured
    : [approvalNormalForm.value].filter(Boolean)
})

function setNodeTabRef(tabName: string, instance: any) {
  if (instance) {
    nodeTabRefs.value[tabName] = instance
  } else {
    delete nodeTabRefs.value[tabName]
  }
}

async function loadFormActions() {
  formActions.value = await resolveRuntimeFormActions(runtimeForms.value, {
    entityCode: effectiveEntityCode.value,
    listKey: props.listKey,
    mode: approvalRuntimeMode.value,
    recordId: entityData.value?.id || undefined,
    taskId: currentTask.value?.taskId || undefined,
    workflowReady: isWorkflowReady(props.entityDefinition),
    hasProcessInstance: Boolean(currentTask.value?.processInstanceId),
    canApprove: !isViewMode.value && Boolean(currentTask.value?.taskId),
    systemEntity: props.entityDefinition?.storageMode === 'SYSTEM',
    viewCompositionTraversalToken:
      launchRuntimeContext.value?.viewCompositionTraversalToken || undefined
  })
}

watch(
  () => approveForm.action,
  scheduleNextApproverPreview
)
watch(entityData, scheduleNextApproverPreview, { deep: true })
watch(processDialogVisible, visible => {
  if (!visible) resetNextApproverPreview()
})

watch(
  () => [
    approvalNormalForm.value?.id,
    entityData.value?.id,
    approvalRuntimeMode.value
  ],
  async () => {
    if (!approvalNormalForm.value || !entityData.value) return
    try {
      await dataSourceRuntime.initialize({
        form: approvalNormalForm.value,
        fields: approvalNormalForm.value.fields || [],
        nodes: approvalNormalForm.value.nodes || []
      })
    } catch (error) {
      console.warn('审批表单数据源初始化失败:', error)
      ElMessage.error('审批表单初始化失败')
    }
  }
)

// 打开审批弹窗
interface OpenApproveOptions {
  form?: any
  context?: Record<string, any>
  requireActionCapability?: boolean
}

const openApprove = async (
  row: any,
  options: OpenApproveOptions = {}
) => {
  const actionableTaskId = resolveActionableTaskId(row, 'approve', {
    requireActionCapability: options.requireActionCapability
  })
  if (!actionableTaskId) {
    ElMessage.error('未获取到当前用户可办理的审批任务，请刷新列表后重试')
    return false
  }
  runtimeDiagnosticsRef.value?.reset()
  // 审批任务可能复用同一流程实例，仍需先清空上一个任务的诊断坐标。
  processRuntimeMetadata.value = {}
  resetNextApproverPreview()
  // 每次打开审批任务都恢复完整审批信息，避免沿用上一次弹窗的折叠状态。
  approvalDecisionExpanded.value = true
  overrideForm.value = options.form || null
  launchRuntimeContext.value = { ...(options.context || {}) }
  isViewMode.value = false
  currentTask.value = {
    taskId: actionableTaskId,
    processInstanceId: row.processInstanceId,
    name: row.currentTaskName || row.name || '任务审批',
    startUserName: row.startUserName,
    processName: row.processName,
    entityCode: row.entityCode
  }
  approveForm.action = 'approve'
  approveForm.comment = ''
  activeDialogTab.value = 'basic'
  const loaded = await loadProcessDetail(row.processInstanceId, {
    startUserName: currentTask.value?.startUserName,
    taskId: currentTask.value?.taskId,
    onLoad: (progressRes: any) => {
      if (currentTask.value) {
        currentTask.value.processStatus = progressRes.status
        if (progressRes.processName) {
          currentTask.value.processName = progressRes.processName
        }
      }
      const config = progressRes.approvalConfig
      if (config && Array.isArray(config.options) && config.options.length > 0) {
        const firstOption = config.options[0]
        if (firstOption && firstOption.value) {
          approveForm.action = firstOption.value
        }
      }
      activeDialogTab.value = firstApprovalFormTabName.value
    }
  })
  if (!loaded) {
    ElMessage.error('加载最新流程表单失败，请重试')
    return
  }
  await reloadExplicitFormDetail(row)
  await loadFormActions()
  processDialogVisible.value = true
  await nextTick()
  await refreshNextApproverPreview()
}

interface OpenViewOptions {
  defaultTab?: string
  startUserName?: string
  form?: any
  context?: Record<string, any>
}

// 打开查看弹窗（只读模式）
const openView = async (row: any, options: OpenViewOptions = {}) => {
  runtimeDiagnosticsRef.value?.reset()
  // 无流程的独立数据不会调用进度接口，必须先清空上一次实例坐标，避免诊断信息串行污染。
  processRuntimeMetadata.value = {}
  resetNextApproverPreview()
  const { defaultTab, startUserName, form, context } = options
  overrideForm.value = form || null
  launchRuntimeContext.value = { ...(context || {}) }
  isViewMode.value = true
  currentTask.value = {
    processInstanceId: row.processInstanceId,
    name: row.name || row.currentTaskName || '数据详情',
    startUserName: startUserName || row.startUserName,
    processName: row.processName,
    entityCode: row.entityCode
  }
  activeDialogTab.value = defaultTab || 'basic'
  if (row.processInstanceId) {
    const loaded = await loadProcessDetail(row.processInstanceId, {
      startUserName: currentTask.value?.startUserName,
      onLoad: (progressRes: any) => {
        if (currentTask.value) {
          currentTask.value.processStatus = progressRes.status
          if (progressRes.processName) {
            currentTask.value.processName = progressRes.processName
          }
        }
        if (!defaultTab) {
          activeDialogTab.value = firstApprovalFormTabName.value
        }
      }
    })
    if (!loaded) {
      ElMessage.error('加载最新流程表单失败，请重试')
      return false
    }
    await reloadExplicitFormDetail(row)
  } else {
    try {
      const detail = await entityDataApi.getDetail(
        effectiveEntityCode.value,
        row.id,
        props.listKey,
        overrideForm.value?.id,
        listReleaseContext.value,
        formReleaseContext.value
      )
      entityData.value = normalizeEntityRecordForForm(detail)
      const discoveredProcessInstanceId = String(
        entityData.value?.processInstanceId || ''
      )
      if (discoveredProcessInstanceId) {
        // 调用方的列表投影可能未携带 processInstanceId；详情恢复后仍复用本组件
        // 原有流程加载链，确保流程图与审批历史不会在 Embed/普通列表中静默丢失。
        currentTask.value.processInstanceId = discoveredProcessInstanceId
        const loaded = await loadProcessDetail(discoveredProcessInstanceId, {
          startUserName: currentTask.value?.startUserName,
          onLoad: (progressRes: any) => {
            if (!currentTask.value) return
            currentTask.value.processStatus = progressRes.status
            if (progressRes.processName) {
              currentTask.value.processName = progressRes.processName
            }
          }
        })
        if (!loaded) {
          ElMessage.error('加载最新流程表单失败，请重试')
          return false
        }
        await reloadExplicitFormDetail(row)
        activeDialogTab.value = firstApprovalFormTabName.value
      } else {
        const standaloneForm = overrideForm.value || props.defaultForm
        if (standaloneForm?.fields?.length > 0 || standaloneForm?.nodes?.length > 0) {
          formConfig.value = standaloneForm
          formConfigs.value = [standaloneForm]
          activeDialogTab.value = firstApprovalFormTabName.value
        } else {
          formConfig.value = null
          formConfigs.value = []
          activeDialogTab.value = 'basic'
        }
      }
    } catch (e) {
      console.error('加载数据详情失败:', e)
      ElMessage.error('加载详情失败')
      return false
    }
  }
  await loadFormActions()
  processDialogVisible.value = true
  return true
}

async function reloadExplicitFormDetail(row: any) {
  if (!overrideForm.value?.id || !row?.id) return
  try {
    const detail = await entityDataApi.getDetail(
      effectiveEntityCode.value,
      row.id,
      props.listKey,
      overrideForm.value.id,
      listReleaseContext.value,
      formReleaseContext.value
    )
    entityData.value = {
      ...(entityData.value || {}),
      ...normalizeEntityRecordForForm(detail)
    }
  } catch (error: any) {
    console.error('加载按钮指定表单详情失败:', error)
    throw new Error(
      error?.message || '加载按钮指定表单详情失败，请检查表单发布版本'
    )
  }
}

interface ApprovalFormValidationResult {
  valid: boolean
  tabName?: string
  tabLabel?: string
  message?: string
}

/**
 * 逐页签校验并保留失败位置。唯一预检可能在非当前页签中失败，
 * 若只返回 boolean，用户既看不到错误字段，也只能得到误导性的“必填项”提示。
 */
async function validateApprovalForms(): Promise<ApprovalFormValidationResult> {
  const targets = [
    ...(approvalShowBasicTab.value && basicInfoRef.value
      ? [{ name: 'basic', label: '基本信息', formRef: basicInfoRef.value }]
      : []),
    ...approvalNodeTabs.value
      .map(tab => ({
        name: tab.name,
        label: tab.label,
        formRef: nodeTabRefs.value[tab.name]
      }))
      .filter(target => Boolean(target.formRef))
  ]
  for (const target of targets) {
    if ((await target.formRef.validate?.()) !== false) continue
    activeDialogTab.value = target.name
    await nextTick()
    return {
      valid: false,
      tabName: target.name,
      tabLabel: target.label,
      message: String(
        target.formRef.getValidationError?.() || ''
      ).trim()
    }
  }
  return { valid: true }
}

function approvalValidationMessage(result: ApprovalFormValidationResult) {
  const detail = result.message || '请检查当前页签中的表单校验错误'
  return result.tabLabel ? `${result.tabLabel}：${detail}` : detail
}

async function confirmAction(action: any) {
  if (action?.confirm?.enabled !== true) return true
  try {
    await ElMessageBox.confirm(
      action.confirm.message || `确认执行“${action.label}”？`,
      '操作确认',
      { type: 'warning' }
    )
    return true
  } catch {
    return false
  }
}

async function handleFormAction(action: any) {
  if (!action || action.enabled === false || actionLoadingKey.value) return
  if (!(await confirmAction(action))) return
  actionLoadingKey.value = String(action.runtimeKey || action.key || '')
  try {
    if (action.key === 'close') {
      processDialogVisible.value = false
      return
    }
    if (action.key === 'submitApproval') {
      await submitApprove()
      return
    }
    if (action.type !== 'custom') return
    if (action.validateBeforeExecute) {
      const validation = await validateApprovalForms()
      if (!validation.valid) {
        ElMessage.warning(approvalValidationMessage(validation))
        return
      }
    }
    const result = await executeCustomFormAction(
      action,
      runtimeForms.value,
      {
        entityCode: effectiveEntityCode.value,
        listKey: props.listKey,
        mode: approvalRuntimeMode.value,
        recordId: entityData.value?.id || undefined,
        taskId: currentTask.value?.taskId || undefined,
        task: currentTask.value,
        formData: entityData.value,
        processInstanceId: currentTask.value?.processInstanceId,
        viewCompositionTraversalToken:
          launchRuntimeContext.value?.viewCompositionTraversalToken || undefined
      }
    )
    await applyFormEventResult(result)
    if (result?.message) {
      ElMessage.success(result.message)
    }
  } catch (error: any) {
    ElMessage.error(error.message || '按钮操作执行失败')
  } finally {
    actionLoadingKey.value = ''
  }
}

async function applyFormEventResult(result: any) {
  const effects = Array.isArray(result?.effects) ? result.effects : []
  for (const effect of effects) {
    const type = String(effect?.type || '').toUpperCase()
    if (type === 'FIELD_MAPPING') {
      const mappings = Array.isArray(effect.mappings) ? effect.mappings : []
      for (const mapping of mappings) {
        const targetPath = String(mapping?.targetPath || '')
          .replace(/^form\./, '')
          .replace(/^data\./, '')
        if (!targetPath) continue
        const value = resolvePath(effect.data || {}, mapping?.targetPath)
        const current = resolvePath(entityData.value, targetPath)
        const overwrite = String(mapping?.overwrite || 'ALWAYS').toUpperCase()
        if (overwrite === 'IF_EMPTY' && !emptyValue(current)) continue
        if (overwrite === 'CONFIRM' && !emptyValue(current) && current !== value) {
          try {
            await ElMessageBox.confirm(
              `字段“${fieldName(targetPath)}”已有值，是否覆盖？`,
              '确认回填',
              { type: 'warning' }
            )
          } catch {
            continue
          }
        }
        setPath(entityData.value, targetPath, value)
      }
      continue
    }
    if (type === 'MESSAGE' && effect.message) {
      ElMessage({
        type: effect.level || 'success',
        message: effect.message
      })
      continue
    }
    if (type === 'OPEN_ROUTE' && effect.route) {
      await router.push(effect.route)
      continue
    }
    if (type === 'CLOSE_FORM') {
      processDialogVisible.value = false
      continue
    }
    if (type === 'REFRESH_PARENT') {
      emit('success')
      continue
    }
    if (type === 'DOWNLOAD_TASK') {
      ElMessage.success(effect.message || '下载任务已创建')
    }
  }
  if (!effects.length && result?.data && typeof result.data === 'object') {
    const patch = result.data.form || result.data.data || result.data
    Object.entries(patch || {}).forEach(([key, value]) => {
      entityData.value[key] = value
    })
  }
}

function resolvePath(source: any, path: string) {
  return String(path || '')
    .replace(/^form\./, '')
    .replace(/^data\./, '')
    .split('.')
    .filter(Boolean)
    .reduce((current, key) => current?.[key], source)
}

function setPath(target: Record<string, any>, path: string, value: any) {
  const parts = String(path || '').split('.').filter(Boolean)
  if (!parts.length) return
  let current: Record<string, any> = target
  parts.slice(0, -1).forEach(part => {
    if (!current[part] || typeof current[part] !== 'object') {
      current[part] = {}
    }
    current = current[part]
  })
  current[parts[parts.length - 1]] = value
}

function emptyValue(value: any) {
  return value == null
    || value === ''
    || (Array.isArray(value) && value.length === 0)
}

function fieldName(path: string) {
  const code = String(path || '').split('.')[0]
  const field = props.entityFields.find((item: any) =>
    String(item.fieldCode) === code)
  return field?.fieldName || field?.fieldLabel || code
}

function isNextApprovalScopeChanged(error: any) {
  const codes = [
    error?.errorCode,
    error?.source?.errorCode,
    error?.source?.code,
    error?.response?.data?.errorCode,
    error?.response?.data?.code,
    error?.code
  ].map(value => String(value || '').toUpperCase())
  return codes.includes('NEXT_APPROVAL_SCOPE_CHANGED')
}

function isDeferredDefaultRequired(error: any) {
  const codes = [
    error?.errorCode,
    error?.source?.errorCode,
    error?.source?.code,
    error?.response?.data?.errorCode,
    error?.response?.data?.code,
    error?.code
  ].map(value => String(value || '').toUpperCase())
  return codes.includes('NEXT_APPROVER_DEFERRED_DEFAULT_REQUIRED')
}

// 提交审批
const submitApprove = async () => {
  if (!currentTask.value?.taskId || approveSubmitLoading.value) return
  approveSubmitLoading.value = true
  try {
    const validation = await validateApprovalForms()
    if (!validation.valid) {
      ElMessage.warning(approvalValidationMessage(validation))
      return
    }
    await dataSourceRuntime.prevalidateBeforeSubmit({
      form: approvalNormalForm.value,
      fields: approvalNormalForm.value?.fields || [],
      nodes: approvalNormalForm.value?.nodes || []
    })
    await ensureNextApproverPreviewCurrent()
    const nextApproverValidation = approvalDecisionRef.value?.validate?.()
      || { valid: true, message: '' }
    if (!nextApproverValidation.valid) {
      ElMessage.warning(nextApproverValidation.message)
      return
    }
    const changedSelections =
      approvalDecisionRef.value?.getChangedSelections?.() || []
    const completePayload: Record<string, any> = {
      taskId: currentTask.value.taskId,
      action: approveForm.action,
      actionLabel: selectedApprovalOption.value?.label,
      comment: approveForm.comment,
      formData: entityData.value
    }
    if (changedSelections.length > 0) {
      completePayload.nextApprovalScopeKey = nextApproverPreview.value.scopeKey
      completePayload.nextApproverSelections = changedSelections
    }
    const previewTraceKey = getNextApproverPreviewTraceKey()
    await completeTask(completePayload, {
      silentError: true,
      ...(previewTraceKey
        ? { headers: { [BUSINESS_TRACE_HEADER]: previewTraceKey } }
        : {})
    })
    ElMessage.success('审批成功')
    processDialogVisible.value = false
    emit('success')
  } catch (e: any) {
    console.error('审批失败:', e)
    if (isDeferredDefaultRequired(e)) {
      const message = e?.message
        || '延迟解析的下一审批节点必须配置可用默认审批人，请联系流程管理员'
      nextApproverPreview.value = normalizeNextApproverPreview({
        status: 'BLOCKED',
        message
      })
      ElMessage.error(message)
      return
    }
    if (isNextApprovalScopeChanged(e)) {
      ElMessage.warning('下一审批人范围已变化，请确认刷新后的人员后再次提交')
      await refreshNextApproverPreview()
      return
    }
    ElMessage.error(e?.message || '审批失败')
  } finally {
    approveSubmitLoading.value = false
  }
}

defineExpose({
  openApprove,
  openView
})
</script>

<style scoped lang="scss">
:global(.el-dialog.entity-approval-dialog) {
  --approval-dialog-margin: clamp(12px, 2.2vh, 24px);
  box-sizing: border-box;
  margin-top: var(--approval-dialog-margin) !important;
  margin-bottom: var(--approval-dialog-margin) !important;
  height: calc(100vh - var(--approval-dialog-margin) * 2);
  height: calc(100dvh - var(--approval-dialog-margin) * 2);
  max-height: calc(100vh - var(--approval-dialog-margin) * 2);
  max-height: calc(100dvh - var(--approval-dialog-margin) * 2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

:global(.entity-approval-dialog .el-dialog__header) {
  flex: 0 0 auto;
}

:global(.entity-approval-dialog .el-dialog__body) {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
}

:global(.entity-approval-dialog .el-dialog__footer) {
  position: relative;
  z-index: 2;
  flex: 0 0 auto;
  padding-bottom: calc(16px + env(safe-area-inset-bottom));
  border-top: 1px solid #e4e7ed;
  background: #ffffff;
}

.approval-dialog-body {
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.approval-dialog-title {
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

.approval-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 0;
}
.approval-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}
.approval-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.approval-dialog-footer {
  min-height: 32px;
}

@media (max-width: 900px) {
  :global(.el-dialog.entity-approval-dialog:not(.entity-form-dialog--seamless)) {
    --approval-dialog-margin: 12px;
    width: calc(100vw - 24px) !important;
  }
}

// Embed seamless 模式铺满 iframe，且不改变审批确认等二级弹窗的遮罩行为。
:global(.el-dialog.entity-approval-dialog.entity-form-dialog--seamless) {
  --approval-dialog-margin: 0px;
  width: 100% !important;
  max-width: none;
  height: 100vh;
  height: 100dvh;
  max-height: none;
  margin: 0 !important;
  border-radius: 0;
  box-shadow: none;
}

</style>
