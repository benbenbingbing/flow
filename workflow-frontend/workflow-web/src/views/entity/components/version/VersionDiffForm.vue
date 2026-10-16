<template>
  <div class="version-diff-form">
    <section v-for="section in visibleSections" :key="section.sectionCode" class="form-section">
      <header>
        <strong>{{ section.sectionName }}</strong>
        <span>{{ section.fields.length }} 项</span>
      </header>
      <VersionDiffField
        v-for="field in section.fields"
        :key="field.fieldCode"
        :field="field"
        :show-code="showCode"
      />
    </section>
    <el-empty v-if="!visibleSections.length" description="筛选条件下没有字段变化" :image-size="70" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import VersionDiffField from './VersionDiffField.vue'

const props = defineProps<{
  sections?: any[]
  changedOnly?: boolean
  showCode?: boolean
}>()
const visibleSections = computed(() => (props.sections || [])
  .map(section => ({
    ...section,
    fields: props.changedOnly
      ? (section.fields || []).filter((field: any) => field.changeType !== 'UNCHANGED')
      : (section.fields || [])
  }))
  .filter(section => section.fields.length))
</script>

<style scoped>
.form-section { overflow: hidden; margin-bottom: 14px; border: 1px solid var(--el-border-color); border-radius: 8px; background: var(--el-bg-color); }
.form-section > header { display: flex; align-items: center; justify-content: space-between; padding: 11px 14px; background: var(--el-fill-color-lighter); border-bottom: 1px solid var(--el-border-color); }
.form-section > header span { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
