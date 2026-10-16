<template>
  <div class="snapshot-form">
    <section v-for="section in sections || []" :key="section.sectionCode" class="snapshot-section">
      <header><strong>{{ section.sectionName }}</strong><span>{{ section.fields?.length || 0 }} 项</span></header>
      <div v-for="field in section.fields || []" :key="field.fieldCode" class="snapshot-field">
        <div><strong>{{ field.label }}</strong><small v-if="showCode">{{ field.fieldCode }}</small></div>
        <HistoricalValue :value="field.value" side="snapshot" />
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import HistoricalValue from './HistoricalValue.vue'
defineProps<{ sections?: any[], showCode?: boolean }>()
</script>

<style scoped>
.snapshot-section { overflow: hidden; margin-bottom: 14px; border: 1px solid var(--el-border-color); border-radius: 8px; }
.snapshot-section header { display: flex; justify-content: space-between; padding: 10px 14px; background: var(--el-fill-color-lighter); }
.snapshot-section header span { color: var(--el-text-color-secondary); font-size: 12px; }
.snapshot-field { display: grid; grid-template-columns: minmax(160px, 28%) 1fr; gap: 12px; padding: 10px 14px; border-top: 1px solid var(--el-border-color-lighter); }
.snapshot-field > div:first-child { display: flex; flex-direction: column; padding-top: 8px; }
.snapshot-field small { color: var(--el-text-color-secondary); font-family: monospace; }
@media (max-width: 767px) { .snapshot-field { grid-template-columns: 1fr; gap: 5px; } }
</style>
