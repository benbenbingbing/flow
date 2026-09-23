<template>
  <div ref="root" class="mobile-form-renderer">
    <div v-if="loading" class="form-loading"><VanLoading size="18" />正在加载表单</div>
    <p v-if="loadError || modelError || submissionError" class="form-message" role="alert">{{ loadError || modelError || submissionError }}<button v-if="loadError" @click="initialize">重试</button></p>
    <MobileFormPages :tabbed="tabbed" :active-tab="activeTab" :pages="pageLayout.pages" @update:active-tab="$emit('update:activeTab', $event)">
      <template #default="{ page }">
        <component v-if="customForm?.readonly" ref="customFormRef" :is="customForm.component" :form="form" :fields="fields" :entity-fields="entityFields" :model-value="record" :readonly="readonly || !customForm.editable" :mode="mode" :context="runtimeContext" :config="formConfig.customComponentProps || {}" :data-source-runtime="dataSourceRuntime" :services="services" :linkage-state="linkage" :entity-code="context.entityCode" :form-action-slots="formActionSlots" @update:model-value="replaceRecord" @form-action="$emit('action', $event)" />
        <p v-else-if="form.customComponent" class="form-message" role="alert">此业务表单暂不支持手机展示，请在电脑端处理。</p>
        <MobileFormSections v-else :ref="value => setSectionsRef(page.name, value)" v-model:expanded="expanded" :items="page.items" :record="record" :runtime-options="runtimeOptions" :linkage="linkage" :context="runtimeContext" :services="services" :data-source-runtime="dataSourceRuntime" :errors="errors" :actions="actions" :action-loading-key="actionLoadingKey" @update-field="setField" @field-change="fieldChange" @field-blur="fieldBlur" @action="$emit('action', $event)" />
        <MobileRelatedContent v-for="composition in page.relatedContents" :key="composition.compositionKey" :composition="composition" :form="form" :context="runtimeContext" :services="services" :data-source-runtime="dataSourceRuntime" />
      </template>
      <template #after-tabs><slot name="after-tabs" /></template>
    </MobileFormPages>
  </div>
