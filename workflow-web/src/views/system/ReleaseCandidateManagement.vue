<template>
  <div class="candidate-page">
    <header class="command-header">
      <div>
        <span class="kicker">APPLICATION RELEASE CONTROL</span>
        <h2>应用级发布候选</h2>
        <p>把配置迁移资产冻结为可预检、可排序、可续跑、可补偿的发布单元。</p>
      </div>
      <div class="header-actions">
        <el-button :loading="loadingList" @click="loadCandidates">刷新</el-button>
        <el-button type="primary" :disabled="!canCreate" @click="openCreate">创建候选</el-button>
      </div>
    </header>

    <section class="workspace">
      <aside class="candidate-rail">
        <div class="rail-title">
          <span>候选队列</span>
          <el-tag effect="plain">{{ candidates.length }}</el-tag>
        </div>
        <el-scrollbar height="calc(100vh - 245px)">
          <button
            v-for="candidate in candidates"
            :key="candidate.id"
            class="candidate-card"
            :class="{ active: detail?.id === candidate.id }"
            @click="selectCandidate(candidate.id)"
          >
            <span class="candidate-card__top">
              <strong>{{ candidate.candidateName }}</strong>
              <el-tag size="small" :type="statusType(candidate.status)">{{ statusText(candidate.status) }}</el-tag>
            </span>
            <span class="candidate-no">{{ candidate.candidateNo }}</span>
            <span class="candidate-meta">{{ candidate.migrationTag }} · r{{ candidate.revision }}</span>
          </button>
          <el-empty v-if="!candidates.length && !loadingList" description="暂无发布候选" :image-size="72" />
        </el-scrollbar>
      </aside>

      <main v-loading="loadingDetail" class="detail-stage">
        <template v-if="detail">
          <div class="detail-heading">
            <div>
              <div class="detail-title-line">
                <h3>{{ detail.candidateName }}</h3>
                <el-tag :type="statusType(detail.status)">{{ statusText(detail.status) }}</el-tag>
                <el-tag :type="detail.preflightStatus === 'PASS' ? 'success' : detail.preflightStatus === 'FAIL' ? 'danger' : 'info'" effect="plain">
                  预检 {{ detail.preflightStatus }}
                </el-tag>
              </div>
              <p>{{ detail.description || '未填写说明' }}</p>
              <div class="fingerprints">
                <span>迁移标记 <b>{{ detail.migrationTag }}</b></span>
                <span>修订 <b>r{{ detail.revision }}</b></span>
                <span title="冻结候选哈希">SHA <b>{{ shortHash(detail.candidateHash) }}</b></span>
              </div>
            </div>
            <div class="detail-actions">
              <el-button :disabled="!canCreate || running" @click="runPreflight">统一预检</el-button>
              <el-button v-if="detail.status === 'READY'" type="primary" :disabled="!canPublish || running" @click="publishCandidate">执行发布</el-button>
              <el-button v-if="detail.status === 'FAILED'" type="warning" :disabled="!canRecover || running" @click="resumeCandidate">从失败处续跑</el-button>
              <el-button v-if="['PUBLISHED', 'FAILED', 'MANUAL_REQUIRED'].includes(detail.status)" type="danger" plain :disabled="!canRecover || running" @click="compensateCandidate">补偿 / 回滚</el-button>
              <el-button @click="downloadReport">下载报告</el-button>
            </div>
          </div>

          <div class="health-grid">
            <article><span>资产条目</span><strong>{{ detail.items?.length || 0 }}</strong></article>
            <article><span>硬依赖</span><strong>{{ hardDependencies }}</strong></article>
            <article :class="{ alert: blockerCount }"><span>阻断项</span><strong>{{ blockerCount }}</strong></article>
            <article><span>执行进度</span><strong>{{ completedSteps }}/{{ detail.steps?.length || 0 }}</strong></article>
          </div>

          <el-tabs v-model="activeTab" class="detail-tabs">
            <el-tab-pane label="资产冻结" name="assets">
              <el-table :data="detail.items || []" border stripe>
                <el-table-column label="资产" min-width="240">
                  <template #default="{ row }"><strong>{{ row.assetName || row.businessKey }}</strong><div class="muted">{{ row.assetType }} · {{ row.businessKey }}</div></template>
                </el-table-column>
                <el-table-column label="冻结版本" width="110" align="center"><template #default="{ row }">v{{ row.frozenSourceVersion ?? '-' }}</template></el-table-column>
                <el-table-column label="源哈希" min-width="155"><template #default="{ row }"><code>{{ shortHash(row.frozenSourceHash) }}</code></template></el-table-column>
                <el-table-column label="快照哈希" min-width="155"><template #default="{ row }"><code>{{ shortHash(row.frozenSnapshotHash) }}</code></template></el-table-column>
                <el-table-column label="迁移资产索引" min-width="170"><template #default="{ row }"><code>{{ row.sourceAssetId || '未索引' }}</code></template></el-table-column>
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="依赖图" name="dependencies">
              <div v-if="detail.dependencies?.length" class="dependency-map">
                <article v-for="edge in detail.dependencies" :key="edge.id" :class="{ unresolved: !edge.resolved }">
                  <div><span class="node-label">{{ itemLabel(edge.dependentItemId) }}</span><small>依赖方</small></div>
                  <div class="edge-arrow"><b>{{ edge.required ? 'HARD' : 'SOFT' }}</b><span>→</span></div>
                  <div><span class="node-label">{{ dependencyLabel(edge) }}</span><small>{{ edge.resolved ? '已解析' : '缺失' }}</small></div>
                </article>
              </div>
              <el-empty v-else description="预检后显示资产依赖边" />
            </el-tab-pane>

            <el-tab-pane :label="`校验结果 ${detail.validations?.length || 0}`" name="validations">
              <div class="validation-list">
                <article v-for="item in detail.validations || []" :key="item.id" :class="item.severity.toLowerCase()">
                  <el-tag :type="validationType(item.severity)" size="small">{{ item.severity }}</el-tag>
                  <div><strong>{{ item.validationCode }}</strong><p>{{ item.message }}</p></div>
                </article>
                <el-empty v-if="!detail.validations?.length" description="尚未执行统一预检" />
              </div>
            </el-tab-pane>

            <el-tab-pane :label="`执行计划 ${detail.steps?.length || 0}`" name="steps">
              <el-table :data="detail.steps || []" border>
                <el-table-column prop="stepNo" label="#" width="58" align="center" />
                <el-table-column label="确定性步骤" min-width="260"><template #default="{ row }"><strong>{{ stepText(row.stepType) }}</strong><div class="muted">{{ row.stepKey }}</div></template></el-table-column>
                <el-table-column label="状态" width="130"><template #default="{ row }"><el-tag :type="stepStatusType(row.status)">{{ stepStatusText(row.status) }}</el-tag></template></el-table-column>
                <el-table-column prop="operator" label="操作者" min-width="120" />
                <el-table-column label="耗时" width="100"><template #default="{ row }">{{ row.durationMs == null ? '-' : `${row.durationMs} ms` }}</template></el-table-column>
                <el-table-column label="失败 / 恢复" min-width="260"><template #default="{ row }"><span class="error-text">{{ row.errorMessage }}</span><div class="muted">{{ row.recoveryAction }}</div></template></el-table-column>
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>
        <el-empty v-else description="从左侧选择一个发布候选" />
      </main>
    </section>

    <el-dialog v-model="createVisible" title="创建发布候选" width="780px" destroy-on-close>
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="候选名称" required><el-input v-model="createForm.candidateName" maxlength="200" /></el-form-item>
          <el-form-item label="来源导入批次" required>
            <el-select v-model="createForm.sourceImportId" filterable placeholder="选择已上传并分析的 wfpack" @change="sourceChanged">
              <el-option v-for="source in sources" :key="source.id" :value="source.id" :label="`${source.migrationTag} · ${source.packageNo} · ${source.status}`" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="说明"><el-input v-model="createForm.description" type="textarea" :rows="2" maxlength="1000" /></el-form-item>
        <el-alert type="info" :closable="false" title="当前发布器按导入批次执行。可以保存不完整草稿，但统一预检会阻断缺少批次条目的候选。" />
        <el-table ref="sourceTableRef" class="source-table" :data="selectedSource?.items || []" row-key="id" border @selection-change="sourceSelection = $event">
          <el-table-column type="selection" width="48" />
          <el-table-column label="资产" min-width="240"><template #default="{ row }"><strong>{{ row.assetName || row.businessKey }}</strong><div class="muted">{{ row.assetType }} · {{ row.businessKey }}</div></template></el-table-column>
          <el-table-column prop="sourceVersion" label="版本" width="80" />
          <el-table-column label="映射" width="105"><template #default="{ row }"><el-tag :type="row.mappingStatus === 'RESOLVED' ? 'success' : 'danger'" size="small">{{ row.mappingStatus }}</el-tag></template></el-table-column>
          <el-table-column label="比较" width="130"><template #default="{ row }">{{ row.comparisonStatus }}</template></el-table-column>
        </el-table>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :loading="creating" @click="createCandidate">冻结候选</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { releaseCandidateApi } from '@/api/releaseCandidate'

