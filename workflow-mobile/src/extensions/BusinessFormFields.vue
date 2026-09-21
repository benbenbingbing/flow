<template>
  <div v-for="field in visibleFields" :key="field.fieldCode" :data-field-code="field.fieldCode">
    <MobileFieldRenderer :ref="value => setRef(field.fieldCode, value)" :field="field" :model-value="modelValue[field.fieldCode]" :disabled="!stateFor(field).editable" :required="stateFor(field).required" :options="linkageState.options?.[field.fieldCode] ?? null" :context="context" :services="services" :data-source-runtime="dataSourceRuntime" @update:model-value="value => update(field, value)" @change="value => context.onFieldChange?.(field, value)" @blur="context.onFieldBlur?.(field)" />
    <p v-if="errors[field.fieldCode] || externalErrors[field.fieldCode]" class="field-error" role="alert">{{ errors[field.fieldCode] || externalErrors[field.fieldCode] }}</p>
  </div>
</template>
<script setup>
import { computed, nextTick, ref } from 'vue'
import { MobileFieldRenderer } from '@flow/workflow-mobile-ui'
import { runtimeFieldState, validateRuntimeFields } from '@flow/workflow-core/form-runtime/formModel'
defineOptions({ inheritAttrs: false })
const props = defineProps({ fields: Array, modelValue: Object, readonly: Boolean, mode: String, form: Object, context: Object, services: Object, dataSourceRuntime: Object, linkageState: { type: Object, default: () => ({}) }, externalErrors: { type: Object, default: () => ({}) } })
const emit = defineEmits(['update:modelValue', 'field-change'])
const errors = ref({}), refs = new Map()
const runtime = computed(() => ({ form: props.form, record: props.modelValue, context: props.context, mode: props.mode, readonly: props.readonly }))
const stateFor = field => runtimeFieldState(field, runtime.value, props.linkageState)
const visibleFields = computed(() => props.fields.filter(field => stateFor(field).visible))
function setRef(key, value) { if (value) refs.set(key, value); else refs.delete(key) }
function update(field, value) {
  delete errors.value[field.fieldCode]
  emit('update:modelValue', { ...props.modelValue, [field.fieldCode]: value })
  emit('field-change', field.fieldCode, value)
}
/** 自定义整表单的补充字段仍遵循发布字段的可见性、只读和公共校验规则。 */
async function validate() {
  await nextTick()
  errors.value = await validateRuntimeFields(props.fields, props.modelValue, runtime.value, props.linkageState)
  for (const field of visibleFields.value) if (await refs.get(field.fieldCode)?.validate?.() === false) errors.value[field.fieldCode] ||= '请检查该项内容'
  const first = Object.keys({ ...errors.value, ...props.externalErrors })[0]
  if (first) await props.context.revealField?.(first)
  return !first
}
defineExpose({ validate })
</script>
<style scoped>.field-error { color: #bc4738; font-size: 12px; line-height: 1.6; margin: 0 0 8px; }</style>
