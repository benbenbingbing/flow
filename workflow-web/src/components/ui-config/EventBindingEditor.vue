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
      v-if="!formButtonOnlyContext"
      title="除表单自定义按钮外，事件默认保留平台处理；只有执行链中加入 REPLACE 步骤，才会由自定义接口完全替代。"
      type="info"
      :closable="false"
      show-icon
      class="binding-hint"
    />
    <el-alert
      v-if="formButtonOnlyContext || formButtonEventSelected"
      title="表单自定义按钮没有平台默认动作，发布时最终继承链必须且只能包含一个主处理；主处理可使用无副作用查询或结果映射，实体写入请使用平台保存动作，外部副作用请由受控业务 Outbox 处理。"
      type="warning"
      :closable="false"
      show-icon
      class="binding-hint"
    />

    <el-empty
      v-if="!ownerId"
      description="请先选择实体、表单或列表"
    />
    <el-alert
      v-if="ownerId && outOfScopeBindings.length"
      type="warning"
      :closable="false"
      show-icon
      class="scope-warning"
      :title="`检测到 ${outOfScopeBindings.length} 条历史绑定与当前${ownerTypeLabel}范围不匹配，已禁止继续编辑；请删除后到正确的配置位置重建。`"
    />
    <el-table
      v-if="ownerId"
      v-loading="loading"
      :data="visibleBindings"
      row-key="id"
      border
    >
      <el-table-column label="触发事件" min-width="180">
        <template #default="{ row }">
          <div class="event-name-line">
            <span class="primary-text">{{ eventLabel(row.eventCode) }}</span>
            <el-tag
              v-if="!isEventAllowed(row.eventCode)"
              size="small"
              type="danger"
              effect="plain"
            >范围不匹配</el-tag>
          </div>
          <div class="secondary-text">{{ row.eventCode }}</div>
        </template>
      </el-table-column>
      <el-table-column label="继承方式" width="130">
        <template #default="{ row }">
          <el-tag :type="inheritanceType(row.inheritanceMode)" effect="plain">
            {{ inheritanceLabel(row.inheritanceMode, row) }}
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
              <span class="chain-node">
                <el-tag
                  :type="item.type"
                  :effect="item.kind === 'platform' ? 'dark' : 'plain'"
                >
                  {{ item.label }}
                </el-tag>
                <ConfigHelpLabel
                  v-if="item.kind === 'platform'"
                  label="平台默认处理"
                  :show-label="false"
                  :content="platformDefaultHelp(row.eventCode)"
                />
              </span>
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
          <el-button
            link
            type="primary"
            :disabled="!isEventAllowed(row.eventCode)"
            :title="isEventAllowed(row.eventCode)
              ? '编辑事件绑定'
              : '该历史绑定与当前配置范围不匹配，请删除后到正确位置重新配置'"
            @click="openEdit(row)"
          >编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <section
      v-if="hiddenOutOfScopeBindings.length"
      class="scope-cleanup"
    >
      <div class="section-title">待清理的历史错配绑定</div>
      <div class="secondary-text">
        以下绑定位于当前配置不支持的字段或按钮目标，常规入口无法打开；可在此直接删除后重新配置。
      </div>
      <div
        v-for="row in hiddenOutOfScopeBindings"
        :key="row.id || `${row.targetType}-${row.targetKey}-${row.eventCode}`"
        class="scope-cleanup-row"
      >
        <div class="scope-cleanup-content">
          <el-tag size="small" type="danger" effect="plain">范围不匹配</el-tag>
          <span>{{ eventLabel(row.eventCode) }} ({{ row.eventCode }})</span>
          <span class="secondary-text">{{ bindingTargetLabel(row) }}</span>
        </div>
        <el-button link type="danger" @click="remove(row)">删除</el-button>
      </div>
    </section>

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
              <el-option-group
                v-for="group in availableEventGroups"
                :key="group.label"
                :label="group.label"
              >
                <el-option
                  v-for="event in group.events"
                  :key="event"
                  :label="`${eventLabel(event)} (${event})`"
                  :value="event"
                />
              </el-option-group>
            </el-select>
            <div class="event-scope-hint">{{ eventScopeHint }}</div>
          </el-form-item>
          <el-form-item label="继承方式" required>
            <template #label>
              <ConfigHelpLabel
                label="继承方式"
                help-key="uiEvent.inheritanceMode"
                :content="formButtonInheritanceHelp"
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
          :title="formButtonExactTarget
            ? '当前按钮事件链已被清空；启用按钮无法以空链发布，请关闭按钮本身或选择其他继承方式。'
            : formButtonEventSelected
              ? '清空当前层及上级的表单按钮公共链；具体按钮仍必须通过本层或下级配置形成主处理。'
              : '禁用当前层的自定义链，仅保留平台默认处理。'"
          type="warning"
          :closable="false"
          class="editor-alert"
        />

        <template v-else>
          <div class="steps-header">
            <div>
              <div class="section-title">执行步骤</div>
              <div class="secondary-text">
                {{ formButtonEventSelected
                  ? '按前置处理、主处理、后置处理三个阶段执行；表单自定义按钮本身没有平台默认动作。'
                  : '前置接口先执行，平台默认处理居中，后置接口最后执行。' }}
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
              <span class="chain-node">
                <el-tag
                  :type="item.type"
                  :effect="item.kind === 'platform' ? 'dark' : 'plain'"
                >
                  {{ item.label }}
                </el-tag>
                <ConfigHelpLabel
                  v-if="item.kind === 'platform'"
                  label="平台默认处理"
                  :show-label="false"
                  :content="platformDefaultHelp(editor.eventCode)"
                />
              </span>
            </template>
          </div>

          <el-empty
            v-if="editor.steps.length === 0"
            :description="formButtonEventSelected
              ? '本层尚无步骤，将继承上级事件链；发布时最终链必须且只能包含一个主处理'
              : '尚未增加接口步骤，将直接执行平台默认处理'"
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
              <el-form-item :label="stepStrategyFieldLabel">
                <template #label>
                  <ConfigHelpLabel
                    :label="stepStrategyFieldLabel"
                    help-key="uiEvent.stepStrategy"
                    :content="formButtonStepStrategyHelp"
                  />
                </template>
                <el-select v-model="step.strategy" @change="normalizeReplace(step)">
                  <el-option
                    v-for="option in stepStrategyOptions"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="接口服务">
                <template #label>
                  <ConfigHelpLabel
                    label="接口服务"
                    help-key="uiDataSource.service"
                  />
                </template>
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
                    :label="`${operation.name} (${operation.code}) · ${operationContextLabel(operation.contextType)}`"
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
                    <el-tag class="operation-kind" size="small" effect="plain">
                      {{ operationContextLabel(operation.contextType) }}
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
              <el-collapse-item name="input">
                <template #title>
                  <ConfigHelpLabel
                    label="输入参数映射"
                    help-key="uiEvent.inputMapping"
                  />
                </template>
                <EventMappingRows
                  v-model="step.inputRows"
                  mode="input"
                  :field-options="fieldOptions"
                />
              </el-collapse-item>
              <el-collapse-item name="output">
                <template #title>
                  <ConfigHelpLabel
                    label="结果回填"
                    help-key="uiEvent.outputMapping"
                  />
                </template>
                <EventMappingRows
                  v-model="step.outputRows"
                  mode="output"
                  :field-options="fieldOptions"
                />
              </el-collapse-item>
              <el-collapse-item
                :title="isFormButtonMainStep(step)
                  ? hasStepCondition(step)
                    ? '执行条件（主处理需清空）'
                    : '执行条件（主处理固定无条件执行）'
                  : '执行条件'"
                name="condition"
                :disabled="isFormButtonMainStep(step) && !hasStepCondition(step)"
              >
                <div
                  v-if="isFormButtonMainStep(step) && hasStepCondition(step)"
                  class="main-condition-cleanup"
                >
                  <el-alert
                    title="主处理必须无条件执行，请清空当前执行条件后再保存。"
                    type="warning"
                    :closable="false"
                    show-icon
                  />
                  <el-button type="warning" plain @click="clearStepCondition(step)">
                    清空执行条件
                  </el-button>
                </div>
                <div
                  v-if="!isFormButtonMainStep(step) || hasStepCondition(step)"
                  class="condition-grid"
                >
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
import { getConfigFieldHelp } from '@/shared/config-field-help'
import {
  eventGroupsForScope,
  eventsForScope
} from '@/components/ui-config/uiEventScope'
import { eventBindingOperationsForEvent } from '@/components/ui-config/interfaceServiceModel'
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