const userStore = useUserStore()
const candidates = ref([])
const detail = ref(null)
const sources = ref([])
const sourceSelection = ref([])
const sourceTableRef = ref()
const loadingList = ref(false)
const loadingDetail = ref(false)
const running = ref(false)
const creating = ref(false)
const createVisible = ref(false)
const activeTab = ref('assets')
const createForm = reactive({ candidateName: '', description: '', sourceImportId: '' })

const hasPermission = permission => userStore.isSuperAdmin || userStore.permissions.includes('*') || userStore.permissions.includes(permission)
const canCreate = computed(() => hasPermission('release-candidate:create'))
const canPublish = computed(() => hasPermission('release-candidate:publish'))
const canRecover = computed(() => hasPermission('release-candidate:recover'))
const selectedSource = computed(() => sources.value.find(source => source.id === createForm.sourceImportId))
const blockerCount = computed(() => detail.value?.validations?.filter(item => item.severity === 'BLOCKER').length || 0)
const hardDependencies = computed(() => detail.value?.dependencies?.filter(item => item.required).length || 0)
const completedSteps = computed(() => detail.value?.steps?.filter(item => ['COMPLETED', 'COMPENSATED'].includes(item.status)).length || 0)

function unwrap(response) {
  const body = response?.data ?? response
  return body?.data ?? body
}

