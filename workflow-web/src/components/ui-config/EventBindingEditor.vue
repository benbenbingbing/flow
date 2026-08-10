<template>
  <div class="event-binding-editor">
    <div class="binding-toolbar">
      <div>
        <div class="binding-title">{{ title }}</div>
        <div class="binding-scope">
          {{ ownerTypeLabel }} / {{ ownerId || '未选择配置对象' }}
          <span v-if="targetType !== 'OWNER'"> / {{ targetLabel }}</span>
        </div>
      </div>
      <div class="toolbar-actions">
        <el-button
          :loading="loading"
          :disabled="!ownerId"
          title="刷新事件绑定"
          @click="load"
        >
          <el-icon><Refresh /></el-icon>
        </el-button>
        <el-button
          type="primary"
          :disabled="!ownerId"
          @click="openCreate"
        >
          <el-icon><Plus /></el-icon>
          新增绑定
        </el-button>
      </div>
    </div>

    <el-alert
      title="默认保留平台处理。只有执行链中加入 REPLACE 步骤，才会由自定义接口完全替代。"
      type="info"
      :closable="false"
      show-icon
      class="binding-hint"
    />

    <el-empty
      v-if="!ownerId"
      description="请先选择实体、表单或列表"
    />
    <el-table
      v-else
      v-loading="loading"
      :data="visibleBindings"
      row-key="id"
      border
    >
      <el-table-column label="触发事件" min-width="180">
        <template #default="{ row }">
          <div class="primary-text">{{ eventLabel(row.eventCode) }}</div>
          <div class="secondary-text">{{ row.eventCode }}</div>
        </template>
      </el-table-column>
      <el-table-column label="继承方式" width="130">
        <template #default="{ row }">
          <el-tag :type="inheritanceType(row.inheritanceMode)" effect="plain">
            {{ inheritanceLabel(row.inheritanceMode) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="完整执行链" min-width="360">
        <template #default="{ row }">
          <div class="chain-preview">
            <template
              v-for="(item, index) in chainItems(row)"
              :key="`${item.kind}-${index}`"
            >
              <span v-if="index" class="chain-arrow">→</span>
              <el-tag
                :type="item.type"
                :effect="item.kind === 'platform' ? 'dark' : 'plain'"
              >
                {{ item.label }}
              </el-tag>
            </template>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled === false ? 'info' : 'success'">
            {{ row.enabled === false ? '停用' : '启用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right" align="center">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog
      v-model="dialogVisible"
      :title="editor.id ? '编辑事件绑定' : '新增事件绑定'"
      width="980px"
      append-to-body
      destroy-on-close
      :close-on-click-modal="false"
    >
      <el-form :model="editor" label-width="96px">
        <div class="base-grid">
          <el-form-item label="触发事件" required>
            <el-select
              v-model="editor.eventCode"
              filterable
              style="width: 100%"
              :disabled="Boolean(editor.id)"
              @change="handleEventChange"
            >
              <el-option
                v-for="event in availableEvents"
                :key="event"
                :label="`${eventLabel(event)} (${event})`"
                :value="event"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="继承方式" required>
            <template #label>
              <ConfigHelpLabel
                label="继承方式"
                help-key="uiEvent.inheritanceMode"
              />
            </template>
            <el-segmented
              v-model="editor.inheritanceMode"
              :options="inheritanceOptions"
            />
          </el-form-item>
        </div>

        <el-alert
          v-if="editor.inheritanceMode === 'DISABLE'"
          title="禁用当前层的自定义链，仅保留平台默认处理。"
          type="warning"
          :closable="false"
          class="editor-alert"
        />

        <template v-else>
          <div class="steps-header">
            <div>
              <div class="section-title">执行步骤</div>
              <div class="secondary-text">
                前置接口先执行，平台默认处理居中，后置接口最后执行。
              </div>
            </div>
            <el-button type="primary" plain @click="addStep">
              <el-icon><Plus /></el-icon>
              增加步骤
            </el-button>
          </div>

          <div v-if="editor.steps.length" class="draft-chain">
            <template
              v-for="(item, index) in editorChainItems"
              :key="`${item.kind}-${index}`"
            >
              <span v-if="index" class="chain-arrow">→</span>
              <el-tag
                :type="item.type"
                :effect="item.kind === 'platform' ? 'dark' : 'plain'"
              >
                {{ item.label }}
              </el-tag>
            </template>
          </div>

          <el-empty
            v-if="editor.steps.length === 0"
            description="尚未增加接口步骤，将直接执行平台默认处理"
            :image-size="72"
          />

          <div
            v-for="(step, index) in editor.steps"
            :key="step.rowKey"
            class="step-panel"
          >
            <div class="step-heading">
              <div class="step-order">{{ index + 1 }}</div>
              <el-input
                v-model="step.name"
                placeholder="步骤名称，例如：校验客户状态"
              />
              <div class="step-tools">
                <el-button
                  circle
                  :disabled="index === 0"
                  title="上移"
                  @click="moveStep(index, -1)"
                >
                  <el-icon><ArrowUp /></el-icon>
                </el-button>
                <el-button
                  circle
                  :disabled="index === editor.steps.length - 1"
                  title="下移"
                  @click="moveStep(index, 1)"
                >
                  <el-icon><ArrowDown /></el-icon>
                </el-button>
                <el-button
                  circle
                  type="danger"
                  title="删除步骤"
                  @click="editor.steps.splice(index, 1)"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>

            <div class="step-grid">
              <el-form-item label="执行位置">
                <template #label>
                  <ConfigHelpLabel
                    label="执行位置"
                    help-key="uiEvent.stepStrategy"
                  />
                </template>
                <el-select v-model="step.strategy" @change="normalizeReplace(step)">
                  <el-option label="前置" value="BEFORE" />
                  <el-option label="替代平台处理" value="REPLACE" />
                  <el-option label="后置" value="AFTER" />
                </el-select>
              </el-form-item>
              <el-form-item label="接口服务">
                <el-select
                  v-model="step.serviceId"
                  filterable
                  clearable
                  placeholder="留空表示只做字段映射"
                  @change="onServiceChange(step)"
                >
                  <el-option
                    v-for="service in services"
                    :key="service.id"
                    :label="`${service.sourceName} (${service.sourceCode})`"
                    :value="service.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="接口操作">
                <el-select
                  v-model="step.operationCode"
                  :disabled="!step.serviceId"
                  placeholder="选择操作"
                >
                  <el-option
                    v-for="operation in operationOptions(step.serviceId)"
                    :key="operation.code"
                    :label="`${operation.name} (${operation.code})`"
                    :value="operation.code"
                  >
                    <span>{{ operation.name }}</span>
                    <el-tag
                      class="operation-kind"
                      size="small"
                      :type="operation.kind === 'WRITE' ? 'warning' : 'info'"
                    >
                      {{ operation.kind === 'WRITE' ? '写操作' : '查询' }}
                    </el-tag>
                  </el-option>
                </el-select>
              </el-form-item>
              <el-form-item label="失败策略">
                <template #label>
                  <ConfigHelpLabel
                    label="失败策略"
                    help-key="uiEvent.failurePolicy"
                  />
                </template>
                <el-select v-model="step.failurePolicy">
                  <el-option label="停止执行" value="STOP" />
                  <el-option label="记录后继续" value="CONTINUE" />
                  <el-option label="按空结果继续" value="EMPTY" />
                </el-select>
              </el-form-item>
            </div>

            <el-collapse>
              <el-collapse-item title="输入参数映射" name="input">
                <EventMappingRows
                  v-model="step.inputRows"
                  mode="input"
                  :field-options="fieldOptions"
                />
              </el-collapse-item>
              <el-collapse-item title="结果回填" name="output">
                <EventMappingRows
                  v-model="step.outputRows"
                  mode="output"
                  :field-options="fieldOptions"
                />
              </el-collapse-item>
              <el-collapse-item title="执行条件" name="condition">
                <div class="condition-grid">
                  <el-input
                    v-model="step.conditionPath"
                    placeholder="数据路径，例如 input.status"
                  />
                  <el-select v-model="step.conditionOperator">
                    <el-option label="等于" value="equals" />
                    <el-option label="不等于" value="notEquals" />
                    <el-option label="存在" value="exists" />
                    <el-option label="为真" value="truthy" />
                  </el-select>
                  <el-input
                    v-if="!['exists', 'truthy'].includes(step.conditionOperator)"
                    v-model="step.conditionValue"
                    placeholder="比较值"
                  />
                  <el-switch
                    v-else
                    v-model="step.conditionBoolean"
                    active-text="是"
                    inactive-text="否"
                  />
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </template>

        <el-form-item label="启用">
          <el-switch v-model="editor.enabled" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">
          保存绑定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  ArrowDown,
  ArrowUp,
  Delete,
  Plus,
  Refresh
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import EventMappingRows from '@/components/ui-config/EventMappingRows.vue'
import { uiDataSourceApi, uiEventBindingApi } from '@/api/uiConfig'

const props = defineProps({
  ownerType: { type: String, required: true },
  ownerId: { type: [String, Number], default: '' },
  targetType: { type: String, default: 'OWNER' },
  targetKey: { type: [String, Number], default: '' },
  targetName: { type: String, default: '' },
  title: { type: String, default: '事件绑定' },
  allowedEvents: { type: Array, default: () => [] },
  fieldOptions: { type: Array, default: () => [] }
})

const emit = defineEmits(['changed'])

const eventLabels = {
  LIST_LOAD: '加载列表',
  LIST_EXPORT: '导出列表',
  DETAIL_LOAD: '加载详情',
  DATA_CREATE: '新增数据',
  DATA_UPDATE: '修改数据',
  DATA_DELETE: '删除数据',
  DATA_BATCH_DELETE: '批量删除',
  FORM_OPEN: '打开表单',
  FORM_SAVE: '保存表单',
  FORM_RESET: '重置表单',
  FIELD_CHANGE: '字段值变化',
  ENTITY_SELECTED: '选择实体后',
  FIELD_BUTTON_CLICK: '字段按钮点击',
  SUBFORM_LOAD: '加载子表',
  SUBFORM_SAVE: '保存子表',
  TOOLBAR_BUTTON_CLICK: '工具栏按钮点击',
  ROW_BUTTON_CLICK: '行按钮点击',
  FORM_BUTTON_CLICK: '表单按钮点击'
}

const inheritanceOptions = [
  { label: '继承并追加', value: 'INHERIT' },
  { label: '替换上级', value: 'REPLACE' },
  { label: '禁用自定义', value: 'DISABLE' }
]

const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const bindings = ref([])
const services = ref([])
const catalog = ref({ events: [] })
const operationCache = reactive({})
let rowSequence = 0

const editor = reactive(emptyEditor())

const ownerTypeLabel = computed(() => ({
  ENTITY: '实体默认配置',
  FORM: '表单覆盖',
  LIST: '列表覆盖'
}[String(props.ownerType).toUpperCase()] || props.ownerType))

const targetLabel = computed(() =>
  props.targetName || `${props.targetType}:${props.targetKey}`)

const visibleBindings = computed(() =>
  bindings.value.filter(row =>
    String(row.targetType || 'OWNER').toUpperCase() === String(props.targetType).toUpperCase()
    && String(row.targetKey || '') === String(props.targetKey || '')
  )
)

const availableEvents = computed(() => {
  const source = props.allowedEvents.length
    ? props.allowedEvents
    : catalog.value.events || Object.keys(eventLabels)
  return source.map(item => String(item).toUpperCase())
})

const editorChainItems = computed(() => chainItems({
  inheritanceMode: editor.inheritanceMode,
  stepsDocument: '',
  steps: editor.steps
}))

function emptyEditor() {
  return {
    id: '',
    expectedRevision: null,
    eventCode: '',
    inheritanceMode: 'INHERIT',
    steps: [],
    enabled: true
  }
}

function resetEditor(value = {}) {
  Object.assign(editor, emptyEditor(), value)
}

function parseJson(document, fallback) {
  if (!document) return fallback
  if (typeof document !== 'string') return document
  try {
    return JSON.parse(document)
  } catch {
    return fallback
  }
}

function normalizeStep(step, index) {
  const condition = step.condition || {}
  const operator = ['equals', 'notEquals', 'exists', 'truthy']
    .find(key => Object.prototype.hasOwnProperty.call(condition, key)) || 'equals'
  return {
    ...step,
    rowKey: `step_${++rowSequence}`,
    name: step.name || '',
    strategy: String(step.strategy || 'BEFORE').toUpperCase(),
    serviceId: step.serviceId || '',
    operationCode: step.operationCode || '',
    order: Number(step.order ?? index * 10),
    failurePolicy: String(step.failurePolicy || 'STOP').toUpperCase(),
    inputRows: mappingRows(step.inputMapping, 'input'),
    outputRows: mappingRows(step.outputMapping, 'output'),
    conditionPath: condition.path || '',
    conditionOperator: operator,
    conditionValue: condition[operator] ?? '',
    conditionBoolean: Boolean(condition[operator])
  }
}

function mappingRows(mapping, mode) {
  if (Array.isArray(mapping)) {
    return mapping.map(row => ({
      rowKey: `mapping_${++rowSequence}`,
      overwrite: 'ALWAYS',
      clearOnEmpty: true,
      transform: 'IDENTITY',
      separator: ',',
      ...row
    }))
  }
  if (!mapping || typeof mapping !== 'object') return []
  return Object.entries(mapping).map(([targetPath, sourcePath]) => ({
    rowKey: `mapping_${++rowSequence}`,
    targetPath,
    sourcePath: typeof sourcePath === 'string' ? sourcePath : '',
    overwrite: 'ALWAYS',
    clearOnEmpty: true,
    transform: 'IDENTITY',
    separator: ',',
    mode
  }))
}

async function load() {
  if (!props.ownerId) {
    bindings.value = []
    return
  }
  loading.value = true
  try {
    const [bindingRows, bindingCatalog] = await Promise.all([
      uiEventBindingApi.list(props.ownerType, String(props.ownerId)),
      uiEventBindingApi.catalog()
    ])
    bindings.value = Array.isArray(bindingRows) ? bindingRows : []
    catalog.value = bindingCatalog || {}
  } catch (error) {
    ElMessage.error(error.message || '加载事件绑定失败')
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  resetEditor({
    eventCode: availableEvents.value[0] || '',
    steps: []
  })
  await loadAvailableOperations(editor.eventCode)
  dialogVisible.value = true
}

async function openEdit(row) {
  const steps = parseJson(row.stepsDocument, row.steps || [])
  resetEditor({
    id: row.id,
    expectedRevision: row.revision,
    eventCode: row.eventCode,
    inheritanceMode: row.inheritanceMode || 'INHERIT',
    enabled: row.enabled !== false,
    steps: steps.map(normalizeStep)
  })
  await loadAvailableOperations(editor.eventCode)
  dialogVisible.value = true
}

function addStep() {
  editor.steps.push(normalizeStep({
    strategy: 'BEFORE',
    failurePolicy: 'STOP'
  }, editor.steps.length))
}

function moveStep(index, offset) {
  const target = index + offset
  if (target < 0 || target >= editor.steps.length) return
  const [step] = editor.steps.splice(index, 1)
  editor.steps.splice(target, 0, step)
}

function normalizeReplace(current) {
  if (current.strategy !== 'REPLACE') return
  editor.steps.forEach(step => {
    if (step !== current && step.strategy === 'REPLACE') {
      step.strategy = 'BEFORE'
    }
  })
}

async function onServiceChange(step) {
  step.operationCode = ''
  if (!step.serviceId) return
  const operations = operationOptions(step.serviceId)
  step.operationCode = operations.length === 1
    ? operations[0].code
    : ''
}

function operationOptions(serviceId) {
  return operationCache[serviceId] || []
}

async function handleEventChange(eventCode) {
  editor.steps.forEach(step => {
    step.serviceId = ''
    step.operationCode = ''
  })
  await loadAvailableOperations(eventCode)
}

async function loadAvailableOperations(eventCode) {
  services.value = []
  Object.keys(operationCache).forEach(key => delete operationCache[key])
  if (!props.ownerId || !eventCode) return
  const rows = await uiDataSourceApi.availableOperations({
    ownerType: String(props.ownerType).toUpperCase(),
    ownerId: String(props.ownerId),
    bindingCode: String(eventCode).toUpperCase()
  }).catch(() => [])
  const grouped = new Map()
  ;(Array.isArray(rows) ? rows : []).forEach(item => {
    if (!grouped.has(item.serviceId)) {
      grouped.set(item.serviceId, {
        id: item.serviceId,
        sourceCode: item.serviceCode,
        sourceName: item.serviceName,
        sourceType: item.sourceType,
        operations: []
      })
    }
    grouped.get(item.serviceId).operations.push({
      code: item.operationCode,
      name: item.operationName,
      kind: item.kind,
      contextType: item.contextType
    })
  })
  services.value = [...grouped.values()]
  services.value.forEach(service => {
    operationCache[service.id] = service.operations
  })
}

function serializeCondition(step) {
  if (!step.conditionPath) return {}
  return {
    path: step.conditionPath,
    [step.conditionOperator]: ['exists', 'truthy'].includes(step.conditionOperator)
      ? step.conditionBoolean
      : step.conditionValue
  }
}

function cleanMappings(rows) {
  return (rows || [])
    .filter(row => row.targetPath && (row.sourcePath || Object.prototype.hasOwnProperty.call(row, 'literal')))
    .map(({ rowKey, ...row }) => row)
}

function serializeStep(step, index) {
  return {
    stepCode: step.stepCode || undefined,
    name: step.name || undefined,
    strategy: step.strategy,
    serviceId: step.serviceId || undefined,
    operationCode: step.serviceId ? step.operationCode : undefined,
    order: (index + 1) * 10,
    condition: serializeCondition(step),
    inputMapping: Object.fromEntries(
      cleanMappings(step.inputRows).map(row => [row.targetPath, row.sourcePath])
    ),
    outputMapping: cleanMappings(step.outputRows),
    failurePolicy: step.failurePolicy
  }
}

async function save() {
  if (!editor.eventCode) {
    ElMessage.warning('请选择触发事件')
    return
  }
  const steps = editor.inheritanceMode === 'DISABLE'
    ? []
    : editor.steps.map(serializeStep)
  for (const step of steps) {
    if (step.serviceId && !step.operationCode) {
      ElMessage.warning('已选择接口服务的步骤必须选择接口操作')
      return
    }
    if (!step.serviceId && !step.outputMapping.length) {
      ElMessage.warning('未选择接口服务的步骤必须配置结果回填')
      return
    }
  }
  saving.value = true
  try {
    const payload = {
      expectedRevision: editor.expectedRevision,
      ownerType: String(props.ownerType).toUpperCase(),
      ownerId: String(props.ownerId),
      targetType: String(props.targetType).toUpperCase(),
      targetKey: String(props.targetKey || ''),
      eventCode: editor.eventCode,
      inheritanceMode: editor.inheritanceMode,
      steps,
      enabled: editor.enabled
    }
    if (editor.id) {
      await uiEventBindingApi.update(editor.id, payload)
    } else {
      await uiEventBindingApi.create(payload)
    }
    ElMessage.success('事件绑定已保存，发布页面配置后生效')
    dialogVisible.value = false
    await load()
    emit('changed')
  } catch (error) {
    ElMessage.error(error.message || '保存事件绑定失败')
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  await ElMessageBox.confirm(
    `确认删除“${eventLabel(row.eventCode)}”绑定？`,
    '删除事件绑定',
    { type: 'warning' }
  )
  await uiEventBindingApi.remove(row.id, row.revision)
  ElMessage.success('已删除')
  await load()
  emit('changed')
}

function eventLabel(code) {
  return eventLabels[code] || code
}

function inheritanceLabel(mode) {
  return {
    INHERIT: '继承并追加',
    REPLACE: '替换上级',
    DISABLE: '禁用自定义'
  }[mode] || mode
}

function inheritanceType(mode) {
  return {
    INHERIT: 'info',
    REPLACE: 'warning',
    DISABLE: 'danger'
  }[mode] || 'info'
}

function chainItems(row) {
  if (row.inheritanceMode === 'DISABLE') {
    return [{ kind: 'platform', label: '平台默认处理', type: 'success' }]
  }
  const steps = row.steps || parseJson(row.stepsDocument, [])
  const before = steps.filter(step => step.strategy === 'BEFORE')
  const replace = steps.filter(step => step.strategy === 'REPLACE')
  const after = steps.filter(step => step.strategy === 'AFTER')
  const label = step => {
    const service = services.value.find(item => item.id === step.serviceId)
    const operation = operationOptions(step.serviceId)
      .find(item => item.code === step.operationCode)
    return step.name || operation?.name || service?.sourceName || '字段映射'
  }
  return [
    ...before.map(step => ({ kind: 'step', label: label(step), type: 'info' })),
    ...(replace.length
      ? replace.map(step => ({ kind: 'replace', label: label(step), type: 'warning' }))
      : [{ kind: 'platform', label: '平台默认处理', type: 'success' }]),
    ...after.map(step => ({ kind: 'step', label: label(step), type: '' }))
  ]
}

watch(
  () => [props.ownerType, props.ownerId, props.targetType, props.targetKey],
  load,
  { immediate: true }
)

onMounted(load)
</script>

<style scoped>
.event-binding-editor {
  width: 100%;
}

.binding-toolbar,
.steps-header,
.step-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.binding-toolbar {
  margin-bottom: 12px;
}

.binding-title,
.section-title,
.primary-text {
  color: var(--el-text-color-primary);
  font-weight: 600;
}

.binding-title {
  font-size: 16px;
}

.binding-scope,
.secondary-text {
  margin-top: 3px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.toolbar-actions,
.chain-preview,
.draft-chain,
.step-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.binding-hint,
.editor-alert,
.draft-chain {
  margin-bottom: 14px;
}

.chain-preview,
.draft-chain {
  flex-wrap: wrap;
}

.chain-arrow {
  color: var(--el-text-color-placeholder);
}

.base-grid,
.step-grid,
.condition-grid {
  display: grid;
  gap: 12px;
}

.base-grid {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.step-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.condition-grid {
  grid-template-columns: 2fr 1fr 1fr;
}

.steps-header {
  margin: 14px 0 10px;
}

.draft-chain {
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.step-panel {
  margin-bottom: 14px;
  padding: 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}

.step-heading {
  margin-bottom: 12px;
}

.step-order {
  display: flex;
  flex: 0 0 28px;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--el-color-primary);
  font-weight: 600;
  background: var(--el-color-primary-light-9);
  border-radius: 4px;
}

.operation-kind {
  margin-left: 10px;
}

@media (max-width: 900px) {
  .base-grid,
  .step-grid,
  .condition-grid {
    grid-template-columns: 1fr;
  }
}
</style>
