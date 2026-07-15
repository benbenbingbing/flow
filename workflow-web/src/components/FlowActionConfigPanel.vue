<template>
  <div class="flow-action-config">
    <div class="actions-header">
      <div>
        <div class="section-title">{{ sectionTitle }}</div>
        <div class="section-subtitle">{{ sectionSubtitle }}</div>
      </div>
      <el-button type="primary" size="small" @click="showActionDialog()">
        <el-icon><Plus /></el-icon>添加动作
      </el-button>
    </div>

    <el-alert type="info" :closable="false" class="action-alert">
      <template #title>
        <div class="alert-content">
          <span>流程发布后动作才生效</span>
          <el-tag v-if="hasDraftChanges" type="warning" size="small">有未发布变更</el-tag>
        </div>
      </template>
    </el-alert>

    <div class="actions-list" v-loading="loading">
      <div
        v-for="(action, index) in sortedActions"
        :key="action.id"
        class="action-item"
        :class="{ disabled: !action.enabled }"
      >
        <div class="action-sort">
          <el-button link size="small" :disabled="index === 0" @click="moveAction(index, -1)">
            <el-icon><ArrowUp /></el-icon>
          </el-button>
          <span class="sort-number">{{ index + 1 }}</span>
          <el-button link size="small" :disabled="index === sortedActions.length - 1" @click="moveAction(index, 1)">
            <el-icon><ArrowDown /></el-icon>
          </el-button>
        </div>

        <div class="action-content">
          <div class="action-title-row">
            <span class="action-name">{{ action.actionName }}</span>
            <el-tag size="small" :type="action.enabled ? 'success' : 'info'">
              {{ action.enabled ? '启用' : '禁用' }}
            </el-tag>
          </div>
          <div class="action-meta">
            <el-tag size="small" type="primary">{{ timingLabel(action.triggerTiming) }}</el-tag>
            <el-tag size="small" :type="action.executionMode === 'AFTER_COMMIT' ? 'warning' : 'success'">
              {{ executionModeLabel(action.executionMode) }}
            </el-tag>
            <span>{{ action.interfaceName }}</span>
          </div>
        </div>

        <div class="action-ops">
          <el-button link type="primary" size="small" @click="showActionDialog(action)">编辑</el-button>
          <el-button link type="warning" size="small" @click="toggleActionEnabled(action)">
            {{ action.enabled ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="danger" size="small" @click="deleteAction(action)">删除</el-button>
        </div>
      </div>

      <el-empty v-if="!loading && sortedActions.length === 0" description="暂无流程动作" />
    </div>

    <el-dialog
      v-model="actionDialogVisible"
      :title="editingAction.id ? '编辑流程动作' : '添加流程动作'"
      width="680px"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form :model="editingAction" label-width="110px" size="small">
        <el-form-item label="快捷模板">
          <el-select
            v-model="selectedTemplate"
            placeholder="可选：选择常用场景快速带出配置"
            clearable
            style="width: 100%"
            @change="applyTemplate"
          >
            <el-option
              v-for="template in availableTemplates"
              :key="template.value"
              :label="template.label"
              :value="template.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="动作名称" required>
          <el-input v-model="editingAction.actionName" placeholder="如：发送下一办理人通知" />
        </el-form-item>

        <el-form-item label="执行时机" required>
          <el-select
            v-model="editingAction.triggerTiming"
            :disabled="timingOptions.length === 1"
            style="width: 100%"
            @change="onTimingChange"
          >
            <el-option
              v-for="option in timingOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            >
              <div class="timing-option">
                <span>{{ option.label }}</span>
                <span>{{ option.description }}</span>
              </div>
            </el-option>
          </el-select>
          <div class="form-tip">{{ currentTimingOption?.availableContext }}</div>
        </el-form-item>

        <el-form-item label="执行方式" required>
          <el-radio-group v-model="editingAction.executionMode" @change="onExecutionModeChange">
            <el-radio-button
              label="IN_TRANSACTION"
              :disabled="!handlerSupportsMode('IN_TRANSACTION')"
            >
              事务内执行
            </el-radio-button>
            <el-radio-button
              label="AFTER_COMMIT"
              :disabled="!handlerSupportsMode('AFTER_COMMIT')"
            >
              提交后执行
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="失败策略" required>
          <el-select v-model="editingAction.failurePolicy" style="width: 100%">
            <el-option
              v-for="option in failurePolicyOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item
          v-if="editingAction.executionMode === 'AFTER_COMMIT' && editingAction.failurePolicy === 'RETRY'"
          label="最大重试"
        >
          <el-input-number v-model="retryForm.maxRetries" :min="0" :max="20" />
          <div class="form-tip">默认指数退避，最多等待 6 小时；超过次数进入死信记录</div>
        </el-form-item>

        <el-alert
          v-for="warning in riskWarnings"
          :key="warning"
          type="warning"
          :closable="false"
          show-icon
          class="risk-warning"
          :title="warning"
        />

        <el-form-item label="处理器" required>
          <el-select
            v-model="editingAction.interfaceName"
            placeholder="选择已注册的 FlowActionHandler Bean"
            filterable
            clearable
            style="width: 100%"
            @change="onHandlerChange"
          >
            <el-option
              v-for="handler in handlers"
              :key="handler.beanName"
              :label="`${handler.beanName} (${handler.className.split('.').pop()})`"
              :value="handler.beanName"
            />
          </el-select>
          <div class="form-tip">处理器必须实现 FlowActionHandler，提交后动作应使用幂等键</div>
        </el-form-item>

        <el-form-item label="描述">
          <el-input v-model="editingAction.description" type="textarea" :rows="2" />
        </el-form-item>

        <el-form-item label="参数配置">
          <div class="action-params-list">
            <el-row
              v-for="(param, index) in actionParamList"
              :key="index"
              :gutter="8"
              align="middle"
              class="action-param-row"
            >
              <el-col :span="7"><el-input v-model="param.name" placeholder="参数名" /></el-col>
              <el-col :span="6">
                <el-select v-model="param.type" style="width: 100%">
                  <el-option
                    v-for="type in actionParamTypeOptions"
                    :key="type.value"
                    :label="type.label"
                    :value="type.value"
                  />
                </el-select>
              </el-col>
              <el-col :span="8"><el-input v-model="param.value" placeholder="参数值" /></el-col>
              <el-col :span="3">
                <el-button type="danger" link @click="removeActionParam(index)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-col>
            </el-row>
            <el-button type="primary" link @click="addActionParam">
              <el-icon><Plus /></el-icon>添加参数
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="是否启用">
          <el-switch v-model="editingAction.enabled" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="actionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAction">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, ArrowUp, Delete, Plus } from '@element-plus/icons-vue'
import { flowActionApi } from '@/api/flowAction'

const props = defineProps({
  processId: { type: String, required: true },
  scopeType: { type: String, required: true },
  elementId: { type: String, default: '' },
  elementName: { type: String, default: '' },
  bpmnType: { type: String, default: '' }
})

const emit = defineEmits(['changed'])

const actions = ref([])
const timingOptions = ref([])
const handlers = ref([])
const loading = ref(false)
const saving = ref(false)
const hasDraftChanges = ref(false)
const actionDialogVisible = ref(false)
const selectedTemplate = ref('')
const editingAction = ref({})
const actionParamList = ref([])
const retryForm = ref({ maxRetries: 5 })

const actionParamTypeOptions = [
  { label: '静态文本', value: 'string' },
  { label: '数字', value: 'number' },
  { label: '布尔', value: 'boolean' },
  { label: '流程变量', value: 'variable' },
  { label: '表达式', value: 'expression' }
]

const templates = [
  { value: 'approval-validation', label: '审批前校验', scopeType: 'NODE', bpmnType: 'UserTask', triggerTiming: 'TASK_COMPLETING', executionMode: 'IN_TRANSACTION', failurePolicy: 'ROLLBACK', actionName: '审批前业务校验' },
  { value: 'next-assignee-notice', label: '通知下一办理人', scopeType: 'NODE', bpmnType: 'UserTask', triggerTiming: 'TASK_CREATED', executionMode: 'AFTER_COMMIT', failurePolicy: 'RETRY', actionName: '发送待办通知' },
  { value: 'approval-result-sync', label: '审批结果同步', scopeType: 'SEQUENCE_FLOW', triggerTiming: 'TRANSITION_TAKEN', executionMode: 'IN_TRANSACTION', failurePolicy: 'ROLLBACK', actionName: '同步审批结果' },
  { value: 'process-complete-notice', label: '流程完成通知', scopeType: 'PROCESS', triggerTiming: 'PROCESS_COMPLETED', executionMode: 'AFTER_COMMIT', failurePolicy: 'RETRY', actionName: '发送流程完成通知' },
  { value: 'withdraw-cleanup', label: '撤回清理', scopeType: 'PROCESS', triggerTiming: 'PROCESS_WITHDRAWN', executionMode: 'AFTER_COMMIT', failurePolicy: 'RETRY', actionName: '执行撤回清理' }
]

const sectionTitle = computed(() => {
  if (props.scopeType === 'PROCESS') return '全局流程动作'
  if (props.scopeType === 'SEQUENCE_FLOW') return '连线动作'
  return '节点动作'
})

const sectionSubtitle = computed(() => {
  if (props.scopeType === 'PROCESS') return '作用于整个流程实例'
  return props.elementName || props.elementId
})

const sortedActions = computed(() => [...actions.value].sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0)))
const currentTimingOption = computed(() => timingOptions.value.find(item => item.value === editingAction.value.triggerTiming))
const currentHandler = computed(() => handlers.value.find(item => item.beanName === editingAction.value.interfaceName))
const availableTemplates = computed(() => templates.filter(template => {
  if (template.scopeType !== props.scopeType) return false
  if (template.bpmnType && !props.bpmnType.includes(template.bpmnType)) return false
  return true
}))

