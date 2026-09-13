<template>
  <div class="form-button-config-panel">
    <div class="mode-toolbar">
      <el-segmented
        v-model="activeMode"
        :options="modeOptions"
        size="default"
      />
      <span class="mode-tip">当前预览模式：{{ modeLabel(activeMode) }}</span>
    </div>

    <el-alert
      v-if="systemEntity"
      type="warning"
      :closable="false"
      show-icon
      title="平台系统表只提供只读查看，操作栏固定为关闭按钮。"
      class="section-alert"
    />

    <section class="config-section">
      <div class="section-heading">
        <div>
          <h3>平台默认按钮</h3>
          <p>动作和权限语义由平台约定，可调整名称、样式、顺序、适用模式和条件。</p>
        </div>
      </div>

      <el-table :data="builtInRows" border size="small">
        <el-table-column label="按钮" min-width="150">
          <template #default="{ row }">
            <div class="button-name-cell">
              <strong>{{ builtInLabel(row.key) }}</strong>
              <el-tag size="small" effect="plain">平台约定</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="72" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="builtInValue(row.key, 'enabled', true)"
              :disabled="systemEntity"
              @change="setBuiltInValue(row.key, 'enabled', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="当前模式名称" min-width="170">
          <template #default="{ row }">
            <el-input
              :model-value="builtInModeLabel(row.key)"
              size="small"
              @update:model-value="setBuiltInLabel(row.key, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="样式" width="120">
          <template #default="{ row }">
            <el-select
              :model-value="builtInValue(row.key, 'buttonType', row.buttonType)"
              size="small"
              @update:model-value="setBuiltInValue(row.key, 'buttonType', $event)"
            >
              <el-option
                v-for="option in buttonTypeOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="顺序" width="100">
          <template #default="{ row }">
            <el-input-number
              :model-value="builtInValue(row.key, 'sort', row.sort)"
              :min="0"
              :max="999"
              controls-position="right"
              size="small"
              @update:model-value="setBuiltInValue(row.key, 'sort', $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="适用模式" min-width="210">
          <template #default="{ row }">
            <el-select
              :model-value="builtInModes(row.key)"
              multiple
              collapse-tags
              size="small"
              style="width: 100%"
              @update:model-value="setBuiltInValue(row.key, 'enabledModes', $event)"
            >
              <el-option
                v-for="mode in builtInModeOptions(row.key)"
                :key="mode.value"
                :label="mode.label"
                :value="mode.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="条件" min-width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="configureBuiltInRule(row.key)">
              {{ ruleSummary(builtInOverride(row.key)) }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="!hasBuiltInOverride(row.key)"
              @click="restoreBuiltIn(row.key)"
            >
              恢复默认
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section v-if="!systemEntity" class="config-section custom-section">
      <div class="section-heading">
        <div>
          <h3>自定义按钮</h3>
          <p>每个按钮必须配置稳定编码、权限码和事件链，不执行任意前端脚本或 URL。</p>
        </div>
        <el-button type="primary" :icon="Plus" @click="addCustomButton">
          添加按钮
        </el-button>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="custom-button-guide"
      >
        <template #title>
          先确定按钮在哪些模式和位置出现，再配置权限与显示条件，最后绑定事件链。
          稳定编码发布后请保持不变。
        </template>
      </el-alert>

      <el-table :data="draft.customButtons" border size="small">
        <el-table-column label="启用" width="64" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :disabled="row.enabled === false
                && (!formId || isLocallyCreatedButton(row))"
              @change="handleEnabledChange(row, $event)"
            />
          </template>
        </el-table-column>
        <el-table-column label="按钮名称" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.label" size="small" placeholder="按钮名称" />
            <div v-if="labelError(row)" class="validation-message">
              {{ labelError(row) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="稳定编码" min-width="180">
          <template #header>
            <ConfigHelpLabel
              label="稳定编码"
              content="按钮的永久技术标识，用于事件绑定、发布差异和历史版本。以小写字母开头，仅支持小写字母、数字、下划线和短横线；发布后不要修改。"
            />
          </template>
          <template #default="{ row }">
            <el-input
              v-model="row.key"
              size="small"
              placeholder="例如 generate_report"
              :disabled="isKeyLocked(row)"
              :class="{ 'is-invalid': keyError(row) }"
            />
            <div v-if="isKeyLocked(row)" class="stable-key-lock">
              {{ keyLockReason(row) }}
            </div>
            <div v-if="keyError(row)" class="validation-message">
              {{ keyError(row) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="适用模式" min-width="180">
          <template #header>
            <ConfigHelpLabel
              label="适用模式"
              content="决定按钮在哪些运行场景出现。新增、编辑、审批和查看模式可同时选择多个。"
            />
          </template>
          <template #default="{ row }">
            <el-select
              v-model="row.modes"
              multiple
              collapse-tags
              size="small"
              style="width: 100%"
            >
              <el-option
                v-for="mode in FORM_ACTION_MODES"
                :key="mode.value"
                :label="mode.label"
                :value="mode.value"
              />
            </el-select>
            <div v-if="!row.modes?.length" class="validation-message">
              必须选择适用模式
            </div>
          </template>
        </el-table-column>
        <el-table-column label="位置" min-width="160">
          <template #header>
            <ConfigHelpLabel
              label="位置"
              content="底部操作栏显示在表单底部；动作插槽显示在设计器中指定的 ACTION_SLOT 节点位置。"
            />
          </template>
          <template #default="{ row }">
            <el-select
              v-model="row.placement"
              size="small"
              style="width: 100%"
              @change="handlePlacementChange(row)"
            >
              <el-option label="底部操作栏" value="FOOTER" />
              <el-option
                label="动作插槽"
                value="ACTION_SLOT"
                :disabled="!actionSlotOptions.length"
              />
            </el-select>
            <el-select
              v-if="row.placement === 'ACTION_SLOT'"
              v-model="row.slotKey"
              size="small"
              placeholder="选择动作插槽"
              style="width: 100%; margin-top: 6px"
            >
              <el-option
                v-for="slot in actionSlotOptions"
                :key="slot.value"
                :label="slot.label"
                :value="slot.value"
              />
            </el-select>
            <div
              v-if="row.placement === 'ACTION_SLOT' && !validSlotKey(row.slotKey)"
              class="validation-message"
            >
              请选择当前表单中的动作插槽
            </div>
          </template>
        </el-table-column>
        <el-table-column label="权限码" min-width="220">
          <template #header>
            <ConfigHelpLabel
              label="权限码"
              content="运行时先校验当前用户是否拥有该权限。可以从实体权限中选择，也可以输入已注册的权限码；启用按钮时必填。"
            />
          </template>
          <template #default="{ row }">
            <el-select
              v-model="row.perm"
              filterable
              allow-create
              clearable
              size="small"
              placeholder="选择或输入权限码"
              style="width: 100%"
            >
              <el-option
                v-for="option in permissionOptions"
                :key="option.code"
                :label="`${option.label || option.code} · ${option.code}`"
                :value="option.code"
              />
            </el-select>
            <div v-if="row.enabled && permissionError(row)" class="validation-message">
              {{ permissionError(row) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="顺序" width="90">
          <template #default="{ row }">
            <el-input-number
              v-model="row.sort"
              :min="0"
              :max="999"
              controls-position="right"
              size="small"
            />
          </template>
        </el-table-column>
        <el-table-column label="配置" width="210" align="center" fixed="right">
          <template #header>
            <ConfigHelpLabel
              label="配置"
              content="事件链定义点击后执行什么；更多中统一配置图标、样式、表单校验、二次确认以及显示和启用条件。"
            />
          </template>
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="isEventConfigurationBlocked(row)"
              :title="isLocallyCreatedButton(row)
                ? '先保存表单草稿，再配置事件链'
                : '配置按钮点击事件链'"
              @click="configureEvent(row)"
            >
              事件链
            </el-button>
            <el-tag
              v-if="buttonBindings(row).length"
              size="small"
              type="success"
              effect="plain"
            >
              {{ buttonBindings(row).length }} 条
            </el-tag>
            <el-button
              link
              type="primary"
              :title="`更多设置（${ruleSummary(row)}）`"
              @click="openAdvanced(row)"
            >
              更多
            </el-button>
            <el-button link type="danger" @click="removeCustomButton(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-alert
        v-if="!formId && draft.customButtons.length"
        type="info"
        :closable="false"
        show-icon
        title="先保存表单草稿，随后即可为自定义按钮配置事件链。"
        class="section-alert"
      />
    </section>

    <ActionRuleEditorDialog
      ref="ruleEditorRef"
      :entity-fields="entityFields"
      :statuses="statuses"
      :allow-custom-conditions="false"
      @save="saveRule"
    />

    <EventBindingDialog
      ref="eventBindingDialogRef"
      owner-type="FORM"
      :owner-id="formId || ''"
      owner-label="表单"
      :field-options="eventFieldOptions"
      @changed="handleEventBindingsChanged"
    />

    <el-dialog
      v-model="advancedVisible"
      title="自定义按钮设置"
      width="1180px"
      top="5vh"
      class="form-button-advanced-dialog"
      :close-on-click-modal="false"
      append-to-body
      @closed="resetAdvanced"
    >
      <div v-if="advancedButton" class="advanced-dialog-content">
        <section class="advanced-basic-section" aria-label="基础设置">
          <header class="advanced-section-heading">
            <h3>基础设置</h3>
            <p>设置按钮的视觉样式和执行前交互。</p>
          </header>
          <el-form label-width="110px" class="advanced-settings-form">
            <el-form-item label="图标">
              <el-select
                v-model="advancedButton.icon"
                clearable
                filterable
                placeholder="不显示图标"
                style="width: 100%"
              >
                <el-option
                  v-for="icon in iconOptions"
                  :key="icon"
                  :label="icon"
                  :value="icon"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="按钮样式">
              <template #label>
                <ConfigHelpLabel
                  label="按钮样式"
                  content="只影响视觉强调程度，不改变权限、条件或事件执行逻辑。危险操作建议使用“危险”样式并开启二次确认。"
                />
              </template>
              <el-select v-model="advancedButton.buttonType" style="width: 100%">
                <el-option
                  v-for="option in buttonTypeOptions"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
            </el-form-item>
            <el-form-item :error="buttonAppearanceError(advancedButton)">
              <template #label>
                <ConfigHelpLabel
                  label="按钮外观"
                  content="四种外观互斥；默认保持当前效果，圆形按钮仅显示图标，按钮名称仍用于提示和无障碍识别。"
                />
              </template>
              <el-radio-group
                v-model="advancedButton.buttonAppearance"
                class="button-appearance-options"
              >
                <el-radio-button
                  v-for="option in buttonAppearanceOptions"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </el-radio-button>
              </el-radio-group>
            </el-form-item>
            <el-form-item label="执行前校验">
              <template #label>
                <ConfigHelpLabel
                  label="执行前校验"
                  content="开启后先执行当前表单的必填和格式校验；校验通过后才运行按钮事件链。"
                />
              </template>
              <el-switch v-model="advancedButton.validateBeforeExecute" />
              <span class="field-help">开启后先校验当前表单，再执行事件链。</span>
            </el-form-item>
            <el-form-item label="二次确认">
              <el-switch v-model="advancedButton.confirm.enabled" />
            </el-form-item>
            <el-form-item
              v-if="advancedButton.confirm.enabled"
              label="确认提示"
              class="advanced-settings-form__wide"
            >
              <el-input
                v-model="advancedButton.confirm.message"
                placeholder="确认执行该操作？"
              />
            </el-form-item>
          </el-form>
        </section>

        <el-divider content-position="left">显示与启用条件</el-divider>
        <ActionRuleEditorPanel
          ref="advancedRuleEditorRef"
          v-model="advancedRule"
          :entity-fields="entityFields"
          :statuses="statuses"
          :allow-custom-conditions="false"
        />
      </div>
      <template #footer>
        <el-button @click="advancedVisible = false">取消</el-button>
        <el-button type="primary" @click="completeAdvanced">保存设置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { getEntityPermissionOptions } from '@/api/system/menu'
import { getEntityStatusList } from '@/api/entityStatus'
import { resolveEntityPermissionOptions } from '@/utils/entityActionRuleRegistry'
import { useFormButtonReferences } from '@/composables/useFormButtonReferences'
import ActionRuleEditorDialog from '@/components/ActionRuleEditorDialog.vue'
import ActionRuleEditorPanel from '@/components/ActionRuleEditorPanel.vue'
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'
import EventBindingDialog from '@/components/ui-config/EventBindingDialog.vue'
import {
  ACTION_RULE_VERSION,
  createEmptyActionRule,
  summarizeActionRule,
  toEditableActionRuleRoot
} from '@/shared/action-rules'
import {
  FORM_ACTION_MODES,
  FORM_ACTION_KEY_PATTERN,
  FORM_ACTION_PERMISSION_PATTERN,
  FORM_BUTTON_ICONS,
  FORM_BUILT_IN_ACTIONS,
  isRegisteredFormButtonIcon,
  normalizeCustomButton,
  normalizeFormActionBar
} from '@/shared/form-actions'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  entityCode: { type: String, default: '' },
  entityFields: { type: Array, default: () => [] },
  formId: { type: [String, Number], default: '' },
  activeReleaseId: { type: [String, Number], default: '' },
  eventBindingRevision: { type: Number, default: 0 },
  persistenceRevision: { type: Number, default: 0 },
  persistedButtonKeys: { type: Array, default: () => [] },
  nodes: { type: Array, default: () => [] },
  createActionSlot: { type: Function, default: null },
  allowActionSlotCreate: { type: Boolean, default: true },
  systemEntity: Boolean
})

const emit = defineEmits(['update:modelValue', 'changed'])
const draft = ref(normalizeFormActionBar(props.modelValue))
const activeMode = ref(props.systemEntity ? 'view' : 'create')
const permissionOptions = ref([])
const statuses = ref([])
const ruleEditorRef = ref()
const eventBindingDialogRef = ref()
const advancedVisible = ref(false)
const advancedButton = ref(null)
const advancedTarget = ref(null)
const advancedRule = ref(createEmptyActionRule())
const advancedRuleEditorRef = ref()
const {
  buttonBindings,
  isKeyLocked,
  isLocallyCreatedButton,
  keyLockReason,
  loadButtonReferences,
  markLocallyCreated,
  persistedButtonKeySet,
  referenceLoadFailed,
  referenceLoading
} = useFormButtonReferences(props)

const buttonTypeOptions = [
  { label: '默认', value: 'default' },
  { label: '主要', value: 'primary' },
  { label: '成功', value: 'success' },
  { label: '警告', value: 'warning' },
  { label: '危险', value: 'danger' },
  { label: '信息', value: 'info' }
]
const buttonAppearanceOptions = [
  { label: '默认', value: 'DEFAULT' },
  { label: '朴素', value: 'PLAIN' },
  { label: '圆角', value: 'ROUND' },
  { label: '圆形', value: 'CIRCLE' }
]
const iconOptions = [...FORM_BUTTON_ICONS]
const modeOptions = computed(() =>
  (props.systemEntity
    ? FORM_ACTION_MODES.filter(mode => mode.value === 'view')
    : FORM_ACTION_MODES
  ).map(mode => ({ label: mode.label, value: mode.value }))
)
const builtInRows = computed(() =>
  Object.values(FORM_BUILT_IN_ACTIONS)
    .filter(button => props.systemEntity ? button.key === 'close' : true)
    .filter(button => button.modes.includes(activeMode.value))
)
const actionSlotOptions = computed(() =>
  props.nodes
    .filter(node => String(node?.nodeType || '').toUpperCase() === 'ACTION_SLOT')
    .map(node => ({
      value: node.nodeKey,
      label: `${node.nodeLabel || node.fieldLabel || node.label || '动作插槽'} (${node.nodeKey})`
    }))
    .filter(option => option.value)
)
const eventFieldOptions = computed(() =>
  props.entityFields
    .filter(field => field.fieldCode)
    .map(field => ({
      label: field.fieldName || field.fieldCode,
      value: field.fieldCode
    }))
)

watch(
  () => props.modelValue,
  value => {
    const next = normalizeFormActionBar(value)
    if (fingerprint(next) !== fingerprint(draft.value)) {
      draft.value = next
    }
  },
  { deep: true }
)

watch(
  draft,
  value => emit('update:modelValue', cloneValue(value)),
  { deep: true }
)

watch(
  () => props.systemEntity,
  value => {
    if (value) activeMode.value = 'view'
  }
)

onMounted(loadPermissionOptions)
watch(() => props.entityCode, loadPermissionOptions)
async function loadPermissionOptions() {
  if (!props.entityCode) {
    permissionOptions.value = []
    statuses.value = []
    return
  }
  try {
    const [serverOptions, extensionOptions, statusList] = await Promise.all([
      getEntityPermissionOptions(props.entityCode),
      resolveEntityPermissionOptions({
        entityCode: props.entityCode,
        type: 'form'
      }),
      getEntityStatusList(props.entityCode)
    ])
    const merged = [...(serverOptions || []), ...(extensionOptions || [])]
    permissionOptions.value = merged.filter((option, index) =>
      option?.code
      && merged.findIndex(item => item?.code === option.code) === index
    )
    statuses.value = statusList || []
  } catch (error) {
    console.error('加载表单按钮权限选项失败:', error)
    permissionOptions.value = []
    statuses.value = []
  }
}

function modeLabel(mode) {
  return FORM_ACTION_MODES.find(item => item.value === mode)?.label || mode
}

function builtInLabel(key) {
  const labels = {
    close: '关闭/取消',
    reset: '重置',
    save: '保存',
    saveAndStart: '保存并发起流程',
    submitApproval: '提交审批'
  }
  return labels[key] || key
}

function defaultModeLabel(key) {
  if (key === 'close') {
    return ['create', 'edit'].includes(activeMode.value) ? '取消' : '关闭'
  }
  if (key === 'save') {
    return activeMode.value === 'create' ? '保存' : '保存修改'
  }
  return FORM_BUILT_IN_ACTIONS[key]?.label || builtInLabel(key)
}

function builtInOverride(key) {
  return draft.value.builtInOverrides[key] || {}
}

function hasBuiltInOverride(key) {
  return Object.prototype.hasOwnProperty.call(
    draft.value.builtInOverrides,
    key
  )
}

function ensureBuiltInOverride(key) {
  if (!hasBuiltInOverride(key)) {
    draft.value.builtInOverrides[key] = {}
  }
  return draft.value.builtInOverrides[key]
}

function builtInValue(key, property, fallback) {
  const override = builtInOverride(key)
  return override[property] === undefined ? fallback : override[property]
}

function setBuiltInValue(key, property, value) {
  ensureBuiltInOverride(key)[property] = value
}

function builtInModeLabel(key) {
  return builtInOverride(key).labelByMode?.[activeMode.value]
    || defaultModeLabel(key)
}

function setBuiltInLabel(key, value) {
  const override = ensureBuiltInOverride(key)
  override.labelByMode = {
    ...(override.labelByMode || {}),
    [activeMode.value]: value
  }
}

function builtInModes(key) {
  return builtInOverride(key).enabledModes
    || [...(FORM_BUILT_IN_ACTIONS[key]?.modes || [])]
}

function builtInModeOptions(key) {
  const allowed = FORM_BUILT_IN_ACTIONS[key]?.modes || []
  return FORM_ACTION_MODES.filter(mode => allowed.includes(mode.value))
}

function restoreBuiltIn(key) {
  delete draft.value.builtInOverrides[key]
}

function configureBuiltInRule(key) {
  const target = ensureBuiltInOverride(key)
  ruleEditorRef.value?.open(target)
}

/**
 * 新按钮统一放入底部操作栏；如需内嵌，用户再从位置列选择已有动作插槽。
 * 新按钮默认停用，避免尚未配置事件链时形成可发布的空操作。
 */
function addCustomButton() {
  const button = normalizeCustomButton({
    key: uniqueButtonKey(),
    label: '自定义按钮',
    modes: [activeMode.value],
    sort: nextSort(),
    perm: '',
    enabled: false,
    placement: 'FOOTER'
  }, draft.value.customButtons.length)
  markLocallyCreated(button)
  draft.value.customButtons.push(button)
  ElMessage.success('已添加按钮，请在“位置”列选择显示位置，保存草稿后配置事件链')
}

async function removeCustomButton(button) {
  if (referenceLoading.value) {
    ElMessage.info('正在检查按钮引用，请稍候')
    return
  }
  if (referenceLoadFailed.value) {
    ElMessage.warning('暂时无法确认按钮引用，为避免遗留事件绑定，当前不能删除')
    return
  }
  const bindings = buttonBindings(button)
  if (bindings.length) {
    ElMessage.warning(
      `按钮仍有 ${bindings.length} 条事件绑定，请先在“事件链”中删除绑定`
    )
    configureEvent(button)
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除按钮“${button.label || button.key}”？`,
      '删除按钮',
      { type: 'warning' }
    )
    const index = draft.value.customButtons.indexOf(button)
    if (index >= 0) draft.value.customButtons.splice(index, 1)
  } catch {
    // 用户取消删除。
  }
}

function uniqueButtonKey() {
  const prefix = 'custom_action'
  let index = draft.value.customButtons.length + 1
  let candidate = `${prefix}_${index}`
  while (draft.value.customButtons.some(button => button.key === candidate)
      || persistedButtonKeySet.value.has(candidate)) {
    index += 1
    candidate = `${prefix}_${index}`
  }
  return candidate
}

function nextSort() {
  const values = draft.value.customButtons.map(button => Number(button.sort) || 0)
  return Math.max(40, ...values) + 10
}

function keyError(button) {
  const key = String(button?.key || '')
  if (!key) return '请输入稳定编码'
  if (!FORM_ACTION_KEY_PATTERN.test(key)) {
    return '以小写字母开头，仅支持小写字母、数字、_、-'
  }
  if (Object.hasOwn(FORM_BUILT_IN_ACTIONS, key)) {
    return '不能占用平台默认按钮编码'
  }
  const duplicates = draft.value.customButtons.filter(item => item.key === key)
  return duplicates.length > 1 ? '稳定编码不能重复' : ''
}

function permissionError(button) {
  const permission = String(button?.perm || '').trim()
  if (!permission) return '启用时必须配置权限码'
  if (permission.length > 200
      || !FORM_ACTION_PERMISSION_PATTERN.test(permission)) {
    return '权限码至少包含一个冒号，仅支持字母、数字、_、-、.'
  }
  return ''
}

function labelError(button) {
  const label = String(button?.label || '').trim()
  if (!label) return '请输入按钮名称'
  return label.length > 60 ? '按钮名称不能超过 60 个字符' : ''
}

function validSlotKey(slotKey) {
  return actionSlotOptions.value.some(option =>
    String(option.value) === String(slotKey || '')
  )
}

function handleEnabledChange(button, enabled) {
  if (enabled && (!props.formId || isLocallyCreatedButton(button))) {
    button.enabled = false
    ElMessage.warning('请先保存表单草稿并配置事件链，再启用按钮')
  }
}

function isEventConfigurationBlocked(button) {
  return !props.formId
    || isLocallyCreatedButton(button)
    || Boolean(keyError(button))
}

function handlePlacementChange(button) {
  if (button.placement !== 'ACTION_SLOT') {
    button.slotKey = ''
  } else if (!button.slotKey && actionSlotOptions.value.length) {
    button.slotKey = actionSlotOptions.value[0].value
  }
}

function configureEvent(button) {
  const error = keyError(button)
  if (error) {
    ElMessage.warning(error)
    return
  }
  if (!props.formId) {
    ElMessage.warning('请先保存表单草稿')
    return
  }
  if (isLocallyCreatedButton(button)) {
    ElMessage.warning('请先保存当前按钮，再配置事件链')
    return
  }
  eventBindingDialogRef.value?.openButton(button)
}

async function handleEventBindingsChanged() {
  await loadButtonReferences()
  emit('changed')
}

function saveRule({ button, rule }) {
  // 保留空 v2 规则，表示用户明确选择“始终显示且可用”，避免默认规则被再次回填。
  button.availabilityRule = rule
}

function ruleSummary(target) {
  return summarizeActionRule(target?.availabilityRule)
}

/**
 * “更多”使用独立按钮与条件草稿，只有完整校验并保存后才回写表格数据。
 * 旧版规则不在此处转换，项目上线前由配置人员清理并按 v2 结构重设。
 */
function openAdvanced(button) {
  const existingRule = button?.availabilityRule
  if (existingRule && existingRule.version !== ACTION_RULE_VERSION) {
    ElMessage.error('检测到旧版按钮条件，请先清理旧配置后再通过“更多”重新设置')
    return
  }

  const buttonDraft = cloneValue(button)
  if (!buttonDraft.confirm) {
    buttonDraft.confirm = { enabled: false, message: '' }
  }
  const ruleDraft = existingRule
    ? cloneValue(existingRule)
    : createEmptyActionRule()
  ruleDraft.visibleWhen = toEditableActionRuleRoot(ruleDraft.visibleWhen)
  ruleDraft.enabledWhen = toEditableActionRuleRoot(ruleDraft.enabledWhen)

  advancedTarget.value = button
  advancedButton.value = buttonDraft
  advancedRule.value = ruleDraft
  advancedVisible.value = true
}

function completeAdvanced() {
  const appearanceError = buttonAppearanceError(advancedButton.value)
  if (appearanceError) {
    ElMessage.warning(appearanceError)
    return
  }
  const result = advancedRuleEditorRef.value?.buildValidatedRule()
  if (!result || result.error) {
    ElMessage.warning(result?.error || '按钮条件编辑器尚未就绪，请稍后重试')
    return
  }
  if (!advancedTarget.value || !advancedButton.value) return

  const savedButton = cloneValue(advancedButton.value)
  savedButton.availabilityRule = result.rule
  Object.assign(advancedTarget.value, savedButton)
  advancedVisible.value = false
}

/** 圆形按钮只展示图标，保存前必须保证用户仍能看见可操作内容。 */
function buttonAppearanceError(button) {
  return button?.buttonAppearance === 'CIRCLE'
    && !isRegisteredFormButtonIcon(button?.icon)
    ? '圆形按钮必须选择平台支持的图标'
    : ''
}

function resetAdvanced() {
  advancedTarget.value = null
  advancedButton.value = null
  advancedRule.value = createEmptyActionRule()
}

function fingerprint(value) {
  return JSON.stringify(value || {})
}

function cloneValue(value) {
  return JSON.parse(JSON.stringify(value))
}
</script>

<style scoped src="./FormButtonConfigPanel.scss"></style>
