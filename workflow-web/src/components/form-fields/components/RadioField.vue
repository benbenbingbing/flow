<template>
  <div class="radio-field">
    <el-radio-group
      v-model="fieldValue"
      :disabled="isDisabled"
      v-on="customEventListeners"
      @change="handleChange"
    >
      <el-radio
        v-for="opt in currentOptions"
        :key="opt.value"
        :label="opt.value"
      >
        {{ opt.label }}
      </el-radio>
    </el-radio-group>
  </div>
</template>

<script setup>
import { useFormField } from '../composables/useFormField.js'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number, Boolean], default: '' },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const { fieldValue, isDisabled, handleChange, customEventListeners, currentOptions } = useFormField(
  props,
  emit
)
</script>

<style scoped>
.radio-field {
  width: 100%;
}
</style>
