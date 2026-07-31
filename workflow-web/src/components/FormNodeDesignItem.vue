<template>
  <div
    class="form-node-design-item"
    :data-node-id="node.id"
    :data-node-type="nodeType"
    :class="{
      active: selectedNodeId === node.id,
      'is-container': containerNode,
      'is-grid-child': withinGrid
    }"
    :style="outerStyle"
    @click.stop="$emit('select', node)"
  >
    <div class="node-actions" @click.stop>
      <span
        class="form-node-drag-handle"
        role="button"
        tabindex="0"
        aria-label="拖拽节点"
        title="拖拽调整顺序或移动到其他容器"
      >
        <el-icon><Rank /></el-icon>
      </span>
      <el-button-group size="small">
        <el-button
          aria-label="上移节点"
          :disabled="siblingIndex === 0"
          @click="$emit('move', { node, direction: -1 })"
        >
          <el-icon><ArrowUp /></el-icon>
        </el-button>
        <el-button
          aria-label="下移节点"
          :disabled="siblingIndex === siblingCount - 1"
          @click="$emit('move', { node, direction: 1 })"
        >
          <el-icon><ArrowDown /></el-icon>
        </el-button>
        <el-button type="danger" aria-label="删除节点" @click="$emit('remove', node)">
          <el-icon><Delete /></el-icon>
        </el-button>
      </el-button-group>
    </div>

    <el-card v-if="nodeType === 'SECTION'" shadow="never" class="design-section">
      <template #header>{{ nodeLabelValue }}</template>
      <FormNodeDraggableList
        :items="children"
        :parent-id="node.id"
        :can-drop="canDropNode"
        :disabled="dragDisabled"
        zone-class="design-node-children"
        @drop="$emit('drop', $event)"
      >
        <template #item="{ element: child, index }">
          <FormNodeDesignItem
            v-bind="childItemProps(child, index)"
            @select="$emit('select', $event)"
            @move="$emit('move', $event)"
            @remove="$emit('remove', $event)"
            @drop="$emit('drop', $event)"
          />
        </template>
        <template #footer>
          <div v-if="!children.length" class="design-container-empty">
            拖拽节点到此区块
          </div>
        </template>
      </FormNodeDraggableList>
    </el-card>

    <div v-else-if="nodeType === 'GRID'" class="design-container-shell design-grid-shell">
      <div
        class="design-container-caption"
        role="button"
        tabindex="0"
        @click.stop="$emit('select', node)"
        @keydown.enter.stop="$emit('select', node)"
      >
        <strong>栅格容器</strong>
        <span>{{ children.length }} 个节点</span>
      </div>
      <FormNodeDraggableList
        :items="children"
        :parent-id="node.id"
        :can-drop="canDropNode"
        :disabled="dragDisabled"
        tag="el-row"
        :component-data="{ gutter: Number(nodeConfig.gutter || 16) }"
        zone-class="design-grid"
        @drop="$emit('drop', $event)"
      >
        <template #item="{ element: child, index }">
          <el-col
            :span="nodeSpanFor(child, Number(nodeConfig.defaultSpan || 12))"
          >
            <FormNodeDesignItem
              v-bind="childItemProps(child, index, 'GRID')"
              @select="$emit('select', $event)"
              @move="$emit('move', $event)"
              @remove="$emit('remove', $event)"
              @drop="$emit('drop', $event)"
            />
          </el-col>
        </template>
        <template #footer>
          <div v-if="!children.length" class="design-container-empty">
            拖拽节点到此栅格
          </div>
        </template>
      </FormNodeDraggableList>
    </div>

    <div v-else-if="nodeType === 'TAB_SET'" class="design-container-shell design-tab-set-shell">
      <div
        class="design-container-caption"
        role="button"
        tabindex="0"
        @click.stop="$emit('select', node)"
        @keydown.enter.stop="$emit('select', node)"
      >
        <strong>Tab 集合</strong>
        <span>{{ children.length }} 个页签</span>
      </div>
      <div class="design-tab-order-caption">拖拽页签手柄调整顺序或移动到其他 Tab 集合</div>
      <FormNodeDraggableList
        :items="children"
        :parent-id="node.id"
        :can-drop="canDropNode"
        :disabled="dragDisabled"
        zone-class="design-tab-order"
        @drop="$emit('drop', $event)"
      >
        <template #item="{ element: tabNode }">
          <div
            class="design-tab-order-item"
            :data-node-id="tabNode.id"
            data-node-type="TAB"
            :class="{ active: String(activeTabId) === String(tabNode.id) }"
            @click.stop
          >
            <span
              class="form-node-drag-handle"
              role="button"
              tabindex="0"
              aria-label="拖拽 Tab 页"
              title="拖拽调整 Tab 页顺序"
            >
              <el-icon><Rank /></el-icon>
            </span>
            <button
              type="button"
              class="design-tab-order-label"
              @click="selectTabNode(tabNode)"
            >
              {{ nodeLabelFor(tabNode) }}
            </button>
          </div>
        </template>
        <template #footer>
          <div v-if="!children.length" class="design-container-empty">
            拖拽 Tab 页到此集合
          </div>
        </template>
      </FormNodeDraggableList>
      <el-tabs
        v-if="children.length"
        v-model="activeTabId"
        type="border-card"
        :tab-position="nodeConfig.tabPosition || 'top'"
        class="design-tabs"
        @tab-click="handleTabClick"
      >
        <el-tab-pane
          v-for="(tabNode, index) in children"
          :key="tabNode.id"
          :label="nodeLabelFor(tabNode)"
          :name="tabNode.id"
        >
          <div
            class="tab-node-toolbar"
            :class="{ active: selectedNodeId === tabNode.id }"
            @click.stop
          >
            <button
              type="button"
              class="tab-node-title"
              @click="$emit('select', tabNode)"
            >
              <span>Tab 页</span>
              <strong>{{ nodeLabelFor(tabNode) }}</strong>
            </button>
            <el-button-group size="small">
              <el-button
                aria-label="左移 Tab 页"
                :disabled="index === 0"
                @click="$emit('move', { node: tabNode, direction: -1 })"
              >
                <el-icon><ArrowLeft /></el-icon>
              </el-button>
              <el-button
                aria-label="右移 Tab 页"
                :disabled="index === children.length - 1"
                @click="$emit('move', { node: tabNode, direction: 1 })"
              >
                <el-icon><ArrowRight /></el-icon>
              </el-button>
              <el-button
                type="danger"
                aria-label="删除 Tab 页"
                @click="$emit('remove', tabNode)"
              >
                <el-icon><Delete /></el-icon>
              </el-button>
            </el-button-group>
          </div>
          <FormNodeDraggableList
            :items="childrenFor(tabNode.id)"
            :parent-id="tabNode.id"
            :can-drop="canDropNode"
            :disabled="dragDisabled"
            zone-class="design-node-children tab-node-children"
            @drop="$emit('drop', $event)"
          >
            <template #item="{ element: child, index: childIndex }">
              <FormNodeDesignItem
                v-bind="childItemProps(child, childIndex, 'TAB')"
                @select="$emit('select', $event)"
                @move="$emit('move', $event)"
                @remove="$emit('remove', $event)"
                @drop="$emit('drop', $event)"
              />
            </template>
            <template #footer>
              <div
                v-if="!childrenFor(tabNode.id).length"
                class="design-container-empty"
              >
                拖拽节点到此 Tab 页
              </div>
            </template>
          </FormNodeDraggableList>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-collapse v-else-if="nodeType === 'COLLAPSE'" model-value="design-collapse" class="design-collapse">
      <el-collapse-item
        :name="node.id"
        :title="nodeLabelValue"
      >
        <FormNodeDraggableList
          :items="children"
          :parent-id="node.id"
          :can-drop="canDropNode"
          :disabled="dragDisabled"
          zone-class="design-node-children"
          @drop="$emit('drop', $event)"
        >
          <template #item="{ element: child, index }">
            <FormNodeDesignItem
              v-bind="childItemProps(child, index)"
              @select="$emit('select', $event)"
              @move="$emit('move', $event)"
              @remove="$emit('remove', $event)"
              @drop="$emit('drop', $event)"
            />
          </template>
          <template #footer>
            <div v-if="!children.length" class="design-container-empty">
              拖拽节点到此折叠面板
            </div>
          </template>
        </FormNodeDraggableList>
      </el-collapse-item>
    </el-collapse>

    <div
      v-else-if="nodeType === 'TEXT' && isSectionTitleText"
      class="design-section-title"
    >
      <SectionField :field="sectionTitleField" />
    </div>

    <div v-else-if="nodeType === 'TEXT'" class="design-text">
      {{ nodeConfig.text || nodeConfig.content || nodeLabelValue }}
    </div>

    <div v-else-if="nodeType === 'TAB'" class="design-orphan-tab">
      <strong>Tab 页 · {{ nodeLabelValue }}</strong>
      <span>请选择所属 Tab 集合</span>
    </div>

    <div v-else-if="nodeType === 'ACTION_SLOT'" class="design-action-slot">
      <strong>动作插槽</strong>
      <span>{{ nodeLabelValue }}</span>
    </div>

    <div v-else-if="fieldNode" class="design-field-node">
      <el-form-item
        :label="node.fieldLabel || node.fieldName"
        :required="node.isRequired === 1"
        class="design-form-item"
      >
        <FormFieldRenderer :field="node" :disabled="true" />
      </el-form-item>
      <div
        v-if="nestedFieldContainer"
        class="nested-field-children"
      >
        <div class="design-container-caption">
          <strong>内嵌节点</strong>
          <span>{{ children.length }} 个节点</span>
        </div>
        <FormNodeDraggableList
          :items="children"
          :parent-id="node.id"
          :can-drop="canDropNode"
          :disabled="dragDisabled"
          zone-class="design-node-children"
          @drop="$emit('drop', $event)"
        >
          <template #item="{ element: child, index }">
            <FormNodeDesignItem
              v-bind="childItemProps(child, index)"
              @select="$emit('select', $event)"
              @move="$emit('move', $event)"
              @remove="$emit('remove', $event)"
              @drop="$emit('drop', $event)"
            />
          </template>
          <template #footer>
            <div v-if="!children.length" class="design-container-empty">
              拖拽节点到此内嵌区域
            </div>
          </template>
        </FormNodeDraggableList>
      </div>
    </div>

    <FormNodeDraggableList
      v-else
      :items="children"
      :parent-id="node.id"
      :can-drop="canDropNode"
      :disabled="dragDisabled"
      zone-class="design-node-children"
      @drop="$emit('drop', $event)"
    >
      <template #item="{ element: child, index }">
        <FormNodeDesignItem
          v-bind="childItemProps(child, index)"
          @select="$emit('select', $event)"
          @move="$emit('move', $event)"
          @remove="$emit('remove', $event)"
          @drop="$emit('drop', $event)"
        />
      </template>
    </FormNodeDraggableList>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  Delete,
  Rank
} from '@element-plus/icons-vue'
import FormFieldRenderer from '@/components/FormFieldRenderer.vue'
import FormNodeDraggableList from '@/components/FormNodeDraggableList.vue'
import SectionField from '@/components/form-fields/components/SectionField.vue'
import { safeParseConfig } from '@/shared/config-runtime'

