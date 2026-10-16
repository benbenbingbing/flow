<template>
  <MobileReadonlyField v-if="disabled" :field="field" :model-value="modelValue" :services="services" />
  <div v-else class="mobile-file-field">
    <div class="file-label">{{ field.fieldLabel || field.fieldName }}</div>
    <section v-for="(group, index) in groups" :key="group.itemKey || index">
      <p v-if="items.length" class="file-group-name">{{ group.itemName }}<span v-if="group.required || attachmentItemRequiredState[group.itemKey || group.itemName]"> *</span></p>
      <VanUploader :model-value="fileList(group, index)" :multiple="group.multiple !== false && maxCount(group) > 1" :accept="accept(group)" :max-count="maxCount(group)" :after-read="result => upload(result, group, index)" :before-read="file => checkFile(file, group)" @delete="(_, details) => remove(group, index, details.index)" @click-preview="item => services.openFile?.(item.url)" :preview-full-image="false" :disabled="uploading > 0" />
    </section>
    <p v-if="error" class="file-error" role="alert">{{ error }}<VanButton v-if="failed" size="mini" @click="retry">重试上传</VanButton></p>
    <p v-if="uploading" class="file-progress" role="status">正在上传 {{ progress }}%</p>
  </div>
</template>
<script setup>
import { computed, ref } from 'vue'
import { Uploader as VanUploader, Button as VanButton } from 'vant'
import { safeParseConfig } from '@flow/workflow-core/config-runtime'
import { resolveAttachmentItems, getAttachmentItemValue, setAttachmentItemValue, isAttachmentFileTypeAllowed, normalizeAttachmentFileTypes } from '@flow/workflow-core/file-attachment'
import { fileName } from '@flow/workflow-core/workflow/approval-display'
import MobileReadonlyField from './MobileReadonlyField.vue'
const props = defineProps({ field: { type: Object, required: true }, modelValue: null, disabled: Boolean, services: { type: Object, required: true }, context: { type: Object, default: () => ({}) }, attachmentItemRequiredState: { type: Object, default: () => ({}) } })
const emit = defineEmits(['update:modelValue'])
const configuration = computed(() => safeParseConfig(props.field.componentProps))
const items = computed(() => resolveAttachmentItems(props.field))
const groups = computed(() => items.value.length ? items.value : [{}])
const uploading = ref(0), progress = ref(0), error = ref(''), failed = ref(null)
const urls = value => (Array.isArray(value) ? value : value ? [value] : []).map(item => typeof item === 'string' ? item : item.url || item.fileUrl).filter(Boolean)
const imageField = computed(() => String(props.field.componentType || props.field.fieldType).toLowerCase() === 'image')
const maxCount = group => Number(group.maxCount || props.field.fileMaxCount || configuration.value.fileMaxCount || configuration.value.maxCount || (imageField.value ? 9 : 5))
const fileTypes = group => group.fileTypes || props.field.fileTypes || props.field.fileType || configuration.value.fileTypes || configuration.value.fileType
function groupValue(group, index) { return items.value.length ? getAttachmentItemValue(group, index, props.modelValue) : props.modelValue }
function fileList(group, index) { return urls(groupValue(group, index)).map(url => ({ url, name: fileName(url), isImage: /\.(png|jpe?g|gif|webp)(?:[?#]|$)/i.test(url) })) }
function accept(group) { return normalizeAttachmentFileTypes(fileTypes(group)).map(type => `.${type.replace(/^\./, '')}`).join(',') || (imageField.value ? 'image/*' : '*') }
function checkFile(value, group) {
  for (const file of Array.isArray(value) ? value : [value]) {
    if (!isAttachmentFileTypeAllowed(file, fileTypes(group))) { error.value = '文件类型不符合要求'; return false }
    const maxSize = Number(group.maxSize || props.field.fileMaxSize || configuration.value.fileMaxSize || configuration.value.maxSize || 10)
    if (file.size > maxSize * 1024 * 1024) { error.value = `文件大小不能超过 ${maxSize} MB`; return false }
  }
  error.value = ''; return true
}
function save(group, index, values) {
  const value = group.multiple === false || maxCount(group) === 1 ? values[0] || '' : values
  emit('update:modelValue', items.value.length ? setAttachmentItemValue(props.modelValue, group, index, value) : value)
}
/** 同一批文件顺序上传，使用共享 API 的文件幂等键；失败重试不会重复创建同一文件。 */
async function upload(value, group, index) {
  if (uploading.value) return
  const entries = Array.isArray(value) ? value : [value]
  uploading.value++; error.value = ''; failed.value = null
  const accumulated = urls(groupValue(group, index))
  try {
    if (!props.services.uploadFile) throw new Error('当前表单未提供上传能力')
    for (let itemIndex = 0; itemIndex < entries.length; itemIndex++) {
      const item = entries[itemIndex]
      try {
        progress.value = 0
        const result = await props.services.uploadFile(item.file, props.field, props.context, percent => { progress.value = Number(percent) || 0 })
        const url = typeof result === 'string' ? result : result.url || result.fileUrl
        if (!url) throw new Error('上传响应缺少文件地址')
        accumulated.push(url); save(group, index, accumulated)
      } catch (cause) { failed.value = { entries: entries.slice(itemIndex), group, index }; throw cause }
    }
  } catch (cause) { error.value = cause.message || '上传失败，请重试' }
  finally { uploading.value-- }
}
function remove(group, index, fileIndex) { const values = urls(groupValue(group, index)); values.splice(fileIndex, 1); save(group, index, values) }
function retry() { if (failed.value) upload(failed.value.entries, failed.value.group, failed.value.index) }
defineExpose({ validate: () => !uploading.value && !error.value })
</script>
<style scoped>
.mobile-file-field { padding: 14px 0; }.file-label { color: var(--flow-mobile-muted); margin-bottom: 12px; font-size: 14px; }.file-group-name { font-size: 13px; margin: 8px 0; }.file-group-name span, .file-error { color: #b24434; }.file-error { font-size: 13px; line-height: 1.6; }.file-error button { margin-left: 10px; }.file-progress { font-size: 12px; color: var(--flow-mobile-accent-text); }
</style>
