<template>
  <el-form-item :label="label" class="configured-form-field-item" :class="{ 'is-multiline': isMultilineFormField(field) }">
    <template v-if="label" #label="{ label: labelText }">
      <FormFieldLabel :text="labelText" />
    </template>
    <slot />
  </el-form-item>
</template>

<script setup>
import FormFieldLabel from '@flow/workflow-core/vue/FormFieldLabel.vue'
import { isMultilineFormField } from '@/shared/form-layout'

defineProps({
  label: { type: String, default: '' },
  field: { type: Object, default: null }
})
</script>

<style scoped>
/* 普通横向输入按整行居中，多行编辑器的标签跟随编辑器顶部。 */
.configured-form-field-item:not(.el-form-item--label-top) { align-items: center; }
.configured-form-field-item.is-multiline:not(.el-form-item--label-top) { align-items: flex-start; }
.configured-form-field-item :deep(.el-form-item__label) {
  height: auto;
  align-items: baseline;
  line-height: 1.4;
}
.configured-form-field-item :deep(.el-form-item__label::before),
.configured-form-field-item :deep(.el-form-item__label::after) { flex: none; }
.configured-form-field-item.el-form-item--label-top :deep(.el-form-item__label) {
  display: flex;
  width: 100%;
  max-width: 100%;
}
</style>
