<template>
  <div class="execution-log">
    <div class="log-toolbar">
      <el-alert
        title="执行上下文、参数、结果和异常仅对超级管理员开放；敏感字段会在接口响应中脱敏。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-table :data="records" v-loading="loading" stripe row-key="id">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="execution-detail">
            <el-descriptions :column="3" border size="small">
              <el-descriptions-item label="动作名称">{{ row.actionName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="处理器中文名">{{ row.handlerDisplayName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="处理器 Bean">{{ row.handlerName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="作用域">{{ scopeLabel(row.scopeType) }}</el-descriptions-item>
              <el-descriptions-item label="元素 ID">{{ row.elementId || '全局流程' }}</el-descriptions-item>
              <el-descriptions-item label="执行时机">{{ row.triggerTiming || '-' }}</el-descriptions-item>
              <el-descriptions-item label="任务 ID">{{ row.taskId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="执行 ID">{{ row.executionId || '-' }}</el-descriptions-item>
              <el-descriptions-item label="幂等键">{{ row.idempotencyKey || '-' }}</el-descriptions-item>
              <el-descriptions-item label="开始时间">{{ formatTime(row.startedAt) }}</el-descriptions-item>
              <el-descriptions-item label="结束时间">{{ formatTime(row.finishedAt) }}</el-descriptions-item>
              <el-descriptions-item label="执行耗时">{{ formatDuration(row.durationMs) }}</el-descriptions-item>
            </el-descriptions>

            <div class="detail-grid">
              <section>
                <h4>解析后参数</h4>
                <pre>{{ pretty(row.resolvedParams) }}</pre>
              </section>
              <section>
                <h4>执行结果</h4>
                <pre>{{ pretty(row.result) }}</pre>
              </section>
              <section class="context-section">
                <h4>触发上下文</h4>
                <pre>{{ pretty(row.triggerContext) }}</pre>
              </section>
            </div>

            <section class="trace-section">
              <h4>执行过程</h4>
              <el-timeline v-if="row.executionTrace?.length">
                <el-timeline-item
                  v-for="(step, index) in row.executionTrace"
                  :key="`${row.id}-${index}`"
                  :timestamp="step.time"
                  :type="traceType(step.stage)"
                >
                  <strong>{{ step.stage }}</strong>
                  <span class="trace-message">{{ step.message }}</span>
                  <pre v-if="step.details">{{ pretty(step.details) }}</pre>
                </el-timeline-item>
              </el-timeline>
              <el-empty v-else description="暂无执行步骤" :image-size="56" />
            </section>

            <el-alert
              v-if="row.errorMessage"
              :title="row.errorMessage"
              type="error"
              :closable="false"
              show-icon
              class="error-alert"
            />
            <el-collapse v-if="row.errorStack">
              <el-collapse-item title="查看异常堆栈">
                <pre class="error-stack">{{ row.errorStack }}</pre>
              </el-collapse-item>
            </el-collapse>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="actionName" label="动作" min-width="150">
        <template #default="{ row }">
          <div class="action-summary">
            <strong>{{ row.actionName || row.actionId }}</strong>
            <span>{{ row.handlerDisplayName || row.handlerName }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="triggerTiming" label="执行时机" min-width="150" />
      <el-table-column prop="elementId" label="节点/连线" min-width="130">
        <template #default="{ row }">{{ row.elementId || '全局流程' }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="重试" width="90">
        <template #default="{ row }">{{ row.retryCount || 0 }}/{{ row.maxRetries || 0 }}</template>
      </el-table-column>
      <el-table-column label="耗时" width="100">
        <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="触发时间" min-width="165">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="['FAILED', 'DEAD'].includes(row.status)"
            link
            type="primary"
            @click="retry(row)"
          >
            手工重试
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && records.length === 0" description="暂无流程动作执行记录" />
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { processActionApi } from '@/api/processAction'

const props = defineProps({
  processInstanceId: { type: String, default: '' },
  active: { type: Boolean, default: true }
})

const loading = ref(false)
const records = ref([])
const loadedProcessId = ref('')

watch(
  () => [props.processInstanceId, props.active],
  ([processInstanceId, active]) => {
    if (active && processInstanceId && loadedProcessId.value !== processInstanceId) {
      load()
    }
  },
  { immediate: true }
)

async function load() {
  if (!props.processInstanceId) return
  loading.value = true
  try {
    records.value = await processActionApi.findExecutions(props.processInstanceId) || []
    loadedProcessId.value = props.processInstanceId
  } catch (error) {
    console.error(error)
    ElMessage.error(error?.message || '加载流程动作执行记录失败')
  } finally {
    loading.value = false
  }
}

async function retry(row) {
  await ElMessageBox.confirm(
    `确定重新执行“${row.actionName || row.actionId}”吗？请确认外部接口支持幂等。`,
    '手工重试',
    { type: 'warning' }
  )
  await processActionApi.retryExecution(row.id)
  ElMessage.success('已重新加入执行队列')
  await load()
}

function pretty(value) {
  if (value == null) return '-'
  return JSON.stringify(value, null, 2)
}

function scopeLabel(value) {
  return value === 'PROCESS' ? '全局流程' : value === 'NODE' ? '节点' : '连线'
}

function statusLabel(value) {
  const labels = {
    PENDING: '待执行',
    RUNNING: '执行中',
    SUCCESS: '成功',
    FAILED: '等待重试',
    DEAD: '失败/死信'
  }
  return labels[value] || value
}

function statusType(value) {
  if (value === 'SUCCESS') return 'success'
  if (value === 'RUNNING') return 'primary'
  if (value === 'PENDING' || value === 'FAILED') return 'warning'
  return 'danger'
}

function traceType(stage) {
  if (stage === 'SUCCESS' || stage === 'HANDLER_COMPLETED') return 'success'
  if (String(stage).includes('FAILED') || String(stage).includes('EXHAUSTED')) return 'danger'
  if (String(stage).includes('RETRY')) return 'warning'
  return 'primary'
}

function formatTime(value) {
  return value ? String(value).replace('T', ' ') : '-'
}

function formatDuration(value) {
  if (value == null) return '-'
  if (value < 1000) return `${value} ms`
  return `${(value / 1000).toFixed(2)} s`
}

defineExpose({ load })
</script>

<style scoped>
.log-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.log-toolbar .el-alert {
  flex: 1;
}

.action-summary,
.handler-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.action-summary span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.execution-detail {
  padding: 12px 24px 20px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.context-section {
  grid-column: 1 / -1;
}

.detail-grid section,
.trace-section {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}

h4 {
  margin: 0 0 10px;
}

pre {
  max-height: 320px;
  margin: 0;
  padding: 10px;
  overflow: auto;
  color: #d7dae0;
  background: #1f2329;
  border-radius: 5px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.55;
}

.trace-section {
  margin-top: 12px;
}

.trace-message {
  margin-left: 8px;
  color: var(--el-text-color-regular);
}

.trace-section pre {
  margin-top: 8px;
}

.error-alert,
.error-stack {
  margin-top: 12px;
}

@media (max-width: 900px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .context-section {
    grid-column: auto;
  }
}
</style>
