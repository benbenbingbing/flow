<template>
  <div
    class="diff-field"
    :class="`change-${field.changeType.toLowerCase()}`"
    :data-diff-change="changed ? 'true' : 'false'"
    :tabindex="changed ? -1 : undefined"
  >
    <div class="diff-field__label">
      <strong>{{ field.label }}</strong>
      <span v-if="showCode">{{ field.fieldCode }}</span>
      <el-tag :type="changeTagType" effect="plain" size="small">{{ changeText }}</el-tag>
      <el-tag v-if="field.displayChanged" type="info" effect="plain" size="small">中文展示变化</el-tag>
    </div>
    <div class="diff-field__value old-value">
      <small>基准版本</small>
      <HistoricalValue :value="field.oldValue" side="old" />
    </div>
    <div class="diff-field__value new-value">
      <small>对比版本</small>
      <HistoricalValue :value="field.newValue" side="new" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import HistoricalValue from './HistoricalValue.vue'

const props = defineProps<{
  field: any
  showCode?: boolean
}>()
const changed = computed(() => props.field.changeType !== 'UNCHANGED')
const changeText = computed(() => ({
  ADDED: '新增', REMOVED: '删除', MODIFIED: '修改', MOVED: '移动',
  NOT_COMPARABLE: '不可比较', UNCHANGED: '未变化'
})[props.field.changeType] || props.field.changeType)
const changeTagType = computed(() => ({
  ADDED: 'success', REMOVED: 'danger', MODIFIED: 'warning', MOVED: 'primary',
  NOT_COMPARABLE: 'info', UNCHANGED: 'info'
})[props.field.changeType] || 'info')
</script>

<style scoped>
.diff-field {
  display: grid;
  grid-template-columns: minmax(150px, 24%) minmax(0, 1fr) minmax(0, 1fr);
  gap: 10px;
  padding: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  border-left: 4px solid transparent;
}
.diff-field:last-child { border-bottom: 0; }
.diff-field__label { display: flex; align-content: flex-start; align-items: flex-start; flex-wrap: wrap; gap: 6px; padding: 8px 4px; }
.diff-field__label strong { width: 100%; line-height: 1.45; }
.diff-field__label > span { color: var(--el-text-color-secondary); font-family: monospace; font-size: 12px; }
.diff-field__value small { display: block; margin-bottom: 5px; color: var(--el-text-color-secondary); }
.change-added { border-left-color: var(--el-color-success); background: var(--el-color-success-light-9); }
.change-removed { border-left-color: var(--el-color-danger); background: var(--el-color-danger-light-9); }
.change-modified { border-left-color: var(--el-color-warning); background: var(--el-color-warning-light-9); }
.change-moved { border-left-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.change-not_comparable { border-left-color: var(--el-text-color-secondary); background: var(--el-fill-color-light); }
.change-added :deep(.new-value .historical-value) { border-color: var(--el-color-success-light-5); }
.change-removed :deep(.old-value .historical-value) { border-color: var(--el-color-danger-light-5); }
.change-modified :deep(.historical-value) { border-color: var(--el-color-warning-light-5); }

@media (max-width: 767px) {
  .diff-field { grid-template-columns: 1fr; gap: 7px; }
  .diff-field__label { padding-bottom: 0; }
}
</style>
