<template>
  <div class="version-management">
    <header class="page-heading">
      <div>
        <h2>数据版本</h2>
        <span class="count-text">共 {{ configs.length }} 个实体</span>
      </div>
      <el-button :loading="loading" title="刷新" @click="loadConfigs">
        <el-icon><Refresh /></el-icon>
      </el-button>
    </header>

    <section class="content-panel">
      <div class="table-toolbar">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索实体名称或编码"
          @keyup.enter="loadConfigs"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" @click="loadConfigs">
          <el-icon><Search /></el-icon>
          查询
        </el-button>
      </div>

      <el-table v-loading="loading" :data="configs" border stripe>
        <el-table-column label="实体" min-width="240">
          <template #default="{ row }">
            <div class="primary-text">{{ row.entityName }}</div>
            <div class="secondary-text">{{ row.entityCode }}</div>
          </template>
        </el-table-column>
        <el-table-column label="版本管理" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '已启用' : '未启用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="配置状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'warning'" effect="plain">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="activeReleaseVersion" label="发布版本" width="100" align="center">
          <template #default="{ row }">
            {{ row.activeReleaseVersion ? `v${row.activeReleaseVersion}` : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="scenarioCount" label="场景" width="80" align="center" />
        <el-table-column prop="stepCount" label="前置操作" width="100" align="center" />
        <el-table-column prop="targetBindingCount" label="变更目标" width="100" align="center" />
        <el-table-column prop="updateTime" label="更新时间" width="180">
          <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openConfig(row)">配置</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <el-drawer
      v-model="drawerVisible"
      size="88%"
      destroy-on-close
      class="config-drawer"
    >
      <template #header>
        <div class="drawer-heading">
          <div>
            <h3>{{ draft.entityName }}</h3>
            <span>{{ draft.entityCode }}</span>
          </div>
          <div class="drawer-actions">
            <el-tag :type="draft.enabled ? 'success' : 'info'">
              {{ draft.enabled ? '已启用' : '未启用' }}
            </el-tag>
            <el-tag v-if="draft.activeReleaseVersion" effect="plain">
              运行版本 v{{ draft.activeReleaseVersion }}
            </el-tag>
            <el-button @click="openSimulation">模拟</el-button>
            <el-button :loading="saving" @click="saveDraft">
              <el-icon><DocumentChecked /></el-icon>
              保存草稿
            </el-button>
            <el-button type="primary" :loading="publishing" @click="publishDraft">
              <el-icon><Upload /></el-icon>
              发布
            </el-button>
          </div>
        </div>
      </template>

      <el-tabs v-model="activeTab" class="config-tabs">
        <el-tab-pane label="基本配置" name="basic">
          <el-form label-width="130px" class="basic-form">
            <el-form-item label="启用数据版本">
              <el-switch v-model="draft.enabled" />
            </el-form-item>
            <el-form-item label="草稿修订">
              <span>r{{ draft.revision || 0 }}</span>
            </el-form-item>
            <el-form-item label="当前发布版本">
              <span>{{ draft.activeReleaseVersion ? `v${draft.activeReleaseVersion}` : '未发布' }}</span>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane :label="`版本场景 ${draft.scenarios.length}`" name="scenarios">
          <div class="section-toolbar">
            <span>版本场景</span>
            <el-button type="primary" @click="editScenario()">
              <el-icon><Plus /></el-icon>
              新增
            </el-button>
          </div>
          <el-table :data="sortedScenarios" border>
            <el-table-column label="场景" min-width="190">
              <template #default="{ row }">
                <div class="primary-text">{{ row.scenarioName }}</div>
                <div class="secondary-text">{{ row.scenarioCode }}</div>
              </template>
            </el-table-column>
            <el-table-column label="变更入口" min-width="180">
              <template #default="{ row }">
                <el-tag v-for="item in row.sourceTypes" :key="item" effect="plain">
                  {{ sourceTypeText(item) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作类型" min-width="150">
              <template #default="{ row }">
                <el-tag v-for="item in row.operationTypes" :key="item" type="info" effect="plain">
                  {{ operationTypeText(item) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="业务意图" min-width="160">
              <template #default="{ row }">
                <span>{{ (row.businessIntents || []).join('、') || '全部' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="80" align="center" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled === false ? 'info' : 'success'">
                  {{ row.enabled === false ? '停用' : '启用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="editScenario(row, scenarioOriginalIndex(row))">编辑</el-button>
                <el-button link type="danger" @click="removeScenario(scenarioOriginalIndex(row))">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`前置操作 ${draft.steps.length}`" name="steps">
          <div class="section-toolbar">
            <span>前置操作</span>
            <el-button type="primary" @click="editStep()">
              <el-icon><Plus /></el-icon>
              新增
            </el-button>
          </div>
          <el-table :data="sortedSteps" border>
            <el-table-column label="操作" min-width="220">
              <template #default="{ row }">
                <div class="primary-text">{{ row.stepName }}</div>
                <div class="secondary-text">{{ row.providerCode || '-' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="阶段" width="130">
              <template #default="{ row }">{{ phaseText(row.phase) }}</template>
            </el-table-column>
            <el-table-column label="类型" width="150">
              <template #default="{ row }">{{ stepTypeText(row.stepType) }}</template>
            </el-table-column>
            <el-table-column label="限定场景" min-width="150">
              <template #default="{ row }">{{ scenarioName(row.scenarioCode) }}</template>
            </el-table-column>
            <el-table-column prop="sortOrder" label="顺序" width="70" align="center" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled === false ? 'info' : 'success'">
                  {{ row.enabled === false ? '停用' : '启用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="editStep(row, stepOriginalIndex(row))">编辑</el-button>
                <el-button link type="danger" @click="removeStep(stepOriginalIndex(row))">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane :label="`变更目标 ${draft.targetBindings.length}`" name="targets">
          <div class="section-toolbar">
            <span>变更目标</span>
            <el-button type="primary" @click="editTarget()">
              <el-icon><Plus /></el-icon>
              新增
            </el-button>
          </div>
          <el-table :data="draft.targetBindings" border>
            <el-table-column label="绑定" min-width="210">
              <template #default="{ row }">
                <div class="primary-text">{{ row.bindingName }}</div>
                <div class="secondary-text">{{ row.bindingCode }}</div>
              </template>
            </el-table-column>
            <el-table-column label="来源实体" min-width="180">
              <template #default="{ row }">{{ entityLabel(row.sourceEntityCode) }}</template>
            </el-table-column>
            <el-table-column label="解析方式" width="130">
              <template #default="{ row }">{{ resolverTypeText(row.resolverType) }}</template>
            </el-table-column>
            <el-table-column prop="resolverCode" label="字段 / 关系 / Provider" min-width="190" />
            <el-table-column prop="applyStrategy" label="应用策略" width="100" />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag :type="row.enabled === false ? 'info' : 'success'">
                  {{ row.enabled === false ? '停用' : '启用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="130" align="center">
              <template #default="{ row, $index }">
                <el-button link type="primary" @click="editTarget(row, $index)">编辑</el-button>
                <el-button link type="danger" @click="removeTarget($index)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="发布记录" name="releases">
          <el-table v-loading="releaseLoading" :data="releases" border>
            <el-table-column prop="version" label="版本" width="90">
              <template #default="{ row }">v{{ row.version }}</template>
            </el-table-column>
            <el-table-column prop="publishedByName" label="发布人" min-width="140" />
            <el-table-column prop="publishTime" label="发布时间" width="190">
              <template #default="{ row }">{{ formatTime(row.publishTime) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>
    <EntityVersionConfigDialogs
      v-model:scenario-visible="scenarioDialogVisible"
      v-model:step-visible="stepDialogVisible"
      v-model:target-visible="targetDialogVisible"
      v-model:picker-visible="pickerVisible"
      v-model:picker-keyword="pickerKeyword"
      v-model:picker-page="pickerPage"
      v-model:simulation-visible="simulationVisible"
      :scenario-index="scenarioIndex"
      :scenario-editor="scenarioEditor"
      :source-type-options="sourceTypeOptions"
      :operation-type-options="operationTypeOptions"
      :step-index="stepIndex"
      :step-editor="stepEditor"
      :phase-options="phaseOptions"
      :step-type-options="stepTypeOptions"
      :scenarios="draft.scenarios"
      :catalog="catalog"
      :target-index="targetIndex"
      :target-editor="targetEditor"
      :entities="entities"
      :target-entity-name="draft.entityName"
      :target-entity-code="draft.entityCode"
      :resolver-type-options="resolverTypeOptions"
      :picker-items="pagedPickerItems"
      :picker-total="pickerTotal"
      :picker-loading="pickerLoading"
      :simulation="simulation"
      :simulation-result="simulationResult"
      :simulation-loading="simulationLoading"
      @save-scenario="saveScenario"
      @save-step="saveStep"
      @save-target="saveTarget"
      @open-picker="openPicker"
      @select-picker-item="selectPickerItem"
      @run-simulation="runSimulation"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { DocumentChecked, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { entityApi } from '@/api/entity'
import { entityVersionApi } from '@/api/entityVersion'
import EntityVersionConfigDialogs from './components/EntityVersionConfigDialogs.vue'

const sourceTypeOptions = [
  ['FORM', '表单'],
  ['LIST', '列表'],
  ['APPROVAL_TASK', '审批'],
  ['PROCESS_RUNTIME', '流程运行态'],
  ['FLOW_ACTION', '流程动作'],
  ['CUSTOM_INTERFACE', '自定义接口'],
  ['BATCH', '批量'],
  ['IMPORT', '导入'],
  ['SCHEDULED_JOB', '定时任务'],
  ['MESSAGE_CONSUMER', '消息消费'],
  ['SYSTEM_TASK', '系统任务']
].map(([value, label]) => ({ value, label }))

const operationTypeOptions = [
  ['CREATE', '新增'],
  ['UPDATE', '修改'],
  ['DELETE', '删除'],
  ['STATUS_CHANGE', '状态变化'],
  ['APPLY_CHANGE', '变更生效'],
  ['UPSERT', '新增或修改']
].map(([value, label]) => ({ value, label }))

const phaseOptions = [
  ['PREPARE', '准备'],
  ['BEFORE_WRITE', '写入前'],
  ['AFTER_WRITE', '写入后'],
  ['AFTER_COMMIT', '提交后']
].map(([value, label]) => ({ value, label }))

const stepTypeOptions = [
  ['BUILT_IN_RULE', '内置规则'],
  ['EXPRESSION', '条件表达式'],
  ['FIELD_MAPPING', '字段映射'],
  ['MANAGED_INTERFACE', '受管理接口'],
  ['JAVA_PROVIDER', 'Java Provider']
].map(([value, label]) => ({ value, label }))

const resolverTypeOptions = [
  { label: '引用字段', value: 'FIELD' },
  { label: '实体关系', value: 'RELATION' },
  { label: 'Java Provider', value: 'JAVA_PROVIDER' }
]

const configs = ref([])
const entities = ref([])
const releases = ref([])
const catalog = ref({})
const keyword = ref('')
const loading = ref(false)
const releaseLoading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const drawerVisible = ref(false)
const activeTab = ref('basic')

const emptyDraft = () => ({
  entityCode: '',
  entityName: '',
  enabled: false,
  revision: 0,
  status: 'UNCONFIGURED',
  activeReleaseVersion: null,
  scenarios: [],
  steps: [],
  targetBindings: []
})
const draft = reactive(emptyDraft())

const scenarioDialogVisible = ref(false)
const scenarioIndex = ref(-1)
const scenarioEditor = reactive({})
const stepDialogVisible = ref(false)
const stepIndex = ref(-1)
const stepEditor = reactive({})
const targetDialogVisible = ref(false)
const targetIndex = ref(-1)
const targetEditor = reactive({})

const pickerVisible = ref(false)
const pickerType = ref('')
const pickerTarget = ref('')
const pickerKeyword = ref('')
const pickerPage = ref(1)
const pagedPickerItems = ref([])
const pickerTotal = ref(0)
const pickerLoading = ref(false)
let pickerRequestSequence = 0
let pickerSearchTimer

const simulationVisible = ref(false)
const simulationLoading = ref(false)
const simulationResult = ref(null)
const simulation = reactive({
  sourceType: 'FLOW_ACTION',
  operationType: 'APPLY_CHANGE',
  businessIntentCode: 'CHANGE_EFFECTIVE',
  beforeText: '{}',
  afterText: '{}',
  extraText: '{}'
})

const sortedScenarios = computed(() =>
  [...draft.scenarios].sort((a, b) => Number(b.priority || 0) - Number(a.priority || 0)))
const sortedSteps = computed(() =>
  [...draft.steps].sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0)))
const scenarioOriginalIndex = row => draft.scenarios.indexOf(row)
const stepOriginalIndex = row => draft.steps.indexOf(row)

watch(pickerKeyword, () => {
  pickerPage.value = 1
  schedulePickerLoad()
})

watch(pickerPage, () => {
  schedulePickerLoad()
})

async function loadConfigs() {
  loading.value = true
  try {
    configs.value = await entityVersionApi.listConfigs({
      keyword: keyword.value || undefined
    })
  } finally {
    loading.value = false
  }
}

async function loadCatalog() {
  catalog.value = await entityVersionApi.mutationCatalog()
}

async function loadEntities() {
  entities.value = await entityApi.getAll()
}

async function openConfig(row) {
  const data = await entityVersionApi.getConfig(row.entityCode)
  Object.assign(draft, emptyDraft(), clone(data))
  activeTab.value = 'basic'
  drawerVisible.value = true
  loadReleases()
}

async function loadReleases() {
  releaseLoading.value = true
  try {
    releases.value = await entityVersionApi.releases(draft.entityCode)
  } finally {
    releaseLoading.value = false
  }
}

async function saveDraft() {
  saving.value = true
  try {
    const saved = await entityVersionApi.saveConfig(draft.entityCode, clone(draft))
    Object.assign(draft, emptyDraft(), clone(saved))
    ElMessage.success('草稿已保存')
    await loadConfigs()
  } finally {
    saving.value = false
  }
}

async function publishDraft() {
  await ElMessageBox.confirm('发布后运行时将读取新的不可变配置快照。', '发布数据版本配置', {
    type: 'warning'
  })
  publishing.value = true
  try {
    const published = await entityVersionApi.publishConfig(draft.entityCode)
    Object.assign(draft, emptyDraft(), clone(published))
    ElMessage.success('配置已发布')
    await Promise.all([loadConfigs(), loadReleases()])
  } finally {
    publishing.value = false
  }
}

function editScenario(row, index = -1) {
  scenarioIndex.value = index
  Object.assign(scenarioEditor, {
    scenarioName: row?.scenarioName || '',
    scenarioCode: row?.scenarioCode || '',
    sourceTypes: [...(row?.sourceTypes || [])],
    operationTypes: [...(row?.operationTypes || [])],
    businessIntents: [...(row?.businessIntents || [])],
    conditionText: pretty(row?.condition || {}),
    versionTitleTemplate: row?.versionTitleTemplate || 'V${versionNo} ${scenarioName}',
    priority: row?.priority ?? 0,
    enabled: row?.enabled !== false
  })
  scenarioDialogVisible.value = true
}

function saveScenario() {
  if (!scenarioEditor.scenarioName?.trim() || !scenarioEditor.scenarioCode?.trim()) {
    ElMessage.warning('请填写场景名称和编码')
    return
  }
  const item = {
    ...(scenarioIndex.value >= 0 ? draft.scenarios[scenarioIndex.value] : {}),
    scenarioName: scenarioEditor.scenarioName.trim(),
    scenarioCode: scenarioEditor.scenarioCode.trim().toUpperCase(),
    sourceTypes: scenarioEditor.sourceTypes,
    operationTypes: scenarioEditor.operationTypes,
    businessIntents: scenarioEditor.businessIntents,
    condition: parseJson(scenarioEditor.conditionText, '场景条件'),
    versionTitleTemplate: scenarioEditor.versionTitleTemplate,
    priority: scenarioEditor.priority,
    enabled: scenarioEditor.enabled
  }
  if (!item.condition) return
  if (scenarioIndex.value >= 0) draft.scenarios.splice(scenarioIndex.value, 1, item)
  else draft.scenarios.push(item)
  scenarioDialogVisible.value = false
}

function removeScenario(index) {
  const removed = draft.scenarios[index]
  draft.scenarios.splice(index, 1)
  draft.steps = draft.steps.filter(item => item.scenarioCode !== removed.scenarioCode)
}

function editStep(row, index = -1) {
  stepIndex.value = index
  Object.assign(stepEditor, {
    stepName: row?.stepName || '',
    phase: row?.phase || 'BEFORE_WRITE',
    stepType: row?.stepType || 'BUILT_IN_RULE',
    scenarioCode: row?.scenarioCode || '',
    providerCode: row?.providerCode || '',
    configText: pretty(row?.config || {}),
    sortOrder: row?.sortOrder ?? draft.steps.length * 10,
    enabled: row?.enabled !== false
  })
  stepDialogVisible.value = true
}

function saveStep() {
  if (!stepEditor.stepName?.trim() || !stepEditor.stepType || !stepEditor.phase) {
    ElMessage.warning('请填写操作名称、阶段和类型')
    return
  }
  const config = parseJson(stepEditor.configText, '操作参数')
  if (!config) return
  if (stepEditor.stepType === 'MANAGED_INTERFACE') {
    stepEditor.phase = 'PREPARE'
    config.dataSourceId = stepEditor.providerCode
  }
  const item = {
    ...(stepIndex.value >= 0 ? draft.steps[stepIndex.value] : {}),
    stepName: stepEditor.stepName.trim(),
    phase: stepEditor.phase,
    stepType: stepEditor.stepType,
    scenarioCode: stepEditor.scenarioCode || null,
    providerCode: stepEditor.providerCode || null,
    config,
    sortOrder: stepEditor.sortOrder,
    enabled: stepEditor.enabled
  }
  if (stepIndex.value >= 0) draft.steps.splice(stepIndex.value, 1, item)
  else draft.steps.push(item)
  stepDialogVisible.value = false
}

function removeStep(index) {
  draft.steps.splice(index, 1)
}

function editTarget(row, index = -1) {
  targetIndex.value = index
  const resolverConfig = clone(row?.resolverConfig || {})
  Object.assign(targetEditor, {
    bindingName: row?.bindingName || '',
    bindingCode: row?.bindingCode || '',
    sourceEntityCode: row?.sourceEntityCode || '',
    resolverType: row?.resolverType || 'FIELD',
    resolverCode: row?.resolverCode || '',
    mappingText: pretty(row?.fieldMapping || {}),
    resolverConfigText: pretty(withoutLifecycle(resolverConfig)),
    effectivePatchText: pretty(resolverConfig.sourceEffectivePatch || {}),
    failedPatchText: pretty(resolverConfig.sourceFailedPatch || {}),
    applyStrategy: row?.applyStrategy || 'MERGE',
    enabled: row?.enabled !== false
  })
  targetDialogVisible.value = true
}

function saveTarget() {
  if (!targetEditor.bindingName?.trim()
    || !targetEditor.bindingCode?.trim()
    || !targetEditor.sourceEntityCode
    || !targetEditor.resolverCode?.trim()) {
    ElMessage.warning('请完整填写绑定、来源实体和解析字段')
    return
  }
  const fieldMapping = parseJson(targetEditor.mappingText, '字段映射')
  const resolverConfig = parseJson(targetEditor.resolverConfigText, '解析参数')
  const effectivePatch = parseJson(targetEditor.effectivePatchText, '生效后回写')
  const failedPatch = parseJson(targetEditor.failedPatchText, '失败后回写')
  if (!fieldMapping || !resolverConfig || !effectivePatch || !failedPatch) return
  if (Object.keys(effectivePatch).length) resolverConfig.sourceEffectivePatch = effectivePatch
  if (Object.keys(failedPatch).length) resolverConfig.sourceFailedPatch = failedPatch
  const item = {
    ...(targetIndex.value >= 0 ? draft.targetBindings[targetIndex.value] : {}),
    bindingName: targetEditor.bindingName.trim(),
    bindingCode: targetEditor.bindingCode.trim().toUpperCase(),
    sourceEntityCode: targetEditor.sourceEntityCode,
    targetEntityCode: draft.entityCode,
    resolverType: targetEditor.resolverType,
    resolverCode: targetEditor.resolverCode.trim(),
    resolverConfig,
    fieldMapping,
    applyStrategy: targetEditor.applyStrategy,
    enabled: targetEditor.enabled
  }
  if (targetIndex.value >= 0) draft.targetBindings.splice(targetIndex.value, 1, item)
  else draft.targetBindings.push(item)
  targetDialogVisible.value = false
}

function removeTarget(index) {
  draft.targetBindings.splice(index, 1)
}

function openPicker(type, target) {
  pickerType.value = type
  pickerTarget.value = target
  pickerKeyword.value = ''
  pickerPage.value = 1
  pickerVisible.value = true
  loadPickerOptions()
}

function selectPickerItem(item) {
  if (pickerTarget.value === 'target') {
    targetEditor.resolverCode = item.value
  } else {
    stepEditor.providerCode = item.value
  }
  pickerVisible.value = false
}

function schedulePickerLoad() {
  if (!pickerVisible.value) return
  window.clearTimeout(pickerSearchTimer)
  pickerSearchTimer = window.setTimeout(
    loadPickerOptions,
    250)
}

async function loadPickerOptions() {
  if (!pickerVisible.value || !pickerType.value) return
  const sequence = ++pickerRequestSequence
  pickerLoading.value = true
  try {
    const page = await entityVersionApi.mutationCatalogOptions(
      pickerType.value,
      {
        keyword: pickerKeyword.value || undefined,
        pageNum: pickerPage.value,
        pageSize: 8
      }
    )
    if (sequence !== pickerRequestSequence) return
    pagedPickerItems.value = page?.records || []
    pickerTotal.value = Number(page?.total || 0)
  } finally {
    if (sequence === pickerRequestSequence) {
      pickerLoading.value = false
    }
  }
}

function openSimulation() {
  simulationResult.value = null
  simulationVisible.value = true
}

async function runSimulation() {
  const beforeRecord = parseJson(simulation.beforeText, '写入前数据')
  const afterRecord = parseJson(simulation.afterText, '写入后数据')
  const extraParams = parseJson(simulation.extraText, '扩展参数')
  if (!beforeRecord || !afterRecord || !extraParams) return
  simulationLoading.value = true
  try {
    simulationResult.value = await entityVersionApi.simulate(draft.entityCode, {
      sourceType: simulation.sourceType,
      operationType: simulation.operationType,
      businessIntentCode: simulation.businessIntentCode,
      businessIntentName: simulation.businessIntentCode,
      beforeRecord,
      afterRecord,
      extraParams
    })
  } finally {
    simulationLoading.value = false
  }
}

function parseJson(text, label) {
  try {
    const value = JSON.parse(text || '{}')
    if (!value || Array.isArray(value) || typeof value !== 'object') {
      throw new Error()
    }
    return value
  } catch {
    ElMessage.warning(`${label}必须是 JSON 对象`)
    return null
  }
}

function pretty(value) {
  return JSON.stringify(value || {}, null, 2)
}

function clone(value) {
  return JSON.parse(JSON.stringify(value || {}))
}

function withoutLifecycle(value) {
  delete value.sourceEffectivePatch
  delete value.sourceFailedPatch
  return value
}

function statusText(status) {
  return {
    UNCONFIGURED: '未配置',
    DRAFT: '草稿',
    PUBLISHED: '已发布'
  }[status] || status
}

function labelOf(options, value) {
  return options.find(item => item.value === value)?.label || value
}

const sourceTypeText = value => labelOf(sourceTypeOptions, value)
const operationTypeText = value => labelOf(operationTypeOptions, value)
const phaseText = value => labelOf(phaseOptions, value)
const stepTypeText = value => labelOf(stepTypeOptions, value)
const resolverTypeText = value => labelOf(resolverTypeOptions, value)

function scenarioName(code) {
  if (!code) return '全部场景'
  return draft.scenarios.find(item => item.scenarioCode === code)?.scenarioName || code
}

function entityLabel(code) {
  const entity = entities.value.find(item => item.entityCode === code)
  return entity ? `${entity.entityName} (${code})` : code
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

onMounted(async () => {
  await Promise.all([loadConfigs(), loadCatalog(), loadEntities()])
})
</script>

<style scoped>
.version-management {
  padding: 20px;
}

.page-heading,
.drawer-heading,
.table-toolbar,
.section-toolbar,
.drawer-actions {
  display: flex;
  align-items: center;
}

.page-heading,
.drawer-heading,
.section-toolbar {
  justify-content: space-between;
}

.page-heading {
  margin-bottom: 16px;
}

.page-heading h2,
.drawer-heading h3 {
  margin: 0;
}

.count-text,
.drawer-heading span,
.secondary-text {
  color: #909399;
  font-size: 13px;
}

.content-panel {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 16px;
}

.table-toolbar {
  gap: 10px;
  margin-bottom: 14px;
}

.table-toolbar .el-input {
  width: 320px;
}

.primary-text {
  color: #303133;
  font-weight: 600;
}

.drawer-actions {
  gap: 8px;
}

.drawer-heading {
  width: 100%;
  padding-right: 20px;
}

.config-tabs {
  height: 100%;
}

.basic-form {
  max-width: 620px;
  padding-top: 20px;
}

.section-toolbar {
  margin-bottom: 14px;
  font-weight: 600;
}

.el-table .el-tag {
  margin: 2px 4px 2px 0;
}

:deep(.config-drawer .el-drawer__body) {
  overflow-y: auto;
  padding-top: 0;
}

:deep(.config-tabs .el-tabs__content) {
  overflow: visible;
}

@media (max-width: 900px) {
  .version-management {
    padding: 12px;
  }

  .drawer-heading {
    align-items: flex-start;
    gap: 12px;
  }

  .drawer-actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }
}
</style>