defineOptions({ name: 'FormNodeDesignItem' })

const props = defineProps({
  node: { type: Object, required: true },
  siblingIndex: { type: Number, required: true },
  siblingCount: { type: Number, required: true },
  selectedNodeId: { type: [String, Number], default: '' },
  layoutType: { type: String, default: 'vertical' },
  parentNodeType: { type: String, default: '' },
  childrenFor: { type: Function, required: true },
  nodeSpanFor: { type: Function, required: true },
  nodeStyleFor: { type: Function, required: true },
  legacyNodeType: { type: Function, required: true },
  nodeLabel: { type: Function, required: true },
  canDropNode: { type: Function, required: true },
  dragDisabled: { type: Boolean, default: false }
})

const emit = defineEmits(['select', 'move', 'remove', 'drop'])

const containerTypes = new Set([
  'SECTION', 'GRID', 'TAB_SET', 'TAB', 'COLLAPSE', 'ACTION_SLOT'
])
const fieldTypes = new Set(['FIELD', 'SUB_FORM', 'REPEATER'])
const nodeType = computed(() =>
  String(props.node.nodeType || props.legacyNodeType(props.node) || 'FIELD').toUpperCase()
)
const nodeConfig = computed(() => safeParseConfig(props.node.componentProps))
const isSectionTitleText = computed(() =>
  String(nodeConfig.value.textStyle || '').toUpperCase() === 'SECTION_TITLE'
)
const sectionTitleField = computed(() => ({
  fieldLabel: nodeConfig.value.text || nodeConfig.value.content || nodeLabelValue.value
}))
const children = computed(() => props.childrenFor(props.node.id))
const withinGrid = computed(() => String(props.parentNodeType).toUpperCase() === 'GRID')
const containerNode = computed(() => containerTypes.has(nodeType.value))
const fieldNode = computed(() => fieldTypes.has(nodeType.value))
const nestedFieldContainer = computed(() =>
  ['SUB_FORM', 'REPEATER'].includes(nodeType.value)
)
const activeTabId = ref('')
const outerStyle = computed(() => {
  if (withinGrid.value) return { width: '100%' }
  if (containerNode.value || nodeType.value === 'TEXT') return { width: '100%' }
  return props.nodeStyleFor(props.node)
})
const nodeLabelValue = computed(() => props.nodeLabel(props.node.id))

