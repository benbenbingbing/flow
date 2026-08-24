<template>
  <main class="test-center">
    <header class="hero-panel">
      <div>
        <p class="hero-kicker">CONFIGURATION ASSURANCE</p>
        <h1>统一配置测试中心</h1>
        <p>把实体、表单、列表和流程配置放进同一条可重复、可审计的发布验证链。</p>
      </div>
      <div class="hero-actions">
        <el-button @click="openGenerateDialog">自动生成套件</el-button>
        <el-button type="primary" @click="openEditor()">新建测试套件</el-button>
      </div>
    </header>

    <section class="metric-ribbon">
      <article>
        <span>测试套件</span>
        <strong>{{ suites.length }}</strong>
      </article>
      <article>
        <span>当前用例</span>
        <strong>{{ suiteDetail?.cases?.length || 0 }}</strong>
      </article>
      <article>
        <span>最近通过率</span>
        <strong>{{ report ? `${report.passRate}%` : '--' }}</strong>
      </article>
      <article :class="gateStatusClass">
        <span>发布门禁</span>
        <strong>{{ gateStatusText }}</strong>
      </article>
    </section>

    <section class="workspace-grid">
      <aside class="suite-rail">
        <div class="rail-filter">
          <el-select v-model="filters.scopeType" clearable placeholder="全部配置类型" @change="loadSuites">
            <el-option v-for="type in targetTypes" :key="type.value" :label="type.label" :value="type.value" />
          </el-select>
          <el-input v-model="filters.scopeId" clearable placeholder="配置 ID" @keyup.enter="loadSuites" />
        </div>

        <div v-if="loadingSuites" class="rail-state">正在加载测试资产...</div>
        <button
          v-for="item in suites"
          :key="item.id"
          type="button"
          :class="['suite-card', { active: selectedSuiteId === item.id }]"
          @click="selectSuite(item.id)"
        >
          <span class="suite-type">{{ item.scopeType }}</span>
          <strong>{{ item.name }}</strong>
          <small>{{ item.scopeId }}</small>
          <i v-if="item.releaseGate">GATE</i>
        </button>
        <el-empty v-if="!loadingSuites && !suites.length" description="暂无测试套件" :image-size="72" />
      </aside>

      <section class="detail-stage">
        <el-empty v-if="!suiteDetail" description="选择一个套件查看用例和运行结果" />
        <template v-else>
          <header class="detail-head">
            <div>
              <p>{{ suiteDetail.suite.scopeType }} / {{ suiteDetail.suite.scopeId }}</p>
              <h2>{{ suiteDetail.suite.name }}</h2>
              <span>{{ suiteDetail.suite.description || '暂无说明' }}</span>
            </div>
            <div class="detail-actions">
              <el-button @click="openEditor(suiteDetail)">编辑</el-button>
              <el-button type="danger" plain @click="removeSuite">删除</el-button>
              <el-button type="primary" :loading="running" @click="runSuite">运行全部</el-button>
            </div>
          </header>

          <el-tabs v-model="activeTab">
            <el-tab-pane label="测试用例" name="cases">
              <el-table :data="suiteDetail.cases" stripe>
                <el-table-column type="index" width="54" />
                <el-table-column prop="name" label="用例" min-width="190" />
                <el-table-column prop="targetType" label="目标" width="140" />
                <el-table-column prop="scenarioType" label="场景" width="150" />
                <el-table-column prop="targetId" label="配置 ID" min-width="170" show-overflow-tooltip />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain">
                      {{ row.enabled ? '启用' : '停用' }}
                    </el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="运行报告" name="report">
              <div v-if="report" class="report-area">
                <div class="report-summary">
                  <span :class="['run-status', report.run.status.toLowerCase()]">{{ report.run.status }}</span>
                  <div><b>{{ report.run.passedCount }}</b><small>通过</small></div>
                  <div><b>{{ report.run.warningCount }}</b><small>警告</small></div>
                  <div><b>{{ report.run.failedCount }}</b><small>失败</small></div>
                  <div><b>{{ report.run.blockerCount }}</b><small>阻断</small></div>
                  <el-button @click="exportReport">导出 JSON 报告</el-button>
                </div>
                <el-table :data="report.results" stripe>
                  <el-table-column prop="caseName" label="检查项" min-width="200" />
                  <el-table-column label="结果" width="100">
                    <template #default="{ row }">
                      <el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="code" label="代码" min-width="190" />
                  <el-table-column prop="message" label="结论" min-width="260" />
                  <el-table-column prop="durationMs" label="耗时" width="90">
                    <template #default="{ row }">{{ row.durationMs }} ms</template>
                  </el-table-column>
                  <el-table-column width="90">
                    <template #default="{ row }">
                      <el-button link type="primary" @click="showEvidence(row)">证据</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
              <el-empty v-else description="尚未运行此套件" />
            </el-tab-pane>

            <el-tab-pane label="运行历史" name="history">
              <el-table :data="runHistory" @row-click="openHistoricalRun">
                <el-table-column prop="startedAt" label="开始时间" min-width="180" />
                <el-table-column prop="triggerType" label="触发方式" width="120" />
                <el-table-column prop="status" label="状态" width="110" />
                <el-table-column prop="totalCount" label="总数" width="80" />
                <el-table-column prop="configFingerprint" label="配置指纹" min-width="220" show-overflow-tooltip />
              </el-table>
            </el-tab-pane>
          </el-tabs>
        </template>
      </section>
    </section>

    <el-dialog v-model="editorVisible" :title="editor.id ? '编辑测试套件' : '新建测试套件'" width="min(980px, 94vw)" destroy-on-close>
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="套件名称"><el-input v-model="editor.name" /></el-form-item>
          <el-form-item label="配置类型">
            <el-select v-model="editor.scopeType">
              <el-option v-for="type in targetTypes" :key="type.value" :label="type.label" :value="type.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="配置 ID"><el-input v-model="editor.scopeId" /></el-form-item>
          <el-form-item label="发布策略">
            <div class="switch-line"><el-switch v-model="editor.releaseGate" /> 作为发布门禁</div>
          </el-form-item>
          <el-form-item label="说明" class="form-wide"><el-input v-model="editor.description" type="textarea" /></el-form-item>
        </div>

        <div class="case-editor-head">
          <h3>测试用例</h3>
          <el-button @click="appendCase">添加用例</el-button>
        </div>
        <el-collapse class="case-editor-list">
          <el-collapse-item v-for="(item, index) in editor.cases" :key="item.localId" :name="item.localId">
            <template #title>
              <div class="case-collapse-title">
                <strong>{{ item.name || `用例 ${index + 1}` }}</strong>
                <el-tag effect="plain">{{ item.targetType }}</el-tag>
              </div>
            </template>
            <div class="form-grid case-grid">
              <el-form-item label="用例名称"><el-input v-model="item.name" /></el-form-item>
              <el-form-item label="用例 Key"><el-input v-model="item.caseKey" /></el-form-item>
              <el-form-item label="目标类型">
                <el-select v-model="item.targetType">
                  <el-option v-for="type in targetTypes" :key="type.value" :label="type.label" :value="type.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="目标 ID"><el-input v-model="item.targetId" /></el-form-item>
              <el-form-item label="场景类型"><el-input v-model="item.scenarioType" /></el-form-item>
              <el-form-item label="启用"><el-switch v-model="item.enabled" /></el-form-item>
              <el-form-item label="输入 JSON" class="form-wide">
                <el-input v-model="item.inputText" type="textarea" :rows="5" />
              </el-form-item>
              <el-form-item label="预期 JSON" class="form-wide">
                <el-input v-model="item.expectedText" type="textarea" :rows="3" />
              </el-form-item>
              <div class="case-actions">
                <el-button v-if="item.targetType === 'NODE_OPERATION'" @click="expandMatrixCoverage(index)">生成矩阵正反向用例</el-button>
                <el-button v-if="item.targetType === 'SCENARIO'" @click="applySandboxTemplate(index)">填充隔离场景模板</el-button>
                <el-button type="danger" plain @click="editor.cases.splice(index, 1)">删除用例</el-button>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button :loading="previewing" @click="previewEditor">保存前试跑</el-button>
        <el-button type="primary" :loading="saving" @click="saveEditor">保存套件</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="generateVisible" title="自动生成基础回归套件" width="520px">
      <el-form label-position="top">
        <el-form-item label="配置类型">
          <el-select v-model="generator.scopeType">
            <el-option v-for="type in persistedTargetTypes" :key="type.value" :label="type.label" :value="type.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="配置 ID"><el-input v-model="generator.scopeId" /></el-form-item>
        <el-form-item label="套件名称"><el-input v-model="generator.name" /></el-form-item>
        <el-form-item><el-switch v-model="generator.releaseGate" /> 启用发布门禁</el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="generateSuite">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="evidenceVisible" title="测试证据" width="min(760px, 92vw)">
      <pre class="evidence-view">{{ evidenceText }}</pre>
    </el-dialog>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  deleteConfigTestSuite,
  generateConfigTestSuite,
  getConfigTestGate,
  getConfigTestReport,
  getConfigTestSuite,
  listConfigTestRuns,
  listConfigTestSuites,
  quickRunConfigTests,
  runConfigTestSuite,
  saveConfigTestSuite
} from '@/api/configTestCenter'
import { generateNodeOperationCoverage } from '@/api/nodeOperationPolicy'

