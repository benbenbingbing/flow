<template>
  <el-tag :type="tagType" :size="size">{{ displayValue }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: [String, Number], default: '' },
  row: { type: Object, default: () => ({}) },
  field: { type: Object, default: () => ({}) },
  config: { type: Object, default: () => ({}) }
})

const size = computed(() => props.config?.size || 'small')

const tagType = computed(() => {
  const val = String(props.value || '').toLowerCase()
  const mapping = props.config?.statusMap || {}
  if (mapping[val]) return mapping[val]
  // 默认映射
  if (val === 'approved' || val === 'completed' || val === '通过' || val === '已完成') return 'success'
  if (val === 'rejected' || val === 'terminated' || val === '驳回' || val === '已终止') return 'danger'
  if (val === 'pending' || val === 'running' || val === '审批中' || val === '运行中') return 'warning'
  if (val === 'draft' || val === '草稿') return 'info'
  return undefined
})

const displayValue = computed(() => {
  if (props.value === null || props.value === undefined) return '-'
  const labelMap = props.config?.labelMap || {}
  return labelMap[String(props.value)] || props.value
})
</script>
