<template>
  <div class="checkbox-field">
    <el-checkbox-group
      v-model="fieldValue"
      :disabled="isDisabled"
      v-on="customEventListeners"
      @change="handleChange"
    >
      <el-checkbox
        v-for="opt in currentOptions"
        :key="opt.value"
        :label="opt.value"
      >
        {{ opt.label }}
      </el-checkbox>
    </el-checkbox-group>
  </div>
</template>

<script setup>
import { useFormField } from '../composables/useFormField.js'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number, Array], default: () => [] },
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
.checkbox-field {
  width: 100%;
}
</style>
