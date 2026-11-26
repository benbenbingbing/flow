<template>
  <section class="acceptance-summary"><h3>{{ config.title || '扩展执行摘要' }}</h3>
    <MobileReadonlyField v-for="field in fields" :key="field.fieldCode" :field="field" :model-value="formData[field.fieldCode]" />
  </section>
</template>
<script setup>
import { formFieldProps } from '@flow/workflow-extensions/contracts/form-field'
import { MobileReadonlyField } from '@flow/workflow-mobile-ui'
defineProps(formFieldProps)
const fields = [['name', '验收单'], ['acceptance_score', '验收评分'], ['acceptance_scene', '验收场景'], ['provider_trace', '数据源轨迹'], ['extension_summary', '执行结果']].map(([fieldCode, fieldName]) => ({ fieldCode, fieldName }))
</script>
<style scoped>.acceptance-summary { border: 1px solid var(--flow-mobile-border); border-radius: 12px; padding: 12px; margin: 12px 0; }.acceptance-summary h3 { font-size: 15px; margin: 0; }</style>
