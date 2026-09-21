<template>
  <template v-for="item in items" :key="item.id">
    <VanCollapse class="mobile-form-group" v-if="item.kind === 'group'" :model-value="expanded.includes(item.id) ? [item.id] : []" @update:model-value="value => toggle(item.id, value.length > 0)">
      <VanCollapseItem :name="item.id" :title="item.title" :lazy-render="false"><MobileFormSections :ref="value => setRef(item.id, value)" v-bind="childProps" :items="item.children" @update:expanded="$emit('update:expanded', $event)" @field-change="(field, value) => $emit('field-change', field, value)" @field-blur="$emit('field-blur', $event)" @update-field="(field, value) => $emit('update-field', field, value)" @action="$emit('action', $event)" /></VanCollapseItem>
    </VanCollapse>
    <div v-else-if="item.kind === 'field'" :data-field-code="item.field.fieldCode" class="mobile-field-container" :class="{ 'has-error': errorFor(item.field) }">
      <MobileFieldRenderer :ref="value => setRef(item.id, value)" :field="item.field" :model-value="record[item.field.fieldCode]" :disabled="!stateFor(item.field).editable" :required="stateFor(item.field).required" :extension-name="item.extensionName" :options="linkage.options?.[item.field.fieldCode] ?? null" :context="context" :services="services" :data-source-runtime="dataSourceRuntime" :attachment-item-required-state="stateFor(item.field).attachmentItemRequiredState" @update:model-value="value => $emit('update-field', item.field, value)" @change="value => $emit('field-change', item.field, value)" @blur="$emit('field-blur', item.field)" />
      <p v-if="errorFor(item.field)" class="mobile-field-error" role="alert">{{ errorFor(item.field) }}</p>
    </div>
    <p v-else-if="item.kind === 'text'" class="mobile-form-text" :class="{ title: item.title }">{{ item.text }}</p>
    <div v-else-if="item.kind === 'actions'" class="mobile-slot-actions"><VanButton v-for="action in slotActions(item.slotKey)" :key="action.runtimeKey || action.key" size="small" :disabled="action.enabled === false || Boolean(actionLoadingKey)" :loading="actionLoadingKey === (action.runtimeKey || action.key)" @click="$emit('action', action)">{{ action.label }}</VanButton></div>
    <component v-else-if="nodeExtension(item)?.readonly" :ref="value => setRef(item.id, value)" :is="nodeExtension(item).component" :node="item.node" :config="item.node.props" :model-value="record" :readonly="!stateFor({ id: item.node.id, fieldCode: item.node.nodeKey }).editable || !nodeExtension(item).editable" :mode="runtimeOptions.mode" :context="context" :data-source-runtime="dataSourceRuntime" @update:model-value="value => Object.entries(value).forEach(([fieldCode, next]) => $emit('update-field', { fieldCode }, next))" />
    <p v-else class="mobile-field-error" role="alert">此内容暂不支持手机展示，请在电脑端查看。</p>
  </template>
</template>
<script setup>
import { computed } from 'vue'
import { Collapse as VanCollapse, CollapseItem as VanCollapseItem, Button as VanButton } from 'vant'
import { runtimeFieldState } from '@flow/workflow-core/form-runtime/formModel'
import { slotFormActions } from '@flow/workflow-core/form-actions'
import MobileFieldRenderer from '../fields/MobileFieldRenderer.vue'
import { getMobileExtension } from '../fields/registry.js'
const props = defineProps({ items: Array, expanded: Array, record: Object, runtimeOptions: Object, linkage: Object, context: Object, services: Object, dataSourceRuntime: Object, errors: Object, actions: { type: Array, default: () => [] }, actionLoadingKey: String })
const emit = defineEmits(['update:expanded', 'update-field', 'field-change', 'field-blur', 'action'])
const childProps = computed(() => ({ ...props, items: undefined }))
const refs = new Map()
function setRef(key, value) { if (value) refs.set(key, value); else refs.delete(key) }
const stateFor = field => runtimeFieldState(field, props.runtimeOptions, props.linkage)
const errorFor = field => props.errors?.[field.fieldCode] || ''
const slotActions = key => slotFormActions(props.actions, key).filter(action => action.visible !== false)
const nodeExtension = item => getMobileExtension('NODE', item.node?.componentName, item.node?.componentVersion || 1)
function toggle(key, show) { emit('update:expanded', show ? [...new Set([...props.expanded, key])] : props.expanded.filter(value => value !== key)) }
/** 子表、上传和复杂扩展补充自身状态校验；所有折叠面板保持挂载。 */
async function validate() {
  for (const item of props.items || []) {
    const result = await refs.get(item.id)?.validate?.()
    if (result === false) return { valid: false, fieldCode: item.field?.fieldCode, message: '请检查该项内容或尚未完成的上传' }
    if (result?.valid === false) return result
  }
  return { valid: true }
}
defineExpose({ validate })
</script>
<style scoped>
.mobile-form-group { margin: 0 0 12px; border: 1px solid var(--flow-mobile-border); border-radius: 10px; overflow: hidden; }.mobile-form-group :deep(.van-collapse-item__title) { font-weight: 600; font-size: 14px; padding: 13px 14px; background: var(--flow-mobile-inset); }.mobile-form-group :deep(.van-collapse-item__content) { padding: 0 14px 4px; color: inherit; }.mobile-form-group :deep(.mobile-form-group) { margin-top: 12px; }.mobile-field-error { color: #bc4738; font-size: 12px; line-height: 1.6; margin: 4px 0 10px; }.has-error { border-left: 2px solid #bc4738; padding-left: 10px; }.mobile-form-text { font-size: 14px; line-height: 1.8; white-space: pre-wrap; }.mobile-form-text.title { font-size: 16px; font-weight: 600; }.mobile-slot-actions { display: flex; gap: 8px; flex-wrap: wrap; padding: 8px 0; }
</style>
