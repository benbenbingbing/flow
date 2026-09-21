<template>
  <section class="mobile-subform">
    <header><strong>{{ field.fieldLabel || field.fieldName }}</strong><span>{{ rows.length }} 条</span></header>
    <p v-if="error" class="subform-error" role="alert">{{ error }}<button v-if="loadError" @click="loadDefinition">重试</button></p>
    <VanLoading v-if="loading" size="20" />
    <VanEmpty v-else-if="!rows.length" description="暂无明细" :image-size="48" />
    <div v-for="(row, index) in rows" :key="rowKey(row)" class="subform-row">
      <VanCell :title="rowTitle(row, index)" :label="rowSummary(row)" is-link @click="opened = rowKey(row)" />
      <button v-if="!disabled" class="remove-row" @click="removeRow(row)">删除</button>
      <!-- 关闭的行保留运行实例，提交校验覆盖所有明细，且不会丢失异步校验状态。 -->
      <VanPopup :show="opened === rowKey(row)" :lazy-render="false" position="bottom" round class="subform-editor" safe-area-inset-bottom @update:show="value => { if (!value) opened = '' }">
        <VanNavBar :title="rowTitle(row, index)" left-text="返回" @click-left="opened = ''" />
        <div class="subform-content"><MobileFormRenderer v-if="definition && !loading" :ref="value => setRef(rowKey(row), value)" :form="definition" :root-parent-id="rootParentId" :model-value="row" :readonly="disabled" :mode="disabled ? 'view' : context.mode || 'edit'" :context="rowContext(row, index)" :services="services" :data-source-runtime="rowRuntime(row)" @update:model-value="value => updateRow(row, value)" /></div>
      </VanPopup>
    </div>
    <VanButton v-if="!disabled && !loading && rows.length < maxRows" block plain type="primary" icon="plus" size="small" @click="addRow">添加明细</VanButton>
  </section>
