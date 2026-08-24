<template>
  <main class="intelligence-page">
    <header class="hero-panel">
      <div>
        <span class="eyebrow">CONFIGURATION INTELLIGENCE</span>
        <h1>配置智能中心</h1>
        <p>把实体、表单、列表和流程配置沉淀为可复用、可分析、可验证的工程资产。</p>
      </div>
      <div class="hero-metrics">
        <article>
          <strong>{{ blueprints.length }}</strong>
          <span>配置蓝图</span>
        </article>
        <article>
          <strong>{{ qualityReport?.score ?? '--' }}</strong>
          <span>质量评分</span>
        </article>
        <article>
          <strong>{{ impactCount }}</strong>
          <span>影响资产</span>
        </article>
      </div>
    </header>

    <nav class="workspace-tabs" aria-label="配置智能中心功能">
      <button
        v-for="item in tabs"
        :key="item.key"
        type="button"
        :class="{ active: activeTab === item.key }"
        @click="activeTab = item.key"
      >
        <span>{{ item.index }}</span>{{ item.label }}
      </button>
    </nav>

    <section v-if="activeTab === 'blueprint'" class="workspace-grid blueprint-grid">
      <aside class="panel catalog-panel">
        <div class="panel-heading">
          <div>
            <span class="section-number">01</span>
            <h2>蓝图库</h2>
          </div>
          <button class="text-button" type="button" @click="resetBlueprint">新建</button>
        </div>
        <label class="search-field">
          <span>检索</span>
          <input v-model.trim="blueprintKeyword" placeholder="编码、名称或资产类型" @keyup.enter="loadBlueprints">
        </label>
        <button
          v-for="item in filteredBlueprints"
          :key="item.id"
          type="button"
          class="catalog-item"
          :class="{ selected: Number(blueprintForm.id) === Number(item.id) }"
          @click="selectBlueprint(item)"
        >
          <span class="asset-tag">{{ item.assetType || 'CONFIG' }}</span>
          <strong>{{ item.blueprintName || item.name }}</strong>
          <small>{{ item.blueprintCode || item.code }} · v{{ item.version ?? item.revision ?? 1 }}</small>
        </button>
        <p v-if="!filteredBlueprints.length" class="empty-state">暂无蓝图，从右侧创建第一份标准配置。</p>
      </aside>

      <section class="panel editor-panel">
        <div class="panel-heading">
          <div>
            <span class="section-number">02</span>
            <h2>蓝图工作台</h2>
          </div>
          <span class="status-pill">{{ blueprintForm.status || 'DRAFT' }}</span>
        </div>
        <div class="form-grid">
          <label>蓝图编码<input v-model.trim="blueprintForm.blueprintCode" placeholder="customer-approval"></label>
          <label>蓝图名称<input v-model.trim="blueprintForm.blueprintName" placeholder="客户审批标准蓝图"></label>
          <label>资产类型
            <select v-model="blueprintForm.assetType">
              <option v-for="type in assetTypes" :key="type" :value="type">{{ type }}</option>
            </select>
          </label>
          <label>说明<input v-model.trim="blueprintForm.description" placeholder="适用边界与设计意图"></label>
        </div>
        <div class="code-grid">
          <label>参数 Schema<textarea v-model="blueprintForm.parameterSchemaText" spellcheck="false"></textarea></label>
          <label>配置模板<textarea v-model="blueprintForm.templateText" spellcheck="false"></textarea></label>
        </div>
        <div class="action-row">
          <button class="primary-button" type="button" :disabled="busy" @click="handleSaveBlueprint">保存蓝图</button>
          <button type="button" :disabled="!blueprintForm.id || busy" @click="handlePublishBlueprint">发布版本</button>
          <button type="button" :disabled="!blueprintForm.id || busy" @click="handleExportPackage">导出校验包</button>
        </div>
        <div class="instantiate-box">
          <div>
            <h3>参数化实例</h3>
            <p>仅替换声明过的强类型参数，不执行脚本或表达式。</p>
          </div>
          <textarea v-model="instantiateParametersText" spellcheck="false"></textarea>
          <button type="button" :disabled="!blueprintForm.id || busy" @click="handleInstantiate">生成实例</button>
          <pre v-if="instantiatedConfig">{{ pretty(instantiatedConfig) }}</pre>
        </div>
      </section>
    </section>

    <section v-else-if="activeTab === 'impact'" class="workspace-grid impact-grid">
      <section class="panel">
        <div class="panel-heading">
          <div><span class="section-number">01</span><h2>依赖登记</h2></div>
          <span class="status-pill">原子替换</span>
        </div>
        <div class="form-grid compact">
          <label>源资产类型<select v-model="dependencyForm.sourceAssetType"><option v-for="type in assetTypes" :key="type">{{ type }}</option></select></label>
          <label>源资产标识<input v-model.trim="dependencyForm.sourceAssetId" placeholder="customer"></label>
        </div>
        <label class="block-label">依赖边 JSON
          <textarea v-model="dependencyEdgesText" class="tall-code" spellcheck="false"></textarea>
        </label>
        <div class="action-row">
          <button type="button" :disabled="busy" @click="handleLoadDependencies">读取依赖</button>
          <button class="primary-button" type="button" :disabled="busy" @click="handleSaveDependencies">保存依赖</button>
        </div>
      </section>

      <section class="panel impact-panel">
        <div class="panel-heading">
          <div><span class="section-number">02</span><h2>变更影响分析</h2></div>
          <span class="risk-badge" :data-risk="impactReport?.riskLevel || 'UNKNOWN'">{{ impactReport?.riskLevel || '未分析' }}</span>
        </div>
        <div class="form-grid compact three-columns">
          <label>根资产类型<select v-model="impactForm.assetType"><option v-for="type in assetTypes" :key="type">{{ type }}</option></select></label>
          <label>根资产标识<input v-model.trim="impactForm.assetId" placeholder="customer"></label>
          <label>分析方向<select v-model="impactForm.direction"><option>DOWNSTREAM</option><option>UPSTREAM</option><option>BOTH</option></select></label>
        </div>
        <button class="primary-button full-button" type="button" :disabled="busy" @click="handleAnalyzeImpact">生成影响报告</button>
        <div v-if="impactReport" class="impact-summary">
          <article><span>风险分</span><strong>{{ impactReport.riskScore ?? '--' }}</strong></article>
          <article><span>资产数</span><strong>{{ impactCount }}</strong></article>
          <article><span>最大深度</span><strong>{{ impactReport.maxDepthReached ?? impactReport.depth ?? '--' }}</strong></article>
        </div>
        <div v-if="impactNodes.length" class="impact-path">
          <article v-for="(node, index) in impactNodes" :key="`${node.assetType}-${node.assetId}-${index}`">
            <span>{{ node.depth ?? index }}</span>
            <div><strong>{{ node.displayName || node.assetId }}</strong><small>{{ node.assetType }} / {{ node.assetId }}</small></div>
          </article>
        </div>
        <p v-else class="empty-state">输入拟变更资产，即可追踪直接和传递依赖、循环关系与发布风险。</p>
      </section>
    </section>

    <section v-else class="workspace-grid quality-grid">
      <section class="panel">
        <div class="panel-heading">
          <div><span class="section-number">01</span><h2>配置体检</h2></div>
          <span class="status-pill">可解释规则</span>
        </div>
        <div class="form-grid compact">
          <label>资产类型<select v-model="qualityForm.assetType"><option v-for="type in assetTypes" :key="type">{{ type }}</option></select></label>
          <label>资产标识<input v-model.trim="qualityForm.assetId" placeholder="customer-edit"></label>
        </div>
        <label class="block-label">待分析配置
          <textarea v-model="qualityConfigText" class="tall-code" spellcheck="false"></textarea>
        </label>
        <button class="primary-button full-button" type="button" :disabled="busy" @click="handleAnalyzeQuality">开始体检</button>
        <details class="package-verifier">
          <summary>校验可移植配置包</summary>
          <textarea v-model="packageText" spellcheck="false" placeholder="粘贴导出的配置包 JSON"></textarea>
          <button type="button" :disabled="busy" @click="handleVerifyPackage">验证签名与完整性</button>
          <pre v-if="packageVerification">{{ pretty(packageVerification) }}</pre>
        </details>
      </section>

      <section class="panel report-panel">
        <div class="score-ring" :style="scoreStyle">
          <strong>{{ qualityReport?.score ?? '--' }}</strong>
          <span>{{ qualityReport?.grade || '等待分析' }}</span>
        </div>
        <div class="findings-list" v-if="qualityFindings.length">
          <article v-for="(finding, index) in qualityFindings" :key="finding.code || index" :data-severity="finding.severity">
            <header><strong>{{ finding.title || finding.code }}</strong><span>{{ finding.severity || 'INFO' }}</span></header>
            <p>{{ finding.message || finding.description }}</p>
            <small v-if="finding.path">位置：{{ finding.path }}</small>
          </article>
        </div>
        <div v-if="qualitySuggestions.length" class="suggestions-box">
          <h3>建议动作</h3>
          <p v-for="(suggestion, index) in qualitySuggestions" :key="index">{{ index + 1 }}. {{ suggestion }}</p>
        </div>
        <p v-if="!qualityReport" class="empty-state">检测明文凭据、不安全脚本、宽泛数据权限、操作权限缺口与配置可维护性。</p>
      </section>
    </section>

    <div v-if="notice.text" class="notice" :data-type="notice.type">{{ notice.text }}</div>
  </main>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  analyzeConfigurationImpact,
  analyzeConfigurationQuality,
  exportBlueprintPackage,
  getBlueprint,
  getDependencies,
  instantiateBlueprint,
  listBlueprints,
  publishBlueprint,
  replaceDependencies,
  saveBlueprint,
  verifyConfigurationPackage
} from '@/api/configIntelligence'

