<template>
  <div v-if="hasContent" class="next-approver-section">
    <el-form-item v-if="loading" label="下一节点审批人">
      <div class="next-approver-section__loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在根据审批条件计算下一节点…</span>
      </div>
    </el-form-item>

    <el-form-item
      v-else-if="normalizedPreview.status === 'DEFERRED'"
      label="下一节点审批人"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="normalizedPreview.message || '后续包含运行时节点，将在提交后确定最终审批人'"
      />
    </el-form-item>

    <el-form-item
      v-else-if="normalizedPreview.status === 'BLOCKED'"
      label="下一节点审批人"
    >
      <el-alert
        type="error"
        :closable="false"
        show-icon
        :title="normalizedPreview.message || '当前条件无法确定下一审批节点'"
      />
    </el-form-item>

    <template v-else-if="visibleNodes.length">
      <el-form-item
        v-for="node in visibleNodes"
        :key="node.nodeId"
        :label="node.nodeName"
        :required="node.editable"
      >
        <ControlledUserSelector
          v-if="node.editable"
          :model-value="nodeModelValue(node)"
          :multiple="node.multiple"
          :ordered="node.assignmentMode === 'MULTI_INSTANCE'"
          :initial-options="node.assignees"
          :task-id="taskId"
          :target-node-id="node.nodeId"
          :scope-key="normalizedPreview.scopeKey"
          :action="action"
          :action-label="actionLabel"
          :comment="comment"
          :form-data="formData"
          :title="`选择${node.nodeName}审批人`"
          :placeholder="`请选择${node.nodeName}审批人`"
          @update:model-value="updateNodeValue(node, $event)"
        />
        <div v-else class="next-approver-section__readonly">
          <el-tag
            v-for="user in node.assignees"
            :key="user.userKey"
            size="small"
          >
            {{ user.displayName || user.username || user.userId }}
          </el-tag>
          <span v-if="!node.assignees.length" class="next-approver-section__empty">
            暂未解析到审批人
          </span>
        </div>
      </el-form-item>
    </template>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import ControlledUserSelector from '@/components/ControlledUserSelector.vue'
import {
  buildChangedNextApproverSelections,
  hasNextApproverPresentation,
  normalizeNextApproverPreview,
  normalizeUserKeys,
  reconcileNextApproverDraftState,
  validateNextApproverDraft
} from '@/shared/next-approver'

const props = defineProps({
  preview: {
    type: Object,
    default: () => ({})
  },
  loading: {
    type: Boolean,
    default: false
  },
  taskId: {
    type: String,
    default: ''
  },
  action: {
    type: String,
    default: ''
  },
  actionLabel: {
    type: String,
    default: ''
  },
  comment: {
    type: String,
    default: ''
  },
  formData: {
    type: Object,
    default: () => ({})
  }
})

const draftMap = ref({})
const touchedNodeIds = ref([])
let previousPreview = normalizeNextApproverPreview()
const normalizedPreview = computed(() =>
  normalizeNextApproverPreview(props.preview)
)
const visibleNodes = computed(() =>
  normalizedPreview.value.nextNodes.filter(node => node.visible)
)
const hasContent = computed(() =>
  hasNextApproverPresentation(normalizedPreview.value, props.loading)
)

watch(
  () => props.preview,
  preview => {
    const reconciled = reconcileNextApproverDraftState(
      previousPreview,
      preview,
      draftMap.value,
      touchedNodeIds.value
    )
    draftMap.value = reconciled.draftMap
    touchedNodeIds.value = reconciled.touchedNodeIds
    previousPreview = normalizeNextApproverPreview(preview)
  },
  { immediate: true }
)

function nodeModelValue(node) {
  const values = normalizeUserKeys(draftMap.value[node.nodeId])
  return node.multiple ? values : (values[0] || '')
}

function updateNodeValue(node, value) {
  const values = normalizeUserKeys(node.multiple
    ? value
    : [value])
  draftMap.value = {
    ...draftMap.value,
    [node.nodeId]: values
  }
  touchedNodeIds.value = normalizeUserKeys([
    ...touchedNodeIds.value,
    node.nodeId
  ])
}

function validate() {
  if (props.loading) {
    return { valid: false, message: '正在计算下一节点审批人，请稍候' }
  }
  return validateNextApproverDraft(normalizedPreview.value, draftMap.value)
}

function getChangedSelections() {
  return buildChangedNextApproverSelections(
    normalizedPreview.value,
    draftMap.value
  )
}

defineExpose({ validate, getChangedSelections })
</script>

<style scoped>
.next-approver-section {
  display: contents;
}

.next-approver-section__loading {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 42px;
  color: var(--el-text-color-secondary);
}

.next-approver-section__readonly {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  min-height: 32px;
  align-items: center;
}

.next-approver-section__empty {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
