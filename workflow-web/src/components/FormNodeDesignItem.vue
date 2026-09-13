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
    @dblclick.stop="$emit('open-properties', node)"
  >
    <div class="node-actions" @click.stop @dblclick.stop>
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

    <el-card
      v-if="nodeType === 'SECTION'"
      shadow="never"
      class="design-section"
      :class="containerAppearanceClasses"
    >
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
            @open-properties="$emit('open-properties', $event)"
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

    <div
      v-else-if="nodeType === 'GRID'"
      class="design-container-shell design-grid-shell"
      :class="containerAppearanceClasses"
    >
      <div
        class="design-container-caption"
        role="button"
        tabindex="0"
        @click.stop="$emit('select', node)"
        @dblclick.stop="$emit('open-properties', node)"
        @keydown.enter.stop="$emit('open-properties', node)"
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
              @open-properties="$emit('open-properties', $event)"
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

    <div
      v-else-if="nodeType === 'TAB_SET'"
      class="design-container-shell design-tab-set-shell"
      :class="containerAppearanceClasses"
    >
      <div
        class="design-container-caption"
        role="button"
        tabindex="0"
        @click.stop="$emit('select', node)"
        @dblclick.stop="$emit('open-properties', node)"
        @keydown.enter.stop="$emit('open-properties', node)"
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
            @dblclick.stop
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
              @dblclick.stop="openTabNodeProperties(tabNode)"
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
          <div class="design-tab-panel" :class="appearanceClassesFor(tabNode)">
            <div
              class="tab-node-toolbar"
              :class="{ active: selectedNodeId === tabNode.id }"
              @click.stop
              @dblclick.stop
            >
              <button
                type="button"
                class="tab-node-title"
                @click="$emit('select', tabNode)"
                @dblclick.stop="openTabNodeProperties(tabNode)"
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
                  @open-properties="$emit('open-properties', $event)"
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
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-collapse
      v-else-if="nodeType === 'COLLAPSE'"
      model-value="design-collapse"
      class="design-collapse"
      :class="containerAppearanceClasses"
    >
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
              @open-properties="$emit('open-properties', $event)"
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

    <div
      v-else-if="nodeType === 'TAB'"
      class="design-orphan-tab"
      :class="containerAppearanceClasses"
    >
      <strong>Tab 页 · {{ nodeLabelValue }}</strong>
      <span>请选择所属 Tab 集合</span>
    </div>

    <div v-else-if="nodeType === 'ACTION_SLOT'" class="design-action-slot">
      <div class="design-action-slot__identity">
        <strong>{{ nodeLabelValue || '动作插槽' }}</strong>
        <span>{{ node.nodeKey }}</span>
      </div>
      <div class="design-action-slot__buttons">
        <el-tag
          v-for="button in associatedActionButtons"
          :key="button.key"
          size="small"
          :type="button.enabled === false ? 'info' : 'primary'"
          effect="plain"
        >
          {{ button.label || button.key }}{{ button.enabled === false ? '（停用）' : '' }}
        </el-tag>
        <span v-if="!associatedActionButtons.length">尚未关联按钮</span>
      </div>
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
        :class="containerAppearanceClasses"
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
              @open-properties="$emit('open-properties', $event)"
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
          @open-properties="$emit('open-properties', $event)"
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
import {
  resolveFormContainerAppearance,
  supportsFormContainerAppearance
} from '@/shared/form-container-appearance'

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
  actionButtons: { type: Array, default: () => [] },
  canDropNode: { type: Function, required: true },
  dragDisabled: { type: Boolean, default: false }
})

const emit = defineEmits(['select', 'open-properties', 'move', 'remove', 'drop'])

const containerTypes = new Set([
  'SECTION', 'GRID', 'TAB_SET', 'TAB', 'COLLAPSE', 'ACTION_SLOT'
])
const fieldTypes = new Set(['FIELD', 'SUB_FORM', 'REPEATER'])
const nodeType = computed(() =>
  String(props.node.nodeType || props.legacyNodeType(props.node) || 'FIELD').toUpperCase()
)
const nodeConfig = computed(() => safeParseConfig(props.node.componentProps))
const containerAppearanceClasses = computed(() =>
  appearanceClasses(nodeType.value, nodeConfig.value)
)
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
  // ACTION_SLOT 没有子节点，只借用容器视觉样式；其外层仍应参与表单栅格布局。
  if (
    (containerNode.value && nodeType.value !== 'ACTION_SLOT')
    || nodeType.value === 'TEXT'
  ) return { width: '100%' }
  return props.nodeStyleFor(props.node)
})
const nodeLabelValue = computed(() => props.nodeLabel(props.node.id))
const associatedActionButtons = computed(() => props.actionButtons.filter(button =>
  String(button?.placement || '').toUpperCase() === 'ACTION_SLOT'
  && String(button?.slotKey || '') === String(props.node.nodeKey || '')
))

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

/**
 * 业务边框与内边距只作用于容器内容；最外层节点的 hover/active 描边
 * 始终保留，确保无边框容器在设计画布中仍可被发现和选中。
 */
function appearanceClasses(type, config) {
  if (!supportsFormContainerAppearance(type)) return {}
  const resolved = resolveFormContainerAppearance(type, config)
  return {
    'is-container-padded': resolved.showPadding === true,
    'is-container-paddingless': resolved.showPadding !== true,
    'is-container-bordered': resolved.showBorder === true,
    'is-container-borderless': resolved.showBorder !== true
  }
}

function appearanceClassesFor(targetNode) {
  const type = String(
    targetNode?.nodeType || props.legacyNodeType(targetNode) || 'FIELD'
  ).toUpperCase()
  return appearanceClasses(
    type,
    safeParseConfig(targetNode?.componentProps)
  )
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

function openTabNodeProperties(tabNode) {
  activeTabId.value = tabNode.id
  emit('open-properties', tabNode)
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
    actionButtons: props.actionButtons,
    canDropNode: props.canDropNode,
    dragDisabled: props.dragDisabled
  }
}
</script>

<style scoped src="./FormNodeDesignItem.scss"></style>
