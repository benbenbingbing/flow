<!--
  单字段模板：仅需替换 el-input 展示即可复用通用值、配置、禁用、选项和事件逻辑。
  当前模板面向 STRING/TEXT；注册时 supportedFieldTypes 也应限定这两个类型。
  更换控件后保留 update:modelValue/change/blur/focus 的业务语义，避免双发 change。
-->
<template>
  <el-input
    :model-value="fieldValue"
    :disabled="isDisabled"
    :placeholder="placeholder"
    :maxlength="parsedComponentProps.maxLength"
    @update:model-value="handleChange"
    @blur="handleBlur"
    @focus="handleFocus"
  >
    <template v-if="parsedComponentProps.suffix" #suffix>{{ parsedComponentProps.suffix }}</template>
  </el-input>
</template>

<script setup>
import { formFieldProps, formFieldEmits } from '@/contracts/form-field.js'
import { useFormField } from '@/contracts/runtime.js'

const props = defineProps(formFieldProps)
const emit = defineEmits(formFieldEmits)
const {
  fieldValue, isDisabled, placeholder, parsedComponentProps,
  handleChange, handleBlur, handleFocus
} = useFormField(props, emit)
</script>
