<template>
  <LinkageConditionRuleEditor
    :title="title" :description="description" :enabled="modelValue != null" :root="root"
    :show-group-tip="false" :fields="conditionFields"
    @update:enabled="emit('update:modelValue', $event ? createFlowConditionConfig(root) : null)"
    @change="emit('update:modelValue', createFlowConditionConfig(root))"
  />
</template>

<script setup>
import { computed } from 'vue'
import LinkageConditionRuleEditor from '@/components/LinkageConditionRuleEditor.vue'
import { formContextConditionFields } from '@/shared/form-context-parameters'
import { createFlowConditionConfig, createFlowConditionGroup, parseFlowConditionConfig } from '@flow/workflow-core/utils/flowConditionGroups'

const props = defineProps({
  modelValue: { type: Object, default: null },
  title: { type: String, required: true },
  description: String,
  fields: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue'])
const root = computed(() => parseFlowConditionConfig(props.modelValue) || createFlowConditionGroup())
const conditionFields = computed(() => [...props.fields, ...formContextConditionFields])
</script>
