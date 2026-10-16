<template>
  <div class="historical-value" :class="[`is-${side}`, `state-${normalized.state.toLowerCase()}`]">
    <span class="historical-value__text">{{ text }}</span>
    <el-tag v-if="normalized.resolution === 'RAW_FALLBACK'" type="info" effect="plain" size="small">
      原始值
    </el-tag>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { frozenValueText, normalizeFrozenValue } from '@/shared/entity-version-model'

const props = withDefaults(defineProps<{
  value?: any
  side?: 'old' | 'new' | 'snapshot'
}>(), {
  value: null,
  side: 'snapshot'
})

const normalized = computed(() => normalizeFrozenValue(props.value))
const text = computed(() => frozenValueText(normalized.value))
</script>

<style scoped>
.historical-value {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  min-height: 38px;
  gap: 8px;
  padding: 9px 11px;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
}
.historical-value__text { white-space: pre-wrap; overflow-wrap: anywhere; }
.state-empty, .state-not_captured, .state-field_missing { color: var(--el-text-color-secondary); background: var(--el-fill-color-lighter); font-style: italic; }
</style>
