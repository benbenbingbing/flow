<template>
  <el-dialog v-model="processDialogVisible" :title="`${currentTask?.name || '任务审批'}${currentTask?.processStatus ? '（' + getProcessStatusText(currentTask?.processStatus) + '）' : ''}`" width="75%" class="entity-form-dialog entity-approval-dialog" top="3vh">
    <div class="approval-dialog-body">
      <el-tabs v-model="activeDialogTab" type="border-card" class="approval-tabs">
        <el-tab-pane v-if="approvalShowBasicTab" label="基本信息" name="basic">
          <EntityApprovalBasicInfo
            ref="basicInfoRef"
            v-model:entityData="entityData"
            :approvalNormalForm="approvalNormalForm"
            :formReadonly="approvalFormReadonly"
            :mode="approvalRuntimeMode"
            :entityCode="entityCode"
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
            :entityCode="entityCode"
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

        <!-- 流程图（仅在有流程实例时显示）-->
        <el-tab-pane v-if="currentTask?.processInstanceId" label="流程图" name="diagram">
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

      <div
        v-if="!isViewMode && effectiveApprovalConfig.enabled !== false && isApprovalFormTab"
        class="approval-opinion-section"
      >
        <div class="section-title">审批意见</div>
        <el-form :model="approveForm" label-width="80px">
          <el-form-item label="审批操作" required>
            <el-radio-group v-model="approveForm.action">
              <el-radio-button
                v-for="option in effectiveApprovalConfig.options"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item
            v-if="effectiveApprovalConfig.options.find(o => o.value === approveForm.action)?.showComment !== false"
            :label="effectiveApprovalConfig.commentLabel || '审批备注'"
          >
            <el-input
              v-model="approveForm.comment"
              type="textarea"
              :rows="3"
              :placeholder="`请输入${effectiveApprovalConfig.commentLabel || '审批备注'}`"
            />
          </el-form-item>
        </el-form>
      </div>
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
import { useUserStore } from '@/stores/user'
import EntityApprovalBasicInfo from './EntityApprovalBasicInfo.vue'
import EntityApprovalHistory from './EntityApprovalHistory.vue'
import EntityApprovalDiagram from './EntityApprovalDiagram.vue'
import FlowActionExecutionLog from '@/components/FlowActionExecutionLog.vue'
import { resolveApprovalFormConfig } from './entityApprovalDisplay.js'
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

const props = withDefaults(defineProps<{
  entityCode?: string
  defaultForm?: any
  entityDefinition?: any
  entityFields?: any[]
  listKey?: string
  entityStatusOptions?: any[]
}>(), {
  entityCode: '',
  defaultForm: null,
  entityDefinition: () => ({}),
  entityFields: () => [],
  listKey: '',
  entityStatusOptions: () => []
})

const emit = defineEmits<{
  success: []
}>()
const userStore = useUserStore()
const router = useRouter()

const processDialogVisible = ref(false)
const activeDialogTab = ref('basic')
const approveSubmitLoading = ref(false)
const formActions = ref<any[]>([])
const actionLoadingKey = ref('')
const currentTask = ref<any>(null)
const isViewMode = ref(false)
const overrideForm = ref<any>(null)
const basicInfoRef = ref<any>()
const nodeTabRefs = ref<Record<string, any>>({})

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
  getProcessStatusText,
  loadProcessDetail
} = useProcessDetail()

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
const approvalRuntimeContext = computed(() => ({
  entityCode: props.entityCode,
  mode: approvalRuntimeMode.value,
  record: entityData.value,
  task: currentTask.value,
  processInstanceId: currentTask.value?.processInstanceId,
  entityStatusMap: buildEntityStatusMap(props.entityStatusOptions),
  entityStatusOptions: props.entityStatusOptions,
  releaseResolutionToken: effectiveFormConfig.value?.releaseResolutionToken
}))
const dataSourceRuntime = createFormDataSourceRuntime({
  entityCode: props.entityCode,
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
    entityCode: props.entityCode,
    listKey: props.listKey,
    mode: approvalRuntimeMode.value,
    recordId: entityData.value?.id || undefined,
    taskId: currentTask.value?.taskId || undefined,
    workflowReady: isWorkflowReady(props.entityDefinition),
    hasProcessInstance: Boolean(currentTask.value?.processInstanceId),
    canApprove: !isViewMode.value && Boolean(currentTask.value?.taskId),
    systemEntity: props.entityDefinition?.storageMode === 'SYSTEM'
  })
}

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

