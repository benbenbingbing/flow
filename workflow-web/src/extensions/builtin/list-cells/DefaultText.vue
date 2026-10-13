<template>
  <span>{{ displayValue }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { formatDefaultCellValue } from '@/shared/list-cell-masking'

const props = defineProps({
  value: { type: [String, Number, Boolean, Object, Array], default: '' },
  row: { type: Object, default: () => ({}) },
  field: { type: Object, default: () => ({}) },
  config: { type: Object, default: () => ({}) }
})

// 与脱敏入口共用格式化，确保保留位数针对最终展示文本。
const displayValue = computed(() => formatDefaultCellValue(props.value, props.field, props.config))
</script>
