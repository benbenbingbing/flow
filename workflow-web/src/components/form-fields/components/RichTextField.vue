<template>
  <div class="rich-text-field">
    <RichTextEditor
      v-model="fieldValue"
      :disabled="isDisabled"
      :height="editorHeight"
      @change="handleChange"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import { useFormField } from '../composables/useFormField.js'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const { fieldValue, isDisabled, handleChange, parsedComponentProps } = useFormField(props, emit)

const editorHeight = computed(() => parsedComponentProps.value.height || 200)
</script>

<style scoped>
.rich-text-field {
  width: 100%;
}
</style>
