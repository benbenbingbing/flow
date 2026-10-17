<template>
  <div class="acceptance-score-cell">
    <el-progress
      :percentage="score"
      :stroke-width="7"
      :show-text="false"
      :status="progressStatus"
    />
    <span>{{ score }}</span>
    <el-tag :type="tagType" size="small">{{ levelLabel }}</el-tag>
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

const score = computed(() => {
  const value = Number(props.value)
  return Number.isFinite(value)
    ? Math.max(0, Math.min(100, value))
    : 0
})
const passScore = computed(() => Number(props.config.passScore ?? 60))
const levelLabel = computed(() => {
  if (score.value >= 85) return '优秀'
  if (score.value >= passScore.value) return '通过'
  return '待改进'
})
const tagType = computed(() => {
  if (score.value >= 85) return 'success'
  if (score.value >= passScore.value) return 'primary'
  return 'danger'
})
const progressStatus = computed(() =>
  score.value >= passScore.value ? 'success' : 'exception'
)
</script>

<style scoped>
.acceptance-score-cell {
  display: grid;
  grid-template-columns: minmax(80px, 1fr) 30px 58px;
  gap: 8px;
  align-items: center;
  min-width: 180px;
}
</style>
