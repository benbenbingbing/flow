<template>
  <el-dialog
    v-model="visible"
    title="按钮显示与启用条件"
    width="1180px"
    top="5vh"
    class="action-rule-dialog"
    :close-on-click-modal="false"
  >
    <ActionRuleEditorPanel
      ref="editorPanelRef"
      v-model="rule"
      :entity-fields="entityFields"
      :statuses="statuses"
      :allow-custom-conditions="allowCustomConditions"
    />

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="save">保存显示与启用条件</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import ActionRuleEditorPanel from './ActionRuleEditorPanel.vue'
import {
  ACTION_RULE_VERSION,
  createEmptyActionRule,
  toEditableActionRuleRoot
} from '@/shared/action-rules'

defineProps({
  entityFields: { type: Array, default: () => [] },
  statuses: { type: Array, default: () => [] },
  allowCustomConditions: { type: Boolean, default: true }
})

const emit = defineEmits(['save'])
const visible = ref(false)
const button = ref(null)
const rule = ref(createEmptyActionRule())
const editorPanelRef = ref()

/**
 * 使用独立草稿打开条件编辑器。旧版规则由人工清理后重新配置，不在前端做
 * 静默转换，避免丢失限制并扩大按钮权限。
 */
function open(targetButton) {
  const existingRule = targetButton?.availabilityRule
  if (existingRule && existingRule.version !== ACTION_RULE_VERSION) {
    ElMessage.error('检测到旧版按钮条件，请先清理旧配置后再按显示条件和启用条件重新设置')
    return
  }
  button.value = targetButton
  const editableRule = existingRule
    ? cloneValue(existingRule)
    : createEmptyActionRule()
  editableRule.visibleWhen = toEditableActionRuleRoot(editableRule.visibleWhen)
  editableRule.enabledWhen = toEditableActionRuleRoot(editableRule.enabledWhen)
  rule.value = editableRule
  editorPanelRef.value?.resetPresets()
  visible.value = true
}

function save() {
  const result = editorPanelRef.value?.buildValidatedRule()
  if (!result || result.error) {
    ElMessage.warning(result?.error || '按钮条件编辑器尚未就绪，请稍后重试')
    return
  }
  emit('save', {
    button: button.value,
    rule: result.rule
  })
  visible.value = false
}

function cloneValue(value) {
  return JSON.parse(JSON.stringify(value))
}

defineExpose({ open })
</script>

<style scoped>
:global(.action-rule-dialog) {
  max-width: calc(100vw - 32px);
}

:global(.action-rule-dialog .el-dialog__body) {
  max-height: calc(100vh - 190px);
  overflow-y: auto;
  padding-top: 12px;
}

@media (max-width: 760px) {
  :global(.action-rule-dialog .el-dialog__body) {
    max-height: calc(100vh - 160px);
    padding-right: 14px;
    padding-left: 14px;
  }
}
</style>
