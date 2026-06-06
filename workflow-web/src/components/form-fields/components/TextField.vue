<template>
  <div class="text-field">
    <el-input
      v-if="isTextarea"
      v-model="fieldValue"
      type="textarea"
      :rows="textareaRows"
      :placeholder="placeholder"
      :disabled="isDisabled"
      :maxlength="maxlength"
      :show-word-limit="showWordLimit"
      v-on="customEventListeners"
      @change="handleChange"
      @input="handleChange"
      @blur="handleBlur"
      @focus="handleFocus"
    />
    <el-input
      v-else
      v-model="fieldValue"
      :placeholder="placeholder"
      :disabled="isDisabled"
      :maxlength="maxlength"
      :show-word-limit="showWordLimit"
      clearable
      v-on="customEventListeners"
      @change="handleChange"
      @input="handleChange"
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
  modelValue: { type: [String, Number], default: '' },
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

const isTextarea = computed(() => {
  const type = props.field?.componentType || props.field?.fieldType || ''
  return type.toLowerCase() === 'textarea'
})

const textareaRows = computed(() => {
  return parsedComponentProps.value.rows || 3
})

const maxlength = computed(() => {
  return parsedComponentProps.value.maxlength || props.field?.fieldLength || undefined
})

const showWordLimit = computed(() => {
  return !!maxlength.value
})
</script>

<style scoped>
.text-field {
  width: 100%;
}
</style>
