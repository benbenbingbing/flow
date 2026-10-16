<template>
  <el-select
    v-if="multiple"
    :model-value="Array.isArray(modelValue) ? modelValue : []"
    multiple filterable :allow-create="type !== 'boolean'" default-first-option
    :placeholder="placeholder || '输入值后按回车添加'"
    :aria-label="label"
    @update:model-value="updateMultiple"
  >
    <el-option v-for="option in choices" :key="`${typeof option.value}:${option.value}`" :label="option.label" :value="option.value" />
  </el-select>
  <el-select
    v-else-if="type === 'boolean' || choices.length"
    :model-value="modelValue" filterable :allow-create="type !== 'boolean'" default-first-option
    :placeholder="placeholder || '选择值'" :aria-label="label"
    @update:model-value="updateScalar"
  >
    <el-option v-for="option in choices" :key="`${typeof option.value}:${option.value}`" :label="option.label" :value="option.value" />
  </el-select>
  <el-input-number
    v-else-if="type === 'number'"
    :model-value="numericValue" :controls="false" :placeholder="placeholder || '填写数值'" :aria-label="label"
    @update:model-value="value => emit('update:modelValue', value)"
  />
  <el-date-picker
    v-else-if="type === 'date' || type === 'datetime'"
    :model-value="modelValue" :type="type"
    :value-format="type === 'date' ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss'"
    :placeholder="placeholder || '选择日期'" :aria-label="label"
    @update:model-value="value => emit('update:modelValue', value)"
  />
  <el-input
    v-else :model-value="modelValue" :placeholder="placeholder || '填写条件值'" :aria-label="label"
    @update:model-value="value => emit('update:modelValue', value)"
  />
</template>

<script setup>
import { computed } from 'vue'
import { fixedFilterFieldOptions, fixedFilterValueType } from '@/shared/list-fixed-filters'

const props = defineProps({
  modelValue: { default: '' },
  field: { type: Object, default: () => ({}) },
  multiple: { type: Boolean, default: false },
  placeholder: { type: String, default: '' },
  label: { type: String, default: '条件值' }
})
const emit = defineEmits(['update:modelValue'])
const type = computed(() => fixedFilterValueType(props.field))
const numericValue = computed(() => props.modelValue === '' || props.modelValue == null
  ? undefined : Number(props.modelValue))
// 已有布尔值可能使用 0/1，展示友好标签时仍保留原始值类型。
const choices = computed(() => type.value === 'boolean'
  ? typeof props.modelValue === 'number'
    ? [{ label: '是', value: 1 }, { label: '否', value: 0 }]
    : [{ label: '是', value: true }, { label: '否', value: false }]
  : fixedFilterFieldOptions(props.field))

function updateScalar(value) {
  emit('update:modelValue', type.value === 'number' && value !== '' && Number.isFinite(Number(value))
    ? Number(value) : value)
}

/** 自建多值选项按字段类型回写；非法数字保留输入，交给保存校验说明错误。 */
function updateMultiple(values) {
  emit('update:modelValue', values.map(value =>
    type.value === 'number' && String(value).trim() !== '' && Number.isFinite(Number(value))
      ? Number(value) : value))
}
</script>
