<template>
  <div>
    <SettingsCapability :disabled="disabled" reason="仅实体字段支持条件状态">
      <div class="node-state-conditions">
        <LinkageConditionRuleEditor
          v-for="definition in FIELD_STATE_CONDITIONS"
          :key="definition.name"
          :title="definition.title"
          :description="definition.description"
          :enabled="states[definition.name].enabled"
          :disabled="disabled"
          :root="states[definition.name].root"
          :fields="availableFields"
          :parse-warning="states[definition.name].parseWarning"
          @update:enabled="!disabled && setEnabled(definition.name, $event)"
          @change="!disabled && persist(definition.name)"
          @reset-group="!disabled && resetCondition(definition.name)"
        />
      </div>
    </SettingsCapability>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import LinkageConditionRuleEditor from '@/components/LinkageConditionRuleEditor.vue'
import SettingsCapability from '@/components/SettingsCapability.vue'
import { useFieldStateConditions } from '@/composables/useFieldStateConditions'
import { FIELD_STATE_CONDITIONS } from '@/shared/form-field-state-conditions'
import { getProcessConditionFieldType } from '@/shared/process-config'

const props = defineProps({
  field: { type: Object, required: true },
  disabled: { type: Boolean, default: false },
  fields: { type: Array, default: () => [] }
})

const availableFields = computed(() => props.fields.filter(field =>
  field.uiConfigurable !== false
    && (field.fieldCode || field.fieldKey) !== (props.field.fieldCode || props.field.fieldKey)))
const { states, persist, setEnabled, resetCondition } = useFieldStateConditions(
  () => props.field,
  code => getProcessConditionFieldType(availableFields.value.find(field =>
    (field.fieldCode || field.fieldKey) === code))
)
</script>

<style scoped>
.node-state-conditions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* 抽屉宽度与浏览器视口不同，嵌套条件组必须按窄面板换行，不能依赖页面媒体查询。 */
.node-state-conditions :deep(.group-header) {
  flex-direction: column;
  align-items: stretch;
}

.node-state-conditions :deep(.group-actions) {
  justify-content: flex-start;
}

.node-state-conditions :deep(.condition-row) {
  flex-wrap: wrap;
}

.node-state-conditions :deep(.condition-property),
.node-state-conditions :deep(.condition-operator),
.node-state-conditions :deep(.condition-value) {
  flex: 1 1 120px;
  min-width: 0;
}
</style>
