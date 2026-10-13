<template>
  <span>{{ displayValue }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { formatDateCellValue } from '@/shared/list-cell-masking'

const props = defineProps({
  value: { type: [String, Number, Date], default: '' },
  row: { type: Object, default: () => ({}) },
  field: { type: Object, default: () => ({}) },
  config: { type: Object, default: () => ({}) }
})

// 日期格式化先于脱敏；两条展示路径共享同一规则。
const displayValue = computed(() => formatDateCellValue(props.value, props.field, props.config))
</script>
