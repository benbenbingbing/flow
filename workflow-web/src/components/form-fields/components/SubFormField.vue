<template>
  <div class="sub-form-field">
    <SubFormRenderer
      v-model="fieldValue"
      :config="subFormConfig"
      :readonly="isDisabled"
      :disabled="isDisabled"
      @change="handleChange"
    >
      <template v-if="hasNodeTree" #row="{ row, index }">
        <FormNodeRenderer
          :nodes="runtimeNodes"
          :root-parent-id="runtimeRootParentId"
          :fields="runtimeFields"
          :model-value="row"
          :readonly="isDisabled"
          :mode="context.mode || (isDisabled ? 'view' : 'edit')"
          :context="{ ...context, parentField: field, subFormRowIndex: index }"
          :data-source-runtime="dataSourceRuntime"
          @update:model-value="replaceNestedRow(row, $event)"
        />
      </template>
    </SubFormRenderer>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import SubFormRenderer from '@/components/SubFormRenderer.vue'
import FormNodeRenderer from '@/components/FormNodeRenderer.vue'
import { useFormField } from '../composables/useFormField.js'
import { getEntityFields, getFormRuntimeRelease } from '@/api/entityForm'

const props = defineProps({
  field: { type: Object, required: true },
  modelValue: { type: [Array, String, Object, Number], default: () => [] },
  disabled: { type: Boolean, default: false },
  options: { type: Array, default: null },
  context: { type: Object, default: () => ({}) },
  dataSourceRuntime: { type: Object, default: null }
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
  const childFormId = field?.childFormId
    || field?.refFormId
    || field?.publishedFormId
    || config.childFormId
    || config.refFormId
    || config.publishedFormId
    || null
  const childFormReleaseId = field?.childFormReleaseId
    || field?.refFormReleaseId
    || field?.publishedFormReleaseId
    || config.childFormReleaseId
    || config.refFormReleaseId
    || config.publishedFormReleaseId
    || null
  const childFormReleaseVersion = field?.childFormReleaseVersion
    ?? field?.refFormReleaseVersion
    ?? field?.publishedFormReleaseVersion
    ?? config.childFormReleaseVersion
    ?? config.refFormReleaseVersion
    ?? config.publishedFormReleaseVersion
    ?? null

  return {
    childFormId,
    refFormId: childFormId,
    childFormReleaseId,
    childFormReleaseVersion,
    refEntityId,
    relationType,
    childRefFieldCode
  }
})

// 外部表单字段（子表单引用外部表单时使用）
const externalFormFields = ref([])
const externalFormNodes = ref([])
const refFormLayoutType = ref('vertical')
const childFormDefinition = ref(null)
const childReleaseIdentity = ref('')
let releaseLoadSequence = 0
let childInitializationSequence = 0

watch(
  () => [
    subFormMeta.value.childFormId,
    subFormMeta.value.childFormReleaseId,
    subFormMeta.value.childFormReleaseVersion,
    subFormMeta.value.refEntityId
  ],
  async ([formId, releaseId, releaseVersion, refEntityId]) => {
    const sequence = ++releaseLoadSequence
    refFormLayoutType.value = 'vertical'
    childFormDefinition.value = null
    childReleaseIdentity.value = ''
    if (formId) {
      try {
        const release = await getFormRuntimeRelease(
          formId,
          releaseId,
          releaseVersion
        )
        if (!releaseId) {
          console.warn(
            `子表单 ${formId} 使用 legacy refFormId，已临时读取 ACTIVE release；请重新保存并发布父表单以固定版本`
          )
        }
        const snapshot = parseSnapshot(release.snapshotDocument)
        if (sequence !== releaseLoadSequence) return
        externalFormNodes.value = normalizeExternalNodes(snapshot?.nodes)
        externalFormFields.value = normalizeExternalFields(
          resolveSnapshotFields(snapshot)
        )
        childFormDefinition.value = snapshot?.form || {
          id: formId,
          entityId: refEntityId
        }
        childReleaseIdentity.value = `${formId}:${release.id || releaseId || 'active'}:${release.version || releaseVersion || 'latest'}`
        const layout = snapshot?.form?.layoutType
        if (layout) refFormLayoutType.value = layout
      } catch (error) {
        console.warn('固定子表单发布快照加载失败:', error)
        if (sequence !== releaseLoadSequence) return
        externalFormNodes.value = []
        externalFormFields.value = []
        childFormDefinition.value = null
      }
    } else if (refEntityId) {
      try {
        const res = await getEntityFields(refEntityId)
        if (sequence !== releaseLoadSequence) return
        const fields = Array.isArray(res) ? res : Array.isArray(res.data) ? res.data : []
        externalFormFields.value = normalizeExternalFields(fields)
        externalFormNodes.value = []
        childFormDefinition.value = {
          id: `entity:${refEntityId}`,
          entityId: refEntityId
        }
        childReleaseIdentity.value = `entity:${refEntityId}`
      } catch (error) {
        if (sequence !== releaseLoadSequence) return
        externalFormNodes.value = []
        externalFormFields.value = []
        childFormDefinition.value = null
      }
    } else {
      externalFormNodes.value = []
      externalFormFields.value = []
      childFormDefinition.value = null
    }
  },
  { immediate: true }
)

