<template>
  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="75%" class="entity-form-dialog" top="3vh">
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
      <el-tab-pane v-if="hasProcessInfo" label="流程图" name="diagram">
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
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { entityDataApi } from '@/api/entity'
import { uiEventBindingApi } from '@/api/uiConfig'
import { useUserStore } from '@/stores/user'
import { executeFormInitializer } from '@/utils/formInitializer'
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
import {
  executeCustomFormAction,
  resolveRuntimeFormActions
} from '@/shared/form-action-runtime'
import { footerFormActions } from '@/shared/form-actions'
import { isWorkflowReady } from '@/shared/entity-design'

const props = defineProps<{
  entityCode: string
  entityDefinition: any
  entityFields: any[]
  defaultForm: any
  listKey?: string
  entityStatusOptions?: any[]
}>()

const emit = defineEmits<{
  success: []
}>()

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formActions = ref<any[]>([])
const actionLoadingKey = ref('')
const formFieldsRef = ref<InstanceType<typeof EntityDataFormFields>>()
const basicFormFieldsRef = ref<InstanceType<typeof EntityDataFormFields>>()
const nodeFormFieldsRefs = ref<Record<string, InstanceType<typeof EntityDataFormFields>>>({})
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

const formData = reactive({
  id: '',
  name: '',
  data: {} as Record<string, any>,
  startProcess: false
})

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
  loadProcessDetail
} = useProcessDetail()

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

// 切换到流程图 tab 时重新触发 BPMN 渲染，避免隐藏 tab 中画布尺寸为 0
watch(activeTab, (newVal) => {
  if (newVal === 'diagram' && bpmnXml.value && progressData.value) {
    nextTick(() => {
      const tempXml = bpmnXml.value
      bpmnXml.value = ''
      nextTick(() => {
        bpmnXml.value = tempXml
      })
    })
  }
})

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
    systemEntity: props.entityDefinition?.storageMode === 'SYSTEM'
  })
}

async function executeFormEvent(eventCode: string) {
  if (!runtimeForm.value?.id) return
  const result = await uiEventBindingApi.execute(eventCode, {
    configType: 'FORM',
    configId: String(runtimeForm.value.id),
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
  if (!action || action.enabled === false || actionLoadingKey.value) return
  if (!(await confirmAction(action))) return
  const loadingKey = String(action.runtimeKey || action.key || '')
  actionLoadingKey.value = loadingKey
  try {
    if (action.key === 'close') {
      dialogVisible.value = false
      return
    }
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
        processInstanceId: processInstanceId.value || undefined
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

  if (runtimeForm.value?.initConfig) {
    try {
      const initData = await executeFormInitializer(runtimeForm.value.initConfig, {
        entityCode: props.entityCode,
        entityDefinition: props.entityDefinition,
        routeQuery: route.query,
        userStore: userStore,
        params: inputParameters,
        parent: launchRuntimeContext.value?.parent || {},
        context: launchRuntimeContext.value
      })
      if (initData && typeof initData === 'object') {
        Object.entries(initData).forEach(([key, value]) => {
          formData.data[key] = value
        })
      }
    } catch (e) {
      console.warn('表单初始化失败:', e)
    }
  }

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
  nextTick(() => {
    refreshFormLinkage()
  })
}

// 编辑
const openEdit = async (row: any, options: any = {}) => {
  activeForm.value = options?.form || null
  isEdit.value = true
  launchRuntimeContext.value = {
    initializationKey: `edit:${row?.id || 'record'}:${++launchSequence}`
  }
  formData.startProcess = false
  const detail = await entityDataApi.getDetail(
    props.entityCode,
    row.id,
    props.listKey,
    runtimeForm.value?.id
  ).catch(() => row)
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
  nextTick(() => {
    refreshFormLinkage()
  })
}

async function validateRuntimeForms() {
  const formRefs: Array<InstanceType<typeof EntityDataFormFields>> = []
  if (!showOuterTabs.value) {
    if (formFieldsRef.value) formRefs.push(formFieldsRef.value)
  } else {
    if (showBasicTab.value && basicFormFieldsRef.value) {
      formRefs.push(basicFormFieldsRef.value)
    }
    runtimeNodeTabs.value.forEach(tab => {
      const formRef = nodeFormFieldsRefs.value[tab.name]
      if (formRef) formRefs.push(formRef)
    })
  }

  for (const formRef of formRefs) {
    if ((await formRef.validate()) === false) return false
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
      id: formData.id,
      name: submittedData?.name || formData.name,
      data: submittedData,
      startProcess: formData.startProcess
    }

    if (formData.id) {
      await entityDataApi.update(
        props.entityCode,
        formData.id,
        {
          data: submittedData,
          formId: runtimeForm.value?.id,
          startProcess: formData.startProcess
        },
        formData.startProcess,
        props.listKey
      )
      ElMessage.success('更新成功')
    } else {
      await entityDataApi.save(data, data.startProcess)
      ElMessage.success('创建成功')
    }

    dialogVisible.value = false
    emit('success')
  } catch (error: any) {
    ElMessage.error(error.message || '操作失败')
  }
}

defineExpose({
  openCreate,
  openEdit
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
</style>