watch(
  [children, () => props.selectedNodeId],
  ([tabNodes, selectedNodeId]) => {
    if (nodeType.value !== 'TAB_SET') return
    const selectedTabId = findContainingTabId(selectedNodeId, tabNodes)
    if (selectedTabId) {
      activeTabId.value = selectedTabId
      return
    }
    if (!tabNodes.some(item => String(item.id) === String(activeTabId.value))) {
      activeTabId.value = tabNodes[0]?.id || ''
    }
  },
  { immediate: true }
)

function nodeLabelFor(node) {
  return props.nodeLabel(node.id)
}

function nodeSpanFor(node, fallback) {
  return props.nodeSpanFor(node, fallback)
}

function findContainingTabId(selectedNodeId, tabNodes) {
  if (!selectedNodeId) return ''
  for (const tabNode of tabNodes) {
    if (String(tabNode.id) === String(selectedNodeId)
        || containsNode(tabNode.id, selectedNodeId)) {
      return tabNode.id
    }
  }
  return ''
}

function containsNode(parentId, targetId, visited = new Set()) {
  const normalizedParentId = String(parentId)
  if (visited.has(normalizedParentId)) return false
  const nextVisited = new Set(visited)
  nextVisited.add(normalizedParentId)
  return props.childrenFor(parentId).some(child =>
    String(child.id) === String(targetId)
      || containsNode(child.id, targetId, nextVisited)
  )
}

