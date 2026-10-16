<template>
  <div class="attachment-conditions">
    <LinkageConditionRuleEditor
      v-for="item in items"
      :key="item.itemKey"
      :title="item.itemName"
      :description="item.staticRequired ? '实体层已设为必填，所有表单始终要求至少上传一份。' : '满足条件时，该附件项至少上传一份文件。'"
      :enabled="item.staticRequired || item.enabled"
      :disabled="disabled || item.staticRequired"
      :root="item.root"
      :fields="availableFields"
      @update:enabled="setEnabled(item, $event)"
      @change="persist(item)"
    />
    <el-empty v-if="!items.length" description="当前字段没有附件项" :image-size="48" />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import LinkageConditionRuleEditor from '@/components/LinkageConditionRuleEditor.vue'
import { LinkageEngine } from '@flow/workflow-core/utils/linkageEngine'
import { createFlowConditionConfig, createFlowConditionGroup, parseFlowConditionConfig } from '@flow/workflow-core/utils/flowConditionGroups'
import { patchFieldLinkageRules } from '@/shared/form-field-linkage'

const props = defineProps({
  field: { type: Object, required: true },
  fields: { type: Array, default: () => [] },
  attachmentItems: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false }
})
const items = ref([])
const availableFields = computed(() => props.fields.filter(field => field.uiConfigurable !== false && (field.fieldCode || field.fieldKey) !== (props.field.fieldCode || props.field.fieldKey)))
const signature = () => JSON.stringify([props.attachmentItems, LinkageEngine.getFieldLinkageRules(props.field).attachmentItemRequiredRules])
let currentField
let currentSignature
watch([() => props.field, signature], ([field, nextSignature]) => {
  if (currentField === field && currentSignature === nextSignature) return
  currentField = field
  currentSignature = nextSignature
  const saved = LinkageEngine.getFieldLinkageRules(field).attachmentItemRequiredRules?.items || []
  items.value = props.attachmentItems.filter(item => item.itemKey).map(item => {
    const rule = saved.find(rule => rule.itemKey === item.itemKey)
    return { itemKey: item.itemKey, itemName: item.itemName || item.itemKey, staticRequired: [true, 1, '1'].includes(item.required), enabled: !!rule, root: parseFlowConditionConfig(rule?.requiredConditionConfig) || createFlowConditionGroup() }
  })
}, { immediate: true })

/** 只更新当前附件项，保留其他附件项以及字段值、状态等规则。 */
function persist(item) {
  if (props.disabled || item.staticRequired) return
  const rules = LinkageEngine.getFieldLinkageRules(props.field).attachmentItemRequiredRules
  const saved = (rules?.items || []).filter(rule => rule.itemKey !== item.itemKey)
  if (item.enabled) saved.push({ itemKey: item.itemKey, requiredConditionConfig: createFlowConditionConfig(item.root) })
  patchFieldLinkageRules(props.field, ['attachmentItemRequiredRules'], saved.length ? { attachmentItemRequiredRules: { ...rules, version: 1, items: saved } } : {})
  currentSignature = signature()
}
function setEnabled(item, enabled) {
  if (props.disabled || item.staticRequired) return
  item.enabled = enabled
  persist(item)
}
</script>

<style scoped>
.attachment-conditions { display: flex; flex-direction: column; gap: 12px; }
.attachment-conditions :deep(.group-header), .attachment-conditions :deep(.condition-row) { flex-wrap: wrap; }
.attachment-conditions :deep(.condition-property), .attachment-conditions :deep(.condition-operator), .attachment-conditions :deep(.condition-value) { flex: 1 1 120px; min-width: 0; }
</style>