const tabs = [
  { key: 'blueprint', index: '01', label: '配置蓝图' },
  { key: 'impact', index: '02', label: '依赖与影响' },
  { key: 'quality', index: '03', label: '质量与校验' }
]
const assetTypes = ['ENTITY', 'FORM', 'LIST', 'PROCESS', 'NODE_POLICY', 'DATA_SCOPE']
const activeTab = ref('blueprint')
const busy = ref(false)
const notice = reactive({ text: '', type: 'success' })
const blueprints = ref([])
const blueprintKeyword = ref('')
const instantiatedConfig = ref(null)
const instantiateParametersText = ref('{\n  "owner": "admin"\n}')
const impactReport = ref(null)
const qualityReport = ref(null)
const packageVerification = ref(null)
const packageText = ref('')

const blueprintForm = reactive({
  id: null,
  blueprintCode: '',
  blueprintName: '',
  assetType: 'FORM',
  description: '',
  status: 'DRAFT',
  version: 0,
  parameterSchemaText: '{\n  "type": "object",\n  "properties": {}\n}',
  templateText: '{}'
})
const dependencyForm = reactive({ sourceAssetType: 'ENTITY', sourceAssetId: '' })
const dependencyEdgesText = ref('[]')
const impactForm = reactive({ assetType: 'ENTITY', assetId: '', direction: 'DOWNSTREAM', maxDepth: 8 })
const qualityForm = reactive({ assetType: 'FORM', assetId: '' })
const qualityConfigText = ref('{}')

