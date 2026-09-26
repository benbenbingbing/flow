<template>
  <div class="process-progress">
    <div class="progress-header">
      <div class="header-left">
        <el-button @click="$router.back()">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <RuntimeVersionDiagnostics
          class="process-runtime-diagnostics"
          :entries="processRuntimeDiagnosticEntries"
          :copy-entries="processRuntimeDiagnosticCopyEntries"
          copy-title="流程运行版本排障信息"
          :reset-key="progressData.processInstanceId || processInstanceId"
        >
          <span class="process-name">{{ progressData.processName || '流程进度' }}</span>
        </RuntimeVersionDiagnostics>
        <el-tag :type="getStatusType(progressData.status)" size="small">
          {{ getStatusText(progressData.status) }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-button v-if="userStore.isSuperAdmin" @click="actionExecutionVisible = true">动作执行记录</el-button>
        <div class="legend">
          <div class="legend-item">
            <span class="legend-color completed"></span>
            <span>已完成</span>
          </div>
          <div class="legend-item">
            <span class="legend-color active"></span>
            <span>进行中</span>
          </div>
          <div class="legend-item">
            <span class="legend-color pending"></span>
            <span>未开始</span>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="actionExecutionVisible" title="流程动作执行记录" width="1100px">
      <FlowActionExecutionLog
        :process-instance-id="processInstanceId"
        :active="actionExecutionVisible"
      />
    </el-dialog>
    
    <div class="progress-container">
      <div class="canvas-wrapper">
        <VueBpmnViewer ref="viewerRef" :xml="bpmnXml" :progress-data="progressData" navigated class="canvas" />
      </div>
      
      <!-- 右侧任务信息面板 -->
      <div class="info-panel">
        <div class="panel-title">流程信息</div>
        <div class="panel-content">
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="流程实例">{{ progressData.processInstanceId }}</el-descriptions-item>
            <el-descriptions-item label="流程标识">{{ progressData.processKey }}</el-descriptions-item>
            <el-descriptions-item label="流程名称">{{ progressData.processName }}</el-descriptions-item>
          </el-descriptions>
          
          <div class="section-title">当前任务</div>
          <div v-if="progressData.tasks && progressData.tasks.length > 0">
            <el-card v-for="task in progressData.tasks" :key="task.taskId" class="task-card" shadow="hover">
              <div class="task-name">{{ task.taskName }}</div>
              <div class="task-info">
                <span>处理人: {{ task.assigneeName || task.assignee || '未分配' }}</span>
                <span class="task-time">{{ task.createTime }}</span>
              </div>
            </el-card>
          </div>
          <el-empty v-else description="暂无进行中的任务" />
          
          <div class="section-title">执行历史</div>
          <el-timeline v-if="progressData.nodeHistory && progressData.nodeHistory.length > 0">
            <el-timeline-item
              v-for="(node, index) in progressData.nodeHistory"
              :key="index"
              :type="getNodeTimelineType(node)"
              :icon="getNodeTimelineIcon(node)"
              :timestamp="node.endTime || node.startTime"
            >
              <div class="history-item">
                <span class="node-name">{{ node.nodeName || node.nodeId }}</span>
                <el-tag size="small" :type="node.status === 'COMPLETED' ? 'success' : 'warning'">
                  {{ node.status === 'COMPLETED' ? '已完成' : '进行中' }}
                </el-tag>
              </div>
              <div v-if="node.assignee || node.assigneeName" class="assignee-info">
                执行人: {{ node.assigneeName || node.assignee }}
              </div>
              <div v-if="node.duration" class="duration-info">
                耗时: {{ formatDuration(node.duration) }}
              </div>
              <div v-if="node.action" class="action-info">
                处理结果:
                <el-tag size="small" :type="getActionType(node.action)">
                  {{ getActionText(node.action, node.actionLabel) }}
                </el-tag>
              </div>
              <div v-if="node.comment" class="comment-info">
                <span class="comment-label">审批意见:</span>
                <span>{{ node.comment }}</span>
              </div>
              <div v-if="Object.keys(getDisplayVariables(node)).length > 0" class="node-variables">
                <el-tag v-for="(val, key) in getDisplayVariables(node)" :key="key" size="small" type="info" class="var-tag">
                  {{ key }}: {{ val }}
                </el-tag>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无执行历史" />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { processApi } from '@/api/process'
import VueBpmnViewer from '@/components/VueBpmnViewer.vue'
import FlowActionExecutionLog from '@/components/FlowActionExecutionLog.vue'
import RuntimeVersionDiagnostics from '@/components/RuntimeVersionDiagnostics.vue'
import { useUserStore } from '@/stores/user'
import { formatProcessDuration as formatDuration } from '@flow/workflow-core/process-progress'
import { formatRuntimeCodeVersion } from '@/shared/runtime-diagnostics'

const route = useRoute()
const userStore = useUserStore()
const processInstanceId = route.params.instanceId

const viewerRef = ref()
const actionExecutionVisible = ref(false)
const bpmnXml = ref('')
const progressData = ref({
  completedNodes: [],
  activeNodes: [],
  executedSequenceFlows: [],
  nodeHistory: [],
  tasks: [],
  nodeAssigneeMap: {}
})
const progressRuntimeForm = computed(() =>
  progressData.value?.formConfigs?.[0] || progressData.value?.formConfig || null
)
const processRuntimeDiagnosticEntries = computed(() => {
  const form = progressRuntimeForm.value
  const formVersion = form?.runtimeReleaseVersion ?? form?.formReleaseVersion
  const hotfixSuffix = form?.hotfixApplied === true ? '（已应用热修复）' : ''
  return [
    {
      label: '流程',
      value: formatRuntimeCodeVersion(
        progressData.value?.processKey,
        progressData.value?.processVersion
      )
    },
    {
      label: '表单',
      value: form
        ? `${formatRuntimeCodeVersion(form.formKey, formVersion)}${hotfixSuffix}`
        : '未记录运行表单'
    }
  ]
})
const processRuntimeDiagnosticCopyEntries = computed(() => {
  return [
    ...processRuntimeDiagnosticEntries.value,
    {
      label: '流程实例 ID',
      value: progressData.value?.processInstanceId || processInstanceId
    }
  ]
})

// 提示框状态
const loadProcessProgress = async () => {
  if (!processInstanceId) {
    ElMessage.error('流程实例ID不能为空')
    return
  }
  
  try {
    console.log('正在加载流程进度，实例ID:', processInstanceId)
    const data = await processApi.getProcessProgress(processInstanceId)
    console.log('获取到流程进度数据:', data)
    progressData.value = data
    
    if (data.bpmnXml) {
      console.log('BPMN XML长度:', data.bpmnXml.length)
      bpmnXml.value = data.bpmnXml
    } else {
      ElMessage.warning('无法获取流程图')
    }
  } catch (error) {
    console.error('加载流程进度失败:', error)
    ElMessage.error('加载流程进度失败: ' + (error.message || '未知错误'))
  }
}

// 状态文本
const getStatusType = (status) => {
  const types = {
    'RUNNING': 'warning',
    'COMPLETED': 'success',
    'SUSPENDED': 'info'
  }
  return types[status] || 'info'
}

const getStatusText = (status) => {
  const texts = {
    'RUNNING': '运行中',
    'COMPLETED': '已完成',
    'SUSPENDED': '运行中'
  }
  return texts[status] || status
}

// 处理方式文本
const getActionType = (action) => {
  const types = {
    'APPROVED': 'success',
    'REJECTED': 'danger',
    'TRANSFERRED': 'warning',
    'PROCESSING': 'primary'
  }
  return types[action] || 'info'
}

const getActionText = (action, actionLabel) => {
  if (actionLabel) return actionLabel
  const texts = {
    'APPROVED': '同意',
    'REJECTED': '驳回',
    'TRANSFERRED': '转办',
    'PROCESSING': '处理中'
  }
  return texts[action] || action
}

const getDisplayVariables = (node) => {
  const variables = node?.variables || {}
  const hiddenKeys = new Set([
    'action',
    'actionLabel',
    'approved',
    'approver',
    'comment',
    'initiator',
    'skipNodeEnabled',
    'entityCode',
    'entityDataId',
    'code',
    'submitterId',
    'submitterName',
    '_approvers_'
  ])
  return Object.fromEntries(
    Object.entries(variables).filter(([key]) => !hiddenKeys.has(key))
  )
}

// 时间线样式
const getNodeTimelineType = (node) => {
  if (node.status === 'COMPLETED') return 'success'
  if (node.status === 'ACTIVE') return 'primary'
  return 'info'
}

const getNodeTimelineIcon = (node) => {
  if (node.status === 'COMPLETED') return 'Check'
  if (node.status === 'ACTIVE') return 'Loading'
  return 'CircleCheck'
}

// 布局、异步导入及节点状态均由 VueBpmnViewer 统一维护。
onMounted(loadProcessProgress)
</script>

<style scoped>
.process-progress {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.process-name {
  font-size: 16px;
  font-weight: 600;
}

.process-runtime-diagnostics {
  min-width: 0;
}

.header-right {
  display: flex;
  align-items: center;
}

.legend {
  display: flex;
  gap: 20px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
}

.legend-color {
  width: 20px;
  height: 12px;
  border-radius: 2px;
  border: 2px solid;
}

.legend-color.completed {
  background: #f6ffed;
  border-color: #52c41a;
}

.legend-color.active {
  background: #e6f7ff;
  border-color: #1890ff;
}

.legend-color.pending {
  background: #f5f5f5;
  border-color: #d9d9d9;
}

.progress-container {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.canvas-wrapper {
  flex: 1;
  position: relative;
  background: #fff;
}

.canvas {
  width: 100%;
  height: 100%;
}

/* 节点悬停提示框 */
.info-panel {
  width: 350px;
  background: #fff;
  border-left: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.panel-title {
  padding: 15px;
  font-weight: 600;
  border-bottom: 1px solid #e4e7ed;
  background: #fafafa;
}

.panel-content {
  flex: 1;
  padding: 15px;
  overflow-y: auto;
}

.section-title {
  margin-top: 20px;
  margin-bottom: 10px;
  font-weight: 600;
  color: #303133;
  border-left: 4px solid #409eff;
  padding-left: 10px;
}

.task-card {
  margin-bottom: 10px;
}

.task-name {
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.task-info {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.node-name {
  font-weight: 500;
}

.assignee-info,
.duration-info {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

/* BPMN 高亮样式覆盖 */
:deep(.status-completed .djs-visual rect),
:deep(.status-completed .djs-visual circle),
:deep(.status-completed .djs-visual polygon) {
  fill: #f6ffed !important;
  stroke: #52c41a !important;
  stroke-width: 3px !important;
}

:deep(.status-active .djs-visual rect),
:deep(.status-active .djs-visual circle),
:deep(.status-active .djs-visual polygon) {
  fill: #e6f7ff !important;
  stroke: #1890ff !important;
  stroke-width: 3px !important;
}

:deep(.status-pending .djs-visual rect),
:deep(.status-pending .djs-visual circle),
:deep(.status-pending .djs-visual polygon) {
  fill: #f5f5f5 !important;
  stroke: #d9d9d9 !important;
  stroke-width: 1px !important;
}

/* 节点徽章动画 */
:deep(.node-badge) {
  animation: fadeIn 0.3s ease-in-out;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.node-variables {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.action-info,
.comment-info {
  margin-top: 6px;
  color: #606266;
  font-size: 13px;
}

.action-info :deep(.el-tag) {
  margin-left: 6px;
}

.comment-info {
  display: flex;
  gap: 6px;
  line-height: 1.5;
}

.comment-label {
  flex: none;
  color: #909399;
}

.var-tag {
  font-family: monospace;
  font-size: 12px;
}
</style>
