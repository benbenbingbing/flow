<template>
  <section class="acceptance-summary"><h3>{{ config.title || '扩展执行摘要' }}</h3>
    <MobileReadonlyField v-for="field in fields" :key="field.fieldCode" :field="field" :model-value="modelValue[field.fieldCode]" />
    <VanButton v-if="!readonly && mode !== 'view'" block plain size="small" :loading="loading" @click="execute">执行节点数据源</VanButton>
    <p v-if="error" role="alert">{{ error }}</p>
  </section>
</template>
<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { Button as VanButton } from 'vant'
import { MobileReadonlyField } from '@flow/workflow-mobile-ui'
const props = defineProps({ node: Object, config: { type: Object, default: () => ({}) }, modelValue: Object, readonly: Boolean, mode: String, dataSourceRuntime: Object })
const emit = defineEmits(['update:modelValue'])
const fields = [['name', '验收单'], ['acceptance_score', '验收评分'], ['acceptance_scene', '验收场景'], ['provider_trace', '数据源轨迹'], ['extension_summary', '执行结果']].map(([fieldCode, fieldName]) => ({ fieldCode, fieldName }))
const loading = ref(false), error = ref(''); let alive = true
async function execute() {
  if (loading.value || props.readonly || props.mode === 'view') return
  loading.value = true; error.value = ''
  try {
    if (!props.dataSourceRuntime?.executeOwnerUsage) throw new Error('当前节点没有可用的数据源运行时')
    const results = await props.dataSourceRuntime.executeOwnerUsage(props.node, 'FIELD_COMPUTE', { record: props.modelValue, input: { fieldCode: 'extension_summary', value: Number(props.modelValue.acceptance_score || 0) } })
    const first = results[0]?.data ?? results[0]
    if (alive) emit('update:modelValue', { ...props.modelValue, extension_summary: first?.value ?? first ?? '节点 Provider 已执行' })
  } catch (cause) { if (alive) error.value = cause.message }
  finally { loading.value = false }
}
onBeforeUnmount(() => { alive = false })
defineExpose({ validate: () => !loading.value })
</script>
<style scoped>.acceptance-summary { border: 1px solid var(--flow-mobile-border); border-radius: 12px; padding: 12px; margin: 12px 0; }.acceptance-summary h3 { font-size: 15px; margin: 0; }.acceptance-summary p { color: #bc4738; font-size: 12px; }</style>
