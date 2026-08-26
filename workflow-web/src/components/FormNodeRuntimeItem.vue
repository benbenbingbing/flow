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
    <el-row :gutter="childGutter" class="node-child-row">
      <el-col v-for="child in children" :key="child.id" :span="childSpan(child)">
        <FormNodeRuntimeItem
          v-bind="childProps(child)"
          @update:model-value="$emit('update:modelValue', $event)"
        />
      </el-col>
    </el-row>
  </el-card>

  <el-row
    v-else-if="node.nodeType === 'GRID'"
    :gutter="Number(node.props.gutter || 16)"
    class="node-grid"
  >
    <el-col
      v-for="child in children"
      :key="child.id"
      :span="Number(child.props.gridSpan || child.props.span || node.props.defaultSpan || 12)"
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
    :tab-position="node.props.tabPosition || 'top'"
    class="node-tabs"
    @update:model-value="activeTab = $event"
  >
    <el-tab-pane
      v-for="tabNode in visibleTabs"
      :key="tabNode.id"
      :name="tabNode.id"
      :label="tabNode.props.label || tabNode.props.title || tabNode.nodeKey"
    >
      <el-row :gutter="childGutter" class="node-child-row">
        <el-col
          v-for="child in childrenFor(tabNode.id)"
          :key="child.id"
          :span="childSpan(child)"
        >
          <FormNodeRuntimeItem
            v-bind="childProps(child)"
            @update:model-value="$emit('update:modelValue', $event)"
          />
        </el-col>
      </el-row>
      <RelatedContentRuntime
        v-for="item in relatedContentsForNode(tabNode)"
        :key="item.id || item.compositionKey"
        :composition="item"
        owner-type="FORM"
        :owner-id="runtimeForm.id"
        :release-id="runtimeReleaseId"
        :release-version="runtimeReleaseVersion"
        :source-record-id="sourceRecordId"
        :host-readonly="readonly"
        :traversal-context-token="runtimeTraversalContextToken"
        :release-resolution-token="runtimeReleaseResolutionToken"
        @source-patch="applyRelatedContentPatch"
      />
    </el-tab-pane>
  </el-tabs>

  <el-collapse
    v-else-if="node.nodeType === 'COLLAPSE'"
    :model-value="collapseModelValue"
    :accordion="node.props.accordion === true"
    class="node-collapse"
    @update:model-value="updateCollapseNames"
  >
    <el-collapse-item
      :name="node.id"
      :title="node.props.label || node.props.title || node.nodeKey"
    >
      <el-row :gutter="childGutter" class="node-child-row">
        <el-col v-for="child in children" :key="child.id" :span="childSpan(child)">
          <FormNodeRuntimeItem
            v-bind="childProps(child)"
            @update:model-value="$emit('update:modelValue', $event)"
          />
        </el-col>
      </el-row>
    </el-collapse-item>
  </el-collapse>

  <div
    v-else-if="node.nodeType === 'TEXT' && isSectionTitleText"
    class="node-section-title"
  >
    <SectionField :field="sectionTitleField" />
  </div>

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
      :attachment-item-required-state="attachmentItemRequiredState"
      @update:model-value="updateField"
    />
  </el-form-item>

  <div v-else-if="node.nodeType === 'ACTION_SLOT'" class="node-action-slot">
    <component :is="actionSlotRenderer" />
  </div>

  <div v-else class="node-container">
    <el-row :gutter="childGutter" class="node-child-row">
      <el-col v-for="child in children" :key="child.id" :span="childSpan(child)">
        <FormNodeRuntimeItem
          v-bind="childProps(child)"
          @update:model-value="$emit('update:modelValue', $event)"
        />
      </el-col>
    </el-row>
  </div>

  <RelatedContentRuntime
    v-for="item in relatedContentsForNode(node)"
    :key="item.id || item.compositionKey"
    :composition="item"
    owner-type="FORM"
    :owner-id="runtimeForm.id"
    :release-id="runtimeReleaseId"
    :release-version="runtimeReleaseVersion"
    :source-record-id="sourceRecordId"
    :host-readonly="readonly"
    :traversal-context-token="runtimeTraversalContextToken"
    :release-resolution-token="runtimeReleaseResolutionToken"
    @source-patch="applyRelatedContentPatch"
  />
</template>

