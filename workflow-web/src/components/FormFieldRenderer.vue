<template>
  <div class="form-field-renderer">
    <component
      :is="resolvedComponent"
      :field="field"
      :modelValue="modelValue"
      @update:modelValue="$emit('update:modelValue', $event)"
      :disabled="disabled"
      @change="$emit('change', $event)"
      @blur="$emit('blur', $event)"
      @focus="$emit('focus', $event)"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { resolveFieldComponent, TextField } from '@/components/form-fields'

const props = defineProps({
  field: {
    type: Object,
    required: true
  },
  modelValue: {
    type: [String, Number, Array, Date, Object],
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const resolvedComponent = computed(() => {
  const component = resolveFieldComponent(props.field)
  return component || TextField
})
</script>

<style scoped>
.form-field-renderer {
  width: 100%;
}
</style>
