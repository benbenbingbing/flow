<template>
  <div class="scope-inventory-page">
    <header class="hero-panel">
      <div>
        <span class="eyebrow">SECURITY GOVERNANCE</span>
        <h2>存量数据范围盘点</h2>
        <p>逐项明确责任人和安全默认策略，再把历史 OBSERVE 列表切换为强制执行。</p>
      </div>
      <div class="hero-actions">
        <el-button :loading="refreshing" @click="refreshInventory">扫描存量对象</el-button>
        <el-button type="primary" :loading="submitting" :disabled="!selectedRows.length" @click="confirmSelected">
          确认所选 {{ selectedRows.length }} 项
        </el-button>
      </div>
    </header>

    <el-alert
      class="risk-alert"
      type="warning"
      :closable="false"
      show-icon
      title="EXPLICIT_ALL 会让没有命中任何 ALLOW 规则的数据保持全量可见，仅超级管理员或拥有专用高风险权限的账号可以确认。"
    />

    <section class="summary-strip">
      <article><strong>{{ total }}</strong><span>盘点对象</span></article>
      <article><strong>{{ visiblePending }}</strong><span>当前页待处理</span></article>
      <article><strong>{{ visibleConfirmed }}</strong><span>当前页已确认</span></article>
      <article :class="{ danger: visibleExplicitAll > 0 }"><strong>{{ visibleExplicitAll }}</strong><span>当前页全量放行</span></article>
    </section>

    <section class="content-panel">
      <div class="filters">
        <el-input v-model="filters.keyword" clearable placeholder="搜索实体、列表名称或标识" @keyup.enter="loadInventory" />
        <el-select v-model="filters.processingStatus" clearable placeholder="全部处理状态">
          <el-option label="待处理" value="PENDING" />
          <el-option label="未分配" value="UNASSIGNED" />
          <el-option label="已确认" value="CONFIRMED" />
          <el-option label="异常" value="EXCEPTION" />
        </el-select>
        <el-button type="primary" @click="search">查询</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        row-key="listId"
        border
        stripe
        @selection-change="selectedRows = $event"
      >
        <el-table-column type="selection" width="48" :selectable="row => row.processingStatus !== 'CONFIRMED'" />
        <el-table-column label="实体 / 列表" min-width="220" fixed="left">
          <template #default="{ row }">
            <div class="primary-text">{{ row.listName || row.listKey }}</div>
            <div class="secondary-text">{{ row.entityCode }} · {{ row.listKey }}</div>
          </template>
        </el-table-column>
        <el-table-column label="检测结果" width="145">
          <template #default="{ row }">
            <el-tag :type="policyTag(row.detectedPolicy)" effect="plain">{{ policyText(row.detectedPolicy) }}</el-tag>
            <div class="secondary-text enforcement">{{ row.detectedEnforcement }}</div>
          </template>
        </el-table-column>
        <el-table-column label="责任人" min-width="230">
          <template #default="{ row }">
            <div v-if="row.processingStatus === 'CONFIRMED'" class="owner-display">
              <strong>{{ row.ownerName }}</strong><span>{{ row.ownerId }}</span>
            </div>
            <div v-else class="owner-editor">
              <el-input v-model="row.ownerName" placeholder="责任人名称" />
              <el-input v-model="row.ownerId" placeholder="用户 ID" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="确认模式" min-width="175">
          <template #default="{ row }">
            <span v-if="row.processingStatus === 'CONFIRMED'">{{ policyText(row.selectedPolicy) }}</span>
            <el-select v-else v-model="row.selectedPolicy" placeholder="必须选择">
              <el-option label="拒绝全部（最安全）" value="DENY_ALL" />
              <el-option label="仅本人数据" value="PERSONAL" />
              <el-option label="显式全量放行" value="EXPLICIT_ALL" :disabled="!canExplicitAll" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="确认理由" min-width="260">
          <template #default="{ row }">
            <span v-if="row.processingStatus === 'CONFIRMED'">{{ row.confirmationReason || '-' }}</span>
            <el-input
              v-else
              v-model="row.confirmationReason"
              :disabled="row.selectedPolicy !== 'EXPLICIT_ALL'"
              maxlength="500"
              placeholder="全量放行时至少填写 5 个字符"
            />
          </template>
        </el-table-column>
        <el-table-column label="处理状态" width="110" align="center">
          <template #default="{ row }"><el-tag :type="statusTag(row.processingStatus)">{{ statusText(row.processingStatus) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="确认记录" min-width="190">
          <template #default="{ row }">
            <template v-if="row.confirmedAt"><div>{{ row.confirmedBy || '-' }}</div><div class="secondary-text">{{ formatTime(row.confirmedAt) }}</div></template>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="filters.pageNum"
        v-model:page-size="filters.pageSize"
        class="pagination"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadInventory"
        @size-change="search"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  batchConfirmEntityListScopeInventory,
  getEntityListScopeInventory,
  refreshEntityListScopeInventory
} from '@/api/entityListScopeInventory'

