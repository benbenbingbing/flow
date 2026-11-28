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

// 仅声明此实现已接入的前端事件，供字段配置和运行时共用。
defineOptions({ scriptEvents: [
    { name: 'onChange', label: '值变化', description: '用户修改值后触发，完成后执行值变化事件链' }
] })
import { computed } from 'vue'
import RichTextEditor from '@/components/RichTextEditor.vue'
import { useFormField } from '@flow/workflow-core/vue/useFormField'

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
