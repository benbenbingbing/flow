<template>
  <div class="switch-field">
    <el-switch
      :model-value="switchValue"
      :disabled="isDisabled"
      :active-text="activeText"
      :inactive-text="inactiveText"
      v-on="customEventListeners"
      @update:model-value="handleChange"
    />
  </div>
</template>

<script setup>

// 仅声明此实现已接入的前端事件，供字段配置和运行时共用。
defineOptions({ scriptEvents: [
    { name: 'onChange', label: '值变化', description: '用户修改值后触发，完成后执行值变化事件链' }
] })
import { computed } from 'vue'
import {
  normalizeFieldDefaultValue,
  useFormField
} from '@flow/workflow-core/vue/useFormField'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number, Boolean], default: false },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const { isDisabled, handleChange, customEventListeners, parsedComponentProps } =
  useFormField(props, emit)

const switchValue = computed(() =>
  normalizeFieldDefaultValue(props.field, props.modelValue)
)
const activeText = computed(() => parsedComponentProps.value.activeText || '')
const inactiveText = computed(() => parsedComponentProps.value.inactiveText || '')
</script>

<style scoped>
.switch-field {
  width: 100%;
}
</style>
