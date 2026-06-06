<template>
  <div class="date-field">
    <el-date-picker
      v-model="fieldValue"
      :type="pickerType"
      :placeholder="placeholder"
      :disabled="isDisabled"
      :value-format="valueFormat"
      :format="displayFormat"
      style="width: 100%"
      clearable
      v-on="customEventListeners"
      @change="handleChange"
      @blur="handleBlur"
      @focus="handleFocus"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useFormField } from '../composables/useFormField.js'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Date], default: '' },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const { fieldValue, placeholder, isDisabled, handleChange, handleBlur, handleFocus, customEventListeners } =
  useFormField(props, emit)

const pickerType = computed(() => {
  const type = (props.field?.componentType || props.field?.fieldType || '').toLowerCase()
  return type === 'datetime' ? 'datetime' : 'date'
})

const valueFormat = computed(() => {
  return pickerType.value === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'
})

const displayFormat = computed(() => {
  return valueFormat.value
})
</script>

<style scoped>
.date-field {
  width: 100%;
}
</style>
