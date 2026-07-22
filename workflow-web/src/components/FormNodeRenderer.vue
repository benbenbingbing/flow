<template>
  <el-form
    ref="formRef"
    :model="modelValue"
    :label-width="labelWidth"
    :label-position="labelPosition"
    class="form-node-renderer"
  >
    <el-row :gutter="rootGutter" class="form-node-root-row">
      <el-col
        v-for="node in rootNodes"
        :key="node.id"
        :span="nodeSpan(node)"
      >
        <FormNodeRuntimeItem
          :node="node"
          :model-value="modelValue"
          :fields="fields"
          :linkage-state="linkageState"
          :readonly="readonly"
          :mode="mode"
          :context="context"
          :data-source-runtime="dataSourceRuntime"
          :children-for="childrenFor"
          :layout-type="layoutType"
          :action-slots="$slots"
          @update:model-value="$emit('update:modelValue', $event)"
        >
          <template
            v-for="(_, slotName) in $slots"
            #[slotName]="slotProps"
          >
            <slot :name="slotName" v-bind="slotProps || {}" />
          </template>
        </FormNodeRuntimeItem>
      </el-col>
    </el-row>
  </el-form>
</template>

<script setup>
import { computed, ref } from 'vue'
import FormNodeRuntimeItem from '@/components/FormNodeRuntimeItem.vue'
import { safeParseConfig } from '@/shared/config-runtime'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  fields: { type: Array, default: () => [] },
  modelValue: { type: Object, default: () => ({}) },
  linkageState: { type: Object, default: () => ({}) },
  readonly: Boolean,
  mode: { type: String, default: 'view' },
  context: { type: Object, default: () => ({}) },
  dataSourceRuntime: { type: Object, default: null },
  rootParentId: { type: String, default: '' },
  labelWidth: { type: String, default: '100px' },
  labelPosition: { type: String, default: 'right' },
  layoutType: { type: String, default: 'vertical' }
})

defineEmits(['update:modelValue'])
const formRef = ref()

const normalizedNodes = computed(() =>
  (props.nodes || []).map(node => ({
    ...node,
    nodeType: String(node.nodeType || 'FIELD').toUpperCase(),
    bindingType: String(node.bindingType || 'NONE').toUpperCase(),
    props: safeParseConfig(node.propsDocument || node.props),
    rules: safeParseConfig(node.rulesDocument || node.rules),
    dataSourceBindings: safeParseConfig(
      node.dataSourceBindingsDocument || node.dataSourceBindings
    ),
    legacyProps: safeParseConfig(node.legacyPropsDocument || node.legacyProps),
    localOverrides: safeParseConfig(
      node.localOverridesDocument || node.localOverrides
    )
  })).sort((left, right) => Number(left.orderKey || 0) - Number(right.orderKey || 0))
)

const childrenMap = computed(() => {
  const result = new Map()
  normalizedNodes.value.forEach(node => {
    const parentId = node.parentId || ''
    if (!result.has(parentId)) result.set(parentId, [])
    result.get(parentId).push(node)
  })
  return result
})

const rootNodes = computed(() => childrenFor(props.rootParentId || ''))

function childrenFor(parentId) {
  return childrenMap.value.get(parentId || '') || []
}

const rootGutter = computed(() => props.layoutType === 'vertical' ? 0 : 16)

function nodeSpan(node) {
  const nodeType = String(node.nodeType || '').toUpperCase()
  if (['SECTION', 'GRID', 'TAB_SET', 'TAB', 'COLLAPSE', 'TEXT', 'ACTION_SLOT'].includes(nodeType)) {
    return 24
  }
  if (props.layoutType === 'vertical') return 24
  if (props.layoutType === 'horizontal') return 12
  return Number(node.props?.gridSpan || node.props?.span || 24)
}

async function validate() {
  try {
    await formRef.value?.validate()
    return true
  } catch {
    return false
  }
}

defineExpose({ validate })
</script>

<style scoped>
.form-node-renderer {
  width: 100%;
}
</style>