const platformDefaultDescriptions = {
  LIST_LOAD: '按当前条件查询并展示列表数据',
  LIST_EXPORT: '按当前条件导出列表数据',
  DETAIL_LOAD: '读取并展示当前记录详情',
  DATA_CREATE: '完成权限、表单规则校验并新增实体记录',
  DATA_UPDATE: '完成权限、表单规则校验并更新实体记录',
  DATA_DELETE: '校验权限后删除当前实体记录',
  DATA_BATCH_DELETE: '校验权限后批量删除所选实体记录',
  FORM_OPEN: '加载记录或新增初始值并打开表单',
  FORM_SAVE: '校验并提交当前表单数据',
  FORM_RESET: '把表单恢复到本次打开时的初始值',
  FIELD_CHANGE: '更新字段值并执行平台联动与校验',
  ENTITY_SELECTED: '回填选中记录及已配置的字段映射',
  FIELD_BUTTON_CLICK: '执行该字段按钮原有的内置动作',
  SUBFORM_LOAD: '加载当前子表数据',
  SUBFORM_SAVE: '校验并保存当前子表数据',
  TOOLBAR_BUTTON_CLICK: '执行该工具栏按钮原有的内置动作',
  ROW_BUTTON_CLICK: '执行该行按钮原有的内置动作'
}