const targetTypes = [
  { label: '实体配置', value: 'ENTITY' },
  { label: '表单配置', value: 'FORM' },
  { label: '列表配置', value: 'LIST' },
  { label: '流程配置', value: 'PROCESS' },
  { label: '空办理人策略', value: 'EMPTY_ASSIGNEE' },
  { label: '节点操作矩阵', value: 'NODE_OPERATION' },
  { label: '通用配置 JSON', value: 'CONFIG_JSON' },
  { label: '隔离端到端场景', value: 'SCENARIO' },
  { label: 'Mock 动作', value: 'ACTION' },
  { label: '冻结时间推进', value: 'TIME_ADVANCE' },
  { label: 'SLA 模拟', value: 'SLA' }
]
const persistedTargetTypes = targetTypes.slice(0, 4)
const suites = ref([])
const selectedSuiteId = ref('')
const suiteDetail = ref(null)
const runHistory = ref([])
const report = ref(null)
const gate = ref(null)
const activeTab = ref('cases')
const loadingSuites = ref(false)
const running = ref(false)
const saving = ref(false)
const previewing = ref(false)
const generating = ref(false)
const editorVisible = ref(false)
const generateVisible = ref(false)
const evidenceVisible = ref(false)
const evidenceText = ref('')
const filters = reactive({ scopeType: '', scopeId: '' })

