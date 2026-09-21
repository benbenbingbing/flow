<template>
  <div class="entity-field" @focusin="onFocusIn" @focusout="onFocusOut">
    <EntitySelector
      v-model="fieldValue"
      :entity-type="entityType"
      :entity-code="entityCode"
      :ref-entity-id="refEntityId"
      :list-key="listKey"
      :runtime-entity-code="runtimeEntityCode"
      :context="pickerContext"
      :multiple="isMultiple"
      :placeholder="placeholder"
      :disabled="isDisabled"
      @change="handleSelectionChange"
      v-on="customEventListeners"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import EntitySelector from '@/components/EntitySelector.vue'
import { useFormField } from '../composables/useFormField.js'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [String, Number, Array], default: '' },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const { fieldValue, placeholder, handleSelectionChange, handleFocus, handleBlur, customEventListeners, parsedComponentProps } = useFormField(
  props,
  emit
)

// 实体引用字段（含 CUSTOM 自定义实体）忽略 isReadonly，由父组件的 disabled 控制
const isDisabled = computed(() => {
  const refType = (props.field?.refEntityType || '').toUpperCase()
  if (['USER', 'DEPT', 'ROLE', 'GROUP', 'CUSTOM'].includes(refType)) {
    return props.disabled
  }
  return props.disabled || props.field?.isReadonly === 1
})

const fieldType = computed(() => {
  // 优先使用实际的 fieldType（如 dept/reference），其次才是 componentType
  // 避免 componentType 为通用值（如 input）时覆盖实际的字段类型
  return (props.field?.fieldType || props.field?.componentType || '').toLowerCase()
})

const isMultiple = computed(() => {
  return fieldType.value === 'multi_reference'
})

const entityType = computed(() => {
  // 后端 refEntityType: CUSTOM/USER/DEPT/ROLE/GROUP
  // 前端 fieldType: user, dept, reference, multi_reference
  const ft = fieldType.value
  if (ft === 'user') return 'USER'
  if (ft === 'dept') return 'DEPT'
  // 对于 reference/multi_reference 等类型，使用 refEntityType
  const refType = (props.field?.refEntityType || '').toUpperCase()
  if (['USER', 'DEPT', 'ROLE', 'GROUP'].includes(refType)) {
    return refType
  }
  return 'CUSTOM'
})

const entityCode = computed(() => {
  // refEntityId 是实体定义ID，不是实体编码，不能作为 entityCode 传递
  // 让 EntitySelector 通过 refEntityId 让后端查询真正的 entityCode
  return ''
})

const runtimeEntityCode = computed(() => {
  return parsedComponentProps.value.refConfig?.entityCode
    || props.field?.refEntityCode
    || ''
})

const listKey = computed(() => {
  return parsedComponentProps.value.refConfig?.listKey
    || props.field?.refListKey
    || ''
})

const pickerContext = computed(() => {
  return parsedComponentProps.value.refConfig?.context || {}
})

const refEntityId = computed(() => {
  return props.field?.refEntityId || ''
})

// 复合选择器以输入区域为焦点边界，内部按钮之间移动不重复触发。
function onFocusIn(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) handleFocus()
}

function onFocusOut(event) {
  if (!event.currentTarget.contains(event.relatedTarget)) handleBlur()
}
</script>

<style scoped>
.entity-field {
  width: 100%;
}
</style>
