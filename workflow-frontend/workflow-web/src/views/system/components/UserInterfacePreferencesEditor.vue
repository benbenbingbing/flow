<template>
  <div class="user-interface-preferences-editor">
    <div v-for="field in USER_INTERFACE_PREFERENCE_FIELDS" :key="field.key" class="preference-row">
      <div><h4>{{ field.label }}</h4><p>{{ field.description }}</p></div>
      <el-switch :model-value="modelValue[field.key]" :aria-label="field.label" :disabled="disabled"
        active-text="开启" inactive-text="关闭" @change="value => $emit('change', { ...modelValue, [field.key]: value })" />
    </div>
  </div>
</template>

<script setup>
import { USER_INTERFACE_PREFERENCE_FIELDS } from '@/shared/user-interface-preferences'
defineProps({ modelValue: { type: Object, required: true }, disabled: Boolean })
defineEmits(['change'])
</script>

<style scoped>
.user-interface-preferences-editor { width: 100%; }
.preference-row { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 16px 0; }
.preference-row + .preference-row { border-top: 1px solid #ebeef5; }
.preference-row h4 { margin: 0; font-weight: 500; }
.preference-row p { margin: 8px 0 0; font-size: 12px; color: #909399; line-height: 1.7; }
.preference-row .el-switch { flex-shrink: 0; }
@media (max-width: 600px) { .preference-row { align-items: flex-start; flex-direction: column; gap: 8px; } }
</style>
