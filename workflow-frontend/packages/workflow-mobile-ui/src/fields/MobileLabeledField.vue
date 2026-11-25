<template>
  <VanField :label="label" class="mobile-labeled-field">
    <template v-if="label" #label><FormFieldLabel :text="label" /></template>
    <template v-if="$slots.input" #input><slot name="input" /></template>
  </VanField>
</template>

<script setup>
import { Field as VanField } from 'vant'
import FormFieldLabel from '@flow/workflow-core/vue/FormFieldLabel.vue'

defineProps({ label: { type: String, default: '' } })
</script>

<style scoped>
.mobile-labeled-field:not(.van-field--label-top) { align-items: center; }
.mobile-labeled-field :deep(.van-field__label) {
  display: flex;
  align-items: baseline;
  box-sizing: border-box;
  min-width: 0;
}
.mobile-labeled-field :deep(.van-field__label::before) { flex: none; }
</style>