async function loadCandidates(preferredId) {
  loadingList.value = true
  try {
    candidates.value = unwrap(await releaseCandidateApi.list()) || []
    const id = preferredId || detail.value?.id || candidates.value[0]?.id
    if (id) await selectCandidate(id)
  } finally {
    loadingList.value = false
  }
}

async function selectCandidate(id) {
  loadingDetail.value = true
  try {
    detail.value = unwrap(await releaseCandidateApi.detail(id))
  } finally {
    loadingDetail.value = false
  }
}

async function openCreate() {
  createForm.candidateName = ''
  createForm.description = ''
  createForm.sourceImportId = ''
  sourceSelection.value = []
  sources.value = unwrap(await releaseCandidateApi.sources()) || []
  createVisible.value = true
}

async function sourceChanged() {
  sourceSelection.value = []
  await nextTick()
  selectedSource.value?.items?.forEach(item => sourceTableRef.value?.toggleRowSelection(item, true))
}

async function createCandidate() {
  if (!createForm.candidateName.trim() || !createForm.sourceImportId || !sourceSelection.value.length) {
    ElMessage.warning('请填写候选名称、选择来源批次并至少选择一个资产')
    return
  }
  creating.value = true
  try {
    const created = unwrap(await releaseCandidateApi.create({
      candidateName: createForm.candidateName.trim(),
      description: createForm.description.trim(),
      sourceImportId: createForm.sourceImportId,
      itemIds: sourceSelection.value.map(item => item.id)
    }))
    createVisible.value = false
    ElMessage.success('发布候选已冻结，请执行统一预检')
    await loadCandidates(created.id)
  } finally {
    creating.value = false
  }
}

