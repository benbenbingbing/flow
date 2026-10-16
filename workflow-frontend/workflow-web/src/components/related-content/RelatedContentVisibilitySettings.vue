<template>
  <LinkageConditionRuleEditor title="条件显示" description="满足条件时显示当前关联区域。" :enabled="enabled" :root="root" :fields="fields" @update:enabled="toggle" @change="persist" />
  <el-form-item label="运行模式显示">
    <el-checkbox v-for="mode in modes" :key="mode.value" :model-value="presentation.visibleModes?.[mode.value] !== false" @update:model-value="updateMode(mode.value, $event)">{{ mode.label }}</el-checkbox>
  </el-form-item>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import LinkageConditionRuleEditor from '@/components/LinkageConditionRuleEditor.vue'
import { createFlowConditionConfig, createFlowConditionGroup, parseFlowConditionConfig } from '@flow/workflow-core/utils/flowConditionGroups'
const props = defineProps({ presentation: { type: Object, required: true }, fields: { type: Array, default: () => [] } })
const modes = [{ value: 'create', label: '新增' }, { value: 'edit', label: '编辑' }, { value: 'view', label: '查看' }, { value: 'approve', label: '审批' }]
const enabled = computed(() => !!props.presentation.visibilityCondition)
const root = ref(createFlowConditionGroup())
let writing = false
watch(() => JSON.stringify(props.presentation.visibilityCondition), () => {
  if (!writing) root.value = parseFlowConditionConfig(props.presentation.visibilityCondition) || createFlowConditionGroup()
}, { immediate: true })
// 未完成条件也留在本地草稿中，切换入口不会丢失；保存和发布统一检查完整性。
function persist() {
  writing = true
  props.presentation.visibilityCondition = createFlowConditionConfig(root.value)
  writing = false
}
function toggle(value) {
  if (value) persist()
  else delete props.presentation.visibilityCondition
}
function updateMode(mode, value) { props.presentation.visibleModes = { ...(props.presentation.visibleModes || {}), [mode]: value } }
</script>
