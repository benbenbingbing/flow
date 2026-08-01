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
          新增按钮
        </el-button>
      </div>

      <el-table :data="draft.customButtons" border size="small">
        <el-table-column label="启用" width="64" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" />
          </template>
        </el-table-column>
        <el-table-column label="按钮名称" min-width="150">
          <template #default="{ row }">
            <el-input v-model="row.label" size="small" placeholder="按钮名称" />
          </template>
        </el-table-column>
        <el-table-column label="稳定编码" min-width="180">
          <template #default="{ row }">
            <el-input
              v-model="row.key"
              size="small"
              placeholder="例如 generate_report"
              :class="{ 'is-invalid': keyError(row) }"
            />
            <div v-if="keyError(row)" class="validation-message">
              {{ keyError(row) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="适用模式" min-width="180">
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
          </template>
        </el-table-column>
        <el-table-column label="位置" min-width="160">
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
          </template>
        </el-table-column>
        <el-table-column label="权限码" min-width="220">
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
            <div v-if="row.enabled && !row.perm" class="validation-message">
              启用时必须配置权限码
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
        <el-table-column label="配置" width="240" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="!formId || Boolean(keyError(row))"
              @click="configureEvent(row)"
            >
              事件链
            </el-button>
            <el-button link type="primary" @click="configureCustomRule(row)">
              条件
            </el-button>
            <el-button link type="primary" @click="openAdvanced(row)">
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
      @save="saveRule"
    />

    <EventBindingDialog
      ref="eventBindingDialogRef"
      owner-type="FORM"
      :owner-id="formId || ''"
      owner-label="表单"
      :field-options="eventFieldOptions"
    />

    <el-dialog
      v-model="advancedVisible"
      title="自定义按钮设置"
      width="620px"
      append-to-body
    >
      <el-form v-if="advancedButton" label-width="110px">
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
          <el-select v-model="advancedButton.buttonType" style="width: 100%">
            <el-option
              v-for="option in buttonTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="执行前校验">
          <el-switch v-model="advancedButton.validateBeforeExecute" />
          <span class="field-help">开启后先校验当前表单，再执行事件链。</span>
        </el-form-item>
        <el-form-item label="二次确认">
          <el-switch v-model="advancedButton.confirm.enabled" />
        </el-form-item>
        <el-form-item
          v-if="advancedButton.confirm.enabled"
          label="确认提示"
        >
          <el-input
            v-model="advancedButton.confirm.message"
            placeholder="确认执行该操作？"
          />
        </el-form-item>
        <el-form-item label="适用条件">
          <el-button type="primary" text @click="configureCustomRule(advancedButton)">
            {{ ruleSummary(advancedButton) }}
          </el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="primary" @click="advancedVisible = false">完成</el-button>
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
import ActionRuleEditorDialog from '@/components/ActionRuleEditorDialog.vue'
import EventBindingDialog from '@/components/ui-config/EventBindingDialog.vue'
import {
  FORM_ACTION_MODES,
  FORM_BUILT_IN_ACTIONS,
  normalizeCustomButton,
  normalizeFormActionBar
} from '@/shared/form-actions'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  entityCode: { type: String, default: '' },
  entityFields: { type: Array, default: () => [] },
  formId: { type: [String, Number], default: '' },
  nodes: { type: Array, default: () => [] },
  systemEntity: Boolean
})

const emit = defineEmits(['update:modelValue'])
const draft = ref(normalizeFormActionBar(props.modelValue))
const activeMode = ref(props.systemEntity ? 'view' : 'create')
const permissionOptions = ref([])
const statuses = ref([])
const ruleEditorRef = ref()
const eventBindingDialogRef = ref()
const advancedVisible = ref(false)
const advancedButton = ref(null)

const buttonTypeOptions = [
  { label: '默认', value: 'default' },
  { label: '主要', value: 'primary' },
  { label: '成功', value: 'success' },
  { label: '警告', value: 'warning' },
  { label: '危险', value: 'danger' },
  { label: '信息', value: 'info' }
]
const iconOptions = [
  'Check', 'Close', 'Document', 'Download', 'Edit', 'Link',
  'Message', 'Plus', 'Printer', 'Promotion', 'Refresh',
  'RefreshLeft', 'Select', 'Setting', 'Upload', 'View'
]
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
      label: `${node.nodeLabel || node.label || '动作插槽'} (${node.nodeKey})`
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
  ruleEditorRef.value?.open(target, 'HIDE')
}

function addCustomButton() {
  const button = normalizeCustomButton({
    key: uniqueButtonKey(),
    label: '自定义按钮',
    modes: [activeMode.value],
    sort: nextSort(),
    perm: ''
  }, draft.value.customButtons.length)
  draft.value.customButtons.push(button)
}

async function removeCustomButton(button) {
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
  while (draft.value.customButtons.some(button => button.key === candidate)) {
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
  if (!/^[a-z][a-z0-9_-]{0,63}$/.test(key)) {
    return '以小写字母开头，仅支持小写字母、数字、_、-'
  }
  const duplicates = draft.value.customButtons.filter(item => item.key === key)
  return duplicates.length > 1 ? '稳定编码不能重复' : ''
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
  eventBindingDialogRef.value?.openButton(button)
}

function configureCustomRule(button) {
  ruleEditorRef.value?.open(button, 'HIDE')
}

function saveRule({ button, rule }) {
  button.availabilityRule = rule?.root ? rule : null
}

function ruleSummary(target) {
  if (!target?.availabilityRule?.root) return '始终可操作'
  return target.availabilityRule.message || '已配置条件'
}

function openAdvanced(button) {
  if (!button.confirm) {
    button.confirm = { enabled: false, message: '' }
  }
  advancedButton.value = button
  advancedVisible.value = true
}

function fingerprint(value) {
  return JSON.stringify(value || {})
}

function cloneValue(value) {
  return JSON.parse(JSON.stringify(value))
}
</script>

<style scoped>
.form-button-config-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.mode-toolbar,
.section-heading,
.button-name-cell {
  display: flex;
  align-items: center;
}

.mode-toolbar {
  gap: 12px;
  justify-content: space-between;
}

.mode-tip,
.config-section p,
.field-help {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.config-section {
  width: 100%;
}

.section-heading {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.section-heading h3 {
  margin: 0 0 4px;
  font-size: 16px;
}

.section-heading p {
  margin: 0;
}

.button-name-cell {
  gap: 8px;
}

.section-alert {
  margin-top: 12px;
}

.validation-message {
  margin-top: 4px;
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 1.3;
}

.is-invalid :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

.field-help {
  margin-left: 10px;
}
</style>