<script setup>
import { computed, defineComponent, h, ref, watch } from 'vue'
import FormFieldRendererLinkage from '@/components/FormFieldRendererLinkage.vue'
import SectionField from '@/components/form-fields/components/SectionField.vue'
import RelatedContentRuntime from '@/components/related-content/RelatedContentRuntime.vue'
import { buildRuntimeFieldRules, getFieldKey } from '@/shared/form-runtime'
import {
  getFieldModeAccess,
  resolveRuntimeNodeFieldRules,
  safeParseConfig
} from '@/shared/config-runtime'
import { hasFormFieldComponent } from '@/components/form-fields'
import {
  isFormFieldExtensionNode,
  resolveRuntimeFormFieldComponentType
} from '@/shared/form-field-extension'
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
  layoutType: { type: String, default: 'vertical' },
  actionSlots: { type: Object, default: () => ({}) }
})

const emit = defineEmits(['update:modelValue'])
const runtimeForm = computed(() => props.context?.form || {})
const runtimeReleaseId = computed(() =>
  runtimeForm.value.runtimeReleaseId
  || runtimeForm.value.formReleaseId
  || ''
)
const runtimeReleaseVersion = computed(() => Number(
  runtimeForm.value.runtimeReleaseVersion
  ?? runtimeForm.value.formReleaseVersion
  ?? 0
))
const sourceRecordId = computed(() => String(
  props.context?.record?.id
  || props.context?.recordId
  || ''
))
const runtimeTraversalContextToken = computed(() => String(
  props.context?.viewCompositionTraversalToken || ''
))
const runtimeReleaseResolutionToken = computed(() => String(
  runtimeForm.value.releaseResolutionToken
  || props.context?.releaseResolutionToken
  || ''
))

function relatedContentsForNode(targetNode) {
  if (!runtimeReleaseId.value || runtimeReleaseVersion.value < 1) return []
  const keys = new Set([
    String(targetNode?.id || ''),
    String(targetNode?.nodeKey || '')
  ].filter(Boolean))
  return (runtimeForm.value.viewCompositions || []).filter(item =>
    String(item?.anchorType || '').toUpperCase() === 'FORM_NODE'
      && keys.has(String(item?.anchorKey || ''))
      && item?.config?.enabled !== false
  )
}

/**
 * 节点内关联内容的选择结果只接受服务端裁剪后的字段补丁，并通过统一
 * modelValue 事件交给父表单，避免节点局部状态绕过表单联动与校验。
 */
function applyRelatedContentPatch(patch) {
  if (!patch || typeof patch !== 'object' || Array.isArray(patch)) return
  emit('update:modelValue', {
    ...props.modelValue,
    ...patch
  })
}
const children = computed(() => props.childrenFor(props.node.id))
const childGutter = computed(() => props.layoutType === 'vertical' ? 0 : 16)
const customDescriptor = computed(() =>
  isFormFieldExtensionNode(props.node)
    ? null
    : resolveFormNodeDescriptor(props.node)
)
const customConfig = computed(() =>
  migrateFormNodeConfig(props.node, customDescriptor.value)
)
const isSectionTitleText = computed(() =>
  String(props.node.props?.textStyle || '').toUpperCase() === 'SECTION_TITLE'
)
const sectionTitleField = computed(() => ({
  fieldLabel: props.node.props?.text
    || props.node.props?.content
    || props.node.props?.label
    || props.node.nodeKey
}))
const activeTab = ref('')
const visibleTabs = computed(() => children.value.filter(tab => {
  if (tab?.props?.hidden === true) return false
  const modeRule = tab?.props?.modeAccess?.[props.mode]
  if (String(modeRule || '').toUpperCase() === 'HIDDEN') return false
  const permissionCode = String(tab?.props?.permissionCode || '').trim()
  if (!permissionCode) return true
  if (typeof props.context?.hasPermission === 'function') {
    return props.context.hasPermission(permissionCode)
  }
  const permissions = props.context?.permissions
  return Array.isArray(permissions)
    ? (permissions.includes('*') || permissions.includes(permissionCode))
    : false
}))
const activeCollapseNames = ref([])
const collapseModelValue = computed(() =>
  props.node.props?.accordion === true
    ? (activeCollapseNames.value[0] || '')
    : activeCollapseNames.value
)
const actionSlotRenderer = computed(() => defineComponent({
  name: 'FormNodeActionSlotOutlet',
  setup() {
    return () => {
      const slot = props.actionSlots?.[`action-${props.node.nodeKey}`]
      return slot ? slot({ node: props.node }) : h('span')
    }
  }
}))

