<template>
  <EventBindingEditor
    ref="editorRef"
    owner-type="FORM"
    :owner-id="formId"
    target-type="FIELD"
    :target-key="field?.fieldCode || ''"
    :target-name="field?.fieldLabel || field?.fieldName || ''"
    :target-field="field"
    :field-options="fieldOptions"
    :disabled-reason="disabledReason"
    compact
    @changed="emit('changed')"
  >
    <template #toolbar-actions>
      <el-tooltip :disabled="!quickFillDisabledReason" :content="quickFillDisabledReason" placement="top">
        <span>
          <el-button type="primary" plain :disabled="!!quickFillDisabledReason" @click="openQuickFill">
            配置快捷回填
          </el-button>
        </span>
      </el-tooltip>
    </template>
  </EventBindingEditor>
  <EntitySelectionMappingDialog
    ref="mappingDialogRef"
    :form-id="formId"
    :form-fields="formFields"
    @changed="handleQuickFillChanged"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import EventBindingEditor from '@/components/ui-config/EventBindingEditor.vue'
import EntitySelectionMappingDialog from '@/components/ui-config/EntitySelectionMappingDialog.vue'
import { isPersistedEntitySelectionField } from '@flow/workflow-core/entity-selection-mapping'
import { isSingleEntitySelectionEventField } from '@/components/ui-config/uiFieldEventCapabilities'

const props = defineProps({
  formId: { type: [String, Number], default: '' },
  field: { type: Object, default: null },
  formFields: { type: Array, default: () => [] },
  fieldOptions: { type: Array, default: () => [] },
  enabled: { type: Boolean, default: true }
})
const emit = defineEmits(['changed'])
const editorRef = ref(null)
const mappingDialogRef = ref(null)
const disabledReason = computed(() => {
  if (!props.enabled) return '仅实体字段支持事件与回填'
  if (!props.formId) return '请先保存表单草稿'
  if (!props.field?.fieldCode) return '当前节点没有稳定字段编码，无法绑定字段事件'
  return isPersistedEntitySelectionField(props.field) ? '' : '请先保存当前字段节点，再配置事件与回填'
})
const quickFillDisabledReason = computed(() => disabledReason.value
  || (isSingleEntitySelectionEventField(props.field) ? '' : '仅单选实体引用字段支持快捷回填'))

function openQuickFill() {
  if (!quickFillDisabledReason.value) mappingDialogRef.value?.open(props.field)
}

/** 两个编辑入口共用事件绑定，快捷保存后立即同步列表并通知表单刷新草稿状态。 */
async function handleQuickFillChanged() {
  await editorRef.value?.reload()
  emit('changed')
}
</script>
