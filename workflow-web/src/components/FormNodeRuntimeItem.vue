<template>
  <component
    v-if="customDescriptor"
    :is="customDescriptor.component"
    :node="node"
    :model-value="modelValue"
    :readonly="readonly"
    :mode="mode"
    :context="context"
    :config="customConfig"
    :data-source-runtime="dataSourceRuntime"
    @update:model-value="$emit('update:modelValue', $event)"
  />

  <el-card v-else-if="node.nodeType === 'SECTION'" shadow="never" class="node-section">
    <template #header>{{ node.props.label || node.props.title || node.nodeKey }}</template>
    <FormNodeRuntimeItem
      v-for="child in children"
      :key="child.id"
      v-bind="childProps(child)"
      @update:model-value="$emit('update:modelValue', $event)"
    />
  </el-card>

  <el-row
    v-else-if="node.nodeType === 'GRID'"
    :gutter="Number(node.props.gutter || 16)"
    class="node-grid"
  >
    <el-col
      v-for="child in children"
      :key="child.id"
      :span="Number(child.props.span || node.props.defaultSpan || 12)"
    >
      <FormNodeRuntimeItem
        v-bind="childProps(child)"
        @update:model-value="$emit('update:modelValue', $event)"
      />
    </el-col>
  </el-row>

  <el-tabs
    v-else-if="node.nodeType === 'TAB_SET'"
    :model-value="activeTab"
    type="border-card"
    class="node-tabs"
    @update:model-value="activeTab = $event"
  >
    <el-tab-pane
      v-for="tabNode in children"
      :key="tabNode.id"
      :name="tabNode.id"
      :label="tabNode.props.label || tabNode.props.title || tabNode.nodeKey"
    >
      <FormNodeRuntimeItem
        v-for="child in childrenFor(tabNode.id)"
        :key="child.id"
        v-bind="childProps(child)"
        @update:model-value="$emit('update:modelValue', $event)"
      />
    </el-tab-pane>
  </el-tabs>

  <el-collapse v-else-if="node.nodeType === 'COLLAPSE'" class="node-collapse">
    <el-collapse-item
      :name="node.id"
      :title="node.props.label || node.props.title || node.nodeKey"
    >
      <FormNodeRuntimeItem
        v-for="child in children"
        :key="child.id"
        v-bind="childProps(child)"
        @update:model-value="$emit('update:modelValue', $event)"
      />
    </el-collapse-item>
  </el-collapse>

  <div v-else-if="node.nodeType === 'TEXT'" class="node-text">
    {{ node.props.text || node.props.content || '' }}
  </div>

  <el-form-item
    v-else-if="runtimeField"
    v-show="visible"
    :label="node.props.label || runtimeField.fieldLabel || runtimeField.fieldName"
    :prop="fieldKey"
    :rules="fieldRules"
    :required="required"
    class="node-field"
  >
    <FormFieldRendererLinkage
      :field="runtimeField"
      :model-value="modelValue[fieldKey]"
      :disabled="disabled"
      :options="options"
      :context="{ ...context, node, field: runtimeField }"
      :data-source-runtime="dataSourceRuntime"
      @update:model-value="updateField"
    />
  </el-form-item>

  <div v-else-if="node.nodeType === 'ACTION_SLOT'" class="node-action-slot">
    <component :is="actionSlotRenderer" />
  </div>

  <div v-else class="node-container">
    <FormNodeRuntimeItem
      v-for="child in children"
      :key="child.id"
      v-bind="childProps(child)"
      @update:model-value="$emit('update:modelValue', $event)"
    />
  </div>
</template>

<script setup>
import { computed, defineComponent, h, ref, watch } from 'vue'
import FormFieldRendererLinkage from '@/components/FormFieldRendererLinkage.vue'
import { buildRuntimeFieldRules, getFieldKey } from '@/shared/form-runtime'
import {
  getFieldModeAccess,
  safeParseConfig
} from '@/shared/config-runtime'
import {
  migrateFormNodeConfig,
  resolveFormNodeDescriptor
} from '@/utils/formNodeRegistry'

defineOptions({ name: 'FormNodeRuntimeItem' })

