<template>
  <el-form-item :label="label" class="configured-form-field-item">
    <template v-if="label" #label="{ label: labelText }">
      <FormFieldLabel :text="labelText" />
    </template>
    <slot />
  </el-form-item>
</template>

<script setup>
import FormFieldLabel from '@flow/workflow-core/vue/FormFieldLabel.vue'

defineProps({ label: { type: String, default: '' } })
</script>

<style scoped>
/* 横向标签和控件共享整行高度。长标签撑开本行时也会与输入框垂直对齐。 */
.configured-form-field-item:not(.el-form-item--label-top) { align-items: center; }
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
