<template>
  <div class="switch-field">
    <el-switch
      v-model="fieldValue"
      :disabled="isDisabled"
      :active-text="activeText"
      :inactive-text="inactiveText"
      v-on="customEventListeners"
      @change="handleChange"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useFormField } from '../composables/useFormField.js'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [Boolean, Number, String], default: false },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const { fieldValue, isDisabled, handleChange, customEventListeners, parsedComponentProps } =
  useFormField(props, emit)

const activeText = computed(() => parsedComponentProps.value.activeText || '')
const inactiveText = computed(() => parsedComponentProps.value.inactiveText || '')
</script>

<style scoped>
.switch-field {
  width: 100%;
}
</style>
