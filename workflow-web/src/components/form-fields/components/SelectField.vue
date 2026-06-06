<template>
  <div class="select-field">
    <el-select
      v-model="fieldValue"
      :placeholder="placeholder"
      :disabled="isDisabled"
      :multiple="isMultiple"
      :clearable="!isMultiple"
      style="width: 100%"
      v-on="customEventListeners"
      @change="handleChange"
      @blur="handleBlur"
      @focus="handleFocus"
    >
      <el-option
        v-for="opt in currentOptions"
        :key="opt.value"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useFormField } from '../composables/useFormField.js'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number, Array], default: '' },
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
  currentOptions
} = useFormField(props, emit)

const isMultiple = computed(() => {
  const type = (props.field?.componentType || props.field?.fieldType || '').toLowerCase()
  return type === 'select_multiple' || type === 'multi_select'
})
</script>

<style scoped>
.select-field {
  width: 100%;
}
</style>
