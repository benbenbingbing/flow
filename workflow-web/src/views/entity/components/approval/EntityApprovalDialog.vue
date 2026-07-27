<template>
  <el-dialog v-model="processDialogVisible" :title="`${currentTask?.name || '任务审批'}${currentTask?.processStatus ? '（' + getProcessStatusText(currentTask?.processStatus) + '）' : ''}`" width="75%" class="entity-form-dialog" top="3vh">
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
            :context="approvalRuntimeContext"
            :dataSourceRuntime="dataSourceRuntime"
            :excludedNodeIds="approvalLiftedRootNodeIds"
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
            :context="approvalRuntimeContext"
            :dataSourceRuntime="dataSourceRuntime"
            :nodeRootParentId="tab.rootParentId"
          />
        </el-tab-pane>

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

      <div
        v-if="!isViewMode && effectiveApprovalConfig.enabled !== false && isApprovalFormTab"
        class="approval-opinion-section"
      >
        <el-divider />
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
      <el-button @click="processDialogVisible = false">关闭</el-button>
      <el-button v-if="!isViewMode && isApprovalFormTab" type="primary" @click="submitApprove" :loading="approveSubmitLoading">确认</el-button>
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
const activeDialogTab = ref('basic')
const approveSubmitLoading = ref(false)
const currentTask = ref<any>(null)
const isViewMode = ref(false)
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
  processInstanceId: currentTask.value?.processInstanceId,
  releaseResolutionToken: formConfig.value?.releaseResolutionToken
}))
const dataSourceRuntime = createFormDataSourceRuntime({
  entityCode: props.entityCode,
  getRecord: () => entityData.value || {},
  getRecordId: () => entityData.value?.id,
  getListKey: () => props.listKey,
  getMode: () => approvalRuntimeMode.value,
  getForm: () => approvalNormalForm.value
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
const approvalTabLayout = computed(() =>
  resolveRuntimeFormTabLayout(approvalNormalForm.value)
)
const approvalNodeTabs = computed(() => approvalTabLayout.value.tabs)
const approvalLiftedRootNodeIds = computed(() =>
  approvalTabLayout.value.liftedRootNodeIds
)
const approvalHasFormTabs = computed(() =>
  approvalNodeTabs.value.length > 0 || approvalTabSubForms.value.length > 0
)
const approvalShowBasicTab = computed(() =>
  approvalTabLayout.value.hasBaseContent || !approvalHasFormTabs.value
)
const approvalFormTabNames = computed(() => [
  ...(approvalShowBasicTab.value ? ['basic'] : []),
  ...approvalNodeTabs.value.map(tab => tab.name),
  ...approvalTabSubForms.value.map((_, idx) => `subform_${idx}`)
])
const firstApprovalFormTabName = computed(() =>
  approvalFormTabNames.value[0] || 'basic'
)
const isApprovalFormTab = computed(() =>
  approvalFormTabNames.value.includes(activeDialogTab.value)
)

function setNodeTabRef(tabName: string, instance: any) {
  if (instance) {
    nodeTabRefs.value[tabName] = instance
  } else {
    delete nodeTabRefs.value[tabName]
  }
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
  } else {
    try {
      const detail = await entityDataApi.getDetail(props.entityCode, row.id, props.listKey)
      entityData.value = normalizeEntityRecordForForm(detail)
      if (props.defaultForm && props.defaultForm.fields && props.defaultForm.fields.length > 0) {
        formConfig.value = props.defaultForm
        formConfigs.value = [props.defaultForm]
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
  processDialogVisible.value = true
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

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 16px;
  padding-left: 8px;
  border-left: 4px solid #409eff;
}

.approval-opinion-section {
  flex-shrink: 0;
  background: #ffffff;
  padding: 0 0 8px;
  border-top: 1px solid #e4e7ed;
}
</style>