const defaultInheritanceOptions = [
  { label: '继承并追加', value: 'INHERIT' },
  { label: '替换上级', value: 'REPLACE' },
  { label: '禁用自定义', value: 'DISABLE' }
]

const formButtonInheritanceOptions = [
  { label: '继承并追加', value: 'INHERIT' },
  { label: '仅使用当前层', value: 'REPLACE' }
]

const defaultStepStrategyOptions = [
  { label: '前置', value: 'BEFORE' },
  { label: '替代平台处理', value: 'REPLACE' },
  { label: '后置', value: 'AFTER' }
]

const formButtonStepStrategyOptions = [
  { label: '前置处理', value: 'BEFORE' },
  { label: '主处理', value: 'REPLACE' },
  { label: '后置处理', value: 'AFTER' }
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

const scopeEvents = computed(() =>
  eventsForScope(props.ownerType, props.targetType))

const availableEventGroups = computed(() => {
  const catalogSource = Array.isArray(catalog.value.events)
    && catalog.value.events.length
    ? catalog.value.events
    : Object.keys(eventLabels)
  const catalogEvents = new Set(
    catalogSource
      .map(item => String(item).toUpperCase())
  )
  const explicitEvents = props.allowedEvents.length
    ? new Set(props.allowedEvents.map(item => String(item).toUpperCase()))
    : null
  return eventGroupsForScope(props.ownerType, props.targetType)
    .map(group => ({
      ...group,
      events: group.events.filter(event =>
        catalogEvents.has(event)
        && (!explicitEvents || explicitEvents.has(event)))
    }))
    .filter(group => group.events.length)
})

const availableEvents = computed(() =>
  availableEventGroups.value.flatMap(group => group.events))
const formButtonEventSelected = computed(() =>
  String(editor.eventCode || '').toUpperCase() === 'FORM_BUTTON_CLICK'
)
const formButtonOnlyContext = computed(() =>
  availableEvents.value.length === 1
  && isFormButtonEvent(availableEvents.value[0])
)
const formButtonExactTarget = computed(() =>
  formButtonEventSelected.value
  && String(props.ownerType || '').toUpperCase() === 'FORM'
  && String(props.targetType || '').toUpperCase() === 'BUTTON'
)
const inheritanceOptions = computed(() =>
  formButtonExactTarget.value
    ? formButtonInheritanceOptions
    : defaultInheritanceOptions
)
const stepStrategyFieldLabel = computed(() =>
  formButtonEventSelected.value ? '执行阶段' : '执行位置'
)
const stepStrategyOptions = computed(() =>
  formButtonEventSelected.value
    ? formButtonStepStrategyOptions
    : defaultStepStrategyOptions
)
const formButtonInheritanceHelp = computed(() =>
  formButtonExactTarget.value
    ? getConfigFieldHelp('uiEvent.formButtonInheritanceMode')
    : ''
)
const formButtonStepStrategyHelp = computed(() =>
  formButtonEventSelected.value
    ? getConfigFieldHelp('uiEvent.formButtonStepStrategy')
    : ''
)

const eventScopeHint = computed(() => {
  const owner = String(props.ownerType).toUpperCase()
  const target = String(props.targetType || 'OWNER').toUpperCase()
  if (target === 'FIELD') return '仅显示字段相关事件。'
  if (target === 'BUTTON') {
    return owner === 'LIST'
      ? '仅显示列表工具栏或行按钮事件。'
      : '仅显示表单按钮事件。'
  }
  return owner === 'LIST'
    ? '仅显示列表加载、导出、数据操作和列表按钮事件。'
    : owner === 'FORM'
      ? '仅显示表单生命周期、表单数据、字段、子表单和表单按钮事件；列表事件请到列表配置。'
      : '当前为实体默认事件，可被表单或列表的同名事件继承。'
})

const currentOutOfScopeBindings = computed(() =>
  visibleBindings.value.filter(row => !isEventAllowed(row.eventCode)))

// OWNER 页面同时兜底展示那些没有合法字段/按钮入口的历史错配，确保管理员仍有可恢复的删除路径。
const hiddenOutOfScopeBindings = computed(() => {
  if (String(props.targetType || 'OWNER').toUpperCase() !== 'OWNER') {
    return []
  }
  return bindings.value.filter(row =>
    !visibleBindings.value.includes(row)
    && !isScopeContractAllowed(row))
})

const outOfScopeBindings = computed(() => [
  ...currentOutOfScopeBindings.value,
  ...hiddenOutOfScopeBindings.value
])

const editorChainItems = computed(() => chainItems({
  eventCode: editor.eventCode,
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
  if (!isEventAllowed(row.eventCode)) return
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
    strategy: defaultNewStepStrategy(),
    failurePolicy: 'STOP'
  }, editor.steps.length))
}