const failurePolicyOptions = computed(() => editingAction.value.executionMode === 'AFTER_COMMIT'
  ? [{ label: '失败自动重试', value: 'RETRY' }, { label: '记录失败后忽略', value: 'IGNORE' }]
  : [{ label: '失败回滚流程', value: 'ROLLBACK' }, { label: '记录失败后继续', value: 'CONTINUE' }])

const riskWarnings = computed(() => {
  const warnings = []
  if (editingAction.value.executionMode === 'IN_TRANSACTION'
      && /http|notify|message|sync/i.test(editingAction.value.interfaceName || '')) {
    warnings.push('外部接口或通知处理器放在事务内执行，网络波动可能阻塞审批。')
  }
  if (editingAction.value.triggerTiming === 'NODE_COMPLETED') {
    warnings.push('该时机发生在路由计算前，处理器修改流程变量可能改变后续分支。')
  }
  if (['PROCESS_COMPLETED', 'PROCESS_WITHDRAWN', 'PROCESS_TERMINATED'].includes(editingAction.value.triggerTiming)) {
    warnings.push('流程结束类动作中当前任务为空，请使用历史变量或实体数据。')
  }
  return warnings
})

watch(
  () => [props.processId, props.scopeType, props.elementId, props.bpmnType],
  loadAll,
  { immediate: true }
)

