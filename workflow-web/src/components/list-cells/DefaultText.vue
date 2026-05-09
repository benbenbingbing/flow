<template>
  <span>{{ displayValue }}</span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: [String, Number, Boolean, Object, Array], default: '' },
  row: { type: Object, default: () => ({}) },
  field: { type: Object, default: () => ({}) },
  config: { type: Object, default: () => ({}) }
})

const displayValue = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') {
    return '-'
  }
  // 如果配置了 formatter（简单数值格式化），尝试应用
  const formatter = props.field?.formatter
  if (formatter && typeof props.value === 'number') {
    try {
      return props.value.toFixed(parseInt(formatter.replace(/[^0-9]/g, '')) || 2)
    } catch (e) {
      return props.value
    }
  }
  return props.value
})
</script>