function handleTabClick(tab) {
  const paneName = tab?.paneName ?? tab?.props?.name
  const tabNode = children.value.find(item =>
    String(item.id) === String(paneName)
  )
  if (tabNode) {
    activeTabId.value = tabNode.id
    emit('select', tabNode)
  }
}

function selectTabNode(tabNode) {
  activeTabId.value = tabNode.id
  emit('select', tabNode)
}

function childItemProps(child, index, parentNodeType = nodeType.value) {
  const siblings = props.childrenFor(child.parentId || '')
  return {
    node: child,
    siblingIndex: index,
    siblingCount: siblings.length,
    selectedNodeId: props.selectedNodeId,
    layoutType: props.layoutType,
    parentNodeType,
    childrenFor: props.childrenFor,
    nodeSpanFor: props.nodeSpanFor,
    nodeStyleFor: props.nodeStyleFor,
    legacyNodeType: props.legacyNodeType,
    nodeLabel: props.nodeLabel,
    canDropNode: props.canDropNode,
    dragDisabled: props.dragDisabled
  }
}
</script>

<style scoped>
.form-node-design-item {
  position: relative;
  min-width: 0;
  margin-bottom: 12px;
  border: 1px solid transparent;
  border-radius: 6px;
  cursor: pointer;
  transition: border-color 0.18s, box-shadow 0.18s, background-color 0.18s;
}