async function loadAll() {
  if (!props.processId) return
  loading.value = true
  try {
    const [actionList, optionList, handlerList] = await Promise.all([
      flowActionApi.findDraftActionsByBinding(props.processId, props.scopeType, props.elementId || undefined),
      flowActionApi.timingOptions(props.scopeType, props.bpmnType),
      flowActionApi.listHandlers()
    ])
    actions.value = actionList || []
    timingOptions.value = optionList || []
    handlers.value = handlerList || []
  } catch (error) {
    console.error(error)
    ElMessage.error('加载流程动作配置失败')
  } finally {
    loading.value = false
  }
}

function createEmptyAction() {
  const timing = timingOptions.value[0]
  return {
    id: null,
    actionName: '',
    description: '',
    interfaceName: '',
    methodName: 'execute',
    enabled: true,
    triggerTiming: timing?.value || '',
    executionMode: timing?.defaultExecutionMode || 'IN_TRANSACTION',
    failurePolicy: timing?.defaultFailurePolicy || 'ROLLBACK'
  }
}

function showActionDialog(action = null) {
  editingAction.value = action ? { ...action } : createEmptyAction()
  actionParamList.value = parseParamsJson(editingAction.value.paramsJson)
  retryForm.value = parseRetryConfig(editingAction.value.retryConfig)
  selectedTemplate.value = ''
  actionDialogVisible.value = true
}

function applyTemplate(value) {
  const template = templates.find(item => item.value === value)
  if (!template) return
  editingAction.value = {
    ...editingAction.value,
    actionName: template.actionName,
    triggerTiming: template.triggerTiming,
    executionMode: template.executionMode,
    failurePolicy: template.failurePolicy
  }
}

function onTimingChange(value) {
  const timing = timingOptions.value.find(item => item.value === value)
  if (!timing) return
  editingAction.value.executionMode = timing.defaultExecutionMode
  editingAction.value.failurePolicy = timing.defaultFailurePolicy
}

function onExecutionModeChange(value) {
  editingAction.value.failurePolicy = value === 'AFTER_COMMIT' ? 'RETRY' : 'ROLLBACK'
}

function onHandlerChange() {
  const recommended = currentHandler.value?.recommendedExecutionMode
  if (recommended && handlerSupportsMode(recommended)) {
    editingAction.value.executionMode = recommended
    onExecutionModeChange(recommended)
  }
}

function handlerSupportsMode(mode) {
  const supported = currentHandler.value?.supportedExecutionModes
  return !Array.isArray(supported) && !(supported instanceof Set)
    ? true
    : supported.size === 0 || supported.length === 0 || [...supported].includes(mode)
}

