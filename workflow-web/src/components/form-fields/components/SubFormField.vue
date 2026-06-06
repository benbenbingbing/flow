<template>
  <div class="sub-form-field">
    <SubFormRenderer
      v-model="fieldValue"
      :config="subFormConfig"
      :readonly="isDisabled"
      :disabled="isDisabled"
      @change="handleChange"
    />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import SubFormRenderer from '@/components/SubFormRenderer.vue'
import { useFormField } from '../composables/useFormField.js'
import { getFormFields } from '@/api/entityForm'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [Array, String, Object, Number], default: () => [] },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null }
})

const emit = defineEmits(['update:modelValue', 'change', 'blur', 'focus'])

const { fieldValue, isDisabled, handleChange, parsedComponentProps } = useFormField(props, emit)

// 子表单元数据
const subFormMeta = computed(() => {
  const field = props.field
  if (field?.subFormType === 'ref' && field?.refFormId) {
    return { subFormType: 'ref', refFormId: field.refFormId, refEntityId: field.refEntityId || '' }
  }
  if (parsedComponentProps.value.subFormConfig?.type === 'ref' && parsedComponentProps.value.subFormConfig?.refFormId) {
    return {
      subFormType: 'ref',
      refFormId: parsedComponentProps.value.subFormConfig.refFormId,
      refEntityId: parsedComponentProps.value.subFormConfig.refEntityId || ''
    }
  }
  return { subFormType: field?.subFormType || 'embedded', refFormId: null, refEntityId: '' }
})

// 外部表单字段（子表单引用外部表单时使用）
const externalFormFields = ref([])

watch(
  () => subFormMeta.value.refFormId,
  async (formId) => {
    if (formId && subFormMeta.value.subFormType === 'ref') {
      try {
        const res = await getFormFields(formId)
        const fields = Array.isArray(res) ? res : Array.isArray(res.data) ? res.data : []
        externalFormFields.value = fields.map((f) => ({
          fieldKey: f.fieldCode || f.fieldId || f.id,
          fieldName: f.fieldLabel || f.fieldName,
          fieldType: mapFieldType(f.componentType || f.fieldType),
          isEditable: true,
          isRequired: f.isRequired === 1,
          options: f.options
        }))
      } catch (e) {
        externalFormFields.value = []
      }
    } else {
      externalFormFields.value = []
    }
  },
  { immediate: true }
)

function mapFieldType(type) {
  const map = {
    string: 'TEXT',
    text: 'TEXT',
    integer: 'NUMBER',
    decimal: 'NUMBER',
    date: 'DATE',
    datetime: 'DATE',
    select: 'SELECT',
    radio: 'SELECT',
    checkbox: 'SELECT'
  }
  return map[(type || '').toLowerCase()] || 'TEXT'
}

function getSubFieldsFromField(field) {
  if (field?.subFields?.length) return field.subFields
  if (field?.fields?.length) return field.fields
  if (parsedComponentProps.value.subFormConfig?.fields?.length) {
    return parsedComponentProps.value.subFormConfig.fields
  }
  if (parsedComponentProps.value.fields?.length) return parsedComponentProps.value.fields
  if (parsedComponentProps.value.subFields?.length) return parsedComponentProps.value.subFields
  return []
}

const subFormConfig = computed(() => {
  const field = props.field
  const isRef = subFormMeta.value.subFormType === 'ref' && externalFormFields.value.length > 0
  const fields = isRef ? externalFormFields.value : getSubFieldsFromField(field)

  // 默认使用 form 布局（与设计器预览保持一致），优先读取字段配置
  let layout = 'form'
  if (field?.layout) {
    layout = field.layout
  } else if (parsedComponentProps.value.subFormConfig?.layout) {
    layout = parsedComponentProps.value.subFormConfig.layout
  }

  let repeatable = false
  if (field?.repeatable != null) {
    repeatable = field.repeatable
  } else if (parsedComponentProps.value.subFormConfig?.repeatable != null) {
    repeatable = parsedComponentProps.value.subFormConfig.repeatable
  }

  return {
    label: field?.fieldName || '明细',
    fieldKey: field?.fieldKey || 'detailList',
    required: field?.required || false,
    minRows: field?.minRows || 0,
    maxRows: field?.maxRows || 100,
    fields,
    showSummary: field?.showSummary || false,
    summaryFields: field?.summaryFields || [],
    layout,
    repeatable
  }
})
</script>

<style scoped>
.sub-form-field {
  width: 100%;
}
</style>
