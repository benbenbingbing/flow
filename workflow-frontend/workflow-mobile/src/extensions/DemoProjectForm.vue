<template><MobileExtensionFormFields ref="fieldsRef" v-bind="$props" :fields="displayFields" :model-value="value" @update:model-value="update" /></template>
<script setup>
import { computed, ref, watch } from 'vue'
import { createDemoProjectValue } from './demoProjectModel.js'
import { extensionFormProps, mergeExtensionField } from '@flow/workflow-extensions/contracts/mobile-form'
import { MobileExtensionFormFields } from '@flow/workflow-mobile-ui'
const props = defineProps(extensionFormProps), emit = defineEmits(['update:modelValue'])
const fieldsRef = ref(), value = ref({ ...props.modelValue, ...createDemoProjectValue(props.modelValue) })
const definitions = [
  { fieldCode: 'projectName', fieldName: '项目名称' }, { fieldCode: 'code', fieldName: '项目编码' }, { fieldCode: 'ownerName', fieldName: '负责人' },
  { fieldCode: 'budget', fieldName: '项目预算', fieldType: 'DECIMAL', componentType: 'number', componentProps: { min: 0, precision: 2 } },
  { fieldCode: 'riskScore', fieldName: '风险评分', fieldType: 'INTEGER', componentType: 'number', componentProps: { min: 0, max: 100 } },
  { fieldCode: 'description', fieldName: '项目说明', componentType: 'textarea', componentProps: { maxlength: 1000 } }
]
const displayFields = computed(() => definitions.filter(field => !props.fields.length || props.fields.some(item => (item.fieldCode || item.fieldKey) === field.fieldCode)).map(field => {
  const merged = mergeExtensionField({ fieldType: 'STRING', componentType: 'input', ...field }, props)
  if (field.fieldCode === 'code' && props.mode === 'edit') merged.isReadonly = true
  return merged
}))
watch(() => props.modelValue, next => { value.value = { ...next, ...createDemoProjectValue(next) } }, { deep: true })
function update(next) { value.value = { ...next, name: next.projectName }; emit('update:modelValue', value.value) }
defineExpose({ async validate() { if (!await fieldsRef.value?.validate()) return false; update(value.value); return true } })
</script>
