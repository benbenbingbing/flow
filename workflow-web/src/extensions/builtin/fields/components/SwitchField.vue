<template>
  <div class="switch-field">
    <el-switch
      :model-value="switchValue"
      :disabled="isDisabled"
      :active-text="activeText"
      :inactive-text="inactiveText"
      v-on="customEventListeners"
      @update:model-value="handleChange"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  normalizeFieldDefaultValue,
  useFormField
} from '../composables/useFormField.js'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number, Boolean], default: false },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const { isDisabled, handleChange, customEventListeners, parsedComponentProps } =
  useFormField(props, emit)

const switchValue = computed(() =>
  normalizeFieldDefaultValue(props.field, props.modelValue)
)
const activeText = computed(() => parsedComponentProps.value.activeText || '')
const inactiveText = computed(() => parsedComponentProps.value.inactiveText || '')
</script>

<style scoped>
.switch-field {
  width: 100%;
}
</style>