/**
 * 表单自定义按钮没有平台阶段：第一个步骤直接承担主处理；主处理存在后，
 * 后续新增步骤默认接在其后，避免用户误把第二个步骤配置成另一个主处理。
 */
function defaultNewStepStrategy() {
  if (!formButtonEventSelected.value) return 'BEFORE'
  return editor.steps.some(step =>
    String(step.strategy || '').toUpperCase() === 'REPLACE')
    ? 'AFTER'
    : 'REPLACE'
}

function moveStep(index, offset) {
  const target = index + offset
  if (target < 0 || target >= editor.steps.length) return
  const [step] = editor.steps.splice(index, 1)
  editor.steps.splice(target, 0, step)
}

function normalizeReplace(current) {
  if (current.strategy !== 'REPLACE') return
  if (formButtonEventSelected.value) {
    clearStepCondition(current)
  }
  // 实体默认事件会分别投影到 FORM/LIST 执行链；两个上下文可以各自拥有
  // 一个 REPLACE，由后端按实际投影链做最终校验。FORM_BUTTON_CLICK 只投影
  // 到表单上下文，因此仍可在编辑器内直接维持唯一主处理。
  if (String(props.ownerType || '').toUpperCase() === 'ENTITY'
    && !formButtonEventSelected.value) return
  editor.steps.forEach(step => {
    if (step !== current && step.strategy === 'REPLACE') {
      step.strategy = 'BEFORE'
    }
  })
}

