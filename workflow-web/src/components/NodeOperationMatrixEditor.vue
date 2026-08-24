<template>
  <section class="operation-matrix">
    <header class="matrix-head">
      <div>
        <p class="eyebrow">NODE AUTHORITY</p>
        <h4>节点操作矩阵</h4>
        <p class="subtitle">由后端统一判断，发布后随流程版本冻结</p>
      </div>
      <el-switch
        v-model="matrixEnabled"
        inline-prompt
        active-text="已启用"
        inactive-text="未启用"
        @change="handleMatrixToggle"
      />
    </header>

    <el-alert
      v-if="!matrixEnabled"
      type="info"
      :closable="false"
      show-icon
      title="未配置时沿用存量流程行为；开启后，未列入矩阵的操作将由后端拒绝。"
    />

    <template v-else>
      <div class="variable-strip">
        <div>
          <strong>条件变量白名单</strong>
          <span>条件只能引用这里声明的流程变量，以及 process、business、task、currentUser、request</span>
        </div>
        <el-input
          v-model="allowedVariablesText"
          placeholder="例如：amount, departmentCode, riskLevel"
          clearable
          @blur="syncAllowedVariables"
        />
      </div>

      <el-alert
        v-if="conflicts.length"
        class="conflict-alert"
        type="warning"
        :closable="false"
        show-icon
      >
        <template #title>发现 {{ conflicts.length }} 个配置冲突</template>
        <template #default>
          <div v-for="item in conflicts" :key="item" class="conflict-item">{{ item }}</div>
        </template>
      </el-alert>

      <el-collapse v-model="openedOperations" class="operation-list">
        <el-collapse-item
          v-for="meta in operationMetas"
          :key="meta.code"
          :name="meta.code"
        >
          <template #title>
            <div class="operation-title">
              <span class="operation-index">{{ meta.index }}</span>
              <span>
                <strong>{{ meta.label }}</strong>
                <small>{{ meta.description }}</small>
              </span>
              <el-tag
                :type="policy.operations[meta.code].enabled ? 'success' : 'info'"
                effect="plain"
                size="small"
              >
                {{ policy.operations[meta.code].enabled ? '允许' : '禁用' }}
              </el-tag>
            </div>
          </template>

          <div class="rule-grid">
            <label class="rule-toggle">
              <span>开放操作</span>
              <el-switch v-model="policy.operations[meta.code].enabled" />
            </label>

            <el-form-item label="适用条件" class="wide-field">
              <el-input
                v-model="policy.operations[meta.code].conditionExpression"
                :disabled="!policy.operations[meta.code].enabled"
                placeholder="例如：amount <= 5000 && process.status == 'OPEN'"
                clearable
              />
            </el-form-item>

            <el-form-item label="权限码">
              <el-input
                v-model="policy.operations[meta.code].permissionCode"
                :disabled="!policy.operations[meta.code].enabled"
                placeholder="例如：process:task:approve"
                clearable
              />
            </el-form-item>

            <div class="inline-switches">
              <label>
                <span>必须填写理由</span>
                <el-switch
                  v-model="policy.operations[meta.code].reasonRequired"
                  :disabled="!policy.operations[meta.code].enabled"
                />
              </label>
              <label>
                <span>必须选择模板</span>
                <el-switch
                  v-model="policy.operations[meta.code].reasonTemplateRequired"
                  :disabled="!policy.operations[meta.code].enabled"
                />
              </label>
            </div>

            <el-form-item label="理由模板" class="wide-field">
              <el-input
                :model-value="joinValues(policy.operations[meta.code].reasonTemplates)"
                :disabled="!policy.operations[meta.code].enabled"
                placeholder="多个模板使用逗号分隔"
                @update:model-value="setList(meta.code, 'reasonTemplates', $event)"
              />
            </el-form-item>

            <template v-if="needsUserTarget(meta.code)">
              <el-form-item label="目标范围">
                <el-select
                  v-model="policy.operations[meta.code].targetScope"
                  :disabled="!policy.operations[meta.code].enabled"
                >
                  <el-option label="任意可选人员" value="ANY" />
                  <el-option label="固定人员白名单" value="FIXED" />
                </el-select>
              </el-form-item>
              <el-form-item
                v-if="policy.operations[meta.code].targetScope === 'FIXED'"
                label="人员 ID"
              >
                <el-input
                  :model-value="joinValues(policy.operations[meta.code].targetIds)"
                  placeholder="多个 ID 使用逗号分隔"
                  @update:model-value="setList(meta.code, 'targetIds', $event)"
                />
              </el-form-item>
            </template>

            <el-form-item v-if="meta.code.startsWith('addSign')" label="允许加签类型">
              <el-checkbox-group
                v-model="policy.operations[meta.code].allowedAddSignTypes"
                :disabled="!policy.operations[meta.code].enabled"
              >
                <el-checkbox label="BEFORE">前加签</el-checkbox>
                <el-checkbox label="AFTER">后加签</el-checkbox>
                <el-checkbox label="PARALLEL">并行加签</el-checkbox>
              </el-checkbox-group>
            </el-form-item>

            <el-form-item v-if="meta.code === 'reject'" label="允许驳回节点" class="wide-field">
              <el-input
                :model-value="joinValues(policy.operations.reject.allowedRejectTargets)"
                :disabled="!policy.operations.reject.enabled"
                placeholder="留空表示使用系统默认目标；多个节点 ID 使用逗号分隔"
                @update:model-value="setList('reject', 'allowedRejectTargets', $event)"
              />
            </el-form-item>

            <el-form-item v-if="meta.code === 'withdraw'" label="撤回时限（分钟）">
              <el-input-number
                v-model="policy.operations.withdraw.withdrawWithinMinutes"
                :disabled="!policy.operations.withdraw.enabled"
                :min="1"
                :max="10080"
                controls-position="right"
                placeholder="不填则不限时"
              />
            </el-form-item>
          </div>
        </el-collapse-item>
      </el-collapse>

      <div class="utility-grid">
        <article class="utility-card">
          <div class="card-title">
            <span>模拟身份预览</span>
            <el-tag effect="dark" type="warning">后端判定</el-tag>
          </div>
          <div class="simulation-grid">
            <el-select v-model="simulation.operation" placeholder="选择操作">
              <el-option
                v-for="meta in operationMetas"
                :key="meta.code"
                :label="meta.label"
                :value="meta.code"
              />
            </el-select>
            <el-input v-model="simulation.permissions" placeholder="权限码，逗号分隔" />
            <el-input v-model="simulation.reason" placeholder="操作理由" />
            <el-input v-model="simulation.targetUserIds" placeholder="目标人员 ID，逗号分隔" />
            <el-input
              v-model="simulation.variablesJson"
              class="simulation-json"
              type="textarea"
              :rows="3"
              placeholder='流程变量 JSON，例如：{"amount": 1200}'
            />
          </div>
          <div class="card-actions">
            <el-button type="primary" :loading="simulating" @click="runSimulation">
              运行预览
            </el-button>
            <div v-if="simulationResult" :class="['decision-pill', simulationResult.allowed ? 'allowed' : 'denied']">
              <strong>{{ simulationResult.allowed ? '允许' : '拒绝' }}</strong>
              <span>{{ simulationResult.message }}</span>
            </div>
          </div>
        </article>

        <article class="utility-card">
          <div class="card-title">
            <span>批量复制与测试覆盖</span>
            <el-tag effect="plain">{{ coverageCases.length }} 条覆盖</el-tag>
          </div>
          <el-select
            v-model="copyTargetNodeIds"
            multiple
            filterable
            collapse-tags
            placeholder="选择要应用相同矩阵的节点"
          >
            <el-option
              v-for="node in copyableNodes"
              :key="node.value"
              :label="node.label"
              :value="node.value"
            />
          </el-select>
          <div class="card-actions stack-on-mobile">
            <el-button :disabled="!copyTargetNodeIds.length" @click="copyToNodes">
              应用到 {{ copyTargetNodeIds.length }} 个节点
            </el-button>
            <el-button :loading="generatingCoverage" @click="generateCoverage">
              生成正反向覆盖
            </el-button>
          </div>
          <div v-if="coverageCases.length" class="coverage-preview">
            <span v-for="item in coverageCases.slice(0, 6)" :key="`${item.operation}-${item.scenarioType}`">
              {{ item.operation }} / {{ item.scenarioType }}
            </span>
          </div>
        </article>
      </div>
    </template>
  </section>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  generateNodeOperationCoverage,
  simulateNodeOperationPolicy
} from '@/api/nodeOperationPolicy'

