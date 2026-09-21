<template>
  <div class="readonly-field" :class="{ multiline }">
    <div class="readonly-label">{{ label }}</div>
    <div v-if="richText" class="readonly-rich" v-html="safeHtml" />
    <div v-else-if="isFile" class="readonly-files"><template v-if="attachments.length"><button v-for="(file, index) in attachments" :key="index" @click="services.openFile?.(file.url)"><VanIcon name="description-o" /><span>{{ file.label }}</span><VanIcon name="arrow" /></button></template><span v-else>—</span></div>
    <div v-else class="readonly-value">{{ display }}</div>
  </div>
</template>
<script setup>
import { computed } from 'vue'
import DOMPurify from 'dompurify'
import { Icon as VanIcon } from 'vant'
import { formatReadonlyValue, fileName } from '@flow/workflow-core/workflow/approval-display'
import { mobileFieldType } from './registry.js'
const props = defineProps({ field: { type: Object, required: true }, modelValue: null, options: { type: Array, default: () => [] }, services: { type: Object, default: () => ({}) } })
const label = computed(() => props.field.fieldLabel || props.field.fieldName || props.field.fieldCode)
const type = computed(() => mobileFieldType(props.field))
const richText = computed(() => type.value === 'rich_text')
const isFile = computed(() => ['file', 'image'].includes(type.value))
const safeHtml = computed(() => DOMPurify.sanitize(String(props.modelValue || '—')))
const display = computed(() => {
  const value = props.modelValue
  if (value == null || value === '' || (Array.isArray(value) && !value.length)) return '—'
  const labelFor = item => props.options.find(option => String(option.value ?? option.id) === String(item))?.label ?? props.options.find(option => String(option.value ?? option.id) === String(item))?.text ?? formatReadonlyValue(item)
  return Array.isArray(value) ? value.map(labelFor).join('、') : labelFor(value)
})
const multiline = computed(() => richText.value || isFile.value || ['textarea', 'sub_form', 'sub_list'].includes(type.value) || display.value.length > 32)
const attachments = computed(() => {
  const result = []
  function collect(value, prefix = '') {
    if (Array.isArray(value)) { value.forEach(item => collect(item, prefix)); return }
    if (typeof value === 'object' && value) {
      if (value.url || value.fileUrl) collect(value.url || value.fileUrl, value.name || prefix)
      else Object.entries(value).forEach(([key, item]) => collect(item, key))
      return
    }
    if (typeof value === 'string' && /^(https?:\/\/|\/)/i.test(value)) result.push({ url: value, label: prefix ? `${prefix} · ${fileName(value)}` : fileName(value) })
  }
  collect(props.modelValue); return result
})
</script>
<style scoped>
.readonly-field { padding: 14px 0; display: flex; gap: 16px; line-height: 1.65; font-size: 14px; border-bottom: 1px solid var(--flow-mobile-border); }.readonly-label { font-size: 13px; flex: 0 0 92px; color: var(--flow-mobile-muted); }.readonly-value { flex: 1; min-width: 0; text-align: right; white-space: pre-wrap; overflow-wrap: anywhere; }.multiline { display: block; }.multiline .readonly-label { margin-bottom: 8px; }.multiline .readonly-value { text-align: left; }.readonly-rich { overflow-wrap: anywhere; overflow: auto; }.readonly-rich :deep(img) { max-width: 100%; height: auto; }.readonly-rich :deep(table) { max-width: 100%; }.readonly-files { width: 100%; }.readonly-files button { display: flex; align-items: center; gap: 8px; width: 100%; min-height: 44px; background: var(--flow-mobile-inset); border: 0; border-radius: 8px; margin: 6px 0; padding: 10px; color: var(--flow-mobile-accent-text); text-align: left; }.readonly-files button span { flex: 1; min-width: 0; overflow-wrap: anywhere; }
</style>
