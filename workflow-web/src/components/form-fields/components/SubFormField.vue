<template>
  <div class="sub-form-field">
    <SubFormRenderer
      ref="subFormRendererRef"
      :model-value="fieldValue"
      :config="subFormConfig"
      :readonly="isDisabled"
      :disabled="isDisabled"
      :external-field-error="resolveLegacyUniqueError"
      @update:model-value="handleSubFormUpdate"
      @field-change="handleLegacyFieldChange"
      @field-blur="handleLegacyFieldBlur"
    >
      <template v-if="hasNodeTree" #row="{ row, index }">
        <SubFormRowRuntime
          :ref="instance => setRowRuntimeRef(index, instance)"
          :form="childRuntimeForm"
          :nodes="runtimeNodes"
          :root-parent-id="runtimeRootParentId"
          :fields="runtimeFields"
          :row="row"
          :readonly="isDisabled"
          :mode="context.mode || (isDisabled ? 'view' : 'edit')"
          :context="childRenderContext(row, index)"
          :data-source-runtime="childDataSourceRuntime(row, index)"
          @update:model-value="replaceNestedRow(row, $event)"
        />
      </template>
    </SubFormRenderer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import SubFormRenderer from '@/components/SubFormRenderer.vue'
import SubFormRowRuntime from './SubFormRowRuntime.vue'
import { useFormField } from '../composables/useFormField.js'
import {
  getEntityFields,
  getFormRuntimeRelease,
  precheckFormFieldUnique
} from '@/api/entityForm'
import { safeParseConfig } from '@/shared/config-runtime'
import {
  createFormUniquePrecheckController,
  resolveFormFieldKey,
  resolveFormFieldUniqueness,
  resolveFormUniqueRuntimeIdentity
} from '@/shared/form-field-uniqueness'
import {
  resolveFormUniqueValidationTrigger
} from '@/shared/form-runtime/uniquePrecheckContext'
import {
  applySubFormFieldInitialization,
  buildSubFormParentContext,
  normalizeInputParameterSchema,
  normalizeSubFormParameterContract,
  resolveSubFormParameters,
  validateSubFormParameters
} from '@/shared/subform-parameter-contract'
import { areSubFormValuesEqual } from '@/shared/subform-value-sync'
import { resolveFormContainerAppearance } from '@/shared/form-container-appearance'

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
const subFormRendererRef = ref(null)
const rowRuntimeRefs = ref({})
const legacyUniqueErrors = ref({})
const legacyRowControllers = new Map()