const props = defineProps({
  modelValue: {
    type: Object,
    default: null
  },
  nodeOptions: {
    type: Array,
    default: () => []
  },
  currentNodeId: {
    type: String,
    default: ''
  }
})

const emit = defineEmits(['update:modelValue', 'copy-to-nodes', 'coverage-generated'])

const operationMetas = [
  { index: '01', code: 'approve', label: '同意', description: '通过当前审批任务' },
  { index: '02', code: 'reject', label: '驳回', description: '退回指定或默认节点' },
  { index: '03', code: 'transfer', label: '转办', description: '将当前任务移交其他人员' },
  { index: '04', code: 'addSignBefore', label: '前加签', description: '原办理人之前增加审批人' },
  { index: '05', code: 'addSignAfter', label: '后加签', description: '原办理人之后增加审批人' },
  { index: '06', code: 'addSignParallel', label: '并行加签', description: '增加同级并行审批人' },
  { index: '07', code: 'manualCc', label: '手工抄送', description: '办理人主动添加知会人员' },
  { index: '08', code: 'withdraw', label: '撤回', description: '发起人撤回运行中的流程' },
  { index: '09', code: 'terminate', label: '终止', description: '强制结束流程实例' }
]

const createRule = () => ({
  enabled: false,
  conditionExpression: '',
  permissionCode: '',
  reasonRequired: false,
  reasonTemplates: [],
  reasonTemplateRequired: false,
  targetScope: 'ANY',
  targetIds: [],
  allowedAddSignTypes: [],
  allowedRejectTargets: [],
  withdrawWithinMinutes: null
})

