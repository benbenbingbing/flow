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
          :reveal-field-code="revealFieldCode"
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
import { computed, nextTick, ref } from 'vue'
import FormNodeRuntimeItem from '@/components/FormNodeRuntimeItem.vue'
import { safeParseConfig } from '@/shared/config-runtime'
import { resolveFormNodeLayoutSpan } from '@/shared/form-node-property-schema'

const props = defineProps({
  nodes: { type: Array, default: () => [] },
  fields: { type: Array, default: () => [] },
  modelValue: { type: Object, default: () => ({}) },
  linkageState: { type: Object, default: () => ({}) },
  readonly: Boolean,
  mode: { type: String, default: 'view' },
  context: { type: Object, default: () => ({}) },
  dataSourceRuntime: { type: Object, default: null },
  rootParentId: { type: [String, Number], default: '' },
  excludedNodeIds: { type: Array, default: () => [] },
  labelWidth: { type: String, default: '100px' },
  labelPosition: { type: String, default: 'right' },
  layoutType: { type: String, default: 'vertical' }
})

defineEmits(['update:modelValue'])
const formRef = ref()
const revealFieldCode = ref('')

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

const excludedNodeIdSet = computed(() =>
  new Set((props.excludedNodeIds || []).map(id => String(id)))
)
const rootNodes = computed(() =>
  childrenFor(props.rootParentId || '')
    .filter(node => !excludedNodeIdSet.value.has(String(node.id)))
)

function childrenFor(parentId) {
  return childrenMap.value.get(parentId || '') || []
}

const rootGutter = computed(() => props.layoutType === 'vertical' ? 0 : 16)

function nodeSpan(node) {
  return resolveFormNodeLayoutSpan(node, props.layoutType, 24)
}

async function validate() {
  try {
    await formRef.value?.validate()
    return true
  } catch {
    return false
  }
}

/**
 * 重新写入定位目标，即使连续两次是同一字段，也能再次触发
 * 用户手动关闭后的 Tab/Collapse 展开逻辑。
 */
async function revealValidationField(fieldCode) {
  const target = String(fieldCode || '').trim()
  if (!target) return false
  revealFieldCode.value = ''
  await nextTick()
  revealFieldCode.value = target
  await nextTick()
  return true
}

defineExpose({ validate, revealValidationField })
</script>

<style scoped>
.form-node-renderer {
  width: 100%;
}
</style>
