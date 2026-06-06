<template>
  <div class="entity-field" @click="handleClick">
    <EntitySelector
      v-model="fieldValue"
      :entity-type="entityType"
      :entity-code="entityCode"
      :ref-entity-id="refEntityId"
      :api-url="apiUrl"
      :multiple="isMultiple"
      :placeholder="placeholder"
      :disabled="isDisabled"
      @change="handleEntityChange"
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

const { fieldValue, placeholder, handleChange, parsedComponentProps } = useFormField(
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

const refEntityId = computed(() => {
  return props.field?.refEntityId || ''
})

const apiUrl = computed(() => {
  if (parsedComponentProps.value.refConfig?.apiUrl) {
    return parsedComponentProps.value.refConfig.apiUrl
  }
  return props.field?.apiUrl || null
})

function handleEntityChange(val) {
  // EntitySelector 的 change 事件传递的是选中对象/对象数组
  // v-model 已经通过 update:modelValue 传递了正确的 ID/ID 数组
  // 这里只触发外部 change 事件（供自定义脚本使用），不再重复 emit update:modelValue
  emit('change', val)
}

function handleClick() {
  // no-op，保留以兼容模板@click
}
</script>

<style scoped>
.entity-field {
  width: 100%;
}
</style>