const blankEditor = () => ({
  id: '', version: null, name: '', description: '', scopeType: 'PROCESS', scopeId: '',
  enabled: true, releaseGate: false, cases: []
})
const editor = reactive(blankEditor())
const generator = reactive({ scopeType: 'PROCESS', scopeId: '', name: '', releaseGate: false })

const gateStatusText = computed(() => {
  if (!gate.value?.required) return '未启用'
  return gate.value.passed ? '已通过' : '已阻断'
})
const gateStatusClass = computed(() => ({ gate: true, blocked: gate.value?.required && !gate.value?.passed }))

onMounted(loadSuites)

async function loadSuites() {
  loadingSuites.value = true
  try {
    suites.value = unwrap(await listConfigTestSuites({ ...filters })) || []
    if (!selectedSuiteId.value && suites.value.length) await selectSuite(suites.value[0].id)
  } catch (error) {
    ElMessage.error(error?.message || '加载测试套件失败')
  } finally {
    loadingSuites.value = false
  }
}

async function selectSuite(suiteId) {
  selectedSuiteId.value = suiteId
  const [detailResponse, runsResponse] = await Promise.all([
    getConfigTestSuite(suiteId), listConfigTestRuns(suiteId)
  ])
  suiteDetail.value = unwrap(detailResponse)
  runHistory.value = unwrap(runsResponse) || []
  report.value = runHistory.value.length ? unwrap(await getConfigTestReport(runHistory.value[0].id)) : null
  const scope = suiteDetail.value.suite
  gate.value = unwrap(await getConfigTestGate(scope.scopeType, scope.scopeId))
}

function openEditor(detail) {
  Object.assign(editor, blankEditor())
  if (detail) {
    Object.assign(editor, clone(detail.suite), {
      cases: detail.cases.map(toEditableCase)
    })
  } else {
    appendCase()
  }
  editorVisible.value = true
}

