<template>
  <div class="user-selector">
    <EntitySelector
      entity-type="USER"
      :model-value="normalizedModelValue"
      :multiple="multiple"
      :value-key="valueKey"
      :placeholder="placeholder"
      :disabled="disabled"
      :title="title"
      @update:model-value="handleValueUpdate"
      @change="handleSelectionChange"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import EntitySelector from '@/components/EntitySelector.vue'
import { recordSelectionValues } from '@/shared/entity-record-selection'

const props = defineProps({
  modelValue: {
    type: [String, Number, Array],
    default: ''
  },
  multiple: {
    type: Boolean,
    default: false
  },
  valueKey: {
    type: String,
    default: 'id',
    validator: value => ['id', 'code'].includes(value)
  },
  placeholder: {
    type: String,
    default: '请选择用户'
  },
  title: {
    type: String,
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits([
  'update:modelValue',
  'change',
  'selected'
])

const normalizedModelValue = computed(() => {
  if (props.multiple) {
    return Array.isArray(props.modelValue)
      ? props.modelValue.map(value => String(value))
      : []
  }
  return props.modelValue == null ? '' : String(props.modelValue)
})

function normalizeValue(value) {
  if (props.multiple) {
    return Array.isArray(value)
      ? value.map(item => String(item)).filter(Boolean)
      : []
  }
  return value == null ? '' : String(value)
}

function handleValueUpdate(value) {
  emit('update:modelValue', normalizeValue(value))
}

function handleSelectionChange(selection) {
  const rows = props.multiple
    ? (Array.isArray(selection) ? selection : [])
    : (selection ? [selection] : [])
  const values = recordSelectionValues(rows, props.valueKey)
  const value = props.multiple ? values : (values[0] || '')
  emit('change', value)
  emit('selected', props.multiple ? rows : (rows[0] || null))
}
</script>

<style scoped>
.user-selector {
  width: 100%;
  min-width: 0;
}
</style>