watch(
  () => [
    props.dataSourceRuntime,
    props.field?.dataSourceBindings,
    props.field?.dataSourceBindingsDocument
  ],
  async () => {
    if (!props.dataSourceRuntime?.loadSubformRows) return
    const current = fieldValue.value
    if (Array.isArray(current) && current.length > 0) return
    try {
      const rows = await props.dataSourceRuntime.loadSubformRows(props.field, {
        context: props.context,
        input: {
          fieldCode: props.field?.fieldCode,
          relation: subFormMeta.value
        }
      })
      if (rows.length > 0) {
        fieldValue.value = rows
        handleChange(rows)
      }
    } catch (error) {
      console.warn('子表数据源加载失败:', error)
    }
  },
  { immediate: true }
)

function parseSnapshot(document) {
  if (document && typeof document === 'object') return document
  if (!document || typeof document !== 'string') {
    throw new Error('子表单发布快照为空')
  }
  try {
    return JSON.parse(document)
  } catch {
    throw new Error('子表单发布快照格式不正确')
  }
}

function resolveSnapshotFields(snapshot) {
  if (Array.isArray(snapshot?.legacyFields)) {
    return snapshot.legacyFields
  }
  if (!Array.isArray(snapshot?.nodes)) {
    return []
  }
  return snapshot.nodes
    .filter(node =>
      ['FIELD', 'SUB_FORM', 'REPEATER'].includes(
        String(node?.nodeType || '').toUpperCase()
      )
    )
    .map(node => {
      const props = parseDocument(node.propsDocument || node.props)
      const rules = parseDocument(node.rulesDocument || node.rules)
      return {
        id: node.id,
        fieldId: props.fieldId,
        fieldCode: props.fieldCode || node.nodeKey,
        fieldName: props.fieldName || props.label || node.nodeKey,
        fieldLabel: props.label || props.fieldName || node.nodeKey,
        fieldType: props.fieldType || node.nodeType,
        componentType:
          props.componentType
          || (node.nodeType === 'REPEATER' ? 'sub_form_list' : node.nodeType),
        placeholder: props.placeholder,
        defaultValue: props.defaultValue,
        gridSpan: props.gridSpan || 24,
        isRequired: props.required === true ? 1 : 0,
        isReadonly: props.readonly === true ? 1 : 0,
        isHidden: props.hidden === true ? 1 : 0,
        componentProps: JSON.stringify(props.componentProps || {}),
        validationRules: JSON.stringify(rules.validation || rules || {}),
        dataSourceBindingsDocument: node.dataSourceBindingsDocument
      }
    })
}

function parseDocument(value) {
  if (value && typeof value === 'object') return value
  if (!value || typeof value !== 'string') return {}
  try {
    return JSON.parse(value)
  } catch {
    return {}
  }
}

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
      gridSpan: f.gridSpan || f.grid_span || 24,
      refEntityId: f.childEntityId || f.refEntityId,
      childEntityId: f.childEntityId || f.refEntityId,
      refFieldCode: f.childRefFieldCode || f.refFieldCode,
      childRefFieldCode: f.childRefFieldCode || f.refFieldCode,
      relationType: f.relationType,
      relation: f.relation
    }))
}

function normalizeExternalNodes(nodes) {
  if (!Array.isArray(nodes)) return []
  return nodes
    .filter(node => node && node.id)
    .map(node => ({
      ...node,
      parentId: node.parentId || '',
      nodeType: String(node.nodeType || 'FIELD').toUpperCase()
    }))
}

const inlineFormNodes = computed(() =>
  Array.isArray(props.field?.runtimeNodes)
    ? props.field.runtimeNodes
    : []
)

const runtimeNodes = computed(() =>
  externalFormNodes.value.length > 0
    ? externalFormNodes.value
    : inlineFormNodes.value
)

const runtimeRootParentId = computed(() =>
  externalFormNodes.value.length > 0
    ? ''
    : (props.field?.runtimeRootParentId || '')
)

const runtimeFields = computed(() => {
  const source = externalFormNodes.value.length > 0
    ? externalFormFields.value
    : (props.field?.runtimeFields || [])
  return deriveNodeRuntimeFields(
    runtimeNodes.value,
    source
  )
})

const hasNodeTree = computed(() => runtimeNodes.value.length > 0)

watch(
  [
    () => fieldValue.value,
    childFormDefinition,
    childReleaseIdentity,
    runtimeNodes,
    runtimeFields,
    () => props.dataSourceRuntime
  ],
  () => {
    initializeChildRows()
  },
  { deep: true, flush: 'post' }
)