const userStore = useUserStore()
const loading = ref(false)
const refreshing = ref(false)
const submitting = ref(false)
const rows = ref([])
const selectedRows = ref([])
const total = ref(0)
const filters = reactive({ keyword: '', processingStatus: 'PENDING', pageNum: 1, pageSize: 20 })

const canExplicitAll = computed(() => userStore.isSuperAdmin
  || userStore.permissions.includes('*')
  || userStore.permissions.includes('entity:list-scope:explicit-all'))
const visiblePending = computed(() => rows.value.filter(row => ['PENDING', 'UNASSIGNED'].includes(row.processingStatus)).length)
const visibleConfirmed = computed(() => rows.value.filter(row => row.processingStatus === 'CONFIRMED').length)
const visibleExplicitAll = computed(() => rows.value.filter(row => (row.selectedPolicy || row.detectedPolicy) === 'EXPLICIT_ALL').length)

function unwrap(response) {
  const body = response?.data ?? response
  return body?.data ?? body
}

async function loadInventory() {
  loading.value = true
  try {
    const data = unwrap(await getEntityListScopeInventory({ ...filters })) || {}
    rows.value = (data.records || []).map(row => ({ ...row, selectedPolicy: row.selectedPolicy || '' }))
    total.value = Number(data.total || 0)
    selectedRows.value = []
  } finally {
    loading.value = false
  }
}

function search() {
  filters.pageNum = 1
  loadInventory()
}

async function refreshInventory() {
  refreshing.value = true
  try {
    const count = unwrap(await refreshEntityListScopeInventory()) || 0
    ElMessage.success(`扫描完成，新增 ${count} 个盘点对象`)
    await loadInventory()
  } finally {
    refreshing.value = false
  }
}

async function confirmSelected() {
  const items = selectedRows.value.map(row => ({
    listId: row.listId,
    ownerId: row.ownerId?.trim(),
    ownerName: row.ownerName?.trim(),
    selectedPolicy: row.selectedPolicy,
    reason: row.confirmationReason?.trim() || null
  }))
  const invalid = items.find(item => !item.ownerId || !item.ownerName || !item.selectedPolicy)
  if (invalid) {
    ElMessage.warning('所选对象必须逐项填写责任人并选择确认模式')
    return
  }
  const invalidExplicit = items.find(item => item.selectedPolicy === 'EXPLICIT_ALL' && (!item.reason || item.reason.length < 5))
  if (invalidExplicit) {
    ElMessage.warning('全量放行对象必须逐项填写至少 5 个字符的确认理由')
    return
  }
  const explicitCount = items.filter(item => item.selectedPolicy === 'EXPLICIT_ALL').length
  await ElMessageBox.confirm(
    explicitCount ? `本批包含 ${explicitCount} 个全量放行对象，确认继续？` : `确认提交 ${items.length} 个盘点结果？`,
    '批量确认安全策略',
    { type: explicitCount ? 'warning' : 'info', confirmButtonText: '确认提交' }
  )
  submitting.value = true
  try {
    const result = unwrap(await batchConfirmEntityListScopeInventory(items)) || {}
    ElMessage.success(`已确认 ${result.processed || 0} 项，跳过重复提交 ${result.skipped || 0} 项`)
    await loadInventory()
  } finally {
    submitting.value = false
  }
}

