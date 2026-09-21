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
import TextField from '@/extensions/builtin/fields/components/TextField.vue'
import { provideFieldScriptContext } from '@/composables/provideFieldScriptContext'
import { computed } from 'vue'
import { resolveFieldComponent } from '@/extensions/core/registries/formFieldRegistry.js'

const props = defineProps({
  context: { type: Object, default: () => ({}) },
  field: {
    type: Object,
    required: true
  },
  modelValue: {
    type: [String, Number, Array, Date, Object, Boolean],
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])
provideFieldScriptContext(() => props.context)

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