.form-node-design-item:hover {
  border-color: var(--el-border-color);
}

.form-node-design-item.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--el-color-primary) 15%, transparent);
}

.form-node-design-item.is-grid-child {
  margin-bottom: 0;
}

.design-section,
.design-tabs,
.design-collapse,
.design-grid,
.design-text,
.design-container-shell {
  width: 100%;
  margin-bottom: 0;
}

.design-container-shell {
  padding: 10px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.design-container-caption {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 28px;
  padding: 0 108px 8px 2px;
  color: var(--el-text-color-regular);
  cursor: pointer;
}

.design-container-caption strong {
  font-size: 13px;
  font-weight: 600;
}

.design-container-caption span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.design-tab-set-shell {
  background: color-mix(in srgb, var(--el-color-primary) 4%, white);
}

.design-container-empty {
  width: 100%;
  min-height: 48px;
  padding: 14px;
  border: 1px dashed var(--el-border-color-lighter);
  border-radius: 4px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  text-align: center;
}

.design-node-children {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  min-height: 24px;
}

.design-node-children.is-empty {
  min-height: 64px;
}

.design-grid :deep(.el-col) {
  padding-bottom: 12px;
}

.design-form-item {
  margin: 12px;
}

.design-field-node {
  width: 100%;
}

.nested-field-children {
  margin: 0 12px 12px;
  padding: 10px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.nested-field-children .design-container-caption {
  padding-right: 2px;
}

.design-text {
  padding: 12px;
  color: var(--el-text-color-regular);
  line-height: 1.7;
  white-space: pre-wrap;
}

.design-section-title {
  display: flex;
  align-items: center;
  min-height: 48px;
  padding: 0 112px 0 12px;
}

.design-orphan-tab,
.design-action-slot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 48px;
  padding: 12px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-extra-light);
}

.design-orphan-tab strong,
.design-action-slot strong {
  font-size: 13px;
}

.design-orphan-tab span,
.design-action-slot span {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.node-actions {
  position: absolute;
  z-index: 3;
  top: 6px;
  right: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.18s;
}

.form-node-design-item:hover > .node-actions,
.form-node-design-item.active > .node-actions {
  opacity: 1;
}

.form-node-drag-handle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 24px;
  padding: 0;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  color: var(--el-text-color-secondary);
  background: var(--el-bg-color);
  cursor: grab;
  touch-action: none;
  user-select: none;
  -webkit-user-drag: none;
}

.form-node-drag-handle:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

.form-node-drag-handle:active {
  cursor: grabbing;
}

.design-tab-order-caption {
  margin-bottom: 6px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.design-tab-order {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  min-height: 36px;
  margin-bottom: 10px;
}

.design-tab-order-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 6px;
  border: 1px solid var(--el-border-color);
  border-radius: 5px;
  background: var(--el-bg-color);
}

.design-tab-order-item.active {
  border-color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 8%, white);
}

.design-tab-order-label {
  max-width: 180px;
  padding: 0 4px;
  overflow: hidden;
  border: 0;
  color: var(--el-text-color-primary);
  background: transparent;
  cursor: pointer;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.design-tabs :deep(.el-tabs__content) {
  padding: 12px;
}

.design-tabs :deep(.el-tabs__header) {
  display: none;
}

.tab-node-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 8px;
  margin-bottom: 10px;
  border-left: 3px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-light);
}

.tab-node-toolbar.active {
  border-left-color: var(--el-color-primary);
  background: color-mix(in srgb, var(--el-color-primary) 8%, white);
}

.tab-node-title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  min-width: 0;
  padding: 0;
  border: 0;
  color: var(--el-text-color-primary);
  background: transparent;
  cursor: pointer;
}

.tab-node-title span {
  color: var(--el-color-primary);
  font-size: 12px;
  font-weight: 600;
}

.tab-node-title strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-node-children {
  padding: 4px;
}
</style>