async function runPreflight() {
  running.value = true
  try {
    detail.value = unwrap(await releaseCandidateApi.preflight(detail.value.id, detail.value.revision))
    ElMessage[detail.value.preflightStatus === 'PASS' ? 'success' : 'warning'](
      detail.value.preflightStatus === 'PASS' ? '统一预检通过' : '预检发现阻断项，请查看校验结果'
    )
    await refreshSummary()
    activeTab.value = detail.value.preflightStatus === 'PASS' ? 'steps' : 'validations'
  } finally {
    running.value = false
  }
}

async function publishCandidate() {
  const confirmation = await ElMessageBox.prompt(
    `发布将真实应用迁移批次。请输入候选编号「${detail.value.candidateNo}」确认。`,
    '执行应用级发布',
    { type: 'warning', inputValidator: value => value === detail.value.candidateNo || '候选编号不匹配' }
  )
  if (confirmation.value !== detail.value.candidateNo) return
  await executeCandidate('publish')
}

async function resumeCandidate() {
  await ElMessageBox.confirm('续跑前将再次核对修订号和冻结哈希，并从失败步骤继续。', '续跑发布候选', { type: 'warning' })
  await executeCandidate('resume')
}

async function executeCandidate(action) {
  running.value = true
  try {
    const request = {
      expectedRevision: detail.value.revision,
      candidateHash: detail.value.candidateHash,
      idempotencyKey: `${detail.value.candidateNo}:${crypto.randomUUID()}`
    }
    detail.value = unwrap(await releaseCandidateApi[action](detail.value.id, request))
    ElMessage.success(action === 'publish' ? '发布候选执行完成' : '发布候选续跑完成')
    await refreshSummary()
    activeTab.value = 'steps'
  } finally {
    running.value = false
  }
}

async function compensateCandidate() {
  const result = await ElMessageBox.prompt('请填写补偿原因（至少 5 个字符）。自动回滚不可用时会转为人工恢复。', '补偿发布候选', { type: 'warning', inputType: 'textarea', inputValidator: value => value?.trim().length >= 5 || '至少填写 5 个字符' })
  running.value = true
  try {
    detail.value = unwrap(await releaseCandidateApi.compensate(detail.value.id, result.value.trim()))
    ElMessage.success('补偿执行完成')
    await refreshSummary()
    activeTab.value = 'steps'
  } finally {
    running.value = false
  }
}

async function downloadReport() {
  const blob = await releaseCandidateApi.report(detail.value.id)
  const data = blob instanceof Blob ? blob : blob?.data instanceof Blob ? blob.data : new Blob([blob], { type: 'application/json' })
  const url = URL.createObjectURL(data)
  const link = document.createElement('a')
  link.href = url
  link.download = `RPT-${detail.value.candidateNo}.json`
  link.click()
  URL.revokeObjectURL(url)
}

async function refreshSummary() {
  const current = detail.value
  candidates.value = unwrap(await releaseCandidateApi.list()) || []
  detail.value = current
}

