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
      @input="handleInput"
      @blur="handleBlur"
      @focus="handleFocus"
    />
    <el-input
      v-else
      v-model="fieldValue"
      :type="isPassword ? 'password' : 'text'"
      :show-password="isPassword && !isDisabled"
      :placeholder="placeholder"
      :disabled="isDisabled"
      :maxlength="maxlength"
      :show-word-limit="showWordLimit"
      clearable
      v-on="customEventListeners"
      @change="handleChange"
      @input="handleInput"
      @blur="handleBlur"
      @focus="handleFocus"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useFormField } from '@flow/workflow-core/vue/useFormField'
import { resolveTextFieldMaxLength } from '@flow/workflow-core/config-runtime'

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
  handleInput,
  handleBlur,
  handleFocus,
  customEventListeners,
  parsedComponentProps
} = useFormField(props, emit)

const isTextarea = computed(() => {
  const type = props.field?.componentType || props.field?.fieldType || ''
  return type.toLowerCase() === 'textarea'
})

// 密码控件复用文本的数据与事件协议；只读时也保持遮蔽，不能回退成明文展示。
const isPassword = computed(() => String(props.field?.componentType || '').toLowerCase() === 'password')

const textareaRows = computed(() => {
  return parsedComponentProps.value.rows || 3
})

const maxlength = computed(() => {
  return resolveTextFieldMaxLength(props.field, parsedComponentProps.value)
})

const showWordLimit = computed(() => {
  return !isPassword.value && !!maxlength.value && parsedComponentProps.value.showWordLimit !== false
})
</script>

<style scoped>
.text-field {
  width: 100%;
}
</style>