</template>
<script setup>
import { computed, nextTick, onBeforeUnmount, provide, ref, watch } from 'vue'
import { Loading as VanLoading } from 'vant'
import { getFieldKey, applyRuntimeFieldDefaults } from '@flow/workflow-core/form-runtime'
import { projectRuntimeFields, settleRuntimeLinkages, runtimeFieldState, validateRuntimeFields } from '@flow/workflow-core/form-runtime/formModel'
import { createFormUniquePrecheckController, resolveFormUniqueRuntimeIdentity, resolveFormFieldUniqueness } from '@flow/workflow-core/form-field-uniqueness'
import { useFormCrossFieldValidation } from '@flow/workflow-core/vue/useFormCrossFieldValidation'
import { useFormCustomValidation } from '@flow/workflow-core/vue/useFormCustomValidation'
import { resolveFormUniqueValidationTrigger, createFormUniquePrecheckRuntime } from '@flow/workflow-core/form-runtime/uniquePrecheckContext'
import { isEntitySelectionEventField } from '@flow/workflow-core/field-event-capabilities'
import { FIELD_SCRIPT_CONTEXT } from '@flow/workflow-core/browser/field-event-scripts'
import { safeParseConfig } from '@flow/workflow-core/config-runtime'
import { createCustomFormActionSlotContract } from '@flow/workflow-core/form-actions'
import { buildMobileFormTree, buildMobileFormPages, groupsContainingField } from './mobileFormTree.js'
import { getMobileExtension, mobileFieldCapability } from '../fields/registry.js'
import MobileFormSections from './MobileFormSections.vue'
import MobileFormPages from './MobileFormPages.vue'
import MobileRelatedContent from './MobileRelatedContent.vue'
const props = defineProps({ tabbed: Boolean, activeTab: { type: String, default: 'basic' }, form: { type: Object, required: true }, modelValue: { type: Object, default: () => ({}) }, entityFields: { type: Array, default: () => [] }, readonly: Boolean, mode: { type: String, default: 'view' }, context: { type: Object, default: () => ({}) }, services: { type: Object, default: () => ({}) }, dataSourceRuntime: { type: Object, default: null }, rootParentId: { type: [String, Number], default: '' }, actions: { type: Array, default: () => [] }, actionLoadingKey: String })
const emit = defineEmits(['update:modelValue', 'update:activeTab', 'action', 'error'])
const record = ref(props.modelValue), expanded = ref([]), baseErrors = ref({}), uniqueErrors = ref({}), loading = ref(false), loadError = ref(''), modelError = ref('')
const root = ref(), customFormRef = ref(), sectionRefs = new Map()
let generation = 0
const eventRevisions = new Map()
const projection = computed(() => projectRuntimeFields(props.form, props.entityFields, { rootParentId: props.rootParentId, hasComponent: () => true }))
const fields = computed(() => projection.value.fields)
const runtimeOptions = computed(() => ({ form: props.form, record: record.value, mode: props.mode, readonly: props.readonly, context: props.context, rootParentId: props.rootParentId }))
const linkage = ref({ visibility: {}, disabled: {}, required: {}, options: {}, attachmentItemRequired: {} })
const tree = computed(() => buildMobileFormTree(props.form, fields.value, runtimeOptions.value, linkage.value))
const formConfig = computed(() => safeParseConfig(props.form.viewConfig))
const relatedContents = computed(() => (props.form.viewCompositions || []).filter(item => {
  if (item.config?.enabled === false) return false
  if (String(item.anchorType).toUpperCase() !== 'FORM_NODE') return true
  const node = projection.value.nodes.find(node => [node.id, node.nodeKey].map(String).includes(String(item.anchorKey)))
  return node && runtimeFieldState({ id: node.id, fieldCode: node.nodeKey }, runtimeOptions.value, linkage.value).visible
}).sort((left, right) => Number(left.orderKey || 0) - Number(right.orderKey || 0)))
const pageLayout = computed(() => buildMobileFormPages(props.form, tree.value, relatedContents.value, props.tabbed && !props.rootParentId))
function setSectionsRef(name, value) { if (value) sectionRefs.set(name, value); else sectionRefs.delete(name) }
const customForm = computed(() => getMobileExtension('FORM', props.form.customComponent, props.form.customComponentVersion || 1))
const formActionSlots = computed(() => createCustomFormActionSlotContract(props.actions, action => emit('action', action)))
const options = { getForm: () => props.form, getRecord: () => record.value, getEntityFields: () => props.entityFields, getMode: () => props.mode, getReadonly: () => props.readonly, getContext: () => props.context, getEntityCode: () => props.context.entityCode, getRootParentId: () => props.rootParentId }
const cross = useFormCrossFieldValidation(options), custom = useFormCustomValidation(options)
const submissionError = computed(() => custom.submissionError.value)
const errors = computed(() => ({ ...baseErrors.value, ...uniqueErrors.value, ...Object.fromEntries(Object.entries(cross.errors.value).map(([key, value]) => [key, value.message])), ...Object.fromEntries(Object.entries(custom.errors.value).map(([key, value]) => [key, value.message || value])) }))
const unique = createFormUniquePrecheckController({
  getIdentity: () => resolveFormUniqueRuntimeIdentity(props.form, props.context),
  request: (...args) => { if (!props.services.precheckUnique) throw new Error('未提供唯一性校验服务'); return props.services.precheckUnique(...args) },
  onErrorsChange: value => { uniqueErrors.value = value }
})
const uniqueRuntime = createFormUniquePrecheckRuntime({ controller: unique, getFields: () => fields.value, getRecord: () => record.value, getErrors: () => uniqueErrors.value })
const runtimeContext = computed(() => ({ ...props.context, form: props.form, mode: props.mode, readonly: props.readonly, record: record.value, getFormData: () => record.value, setFormFieldValue: (code, value) => setField({ fieldCode: code }, value), scriptFields: fields.value, formCustomValidation: custom.runtime, formUniqueness: uniqueRuntime, onFieldChange: fieldChange, onFieldBlur: fieldBlur, revealField: reveal }))
provide(FIELD_SCRIPT_CONTEXT, {
  getFieldValue: code => record.value[code],
  setFieldValue(code, value) {
    if (['__proto__', 'constructor', 'prototype'].includes(code) || !fields.value.some(field => getFieldKey(field) === code)) throw new Error('当前表单不存在该字段')
    setField({ fieldCode: code }, value)
  },
  reportError(message) { emit('error', message) }
})
watch(() => props.modelValue, value => { if (record.value !== value) record.value = value || {} })
watch(() => [record.value, fields.value], () => {
  try {
    const settled = settleRuntimeLinkages(fields.value, record.value)
    linkage.value = settled.linkage; modelError.value = ''
    if (JSON.stringify(settled.record) !== JSON.stringify(record.value)) record.value = settled.record
    unique.handleRecordChange(fields.value, record.value)
    emit('update:modelValue', record.value)
  } catch (cause) { modelError.value = cause.message }
}, { deep: true, immediate: true })
function defaultGroups(items) { return items.flatMap(item => item.children ? [...(item.defaultExpanded ? [item.id] : []), ...defaultGroups(item.children)] : []) }
watch(() => [props.form.id, props.form.runtimeReleaseId || props.form.formReleaseId, props.context.initializationKey], () => {
  expanded.value = defaultGroups(tree.value); baseErrors.value = {}; unique.reset(record.value); initialize()
  if (props.tabbed) emit('update:activeTab', pageLayout.value.defaultActiveTabName)
}, { immediate: true })
async function initialize() {
  const version = ++generation; loading.value = true; loadError.value = ''
  try {
    if (!props.readonly && props.mode !== 'view') applyRuntimeFieldDefaults(record.value, props.form, props.entityFields)
    await props.dataSourceRuntime?.initialize({ form: props.form, fields: fields.value, nodes: projection.value.nodes, record: record.value, recordId: props.context.recordId || record.value.id, initializationKey: props.context.initializationKey, runtimeContext: props.context })
  } catch (cause) { if (version === generation) loadError.value = cause.message || '表单初始化失败' }
  finally { if (version === generation) loading.value = false }
}
onBeforeUnmount(() => { generation++ })
function setField(field, value) {
  const code = getFieldKey(field)
  if (!code || ['__proto__', 'constructor', 'prototype'].includes(code)) return
  const before = record.value, after = { ...before, [code]: value }
  cross.touchChanged(before, after); custom.touchChanged(before, after)
  delete baseErrors.value[code]; record.value = after; emit('update:modelValue', after)
}
/** 自定义表单可能产出路由标记等非可视字段，完整保留模型并触发同一校验链。 */
function replaceRecord(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return
  for (const [code, next] of Object.entries(value)) {
    if (['__proto__', 'constructor', 'prototype'].includes(code) || JSON.stringify(record.value[code]) === JSON.stringify(next)) continue
    setField({ fieldCode: code }, next)
  }
}
async function fieldChange(field, value) {
  try {
    const code = getFieldKey(field), revision = (eventRevisions.get(code) || 0) + 1
    eventRevisions.set(code, revision)
    const version = generation, isCurrent = () => version === generation && revision === eventRevisions.get(code)
    await props.services.fieldEvent?.('FIELD_CHANGE', field, value, runtimeContext.value, isCurrent)
    if (!isCurrent()) return
    if (isEntitySelectionEventField(field)) await props.services.fieldEvent?.('ENTITY_SELECTED', field, value, runtimeContext.value, isCurrent)
    if (!isCurrent()) return
    if (resolveFormUniqueValidationTrigger(field) === 'change') await unique.check(field, record.value, { reason: 'BLUR' })
    await custom.runtime.checkField(field, 'CHANGE')
  } catch (cause) { baseErrors.value[getFieldKey(field)] = cause.message }
}
async function fieldBlur(field) {
  await custom.runtime.onFieldBlur(field)
  if (resolveFormFieldUniqueness(field)?.precheck?.enabled) await unique.check(field, record.value, { reason: 'BLUR' })
}
function unsupported(items = tree.value) {
  return items.flatMap(item => {
    if (item.kind === 'field') {
      const capability = mobileFieldCapability(item.field, item.extensionName), state = runtimeFieldState(item.field, runtimeOptions.value, linkage.value)
      return state.editable && (!capability.editable || !capability.validate) ? [item.field.fieldCode] : []
    }
    if (item.kind === 'extension') {
      const capability = getMobileExtension('NODE', item.node.componentName, item.node.componentVersion || 1)
      const state = runtimeFieldState({ id: item.node.id, fieldCode: item.node.nodeKey }, runtimeOptions.value, linkage.value)
      if (state.editable && (!capability?.editable || !capability?.validate)) return [item.id]
    }
    return item.children ? unsupported(item.children) : []
  })
}
/** 本地规则、跨字段、唯一预检和自定义规则都基于完整模型，不依赖当前展开的分组。 */
async function validate() {
  if (loading.value || loadError.value || modelError.value) return false
  // 关联独立写操作仍在 PC 完成；如果发布配置要求随宿主保存，不能遗漏后继续提交。
  if (relatedContents.value.some(item => Array.isArray(item.config?.actions) && item.config.actions.includes('SAVE_WITH_FORM'))) { loadError.value = '此表单包含需要一并保存的关联内容，请在电脑端办理'; return false }
  if (props.form.customComponent && (!customForm.value || !customForm.value.editable || !customForm.value.validate)) { loadError.value = '该业务表单暂不支持手机办理，请在电脑端处理'; return false }
  const unsupportedFields = unsupported()
  if (unsupportedFields.length) { baseErrors.value = { [unsupportedFields[0]]: '此项暂不支持手机办理，请在电脑端处理' }; await reveal(unsupportedFields[0]); return false }
  const applicable = fields.value.filter(field => { const state = runtimeFieldState(field, runtimeOptions.value, linkage.value); return state.visible && state.editable })
  baseErrors.value = await validateRuntimeFields(fields.value, record.value, runtimeOptions.value, linkage.value)
  const crossResult = await cross.validate(), uniqueResult = await unique.checkAll(applicable, () => record.value), customResult = await custom.validate()
  let componentResult = { valid: true }
  if (customForm.value) componentResult.valid = (await customFormRef.value?.validate?.()) !== false
  else for (const page of pageLayout.value.pages) {
    componentResult = await sectionRefs.get(page.name)?.validate() || { valid: true }
    if (!componentResult.valid) break
  }
  if (!componentResult.valid && componentResult.fieldCode) baseErrors.value[componentResult.fieldCode] = componentResult.message || '请检查该字段'
  const valid = !Object.keys(errors.value).length && crossResult.valid && uniqueResult.valid && customResult.valid && componentResult.valid
  if (!valid) await reveal(Object.keys(errors.value)[0] || componentResult.fieldCode)
  return valid
}
async function reveal(code) {
  const groups = code ? groupsContainingField(tree.value, code) : []
  // 第一层页签切换到错误所在页，内部折叠分组仍按原方式展开；所有校验继续覆盖完整表单。
  if (props.tabbed) {
    const pages = pageLayout.value.pages
    const target = code ? pages.find(page => groups.includes(page.id)) || pages.find(page => page.name === 'basic') : pages.find(page => page.name === props.activeTab)
    emit('update:activeTab', target?.name || pageLayout.value.defaultActiveTabName)
  }
  expanded.value = [...new Set([...expanded.value, ...groups])]
  await nextTick()
  if (!code) return
  const element = [...(root.value?.querySelectorAll('[data-field-code]') || [])].find(item => item.dataset.fieldCode === String(code))
  element?.scrollIntoView({ block: 'center', behavior: 'smooth' })
}
/** 服务端字段错误保留当前草稿，通过与本地校验相同的分组定位展示。 */
async function applyServerValidationError(cause) {
  const incoming = cause?.currentData?.fieldErrors || cause?.source?.currentData?.fieldErrors || []
  if (!Array.isArray(incoming) || !incoming.length) return false
  const result = cross.applyServerErrors(incoming)
  for (const error of incoming) {
    const code = error.fieldCode || error.fieldPath
    if (code && !['__proto__', 'constructor', 'prototype'].includes(code)) baseErrors.value[code] = error.message || '字段校验失败'
  }
  await reveal(result.errors?.[0]?.fieldCode || incoming[0].fieldCode || incoming[0].fieldPath)
  return true
}
defineExpose({ validate, reveal, applyServerValidationError, getValidationError: () => loadError.value || modelError.value || submissionError.value || Object.values(errors.value)[0] || '', getRecord: () => record.value })
</script>
<style scoped>
.mobile-form-renderer { font-size: 14px; }.form-message { color: #b04432; padding: 12px; border-radius: 8px; background: #fff3ec; line-height: 1.6; }.form-message button { border: 0; background: none; color: inherit; min-height: 44px; text-decoration: underline; }.form-loading { display: flex; align-items: center; justify-content: center; gap: 8px; padding: 16px; color: var(--flow-mobile-muted); font-size: 13px; }
</style>