const filteredBlueprints = computed(() => {
  const keyword = blueprintKeyword.value.toLowerCase()
  if (!keyword) return blueprints.value
  return blueprints.value.filter(item => [item.blueprintCode, item.code, item.blueprintName, item.name, item.assetType]
    .some(value => String(value || '').toLowerCase().includes(keyword)))
})
const impactNodes = computed(() => impactReport.value?.nodes || impactReport.value?.affectedNodes || [])
const impactCount = computed(() => impactReport.value?.affectedCount ?? impactNodes.value.length ?? 0)
const qualityFindings = computed(() => qualityReport.value?.findings || [])
const qualitySuggestions = computed(() => qualityReport.value?.suggestions || [])
const scoreStyle = computed(() => {
  const score = Math.max(0, Math.min(100, Number(qualityReport.value?.score || 0)))
  return { '--score-angle': `${score * 3.6}deg` }
})

onMounted(loadBlueprints)

function payload(response) {
  return response?.data ?? response ?? {}
}

function listPayload(response) {
  const value = payload(response)
  if (Array.isArray(value)) return value
  return value.records || value.items || value.content || value.list || []
}

function parseJson(text, label) {
  try {
    return JSON.parse(text)
  } catch (error) {
    throw new Error(`${label}不是合法 JSON：${error.message}`)
  }
}

function pretty(value) {
  return JSON.stringify(value, null, 2)
}

function flash(text, type = 'success') {
  notice.text = text
  notice.type = type
  window.clearTimeout(flash.timer)
  flash.timer = window.setTimeout(() => { notice.text = '' }, 3600)
}

