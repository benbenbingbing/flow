<template>
  <el-dialog
    v-model="visible"
    title="选择后回填"
    width="1080px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
  >
    <EntitySelectionMappingEditor
      v-if="field"
      :form-id="String(formId || '')"
      :field="field"
      :form-fields="formFields"
      @changed="$emit('changed')"
    />
  </el-dialog>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import EntitySelectionMappingEditor from './EntitySelectionMappingEditor.vue'
import {
  isPersistedEntitySelectionField,
  resolveEntitySelectionRefConfig
} from '@/shared/entity-selection-mapping'

const props = defineProps({
  formId: { type: [String, Number], default: '' },
  formFields: { type: Array, default: () => [] }
})

defineEmits(['changed'])
const visible = ref(false)
const field = ref(null)

function open(targetField) {
  if (!props.formId) {
    ElMessage.warning('请先保存表单草稿')
    return
  }
  if (!isPersistedEntitySelectionField(targetField)) {
    ElMessage.warning('请先保存当前字段节点，再配置选择后回填')
    return
  }
  if (!targetField?.fieldCode) {
    ElMessage.warning('当前字段没有稳定字段编码，无法配置回填')
    return
  }
  const refConfig = resolveEntitySelectionRefConfig(targetField)
  if (refConfig.refEntityType === 'CUSTOM' && !refConfig.refEntityId) {
    ElMessage.warning('请先配置关联实体并保存当前字段')
    return
  }
  field.value = targetField
  visible.value = true
}

defineExpose({ open })
</script>