function setRowRuntimeRef(index, instance) {
  if (instance) {
    rowRuntimeRefs.value[index] = instance
  } else {
    delete rowRuntimeRefs.value[index]
  }
}

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
let childRuntimeCache = new WeakMap()

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
          releaseVersion,
          props.context?.releaseResolutionToken
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
        childFormDefinition.value = {
          ...(snapshot?.form || {}),
          id: snapshot?.form?.id || formId,
          entityId: snapshot?.form?.entityId || refEntityId
        }
        childFormDefinition.value.runtimeReleaseId =
          release.id || releaseId || null
        childFormDefinition.value.runtimeReleaseVersion =
          release.version ?? releaseVersion ?? null
        childFormDefinition.value.effectiveReleaseId =
          release.effectiveReleaseId || release.id || releaseId || null
        childFormDefinition.value.hotfixApplied =
          release.hotfixApplied === true
        childFormDefinition.value.releaseResolutionToken =
          release.releaseResolutionToken || null
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
  () => props.dataSourceRuntime,
  () => {
    childRuntimeCache = new WeakMap()
  }
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
          || (node.nodeType === 'REPEATER' ? 'sub_form' : node.nodeType),
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
      validationRules: f.validationRules,
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
const childRuntimeForm = computed(() =>
  childFormDefinition.value || props.context?.form || {}
)
const parentContext = computed(() =>
  buildSubFormParentContext(props.context)
)
const parameterContract = computed(() =>
  normalizeSubFormParameterContract(
    parsedComponentProps.value.subFormConfig?.parameterContract
  )
)
const childInputParameterSchema = computed(() => {
  const viewConfig = safeParseConfig(
    childFormDefinition.value?.viewConfig
  )
  return normalizeInputParameterSchema(viewConfig.inputParameterSchema)
})
const parameterSource = computed(() => ({
  parent: parentContext.value,
  context: props.context
}))
const resolvedParameters = computed(() =>
  resolveSubFormParameters(
    parameterContract.value,
    parameterSource.value,
    childInputParameterSchema.value
  )
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
        parent: parentContext.value,
        params: resolvedParameters.value,
        relation: subFormMeta.value,
        context: {
          ...props.context,
          parent: parentContext.value,
          params: resolvedParameters.value,
          relation: subFormMeta.value
        },
        input: {
          fieldCode: props.field?.fieldCode,
          relation: subFormMeta.value
        }
      })
      if (rows.length > 0) {
        handleSubFormUpdate(rows)
      }
    } catch (error) {
      console.warn('子表数据源加载失败:', error)
    }
  },
  { immediate: true }
)

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
  const rows = relationRows(fieldValue.value)
  if (!runtime?.initialize || !childForm || rows.length === 0) return

  const sequence = ++childInitializationSequence
  const parentRecordId = parentContext.value.recordId || 'new'
  const fieldCode = props.field?.fieldCode || props.field?.fieldKey || props.field?.id || 'subform'
  try {
    await Promise.all(rows.map(async (row, index) => {
      if (!row || typeof row !== 'object') return
      const scopedRuntime = childDataSourceRuntime(row, index)
      await scopedRuntime.initialize({
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
  } catch (error) {
    if (sequence === childInitializationSequence) {
      console.warn('子表单数据源初始化失败:', error)
    }
  }
}

watch(
  [
    () => fieldValue.value,
    parentContext,
    parameterContract,
    resolvedParameters
  ],
  () => {
    applyConfiguredFieldInitializers()
  },
  { deep: true, flush: 'post', immediate: true }
)

function applyConfiguredFieldInitializers() {
  const rows = relationRows(fieldValue.value)
  if (rows.length === 0) return
  let changed = false
  rows.forEach((row, index) => {
    changed = applySubFormFieldInitialization(
      row,
      parameterContract.value,
      {
        ...parameterSource.value,
        params: resolvedParameters.value,
        row: rowRuntimeContext(row, index)
      },
      ['id', subFormMeta.value.childRefFieldCode]
    ) || changed
  })
  return changed
}

function relationRows(value) {
  if (Array.isArray(value)) return value
  return value && typeof value === 'object' ? [value] : []
}

function rowRuntimeContext(row, index) {
  return {
    index,
    id: row?.id || null,
    isNew: !row?.id,
    data: row
  }
}

function childRenderContext(row, index) {
  const rowContext = rowRuntimeContext(row, index)
  const parameterErrors = validateSubFormParameters(
    resolvedParameters.value,
    childInputParameterSchema.value
  )
  return {
    ...props.context,
    form: childFormDefinition.value || props.context?.form,
    entityId: childFormDefinition.value?.entityId
      || subFormMeta.value.refEntityId,
    recordId: row?.id || null,
    record: {
      id: row?.id || null,
      data: row
    },
    releaseResolutionToken:
      childFormDefinition.value?.releaseResolutionToken
        || props.context?.releaseResolutionToken,
    parent: parentContext.value,
    params: resolvedParameters.value,
    parameterErrors,
    row: rowContext,
    relation: subFormMeta.value,
    parentField: props.field,
    subFormRowIndex: index
  }
}

function childDataSourceRuntime(row, index) {
  const runtime = props.dataSourceRuntime
  if (!runtime?.withContext || !row || typeof row !== 'object') {
    return runtime
  }
  let scoped = childRuntimeCache.get(row)
  if (!scoped) {
    scoped = runtime.withContext(() => {
      const currentIndex = relationRows(fieldValue.value).indexOf(row)
      const context = childRenderContext(
        row,
        currentIndex >= 0 ? currentIndex : index
      )
      return {
        form: childFormDefinition.value,
        entityId: childFormDefinition.value?.entityId
          || subFormMeta.value.refEntityId,
        record: row,
        recordId: row?.id || `${parentContext.value.recordId || 'new'}:${index}`,
        parent: context.parent,
        params: context.params,
        row: context.row,
        relation: context.relation,
        context
      }
    })
    childRuntimeCache.set(row, scoped)
  }
  return scoped
}

function deriveNodeRuntimeFields(nodes, sourceFields) {
  const byReference = Array.isArray(sourceFields) ? sourceFields : []
  return nodes
    .filter(node => ['FIELD', 'SUB_FORM', 'REPEATER'].includes(
      String(node?.nodeType || '').toUpperCase()
    ))
    .map(node => {
      const props = parseDocument(node.propsDocument || node.props)
      const rules = parseDocument(node.rulesDocument || node.rules)
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
        fieldType: props.fieldType || source.fieldType || 'SUB_FORM',
        componentType: props.componentType || source.componentType || 'sub_form',
        componentProps: props.componentProps || source.componentProps,
        validationRules: JSON.stringify({
          ...parseDocument(source.validationRules),
          ...(rules.validation || rules || {})
        }),
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

function handleSubFormUpdate(value) {
  if (areSubFormValuesEqual(value, props.modelValue)) return
  handleChange(value)
}

/**
 * 子表单校验必须显式向父表冒泡。legacy fields-only 先用每行独立 controller
 * 强制刷新 SUBMIT 预检，再做常规校验；避免历史 BLUR 结果阻止新的
 * 权威预检。node/REPEATER 路径则由每个 SubFormRowRuntime 完成同样流程。
 */
async function validate() {
  if (!hasNodeTree.value) {
    syncLegacyRowControllers()
    const fields = legacyUniqueFields()
    const uniqueResults = await Promise.all(
      [...legacyRowControllers.values()].map(entry =>
        entry.controller.checkAll(fields, () => entry.row || {})
      )
    )
    if (uniqueResults.some(result => !result.valid)) return false
    return (await subFormRendererRef.value?.validate?.()) !== false
  }

  const structuralValid = (await subFormRendererRef.value?.validate?.()) !== false
  const rowResults = await Promise.all(
    Object.values(rowRuntimeRefs.value)
      .filter(Boolean)
      .map(instance => instance.validate?.())
  )
  return structuralValid && rowResults.every(result => result !== false)
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
  if ((type || '').toLowerCase() === 'sub_form') {
    return 'SUB_FORM'
  }
  return map[(type || '').toLowerCase()] || 'TEXT'
}

function mapComponentType(type) {
  const lower = (type || '').toLowerCase()
  if (lower === 'sub_form') return 'sub_form'
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
  const nodeType = String(field?.nodeType || 'SUB_FORM').toUpperCase()
  // 新版外观字段位于 componentProps 顶层；合并旧 subFormConfig 仅用于兼容
  // 已经将这两个开关写入子表单配置对象的历史草稿，且新版显式值优先。
  const configuredAppearance = {
    ...(parsedComponentProps.value.subFormConfig || {}),
    ...parsedComponentProps.value
  }
  const appearance = resolveFormContainerAppearance(
    nodeType,
    configuredAppearance
  )

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
    showHeaderTitle: false,
    fieldKey: field?.fieldCode || field?.fieldKey || 'detailList',
    required: field?.required || false,
    minRows: field?.minRows || 0,
    maxRows: field?.maxRows || 100,
    fields: hasNodeTree.value ? runtimeFields.value : fields,
    showSummary: field?.showSummary || false,
    summaryFields: field?.summaryFields || [],
    layout: hasNodeTree.value ? 'form' : layout,
    layoutType: refFormLayoutType.value,
    nodeType,
    showPadding: appearance.showPadding,
    showBorder: appearance.showBorder,
    repeatable,
    relationType: subFormMeta.value.relationType,
    childRefFieldCode: subFormMeta.value.childRefFieldCode
  }
})

function legacyUniqueFields() {
  if (hasNodeTree.value) return []
  return (subFormConfig.value.fields || []).filter(field =>
    resolveFormFieldUniqueness(field)?.precheck?.enabled === true
  )
}

function legacyControllerScopeKey(fields, row) {
  const form = childFormDefinition.value || {}
  return JSON.stringify({
    formId: form.id || '',
    releaseId: form.runtimeReleaseId || form.formReleaseId || '',
    releaseVersion:
      form.runtimeReleaseVersion ?? form.formReleaseVersion ?? null,
    releaseResolutionToken:
      form.releaseResolutionToken
      || props.context?.releaseResolutionToken
      || '',
    recordId: row?.id || '',
    rules: fields.map(field => ({
      fieldCode: resolveFormFieldKey(field),
      rule: resolveFormFieldUniqueness(field)
    }))
  })
}

function syncLegacyUniqueErrors() {
  const errors = {}
  legacyRowControllers.forEach((entry, index) => {
    Object.entries(entry.errors || {}).forEach(([fieldCode, message]) => {
      if (message) errors[`${index}:${fieldCode}`] = message
    })
  })
  legacyUniqueErrors.value = errors
}

/**
 * 为 legacy fields-only 的每行创建独立 headless controller。getIdentity 每次从
 * entry 读取当前行，保证防抖请求也携带子表发布身份和正确子记录。
 */
function createLegacyRowController(index, row) {
  const entry = {
    index,
    row,
    scopeKey: '',
    errors: {},
    controller: null
  }
  entry.controller = createFormUniquePrecheckController({
    request: precheckFormFieldUnique,
    getIdentity: () => {
      const currentRow = entry.row || {}
      const form = childFormDefinition.value || {}
      return resolveFormUniqueRuntimeIdentity(
        form,
        {
          ...childRenderContext(currentRow, entry.index),
          form,
          recordId: currentRow?.id || null,
          record: {
            id: currentRow?.id || null,
            data: currentRow
          }
        }
      )
    },
    onErrorsChange: errors => {
      entry.errors = errors
      syncLegacyUniqueErrors()
    }
  })
  return entry
}

function ensureLegacyRowController(index, row, fields) {
  let entry = legacyRowControllers.get(index)
  if (!entry) {
    entry = createLegacyRowController(index, row)
    legacyRowControllers.set(index, entry)
  }
  entry.index = index
  entry.row = row || {}
  const scopeKey = legacyControllerScopeKey(fields, entry.row)
  if (scopeKey !== entry.scopeKey) {
    entry.scopeKey = scopeKey
    entry.controller.reset(entry.row)
  }
  return entry
}

function clearLegacyRowControllers() {
  legacyRowControllers.forEach(entry => entry.controller.reset())
  legacyRowControllers.clear()
  legacyUniqueErrors.value = {}
}

/**
 * 同步子行数据到各自 controller。条件字段的程序回填不一定会触发
 * 字段 change 事件，因此仍需要深度监听来覆盖条件联动和外部更新。
 */
function syncLegacyRowControllers() {
  const fields = legacyUniqueFields()
  if (!fields.length) {
    clearLegacyRowControllers()
    return
  }
  const rows = relationRows(fieldValue.value)
  rows.forEach((row, index) => {
    const entry = ensureLegacyRowController(index, row, fields)
    entry.controller.handleRecordChange(fields, entry.row)
  })
  const staleIndexes = [...legacyRowControllers.keys()]
  staleIndexes.forEach(index => {
    if (index < rows.length) return
    legacyRowControllers.get(index)?.controller.reset()
    legacyRowControllers.delete(index)
  })
  syncLegacyUniqueErrors()
}

function resolveLegacyUniqueError(index, field) {
  if (hasNodeTree.value) return ''
  return legacyUniqueErrors.value[
    `${index}:${resolveFormFieldKey(field || {})}`
  ] || ''
}

function legacyControllerForEvent(payload) {
  if (hasNodeTree.value || !payload?.field) return null
  const fields = legacyUniqueFields()
  if (!fields.length) return null
  const index = Number(payload.index)
  if (!Number.isInteger(index) || index < 0) return null
  const entry = ensureLegacyRowController(index, payload.row || {}, fields)
  // 先更新整行快照，使 CHANGE 规则和条件字段监听共用同一次变更。
  entry.controller.handleRecordChange(fields, entry.row)
  return entry
}

function handleLegacyFieldChange(payload) {
  const entry = legacyControllerForEvent(payload)
  if (!entry
      || resolveFormUniqueValidationTrigger(payload.field) !== 'change') {
    return
  }
  // EntitySelector/Radio/Switch 等没有稳定 blur 的控件，用 change
  // 执行已配置的 BLUR 预检。
  return entry.controller.check(
    payload.field,
    entry.row,
    { reason: 'BLUR' }
  )
}

function handleLegacyFieldBlur(payload) {
  const entry = legacyControllerForEvent(payload)
  if (!entry) return
  return entry.controller.check(
    payload.field,
    entry.row,
    { reason: 'BLUR' }
  )
}

watch(
  [
    () => fieldValue.value,
    () => subFormConfig.value.fields,
    () => hasNodeTree.value,
    () => childFormDefinition.value?.id,
    () => childFormDefinition.value?.runtimeReleaseId,
    () => childFormDefinition.value?.runtimeReleaseVersion,
    () => childFormDefinition.value?.releaseResolutionToken,
    () => props.context?.releaseResolutionToken
  ],
  syncLegacyRowControllers,
  { deep: true, immediate: true, flush: 'post' }
)

onBeforeUnmount(clearLegacyRowControllers)

defineExpose({ validate })
</script>

<style scoped>
.sub-form-field {
  width: 100%;
}
</style>