const createPolicy = source => {
  const operations = {}
  operationMetas.forEach(meta => {
    operations[meta.code] = {
      ...createRule(),
      ...(source?.operations?.[meta.code] || {})
    }
  })
  return {
    version: 1,
    allowedVariables: Array.isArray(source?.allowedVariables) ? [...source.allowedVariables] : [],
    operations
  }
}

const matrixEnabled = ref(Boolean(props.modelValue))
const policy = reactive(createPolicy(props.modelValue))
const allowedVariablesText = ref(policy.allowedVariables.join(', '))
const openedOperations = ref(['approve'])
const copyTargetNodeIds = ref([])
const simulating = ref(false)
const generatingCoverage = ref(false)
const simulationResult = ref(null)
const coverageCases = ref([])
let syncingExternal = false
let initialized = false

const simulation = reactive({
  operation: 'approve',
  permissions: '',
  reason: '',
  targetUserIds: '',
  variablesJson: '{}'
})

watch(
  () => props.modelValue,
  async value => {
    syncingExternal = true
    matrixEnabled.value = Boolean(value)
    Object.assign(policy, createPolicy(value))
    allowedVariablesText.value = policy.allowedVariables.join(', ')
    await nextTick()
    syncingExternal = false
    initialized = true
  },
  { deep: true, immediate: true }
)

watch(
  policy,
  value => {
    if (initialized && !syncingExternal && matrixEnabled.value) {
      emit('update:modelValue', clone(value))
    }
  },
  { deep: true }
)

const copyableNodes = computed(() => props.nodeOptions.filter(node => node.value !== props.currentNodeId))

