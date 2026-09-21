<template>
  <BusinessFormFields ref="fieldsRef" v-bind="$props" :fields="displayFields" :model-value="value" @update:model-value="update" />
  <VanButton v-if="!readonly && mode !== 'view'" block plain :loading="loading" @click="execute">刷新表单数据</VanButton>
  <p v-if="error" class="form-error" role="alert">{{ error }}</p>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { Button as VanButton } from 'vant'
import { createAcceptanceValue } from '@flow/workflow-core/business/project/acceptanceModel'
import { businessFormProps, mergeBusinessField } from './formContract.js'
import BusinessFormFields from './BusinessFormFields.vue'
const props = defineProps(businessFormProps), emit = defineEmits(['update:modelValue'])
const value = ref(createAcceptanceValue(props.modelValue)), fieldsRef = ref(), loading = ref(false), error = ref('')
let alive = true
const definitions = [
  { fieldCode: 'name', fieldName: '验收单名称', isRequired: true },
  { fieldCode: 'acceptance_scene', fieldName: '验收场景', componentType: 'select', isRequired: true, componentProps: { options: [{ value: 'FULL_EXTENSION', label: '全扩展验收' }, { value: 'FORM_EXTENSION', label: '表单扩展验收' }, { value: 'LIST_EXTENSION', label: '列表扩展验收' }, { value: 'PROCESS_EXTENSION', label: '流程扩展验收' }] } },
  { fieldCode: 'owner_name', fieldName: '验收负责人', isRequired: true },
  { fieldCode: 'planned_date', fieldName: '计划日期', fieldType: 'DATE', componentType: 'date' },
  { fieldCode: 'acceptance_score', fieldName: '验收评分', fieldType: 'INTEGER', componentType: 'project_acceptance_score', isRequired: true },
  { fieldCode: 'description', fieldName: '验收说明', componentType: 'textarea', componentProps: { maxlength: 1000 } },
  { fieldCode: 'extension_result', fieldName: '扩展结果', componentType: 'textarea' }
]
const displayFields = computed(() => definitions.map(field => mergeBusinessField({ fieldType: 'STRING', componentType: 'input', ...field }, props)))
watch(() => props.modelValue, next => { value.value = createAcceptanceValue(next) }, { deep: true })
function update(next) { value.value = next; emit('update:modelValue', next) }
async function execute() {
  if (loading.value || props.readonly || props.mode === 'view') return
  loading.value = true; error.value = ''
  try {
    if (!props.dataSourceRuntime?.executeOwnerUsage) throw new Error('当前表单没有可用的数据源运行时')
    const results = await props.dataSourceRuntime.executeOwnerUsage(props.form, 'AFTER_LOAD', { record: value.value, input: { source: 'ProjectExtensionAcceptanceForm', requestedAt: new Date().toISOString() } })
    if (!alive) return
    const next = { ...value.value }
    for (const result of results) { const patch = result?.data ?? result; if (patch && typeof patch === 'object' && !Array.isArray(patch)) Object.assign(next, patch) }
    update(next)
  } catch (cause) { if (alive) error.value = cause.message }
  finally { loading.value = false }
}
onBeforeUnmount(() => { alive = false })
defineExpose({ async validate() { if (loading.value || !await fieldsRef.value?.validate()) return false; update(value.value); return true } })
</script>
<style scoped>.form-error { color: #bc4738; font-size: 12px; }</style>
