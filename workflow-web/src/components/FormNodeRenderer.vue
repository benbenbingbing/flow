<template>
  <el-form
    ref="formRef"
    :model="modelValue"
    :label-width="labelWidth"
    :label-position="labelPosition"
    class="form-node-renderer"
  >
    <FormNodeRuntimeItem
      v-for="node in rootNodes"
      :key="node.id"
      :node="node"
      :model-value="modelValue"
      :fields="fields"
      :linkage-state="linkageState"
      :readonly="readonly"
      :mode="mode"
      :context="context"
      :data-source-runtime="dataSourceRuntime"
      :children-for="childrenFor"
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
  labelPosition: { type: String, default: 'right' }
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