// 监听审批弹窗 Tab 切换，切换到流程图时重新触发渲染
watch(activeDialogTab, (newVal) => {
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

// 打开审批弹窗
interface OpenApproveOptions {
  form?: any
}

const openApprove = async (
  row: any,
  options: OpenApproveOptions = {}
) => {
  overrideForm.value = options.form || null
  isViewMode.value = false
  currentTask.value = {
    taskId: row.currentTaskId || row.taskId,
    processInstanceId: row.processInstanceId,
    name: row.currentTaskName || row.name || '任务审批',
    startUserName: row.startUserName,
    processName: row.processName
  }
  approveForm.action = 'approve'
  approveForm.comment = ''
  activeDialogTab.value = 'basic'
  const loaded = await loadProcessDetail(row.processInstanceId, {
    startUserName: currentTask.value?.startUserName,
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
}

interface OpenViewOptions {
  defaultTab?: string
  startUserName?: string
  form?: any
}

// 打开查看弹窗（只读模式）
const openView = async (row: any, options: OpenViewOptions = {}) => {
  const { defaultTab, startUserName, form } = options
  overrideForm.value = form || null
  isViewMode.value = true
  currentTask.value = {
    processInstanceId: row.processInstanceId,
    name: row.name || row.currentTaskName || '数据详情',
    startUserName: startUserName || row.startUserName,
    processName: row.processName
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
      return
    }
    await reloadExplicitFormDetail(row)
  } else {
    try {
      const detail = await entityDataApi.getDetail(
        props.entityCode,
        row.id,
        props.listKey,
        overrideForm.value?.id
      )
      entityData.value = normalizeEntityRecordForForm(detail)
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
    } catch (e) {
      console.error('加载数据详情失败:', e)
      ElMessage.error('加载详情失败')
    }
  }
  await loadFormActions()
  processDialogVisible.value = true
}

async function reloadExplicitFormDetail(row: any) {
  if (!overrideForm.value?.id || !row?.id) return
  try {
    const detail = await entityDataApi.getDetail(
      props.entityCode,
      row.id,
      props.listKey,
      overrideForm.value.id
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

async function validateApprovalForms() {
  const refs = [
    ...(approvalShowBasicTab.value && basicInfoRef.value ? [basicInfoRef.value] : []),
    ...approvalNodeTabs.value
      .map(tab => nodeTabRefs.value[tab.name])
      .filter(Boolean)
  ]
  for (const formRef of refs) {
    if ((await formRef.validate?.()) === false) return false
  }
  return true
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
    if (action.validateBeforeExecute && !(await validateApprovalForms())) {
      ElMessage.warning('请先完成表单必填项')
      return
    }
    const result = await executeCustomFormAction(
      action,
      runtimeForms.value,
      {
        entityCode: props.entityCode,
        listKey: props.listKey,
        mode: approvalRuntimeMode.value,
        recordId: entityData.value?.id || undefined,
        taskId: currentTask.value?.taskId || undefined,
        task: currentTask.value,
        formData: entityData.value,
        processInstanceId: currentTask.value?.processInstanceId
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

// 提交审批
const submitApprove = async () => {
  if (!currentTask.value?.taskId || approveSubmitLoading.value) return
  approveSubmitLoading.value = true
  try {
    const valid = await validateApprovalForms()
    if (valid === false) {
      ElMessage.warning('请先完成表单必填项')
      return
    }
    await dataSourceRuntime.prevalidateBeforeSubmit({
      form: approvalNormalForm.value,
      fields: approvalNormalForm.value?.fields || [],
      nodes: approvalNormalForm.value?.nodes || []
    })
    const selectedOption = effectiveApprovalConfig.value.options?.find(
      (o: any) => o.value === approveForm.action
    )
    await completeTask({
      taskId: currentTask.value.taskId,
      action: approveForm.action,
      actionLabel: selectedOption?.label,
      comment: approveForm.comment,
      formData: entityData.value
    })
    ElMessage.success('审批成功')
    processDialogVisible.value = false
    emit('success')
  } catch (e) {
    console.error('审批失败:', e)
    ElMessage.error('审批失败')
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

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-left: 8px;
  border-left: 4px solid #409eff;
}

.approval-opinion-section {
  flex: 0 0 auto;
  max-height: min(280px, 34dvh);
  overflow-y: auto;
  background: #ffffff;
  padding: 12px 0 4px;
  border-top: 1px solid #e4e7ed;
}

.approval-opinion-section :deep(.el-form-item) {
  margin-bottom: 12px;
}

.approval-opinion-section :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}

.approval-dialog-footer {
  min-height: 32px;
}

@media (max-width: 900px) {
  :global(.el-dialog.entity-approval-dialog) {
    --approval-dialog-margin: 12px;
    width: calc(100vw - 24px) !important;
  }
}

@media (max-height: 760px) {
  .approval-opinion-section {
    max-height: 30dvh;
  }
}
</style>