const conflicts = computed(() => {
  const result = []
  const allowedRoots = new Set([
    ...policy.allowedVariables,
    'process', 'business', 'task', 'currentUser', 'request', 'true', 'false', 'null'
  ])
  operationMetas.forEach(meta => {
    const rule = policy.operations[meta.code]
    if (!rule.enabled) return
    if (rule.reasonTemplateRequired && !rule.reasonTemplates.length) {
      result.push(`${meta.label}要求选择理由模板，但模板列表为空`)
    }
    if (rule.targetScope === 'FIXED' && needsUserTarget(meta.code) && !rule.targetIds.length) {
      result.push(`${meta.label}使用固定目标范围，但未配置人员 ID`)
    }
    if (rule.withdrawWithinMinutes !== null && meta.code === 'withdraw' && rule.withdrawWithinMinutes < 1) {
      result.push('撤回时限必须大于 0 分钟')
    }
    referencedRoots(rule.conditionExpression).forEach(root => {
      if (!allowedRoots.has(root)) {
        result.push(`${meta.label}条件引用了未声明变量 ${root}`)
      }
    })
    if (/\b[A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*\s*\(/.test(rule.conditionExpression || '')) {
      result.push(`${meta.label}条件包含方法调用，受限表达式不允许执行方法`)
    }
  })
  return [...new Set(result)]
})

function handleMatrixToggle(enabled) {
  if (enabled) {
    emit('update:modelValue', clone(policy))
  } else {
    emit('update:modelValue', null)
    simulationResult.value = null
    coverageCases.value = []
  }
}

function syncAllowedVariables() {
  policy.allowedVariables = splitValues(allowedVariablesText.value)
  allowedVariablesText.value = policy.allowedVariables.join(', ')
}

function setList(operation, field, value) {
  policy.operations[operation][field] = splitValues(value)
}

function joinValues(values) {
  return Array.isArray(values) ? values.join(', ') : ''
}

function splitValues(value) {
  return [...new Set(String(value || '').split(/[,，\n]/).map(item => item.trim()).filter(Boolean))]
}

function needsUserTarget(code) {
  return code === 'transfer' || code.startsWith('addSign') || code === 'manualCc'
}

function referencedRoots(expression) {
  if (!expression) return []
  const roots = []
  const pattern = /(^|[^.\w$])([A-Za-z_$][\w$]*)/g
  let match
  while ((match = pattern.exec(expression))) {
    if (!['true', 'false', 'null'].includes(match[2])) roots.push(match[2])
  }
  return [...new Set(roots)]
}

async function runSimulation() {
  if (conflicts.value.length) {
    ElMessage.warning('请先处理矩阵配置冲突')
    return
  }
  let variables
  try {
    variables = JSON.parse(simulation.variablesJson || '{}')
  } catch (error) {
    ElMessage.error('流程变量必须是有效 JSON')
    return
  }
  simulating.value = true
  try {
    const now = new Date()
    const response = await simulateNodeOperationPolicy({
      policyJson: JSON.stringify(policy),
      operation: simulation.operation,
      context: {
        variables,
        permissions: splitValues(simulation.permissions),
        currentUserId: 'designer-preview',
        reason: simulation.reason,
        targetUserIds: splitValues(simulation.targetUserIds),
        addSignType: addSignType(simulation.operation),
        requestVariables: {},
        processStartedAt: new Date(now.getTime() - 10 * 60 * 1000).toISOString(),
        now: now.toISOString()
      }
    })
    simulationResult.value = unwrapResult(response)
  } catch (error) {
    ElMessage.error(error?.message || '模拟预览失败')
  } finally {
    simulating.value = false
  }
}

async function generateCoverage() {
  if (conflicts.value.length) {
    ElMessage.warning('请先处理矩阵配置冲突')
    return
  }
  generatingCoverage.value = true
  try {
    const response = await generateNodeOperationCoverage(JSON.stringify(policy))
    coverageCases.value = unwrapResult(response) || []
    emit('coverage-generated', clone(coverageCases.value))
    ElMessage.success(`已生成 ${coverageCases.value.length} 条矩阵覆盖用例`)
  } catch (error) {
    ElMessage.error(error?.message || '生成测试覆盖失败')
  } finally {
    generatingCoverage.value = false
  }
}

function copyToNodes() {
  emit('copy-to-nodes', {
    nodeIds: [...copyTargetNodeIds.value],
    policy: clone(policy)
  })
}

function addSignType(operation) {
  if (operation === 'addSignBefore') return 'BEFORE'
  if (operation === 'addSignAfter') return 'AFTER'
  if (operation === 'addSignParallel') return 'PARALLEL'
  return null
}

function unwrapResult(response) {
  if (response?.data?.data !== undefined) return response.data.data
  if (response?.data !== undefined) return response.data
  return response
}

function clone(value) {
  return JSON.parse(JSON.stringify(value))
}
</script>

<style scoped>
.operation-matrix {
  --matrix-ink: #17211b;
  --matrix-accent: #d85b34;
  --matrix-moss: #46745e;
  margin-top: 16px;
  padding: 18px;
  border: 1px solid #d9dfd9;
  border-radius: 14px;
  color: var(--matrix-ink);
  background:
    radial-gradient(circle at 95% 5%, rgb(216 91 52 / 10%), transparent 34%),
    linear-gradient(145deg, #fbfaf4, #f3f6ef);
}

.matrix-head,
.operation-title,
.card-title,
.card-actions,
.rule-toggle,
.inline-switches,
.inline-switches label {
  display: flex;
  align-items: center;
}

.matrix-head {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.matrix-head h4 {
  margin: 1px 0 2px;
  font-family: 'Noto Serif SC', 'Songti SC', serif;
  font-size: 18px;
}

.eyebrow {
  margin: 0;
  color: var(--matrix-accent);
  font-size: 10px;
  font-weight: 800;
  letter-spacing: .16em;
}

.subtitle,
.variable-strip span,
.operation-title small {
  color: #68736b;
  font-size: 12px;
}

.subtitle {
  margin: 0;
}

.variable-strip {
  display: grid;
  grid-template-columns: minmax(170px, .7fr) 1.3fr;
  gap: 14px;
  align-items: center;
  margin: 15px 0;
  padding: 12px;
  border-left: 3px solid var(--matrix-moss);
  background: rgb(255 255 255 / 72%);
}

.variable-strip div {
  display: grid;
  gap: 3px;
}

.conflict-alert {
  margin-bottom: 14px;
}

.conflict-item {
  margin-top: 4px;
}

.operation-list {
  border-top: 1px solid #d9dfd9;
}

.operation-title {
  width: 100%;
  gap: 11px;
  padding-right: 12px;
}

.operation-title > span:nth-child(2) {
  display: grid;
  flex: 1;
  text-align: left;
}

.operation-index {
  color: var(--matrix-accent);
  font-family: Georgia, serif;
  font-size: 18px;
  font-style: italic;
}

.rule-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 14px;
  padding: 4px 8px 14px 42px;
}

.wide-field {
  grid-column: 1 / -1;
}

.rule-toggle,
.inline-switches {
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 14px;
}

.inline-switches {
  gap: 12px;
}

.inline-switches label {
  flex: 1;
  justify-content: space-between;
  gap: 8px;
}

.utility-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
  margin-top: 18px;
}

.utility-card {
  padding: 15px;
  border: 1px solid #d7ded6;
  border-radius: 12px;
  background: rgb(255 255 255 / 78%);
  box-shadow: 0 8px 24px rgb(44 62 51 / 6%);
}

.card-title {
  justify-content: space-between;
  margin-bottom: 12px;
  font-weight: 700;
}

.simulation-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.simulation-json {
  grid-column: 1 / -1;
}

.card-actions {
  gap: 10px;
  margin-top: 12px;
}

.decision-pill {
  display: grid;
  flex: 1;
  padding: 7px 10px;
  border-radius: 8px;
  font-size: 11px;
}

.decision-pill.allowed {
  color: #205e43;
  background: #e5f4e9;
}

.decision-pill.denied {
  color: #8e3529;
  background: #fbe8e2;
}

.coverage-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
  margin-top: 12px;
}

.coverage-preview span {
  padding: 3px 6px;
  border-radius: 4px;
  color: #4d5b52;
  background: #edf0e9;
  font-size: 10px;
}

@media (max-width: 760px) {
  .operation-matrix {
    padding: 13px;
  }

  .variable-strip,
  .rule-grid,
  .utility-grid,
  .simulation-grid {
    grid-template-columns: 1fr;
  }

  .rule-grid {
    padding-left: 8px;
  }

  .wide-field,
  .simulation-json {
    grid-column: auto;
  }

  .matrix-head {
    align-items: flex-start;
  }

  .stack-on-mobile {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
