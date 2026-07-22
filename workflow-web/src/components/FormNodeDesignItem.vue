<template>
  <div
    class="form-node-design-item"
    :class="{
      active: selectedNodeId === node.id,
      'grid-item': layoutType === 'grid',
      'section-node': sectionNode
    }"
    :style="sectionNode ? { width: '100%' } : gridStyle"
    @click.stop="$emit('select', node)"
  >
    <div class="node-order">{{ siblingIndex + 1 }}</div>
    <div class="node-content">
      <div class="node-meta">
        <el-tag size="small" effect="plain">{{ node.nodeType || legacyNodeType(node) }}</el-tag>
        <el-tag v-if="node.parentId" size="small" type="info" effect="plain">
          父级：{{ nodeLabel(node.parentId) }}
        </el-tag>
        <span>rev.{{ node.revision || 0 }}</span>
      </div>
      <SectionField v-if="sectionNode" :field="node" />
      <el-form-item
        v-else
        :label="node.fieldLabel || node.fieldName"
        :required="node.isRequired === 1"
        class="design-form-item"
      >
        <el-tag v-if="tabSubForm" type="warning" size="small" class="tab-badge">
          Tab 子表单
        </el-tag>
        <FormFieldRenderer :field="node" :disabled="true" />
      </el-form-item>

      <div v-if="children.length" class="node-children">
        <FormNodeDesignItem
          v-for="(child, index) in children"
          :key="child.id"
          :node="child"
          :sibling-index="index"
          :sibling-count="children.length"
          :selected-node-id="selectedNodeId"
          :layout-type="layoutType"
          :children-for="childrenFor"
          :grid-style-for="gridStyleFor"
          :is-section-node="isSectionNode"
          :is-tab-sub-form="isTabSubForm"
          :legacy-node-type="legacyNodeType"
          :node-label="nodeLabel"
          @select="$emit('select', $event)"
          @move="$emit('move', $event)"
          @remove="$emit('remove', $event)"
        />
      </div>
    </div>

    <div class="node-actions" @click.stop>
      <el-button-group size="small">
        <el-button
          :disabled="siblingIndex === 0"
          @click="$emit('move', { node, direction: -1 })"
        >
          <el-icon><ArrowUp /></el-icon>
        </el-button>
        <el-button
          :disabled="siblingIndex === siblingCount - 1"
          @click="$emit('move', { node, direction: 1 })"
        >
          <el-icon><ArrowDown /></el-icon>
        </el-button>
        <el-button type="danger" @click="$emit('remove', node)">
          <el-icon><Delete /></el-icon>
        </el-button>
      </el-button-group>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ArrowDown, ArrowUp, Delete } from '@element-plus/icons-vue'
import FormFieldRenderer from '@/components/FormFieldRenderer.vue'
import SectionField from '@/components/form-fields/components/SectionField.vue'

defineOptions({ name: 'FormNodeDesignItem' })

const props = defineProps({
  node: { type: Object, required: true },
  siblingIndex: { type: Number, required: true },
  siblingCount: { type: Number, required: true },
  selectedNodeId: { type: [String, Number], default: '' },
  layoutType: { type: String, default: 'vertical' },
  childrenFor: { type: Function, required: true },
  gridStyleFor: { type: Function, required: true },
  isSectionNode: { type: Function, required: true },
  isTabSubForm: { type: Function, required: true },
  legacyNodeType: { type: Function, required: true },
  nodeLabel: { type: Function, required: true }
})

defineEmits(['select', 'move', 'remove'])

const children = computed(() => props.childrenFor(props.node.id))
const sectionNode = computed(() => props.isSectionNode(props.node))
const tabSubForm = computed(() => props.isTabSubForm(props.node))
const gridStyle = computed(() => props.gridStyleFor(props.node))
</script>

<style scoped>
.form-node-design-item {
  display: flex;
  align-items: flex-start;
  padding: 16px;
  margin-bottom: 8px;
  background: #fff;
  border: 2px solid transparent;
  border-radius: 4px;
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
}
.form-node-design-item:hover {
  border-color: #c0c4cc;
}
.form-node-design-item.active {
  border-color: #409eff;
  background: #f5f7fa;
}
.node-order {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin: 8px 12px 0 0;
  color: #fff;
  background: #409eff;
  border-radius: 50%;
  font-size: 12px;
}
.node-content {
  flex: 1;
  min-width: 0;
}
.node-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  color: #909399;
  font-size: 11px;
}
.node-children {
  width: 100%;
  margin-top: 12px;
  padding: 12px 0 0 16px;
  border-left: 2px dashed #dcdfe6;
}
.design-form-item {
  margin-bottom: 0 !important;
}
.node-actions {
  margin-left: 12px;
  padding-top: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}
.form-node-design-item:hover > .node-actions,
.form-node-design-item.active > .node-actions {
  opacity: 1;
}
.section-node {
  width: 100% !important;
}
</style>