function itemLabel(itemId) {
  const item = detail.value?.items?.find(value => value.id === itemId)
  return item ? `${item.assetType}:${item.businessKey}` : itemId || '外部对象'
}
function dependencyLabel(edge) {
  return edge.requiredItemId ? itemLabel(edge.requiredItemId) : `${edge.dependencyType}:${edge.dependencyKey}`
}
function shortHash(value) {
  return value ? `${value.slice(0, 10)}…${value.slice(-6)}` : '-'
}
function statusText(value) {
  return { DRAFT: '草稿', BLOCKED: '阻断', READY: '待发布', PUBLISHING: '发布中', PUBLISHED: '已发布', FAILED: '失败', COMPENSATING: '补偿中', COMPENSATED: '已补偿', MANUAL_REQUIRED: '需人工恢复' }[value] || value
}
function statusType(value) {
  return { DRAFT: 'info', BLOCKED: 'danger', READY: 'warning', PUBLISHING: 'primary', PUBLISHED: 'success', FAILED: 'danger', COMPENSATING: 'warning', COMPENSATED: 'info', MANUAL_REQUIRED: 'danger' }[value] || 'info'
}
function validationType(value) {
  return { BLOCKER: 'danger', WARNING: 'warning', INFO: 'info' }[value] || 'info'
}
function stepStatusText(value) {
  return { NOT_EXECUTED: '未执行', RUNNING: '执行中', COMPLETED: '已完成', FAILED: '失败', COMPENSATED: '已补偿', MANUAL_REQUIRED: '需人工' }[value] || value
}
function stepStatusType(value) {
  return { NOT_EXECUTED: 'info', RUNNING: 'primary', COMPLETED: 'success', FAILED: 'danger', COMPENSATED: 'warning', MANUAL_REQUIRED: 'danger' }[value] || 'info'
}
function stepText(value) {
  return { VERIFY_ENTITY_SCHEMA: '校验实体结构', VERIFY_ENTITY_CONFIG: '校验实体配置', VERIFY_FORM_LIST: '校验表单与列表', VERIFY_DATA_SCOPE: '校验数据范围', VERIFY_PROCESS: '校验流程', VERIFY_MENU_PERMISSION: '校验菜单权限', VERIFY_EXTERNAL_DEPENDENCY: '校验外部依赖', DEPLOY_IMPORT_PACKAGE: '应用迁移批次', FINALIZE_REPORT: '固化发布报告', ROLLBACK_IMPORT_PACKAGE: '回滚迁移批次' }[value] || value
}

onMounted(loadCandidates)
</script>

