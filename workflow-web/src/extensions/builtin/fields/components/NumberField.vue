<template>
  <div class="number-field">
    <el-input-number
      v-model="fieldValue"
      :placeholder="placeholder"
      :disabled="isDisabled"
      :min="min"
      :max="max"
      :precision="precision"
      :step="step"
      :controls="controls"
      style="width: 100%"
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
  modelValue: { type: [Number, String], default: null },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const {
  fieldValue,
  placeholder,
  isDisabled,
  handleChange,
  handleBlur,
  handleFocus,
  customEventListeners,
  parsedComponentProps
} = useFormField(props, emit)

// 优先从 componentProps 读取，其次从字段根属性读取
const min = computed(() => parsedComponentProps.value.min ?? props.field?.min ?? undefined)
const max = computed(() => parsedComponentProps.value.max ?? props.field?.max ?? undefined)
const precision = computed(() => {
  if (parsedComponentProps.value.precision != null) {
    return parsedComponentProps.value.precision
  }
  if (props.field?.precision != null) {
    return props.field.precision
  }
  const ft = (props.field?.fieldType || '').toLowerCase()
  if (ft === 'decimal' || ft === 'double') return 2
  return 0
})
const step = computed(() => parsedComponentProps.value.step ?? props.field?.step ?? undefined)
const controls = computed(() => parsedComponentProps.value.controls !== false)
</script>

<style scoped>
.number-field {
  width: 100%;
}
</style>