function policyText(value) {
  return { DENY_ALL: '拒绝全部', PERSONAL: '仅本人', EXPLICIT_ALL: '全量放行' }[value] || value || '-'
}
function policyTag(value) {
  return value === 'EXPLICIT_ALL' ? 'danger' : value === 'PERSONAL' ? 'warning' : 'success'
}
function statusText(value) {
  return { UNASSIGNED: '未分配', PENDING: '待处理', CONFIRMED: '已确认', EXCEPTION: '异常' }[value] || value
}
function statusTag(value) {
  return { UNASSIGNED: 'info', PENDING: 'warning', CONFIRMED: 'success', EXCEPTION: 'danger' }[value] || 'info'
}
function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

onMounted(loadInventory)
</script>

<style scoped>
.scope-inventory-page {
  min-height: 100%;
  padding: 24px;
  color: #18332d;
  background: radial-gradient(circle at 88% 2%, rgba(225, 165, 72, .2), transparent 28%), #f3f0e8;
}
.hero-panel, .content-panel, .summary-strip {
  border: 1px solid rgba(39, 74, 65, .14);
  border-radius: 18px;
  background: rgba(255, 255, 252, .94);
  box-shadow: 0 16px 42px rgba(33, 63, 55, .08);
}
.hero-panel { display: flex; align-items: end; justify-content: space-between; gap: 24px; padding: 28px 30px; }
.eyebrow { color: #b06e20; font: 700 12px/1.4 'Avenir Next', sans-serif; letter-spacing: .16em; }
h2 { margin: 7px 0 8px; font: 700 30px/1.2 'Noto Serif SC', serif; }
.hero-panel p { margin: 0; color: #60746e; }
.hero-actions { display: flex; flex-shrink: 0; gap: 10px; }
.risk-alert { margin: 16px 0; }
.summary-strip { display: grid; grid-template-columns: repeat(4, 1fr); margin-bottom: 16px; overflow: hidden; }
.summary-strip article { display: flex; flex-direction: column; padding: 18px 24px; border-right: 1px solid #e5e5dc; }
.summary-strip article:last-child { border-right: 0; }
.summary-strip strong { font: 700 26px/1.2 'Avenir Next', sans-serif; }
.summary-strip span { margin-top: 4px; color: #71817d; font-size: 13px; }
.summary-strip .danger strong { color: #b63d2f; }
.content-panel { padding: 20px; }
.filters { display: grid; grid-template-columns: minmax(260px, 1fr) 190px auto; gap: 10px; margin-bottom: 16px; }
.primary-text { color: #173c32; font-weight: 650; }
.secondary-text { margin-top: 4px; color: #82908c; font-size: 12px; }
.enforcement { letter-spacing: .06em; }
.owner-editor { display: grid; grid-template-columns: 1.15fr .85fr; gap: 6px; }
.owner-display { display: flex; flex-direction: column; }
.owner-display span { color: #82908c; font-size: 12px; }
.pagination { justify-content: flex-end; margin-top: 18px; }
@media (max-width: 900px) {
  .scope-inventory-page { padding: 14px; }
  .hero-panel { align-items: stretch; flex-direction: column; }
  .hero-actions { display: grid; grid-template-columns: 1fr 1fr; }
  .summary-strip { grid-template-columns: repeat(2, 1fr); }
  .summary-strip article:nth-child(2) { border-right: 0; }
  .filters { grid-template-columns: 1fr; }
}
</style>
