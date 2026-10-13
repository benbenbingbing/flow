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
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { provideFieldScriptContext } from '@/composables/provideFieldScriptContext'
import { resolveFieldComponent } from '@/extensions/core/registries/formFieldRegistry.js'
import { formFieldProps, formFieldEmits } from '@flow/workflow-core/extensions/contracts/form-field'
import { safeParseConfig } from '@flow/workflow-core/config-runtime'

const props = defineProps(formFieldProps)
const emit = defineEmits(formFieldEmits)
provideFieldScriptContext(() => props.context)
const componentRef = ref(null)
const resolvedComponent = computed(() => resolveFieldComponent(props.field))
const componentProps = computed(() => ({
  ...props,
  node: props.node || props.field.node || null,
  config: safeParseConfig(props.field.componentProps),
  disabled: props.disabled || props.readonly
}))

/** 所有组件共用写入口，避免只读控件或无绑定展示组件意外写入宿主模型。 */
function updateValue(value) {
  if (!props.disabled && !props.readonly && !props.field.isDisplayOnly) emit('update:modelValue', value)
}
function patchForm(patch) {
  if (!props.disabled && !props.readonly) emit('patch-form', patch)
}
/** 统一收集组件内部校验，无组件实现时阻止提交，避免把错误配置当成普通文本控件。 */
async function validate() {
  return Boolean(resolvedComponent.value) && (await componentRef.value?.validate?.()) !== false
}
defineExpose({ validate })
</script>

<style scoped>
.form-field-renderer { width: 100%; }
</style>
