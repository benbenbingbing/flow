<template>
  <el-drawer
    v-model="visible"
    :size="drawerSize"
    destroy-on-close
    class="record-version-drawer"
    @closed="restoreTriggerFocus"
  >
    <template #header>
      <div class="drawer-heading">
        <div>
          <h3>{{ recordTitle }} · 数据版本</h3>
          <span>{{ entityCode }} / {{ record?.id || '-' }}</span>
        </div>
        <div class="drawer-heading__actions">
          <el-button v-if="canCapture" :loading="captureLoading" @click="captureNow">立即固化</el-button>
          <el-button :loading="loading" title="刷新版本" aria-label="刷新版本" @click="loadVersions">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>
      </div>
    </template>

    <div ref="drawerBodyRef" v-loading="loading" class="version-body">
      <el-empty v-if="!loading && versions.length === 0" description="当前数据还没有正式版本" />

      <div v-else class="version-layout">
        <aside class="timeline-pane" :class="{ 'is-collapsed': timelineCollapsed }">
          <div class="pane-heading">
            <div><strong>版本时间线</strong><span>共 {{ versionTotal }} 个版本</span></div>
            <el-button class="timeline-toggle" text @click="timelineCollapsed = !timelineCollapsed">
              {{ timelineCollapsed ? '展开' : '收起' }}
            </el-button>
          </div>
          <div v-show="!timelineCollapsed" class="timeline-content">
            <el-timeline>
              <el-timeline-item
                v-for="item in versions"
                :key="item.id || item.versionNo"
                :timestamp="formatTime(item.createTime)"
                placement="top"
                :type="item.hasFieldChanges === false ? 'info' : 'primary'"
              >
                <div class="version-item">
                  <div class="version-title">
                    <strong>V{{ item.versionNo }} {{ item.scenarioName || item.versionTitle }}</strong>
                    <el-tag v-if="item.hasFieldChanges === false" type="info" effect="plain" size="small">无数据变化</el-tag>
                  </div>
                  <div class="version-meta">
                    <span>{{ item.businessIntentName || operationText(item.operationType) }}</span>
                    <span>{{ sourceText(item.sourceType) }}</span>
                    <span>{{ item.operatorName || item.operatorId || '系统' }}</span>
                  </div>
                  <el-button link type="primary" @click="openSnapshot(item)">查看表单快照</el-button>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-pagination
              v-if="versionTotal > versionPageSize"
              class="timeline-pagination"
              v-model:current-page="versionPage"
              :page-size="versionPageSize"
              :total="versionTotal"
              small
              layout="prev, pager, next"
              @current-change="loadVersions(false)"
            />
          </div>
        </aside>

        <main ref="comparePaneRef" class="compare-pane">
          <section class="compare-toolbar" aria-label="版本比较选项">
            <label>
              <span>基准版本</span>
              <el-select v-model="fromVersion" filterable allow-create default-first-option placeholder="选择或输入版本号">
                <el-option v-for="item in versionOptions" :key="item.versionNo" :label="versionOptionLabel(item)" :value="item.versionNo" />
              </el-select>
            </label>
            <el-button title="交换两个版本" aria-label="交换基准版本与对比版本" @click="swapVersions">
              <el-icon><Switch /></el-icon>
            </el-button>
            <label>
              <span>对比版本</span>
              <el-select v-model="toVersion" filterable allow-create default-first-option placeholder="选择或输入版本号">
                <el-option v-for="item in versionOptions" :key="item.versionNo" :label="versionOptionLabel(item)" :value="item.versionNo" />
              </el-select>
            </label>
            <el-button type="primary" :loading="compareLoading" :disabled="!canCompare" @click="loadComparison">
              比较
            </el-button>
            <small class="version-input-hint">下拉显示当前时间线页；也可直接输入 V+版本号，比较任意两个历史数据版本。</small>
          </section>

          <template v-if="comparison">
            <section ref="summaryRef" class="diff-summary" tabindex="-1" aria-live="polite">
              <div class="summary-heading">
                <div>
                  <strong>比较结果</strong>
                  <p>基准 V{{ fromVersion }} → 对比 V{{ toVersion }}</p>
                </div>
                <el-tag :type="comparison.compatibilityMode === 'FULL' ? 'success' : 'warning'" effect="plain">
                  {{ compatibilityText(comparison.compatibilityMode) }}
                </el-tag>
              </div>
              <div class="summary-grid">
                <div><strong>{{ comparison.summary.dataChangedCount }}</strong><span>字段变化</span></div>
                <div><strong>{{ comparison.summary.addedRowCount }}</strong><span>关联行新增</span></div>
                <div><strong>{{ comparison.summary.removedRowCount }}</strong><span>关联行删除</span></div>
                <div><strong>{{ comparison.summary.modifiedRowCount }}</strong><span>关联行修改</span></div>
                <div><strong>{{ comparison.summary.movedRowCount }}</strong><span>关联行移动</span></div>
                <div><strong>{{ comparison.summary.displayChangedCount + comparison.summary.schemaChangedCount }}</strong><span>展示 / 结构变化</span></div>
              </div>
            </section>

            <el-alert
              v-if="comparison.summary.scopeChanged"
              title="两个版本的固化范围不同，只比较双方都可靠采集的数据，不会把未采集的数据误报为新增或删除。"
              type="warning"
              show-icon
              :closable="false"
            />
            <el-alert
              v-for="warning in comparison.warnings"
              :key="warning"
              :title="warning"
              type="warning"
              show-icon
              :closable="false"
              class="comparison-warning"
            />

            <div class="diff-actions">
              <el-checkbox v-model="changedOnly" :disabled="compareLoading">仅看变化</el-checkbox>
              <el-checkbox v-model="showFieldCode">显示字段编码</el-checkbox>
              <el-button-group>
                <el-button :disabled="changeElements.length === 0" aria-label="上一处变化" @click="navigateChange(-1)">
                  <el-icon><ArrowUp /></el-icon>上一处
                </el-button>
                <el-button :disabled="changeElements.length === 0" aria-label="下一处变化" @click="navigateChange(1)">
                  下一处<el-icon><ArrowDown /></el-icon>
                </el-button>
              </el-button-group>
              <span class="change-position">{{ changeElements.length ? `${activeChangeIndex + 1} / ${changeElements.length}` : '没有变化' }}</span>
              <el-button text @click="expandAll = !expandAll">{{ expandAll ? '收起全部关联行' : '展开全部关联行' }}</el-button>
            </div>

            <section v-for="node in rootNodes" :key="node.nodeCode" class="root-diff">
              <header><strong>{{ node.newName || node.oldName || node.name || '主记录' }}</strong><el-tag type="primary">A</el-tag></header>
              <VersionDiffForm :sections="node.formSections" :changed-only="changedOnly" :show-code="showFieldCode" />
            </section>

            <VersionRelationDiff
              v-for="node in visibleRelationNodes"
              :key="node.nodeCode"
              :node="node"
              :changed-only="changedOnly"
              :show-code="showFieldCode"
              :expand-all="expandAll"
              @page-change="loadRelationPage"
            />

            <el-empty v-if="!rootNodes.length && !visibleRelationNodes.length" description="筛选条件下没有可显示的变化" />
          </template>
          <el-empty v-else description="选择基准版本和对比版本开始比较" />
        </main>
      </div>
    </div>

    <el-dialog v-model="snapshotVisible" append-to-body width="min(1000px, 96vw)" :title="snapshotTitle">
      <div v-loading="snapshotLoading" class="snapshot-dialog">
        <el-descriptions v-if="selectedDetail" :column="snapshotDescriptionColumns" border>
          <el-descriptions-item label="版本">V{{ selectedDetail.versionNo }}</el-descriptions-item>
          <el-descriptions-item label="生成时机">{{ selectedDetail.scenarioName || selectedDetail.triggerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="记录时间">{{ formatTime(selectedDetail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ selectedDetail.operatorName || selectedDetail.operatorId || '系统' }}</el-descriptions-item>
        </el-descriptions>
        <div class="snapshot-options"><el-checkbox v-model="showSnapshotFieldCode">显示字段编码</el-checkbox></div>
        <el-collapse v-model="snapshotOpenNodes">
          <el-collapse-item v-for="node in snapshotNodes" :key="node.nodeCode" :name="node.nodeCode">
            <template #title>
              <div class="snapshot-node-title">
                <strong>{{ node.name }}</strong>
                <el-tag effect="plain" size="small">{{ node.nodeKind === 'ROOT' ? '主记录' : `${node.rowPage?.total ?? node.rows?.length ?? 0} 条` }}</el-tag>
              </div>
            </template>
            <VersionSnapshotForm v-if="node.formSections?.length" :sections="node.formSections" :show-code="showSnapshotFieldCode" />
            <el-collapse v-if="node.rows?.length" class="snapshot-rows">
              <el-collapse-item v-for="row in node.rows" :key="row.recordId" :name="row.recordId">
                <template #title><span class="snapshot-row-title"><strong>{{ row.title }}</strong><small>{{ row.recordId }}</small></span></template>
                <VersionSnapshotForm :sections="row.formSections" :show-code="showSnapshotFieldCode" />
              </el-collapse-item>
            </el-collapse>
            <el-pagination
              v-if="node.rowPage?.total > node.rowPage?.pageSize"
              :current-page="node.rowPage.pageNum"
              :page-size="node.rowPage.pageSize"
              :total="node.rowPage.total"
              layout="total, prev, pager, next"
              @current-change="loadSnapshotRelationPage(node, $event)"
            />
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-dialog>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ArrowDown, ArrowUp, Refresh, Switch } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { entityVersionApi } from '@/api/entityVersion'
import { useUserStore } from '@/stores/user'
import {
  normalizeComparison,
  normalizePage,
  normalizeSnapshot
} from '@/shared/entity-version-model'
import VersionDiffForm from './version/VersionDiffForm.vue'
import VersionRelationDiff from './version/VersionRelationDiff.vue'
import VersionSnapshotForm from './version/VersionSnapshotForm.vue'

const props = defineProps<{ entityCode: string }>()
const userStore = useUserStore()
const visible = ref(false)
const loading = ref(false)
const compareLoading = ref(false)
const captureLoading = ref(false)
const snapshotLoading = ref(false)
const snapshotVisible = ref(false)
const record = ref<any>(null)
const versions = ref<any[]>([])
const versionPage = ref(1)
const versionPageSize = 20
const versionTotal = ref(0)
const fromVersion = ref<number | null>(null)
const toVersion = ref<number | null>(null)
const comparison = ref<any>(null)
const changedOnly = ref(true)
const showFieldCode = ref(false)
const showSnapshotFieldCode = ref(false)
const expandAll = ref(false)
const timelineCollapsed = ref(false)
const selectedDetail = ref<any>(null)
const snapshotOpenNodes = ref<string[]>([])
const drawerBodyRef = ref<HTMLElement | null>(null)
const comparePaneRef = ref<HTMLElement | null>(null)
const summaryRef = ref<HTMLElement | null>(null)
const activeChangeIndex = ref(0)
const changeElements = ref<HTMLElement[]>([])
const viewportWidth = ref(typeof window === 'undefined' ? 1280 : window.innerWidth)
let triggerElement: Element | null = null

const drawerSize = computed(() => viewportWidth.value < 768 ? '100%' : '88%')
const snapshotDescriptionColumns = computed(() => viewportWidth.value < 768 ? 1 : 4)
const recordTitle = computed(() => record.value?.name || record.value?.title || record.value?.code || record.value?.dataNo || record.value?.id || '业务数据')
const versionOptions = computed(() => [...versions.value].sort((a, b) => Number(a.versionNo) - Number(b.versionNo)))
const rootNodes = computed(() => (comparison.value?.nodes || []).filter((node: any) => node.nodeKind === 'ROOT'))
const relationNodes = computed(() => (comparison.value?.nodes || []).filter((node: any) => node.nodeKind !== 'ROOT'))
const visibleRelationNodes = computed(() => relationNodes.value.filter((node: any) => {
  if (!changedOnly.value) return true
  const counts = node.counts || {}
  const rowChanged = Number(counts.added || 0) + Number(counts.removed || 0) + Number(counts.modified || 0) + Number(counts.moved || 0) > 0
  const fieldChanged = (node.formSections || []).some((section: any) => (section.fields || []).some((field: any) => field.changeType !== 'UNCHANGED'))
  return rowChanged || fieldChanged
}))
const snapshotNodes = computed(() => selectedDetail.value?.nodes || [])
const snapshotTitle = computed(() => selectedDetail.value ? `V${selectedDetail.value.versionNo} ${selectedDetail.value.scenarioName || selectedDetail.value.triggerName || '版本快照'}` : '版本快照')
const canCapture = computed(() => hasPermission('entity:version:record:capture'))
const canCompare = computed(() => {
  const from = parseVersionNo(fromVersion.value)
  const to = parseVersionNo(toVersion.value)
  return from != null && to != null && from !== to
})

watch(comparison, refreshChangeElements, { deep: true })
watch(changedOnly, async () => {
  if (compareLoading.value || !comparison.value) {
    refreshChangeElements()
    return
  }
  compareLoading.value = true
  try {
    await Promise.allSettled(relationNodes.value.map((node: any) =>
      loadRelationPage(node, 1, true)))
    activeChangeIndex.value = 0
    await refreshChangeElements()
  } finally {
    compareLoading.value = false
  }
})

onMounted(() => window.addEventListener('resize', updateViewport))
onBeforeUnmount(() => window.removeEventListener('resize', updateViewport))

async function open(rowValue: any) {
  triggerElement = document.activeElement
  record.value = rowValue
  visible.value = true
  versionPage.value = 1
  comparison.value = null
  await loadVersions(true)
}

async function loadVersions(resetComparison = true) {
  if (!props.entityCode || !record.value?.id) return
  loading.value = true
  try {
    const page = normalizePage(await entityVersionApi.recordVersions(props.entityCode, record.value.id, {
      pageNum: versionPage.value,
      pageSize: versionPageSize
    }), versionPageSize)
    versions.value = [...page.records].sort((a, b) => Number(b.versionNo) - Number(a.versionNo))
    versionTotal.value = page.total
    if (resetComparison) {
      const newest = versions.value[0]
      const previous = versions.value[1]
      fromVersion.value = previous?.versionNo ?? newest?.versionNo ?? null
      toVersion.value = newest?.versionNo ?? null
      comparison.value = null
      if (previous && newest) await loadComparison()
    }
  } catch (error: any) {
    ElMessage.error(error.message || '加载数据版本失败')
  } finally {
    loading.value = false
  }
}

async function loadComparison() {
  const normalizedFrom = parseVersionNo(fromVersion.value)
  const normalizedTo = parseVersionNo(toVersion.value)
  if (normalizedFrom == null || normalizedTo == null) {
    ElMessage.warning('请输入有效的正整数版本号')
    return
  }
  if (normalizedFrom === normalizedTo) {
    ElMessage.warning('请选择两个不同的数据版本')
    return
  }
  if (!record.value?.id) return
  fromVersion.value = normalizedFrom
  toVersion.value = normalizedTo
  compareLoading.value = true
  try {
    comparison.value = normalizeComparison(await entityVersionApi.compareRecordVersions(
      props.entityCode, record.value.id, fromVersion.value, toVersion.value,
      { rowPageNum: 1, rowPageSize: 20 }
    ))
    changedOnly.value = comparison.value?.diffPolicy?.changedOnlyDefault !== false
    const relationFirstPages = comparison.value.nodes
      .filter((node: any) => node.nodeKind !== 'ROOT' && Number(node.counts?.total || 0) > 0)
      .map((node: any) => loadRelationPage(node, 1, true))
    await Promise.allSettled(relationFirstPages)
    activeChangeIndex.value = 0
    await nextTick()
    summaryRef.value?.focus()
    refreshChangeElements()
  } catch (error: any) {
    ElMessage.error(error.message || '版本比较失败')
  } finally {
    compareLoading.value = false
  }
}

async function loadRelationPage(node: any, pageNum: number, silent = false) {
  try {
    const page = normalizePage(await entityVersionApi.comparisonRows(
      props.entityCode, record.value.id, fromVersion.value, toVersion.value,
      node.nodeCode, {
        pageNum,
        pageSize: node.rowPage?.pageSize || 20,
        changedOnly: changedOnly.value
      }
    ), 20)
    const normalized = normalizeComparison({ nodes: [{ ...node, rowChanges: page, rowChangeCounts: page.counts || node.counts }] }).nodes[0]
    const index = comparison.value.nodes.findIndex((item: any) => item.nodeCode === node.nodeCode)
    if (index >= 0) comparison.value.nodes.splice(index, 1, normalized)
  } catch (error: any) {
    if (!silent) ElMessage.error(error.message || '加载关联行差异失败')
  }
}

async function openSnapshot(item: any) {
  snapshotVisible.value = true
  snapshotLoading.value = true
  selectedDetail.value = null
  try {
    selectedDetail.value = normalizeSnapshot(await entityVersionApi.recordVersion(props.entityCode, record.value.id, item.versionNo))
    await Promise.allSettled(selectedDetail.value.nodes
      .filter((node: any) => node.nodeKind !== 'ROOT' && Number(node.rowPage?.total || 0) > 0)
      .map((node: any) => loadSnapshotRelationPage(node, 1, true)))
    snapshotOpenNodes.value = selectedDetail.value.nodes.map((node: any) => node.nodeCode)
  } catch (error: any) {
    ElMessage.error(error.message || '加载版本快照失败')
  } finally {
    snapshotLoading.value = false
  }
}

async function loadSnapshotRelationPage(node: any, pageNum: number, silent = false) {
  try {
    const page = normalizePage(await entityVersionApi.snapshotRows(
      props.entityCode, record.value.id, selectedDetail.value.versionNo,
      node.nodeCode, { pageNum, pageSize: node.rowPage?.pageSize || 20 }
    ), 20)
    const normalized = normalizeSnapshot({ nodes: [{ ...node, rows: page }] }).nodes[0]
    const index = selectedDetail.value.nodes.findIndex((item: any) => item.nodeCode === node.nodeCode)
    if (index >= 0) selectedDetail.value.nodes.splice(index, 1, normalized)
  } catch (error: any) {
    if (!silent) ElMessage.error(error.message || '加载关联快照失败')
  }
}

async function captureNow() {
  try {
    const { value } = await ElMessageBox.prompt('请填写本次手工固化的原因，版本数据将从服务端当前记录读取。', '立即固化当前数据', {
      inputPlaceholder: '例如：合同签署前检查点',
      inputValidator: input => Boolean(String(input || '').trim()) || '请填写固化原因',
      confirmButtonText: '生成版本',
      cancelButtonText: '取消'
    })
    captureLoading.value = true
    const idempotencyKey = globalThis.crypto?.randomUUID?.() || `manual-${Date.now()}-${Math.random().toString(16).slice(2)}`
    await entityVersionApi.captureRecordVersion(props.entityCode, record.value.id, {
      triggerType: 'MANUAL', reason: String(value).trim()
    }, idempotencyKey)
    ElMessage.success('当前数据已固化为新版本')
    versionPage.value = 1
    await loadVersions(true)
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    if (error?.message && !String(error.message).includes('cancel')) ElMessage.error(error.message || '手工固化失败')
  } finally {
    captureLoading.value = false
  }
}

function swapVersions() {
  const current = fromVersion.value
  fromVersion.value = toVersion.value
  toVersion.value = current
}

async function refreshChangeElements() {
  await nextTick()
  changeElements.value = Array.from(comparePaneRef.value?.querySelectorAll<HTMLElement>('[data-diff-change="true"]') || [])
  if (activeChangeIndex.value >= changeElements.value.length) activeChangeIndex.value = 0
}

function navigateChange(offset: number) {
  if (!changeElements.value.length) return
  activeChangeIndex.value = (activeChangeIndex.value + offset + changeElements.value.length) % changeElements.value.length
  const target = changeElements.value[activeChangeIndex.value]
  target.focus({ preventScroll: true })
  target.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function restoreTriggerFocus() {
  if (triggerElement instanceof HTMLElement) triggerElement.focus()
  triggerElement = null
}
function updateViewport() { viewportWidth.value = window.innerWidth }
function hasPermission(permission: string) { return userStore.isSuperAdmin || userStore.permissions.includes('*') || userStore.permissions.includes(permission) }
function versionOptionLabel(item: any) { return `V${item.versionNo} ${item.scenarioName || item.triggerName || item.versionTitle || ''}`.trim() }
function parseVersionNo(value: unknown) {
  const normalized = String(value ?? '').trim().replace(/^v/i, '')
  if (!/^\d+$/.test(normalized)) return null
  const parsed = Number(normalized)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}
function compatibilityText(value: string) { return ({ FULL: '完整比较', PARTIAL: '部分可靠比较', LEGACY: '历史兼容比较' } as any)[value] || value }
function formatTime(value: any) { return value ? String(value).replace('T', ' ').slice(0, 19) : '-' }
function sourceText(value: string) { return ({ FORM: '表单', LIST: '列表', APPROVAL_TASK: '审批', PROCESS_RUNTIME: '流程运行态', FLOW_ACTION: '流程动作', CUSTOM_INTERFACE: '自定义接口', BATCH: '批量', IMPORT: '导入', SCHEDULED_JOB: '定时任务', MESSAGE_CONSUMER: '消息消费', SYSTEM_TASK: '系统任务', MANUAL: '手工固化' } as any)[value] || value || '-' }
function operationText(value: string) { return ({ CREATE: '新增', UPDATE: '修改', DELETE: '删除', STATUS_CHANGE: '状态变化', APPLY_CHANGE: '变更生效', UPSERT: '新增或修改' } as any)[value] || value || '-' }

defineExpose({ open })
</script>

<style scoped lang="scss">
.drawer-heading, .drawer-heading__actions, .pane-heading, .pane-heading > div, .version-title, .version-meta, .compare-toolbar, .diff-actions, .summary-heading, .snapshot-node-title { display: flex; align-items: center; }
.drawer-heading, .pane-heading, .summary-heading { justify-content: space-between; }
.drawer-heading { width: 100%; padding-right: 18px; gap: 16px; }
.drawer-heading h3 { margin: 0 0 4px; }
.drawer-heading span, .pane-heading span { color: var(--el-text-color-secondary); font-size: 13px; }
.drawer-heading__actions, .pane-heading > div { gap: 8px; }
.version-body { min-height: 360px; }
.version-layout { display: grid; grid-template-columns: minmax(280px, 31%) minmax(0, 1fr); min-height: calc(100vh - 150px); }
.timeline-pane { padding: 0 20px 20px 4px; border-right: 1px solid var(--el-border-color-light); }
.timeline-pane.is-collapsed { display: none; }
.timeline-pane.is-collapsed + .compare-pane { grid-column: 1 / -1; padding-left: 4px; }
.compare-pane { min-width: 0; padding: 0 0 20px 20px; }
.pane-heading { min-height: 38px; margin-bottom: 14px; }
.timeline-toggle { display: none; }
.version-item { padding: 10px 12px; border: 1px solid var(--el-border-color-light); border-radius: 7px; }
.version-title, .version-meta { flex-wrap: wrap; gap: 8px; }
.version-meta { margin-top: 7px; color: var(--el-text-color-secondary); font-size: 12px; }
.version-item > .el-button { margin-top: 6px; padding-left: 0; }
.timeline-pagination { justify-content: center; }
.compare-toolbar { align-items: flex-end; flex-wrap: wrap; gap: 9px; margin-bottom: 14px; padding: 12px; background: var(--el-fill-color-lighter); border-radius: 8px; }
.compare-toolbar label { display: flex; flex: 1; flex-direction: column; gap: 5px; min-width: 190px; font-size: 13px; font-weight: 600; }
.version-input-hint { flex-basis: 100%; color: var(--el-text-color-secondary); font-weight: 400; }
.diff-summary { margin-bottom: 14px; padding: 14px; border: 1px solid var(--el-border-color); border-radius: 10px; outline: none; }
.diff-summary:focus { box-shadow: 0 0 0 2px var(--el-color-primary-light-5); }
.summary-heading p { margin: 4px 0 0; color: var(--el-text-color-secondary); font-size: 13px; }
.summary-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 8px; margin-top: 12px; }
.summary-grid > div { display: flex; flex-direction: column; padding: 10px; text-align: center; background: var(--el-fill-color-lighter); border-radius: 7px; }
.summary-grid strong { font-size: 22px; color: var(--el-color-primary); }
.summary-grid span { margin-top: 3px; color: var(--el-text-color-secondary); font-size: 12px; }
.comparison-warning { margin-top: 8px; }
.diff-actions { flex-wrap: wrap; gap: 10px; margin: 14px 0; position: sticky; top: -1px; z-index: 3; padding: 9px 10px; background: color-mix(in srgb, var(--el-bg-color) 94%, transparent); border: 1px solid var(--el-border-color-light); border-radius: 8px; backdrop-filter: blur(5px); }
.change-position { min-width: 54px; color: var(--el-text-color-secondary); font-size: 12px; text-align: center; }
.root-diff { margin-bottom: 18px; }
.root-diff > header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 9px; padding: 0 4px; font-size: 16px; }
.snapshot-options { display: flex; justify-content: flex-end; margin: 12px 0; }
.snapshot-node-title { width: 100%; justify-content: space-between; padding-right: 12px; }
.snapshot-rows { margin: 0 4px 14px; }
.snapshot-row-title { display: flex; flex-direction: column; align-items: flex-start; }
.snapshot-row-title small { color: var(--el-text-color-secondary); font-family: monospace; }

@media (max-width: 1199px) {
  .version-layout { display: block; }
  .timeline-pane { border-right: 0; border-bottom: 1px solid var(--el-border-color-light); padding-right: 0; }
  .timeline-content { max-height: 330px; overflow-y: auto; }
  .timeline-toggle { display: inline-flex; }
  .compare-pane { padding: 20px 0 0; }
  .summary-grid { grid-template-columns: repeat(3, 1fr); }
}

@media (max-width: 767px) {
  .drawer-heading { align-items: flex-start; flex-direction: column; }
  .drawer-heading__actions { width: 100%; }
  .compare-toolbar { align-items: stretch; flex-direction: column; }
  .compare-toolbar label { width: 100%; }
  .compare-toolbar > .el-button { width: 100%; }
  .summary-grid { grid-template-columns: repeat(2, 1fr); }
  .diff-actions { position: static; }
}
</style>