<style scoped>
.candidate-page { min-height: 100%; padding: 24px; color: #202d2c; background: linear-gradient(135deg, #eef0e8 0%, #f7f3e8 48%, #e8efec 100%); }
.command-header { display: flex; justify-content: space-between; align-items: end; gap: 24px; padding: 28px 32px; border-radius: 20px; color: #f6f4e9; background: linear-gradient(118deg, #143f3a, #1f5b50 58%, #99672f); box-shadow: 0 18px 50px rgba(20, 63, 58, .2); }
.kicker { color: #e6bc70; font: 700 11px/1.4 'Avenir Next', sans-serif; letter-spacing: .18em; }
h2 { margin: 7px 0 8px; font: 700 31px/1.15 'Noto Serif SC', serif; }
.command-header p { margin: 0; color: rgba(246, 244, 233, .74); }
.header-actions, .detail-actions { display: flex; flex-wrap: wrap; gap: 9px; }
.workspace { display: grid; grid-template-columns: 290px minmax(0, 1fr); gap: 16px; margin-top: 16px; }
.candidate-rail, .detail-stage { border: 1px solid rgba(22, 68, 61, .13); border-radius: 18px; background: rgba(255, 255, 252, .94); box-shadow: 0 14px 38px rgba(31, 67, 60, .08); }
.candidate-rail { padding: 14px; }
.rail-title { display: flex; justify-content: space-between; align-items: center; padding: 4px 4px 12px; font-weight: 700; }
.candidate-card { display: block; width: calc(100% - 4px); margin: 0 2px 9px; padding: 14px; border: 1px solid #dfe5df; border-radius: 13px; color: inherit; text-align: left; background: #fbfbf7; cursor: pointer; transition: transform .16s ease, border-color .16s ease, background .16s ease; }
.candidate-card:hover { transform: translateY(-1px); border-color: #6c9a8e; }
.candidate-card.active { border-color: #266a5b; background: #edf5f1; box-shadow: inset 4px 0 #c18a42; }
.candidate-card__top { display: flex; justify-content: space-between; gap: 8px; align-items: start; }
.candidate-card__top strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.candidate-no, .candidate-meta { display: block; margin-top: 7px; color: #74827f; font: 12px/1.35 'Avenir Next', sans-serif; }
.detail-stage { min-height: calc(100vh - 205px); padding: 22px; }
.detail-heading { display: flex; justify-content: space-between; align-items: start; gap: 22px; padding-bottom: 18px; border-bottom: 1px solid #e4e7df; }
.detail-title-line { display: flex; align-items: center; flex-wrap: wrap; gap: 9px; }
h3 { margin: 0; font: 700 25px/1.2 'Noto Serif SC', serif; }
.detail-heading p { margin: 7px 0; color: #75817e; }
.fingerprints { display: flex; flex-wrap: wrap; gap: 8px 16px; color: #66736f; font-size: 12px; }
.fingerprints b, code { color: #28594e; font-family: 'SFMono-Regular', Consolas, monospace; }
.health-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin: 18px 0 8px; }
.health-grid article { display: flex; justify-content: space-between; align-items: end; padding: 15px 17px; border-radius: 12px; background: #f0f4ef; }
.health-grid span { color: #77837f; font-size: 12px; }
.health-grid strong { font: 700 24px/1 'Avenir Next', sans-serif; }
.health-grid article.alert { color: #aa3c2e; background: #fff0ec; }
.detail-tabs { margin-top: 10px; }
.muted { margin-top: 4px; color: #82908c; font-size: 12px; }
.dependency-map { display: grid; gap: 10px; }
.dependency-map article { display: grid; grid-template-columns: minmax(160px, 1fr) 100px minmax(160px, 1fr); align-items: center; gap: 12px; padding: 14px 18px; border: 1px solid #dce6e1; border-radius: 13px; background: #f7faf7; }
.dependency-map article.unresolved { border-color: #e6a496; background: #fff3ef; }
.dependency-map article > div:not(.edge-arrow) { display: flex; flex-direction: column; }
.node-label { font-family: 'SFMono-Regular', Consolas, monospace; font-weight: 650; }
.dependency-map small { color: #85918e; }
.edge-arrow { display: flex; align-items: center; justify-content: center; gap: 8px; color: #b0702a; }
.edge-arrow b { font-size: 10px; letter-spacing: .08em; }
.edge-arrow span { font-size: 22px; }
.validation-list { display: grid; gap: 9px; }
.validation-list article { display: grid; grid-template-columns: 85px 1fr; gap: 12px; padding: 13px 15px; border-left: 4px solid #91a09c; border-radius: 8px; background: #f5f7f4; }
.validation-list article.blocker { border-color: #c84d3a; background: #fff1ed; }
.validation-list article.warning { border-color: #c68b38; background: #fff8e9; }
.validation-list p { margin: 3px 0 0; color: #66736f; }
.error-text { color: #b33f30; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
.source-table { margin-top: 14px; }
@media (max-width: 1050px) {
  .workspace { grid-template-columns: 1fr; }
  .candidate-rail :deep(.el-scrollbar) { height: 260px !important; }
  .health-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 720px) {
  .candidate-page { padding: 12px; }
  .command-header, .detail-heading { flex-direction: column; align-items: stretch; }
  .workspace { gap: 10px; }
  .detail-stage { padding: 14px; }
  .health-grid, .form-grid { grid-template-columns: 1fr; }
  .dependency-map article { grid-template-columns: 1fr; }
  .edge-arrow { justify-content: start; }
}
</style>