async function execute(action, successText) {
  busy.value = true
  try {
    const result = await action()
    if (successText) flash(successText)
    return payload(result)
  } catch (error) {
    flash(error?.response?.data?.message || error?.message || '操作失败', 'error')
    throw error
  } finally {
    busy.value = false
  }
}

async function loadBlueprints() {
  try {
    blueprints.value = listPayload(await listBlueprints({ keyword: blueprintKeyword.value }))
  } catch (error) {
    flash(error?.message || '蓝图加载失败', 'error')
  }
}

function resetBlueprint() {
  Object.assign(blueprintForm, {
    id: null,
    blueprintCode: '',
    blueprintName: '',
    assetType: 'FORM',
    description: '',
    status: 'DRAFT',
    version: 0,
    parameterSchemaText: '{\n  "type": "object",\n  "properties": {}\n}',
    templateText: '{}'
  })
  instantiatedConfig.value = null
}

async function selectBlueprint(item) {
  const detail = await execute(() => getBlueprint(item.id))
  const value = detail.blueprint || detail
  Object.assign(blueprintForm, {
    id: value.id,
    blueprintCode: value.blueprintCode || value.code || '',
    blueprintName: value.blueprintName || value.name || '',
    assetType: value.assetType || 'FORM',
    description: value.description || '',
    status: value.status || 'DRAFT',
    version: value.version ?? value.revision ?? 0,
    parameterSchemaText: pretty(value.parameterSchema || value.schema || {}),
    templateText: pretty(value.template || value.templateConfig || {})
  })
}

async function handleSaveBlueprint() {
  const body = {
    id: blueprintForm.id,
    blueprintCode: blueprintForm.blueprintCode,
    blueprintName: blueprintForm.blueprintName,
    assetType: blueprintForm.assetType,
    description: blueprintForm.description,
    parameterSchema: parseJson(blueprintForm.parameterSchemaText, '参数 Schema'),
    template: parseJson(blueprintForm.templateText, '配置模板'),
    expectedVersion: blueprintForm.version
  }
  const saved = await execute(() => saveBlueprint(body), '蓝图已保存')
  blueprintForm.id = saved.id ?? blueprintForm.id
  blueprintForm.version = saved.version ?? saved.revision ?? blueprintForm.version
  blueprintForm.status = saved.status || blueprintForm.status
  await loadBlueprints()
}

async function handlePublishBlueprint() {
  const published = await execute(
    () => publishBlueprint(blueprintForm.id, blueprintForm.version),
    '蓝图版本已冻结发布'
  )
  blueprintForm.status = published.status || 'PUBLISHED'
  blueprintForm.version = published.version ?? published.revision ?? blueprintForm.version
  await loadBlueprints()
}

async function handleInstantiate() {
  const result = await execute(() => instantiateBlueprint(blueprintForm.id, {
    parameters: parseJson(instantiateParametersText.value, '实例参数')
  }), '配置实例已生成')
  instantiatedConfig.value = result.configuration || result.config || result
}

