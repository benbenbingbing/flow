<template>
  <div class="project-rule-condition">
    <el-select
      :model-value="modelValue.field"
      size="small"
      filterable
      style="width: 180px"
      @update:model-value="value => update('field', value)"
    >
      <el-option
        v-for="field in fields"
        :key="field.value"
        :label="field.label"
        :value="field.value"
      />
    </el-select>
    <el-select
      :model-value="modelValue.operator || 'EQ'"
      size="small"
      style="width: 100px"
      @update:model-value="value => update('operator', value)"
    >
      <el-option label="等于" value="EQ" />
      <el-option label="不等于" value="NE" />
      <el-option label="属于" value="IN" />
    </el-select>
    <el-input
      :model-value="displayValue"
      size="small"
      placeholder="条件值；IN 用逗号分隔"
      style="width: 190px"
      @update:model-value="updateValue"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: Object, default: () => ({}) },
  fields: { type: Array, default: () => [] },
  statuses: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])
const displayValue = computed(() =>
  Array.isArray(props.modelValue.value)
    ? props.modelValue.value.join(',')
    : props.modelValue.value ?? ''
)

function update(key, value) {
  emit('update:modelValue', {
    ...props.modelValue,
    type: 'PROJECT:CUSTOM_CONDITION',
    [key]: value
  })
}

function updateValue(value) {
  update(
    'value',
    props.modelValue.operator === 'IN'
      ? String(value).split(',').map(item => item.trim()).filter(Boolean)
      : value
  )
}
</script>

<style scoped>
.project-rule-condition {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
