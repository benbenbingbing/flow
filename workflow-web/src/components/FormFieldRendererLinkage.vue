/**
 * 支持字段联动的字段渲染器
 * 基于 formFieldComponentMap 动态渲染对应字段组件
 */

<template>
  <div class="form-field-renderer-linkage">
    <component
      :is="resolvedComponent"
      :field="field"
      :modelValue="modelValue"
      @update:modelValue="$emit('update:modelValue', $event)"
      :disabled="disabled"
      :options="options"
      :context="context"
      :data-source-runtime="dataSourceRuntime"
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
    type: [String, Number, Array, Date, Object, Boolean],
    default: ''
  },
  disabled: {
    type: Boolean,
    default: false
  },
  options: {
    type: Array,
    default: null
  },
  context: {
    type: Object,
    default: () => ({})
  },
  dataSourceRuntime: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const resolvedComponent = computed(() => {
  const component = resolveFieldComponent(props.field)
  return component || TextField
})
</script>

<style scoped>
.form-field-renderer-linkage {
  width: 100%;
}
</style>
