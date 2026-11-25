<template>
  <div class="readonly-field" :class="[{ multiline, 'is-textarea': type === 'textarea' }, `label-${labelPosition}`]">
    <div class="readonly-label"><FormFieldLabel :text="label" /></div>
    <div v-if="richText" class="readonly-rich" v-html="safeHtml" />
    <div v-else-if="isFile" class="readonly-files"><template v-if="attachments.length"><button v-for="(file, index) in attachments" :key="index" @click="services.openFile?.(file)"><VanIcon name="description-o" /><span>{{ file.label }}</span><VanIcon name="arrow" /></button></template><span v-else>—</span></div>
    <div v-else class="readonly-value">{{ display }}</div>
  </div>
</template>
<script setup>
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import FormFieldLabel from '@flow/workflow-core/vue/FormFieldLabel.vue'
import { Icon as VanIcon } from 'vant'
import { formatReadonlyValue } from '@flow/workflow-core/workflow/approval-display'
import { isEntityStatusField, isProcessStatusField, resolveEntityStatusLabel, resolveProcessStatusLabel } from '@flow/workflow-core/entity-status-runtime'
import { mobileFieldType } from './registry.js'
import { resolveMobileLabelPosition } from './mobileFieldLayout.js'
import { normalizeAttachmentFiles } from '@flow/workflow-core/file-attachment'
const props = defineProps({ field: { type: Object, required: true }, modelValue: null, options: { type: Array, default: () => [] }, context: { type: Object, default: () => ({}) }, services: { type: Object, default: () => ({}) } })
const label = computed(() => props.field.fieldLabel || props.field.fieldName || props.field.fieldCode)
const type = computed(() => mobileFieldType(props.field))
const richText = computed(() => type.value === 'rich_text')
const isFile = computed(() => ['file', 'image'].includes(type.value))
const safeHtml = computed(() => DOMPurify.sanitize(String(props.modelValue || '—')))
const display = computed(() => {
  const value = props.modelValue
  if (value == null || value === '' || (Array.isArray(value) && !value.length)) return '—'
  // 只读详情也遵循密码组件的遮蔽语义，掩码仅用于展示，不能写回字段值。
  if (type.value === 'password') return '••••••••'
  if (isProcessStatusField(props.field)) return resolveProcessStatusLabel(value)
  if (isEntityStatusField(props.field)) {
    // 与详情顶部保持同一口径，优先展示当前记录由服务端解析的实体状态名称。
    // 子表使用自己的行上下文，且只转换显示文本，原状态编码仍用于审批提交。
    const record = props.context.getFormData?.() || props.context.record
    if (record?._statusText && String(record.status) === String(value)) return record._statusText
    const options = props.context.entityStatusMap || props.context.entityStatusOptions || props.options.map(option => ({ value: option.value ?? option.id, label: option.label ?? option.text }))
    return resolveEntityStatusLabel(value, options)
  }
  const labelFor = item => props.options.find(option => String(option.value ?? option.id) === String(item))?.label ?? props.options.find(option => String(option.value ?? option.id) === String(item))?.text ?? formatReadonlyValue(item)
  return Array.isArray(value) ? value.map(labelFor).join('、') : labelFor(value)
})
const labelPosition = computed(() => resolveMobileLabelPosition(props.context.form))
// 只读文本同样服从表单位置；长内容在值区域换行，不再因字数切换标签位置。
// 大块内容保留原来的上下布局，避免富文本和附件被挤入狭窄的值列。
const multiline = computed(() => richText.value || isFile.value || ['sub_form', 'sub_list'].includes(type.value) || labelPosition.value === 'top')
const attachments = computed(() => {
  return normalizeAttachmentFiles(props.modelValue).map(file => ({
    ...file, label: file.groupName ? `${file.groupName} · ${file.name}` : file.name
  }))
})
</script>
<style scoped>
.readonly-field {
  padding: var(--flow-mobile-form-field-padding, 14px) 0;
  display: flex;
  align-items: center;
  gap: 16px;
  line-height: 1.65;
  font-size: var(--flow-mobile-form-field-font-size, 14px);
  border-bottom: 1px solid var(--flow-mobile-border);
}
.readonly-label { font-size: var(--flow-mobile-form-label-font-size, 13px); flex: 0 0 92px; color: var(--flow-mobile-muted); }
.readonly-field.is-textarea { align-items: flex-start; }
.readonly-value { flex: 1; min-width: 0; text-align: right; white-space: pre-wrap; overflow-wrap: anywhere; }.multiline { display: block; }.multiline .readonly-label { margin-bottom: 8px; }.multiline .readonly-value { text-align: left; }.readonly-rich { overflow-wrap: anywhere; overflow: auto; }.readonly-rich :deep(img) { max-width: 100%; height: auto; }.readonly-rich :deep(table) { max-width: 100%; }.readonly-files { width: 100%; }.readonly-files button { display: flex; align-items: center; gap: 8px; width: 100%; min-height: 44px; background: var(--flow-mobile-inset); border: 0; border-radius: 8px; margin: 6px 0; padding: 10px; color: var(--flow-mobile-accent-text); text-align: left; }.readonly-files button span { flex: 1; min-width: 0; overflow-wrap: anywhere; }
</style>
