<template>
  <div class="incident-page">
    <section class="hero-panel">
      <div>
        <p class="eyebrow">WORKFLOW OPERATIONS</p>
        <h1>空办理人事件中心</h1>
        <p class="hero-copy">集中发现、追踪和恢复无法生成有效办理人的流程任务。</p>
      </div>
      <el-button :loading="loading" @click="refresh">刷新</el-button>
    </section>

    <section class="metric-grid">
      <article v-for="item in metricCards" :key="item.label" class="metric-card">
        <span>{{ item.label }}</span>
        <strong>{{ item.value }}</strong>
      </article>
    </section>

    <section class="workspace-panel">
      <header class="toolbar">
        <div>
          <h2>待处理事件</h2>
          <span>所有操作均记录请求号、操作人和处置历史</span>
        </div>
        <el-select v-model="status" clearable placeholder="全部状态" style="width: 180px" @change="loadList">
          <el-option label="待处理" value="OPEN" />
          <el-option label="等待重试" value="RETRY_SCHEDULED" />
          <el-option label="处理中" value="PROCESSING" />
          <el-option label="已解决" value="RESOLVED" />
          <el-option label="已终止" value="TERMINATED" />
        </el-select>
      </header>

      <el-table v-loading="loading" :data="incidents" row-key="id" @row-click="openDetail">
        <el-table-column prop="id" label="事件 ID" min-width="185" show-overflow-tooltip />
        <el-table-column label="流程 / 节点" min-width="190">
          <template #default="{ row }">
            <div class="primary-cell">{{ value(row, 'processInstanceId') || '-' }}</div>
            <small>{{ value(row, 'nodeName') || value(row, 'nodeId') || '-' }}</small>
          </template>
        </el-table-column>
        <el-table-column label="策略" width="160">
          <template #default="{ row }"><el-tag effect="plain">{{ value(row, 'policy') || '-' }}</el-tag></template>
        </el-table-column>
        <el-table-column label="原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ value(row, 'reasonMessage') || value(row, 'reasonCode') || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }"><el-tag :type="statusType(value(row, 'status'))">{{ value(row, 'status') }}</el-tag></template>
        </el-table-column>
        <el-table-column label="重试" width="90">
          <template #default="{ row }">{{ value(row, 'retryCount') || 0 }}/{{ value(row, 'maxRetries') || 0 }}</template>
        </el-table-column>
        <el-table-column label="责任人" width="130">
          <template #default="{ row }">{{ value(row, 'responsibilityOwner') || '-' }}</template>
        </el-table-column>
        <el-table-column label="发生时间" width="175">
          <template #default="{ row }">{{ formatDate(value(row, 'createdAt')) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }"><el-button link type="primary" @click.stop="openDetail(row)">详情</el-button></template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer v-model="detailVisible" title="事件详情" size="min(680px, 92vw)">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="事件 ID" :span="2">{{ value(detail, 'id') }}</el-descriptions-item>
          <el-descriptions-item label="流程实例">{{ value(detail, 'processInstanceId') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="任务">{{ value(detail, 'taskId') || '-' }}</el-descriptions-item>
          <el-descriptions-item label="节点">{{ value(detail, 'nodeName') || value(detail, 'nodeId') }}</el-descriptions-item>
          <el-descriptions-item label="策略">{{ value(detail, 'policy') }}</el-descriptions-item>
          <el-descriptions-item label="原因" :span="2">{{ value(detail, 'reasonMessage') || value(detail, 'reasonCode') }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-heading">
          <h3>处置历史</h3>
          <el-button v-if="canHandle && isOpen" type="primary" @click="handleVisible = true">人工处置</el-button>
        </div>
        <el-timeline v-if="actions.length">
          <el-timeline-item v-for="action in actions" :key="value(action, 'id')" :timestamp="formatDate(value(action, 'createdAt'))">
            <strong>{{ value(action, 'actionType') }}</strong>
            <p>{{ value(value(action, 'request'), 'reason') || value(action, 'errorMessage') || actionResult(action) }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无处置记录" />
      </template>
    </el-drawer>

    <el-dialog v-model="handleVisible" title="处置空办理人事件" width="520px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="处置动作" required>
          <el-select v-model="handleForm.action" style="width: 100%">
            <el-option v-for="option in actionOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="handleForm.action === 'ASSIGN_USER'" label="指定用户 ID" required>
          <el-input v-model.trim="handleForm.userId" />
        </el-form-item>
        <el-form-item v-if="handleForm.action === 'FALLBACK_GROUP'" label="兜底用户组编码" required>
          <el-input v-model.trim="handleForm.groupCode" />
        </el-form-item>
        <el-form-item label="处置原因" required><el-input v-model.trim="handleForm.reason" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="handling" @click="submitHandle">确认处置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { assigneeIncidentApi } from '@/api/assigneeIncident'

const userStore = useUserStore()
const incidents = ref([])
const metrics = ref({})
const detail = ref(null)
const status = ref('OPEN')
const loading = ref(false)
const handling = ref(false)
const detailVisible = ref(false)
const handleVisible = ref(false)
const handleForm = reactive({ action: 'RETRY_RESOLVER', userId: '', groupCode: '', reason: '' })

const hasPermission = permission => userStore.isSuperAdmin || userStore.permissions.includes('*') || userStore.permissions.includes(permission)
const canHandle = computed(() => hasPermission('process:assignee-incident:handle'))
const isOpen = computed(() => ['OPEN', 'RETRY_SCHEDULED', 'PROCESSING'].includes(value(detail.value, 'status')))
const actions = computed(() => detail.value?.actions || detail.value?.actionHistory || [])
const metricCards = computed(() => [
  { label: '待处理', value: statusCount('OPEN') },
  { label: '等待重试', value: statusCount('RETRY_SCHEDULED') },
  { label: '需要人工核对', value: statusCount('MANUAL_REQUIRED') },
  { label: '逾期重试', value: metric('overdueRetries') }
])
const actionOptions = [
  { label: '重新执行解析器', value: 'RETRY_RESOLVER' },
  { label: '指定办理用户', value: 'ASSIGN_USER' },
  { label: '切换兜底用户组', value: 'FALLBACK_GROUP' },
  { label: '终止流程实例', value: 'TERMINATE_INSTANCE' }
]

function unwrap(response) {
  const body = response?.data ?? response
  return body?.data ?? body
}

function value(source, key) {
  if (!source) return undefined
  const snake = key.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`)
  return source[key] ?? source[snake]
}

function metric(...keys) {
  for (const key of keys) {
    const result = value(metrics.value, key)
    if (result !== undefined && result !== null) return result
  }
  return 0
}

function statusCount(statusValue) {
  const rows = value(metrics.value, 'statusCounts') || []
  const row = rows.find(item => value(item, 'status') === statusValue)
  return Number(value(row, 'count') || 0)
}

function actionResult(action) {
  const result = value(action, 'result')
  return result ? JSON.stringify(result) : '-'
}

function formatDate(input) {
  if (!input) return '-'
  return String(input).replace('T', ' ').slice(0, 19)
}

function statusType(input) {
  if (input === 'RESOLVED') return 'success'
  if (input === 'TERMINATED') return 'info'
  if (input === 'RETRY_SCHEDULED') return 'warning'
  return 'danger'
}

async function loadList() {
  loading.value = true
  try {
    incidents.value = unwrap(await assigneeIncidentApi.list(status.value)) || []
  } finally {
    loading.value = false
  }
}

async function loadMetrics() {
  metrics.value = unwrap(await assigneeIncidentApi.metrics()) || {}
}

async function refresh() {
  await Promise.all([loadList(), loadMetrics()])
}

async function openDetail(row) {
  detail.value = unwrap(await assigneeIncidentApi.detail(value(row, 'id')))
  detailVisible.value = true
}

function requestId() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

async function submitHandle() {
  if (!handleForm.reason) return ElMessage.warning('请填写处置原因')
  if (handleForm.action === 'ASSIGN_USER' && !handleForm.userId) return ElMessage.warning('请填写用户 ID')
  if (handleForm.action === 'FALLBACK_GROUP' && !handleForm.groupCode) return ElMessage.warning('请填写用户组编码')
  handling.value = true
  try {
    await assigneeIncidentApi.handle(value(detail.value, 'id'), { requestId: requestId(), ...handleForm })
    ElMessage.success('事件处置已提交')
    handleVisible.value = false
    await openDetail(detail.value)
    await refresh()
  } finally {
    handling.value = false
  }
}

onMounted(refresh)
</script>

<style scoped>
.incident-page { min-height: 100%; padding: 24px; color: #17221f; background: radial-gradient(circle at 8% 0%, #d9efe6 0, transparent 34%), #f3f5f0; }
.hero-panel, .workspace-panel { border: 1px solid #d9dfd8; border-radius: 18px; background: rgba(255, 255, 252, .92); box-shadow: 0 16px 40px rgba(28, 55, 45, .07); }
.hero-panel { display: flex; align-items: center; justify-content: space-between; padding: 26px 30px; }
.eyebrow { margin: 0 0 6px; color: #237154; font-size: 11px; font-weight: 800; letter-spacing: .16em; }
h1 { margin: 0; font-family: Georgia, 'Noto Serif SC', serif; font-size: 30px; }
.hero-copy { margin: 8px 0 0; color: #637069; }
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; margin: 16px 0; }
.metric-card { padding: 18px 20px; border: 1px solid #dce3dc; border-radius: 14px; background: #fff; }
.metric-card span { display: block; color: #6b7771; font-size: 13px; }
.metric-card strong { display: block; margin-top: 5px; color: #164f3b; font: 700 28px Georgia, serif; }
.workspace-panel { padding: 22px; }
.toolbar, .detail-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.toolbar { margin-bottom: 18px; }
.toolbar h2, .detail-heading h3 { margin: 0; }
.toolbar span { color: #7b8580; font-size: 13px; }
.primary-cell { font-weight: 650; }
small { color: #7b8580; }
.detail-heading { margin: 24px 0 14px; }
@media (max-width: 900px) { .metric-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 600px) { .incident-page { padding: 12px; } .hero-panel { align-items: flex-start; padding: 20px; } .hero-copy { max-width: 240px; } .metric-grid { grid-template-columns: 1fr 1fr; } }
</style>