function appendCase() {
  editor.cases.push(toEditableCase({
    caseKey: `case-${editor.cases.length + 1}`,
    name: `配置检查 ${editor.cases.length + 1}`,
    targetType: editor.scopeType || 'PROCESS',
    targetId: editor.scopeId,
    scenarioType: 'BASELINE', input: {}, expected: { status: 'PASS' }, enabled: true
  }))
}

async function saveEditor() {
  let payload
  try {
    payload = editorPayload()
  } catch (error) {
    ElMessage.warning(error.message)
    return
  }
  saving.value = true
  try {
    const saved = unwrap(await saveConfigTestSuite(payload))
    editorVisible.value = false
    await loadSuites()
    await selectSuite(saved.suite.id)
    ElMessage.success('测试套件已保存')
  } finally {
    saving.value = false
  }
}

async function previewEditor() {
  let payload
  try {
    payload = editorPayload()
  } catch (error) {
    ElMessage.warning(error.message)
    return
  }
  previewing.value = true
  try {
    report.value = unwrap(await quickRunConfigTests({
      name: payload.name, scopeType: payload.scopeType, scopeId: payload.scopeId, cases: payload.cases
    }))
    activeTab.value = 'report'
    ElMessage.success(`试跑完成：${report.value.run.status}`)
  } finally {
    previewing.value = false
  }
}

async function runSuite() {
  running.value = true
  try {
    report.value = unwrap(await runConfigTestSuite(selectedSuiteId.value))
    activeTab.value = 'report'
    await selectSuite(selectedSuiteId.value)
    ElMessage.success(`运行完成：${report.value.run.status}`)
  } finally {
    running.value = false
  }
}

async function removeSuite() {
  await ElMessageBox.confirm('删除后历史报告仍保留，但套件不再可运行。', '删除测试套件', { type: 'warning' })
  await deleteConfigTestSuite(selectedSuiteId.value)
  selectedSuiteId.value = ''
  suiteDetail.value = null
  report.value = null
  await loadSuites()
}

function openGenerateDialog() {
  Object.assign(generator, { scopeType: 'PROCESS', scopeId: '', name: '', releaseGate: false })
  generateVisible.value = true
}

async function generateSuite() {
  generating.value = true
  try {
    const detail = unwrap(await generateConfigTestSuite({ ...generator }))
    generateVisible.value = false
    await loadSuites()
    await selectSuite(detail.suite.id)
  } finally {
    generating.value = false
  }
}

function applySandboxTemplate(index) {
  const item = editor.cases[index]
  item.scenarioType = 'ISOLATED_END_TO_END'
  item.inputText = JSON.stringify({
    environment: {
      name: 'TEST',
      adapterMode: 'MOCK',
      identity: 'config-test-user',
      organizations: ['config-test-org'],
      frozenAt: '2026-01-01T00:00:00Z',
      allowExternalSideEffects: false,
      allowBusinessWrites: false
    },
    steps: [
      { key: 'form', type: 'FORM_INIT', targetId: editor.scopeId, input: {}, expected: { status: 'PASS' } },
      { key: 'clock', type: 'TIME_ADVANCE', input: { advanceSeconds: 3600 }, expected: { advancedAt: '2026-01-01T01:00:00Z' } },
      { key: 'action', type: 'ACTION', input: { mockResponse: { accepted: true } }, expected: { failure: false } },
      { key: 'sla', type: 'SLA', input: { dueAt: '2026-01-01T00:30:00Z', advanceSeconds: 3600 }, expected: { breached: true } }
    ]
  }, null, 2)
  item.expectedText = JSON.stringify({ status: 'PASS' }, null, 2)
}

async function expandMatrixCoverage(index) {
  let input
  try {
    input = JSON.parse(editor.cases[index].inputText || '{}')
  } catch {
    ElMessage.warning('请先填写有效的节点矩阵输入 JSON')
    return
  }
  const policyJson = input.policyJson || JSON.stringify(input.policy || {})
  const coverage = unwrap(await generateNodeOperationCoverage(policyJson)) || []
  const source = editor.cases[index]
  const generated = coverage.map((item, offset) => toEditableCase({
    caseKey: `${source.caseKey}-${item.operation}-${item.scenarioType}`.toLowerCase(),
    name: `${item.operation} / ${item.scenarioType}`,
    targetType: 'NODE_OPERATION',
    targetId: source.targetId,
    scenarioType: item.scenarioType,
    input: { ...input, operation: item.operation },
    expected: { status: item.expected === 'ALLOW' ? 'PASS' : undefined },
    enabled: true,
    sortOrder: index + offset
  }))
  editor.cases.splice(index + 1, 0, ...generated)
  ElMessage.success(`已加入 ${generated.length} 条矩阵覆盖用例`)
}