async function initializeChildRows() {
  const runtime = props.dataSourceRuntime
  const childForm = childFormDefinition.value
  const rows = fieldValue.value
  if (!runtime?.initialize || !childForm || !Array.isArray(rows)) return

  const sequence = ++childInitializationSequence
  const parentRecordId = props.context?.recordId || props.context?.id || 'new'
  const fieldCode = props.field?.fieldCode || props.field?.fieldKey || props.field?.id || 'subform'
  try {
    await Promise.all(rows.map(async (row, index) => {
      if (!row || typeof row !== 'object') return
      await runtime.initialize({
        form: childForm,
        fields: runtimeFields.value,
        nodes: runtimeNodes.value,
        record: row,
        recordId: `${parentRecordId}:${fieldCode}:${index}`,
        initializationKey: [
          'nested',
          childReleaseIdentity.value || childForm.id || 'form',
          parentRecordId,
          fieldCode,
          index
        ].join(':')
      })
    }))
    if (sequence === childInitializationSequence) {
      handleChange(rows)
    }
  } catch (error) {
    if (sequence === childInitializationSequence) {
      console.warn('子表单数据源初始化失败:', error)
    }
  }
}

function deriveNodeRuntimeFields(nodes, sourceFields) {
  const byReference = Array.isArray(sourceFields) ? sourceFields : []
  return nodes
    .filter(node => ['FIELD', 'SUB_FORM', 'REPEATER'].includes(
      String(node?.nodeType || '').toUpperCase()
    ))
    .map(node => {
      const props = parseDocument(node.propsDocument || node.props)
      const reference = node.bindingRef || props.fieldCode || props.fieldId
      const source = byReference.find(field =>
        String(field?.id) === String(node.id)
          || String(field?.fieldId) === String(reference)
          || field?.fieldCode === reference
      ) || {}
      const subFormConfig = props.componentProps?.subFormConfig
        || props.subFormConfig
        || {}
      const repeater = String(node.nodeType).toUpperCase() === 'REPEATER'
      return {
        ...source,
        id: node.id,
        fieldId: props.fieldId ?? source.fieldId,
        fieldKey: props.fieldCode || source.fieldCode || node.nodeKey,
        fieldCode: props.fieldCode || source.fieldCode || node.nodeKey,
        fieldName: props.fieldName || props.label || source.fieldName || node.nodeKey,
        fieldLabel: props.label || source.fieldLabel || source.fieldName || node.nodeKey,
        fieldType: props.fieldType || source.fieldType || (repeater ? 'SUB_FORM_LIST' : node.nodeType),
        componentType: props.componentType || source.componentType || (repeater ? 'sub_form_list' : String(node.nodeType).toLowerCase()),
        componentProps: props.componentProps || source.componentProps,
        dataSourceBindings: props.dataSourceBindings || source.dataSourceBindings,
        dataSourceBindingsDocument: node.dataSourceBindingsDocument
          || source.dataSourceBindingsDocument,
        defaultValue: props.defaultValue ?? source.defaultValue,
        isRequired: props.required === true ? 1 : (source.isRequired || 0),
        isReadonly: props.readonly === true ? 1 : (source.isReadonly || 0),
        relationType: props.relationType || source.relationType || subFormConfig.relationType,
        childEntityId: props.childEntityId || source.childEntityId || subFormConfig.childEntityId || subFormConfig.refEntityId,
        refEntityId: props.refEntityId || source.refEntityId || subFormConfig.refEntityId,
        childRefFieldCode: props.childRefFieldCode || source.childRefFieldCode || subFormConfig.childRefFieldCode || subFormConfig.refFieldCode,
        childFormId: props.childFormId || props.refFormId || props.publishedFormId || source.childFormId || subFormConfig.childFormId || subFormConfig.refFormId || subFormConfig.publishedFormId,
        childFormReleaseId: props.childFormReleaseId || props.refFormReleaseId || props.publishedFormReleaseId || source.childFormReleaseId || subFormConfig.childFormReleaseId || subFormConfig.refFormReleaseId || subFormConfig.publishedFormReleaseId,
        childFormReleaseVersion: props.childFormReleaseVersion
          ?? props.refFormReleaseVersion
          ?? props.publishedFormReleaseVersion
          ?? source.childFormReleaseVersion
          ?? subFormConfig.childFormReleaseVersion
          ?? subFormConfig.refFormReleaseVersion
          ?? subFormConfig.publishedFormReleaseVersion
      }
    })
}

function replaceNestedRow(row, value) {
  if (!row || !value || row === value) return
  Object.keys(row).forEach(key => {
    if (!(key in value)) delete row[key]
  })
  Object.assign(row, value)
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
    fields: hasNodeTree.value ? runtimeFields.value : fields,
    showSummary: field?.showSummary || false,
    summaryFields: field?.summaryFields || [],
    layout: hasNodeTree.value ? 'form' : layout,
    layoutType: refFormLayoutType.value,
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
