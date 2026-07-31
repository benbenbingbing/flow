<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>SLA 监控</h2>
        <p>查看运行中、已超时、已暂停和已完成的用户任务 SLA。</p>
      </div>
      <el-button :icon="Refresh" @click="refresh">刷新</el-button>
    </div>

    <div class="metrics">
      <div v-for="item in metricItems" :key="item.key" class="metric">
        <span>{{ item.label }}</span>
        <strong :class="`metric-${item.key.toLowerCase()}`">{{ statistics[item.key] || 0 }}</strong>
      </div>
    </div>

    <div class="filters">
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="节点、业务编号或策略编码"
        @keyup.enter="load"
      />
      <el-select v-model="query.status" clearable placeholder="全部状态">
        <el-option label="运行中" value="RUNNING" />
        <el-option label="已超时" value="BREACHED" />
        <el-option label="已暂停" value="PAUSED" />
        <el-option label="已完成" value="COMPLETED" />
      </el-select>
      <el-input v-model="query.processKey" clearable placeholder="流程编码" />
      <el-input v-model="query.assignee" clearable placeholder="当前负责人" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="nodeName" label="任务节点" min-width="170" />
      <el-table-column prop="processKey" label="流程" min-width="140" />
      <el-table-column prop="businessKey" label="业务编号" min-width="150" />
      <el-table-column prop="policyCode" label="SLA策略" min-width="140" />
      <el-table-column prop="currentAssigneeId" label="当前负责人" min-width="130" />
      <el-table-column label="响应截止" min-width="175">
        <template #default="{ row }">{{ formatTime(row.responseDueAt) }}</template>
      </el-table-column>
      <el-table-column label="办结截止" min-width="175">
        <template #default="{ row }">{{ formatTime(row.completionDueAt) }}</template>
      </el-table-column>
      <el-table-column label="响应" width="105">
        <template #default="{ row }">
          <el-tag :type="metricType(row.responseStatus)">{{ metricText(row.responseStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="办结" width="105">
        <template #default="{ row }">
          <el-tag :type="metricType(row.completionStatus)">{{ metricText(row.completionStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="综合状态" width="110" fixed="right">
        <template #default="{ row }">
          <el-tag :type="overallType(row.overallStatus)">{{ overallText(row.overallStatus) }}</el-tag>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNum"
      v-model:page-size="query.pageSize"
      :total="total"
      :page-sizes="[20, 50, 100]"
      layout="total, sizes, prev, pager, next"
      @current-change="load"
      @size-change="search"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { taskSlaApi } from '@/api/sla'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const statistics = ref({})
const query = reactive({
  pageNum: 1,
  pageSize: 20,
  status: '',
  processKey: '',
  assignee: '',
  keyword: ''
})
const metricItems = [
  { key: 'RUNNING', label: '运行中' },
  { key: 'BREACHED', label: '已超时' },
  { key: 'PAUSED', label: '已暂停' },
  { key: 'COMPLETED', label: '已完成' }
]

async function load() {
  loading.value = true
  try {
    const result = await taskSlaApi.monitor(query)
    rows.value = result.records || []
    total.value = result.total || 0
  } finally {
    loading.value = false
  }
}

async function refresh() {
  statistics.value = await taskSlaApi.statistics()
  await load()
}

function search() {
  query.pageNum = 1
  load()
}

function reset() {
  Object.assign(query, {
    pageNum: 1,
    pageSize: 20,
    status: '',
    processKey: '',
    assignee: '',
    keyword: ''
  })
  load()
}

function formatTime(value) {
  return value ? dayjs(`${value}Z`).format('YYYY-MM-DD HH:mm:ss') : '-'
}

function metricText(value) {
  return ({
    PENDING: '计时中',
    MET: '达标',
    BREACHED: '超时',
    NOT_APPLICABLE: '不计'
  })[value] || value
}

function metricType(value) {
  return ({ PENDING: 'warning', MET: 'success', BREACHED: 'danger', NOT_APPLICABLE: 'info' })[value] || 'info'
}

function overallText(value) {
  return ({ RUNNING: '运行中', BREACHED: '已超时', PAUSED: '已暂停', COMPLETED: '已完成' })[value] || value
}

function overallType(value) {
  return ({ RUNNING: 'warning', BREACHED: 'danger', PAUSED: 'info', COMPLETED: 'success' })[value] || 'info'
}

onMounted(refresh)
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 18px; min-width: 0; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; }
.page-header h2 { margin: 0 0 6px; font-size: 24px; }
.page-header p { margin: 0; color: #64748b; font-size: 13px; }
.metrics { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid #dfe4ec; }
.metric { min-height: 86px; padding: 14px 18px; border-right: 1px solid #dfe4ec; display: flex; flex-direction: column; gap: 8px; }
.metric:last-child { border-right: 0; }
.metric span { color: #64748b; font-size: 13px; }
.metric strong { font-size: 28px; line-height: 1; }
.metric-breached { color: #dc2626; }
.metric-running { color: #d97706; }
.metric-paused { color: #475569; }
.metric-completed { color: #15803d; }
.filters { display: grid; grid-template-columns: minmax(220px, 1.4fr) repeat(3, minmax(150px, 1fr)) auto auto; gap: 10px; }
.el-pagination { justify-content: flex-end; }
@media (max-width: 900px) {
  .metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .metric:nth-child(2) { border-right: 0; }
  .filters { grid-template-columns: 1fr 1fr; }
}
</style>