watch(visibleTabs, value => {
  if (props.node.nodeType === 'TAB_SET'
      && value.length
      && !value.some(item => item.id === activeTab.value)) {
    const configured = String(props.node.props?.defaultActiveTabKey || '')
    const preferred = value.find(item =>
      [String(item.nodeKey || ''), String(item.id)].includes(configured)
    )
    activeTab.value = (preferred || value[0]).id
  } else if (props.node.nodeType === 'TAB_SET' && value.length === 0) {
    activeTab.value = ''
  }
}, { immediate: true })

watch(
  () => [props.node.id, props.node.props?.defaultExpanded],
  () => {
    activeCollapseNames.value = props.node.props?.defaultExpanded === false
      ? []
      : [props.node.id]
  },
  { immediate: true }
)

function updateCollapseNames(value) {
  activeCollapseNames.value = Array.isArray(value)
    ? value
    : (value ? [value] : [])
}

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
  const nodeFieldRules = resolveRuntimeNodeFieldRules(
    linked || {},
    props.node.rules
  )
  const fallback = props.node.nodeType === 'REPEATER'
    ? {
        fieldType: 'SUB_FORM',
        componentType: 'sub_form'
      }
    : {
        fieldType: props.node.nodeType,
        componentType: 'sub_form'
      }
  const fallbackComponentType =
    nodeProps.componentType
    || linked?.componentType
    || fallback.componentType
  return {
    ...(linked || {}),
    ...fallback,
    id: props.node.id,
    fieldId: nodeProps.fieldId ?? linked?.fieldId,
    fieldCode: nodeProps.fieldCode || linked?.fieldCode || props.node.nodeKey,
    fieldName: nodeProps.fieldName || linked?.fieldName || nodeProps.label || props.node.nodeKey,
    fieldLabel: nodeProps.label || linked?.fieldLabel || linked?.fieldName || props.node.nodeKey,
    fieldType: nodeProps.fieldType || linked?.fieldType || fallback.fieldType,
    componentType: resolveRuntimeFormFieldComponentType(
      props.node,
      fallbackComponentType,
      hasFormFieldComponent
    ),
    placeholder: nodeProps.placeholder ?? linked?.placeholder,
    defaultValue: nodeProps.defaultValue ?? linked?.defaultValue,
    isRequired: nodeProps.required === true ? 1 : (linked?.isRequired || 0),
    isReadonly: nodeProps.readonly === true ? 1 : (linked?.isReadonly || 0),
    isHidden: nodeProps.hidden === true ? 1 : (linked?.isHidden || 0),
    options: nodeProps.options ?? linked?.options,
    optionsJson: nodeProps.optionsJson ?? linked?.optionsJson,
    componentProps: Object.keys(componentProps).length ? componentProps : linked?.componentProps,
    validationRules: nodeFieldRules.validationRules,
    extensionConfig: nodeFieldRules.extensionConfig,
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
const required = computed(() => Boolean(
  props.linkageState?.required?.[fieldKey.value]
    ?? runtimeField.value?.isRequired
    ?? false
))
const options = computed(() =>
  props.linkageState?.options?.[fieldKey.value]
    || runtimeField.value?.options
    || []
)
const attachmentItemRequiredState = computed(() =>
  props.linkageState?.attachmentItemRequired?.[fieldKey.value] || {}
)
const fieldRules = computed(() => {
  const field = runtimeField.value
  if (!field) return []
  return buildRuntimeFieldRules(
    field,
    required.value,
    props.node.props.label || field.fieldLabel || field.fieldName,
    attachmentItemRequiredState.value
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
    layoutType: props.layoutType,
    actionSlots: props.actionSlots
  }
}

function childSpan(child) {
  const nodeType = String(child.nodeType || '').toUpperCase()
  if (['SECTION', 'GRID', 'TAB_SET', 'TAB', 'COLLAPSE', 'TEXT', 'ACTION_SLOT'].includes(nodeType)) {
    return 24
  }
  if (props.layoutType === 'vertical') return 24
  if (props.layoutType === 'horizontal') return 12
  return Number(child.props?.gridSpan || child.props?.span || 24)
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
.node-section-title,
.node-container {
  margin-bottom: 12px;
}
.node-child-row :deep(.el-col) {
  margin-bottom: 12px;
}
.node-text {
  color: var(--el-text-color-regular);
  line-height: 1.7;
  white-space: pre-wrap;
}
</style>