async function saveAction() {
  if (!editingAction.value.actionName || !editingAction.value.interfaceName || !editingAction.value.triggerTiming) {
    ElMessage.warning('请填写动作名称、执行时机和处理器')
    return
  }
  saving.value = true
  try {
    await flowActionApi.saveAction({
      ...editingAction.value,
      processConfigId: props.processId,
      scopeType: props.scopeType,
      elementId: props.scopeType === 'PROCESS' ? null : props.elementId,
      sequenceFlowId: props.scopeType === 'PROCESS' ? '__PROCESS__' : props.elementId,
      paramsJson: buildParamsJson(),
      retryConfig: editingAction.value.executionMode === 'AFTER_COMMIT'
        ? JSON.stringify({ maxRetries: retryForm.value.maxRetries })
        : null,
      sortOrder: editingAction.value.id ? editingAction.value.sortOrder : actions.value.length
    })
    actionDialogVisible.value = false
    hasDraftChanges.value = true
    await loadAll()
    emit('changed', actions.value)
    ElMessage.success('流程动作已保存')
  } catch (error) {
    console.error(error)
    ElMessage.error(error?.message || '保存流程动作失败')
  } finally {
    saving.value = false
  }
}

async function deleteAction(action) {
  try {
    await ElMessageBox.confirm('确定删除该流程动作吗？', '提示', { type: 'warning' })
    await flowActionApi.deleteAction(action.id)
    hasDraftChanges.value = true
    await loadAll()
    emit('changed', actions.value)
    ElMessage.success('流程动作已删除')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('删除流程动作失败')
  }
}

async function toggleActionEnabled(action) {
  await flowActionApi.toggleEnabled(action.id)
  hasDraftChanges.value = true
  await loadAll()
  emit('changed', actions.value)
}

async function moveAction(index, direction) {
  const targetIndex = index + direction
  if (targetIndex < 0 || targetIndex >= sortedActions.value.length) return
  const list = [...sortedActions.value]
  ;[list[index], list[targetIndex]] = [list[targetIndex], list[index]]
  await flowActionApi.updateSortOrder(list.map(item => item.id))
  hasDraftChanges.value = true
  await loadAll()
  emit('changed', actions.value)
}

function parseRetryConfig(value) {
  try {
    return value ? { maxRetries: JSON.parse(value).maxRetries ?? 5 } : { maxRetries: 5 }
  } catch {
    return { maxRetries: 5 }
  }
}

function parseParamsJson(value) {
  if (!value) return []
  try {
    return Object.entries(JSON.parse(value)).map(([name, rawValue]) => {
      let type = typeof rawValue
      if (type === 'string' && rawValue.startsWith('${') && rawValue.endsWith('}')) type = 'variable'
      if (!['number', 'boolean', 'variable'].includes(type)) type = 'string'
      return { name, type, value: String(rawValue) }
    })
  } catch {
    return []
  }
}

function buildParamsJson() {
  const params = {}
  actionParamList.value.forEach(param => {
    if (!param.name) return
    if (param.type === 'number') params[param.name] = Number(param.value)
    else if (param.type === 'boolean') params[param.name] = param.value === true || param.value === 'true'
    else if (param.type === 'variable' || param.type === 'expression') {
      params[param.name] = String(param.value).startsWith('${') ? param.value : `\${${param.value}}`
    } else params[param.name] = param.value
  })
  return JSON.stringify(params)
}

function addActionParam() {
  actionParamList.value.push({ name: '', type: 'string', value: '' })
}

function removeActionParam(index) {
  actionParamList.value.splice(index, 1)
}

function timingLabel(value) {
  return timingOptions.value.find(item => item.value === value)?.label || value
}

function executionModeLabel(value) {
  return value === 'AFTER_COMMIT' ? '提交后执行' : '事务内执行'
}
</script>

<style scoped>
.flow-action-config {
  min-height: 160px;
}

.actions-header,
.action-title-row,
.action-meta,
.alert-content {
  display: flex;
  align-items: center;
}

.actions-header {
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.section-title {
  font-weight: 600;
}

.section-subtitle,
.form-tip {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.action-alert {
  margin-bottom: 12px;
}

.alert-content,
.action-title-row,
.action-meta {
  gap: 8px;
}

.action-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid #ebeef5;
}

.action-item.disabled {
  opacity: 0.6;
}

.action-sort {
  width: 34px;
  text-align: center;
}

.sort-number {
  display: block;
  font-size: 12px;
  color: #909399;
}

.action-content {
  flex: 1;
  min-width: 0;
}

.action-name {
  font-weight: 600;
}

.action-meta {
  flex-wrap: wrap;
  margin-top: 6px;
  color: #606266;
  font-size: 12px;
}

.action-ops {
  white-space: nowrap;
}

.action-params-list {
  width: 100%;
}

.action-param-row {
  margin-bottom: 8px;
}

.timing-option {
  display: flex;
  justify-content: space-between;
  gap: 20px;
}

.timing-option span:last-child {
  color: #909399;
  font-size: 12px;
}

.risk-warning {
  margin-bottom: 10px;
}
</style>
