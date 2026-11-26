<template>
  <div class="form-field-renderer">
    <component
      v-if="resolvedComponent"
      ref="componentRef"
      :is="resolvedComponent"
      v-bind="componentProps"
      @update:model-value="updateValue"
      @patch-form="patchForm"
      @change="$emit('change', $event)"
      @blur="$emit('blur', $event)"
      @focus="$emit('focus', $event)"
    />
    <el-alert v-else type="error" :closable="false" title="当前组件未安装或不支持此节点，请检查组件配置" />
    <el-alert v-if="optionError" type="error" :closable="false" :title="optionError" />
    <div v-if="unresolvedSelectionCodes.length" class="unresolved-selection-codes">
      已保存编码（不在当前候选中）：{{ unresolvedSelectionCodes.join('、') }}
    </div>
  </div>
</template>

<script setup>
import { computed, ref, provide, watch } from 'vue'
import { provideFieldScriptContext } from '@/composables/provideFieldScriptContext'
import { resolveFieldComponentDescriptor } from '@/extensions/core/registries/formFieldRegistry.js'
import { FIELD_SCRIPT_EVENTS } from '@flow/workflow-core/browser/field-script-metadata'
import { formFieldProps, formFieldEmits } from '@flow/workflow-core/extensions/contracts/form-field'
import { safeParseConfig } from '@flow/workflow-core/config-runtime'
import { useFieldDictionaryOptions } from '@/composables/useFieldDictionaryOptions'
import { getFormDataSourceBindings } from '@flow/workflow-core/form-runtime'
import { getFieldOptionQueryKey } from '@flow/workflow-core/form-runtime/dataSourceRuntime'

const props = defineProps(formFieldProps)
const emit = defineEmits(formFieldEmits)
// 标准表单可传入已解析候选；独立组件和子表单则按绑定代码表加载，禁止使用字段内嵌选项。
const hasDynamicOptions = computed(() =>
  ['SELECT', 'MULTI_SELECT', 'SELECT_MULTIPLE', 'RADIO', 'CHECKBOX'].includes(String(props.field.componentType || props.field.fieldType).toUpperCase())
    && getFormDataSourceBindings(props.field, 'FIELD_OPTIONS').length > 0)
const optionQueryKey = computed(() => hasDynamicOptions.value
  ? getFieldOptionQueryKey(props.field, props.formData) : null)
const hostOptions = computed(() => optionQueryKey.value === null ? props.options : undefined)
const dictionaryOptions = useFieldDictionaryOptions(() => hostOptions.value == null && !hasDynamicOptions.value ? [props.field] : [])
const dynamicOptions = ref([]), optionError = ref('')
// 已声明依赖的接口只随映射参数刷新；无联动的动态绑定沿用原来的表单数据监听。
watch([hostOptions, () => props.field, optionQueryKey, () => optionQueryKey.value === null ? props.formData : null,
  () => props.dataSourceRuntime], async (_, __, onCleanup) => {
  let current = true
  onCleanup(() => { current = false })
  dynamicOptions.value = []; optionError.value = ''
  if (hostOptions.value != null || !hasDynamicOptions.value) return
  try {
    if (!props.dataSourceRuntime?.loadOptions) throw new Error('当前表单未提供动态选项查询能力')
    const options = await props.dataSourceRuntime.loadOptions(props.field, {
      form: props.context.form, record: props.formData, context: props.context, mode: props.mode
    })
    if (current) dynamicOptions.value = Array.isArray(options) ? options : []
  } catch (error) { if (current) optionError.value = error.message || '动态选项加载失败' }
}, { deep: true, immediate: true })
provideFieldScriptContext(() => props.context)
const componentRef = ref(null)
const resolvedDescriptor = computed(() => resolveFieldComponentDescriptor(props.field))
const resolvedComponent = computed(() => resolvedDescriptor.value?.component)
provide(FIELD_SCRIPT_EVENTS, computed(() => resolvedDescriptor.value?.capabilities.scriptEvents))
const componentProps = computed(() => ({
  ...props,
  options: hostOptions.value ?? (hasDynamicOptions.value ? dynamicOptions.value : dictionaryOptions.value[props.field.dictType]) ?? [],
  node: props.node || props.field.node || null,
  config: safeParseConfig(props.field.componentProps),
  disabled: props.disabled || props.readonly
}))

/** 候选可能随查询上下文变化；未返回的历史编码仍须可读，不作为新选项注入或自动清除。 */
const unresolvedSelectionCodes = computed(() => {
  if (!props.field.optionsInterfaceId || !hasDynamicOptions.value) return []
  const values = Array.isArray(props.modelValue) ? props.modelValue : [props.modelValue]
  const known = new Set(componentProps.value.options.map(option => option.value))
  return values.filter(code => typeof code === 'string' && code && !known.has(code))
})

/** 所有组件共用写入口，避免只读控件或无绑定展示组件意外写入宿主模型。 */
function updateValue(value) {
  if (!props.disabled && !props.readonly && !props.field.isDisplayOnly) emit('update:modelValue', value)
}
function patchForm(patch) {
  if (!props.disabled && !props.readonly) emit('patch-form', patch)
}
/** 统一收集组件内部校验，无组件实现时阻止提交，避免把错误配置当成普通文本控件。 */
async function validate() {
  return !optionError.value && Boolean(resolvedComponent.value) && (await componentRef.value?.validate?.()) !== false
}
defineExpose({ validate })
</script>

<style scoped>
.form-field-renderer { width: 100%; }
</style>
