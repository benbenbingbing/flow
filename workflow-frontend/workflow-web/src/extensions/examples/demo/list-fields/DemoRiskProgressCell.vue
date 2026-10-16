<template>
  <div class="demo-risk-progress">
    <el-progress
      :percentage="percentage"
      :status="progressStatus"
      :stroke-width="10"
      :show-text="config.showText !== false"
    />
    <el-tag v-if="config.showLevel !== false" :type="tagType" size="small">
      {{ levelText }}
    </el-tag>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  value: { type: [String, Number], default: 0 },
  row: { type: Object, default: () => ({}) },
  field: { type: Object, default: () => ({}) },
  config: { type: Object, default: () => ({}) },
  context: { type: Object, default: () => ({}) }
})

const percentage = computed(() => {
  const value = Number(props.value)
  if (Number.isNaN(value)) return 0
  return Math.min(100, Math.max(0, value))
})

const warningAt = computed(() => Number(props.config.warningAt ?? 40))
const dangerAt = computed(() => Number(props.config.dangerAt ?? 70))

const levelText = computed(() => {
  if (percentage.value >= dangerAt.value) return props.config.dangerText || '高风险'
  if (percentage.value >= warningAt.value) return props.config.warningText || '需关注'
  return props.config.safeText || '低风险'
})

const progressStatus = computed(() => {
  if (percentage.value >= dangerAt.value) return 'exception'
  if (percentage.value < warningAt.value) return 'success'
  return 'warning'
})

const tagType = computed(() => {
  if (percentage.value >= dangerAt.value) return 'danger'
  if (percentage.value >= warningAt.value) return 'warning'
  return 'success'
})
</script>

<style scoped>
.demo-risk-progress {
  display: grid;
  grid-template-columns: minmax(90px, 1fr) auto;
  align-items: center;
  gap: 8px;
  min-width: 180px;
}
</style>
