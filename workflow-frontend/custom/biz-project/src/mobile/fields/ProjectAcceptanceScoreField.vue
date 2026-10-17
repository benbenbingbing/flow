<template>
  <MobileReadonlyField v-if="disabled" :field="field" :model-value="`${score} · ${level}`" />
  <div v-else class="score-field">
    <div class="score-label"><span>{{ field.fieldLabel || field.fieldName || '验收评分' }}</span><VanTag plain type="primary">{{ level }}</VanTag></div>
    <div class="score-control"><VanSlider :model-value="score" :min="0" :max="100" @update:model-value="update" @change="change" /><VanStepper :model-value="score" :min="0" :max="100" @change="change" /></div>
    <VanButton size="small" plain :loading="loading" @click="execute">执行字段按钮事件</VanButton>
    <p v-if="error" role="alert">{{ error }}</p>
  </div>
</template>
<script setup>
import { computed, ref, onBeforeUnmount } from 'vue'
import { Slider as VanSlider, Stepper as VanStepper, Tag as VanTag, Button as VanButton } from 'vant'
import { MobileReadonlyField } from '@flow/workflow-mobile-ui'
import { safeParseConfig } from '@flow/workflow-core/config-runtime'
import { useFormField } from '@flow/workflow-core/vue/useFormField'
defineOptions({ scriptEvents: [
  { name: 'onChange', label: '值变化', description: '确认修改评分后触发' },
  { name: 'onScoreCommit', label: '提交评分', description: '滑块或步进器提交评分时触发，随后执行值变化事件', parameters: [
    { name: 'score', type: 'number', description: '本次提交的原始评分', required: true }
  ] }
] })
const props = defineProps({ field: Object, modelValue: [String, Number], disabled: Boolean, context: Object, services: Object })
const emit = defineEmits(['update:modelValue', 'change'])
const fieldEvents = useFormField(props, emit)
const score = computed(() => Math.max(0, Math.min(100, Number(props.modelValue) || 0)))
const level = computed(() => score.value >= 85 ? '优秀' : score.value >= Number(safeParseConfig(props.field.componentProps).passScore ?? 60) ? '通过' : '待改进')
const loading = ref(false), error = ref('')
let revision = 0
function update(value) { revision++; fieldEvents.fieldValue.value = Number(value) }
/** 同 PC 一样由真实交互触发业务事件，再用处理后的值进入标准 change 链路。 */
async function change(value) {
  update(value)
  const result = await fieldEvents.triggerScriptEvent('onScoreCommit', { value: Number(value), data: { score: Number(value) } })
  if (result.current) await fieldEvents.handleChange(result.value)
}
async function execute() {
  if (loading.value || props.disabled) return
  const current = ++revision; loading.value = true; error.value = ''
  try {
    if (!props.services?.fieldEvent) throw new Error('字段事件服务不可用')
    await props.services.fieldEvent('FIELD_BUTTON_CLICK', props.field, score.value, { ...props.context, eventSource: 'ProjectAcceptanceScoreField' }, () => current === revision)
  } catch (cause) { error.value = cause.message }
  finally { loading.value = false }
}
onBeforeUnmount(() => revision++)
defineExpose({ validate: () => !loading.value && Number.isFinite(Number(props.modelValue)) })
</script>
<style scoped>.score-field { padding: 14px 0; }.score-label { display: flex; justify-content: space-between; color: var(--flow-mobile-muted); }.score-control { display: flex; align-items: center; gap: 22px; margin: 20px 6px; }.score-control :deep(.van-slider) { flex: 1; }.score-field p { color: #bc4738; font-size: 12px; }</style>
