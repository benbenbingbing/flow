<template>
  <section class="acceptance-summary"><h3>{{ config.title || '扩展执行摘要' }}</h3>
    <MobileReadonlyField v-for="field in fields" :key="field.fieldCode" :field="field" :model-value="formData[field.fieldCode]" />
    <VanButton v-if="!disabled && !readonly && mode !== 'view'" block plain size="small" :loading="loading" @click="execute">执行节点数据源</VanButton>
    <p v-if="error" role="alert">{{ error }}</p>
  </section>
</template>
<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { formFieldProps, formFieldEmits } from '@flow/workflow-extension-sdk/contracts/form-field'
import { Button as VanButton } from 'vant'
import { MobileReadonlyField } from '@flow/workflow-mobile-ui'
const props = defineProps(formFieldProps)
const emit = defineEmits(formFieldEmits)
const fields = [['name', '验收单'], ['acceptance_score', '验收评分'], ['acceptance_scene', '验收场景'], ['provider_trace', '数据源轨迹'], ['extension_summary', '执行结果']].map(([fieldCode, fieldName]) => ({ fieldCode, fieldName }))
const loading = ref(false), error = ref(''); let alive = true
/** 数据源结果仅提交修改字段，宿主统一合并并触发校验与联动。 */
async function execute() {
  if (loading.value || props.disabled || props.readonly || props.mode === 'view') return
  loading.value = true; error.value = ''
  const record = props.formData, node = props.node
  try {
    if (!props.dataSourceRuntime?.executeOwnerUsage) throw new Error('当前节点没有可用的数据源运行时')
    const results = await props.dataSourceRuntime.executeOwnerUsage(props.node || props.field, 'FIELD_COMPUTE', { record, input: { fieldCode: 'extension_summary', value: Number(props.formData.acceptance_score || 0) } })
    const first = results[0]?.data ?? results[0]
    if (alive && props.formData === record && props.node === node) emit('patch-form', { extension_summary: first?.value ?? first ?? '节点 Provider 已执行' })
  } catch (cause) { if (alive) error.value = cause.message }
  finally { loading.value = false }
}
onBeforeUnmount(() => { alive = false })
defineExpose({ validate: () => !loading.value })
</script>
<style scoped>.acceptance-summary { border: 1px solid var(--flow-mobile-border); border-radius: 12px; padding: 12px; margin: 12px 0; }.acceptance-summary h3 { font-size: 15px; margin: 0; }.acceptance-summary p { color: #bc4738; font-size: 12px; }</style>