function isFormButtonMainStep(step) {
  return formButtonEventSelected.value
    && String(step?.strategy || '').toUpperCase() === 'REPLACE'
}

function hasStepCondition(step) {
  return Boolean(step?.conditionPath)
}

/** 主处理定义按钮的确定性主结果，不能因客户端输入条件被整体跳过。 */
function clearStepCondition(step) {
  step.conditionPath = ''
  step.conditionOperator = 'equals'
  step.conditionValue = ''
  step.conditionBoolean = false
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

function operationContextLabel(contextType) {
  return {
    FORM: '表单',
    LIST: '列表',
    ENTITY: '实体'
  }[String(contextType || '').toUpperCase()] || contextType || '未知上下文'
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
  eventBindingOperationsForEvent(rows, eventCode).forEach(item => {
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
  if (!isEventAllowed(editor.eventCode)) {
    ElMessage.warning('该事件不属于当前配置范围，请在正确的表单、列表、字段或按钮位置配置')
    return
  }
  if (formButtonExactTarget.value && editor.inheritanceMode === 'DISABLE') {
    ElMessage.warning('表单自定义按钮不能禁用事件链；如需停用，请关闭按钮本身')
    return
  }
  const steps = editor.inheritanceMode === 'DISABLE'
    ? []
    : editor.steps.map(serializeStep)
  if (formButtonEventSelected.value && steps.some(step =>
    step.strategy === 'REPLACE'
    && Object.keys(step.condition || {}).length > 0)) {
    ElMessage.warning('主处理必须无条件执行，请先清空执行条件')
    return
  }
  if (formButtonExactTarget.value) {
    const mainStepCount = steps.filter(step => step.strategy === 'REPLACE').length
    if (editor.inheritanceMode === 'REPLACE' && mainStepCount !== 1) {
      ElMessage.warning('仅使用当前层时，必须且只能配置一个主处理步骤')
      return
    }
    if (editor.inheritanceMode === 'INHERIT' && mainStepCount > 1) {
      ElMessage.warning('当前按钮层最多只能配置一个主处理步骤')
      return
    }
  }
  for (const step of steps) {
    if (step.serviceId && !step.operationCode) {
      ElMessage.warning('已选择接口服务的步骤必须选择接口操作')
      return
    }
    if (step.serviceId && !operationOptions(step.serviceId).some(operation =>
      operation.code === step.operationCode
    )) {
      ElMessage.warning(
        formButtonEventSelected.value
          ? '表单自定义按钮事件链仅允许无副作用查询，请重新选择接口操作'
          : '请选择当前事件可用的接口操作'
      )
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

function isEventAllowed(code) {
  const normalized = String(code || '').toUpperCase()
  return scopeEvents.value.includes(normalized)
    && (props.allowedEvents.length === 0
      || props.allowedEvents.some(item =>
        String(item).toUpperCase() === normalized))
}

function isScopeContractAllowed(row) {
  const ownerType = row?.ownerType || props.ownerType
  const targetType = row?.targetType || 'OWNER'
  const normalized = String(row?.eventCode || '').toUpperCase()
  return eventsForScope(ownerType, targetType).includes(normalized)
}

function bindingTargetLabel(row) {
  const targetType = String(row?.targetType || 'OWNER').toUpperCase()
  const targetName = targetType === 'FIELD'
    ? '字段'
    : targetType === 'BUTTON'
      ? '按钮'
      : '当前配置'
  return row?.targetKey
    ? `${targetName}：${row.targetKey}`
    : targetName
}

/**
 * 说明事件链中间的“平台默认处理”究竟代表哪个原有动作，并明确替代语义。
 */
function platformDefaultHelp(eventCode) {
  const code = String(eventCode || '').toUpperCase()
  if (isFormButtonEvent(code)) {
    return '表单自定义按钮没有平台默认处理，最终继承链必须且只能包含一个主处理。'
  }
  const action = platformDefaultDescriptions[code]
    || `执行“${eventLabel(code)}”原有的内置动作`
  return `平台默认处理：${action}。前置步骤在它之前执行；“替代平台处理”会跳过它；后置步骤在它成功后执行。`
}

function isFormButtonEvent(eventCode) {
  return String(eventCode || '').toUpperCase() === 'FORM_BUTTON_CLICK'
}

function inheritanceLabel(mode, row = {}) {
  if (mode === 'REPLACE' && isExactFormButtonBinding(row)) {
    return '仅使用当前层'
  }
  return {
    INHERIT: '继承并追加',
    REPLACE: '替换上级',
    DISABLE: '禁用自定义'
  }[mode] || mode
}

function isExactFormButtonBinding(row) {
  return isFormButtonEvent(row?.eventCode)
    && String(row?.ownerType || props.ownerType || '').toUpperCase() === 'FORM'
    && String(row?.targetType || props.targetType || '').toUpperCase() === 'BUTTON'
}

function formButtonStageLabel(strategy) {
  return {
    BEFORE: '前置处理',
    REPLACE: '主处理',
    AFTER: '后置处理'
  }[String(strategy || '').toUpperCase()] || strategy
}

function inheritanceType(mode) {
  return {
    INHERIT: 'info',
    REPLACE: 'warning',
    DISABLE: 'danger'
  }[mode] || 'info'
}

function chainItems(row) {
  const formButton = isFormButtonEvent(row.eventCode)
  const exactFormButton = isExactFormButtonBinding(row)
  if (row.inheritanceMode === 'DISABLE') {
    return formButton && exactFormButton
      ? [{ kind: 'disabled', label: '清空继承链（空链不可发布）', type: 'danger' }]
      : formButton
        ? [{ kind: 'disabled', label: '清空截至当前层的公共步骤', type: 'info' }]
      : [{ kind: 'platform', label: '平台默认处理', type: 'success' }]
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
  const stagedLabel = (step, strategy) => formButton
    ? `${formButtonStageLabel(strategy)}：${label(step)}`
    : label(step)
  const configured = [
    ...before.map(step => ({
      kind: 'step',
      label: stagedLabel(step, 'BEFORE'),
      type: 'info'
    })),
    ...(replace.length
      ? replace.map(step => ({
          kind: 'replace',
          label: stagedLabel(step, 'REPLACE'),
          type: 'warning'
        }))
      : (formButton
          ? (exactFormButton && row.inheritanceMode === 'REPLACE'
              ? [{
                  kind: 'invalid',
                  label: '当前层缺少主处理（不可发布）',
                  type: 'danger'
                }]
              : [])
          : [{ kind: 'platform', label: '平台默认处理', type: 'success' }])),
    ...after.map(step => ({
      kind: 'step',
      label: stagedLabel(step, 'AFTER'),
      type: ''
    }))
  ]
  if (configured.length || !formButton) return configured
  return [{
    kind: 'inherit',
    label: exactFormButton
      ? '继承上级步骤（发布时校验）'
      : row.inheritanceMode === 'REPLACE'
        ? '当前层不提供公共步骤，由具体按钮补充主处理'
        : '继承上级公共步骤',
    type: 'info'
  }]
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
.chain-node,
.event-name-line,
.step-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.binding-hint,
.editor-alert,
.draft-chain,
.scope-warning {
  margin-bottom: 14px;
}

.scope-warning {
  margin-top: 14px;
}

.event-scope-hint {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}

.scope-cleanup {
  margin-top: 14px;
  padding: 12px;
  border: 1px solid var(--el-color-warning-light-5);
  border-radius: 6px;
  background: var(--el-color-warning-light-9);
}

.scope-cleanup-row,
.scope-cleanup-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.scope-cleanup-row {
  justify-content: space-between;
  margin-top: 10px;
}

.chain-preview,
.draft-chain {
  flex-wrap: wrap;
}

.chain-arrow {
  color: var(--el-text-color-placeholder);
}

.chain-node {
  gap: 4px;
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

.main-condition-cleanup {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.main-condition-cleanup .el-alert {
  flex: 1;
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