const props = defineProps({
  node: { type: Object, required: true },
  modelValue: { type: Object, default: () => ({}) },
  fields: { type: Array, default: () => [] },
  linkageState: { type: Object, default: () => ({}) },
  readonly: Boolean,
  mode: { type: String, default: 'view' },
  context: { type: Object, default: () => ({}) },
  dataSourceRuntime: { type: Object, default: null },
  childrenFor: { type: Function, required: true },
  actionSlots: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue'])
const children = computed(() => props.childrenFor(props.node.id))
const customDescriptor = computed(() => resolveFormNodeDescriptor(props.node))
const customConfig = computed(() =>
  migrateFormNodeConfig(props.node, customDescriptor.value)
)
const activeTab = ref('')
const actionSlotRenderer = computed(() => defineComponent({
  name: 'FormNodeActionSlotOutlet',
  setup() {
    return () => {
      const slot = props.actionSlots?.[`action-${props.node.nodeKey}`]
      return slot ? slot({ node: props.node }) : h('span')
    }
  }
}))

watch(children, value => {
  if (props.node.nodeType === 'TAB_SET'
      && value.length
      && !value.some(item => item.id === activeTab.value)) {
    activeTab.value = value[0].id
  }
}, { immediate: true })

const nestedNodes = computed(() => collectDescendants(props.node.id))

const runtimeField = computed(() => {
  if (!['FIELD', 'SUB_FORM', 'REPEATER'].includes(props.node.nodeType)) return null
  const nodeProps = props.node.props || {}
  const componentProps = nodeProps.componentProps || {}
  const ref = props.node.bindingRef || nodeProps.fieldCode || nodeProps.fieldId
  const linked = props.fields.find(field =>
    String(field.id) === String(ref)
      || String(field.fieldId) === String(ref)
      || field.fieldCode === ref
  )
  const subFormConfig = componentProps.subFormConfig || nodeProps.subFormConfig || {}
  const fallback = props.node.nodeType === 'REPEATER'
    ? {
        fieldType: 'SUB_FORM_LIST',
        componentType: 'sub_form_list'
      }
    : {
        fieldType: props.node.nodeType,
        componentType: 'sub_form'
      }
  return {
    ...(linked || {}),
    ...fallback,
    id: props.node.id,
    fieldId: nodeProps.fieldId ?? linked?.fieldId,
    fieldCode: nodeProps.fieldCode || linked?.fieldCode || props.node.nodeKey,
    fieldName: nodeProps.fieldName || linked?.fieldName || nodeProps.label || props.node.nodeKey,
    fieldLabel: nodeProps.label || linked?.fieldLabel || linked?.fieldName || props.node.nodeKey,
    fieldType: nodeProps.fieldType || linked?.fieldType || fallback.fieldType,
    componentType: nodeProps.componentType || linked?.componentType || fallback.componentType,
    placeholder: nodeProps.placeholder ?? linked?.placeholder,
    defaultValue: nodeProps.defaultValue ?? linked?.defaultValue,
    isRequired: nodeProps.required === true ? 1 : (linked?.isRequired || 0),
    isReadonly: nodeProps.readonly === true ? 1 : (linked?.isReadonly || 0),
    isHidden: nodeProps.hidden === true ? 1 : (linked?.isHidden || 0),
    componentProps: Object.keys(componentProps).length ? componentProps : linked?.componentProps,
    relationType: nodeProps.relationType
      || linked?.relationType
      || subFormConfig.relationType,
    childEntityId: nodeProps.childEntityId
      || linked?.childEntityId
      || subFormConfig.childEntityId
      || subFormConfig.refEntityId,
    refEntityId: nodeProps.refEntityId
      || linked?.refEntityId
      || subFormConfig.refEntityId,
    childRefFieldCode: nodeProps.childRefFieldCode
      || linked?.childRefFieldCode
      || subFormConfig.childRefFieldCode
      || subFormConfig.refFieldCode,
    childFormId: nodeProps.childFormId
      || nodeProps.refFormId
      || nodeProps.publishedFormId
      || linked?.childFormId
      || subFormConfig.childFormId
      || subFormConfig.refFormId
      || subFormConfig.publishedFormId,
    childFormReleaseId: nodeProps.childFormReleaseId
      || nodeProps.refFormReleaseId
      || nodeProps.publishedFormReleaseId
      || linked?.childFormReleaseId
      || subFormConfig.childFormReleaseId
      || subFormConfig.refFormReleaseId
      || subFormConfig.publishedFormReleaseId,
    childFormReleaseVersion: nodeProps.childFormReleaseVersion
      ?? nodeProps.refFormReleaseVersion
      ?? nodeProps.publishedFormReleaseVersion
      ?? linked?.childFormReleaseVersion
      ?? subFormConfig.childFormReleaseVersion
      ?? subFormConfig.refFormReleaseVersion
      ?? subFormConfig.publishedFormReleaseVersion,
    runtimeNodes: nestedNodes.value,
    runtimeRootParentId: props.node.id,
    runtimeFields: props.fields
  }
})

const fieldKey = computed(() => getFieldKey(runtimeField.value || props.node))
const modeAccess = computed(() => getFieldModeAccess(runtimeField.value, props.mode))
const visible = computed(() =>
  modeAccess.value.visible
    && props.linkageState?.visibility?.[fieldKey.value] !== false
)
const disabled = computed(() =>
  props.readonly
    || modeAccess.value.editable === false
    || props.linkageState?.disabled?.[fieldKey.value] === true
    || runtimeField.value?.isReadonly === true
    || runtimeField.value?.isReadonly === 1
)
const required = computed(() =>
  props.linkageState?.required?.[fieldKey.value]
    ?? runtimeField.value?.isRequired
    ?? false
)
const options = computed(() =>
  props.linkageState?.options?.[fieldKey.value]
    || runtimeField.value?.options
    || []
)
const fieldRules = computed(() => {
  const field = runtimeField.value
  if (!field) return []
  const rules = props.node.rules && Object.keys(props.node.rules).length
    ? { ...safeParseConfig(field.validationRules), ...props.node.rules }
    : safeParseConfig(field.validationRules)
  return buildRuntimeFieldRules(
    { ...field, validationRules: rules },
    required.value,
    props.node.props.label || field.fieldLabel || field.fieldName
  )
})

function updateField(value) {
  emit('update:modelValue', {
    ...(props.modelValue || {}),
    [fieldKey.value]: value
  })
}

function childProps(child) {
  return {
    node: child,
    modelValue: props.modelValue,
    fields: props.fields,
    linkageState: props.linkageState,
    readonly: props.readonly,
    mode: props.mode,
    context: props.context,
    dataSourceRuntime: props.dataSourceRuntime,
    childrenFor: props.childrenFor,
    actionSlots: props.actionSlots
  }
}

function collectDescendants(parentId) {
  const result = []
  props.childrenFor(parentId).forEach(child => {
    result.push(child)
    result.push(...collectDescendants(child.id))
  })
  return result
}
</script>

<style scoped>
.node-section,
.node-grid,
.node-tabs,
.node-collapse,
.node-text,
.node-container {
  margin-bottom: 12px;
}
.node-text {
  color: var(--el-text-color-regular);
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>
