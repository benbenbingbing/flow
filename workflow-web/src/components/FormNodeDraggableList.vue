<template>
  <component
    :is="tag"
    ref="containerRef"
    v-bind="componentData"
    :data-form-node-parent-id="String(parentId || '')"
    :data-sortable-ready="sortableReady ? 'true' : 'false'"
    :class="[
      'form-node-drop-zone',
      zoneClass,
      { 'is-empty': localItems.length === 0 }
    ]"
  >
    <template
      v-for="(element, index) in localItems"
      :key="element.id"
    >
      <slot name="item" :element="element" :index="index" />
    </template>
    <slot name="footer" />
  </component>
</template>

<script setup>
import {
  nextTick,
  onBeforeUnmount,
  onMounted,
  onUpdated,
  ref,
  watch
} from 'vue'
import Sortable from 'sortablejs'

let activeDragNode = null

const props = defineProps({
  items: { type: Array, default: () => [] },
  parentId: { type: [String, Number], default: '' },
  canDrop: { type: Function, required: true },
  disabled: { type: Boolean, default: false },
  handle: { type: String, default: '.form-node-drag-handle' },
  tag: { type: String, default: 'div' },
  componentData: { type: Object, default: () => ({}) },
  zoneClass: { type: [String, Array, Object], default: '' }
})

const emit = defineEmits(['drop'])
const containerRef = ref(null)
const localItems = ref([])
const dragging = ref(false)
const sortableReady = ref(false)
let sortableInstance = null

const dragGroup = Object.freeze({
  name: 'form-node-tree',
  pull: true,
  put: true
})

watch(
  () => props.items,
  (items) => {
    if (!dragging.value) {
      localItems.value = Array.isArray(items) ? [...items] : []
    }
  },
  { immediate: true }
)

watch(
  () => props.disabled,
  (disabled) => {
    sortableInstance?.option('disabled', disabled)
  }
)

watch(
  () => props.handle,
  (handle) => {
    sortableInstance?.option('handle', handle)
  }
)

onMounted(async () => {
  await nextTick()
  initializeSortable()
})

onUpdated(() => {
  decorateSortableItems()
})

onBeforeUnmount(() => {
  sortableInstance?.destroy()
  sortableInstance = null
  sortableReady.value = false
})

function resolveContainerElement() {
  const container = containerRef.value
  return container?.$el || container || null
}

function decorateSortableItems() {
  const container = resolveContainerElement()
  if (!container) return

  Array.from(container.children).forEach((child, index) => {
    const item = localItems.value[index]
    const sortableItem = Boolean(item)
    child.classList.toggle('form-node-sortable-item', sortableItem)
    if (sortableItem) {
      child.dataset.formNodeDragId = String(item.id)
    } else {
      delete child.dataset.formNodeDragId
    }
  })
}

function initializeSortable() {
  const container = resolveContainerElement()
  if (!container || sortableInstance) return

  decorateSortableItems()
  sortableInstance = new Sortable(container, {
    group: dragGroup,
    handle: props.handle,
    draggable: '.form-node-sortable-item',
    disabled: props.disabled,
    animation: 160,
    direction: () => resolveDragDirection(container),
    forceFallback: true,
    fallbackOnBody: true,
    fallbackTolerance: 3,
    swapThreshold: 0.65,
    ghostClass: 'form-node-drag-ghost',
    chosenClass: 'form-node-drag-chosen',
    dragClass: 'form-node-dragging',
    fallbackClass: 'form-node-drag-fallback',
    onStart: handleStart,
    onMove: handleMove,
    onAdd: handleAdd,
    onUpdate: handleUpdate,
    onEnd: handleEnd
  })
  sortableReady.value = true
}

function resolveDragDirection(container) {
  if (container.classList.contains('design-tab-order')
      || container.classList.contains('design-grid')) {
    return 'horizontal'
  }

  const items = Array.from(
    container.querySelectorAll(':scope > .form-node-sortable-item')
  )
  if (items.length < 2) return 'vertical'

  const firstRect = items[0].getBoundingClientRect()
  const secondRect = items[1].getBoundingClientRect()
  const sameRowTolerance = Math.min(firstRect.height, secondRect.height) / 2
  return Math.abs(firstRect.top - secondRect.top) <= sameRowTolerance
    ? 'horizontal'
    : 'vertical'
}

function eventIndex(event, key) {
  const draggableIndex = event?.[`${key}DraggableIndex`]
  return Number.isInteger(draggableIndex)
    ? draggableIndex
    : event?.[`${key}Index`]
}

function handleStart(event) {
  const sourceIndex = eventIndex(event, 'old')
  activeDragNode = localItems.value[sourceIndex] || null
  dragging.value = Boolean(activeDragNode)
}

function handleMove(event) {
  const targetParentId =
    event?.to?.dataset?.formNodeParentId ?? props.parentId
  return Boolean(activeDragNode)
    && props.canDrop(activeDragNode, targetParentId)
}

function emitDrop(event) {
  if (!activeDragNode) return
  emit('drop', {
    node: activeDragNode,
    newParentId: props.parentId || '',
    newIndex: eventIndex(event, 'new')
  })
}

function handleAdd(event) {
  emitDrop(event)
}

function handleUpdate(event) {
  emitDrop(event)
}

async function handleEnd() {
  dragging.value = false
  activeDragNode = null
  await nextTick()
  localItems.value = Array.isArray(props.items) ? [...props.items] : []
  await nextTick()
  decorateSortableItems()
}
</script>

<style>
.form-node-drop-zone {
  min-width: 0;
}

.form-node-drag-ghost {
  opacity: 0.35;
  outline: 2px dashed var(--el-color-primary);
  outline-offset: 2px;
}

.form-node-drag-chosen {
  box-shadow: 0 8px 24px rgba(64, 158, 255, 0.2);
}

.form-node-dragging {
  opacity: 0.9;
}

.form-node-drag-fallback {
  opacity: 0.92;
  box-shadow: 0 12px 32px rgba(31, 45, 61, 0.2);
}
</style>
