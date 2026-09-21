<template>
  <MobileFieldRenderer ref="fieldRef" :field="selectField" :model-value="modelValue" :disabled="disabled" :options="choices" :context="context" :services="services" @update:model-value="$emit('update:modelValue', $event)" @change="$emit('change', $event)" @blur="$emit('blur')" />
  <VanButton v-if="!disabled" size="small" plain :loading="loading" @click="load">重新加载选项</VanButton>
  <p v-if="error" class="load-error" role="alert">{{ error }}</p>
</template>
<script setup>
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { Button as VanButton } from 'vant'
import { MobileFieldRenderer } from '@flow/workflow-mobile-ui'
const props = defineProps({ field: Object, modelValue: [String, Number], disabled: Boolean, context: Object, services: Object, options: Array, dataSourceRuntime: Object })
const emit = defineEmits(['update:modelValue', 'change', 'blur'])
const selectField = computed(() => ({ ...props.field, componentType: 'select', componentName: undefined, extensionName: undefined }))
const runtimeOptions = ref(null), loading = ref(false), error = ref(''), fieldRef = ref()
const choices = computed(() => runtimeOptions.value ?? props.options ?? [])
let generation = 0
/** 发布身份变化使旧响应失效；默认值只在可编辑且仍为空时回填。 */
async function load() {
  const version = ++generation, runtime = props.dataSourceRuntime
  if (!runtime?.executeOwnerUsage) return
  loading.value = true; error.value = ''
  const owner = props.context.node || props.field
  const context = { form: props.context.form, record: props.context.getFormData?.() || props.context.record, recordId: props.context.record?.id, input: { fieldCode: props.field.fieldCode, value: props.modelValue } }
  try {
    if (!props.disabled && (props.modelValue == null || props.modelValue === '')) {
      const [result] = await runtime.executeOwnerUsage(owner, 'FIELD_DEFAULT', context)
      const payload = result?.data ?? result, value = payload?.value ?? payload
      if (version === generation && !props.disabled && (props.modelValue == null || props.modelValue === '') && value != null && value !== '') emit('update:modelValue', value)
    }
    const [result] = await runtime.executeOwnerUsage(owner, 'FIELD_OPTIONS', context)
    if (version === generation) { const value = result?.data ?? result; runtimeOptions.value = Array.isArray(value) ? value : [] }
  } catch (cause) { if (version === generation) error.value = cause.message }
  finally { if (version === generation) loading.value = false }
}
watch(() => [props.context.form?.id, props.context.form?.runtimeReleaseId, props.context.node?.id], load, { immediate: true })
onBeforeUnmount(() => generation++)
defineExpose({ validate: async () => props.disabled || (!loading.value && !error.value && await fieldRef.value?.validate()) })
</script>
<style scoped>.load-error { color: #bc4738; font-size: 12px; }</style>
