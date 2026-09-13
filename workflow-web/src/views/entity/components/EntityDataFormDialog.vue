<template>
  <el-dialog
    v-model="dialogVisible"
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
        <EntityApprovalDiagram
          :bpmnXml="bpmnXml"
          :progressData="progressData"
          :processInstanceId="processInstanceId"
        />
      </el-tab-pane>
      <el-tab-pane v-if="hasProcessInfo" label="审批历史" name="history">
        <EntityApprovalHistory :processHistory="processHistory" />
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
      :form-actions="formActions"
      :action-loading-key="actionLoadingKey"
      :entity-status-options="entityStatusOptions"
      :runtime-context="launchRuntimeContext"
      @form-action="handleFormAction"
    />

    <template #footer>
      <FormActionBar
        :actions="footerActions"
        :loading-key="actionLoadingKey"
        @action="handleFormAction"
      />
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
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
} from '@/shared/form-runtime'
import EntityDataFormFields from './EntityDataFormFields.vue'
import EntityApprovalHistory from './approval/EntityApprovalHistory.vue'
import EntityApprovalDiagram from './approval/EntityApprovalDiagram.vue'
import FlowActionExecutionLog from '@/components/FlowActionExecutionLog.vue'
import FormActionBar from '@/components/FormActionBar.vue'
import RuntimeVersionDiagnostics from '@/components/RuntimeVersionDiagnostics.vue'
import {
  acquireFormActionExecution,
  executeCustomFormAction,
  resolveRuntimeFormActions
} from '@/shared/form-action-runtime'
import { footerFormActions } from '@/shared/form-actions'
import { isWorkflowReady } from '@/shared/entity-design'
import { formatRuntimeCodeVersion } from '@/shared/runtime-diagnostics'
import { isEmbedDelegatedRequestEnabled } from '@/shared/request'
import { createRuntimeFormDiscardGuard } from '@/shared/runtime-form-discard'

