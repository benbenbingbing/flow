<template>
  <div class="audit-page">
    <el-card class="search-card" shadow="never">
      <el-form
        class="audit-search-form"
        :model="filters"
        inline
        label-width="64px"
        @submit.prevent="handleSearch"
      >
        <el-form-item label="时间范围" class="filter-time">
          <el-date-picker
            v-model="filters.timeRange"
            type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            range-separator="至"
            clearable
          />
        </el-form-item>
        <el-form-item label="模块" class="filter-select">
          <el-select v-model="filters.module" placeholder="全部" clearable>
            <el-option
              v-for="option in moduleOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作" class="filter-select">
          <el-select v-model="filters.operation" placeholder="全部" clearable filterable>
            <el-option
              v-for="option in operationOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="结果" class="filter-select">
          <el-select v-model="filters.result" placeholder="全部" clearable>
            <el-option label="成功" value="SUCCESS" />
            <el-option label="失败" value="FAILURE" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="searchExpanded" label="风险" class="filter-select">
          <el-select v-model="filters.riskLevel" placeholder="全部" clearable>
            <el-option label="低" value="LOW" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="高" value="HIGH" />
            <el-option label="严重" value="CRITICAL" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="searchExpanded" label="操作人" class="filter-input">
          <el-input v-model="filters.operator" placeholder="姓名或用户 ID" clearable />
        </el-form-item>
        <el-form-item v-if="searchExpanded" label="目标" class="filter-input">
          <el-input v-model="filters.targetId" placeholder="目标 ID" clearable />
        </el-form-item>
        <el-form-item v-if="searchExpanded" label="Trace ID" class="filter-input">
          <el-input v-model="filters.traceId" placeholder="完整 Trace ID" clearable />
        </el-form-item>
        <el-form-item class="search-actions">
          <el-button type="primary" native-type="submit">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button link type="primary" @click="searchExpanded = !searchExpanded">
            {{ searchExpanded ? '收起' : '展开' }}
            <el-icon>
              <ArrowUp v-if="searchExpanded" />
              <ArrowDown v-else />
            </el-icon>
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="audit-panel" shadow="never">
      <div class="table-toolbar">
        <el-button :loading="loading" @click="fetchLogs">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button
          v-if="canExport"
          :loading="exporting"
          @click="handleExport"
        >
          <el-icon><Download /></el-icon>
          导出
        </el-button>
      </div>

      <PageState
        v-if="loadError"
        type="error"
        title="系统日志加载失败"
        :description="loadError"
        retryable
        compact
        @retry="fetchLogs"
      />

      <template v-else>
        <el-table
          v-loading="loading"
          :data="logs"
          border
          stripe
          row-key="id"
          class="audit-table"
        >
          <el-table-column prop="createTime" label="时间" width="168">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column prop="moduleCode" label="模块" width="92">
            <template #default="{ row }">
              <el-tag effect="plain" type="info">{{ moduleLabel(row.moduleCode) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="190">
            <template #default="{ row }">
              <div class="primary-line">{{ row.operationName || operationLabel(row.operationCode) }}</div>
              <div class="meta-line">{{ row.operationCode || '-' }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="riskLevel" label="风险" width="82" align="center">
            <template #default="{ row }">
              <el-tag :type="riskType(row.riskLevel)" effect="plain">
                {{ riskLabel(row.riskLevel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="result" label="结果" width="82" align="center">
            <template #default="{ row }">
              <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'">
                {{ row.result === 'SUCCESS' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作人" min-width="130">
            <template #default="{ row }">
              <div class="primary-line">{{ row.operatorName || '系统' }}</div>
              <div v-if="row.operatorId" class="meta-line">{{ row.operatorId }}</div>
            </template>
          </el-table-column>
          <el-table-column label="目标" min-width="170">
            <template #default="{ row }">
              <div class="primary-line">{{ row.targetName || row.targetType || '-' }}</div>
              <div v-if="row.targetId" class="meta-line">{{ row.targetId }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="summary" label="摘要" min-width="220" show-overflow-tooltip />
          <el-table-column prop="durationMs" label="耗时" width="92" align="right">
            <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
          </el-table-column>
          <el-table-column v-if="canViewDetail" label="操作" width="78" fixed="right" align="center">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="pageInfo.pageNum"
          v-model:page-size="pageInfo.pageSize"
          :total="pageInfo.total"
          :page-sizes="[20, 50, 100, 200]"
          layout="total, sizes, prev, pager, next, jumper"
          class="pagination"
          @size-change="handleSizeChange"
          @current-change="fetchLogs"
        />
      </template>
    </el-card>

    <el-drawer
      v-model="detailVisible"
      title="日志详情"
      size="min(760px, 94vw)"
      destroy-on-close
    >
      <PageState
        v-if="detailError"
        type="error"
        title="日志详情加载失败"
        :description="detailError"
        retryable
        compact
        @retry="loadDetail"
      />
      <div v-else v-loading="detailLoading" class="detail-content">
        <el-alert
          v-if="detail.payloadTruncated"
          title="部分请求或结果内容因长度限制已截断"
          type="warning"
          :closable="false"
          show-icon
        />

        <section class="detail-section">
          <h3>执行信息</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="时间">{{ formatTime(detail.createTime) }}</el-descriptions-item>
            <el-descriptions-item label="结果">
              <el-tag :type="detail.result === 'SUCCESS' ? 'success' : 'danger'">
                {{ detail.result === 'SUCCESS' ? '成功' : '失败' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="模块">{{ moduleLabel(detail.moduleCode) }}</el-descriptions-item>
            <el-descriptions-item label="风险">{{ riskLabel(detail.riskLevel) }}</el-descriptions-item>
            <el-descriptions-item label="操作" :span="2">
              {{ detail.operationName || '-' }}（{{ detail.operationCode || '-' }}）
            </el-descriptions-item>
            <el-descriptions-item label="摘要" :span="2">{{ detail.summary || '-' }}</el-descriptions-item>
            <el-descriptions-item label="耗时">{{ formatDuration(detail.durationMs) }}</el-descriptions-item>
            <el-descriptions-item label="事件 ID">{{ detail.eventId || '-' }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-section">
          <h3>请求与操作人</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="操作人">{{ detail.operatorName || '系统' }}</el-descriptions-item>
            <el-descriptions-item label="用户 ID">{{ detail.operatorId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="IP">{{ detail.operatorIp || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Trace ID">{{ detail.traceId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="请求" :span="2">
              {{ [detail.requestMethod, detail.requestPath].filter(Boolean).join(' ') || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="User Agent" :span="2">{{ detail.userAgent || '-' }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section class="detail-section">
          <h3>目标</h3>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="目标类型">{{ detail.targetType || '-' }}</el-descriptions-item>
            <el-descriptions-item label="目标 ID">{{ detail.targetId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="目标名称" :span="2">{{ detail.targetName || '-' }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section v-if="detail.errorCode || detail.errorMessage" class="detail-section">
          <h3>错误</h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="错误码">{{ detail.errorCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="错误信息">{{ detail.errorMessage || '-' }}</el-descriptions-item>
          </el-descriptions>
        </section>

        <section
          v-for="payload in detailPayloads"
          :key="payload.key"
          class="detail-section"
        >
          <h3>{{ payload.label }}</h3>
          <pre class="json-viewer">{{ formatJson(payload.value) }}</pre>
        </section>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import dayjs from 'dayjs'
import { ArrowDown, ArrowUp, Download, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import PageState from '@/components/PageState.vue'
import { useUserStore } from '@/stores/user'
import {
  exportSystemAuditLogs,
  getSystemAuditLogDetail,
  getSystemAuditLogs
} from '@/api/system/audit'

const moduleOptions = [
  { value: 'SECURITY', label: '安全' },
  { value: 'SYSTEM', label: '系统' },
  { value: 'ENTITY', label: '实体' },
  { value: 'PROCESS', label: '流程' },
  { value: 'ACTION', label: '动作' },
  { value: 'MIGRATION', label: '迁移' },
  { value: 'STORAGE', label: '存储' },
  { value: 'INTEGRATION', label: '集成' }
]

const operationOptions = [
  ['CREATE', '创建'],
  ['UPDATE', '更新'],
  ['UPSERT', '保存'],
  ['DELETE', '删除'],
  ['BATCH_DELETE', '批量删除'],
  ['ENABLE', '启用'],
  ['DISABLE', '禁用'],
  ['RESET_PASSWORD', '重置密码'],
  ['ASSIGN_PERMISSION', '分配权限'],
  ['PUBLISH', '发布'],
  ['UNPUBLISH', '取消发布'],
  ['ROLLBACK', '回滚'],
  ['IMPORT', '导入'],
  ['EXPORT', '导出'],
  ['UPLOAD', '上传'],
  ['START', '发起'],
  ['APPROVE', '同意'],
  ['REJECT', '拒绝'],
  ['TRANSFER', '转办'],
  ['WITHDRAW', '撤回'],
  ['RESUBMIT', '重新提交'],
  ['TERMINATE', '终止'],
  ['ADD_SIGN', '加签'],
  ['CANCEL_ADD_SIGN', '取消加签'],
  ['CC', '知会'],
  ['RETRY', '重试'],
  ['LOGIN', '登录'],
  ['LOGOUT', '退出'],
  ['CONFIGURE', '配置'],
  ['OTHER', '其他']
].map(([value, label]) => ({ value, label }))

const moduleLabelMap = Object.fromEntries(moduleOptions.map(option => [option.value, option.label]))
const operationLabelMap = Object.fromEntries(operationOptions.map(option => [option.value, option.label]))
const userStore = useUserStore()

const filters = reactive({
  timeRange: [],
  module: '',
  operation: '',
  operator: '',
  result: '',
  riskLevel: '',
  targetId: '',
  traceId: ''
})
const pageInfo = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const logs = ref([])
const loading = ref(false)
const loadError = ref('')
const searchExpanded = ref(false)
const exporting = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const detailId = ref('')
const detail = ref({})

const hasPermission = permission => userStore.isSuperAdmin
  || userStore.permissions.includes('*')
  || userStore.permissions.includes(permission)
const canViewDetail = computed(() => hasPermission('system:audit:detail'))
const canExport = computed(() => hasPermission('system:audit:export'))
const detailPayloads = computed(() => [
  { key: 'before', label: '变更前', value: detail.value.beforeJson },
  { key: 'after', label: '变更后', value: detail.value.afterJson },
  { key: 'fields', label: '变更字段', value: detail.value.changedFieldsJson }
].filter(item => item.value !== null && item.value !== undefined && item.value !== ''))

function buildQuery(includePage = true) {
  const query = {
    startTime: filters.timeRange?.[0],
    endTime: filters.timeRange?.[1],
    module: filters.module,
    operation: filters.operation,
    operator: filters.operator?.trim(),
    result: filters.result,
    riskLevel: filters.riskLevel,
    targetId: filters.targetId?.trim(),
    traceId: filters.traceId?.trim()
  }
  if (includePage) {
    query.pageNum = pageInfo.pageNum
    query.pageSize = pageInfo.pageSize
  }
  return Object.fromEntries(Object.entries(query).filter(([, value]) => value !== '' && value != null))
}

async function fetchLogs() {
  loading.value = true
  loadError.value = ''
  try {
    const result = await getSystemAuditLogs(buildQuery())
    logs.value = result?.list || result?.records || []
    pageInfo.total = Number(result?.total || 0)
    pageInfo.pageNum = Number(result?.pageNum || pageInfo.pageNum)
    pageInfo.pageSize = Number(result?.pageSize || pageInfo.pageSize)
  } catch (error) {
    logs.value = []
    pageInfo.total = 0
    loadError.value = error?.message || '请稍后重试'
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageInfo.pageNum = 1
  fetchLogs()
}

function handleReset() {
  Object.assign(filters, {
    timeRange: [],
    module: '',
    operation: '',
    operator: '',
    result: '',
    riskLevel: '',
    targetId: '',
    traceId: ''
  })
  pageInfo.pageNum = 1
  fetchLogs()
}

function handleSizeChange() {
  pageInfo.pageNum = 1
  fetchLogs()
}

async function openDetail(row) {
  detailId.value = row.id
  detail.value = row
  detailVisible.value = true
  await loadDetail()
}

async function loadDetail() {
  if (!detailId.value) return
  detailLoading.value = true
  detailError.value = ''
  try {
    detail.value = await getSystemAuditLogDetail(detailId.value)
  } catch (error) {
    detailError.value = error?.message || '请稍后重试'
  } finally {
    detailLoading.value = false
  }
}

async function handleExport() {
  exporting.value = true
  try {
    const blob = await exportSystemAuditLogs(buildQuery(false))
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `system-audit-logs-${dayjs().format('YYYYMMDD-HHmmss')}.csv`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    ElMessage.success('系统日志已导出')
  } finally {
    exporting.value = false
  }
}

function formatTime(value) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm:ss') : '-'
}

function formatDuration(value) {
  if (value === null || value === undefined) return '-'
  if (Number(value) < 1000) return `${value} ms`
  return `${(Number(value) / 1000).toFixed(2)} s`
}

function moduleLabel(value) {
  return moduleLabelMap[value] || value || '-'
}

function operationLabel(value) {
  return operationLabelMap[value] || value || '-'
}

function riskLabel(value) {
  return { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '严重' }[value] || value || '-'
}

function riskType(value) {
  return { LOW: 'info', MEDIUM: 'primary', HIGH: 'warning', CRITICAL: 'danger' }[value] || 'info'
}

function formatJson(value) {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value !== 'string') return JSON.stringify(value, null, 2)
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

onMounted(fetchLogs)
</script>

<style scoped>
.audit-page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.audit-panel {
  flex: 1;
}

.search-card :deep(.el-card__body) {
  padding: 18px 20px 4px;
}

.audit-panel :deep(.el-card__body) {
  padding: 20px;
}

.audit-search-form {
  display: flex;
  flex-wrap: wrap;
}

.audit-search-form :deep(.el-form-item) {
  margin-right: 24px;
  margin-bottom: 14px;
}

.audit-search-form :deep(.el-form-item__label) {
  white-space: nowrap;
}

.filter-time :deep(.el-date-editor) {
  width: 380px;
}

.filter-select :deep(.el-select) {
  width: 150px;
}

.filter-input :deep(.el-input) {
  width: 210px;
}

.search-actions {
  display: flex;
  align-items: center;
  margin-right: 0;
}

.table-toolbar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 12px;
}

.audit-table {
  width: 100%;
}

.primary-line {
  overflow: hidden;
  color: #303133;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-line {
  overflow: hidden;
  margin-top: 3px;
  color: #909399;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-content {
  min-height: 180px;
}

.detail-section {
  margin-top: 22px;
}

.detail-section:first-child {
  margin-top: 0;
}

.detail-section h3 {
  margin: 0 0 10px;
  color: #303133;
  font-size: 15px;
  line-height: 22px;
}

.json-viewer {
  max-height: 360px;
  padding: 14px;
  margin: 0;
  overflow: auto;
  color: #303133;
  font: 12px/1.6 ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

@media (max-width: 760px) {
  .search-card :deep(.el-card__body),
  .audit-panel :deep(.el-card__body) {
    padding: 14px;
  }

  .audit-search-form {
    display: block;
  }

  .audit-search-form :deep(.el-form-item) {
    display: flex;
    width: 100%;
    margin-right: 0;
  }

  .filter-time :deep(.el-date-editor),
  .filter-select :deep(.el-select),
  .filter-input :deep(.el-input) {
    width: 100%;
  }

  .search-actions :deep(.el-form-item__content) {
    margin-left: 0 !important;
  }

  .table-toolbar {
    justify-content: flex-start;
  }

  .pagination {
    justify-content: flex-start;
    overflow-x: auto;
  }

  .detail-section :deep(.el-descriptions__body) {
    overflow-x: auto;
  }
}
</style>