</template>
<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch, toRaw } from 'vue'
import { Button as VanButton, Cell as VanCell, Empty as VanEmpty, Loading as VanLoading, NavBar as VanNavBar, Popup as VanPopup, showConfirmDialog } from 'vant'
import { formFieldProps } from '@flow/workflow-core/extensions/contracts/form-field'
import { safeParseConfig } from '@flow/workflow-core/config-runtime'
import { normalizeSubFormParameterContract, normalizeInputParameterSchema, resolveSubFormParameters, buildSubFormParentContext, applySubFormFieldInitialization, validateSubFormParameters } from '@flow/workflow-core/subform-parameter-contract'
import { projectRuntimeFields } from '@flow/workflow-core/form-runtime/formModel'
import { normalizeRuntimeFormRelease } from '@flow/workflow-core/form-runtime/release'
import MobileFormRenderer from './MobileFormRenderer.vue'
const props = defineProps({ ...formFieldProps, services: { type: Object, default: () => ({}) } })
const emit = defineEmits(['update:modelValue'])
const config = computed(() => safeParseConfig(props.field.componentProps).subFormConfig || {})
const meta = computed(() => {
  const field = props.field, cfg = config.value
  return {
    childFormId: field.childFormId || field.refFormId || field.publishedFormId || cfg.childFormId || cfg.refFormId || cfg.publishedFormId,
    childFormReleaseId: field.childFormReleaseId || field.refFormReleaseId || field.publishedFormReleaseId || cfg.childFormReleaseId || cfg.refFormReleaseId || cfg.publishedFormReleaseId,
    childFormReleaseVersion: field.childFormReleaseVersion ?? field.refFormReleaseVersion ?? field.publishedFormReleaseVersion ?? cfg.childFormReleaseVersion ?? cfg.refFormReleaseVersion ?? cfg.publishedFormReleaseVersion,
    refEntityId: field.childEntityId || field.refEntityId || cfg.refEntityId,
    childRefFieldCode: field.childRefFieldCode || field.refFieldCode || cfg.childRefFieldCode,
    relationType: field.relationType || field.relation?.type || cfg.relationType || 'ONE_TO_MANY'
  }
})
const one = computed(() => meta.value.relationType === 'ONE_TO_ONE' || props.field.repeatable === false || config.value.repeatable === false)
const rows = computed(() => Array.isArray(props.modelValue) ? props.modelValue : props.modelValue && typeof props.modelValue === 'object' ? [props.modelValue] : [])
const maxRows = computed(() => one.value ? 1 : Number(props.field.maxRows ?? config.value.maxRows ?? 100))
const minRows = computed(() => Number(props.field.minRows ?? config.value.minRows ?? 0))
const definition = ref(null), entityCode = ref(''), loading = ref(false), loadError = ref(''), error = ref(''), opened = ref(''), rootParentId = ref('')
const parent = computed(() => buildSubFormParentContext(props.context))
const contract = computed(() => normalizeSubFormParameterContract(config.value.parameterContract))
const schema = computed(() => normalizeInputParameterSchema(safeParseConfig(definition.value?.viewConfig).inputParameterSchema))
const parameters = computed(() => resolveSubFormParameters(contract.value, { parent: parent.value, context: props.context }, schema.value))
let sequence = 0, nextKey = 0
const keys = new WeakMap(), rowRefs = new Map(), runtimes = new Map()
function rowKey(row) { row = toRaw(row); if (!keys.has(row)) keys.set(row, row.id ? `id:${row.id}` : `draft:${++nextKey}`); return keys.get(row) }
function setRef(key, value) { if (value) rowRefs.set(key, value); else rowRefs.delete(key) }
function publish(value) { emit('update:modelValue', one.value ? value[0] || null : value) }
function updateRow(row, value) {
  if (row === value || JSON.stringify(row) === JSON.stringify(value)) return
  keys.set(toRaw(value), rowKey(row)); publish(rows.value.map(item => item === row ? value : item))
}
function rowTitle(row, index) { return row.dataName || row.name || `明细 ${index + 1}` }
function rowSummary(row) { return Object.entries(row).filter(([key, value]) => key !== 'id' && value !== '' && value != null && typeof value !== 'object').slice(0, 3).map(([, value]) => String(value)).join(' · ') || '查看明细' }
function rowContext(row, index) {
  return { ...props.context, form: definition.value, entityId: definition.value?.entityId || meta.value.refEntityId, entityCode: entityCode.value, recordId: row.id || null, record: { id: row.id || null, data: row }, parent: parent.value, params: parameters.value, parameterErrors: validateSubFormParameters(parameters.value, schema.value), row: { index, id: row.id || null, isNew: !row.id, data: row }, relation: meta.value, parentField: props.field, subFormRowIndex: index, initializationKey: `${props.context.initializationKey || parent.value.recordId}:${props.field.fieldCode}:${rowKey(row)}`, releaseResolutionToken: definition.value?.releaseResolutionToken || props.context.releaseResolutionToken }
}
function rowRuntime(row) {
  const key = rowKey(row)
  if (!props.dataSourceRuntime?.withContext) return props.dataSourceRuntime
  if (!runtimes.has(key)) runtimes.set(key, props.dataSourceRuntime.withContext(() => {
    const current = rows.value.find(item => rowKey(item) === key) || row, context = rowContext(current, rows.value.indexOf(current))
    return { ...context, record: current, context }
  }))
  return runtimes.get(key)
}
/** 子表发布坐标和实体身份独立解析，不能使用父表的身份或当前草稿。 */
async function loadDefinition() {
  const current = ++sequence; loading.value = true; error.value = ''; loadError.value = ''; definition.value = null; runtimes.clear()
  try {
    const value = meta.value
    let form, rootId = ''
    if (value.childFormId) {
      const release = await props.services.getFormRuntimeRelease(value.childFormId, value.childFormReleaseId, value.childFormReleaseVersion, props.context.releaseResolutionToken)
      form = normalizeRuntimeFormRelease(release, value.childFormId)
      form.entityId ||= value.refEntityId
      rootId = ''
    } else if (props.field.runtimeNodes?.length) {
      rootId = props.field.runtimeRootParentId || ''
      // 行作用域的根节点必须保留，公共权限/校验遍历才能识别作用域边界。
      // 只保留后代会将整行误判为作用域外，从而漏掉从未打开的必填字段。
      const rootNode = (props.context.form?.nodes || []).find(node => String(node.id) === String(rootId))
      const nodes = rootNode ? [rootNode, ...props.field.runtimeNodes] : props.field.runtimeNodes
      form = { ...props.context.form, customComponent: null, nodes, fields: props.field.runtimeFields || [] }
    } else if (value.refEntityId) {
      const fields = await props.services.getEntityFields(value.refEntityId)
      form = { id: `entity:${value.refEntityId}`, entityId: value.refEntityId, fields: fields.filter(field => !field.isSystem && field.fieldCode !== value.childRefFieldCode) }; rootId = ''
    } else {
      form = { ...props.context.form, customComponent: null, fields: props.field.runtimeFields || [], nodes: [] }; rootId = ''
    }
    const code = value.refEntityId ? await props.services.resolveEntityCode(value.refEntityId) : ''
    if (current !== sequence) return
    rootParentId.value = rootId; definition.value = form; entityCode.value = code
    if (!rows.value.length && props.dataSourceRuntime?.loadSubformRows) {
      const loaded = await props.dataSourceRuntime.loadSubformRows(props.field, { parent: parent.value, params: parameters.value, relation: value, context: { ...props.context, parent: parent.value, params: parameters.value, relation: value }, input: { fieldCode: props.field.fieldCode, relation: value } })
      if (current === sequence && loaded?.length && !rows.value.length) publish(loaded)
    }
  } catch (cause) { if (current === sequence) error.value = loadError.value = cause.message || '明细加载失败' }
  finally { if (current === sequence) loading.value = false }
}
watch(() => [meta.value.childFormId, meta.value.childFormReleaseId, meta.value.childFormReleaseVersion, meta.value.refEntityId, props.context.initializationKey], loadDefinition, { immediate: true })
watch(() => [rows.value, parameters.value], () => {
  if (props.disabled) return
  for (const row of rows.value) applySubFormFieldInitialization(row, contract.value, { parent: parent.value, context: props.context, params: parameters.value, row: { index: rows.value.indexOf(row), id: row.id, data: row } }, ['id', meta.value.childRefFieldCode])
}, { deep: true })
async function addRow() { const row = {}; publish([...rows.value, row]); await nextTick(); opened.value = rowKey(row) }
async function removeRow(row) {
  try { await showConfirmDialog({ title: '删除明细', message: '确认删除这条明细？' }) } catch { return }
  const key = rowKey(row); runtimes.delete(key); rowRefs.delete(key); publish(rows.value.filter(item => item !== row))
}
/** 全量校验包含从未打开的行；出错后直接进入对应明细面板。 */
async function validate() {
  if (props.disabled) return true
  if (loading.value || loadError.value || !definition.value) return false
  const parameterErrors = validateSubFormParameters(parameters.value, schema.value)
  error.value = parameterErrors[0]?.message || (rows.value.length < minRows.value ? `至少添加 ${minRows.value} 条明细` : rows.value.length > maxRows.value ? `最多允许 ${maxRows.value} 条明细` : '')
  if (error.value) return false
  if (rows.value.length && !projectRuntimeFields(definition.value, [], { rootParentId: rootParentId.value, hasComponent: () => true }).fields.length && !definition.value.customComponent) { error.value = '明细字段未正确加载，请重试'; return false }
  for (const row of rows.value) {
    const key = rowKey(row), renderer = rowRefs.get(key)
    if (!renderer || !(await renderer.validate())) { opened.value = key; error.value = renderer?.getValidationError() || '请检查明细内容'; return false }
  }
  return true
}
onBeforeUnmount(() => { sequence++ })
defineExpose({ validate })
</script>
<style scoped>
.mobile-subform { padding: 14px 0; }.mobile-subform header { display: flex; justify-content: space-between; padding: 0 0 12px; }.mobile-subform header span { color: var(--flow-mobile-muted); }.subform-row { border: 1px solid var(--flow-mobile-border); border-radius: 8px; overflow: hidden; margin-bottom: 10px; }.remove-row { border: 0; background: transparent; color: #ad4837; padding: 8px 16px; min-height: 44px; }.subform-editor { height: 90dvh; display: flex; flex-direction: column; }.subform-content { padding: 0 16px 24px; overflow: auto; flex: 1; }.subform-error { color: #ad4837; font-size: 13px; line-height: 1.6; }.subform-error button { border: 0; background: none; color: inherit; min-height: 44px; }
</style>