async function openHistoricalRun(row) {
  report.value = unwrap(await getConfigTestReport(row.id))
  activeTab.value = 'report'
}

function showEvidence(row) {
  evidenceText.value = JSON.stringify(row.evidence || {}, null, 2)
  evidenceVisible.value = true
}

function exportReport() {
  const blob = new Blob([JSON.stringify(report.value, null, 2)], { type: 'application/json;charset=utf-8' })
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `config-test-report-${report.value.run.id}.json`
  link.click()
  URL.revokeObjectURL(link.href)
}

function editorPayload() {
  if (!editor.name.trim() || !editor.scopeType || !editor.scopeId.trim()) {
    throw new Error('请完整填写套件名称、配置类型和配置 ID')
  }
  if (!editor.cases.length) throw new Error('至少需要一条测试用例')
  return {
    id: editor.id || null,
    version: editor.version,
    name: editor.name,
    description: editor.description,
    scopeType: editor.scopeType,
    scopeId: editor.scopeId,
    enabled: editor.enabled,
    releaseGate: editor.releaseGate,
    cases: editor.cases.map((item, index) => ({
      id: item.id || null,
      caseKey: item.caseKey || `case-${index + 1}`,
      name: item.name,
      targetType: item.targetType,
      targetId: item.targetId || editor.scopeId,
      scenarioType: item.scenarioType || 'BASELINE',
      input: parseJson(item.inputText, `${item.name} 输入`),
      expected: parseJson(item.expectedText, `${item.name} 预期`),
      enabled: item.enabled,
      sortOrder: index
    }))
  }
}

function toEditableCase(item) {
  return {
    ...clone(item),
    localId: crypto.randomUUID(),
    inputText: JSON.stringify(item.input || {}, null, 2),
    expectedText: JSON.stringify(item.expected || {}, null, 2)
  }
}

function parseJson(text, label) {
  try { return JSON.parse(text || '{}') } catch { throw new Error(`${label}不是有效 JSON`) }
}

function statusTag(status) {
  return status === 'PASS' ? 'success' : status === 'WARNING' ? 'warning' : 'danger'
}

function unwrap(response) {
  if (response?.data?.data !== undefined) return response.data.data
  if (response?.data !== undefined) return response.data
  return response
}

function clone(value) {
  return JSON.parse(JSON.stringify(value))
}
</script>