const props = withDefaults(defineProps<{
  entityCode: string
  entityDefinition: any
  entityFields: any[]
  defaultForm: any
  listKey?: string
  listReleaseId?: string
  listReleaseVersion?: number | null
  listReleaseResolutionToken?: string
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
const seamlessPresentation = computed(() => props.formPresentation === 'seamless')
const dialogTitle = ref('')
const formActions = ref<any[]>([])
const actionPendingKey = ref('')
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
const resetSnapshot = ref<any>(null)
const launchRuntimeContext = ref<Record<string, any>>({})
const activeForm = ref<any>(null)
let launchSequence = 0
const runtimeForm = computed(() => activeForm.value || props.defaultForm)
const listReleaseContext = computed(() => ({
  releaseId: props.listReleaseId || undefined,
  releaseVersion: props.listReleaseVersion ?? undefined,
  releaseResolutionToken:
    props.listReleaseResolutionToken || undefined,
  viewCompositionTraversalToken:
    launchRuntimeContext.value?.viewCompositionTraversalToken || undefined
}))
const formReleaseContext = computed(() => ({
  releaseId:
    runtimeForm.value?.runtimeReleaseId
    || runtimeForm.value?.formReleaseId
    || undefined,
  releaseVersion:
    runtimeForm.value?.runtimeReleaseVersion
    ?? runtimeForm.value?.formReleaseVersion
    ?? undefined,
  releaseResolutionToken:
    runtimeForm.value?.releaseResolutionToken || undefined,
  viewCompositionTraversalToken:
    launchRuntimeContext.value?.viewCompositionTraversalToken || undefined
}))

const formData = reactive({
  id: '',
  name: '',
  data: {} as Record<string, any>,
  startProcess: false
})

// 只保护当前有效 Embed 会话中的业务输入；会话撤销/销毁不得被确认框阻挡。
const discardGuard = createRuntimeFormDiscardGuard({
  readValue: () => ({ name: formData.name, data: formData.data }),
  enabled: () => dialogVisible.value && isEmbedDelegatedRequestEnabled(),
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

/** 供原生关闭和 Embed 刷新共同检查；提交中的表单不能被重新初始化。 */
async function confirmDiscardChanges() {
  if (props.reloadPending || (isEmbedDelegatedRequestEnabled() && actionLoadingKey.value)) return false
  return discardGuard.confirmDiscard()
}

async function beforeDialogClose(done: () => void) {
  if (await confirmDiscardChanges()) done()
}

onMounted(() => globalThis.addEventListener?.('beforeunload', discardGuard.beforeUnload))
onBeforeUnmount(() => globalThis.removeEventListener?.('beforeunload', discardGuard.beforeUnload))

const hasProcessInfo = computed(() => !!processInstanceId.value)
const canStartProcess = computed(() => !hasProcessInfo.value)
const footerActions = computed(() => footerFormActions(formActions.value))
const rootDataSourceRuntime = createFormDataSourceRuntime({
  entityCode: props.entityCode,
  getRecord: () => formData.data || {},
  getRecordId: () => formData.id,
  getListKey: () => props.listKey,
  getMode: () => isEdit.value ? 'edit' : 'create',
  getForm: () => runtimeForm.value,
  getEntityDefinition: () => props.entityDefinition
})
const dataSourceRuntime = rootDataSourceRuntime.withContext(() => ({
  ...launchRuntimeContext.value,
  context: {
    ...(launchRuntimeContext.value?.context || {}),
    ...launchRuntimeContext.value
  },
  params:
    launchRuntimeContext.value?.params
    || launchRuntimeContext.value?.parameters
    || {}
}))

const {
  bpmnXml,
  progressData,
  processHistory,
  processRuntimeMetadata,
  loadProcessDetail
} = useProcessDetail()

/**
 * 排障信息必须描述当前真正渲染的表单；编辑已有流程数据时，再补充实例钉定的流程版本。
 */
const dialogRuntimeDiagnosticEntries = computed(() => {
  const process = processRuntimeMetadata.value || {}
  const diagnosticProcessInstanceId =
    process.processInstanceId || processInstanceId.value
  const form = runtimeForm.value
  const formVersion = form?.runtimeReleaseVersion ?? form?.formReleaseVersion
  const hotfixSuffix = form?.hotfixApplied === true ? '（已应用热修复）' : ''
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
        ? `${formatRuntimeCodeVersion(form.formKey, formVersion)}${hotfixSuffix}`
        : '未记录运行表单'
    }
  ]
})
const dialogRuntimeDiagnosticCopyEntries = computed(() => [
  ...(props.listKey
    ? [{
        label: '列表',
        value: formatRuntimeCodeVersion(
          props.listKey,
          props.listReleaseVersion
        )
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
    form?.runtimeReleaseId || form?.formReleaseId || '',
    form?.runtimeReleaseVersion ?? form?.formReleaseVersion ?? ''
  ].join(':')
})

function handleDialogClosed() {
  runtimeDiagnosticsRef.value?.reset()
  processRuntimeMetadata.value = {}
  emit('closed')
}

const runtimeTabLayout = computed(() => resolveRuntimeFormTabLayout(runtimeForm.value))
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

async function loadFormActions() {
  formActions.value = await resolveRuntimeFormActions(runtimeForm.value, {
    entityCode: props.entityCode,
    listKey: props.listKey,
    mode: isEdit.value ? 'edit' : 'create',
    recordId: formData.id || undefined,
    workflowReady: isWorkflowReady(props.entityDefinition),
    hasProcessInstance: hasProcessInfo.value,
    systemEntity: props.entityDefinition?.storageMode === 'SYSTEM',
    viewCompositionTraversalToken:
      launchRuntimeContext.value?.viewCompositionTraversalToken || undefined
  })
}

async function executeFormEvent(eventCode: string) {
  if (!runtimeForm.value?.id) return
  const result = await uiEventBindingApi.execute(eventCode, {
    configType: 'FORM',
    configId: String(runtimeForm.value.id),
    releaseId:
      runtimeForm.value.runtimeReleaseId
      || runtimeForm.value.formReleaseId
      || undefined,
    releaseVersion:
      runtimeForm.value.runtimeReleaseVersion
      ?? runtimeForm.value.formReleaseVersion
      ?? undefined,
    releaseResolutionToken:
      runtimeForm.value.releaseResolutionToken
      || launchRuntimeContext.value?.releaseResolutionToken
      || undefined,
    viewCompositionTraversalToken:
      launchRuntimeContext.value?.viewCompositionTraversalToken || undefined,
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
  await applyFormEventResult(result)
  if (result?.message) {
    ElMessage.success(result.message)
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
        if (!targetPath) continue
        const value = resolvePath(effect.data || {}, targetPath)
        const formPath = targetPath.replace(/^form\./, '').replace(/^data\./, '')
        const current = resolvePath(formData.data, formPath)
        const overwrite = String(mapping?.overwrite || 'ALWAYS').toUpperCase()
        if (overwrite === 'IF_EMPTY' && !emptyValue(current)) {
          continue
        }
        if (overwrite === 'CONFIRM' && !emptyValue(current) && current !== value) {
          try {
            await ElMessageBox.confirm(
              `字段“${fieldName(formPath)}”已有值，是否覆盖？`,
              '确认回填',
              { type: 'warning' }
            )
          } catch {
            continue
          }
        }
        setPath(formData.data, formPath, value)
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
      dialogVisible.value = false
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
      formData.data[key] = value
    })
  }
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
  // 确认框本身也是异步窗口，必须先加锁再等待用户选择，避免快速双击进入
  // 两条执行链并分别生成 requestId。
  const releaseAction = acquireFormActionExecution(action, actionPendingKey)
  if (!releaseAction) return
  try {
    if (!(await confirmAction(action))) return
    if (action.key === 'close') {
      if (await confirmDiscardChanges()) dialogVisible.value = false
      return
    }
    actionLoadingKey.value = String(action.runtimeKey || action.key || '')
    if (action.key === 'reset') {
      await handleReset()
      return
    }
    if (action.key === 'save' || action.key === 'saveAndStart') {
      await handleSubmit(action.key === 'saveAndStart')
      return
    }
    if (action.type !== 'custom') return
    if (action.validateBeforeExecute && !(await validateRuntimeForms())) {
      ElMessage.warning('请先完成表单必填项')
      return
    }
    const result = await executeCustomFormAction(
      action,
      runtimeForm.value,
      {
        entityCode: props.entityCode,
        listKey: props.listKey,
        mode: isEdit.value ? 'edit' : 'create',
        recordId: formData.id || undefined,
        formData: formData.data,
        processInstanceId: processInstanceId.value || undefined,
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
    releaseAction()
  }
}

function resolvePath(source: any, path: string) {
  return String(path || '')
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

async function handleReset() {
  restoreResetSnapshot()
  try {
    await executeFormEvent('FORM_RESET')
  } catch (error: any) {
    ElMessage.error(error.message || '表单重置事件执行失败')
  }
  nextTick(() => refreshFormLinkage())
}

// 新增
const openCreate = async (options: any = {}) => {
  runtimeDiagnosticsRef.value?.reset()
  processRuntimeMetadata.value = {}
  activeForm.value = options?.form || null
  isEdit.value = false
  processInstanceId.value = ''
  currentProcessStatus.value = ''
  currentProcessName.value = ''
  activeTab.value = firstFormTabName.value
  const inputParameters = {
    ...(options?.context?.parameters || {}),
    ...(options?.context?.params || {}),
    ...(options?.parameters || {})
  }
  const initialData =
    options?.initialData && typeof options.initialData === 'object'
      ? options.initialData
      : {}
  launchRuntimeContext.value = {
    ...(options?.context || {}),
    params: inputParameters,
    parameters: inputParameters,
    initialData,
    initializationKey: `create:${++launchSequence}`
  }
  resetForm()
  dialogTitle.value = runtimeForm.value?.formName
    ? `新增数据 - ${runtimeForm.value.formName}${runtimeForm.value.formKey ? `（${runtimeForm.value.formKey}）` : ''}`
    : '新增数据'

  applyCreateInitialData(initialData)

  try {
    await executeFormEvent('FORM_OPEN')
  } catch (error: any) {
    ElMessage.error(error.message || '表单打开事件执行失败')
  }
  applyCreateInitialData(initialData)
  captureResetSnapshot()
  await loadFormActions()
  dialogVisible.value = true
  await nextTick()
  refreshFormLinkage()
  await nextTick()
  // 原生控件初始化会补默认值；这些值应属于基线，不应被误认为用户输入。
  discardGuard.markSaved()
}

// 编辑
const openEdit = async (row: any, options: any = {}) => {
  runtimeDiagnosticsRef.value?.reset()
  processRuntimeMetadata.value = {}
  processInstanceId.value = ''
  currentProcessStatus.value = ''
  currentProcessName.value = ''
  activeForm.value = options?.form || null
  isEdit.value = true
  launchRuntimeContext.value = {
    ...(options?.context || {}),
    initializationKey: `edit:${row?.id || 'record'}:${++launchSequence}`
  }
  formData.startProcess = false
  const detail = await entityDataApi.getDetail(
    props.entityCode,
    row.id,
    props.listKey,
    runtimeForm.value?.id,
    listReleaseContext.value,
    formReleaseContext.value
  )
  formData.id = detail.id
  formData.name = detail.name
  formData.data = normalizeEntityRecordForForm(detail)

  processInstanceId.value = detail.processInstanceId || ''
  if (processInstanceId.value) {
    await loadProcessDetail(processInstanceId.value, {
      onLoad: (progressRes: any) => {
        currentProcessStatus.value = progressRes.status || ''
        currentProcessName.value = progressRes.processName || ''
      }
    })
  } else {
    currentProcessStatus.value = ''
    currentProcessName.value = ''
  }

  activeTab.value = firstFormTabName.value

  dialogTitle.value = '编辑数据'
  try {
    await executeFormEvent('FORM_OPEN')
  } catch (error: any) {
    ElMessage.error(error.message || '表单打开事件执行失败')
  }
  captureResetSnapshot()
  await loadFormActions()
  dialogVisible.value = true
  await nextTick()
  refreshFormLinkage()
  await nextTick()
  discardGuard.markSaved()
}

async function validateRuntimeForms() {
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

  for (const formRef of formRefs) {
    if ((await formRef.instance.validate()) === false) {
      // 唯一性或普通字段错误可能位于未激活页签，切过去才能让用户看到就地提示。
      if (formRef.tabName) activeTab.value = formRef.tabName
      return false
    }
  }

  await dataSourceRuntime.prevalidateBeforeSubmit({
    form: runtimeForm.value,
    fields: runtimeForm.value?.fields || [],
    nodes: runtimeForm.value?.nodes || []
  })
  return true
}

// 提交
const handleSubmit = async (startProcess = false) => {
  const valid = await validateRuntimeForms()
  if (!valid) return

  formData.startProcess = startProcess
  try {
    const submittedData = filterRuntimeFormSubmissionData(
      formData.data,
      runtimeForm.value,
      props.entityFields
    )
    const data = {
      entityCode: props.entityCode,
      listKey: props.listKey,
      formId: runtimeForm.value?.id,
      formReleaseId: formReleaseContext.value.releaseId,
      formReleaseVersion: formReleaseContext.value.releaseVersion,
      formReleaseResolutionToken:
        formReleaseContext.value.releaseResolutionToken,
      viewCompositionActionContextToken:
        launchRuntimeContext.value?.viewCompositionActionContextToken
        || undefined,
      viewCompositionTraversalToken:
        launchRuntimeContext.value?.viewCompositionTraversalToken
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
        actionKey: formData.startProcess ? 'saveAndStart' : 'save',
        data: submittedData,
        entityCode: props.entityCode,
        formId: runtimeForm.value?.id,
        formReleaseId: formReleaseContext.value.releaseId,
        formReleaseVersion: formReleaseContext.value.releaseVersion,
        formReleaseResolutionToken:
          formReleaseContext.value.releaseResolutionToken
      })
    } else if (formData.id) {
      result = await entityDataApi.update(
        props.entityCode,
        formData.id,
        {
          data: submittedData,
          formId: runtimeForm.value?.id,
          formReleaseId: formReleaseContext.value.releaseId,
          formReleaseVersion: formReleaseContext.value.releaseVersion,
          formReleaseResolutionToken:
            formReleaseContext.value.releaseResolutionToken,
          viewCompositionActionContextToken:
            launchRuntimeContext.value?.viewCompositionActionContextToken
            || undefined,
          viewCompositionTraversalToken:
            launchRuntimeContext.value?.viewCompositionTraversalToken
            || undefined,
          startProcess: formData.startProcess
        },
        formData.startProcess,
        props.listKey,
        listReleaseContext.value
      )
      ElMessage.success('更新成功')
    } else {
      result = await entityDataApi.save(
        data,
        data.startProcess,
        listReleaseContext.value
      )
      ElMessage.success('创建成功')
    }

    dialogVisible.value = false
    emit('success', result)
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
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
