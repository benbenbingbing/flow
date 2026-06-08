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
import { getEntityFields, getFormFields } from '@/api/entityForm'

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
  const config = parsedComponentProps.value.subFormConfig || {}
  const relationType = field?.relationType || field?.relation?.type || config.relationType || 'ONE_TO_MANY'
  const refEntityId = field?.childEntityId || field?.refEntityId || config.refEntityId || ''
  const childRefFieldCode = field?.childRefFieldCode || field?.refFieldCode || config.childRefFieldCode || ''
  const refFormId = field?.refFormId || config.refFormId || null

  return {
    refFormId,
    refEntityId,
    relationType,
    childRefFieldCode
  }
})

// 外部表单字段（子表单引用外部表单时使用）
const externalFormFields = ref([])

watch(
  () => [subFormMeta.value.refFormId, subFormMeta.value.refEntityId],
  async ([formId, refEntityId]) => {
    if (formId) {
      try {
        const res = await getFormFields(formId)
        const fields = Array.isArray(res) ? res : Array.isArray(res.data) ? res.data : []
        externalFormFields.value = normalizeExternalFields(fields)
      } catch (e) {
        externalFormFields.value = []
      }
    } else if (refEntityId) {
      try {
        const res = await getEntityFields(refEntityId)
        const fields = Array.isArray(res) ? res : Array.isArray(res.data) ? res.data : []
        externalFormFields.value = normalizeExternalFields(fields)
      } catch (e) {
        externalFormFields.value = []
      }
    } else {
      externalFormFields.value = []
    }
  },
  { immediate: true }
)

function normalizeExternalFields(fields) {
  const childRefFieldCode = subFormMeta.value.childRefFieldCode
  return fields
    .filter((f) => !f.isSystem && f.fieldCode !== childRefFieldCode)
    .map((f) => ({
      fieldKey: f.fieldCode || f.fieldId || f.id,
      fieldCode: f.fieldCode || f.fieldId || f.id,
      fieldName: f.fieldLabel || f.fieldName,
      fieldType: mapFieldType(f.componentType || f.fieldType),
      componentType: mapComponentType(f.componentType || f.fieldType),
      isEditable: true,
      isRequired: f.isRequired === 1 || f.isRequired === true,
      required: f.isRequired === 1 || f.isRequired === true,
      defaultValue: f.defaultValue,
      options: f.options,
      optionsJson: f.optionsJson,
      componentProps: f.componentProps,
      refEntityId: f.childEntityId || f.refEntityId,
      childEntityId: f.childEntityId || f.refEntityId,
      refFieldCode: f.childRefFieldCode || f.refFieldCode,
      childRefFieldCode: f.childRefFieldCode || f.refFieldCode,
      relationType: f.relationType,
      relation: f.relation
    }))
}

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
  if (['sub_form', 'sub_form_list'].includes((type || '').toLowerCase())) {
    return 'SUB_FORM'
  }
  return map[(type || '').toLowerCase()] || 'TEXT'
}

function mapComponentType(type) {
  const lower = (type || '').toLowerCase()
  if (['sub_form', 'sub_form_list'].includes(lower)) return 'sub_form'
  if (['string'].includes(lower)) return 'string'
  if (['text'].includes(lower)) return 'textarea'
  if (['integer', 'long', 'decimal', 'double', 'number'].includes(lower)) return 'number'
  if (['date', 'datetime'].includes(lower)) return lower
  if (['select', 'multi_select', 'radio', 'checkbox'].includes(lower)) return lower
  return lower || 'string'
}

function getSubFieldsFromField(field) {
  if (field?.fields?.length) return field.fields
  return []
}

const subFormConfig = computed(() => {
  const field = props.field
  const fields = externalFormFields.value.length > 0
    ? externalFormFields.value
    : getSubFieldsFromField(field)

  // 默认使用 form 布局（与设计器预览保持一致），优先读取字段配置
  let layout = 'form'
  if (field?.layout) {
    layout = field.layout
  } else if (parsedComponentProps.value.subFormConfig?.layout) {
    layout = parsedComponentProps.value.subFormConfig.layout
  }

  let repeatable = subFormMeta.value.relationType !== 'ONE_TO_ONE'
  if (field?.repeatable != null) {
    repeatable = field.repeatable
  } else if (parsedComponentProps.value.subFormConfig?.repeatable != null) {
    repeatable = parsedComponentProps.value.subFormConfig.repeatable
  }
  if (subFormMeta.value.relationType === 'ONE_TO_ONE') {
    repeatable = false
  }

  return {
    label: field?.fieldName || '明细',
    fieldKey: field?.fieldCode || field?.fieldKey || 'detailList',
    required: field?.required || false,
    minRows: field?.minRows || 0,
    maxRows: field?.maxRows || 100,
    fields,
    showSummary: field?.showSummary || false,
    summaryFields: field?.summaryFields || [],
    layout,
    repeatable,
    relationType: subFormMeta.value.relationType,
    childRefFieldCode: subFormMeta.value.childRefFieldCode
  }
})
</script>

<style scoped>
.sub-form-field {
  width: 100%;
}
</style>