<style scoped>
.test-center {
  --ink: #17201a;
  --rust: #c64f2d;
  --moss: #3f6b54;
  min-height: 100%;
  padding: 24px;
  color: var(--ink);
  background:
    radial-gradient(circle at 88% 6%, rgb(198 79 45 / 14%), transparent 27%),
    linear-gradient(135deg, #eef1e9 0%, #f9f7ef 48%, #e8eee6 100%);
}

.hero-panel,
.detail-head,
.report-summary,
.case-editor-head,
.case-collapse-title,
.switch-line {
  display: flex;
  align-items: center;
}

.hero-panel {
  justify-content: space-between;
  gap: 24px;
  padding: 28px 30px;
  border: 1px solid rgb(63 107 84 / 20%);
  border-radius: 20px;
  background: rgb(255 255 255 / 76%);
  box-shadow: 0 22px 50px rgb(37 56 45 / 9%);
}

.hero-panel h1 {
  margin: 4px 0 7px;
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: clamp(26px, 4vw, 42px);
  font-weight: 700;
}

.hero-panel p { margin: 0; color: #69736c; }
.hero-kicker { color: var(--rust) !important; font-size: 11px; font-weight: 800; letter-spacing: .18em; }
.hero-actions { display: flex; gap: 10px; }

.metric-ribbon {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  margin: 18px 0;
  overflow: hidden;
  border-radius: 14px;
  background: #cfd8cf;
}

.metric-ribbon article {
  display: grid;
  gap: 6px;
  padding: 17px 20px;
  background: rgb(255 255 255 / 78%);
}
.metric-ribbon span { color: #707a72; font-size: 12px; }
.metric-ribbon strong { font-family: Georgia, serif; font-size: 25px; }
.metric-ribbon .gate strong { color: var(--moss); }
.metric-ribbon .blocked strong { color: var(--rust); }

.workspace-grid { display: grid; grid-template-columns: 280px minmax(0, 1fr); gap: 18px; }
.suite-rail, .detail-stage { border: 1px solid #d8ded6; border-radius: 16px; background: rgb(255 255 255 / 80%); }
.suite-rail { min-height: 570px; padding: 12px; }
.rail-filter { display: grid; gap: 8px; margin-bottom: 12px; }
.rail-state { padding: 22px; color: #7c857f; text-align: center; }

.suite-card {
  position: relative;
  display: grid;
  width: 100%;
  gap: 4px;
  margin-bottom: 8px;
  padding: 13px 14px;
  border: 1px solid transparent;
  border-radius: 10px;
  color: inherit;
  text-align: left;
  background: #f3f5ef;
  cursor: pointer;
}
.suite-card:hover, .suite-card.active { border-color: var(--moss); background: #e9f0e8; }
.suite-card small, .suite-type { color: #758079; font-size: 11px; }
.suite-type { letter-spacing: .1em; }
.suite-card i { position: absolute; top: 12px; right: 12px; color: var(--rust); font-size: 9px; font-style: normal; font-weight: 800; }

.detail-stage { min-width: 0; padding: 20px; }
.detail-head { justify-content: space-between; gap: 16px; margin-bottom: 14px; }
.detail-head p { margin: 0; color: var(--rust); font-size: 11px; font-weight: 800; letter-spacing: .1em; }
.detail-head h2 { margin: 4px 0; font-family: 'Noto Serif SC', 'Songti SC', serif; }
.detail-head span { color: #717b74; font-size: 12px; }
.detail-actions { display: flex; flex-wrap: wrap; gap: 7px; }

.report-area { display: grid; gap: 14px; }
.report-summary { gap: 24px; padding: 14px; border-radius: 12px; background: #f0f3ed; }
.report-summary > div { display: grid; text-align: center; }
.report-summary b { font-size: 20px; }
.report-summary small { color: #778178; }
.report-summary .el-button { margin-left: auto; }
.run-status { padding: 7px 11px; border-radius: 8px; font-weight: 800; }
.run-status.pass { color: #286548; background: #dcf0e2; }
.run-status.warning { color: #87611c; background: #faefca; }
.run-status.fail { color: #923c2b; background: #f7ded8; }

.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 15px; }
.form-wide { grid-column: 1 / -1; }
.case-editor-head { justify-content: space-between; margin: 8px 0; }
.case-editor-head h3 { margin: 0; }
.case-editor-list { max-height: 52vh; overflow: auto; }
.case-collapse-title { width: 100%; justify-content: space-between; padding-right: 12px; }
.case-grid { padding: 0 12px 12px; }
.case-actions { grid-column: 1 / -1; display: flex; justify-content: flex-end; gap: 8px; }
.switch-line { gap: 8px; min-height: 32px; }
.evidence-view { max-height: 60vh; overflow: auto; padding: 15px; border-radius: 10px; color: #dce8df; background: #17201a; white-space: pre-wrap; }

@media (max-width: 900px) {
  .test-center { padding: 13px; }
  .hero-panel { align-items: flex-start; flex-direction: column; padding: 20px; }
  .metric-ribbon { grid-template-columns: repeat(2, 1fr); }
  .workspace-grid { grid-template-columns: 1fr; }
  .suite-rail { min-height: auto; max-height: 320px; overflow: auto; }
  .detail-head { align-items: flex-start; flex-direction: column; }
}

@media (max-width: 620px) {
  .metric-ribbon, .form-grid { grid-template-columns: 1fr; }
  .form-wide, .case-actions { grid-column: auto; }
  .report-summary { align-items: stretch; flex-direction: column; gap: 10px; }
  .report-summary .el-button { margin-left: 0; }
}
</style>