async function handleExportPackage() {
  const result = await execute(() => exportBlueprintPackage(blueprintForm.id), '可移植配置包已生成')
  const content = pretty(result)
  packageText.value = content
  const url = URL.createObjectURL(new Blob([content], { type: 'application/json;charset=utf-8' }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `${blueprintForm.blueprintCode || 'configuration'}-package.json`
  anchor.click()
  URL.revokeObjectURL(url)
}

async function handleLoadDependencies() {
  const response = await execute(() => getDependencies({
    sourceAssetType: dependencyForm.sourceAssetType,
    sourceAssetId: dependencyForm.sourceAssetId
  }))
  dependencyEdgesText.value = pretty(Array.isArray(response) ? response : response.edges || response.items || [])
}

async function handleSaveDependencies() {
  await execute(() => replaceDependencies({
    sourceAssetType: dependencyForm.sourceAssetType,
    sourceAssetId: dependencyForm.sourceAssetId,
    dependencies: parseJson(dependencyEdgesText.value, '依赖边')
  }), '依赖关系已原子更新')
}

async function handleAnalyzeImpact() {
  impactReport.value = await execute(() => analyzeConfigurationImpact({ ...impactForm }), '影响分析已完成')
}

async function handleAnalyzeQuality() {
  qualityReport.value = await execute(() => analyzeConfigurationQuality({
    assetType: qualityForm.assetType,
    assetId: qualityForm.assetId,
    configuration: parseJson(qualityConfigText.value, '待分析配置')
  }), '配置体检已完成')
}

async function handleVerifyPackage() {
  packageVerification.value = await execute(
    () => verifyConfigurationPackage(parseJson(packageText.value, '配置包')),
    '配置包校验已完成'
  )
}
</script>

<style scoped>
.intelligence-page {
  --ink: #18312d;
  --paper: #f3efe4;
  --accent: #e65f35;
  --green: #1d6b55;
  min-height: 100%;
  padding: 28px;
  color: var(--ink);
  background:
    radial-gradient(circle at 82% 6%, rgba(230, 95, 53, .16), transparent 27%),
    repeating-linear-gradient(90deg, rgba(24, 49, 45, .035) 0 1px, transparent 1px 32px),
    var(--paper);
  font-family: "Noto Serif SC", "Songti SC", serif;
}

.hero-panel, .panel { border: 1px solid rgba(24, 49, 45, .18); background: rgba(255, 253, 247, .9); box-shadow: 0 18px 55px rgba(45, 54, 45, .08); }
.hero-panel { display: flex; justify-content: space-between; gap: 28px; padding: 34px; border-radius: 4px 34px 4px 4px; }
.eyebrow, .section-number { color: var(--accent); font: 700 11px/1.2 ui-monospace, monospace; letter-spacing: .18em; }
h1 { margin: 8px 0; font-size: clamp(32px, 4vw, 58px); line-height: 1; letter-spacing: -.04em; }
h2, h3, p { margin-top: 0; }
.hero-panel p { max-width: 650px; margin-bottom: 0; color: #61716c; font-size: 16px; }
.hero-metrics { display: grid; grid-template-columns: repeat(3, minmax(95px, 1fr)); align-self: end; }
.hero-metrics article { padding: 8px 18px; border-left: 1px solid rgba(24, 49, 45, .18); }
.hero-metrics strong, .hero-metrics span { display: block; }
.hero-metrics strong { font: 700 30px/1 ui-monospace, monospace; }
.hero-metrics span { margin-top: 8px; color: #74817d; font-size: 12px; }
.workspace-tabs { display: flex; gap: 6px; margin: 18px 0; }
.workspace-tabs button, button { border: 1px solid rgba(24, 49, 45, .25); background: #fffdf7; color: var(--ink); cursor: pointer; }
.workspace-tabs button { padding: 12px 18px; font-weight: 700; }
.workspace-tabs button span { margin-right: 8px; color: #9aa49f; font: 11px ui-monospace, monospace; }
.workspace-tabs button.active { border-color: var(--ink); background: var(--ink); color: #fff; }
.workspace-tabs button.active span { color: #ffc0a9; }
.workspace-grid { display: grid; gap: 18px; }
.blueprint-grid { grid-template-columns: minmax(250px, .72fr) minmax(0, 2fr); }
.impact-grid, .quality-grid { grid-template-columns: minmax(320px, .95fr) minmax(0, 1.3fr); }
.panel { padding: 24px; border-radius: 4px; }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 20px; }
.panel-heading h2 { display: inline; margin: 0 0 0 10px; font-size: 21px; }
.text-button { border: 0; border-bottom: 1px solid currentColor; padding: 4px 0; color: var(--accent); background: transparent; }
.search-field, label { display: grid; gap: 7px; color: #596963; font-size: 12px; font-weight: 700; }
input, select, textarea { width: 100%; box-sizing: border-box; border: 1px solid rgba(24, 49, 45, .22); border-radius: 2px; outline: none; background: #fffdf8; color: var(--ink); }
input, select { height: 39px; padding: 0 11px; }
textarea { min-height: 180px; padding: 12px; resize: vertical; font: 12px/1.55 ui-monospace, SFMono-Regular, monospace; }
input:focus, select:focus, textarea:focus { border-color: var(--green); box-shadow: 0 0 0 3px rgba(29, 107, 85, .1); }
.catalog-item { display: grid; width: 100%; margin-top: 10px; padding: 14px; text-align: left; }
.catalog-item.selected { border-color: var(--accent); box-shadow: inset 4px 0 var(--accent); }
.catalog-item strong { margin: 7px 0 4px; }
.catalog-item small, .impact-path small { color: #7b8883; }
.asset-tag, .status-pill, .risk-badge { width: max-content; padding: 4px 7px; border-radius: 20px; background: #e4eee9; color: var(--green); font: 700 10px ui-monospace, monospace; letter-spacing: .07em; }
.form-grid, .code-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.code-grid { margin-top: 16px; }
.compact { margin-bottom: 16px; }
.three-columns { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.action-row { display: flex; flex-wrap: wrap; gap: 9px; margin-top: 16px; }
.action-row button, .full-button, .instantiate-box button, .package-verifier button { min-height: 39px; padding: 0 15px; font-weight: 700; }
.primary-button { border-color: var(--accent); background: var(--accent); color: white; }
button:disabled { cursor: not-allowed; opacity: .45; }
.instantiate-box, .package-verifier { margin-top: 22px; padding: 18px; border: 1px dashed rgba(24, 49, 45, .3); background: #faf5e9; }
.instantiate-box { display: grid; grid-template-columns: 1fr 1.3fr auto; align-items: start; gap: 12px; }
.instantiate-box textarea { min-height: 90px; }
pre { overflow: auto; max-height: 300px; margin: 12px 0 0; padding: 14px; background: var(--ink); color: #e8f2ec; font-size: 11px; }
.instantiate-box pre { grid-column: 1 / -1; }
.block-label { margin-top: 14px; }
.tall-code { min-height: 360px; }
.full-button { width: 100%; }
.impact-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1px; margin: 18px 0; background: rgba(24, 49, 45, .16); }
.impact-summary article { padding: 15px; background: #fffdf7; }
.impact-summary span, .impact-summary strong { display: block; }
.impact-summary strong { margin-top: 5px; font: 700 25px ui-monospace, monospace; }
.risk-badge[data-risk="HIGH"], .risk-badge[data-risk="CRITICAL"] { background: #fae0d6; color: #a33618; }
.impact-path { display: grid; gap: 8px; }
.impact-path article { display: flex; align-items: center; gap: 13px; padding: 11px; border-left: 3px solid var(--green); background: #f2f5ee; }
.impact-path article > span { display: grid; width: 28px; height: 28px; place-items: center; border-radius: 50%; background: var(--green); color: white; font: 11px ui-monospace, monospace; }
.impact-path strong, .impact-path small { display: block; }
.score-ring { display: grid; width: 176px; height: 176px; margin: 12px auto 28px; place-content: center; border-radius: 50%; background: radial-gradient(circle, #fffdf7 58%, transparent 59%), conic-gradient(var(--green) var(--score-angle), #dedfd7 0); text-align: center; }
.score-ring strong { font: 700 48px/1 ui-monospace, monospace; }
.score-ring span { margin-top: 7px; color: #6d7d77; }
.findings-list { display: grid; gap: 10px; }
.findings-list article { padding: 14px; border-left: 4px solid #d49d35; background: #faf7ee; }
.findings-list article[data-severity="HIGH"], .findings-list article[data-severity="CRITICAL"] { border-left-color: var(--accent); }
.findings-list header { display: flex; justify-content: space-between; gap: 10px; }
.findings-list header span { font: 700 10px ui-monospace, monospace; }
.findings-list p { margin: 7px 0; color: #596963; }
.suggestions-box { margin-top: 18px; padding: 16px; background: #e7efe9; }
.package-verifier summary { cursor: pointer; font-weight: 700; }
.package-verifier textarea { min-height: 130px; margin: 12px 0; }
.empty-state { padding: 26px 4px; color: #81908a; text-align: center; }
.notice { position: fixed; right: 28px; bottom: 28px; z-index: 20; max-width: 420px; padding: 13px 18px; border-radius: 2px; background: var(--green); color: white; box-shadow: 0 12px 35px rgba(0, 0, 0, .18); }
.notice[data-type="error"] { background: #a63f25; }

@media (max-width: 900px) {
  .intelligence-page { padding: 14px; }
  .hero-panel { display: block; padding: 24px; }
  .hero-metrics { margin-top: 24px; }
  .blueprint-grid, .impact-grid, .quality-grid { grid-template-columns: 1fr; }
  .workspace-tabs { overflow-x: auto; }
  .workspace-tabs button { flex: 0 0 auto; }
}

@media (max-width: 620px) {
  .hero-metrics { grid-template-columns: 1fr; }
  .hero-metrics article { border-top: 1px solid rgba(24, 49, 45, .18); border-left: 0; }
  .form-grid, .code-grid, .three-columns { grid-template-columns: 1fr; }
  .instantiate-box { grid-template-columns: 1fr; }
  .instantiate-box pre { grid-column: auto; }
}
</style>
