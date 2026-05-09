<template>
  <span>{{ displayValue }}</span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: [String, Number, Date], default: '' },
  row: { type: Object, default: () => ({}) },
  field: { type: Object, default: () => ({}) },
  config: { type: Object, default: () => ({}) }
})

const displayValue = computed(() => {
  if (!props.value) return '-'
  const pattern = props.config?.pattern || props.field?.formatter || 'yyyy-MM-dd HH:mm:ss'
  try {
    const date = new Date(props.value)
    if (isNaN(date.getTime())) return props.value
    const pad = (n) => String(n).padStart(2, '0')
    return pattern
      .replace('yyyy', date.getFullYear())
      .replace('MM', pad(date.getMonth() + 1))
      .replace('dd', pad(date.getDate()))
      .replace('HH', pad(date.getHours()))
      .replace('mm', pad(date.getMinutes()))
      .replace('ss', pad(date.getSeconds()))
  } catch (e) {
    return props.value
  }
})
</script>
