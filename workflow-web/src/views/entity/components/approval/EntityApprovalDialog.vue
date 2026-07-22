<template>
  <el-dialog v-model="processDialogVisible" :title="`${currentTask?.name || '任务审批'}${currentTask?.processStatus ? '（' + getProcessStatusText(currentTask?.processStatus) + '）' : ''}`" width="75%" class="entity-form-dialog" top="3vh">
    <div class="approval-dialog-body">
      <el-tabs v-model="activeDialogTab" type="border-card" class="approval-tabs">
        <!-- 无 tab 子表单时：基本信息 tab -->
        <el-tab-pane v-if="!approvalHasTabSubForms" label="基本信息" name="approval">
          <EntityApprovalBasicInfo
            ref="basicInfoRef"
            v-model:entityData="entityData"
            :approvalNormalForm="approvalNormalForm"
            :effectiveApprovalConfig="effectiveApprovalConfig"
            :isViewMode="isViewMode"
            :formReadonly="approvalFormReadonly"
            :mode="approvalRuntimeMode"
            :approveForm="approveForm"
            :entityCode="entityCode"
            :context="approvalRuntimeContext"
            :dataSourceRuntime="dataSourceRuntime"
          />
        </el-tab-pane>

        <!-- 有 tab 子表单时：基本信息 tab（普通字段） -->
        <el-tab-pane v-if="approvalHasTabSubForms" label="基本信息" name="basic">
          <EntityApprovalBasicInfo
            ref="basicInfoRef"
            v-model:entityData="entityData"
            :approvalNormalForm="approvalNormalForm"
            :effectiveApprovalConfig="effectiveApprovalConfig"
            :isViewMode="isViewMode"
            :formReadonly="approvalFormReadonly"
            :mode="approvalRuntimeMode"
            :approveForm="approveForm"
            :entityCode="entityCode"
            :context="approvalRuntimeContext"
            :dataSourceRuntime="dataSourceRuntime"
          />
        </el-tab-pane>

        <!-- 子表单 tabs（有 tab 子表单时） -->
        <el-tab-pane
          v-for="(field, idx) in approvalTabSubForms"
          :key="'approval-subform-' + idx"
          :label="field.fieldName"
          :name="'subform_' + idx"
        >
          <FormFieldRendererLinkage
            :field="field"
            v-model="entityData[getFieldKey(field)]"
            :disabled="isRuntimeFieldReadonly(field, approvalFormReadonly, approvalRuntimeMode)"
            :context="approvalRuntimeContext"
            :data-source-runtime="dataSourceRuntime"
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
    </div>

    <template #footer>
      <el-button @click="processDialogVisible = false">关闭</el-button>
      <el-button v-if="!isViewMode && (activeDialogTab === 'approval' || activeDialogTab === 'basic')" type="primary" @click="submitApprove" :loading="approveSubmitLoading">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { entityDataApi } from '@/api/entity'
import { completeTask } from '@/api/processTask'
import FormFieldRendererLinkage from '@/components/FormFieldRendererLinkage.vue'
import {
  getFieldKey,
  isRuntimeFieldReadonly,
  isRuntimeFieldVisible,
  isRuntimeFormReadonly,
  createFormDataSourceRuntime
} from '@/shared/form-runtime'
import { useProcessDetail } from '@/composables/useProcessDetail'
import { useUserStore } from '@/stores/user'
import EntityApprovalBasicInfo from './EntityApprovalBasicInfo.vue'
import EntityApprovalHistory from './EntityApprovalHistory.vue'
import EntityApprovalDiagram from './EntityApprovalDiagram.vue'
import FlowActionExecutionLog from '@/components/FlowActionExecutionLog.vue'

const props = withDefaults(defineProps<{
  entityCode?: string
  defaultForm?: any
  listKey?: string
}>(), {
  entityCode: '',
  defaultForm: null,
  listKey: ''
})

const emit = defineEmits<{
  success: []
}>()
const userStore = useUserStore()

const processDialogVisible = ref(false)
const activeDialogTab = ref('approval')
const approveSubmitLoading = ref(false)
const currentTask = ref<any>(null)
const isViewMode = ref(false)
const basicInfoRef = ref<any>()

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
  isTabSubForm,
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

const approvalFormReadonly = computed(() => {
  return isViewMode.value || isRuntimeFormReadonly(formConfig.value)
})

const approvalRuntimeMode = computed(() => isViewMode.value ? 'view' : 'approve')
const approvalRuntimeContext = computed(() => ({
  entityCode: props.entityCode,
  mode: approvalRuntimeMode.value,
  record: entityData.value,
  task: currentTask.value,
  processInstanceId: currentTask.value?.processInstanceId
}))
const dataSourceRuntime = createFormDataSourceRuntime({
  entityCode: props.entityCode,
  getRecord: () => entityData.value || {},
  getRecordId: () => entityData.value?.id,
  getListKey: () => props.listKey,
  getMode: () => approvalRuntimeMode.value,
  getForm: () => approvalNormalForm.value
})

