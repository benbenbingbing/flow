<template>
  <el-dialog v-model="processDialogVisible" :title="`${currentTask?.name || '任务审批'}${currentTask?.processStatus ? '（' + getProcessStatusText(currentTask?.processStatus) + '）' : ''}`" width="75%" class="entity-form-dialog" top="3vh">
    <div class="approval-dialog-body">
      <el-tabs v-model="activeDialogTab" type="border-card" class="approval-tabs" :class="{'has-approval-panel': !isViewMode && effectiveApprovalConfig.enabled !== false}">
        <!-- 无 tab 子表单时：基本信息 tab -->
        <el-tab-pane v-if="!approvalHasTabSubForms" label="基本信息" name="approval">
          <EntityApprovalBasicInfo
            v-model:entityData="entityData"
            :approvalNormalForm="approvalNormalForm"
          />
        </el-tab-pane>

        <!-- 有 tab 子表单时：基本信息 tab（普通字段） -->
        <el-tab-pane v-if="approvalHasTabSubForms" label="基本信息" name="basic">
          <EntityApprovalBasicInfo
            v-model:entityData="entityData"
            :approvalNormalForm="approvalNormalForm"
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
            :disabled="true"
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
      </el-tabs>

      <!-- 审批意见面板：悬浮在弹窗底部，标题在上，叠加在底部按钮上方 -->
      <div v-if="!isViewMode && effectiveApprovalConfig.enabled !== false" class="approval-panel" :class="{'is-expanded': approvalExpanded}">
        <div v-show="approvalExpanded" class="approval-panel-content">
          <el-form :model="approveForm" label-width="80px">
            <el-form-item label="审批操作" required>
              <el-radio-group v-model="approveForm.action">
                <el-radio-button
                  v-for="option in effectiveApprovalConfig.options"
                  :key="option.value"
                  :label="option.value"
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
        <div class="approval-panel-header" @click="approvalExpanded = !approvalExpanded">
          <span class="approval-panel-title">审批意见</span>
          <el-icon>
            <ArrowUp v-if="!approvalExpanded" />
            <ArrowDown v-else />
          </el-icon>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="processDialogVisible = false">关闭</el-button>
      <el-button v-if="!isViewMode" type="primary" @click="submitApprove" :loading="approveSubmitLoading">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import { entityDataApi } from '@/api/entity'
import { completeTask } from '@/api/processTask'
import FormFieldRendererLinkage from '@/components/FormFieldRendererLinkage.vue'
import { getFieldKey } from '@/shared/form-runtime'
import { useProcessDetail } from '@/composables/useProcessDetail'
import EntityApprovalBasicInfo from './EntityApprovalBasicInfo.vue'
import EntityApprovalHistory from './EntityApprovalHistory.vue'
import EntityApprovalDiagram from './EntityApprovalDiagram.vue'

const props = defineProps<{
  entityCode: string
  defaultForm: any
}>()

const emit = defineEmits<{
  success: []
}>()

const processDialogVisible = ref(false)
const activeDialogTab = ref('approval')
const approveSubmitLoading = ref(false)
const currentTask = ref<any>(null)
const isViewMode = ref(false)
// 审批意见面板默认展开，支持收起以腾出空间查看表单
const approvalExpanded = ref(true)

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

// 审批弹窗中是否有 Tab 子表单
const approvalHasTabSubForms = computed(() => {
  const fields = formConfig.value?.fields || []
  return fields.some((f: any) => isTabSubForm(f))
})

// 审批弹窗中的 Tab 子表单字段
const approvalTabSubForms = computed(() => {
  const fields = formConfig.value?.fields || []
  return fields.filter((f: any) => isTabSubForm(f))
})

// 审批弹窗中普通字段组成的 form（给 FormPreviewLinkage 用，不含 tab 子表单）
const approvalNormalForm = computed(() => {
  const fields = (formConfig.value?.fields || []).filter((f: any) => !isTabSubForm(f))
  return {
    ...formConfig.value,
    fields
  }
})

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
    taskId: row.currentTaskId,
    processInstanceId: row.processInstanceId,
    name: row.currentTaskName || '任务审批'
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

// 打开查看弹窗（只读模式）
const openView = async (row: any) => {
  isViewMode.value = true
  currentTask.value = {
    processInstanceId: row.processInstanceId,
    name: row.name || '数据详情'
  }
  activeDialogTab.value = 'approval'
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
        activeDialogTab.value = hasTabs ? 'basic' : 'approval'
      }
    })
  } else {
    try {
      const detail = await entityDataApi.getDetail(props.entityCode, row.id)
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
  if (!currentTask.value?.taskId) return
  approveSubmitLoading.value = true
  try {
    await completeTask({
      taskId: currentTask.value.taskId,
      action: approveForm.action,
      comment: approveForm.comment
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
  margin-top: 15px !important;
  margin-bottom: 15px !important;
  height: 94vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.entity-form-dialog :deep(.el-dialog__body) {
  flex: 1;
  overflow: hidden;
  min-height: 0;
  padding: 0;
}

.approval-dialog-body {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.approval-tabs {
  height: 100%;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.approval-tabs.has-approval-panel {
  /* 审批意见面板悬浮在 tabs 区域位置 */
}

.approval-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

/* 审批意见面板：悬浮在弹窗 body 底部，颇叠加在底部按钮上方 */
.approval-panel {
  position: absolute;
  bottom: 0;
  left: 1px;
  right: 1px;
  z-index: 10;
  background: #fff;
  border: 1px solid #e4e7ed;
  box-shadow: 0 -4px 12px rgba(0, 0, 0, 0.1);
  border-radius: 4px;
  display: flex;
  flex-direction: column;
}

.approval-panel-header {
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  cursor: pointer;
  flex-shrink: 0;
  border-top: 1px solid #ebeef5;
}

.approval-panel-title {
  position: relative;
  padding-left: 14px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  line-height: 1;
}

/* 左侧蓝色圆角竖线，与测试节点标题（SectionField）保持一致 */
.approval-panel-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 18px;
  background-color: #409eff;
  border-radius: 2px;
}

.approval-panel-content {
  max-height: 35vh;
  overflow-y: auto;
  padding: 16px;
  flex-shrink: 0;
}

.approval-panel.is-expanded {
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
}
</style>
