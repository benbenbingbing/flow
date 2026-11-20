<template>
  <el-form-item :label="label" class="picker-help-item">
    <template #label>
      <ConfigHelpLabel :label="label" :content="help" />
    </template>
    <el-select :model-value="binding?.extensionId" :aria-label="label" filterable clearable :placeholder="placeholder"
      @update:model-value="emit('select', $event)">
      <el-option v-for="item in options" :key="item.extensionId" :label="`${item.displayName} (${item.extensionKey})`" :value="item.extensionId" />
    </el-select>
    <details v-if="binding?.extensionId" class="picker-binding-input">
      <summary>输入映射</summary>
      <el-input :model-value="inputDraft ?? JSON.stringify(binding.inputMapping || {}, null, 2)"
        type="textarea" :rows="2" :aria-label="`${label}输入映射`" placeholder='{"filters.projectId":"data.project_id"}'
        @update:model-value="emit('edit-input', $event)" />
      <small v-if="inputError" class="picker-error" role="alert">{{ inputError }}</small>
    </details>
  </el-form-item>
</template>

<script setup>
import ConfigHelpLabel from '@/components/ConfigHelpLabel.vue'

defineProps({
  label: { type: String, required: true },
  help: { type: String, required: true },
  placeholder: { type: String, default: '请选择接口' },
  binding: Object,
  options: { type: Array, default: () => [] },
  inputDraft: String,
  inputError: String
})
const emit = defineEmits(['select', 'edit-input'])
</script>

<style scoped>
.el-select, .picker-binding-input { width: 100%; }
.picker-binding-input { margin-top: 4px; }
.picker-binding-input summary { cursor: pointer; color: var(--el-text-color-secondary); }
.picker-error { color: var(--el-color-danger); }
</style>