// 审批弹窗中是否有 Tab 子表单
const approvalHasTabSubForms = computed(() => {
  const fields = formConfig.value?.fields || []
  return fields.some((f: any) => isRuntimeFieldVisible(f, approvalRuntimeMode.value) && isTabSubForm(f))
})

// 审批弹窗中的 Tab 子表单字段
const approvalTabSubForms = computed(() => {
  const fields = formConfig.value?.fields || []
  return fields.filter((f: any) => isRuntimeFieldVisible(f, approvalRuntimeMode.value) && isTabSubForm(f))
})

// 审批弹窗中普通字段组成的 form（给 FormPreviewLinkage 用，不含 tab 子表单）
const approvalNormalForm = computed(() => {
  const fields = (formConfig.value?.fields || [])
    .filter((f: any) => isRuntimeFieldVisible(f, approvalRuntimeMode.value))
    .filter((f: any) => !isTabSubForm(f))
  return {
    ...formConfig.value,
    fields
  }
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
const openApprove = async (row: any) => {
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
  activeDialogTab.value = 'approval'
  await loadProcessDetail(row.processInstanceId, {
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
      const hasTabs = (formConfig.value?.fields || []).some((f: any) => isTabSubForm(f))
      activeDialogTab.value = hasTabs ? 'basic' : 'approval'
    }
  })
  processDialogVisible.value = true
}

interface OpenViewOptions {
  defaultTab?: string
  startUserName?: string
}

// 打开查看弹窗（只读模式）
const openView = async (row: any, options: OpenViewOptions = {}) => {
  const { defaultTab, startUserName } = options
  isViewMode.value = true
  currentTask.value = {
    processInstanceId: row.processInstanceId,
    name: row.name || row.currentTaskName || '数据详情',
    startUserName: startUserName || row.startUserName,
    processName: row.processName
  }
  activeDialogTab.value = defaultTab || 'approval'
  if (row.processInstanceId) {
    await loadProcessDetail(row.processInstanceId, {
      startUserName: currentTask.value?.startUserName,
      onLoad: (progressRes: any) => {
        if (currentTask.value) {
          currentTask.value.processStatus = progressRes.status
          if (progressRes.processName) {
            currentTask.value.processName = progressRes.processName
          }
        }
        const hasTabs = (formConfig.value?.fields || []).some((f: any) => isTabSubForm(f))
        if (!defaultTab) {
          activeDialogTab.value = hasTabs ? 'basic' : 'approval'
        }
      }
    })
  } else {
    try {
      const detail = await entityDataApi.getDetail(props.entityCode, row.id, props.listKey)
      entityData.value = {
        ...(detail.data || {}),
        name: detail.name,
        status: detail.status,
        code: detail.code,
        dataNo: detail.dataNo,
        title: detail.title,
        deptId: detail.deptId,
        submitterId: detail.submitterId,
        submitterName: detail.submitterName,
        processInstanceId: detail.processInstanceId,
        currentTaskId: detail.currentTaskId,
        currentTaskName: detail.currentTaskName,
        currentTaskAssignee: detail.currentTaskAssignee
      }
      if (props.defaultForm && props.defaultForm.fields && props.defaultForm.fields.length > 0) {
        formConfig.value = props.defaultForm
        formConfigs.value = [props.defaultForm]
        const hasTabs = props.defaultForm.fields.some((f: any) => isTabSubForm(f))
        activeDialogTab.value = hasTabs ? 'basic' : 'approval'
      } else {
        formConfig.value = null
        formConfigs.value = []
        activeDialogTab.value = 'approval'
      }
    } catch (e) {
      console.error('加载数据详情失败:', e)
      ElMessage.error('加载详情失败')
    }
  }
  processDialogVisible.value = true
}

// 提交审批
const submitApprove = async () => {
  if (!currentTask.value?.taskId || approveSubmitLoading.value) return
  approveSubmitLoading.value = true
  try {
    const valid = await basicInfoRef.value?.validate?.()
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
.entity-form-dialog {
  --dialog-margin: 24px;
  box-sizing: border-box;
  margin-top: var(--dialog-margin) !important;
  margin-bottom: var(--dialog-margin) !important;
  height: calc(100vh - var(--dialog-margin) * 2);
  max-height: calc(100vh - var(--dialog-margin) * 2);
  display: flex;
  flex-direction: column;
}
.entity-form-dialog :deep(.el-dialog__body) {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.approval-dialog-body {
  display: flex;
  flex-direction: column;
  height: 100%;
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
</style>
