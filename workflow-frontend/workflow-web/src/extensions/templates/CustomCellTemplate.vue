<!--
  单元格模板，可直接以不同 config 复用；config.emptyText/prefix/suffix 调整显示。
  使用 null/undefined/空串检查保留 0 和 false。Vue 文本插值不执行 HTML 或脚本。
-->
<template><span>{{ displayValue }}</span></template>

<script setup>
import { computed } from 'vue'
import { listCellProps } from '@/extensions/contracts/list-cell.js'

const props = defineProps(listCellProps)
const displayValue = computed(() => {
  const value = props.value
  if (value === null || value === undefined || value === '') return props.config.emptyText ?? '—'
  return `${props.config.prefix ?? ''}${value}${props.config.suffix ?? ''}`
})
</script>
