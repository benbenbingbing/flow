<template>
  <VanPopup :show="show" :lazy-render="false" position="bottom" round safe-area-inset-bottom :close-on-click-overlay="!submitting" class="approval-popup" @update:show="$emit('update:show', $event)">
    <VanNavBar title="审批操作" :left-text="submitting ? '' : '取消'" @click-left="!submitting && $emit('update:show', false)" />
    <div class="approval-content">
      <VanField v-if="config.enabled !== false" label="审批结果" label-align="top"><template #input><VanRadioGroup :model-value="action" direction="horizontal" :disabled="submitting" @update:model-value="$emit('update:action', $event)"><VanRadio v-for="option in config.options || []" :key="option.value" :name="option.value">{{ option.label }}</VanRadio></VanRadioGroup></template></VanField>
      <VanField v-if="selectedOption?.showComment !== false" :model-value="comment" :label="config.commentLabel || '审批意见'" type="textarea" rows="3" autosize maxlength="2000" show-word-limit :disabled="submitting" placeholder="请输入审批意见" label-align="top" @update:model-value="$emit('update:comment', $event)" />
      <div v-if="loading" class="next-loading"><VanLoading size="18" />正在确认下一审批节点</div>
      <p v-if="preview.message" class="preview-message" :class="{ blocked: preview.status === 'BLOCKED' }">{{ preview.message }}</p>
      <section v-for="node in visibleNodes" :key="node.nodeId" class="next-node">
        <VanCell :title="node.nodeName" :label="node.assignmentMode === 'MULTI_INSTANCE' ? '多实例办理 · 按所选顺序' : '下一审批人'" :is-link="node.editable" @click="openNode(node)" />
        <ol><li v-for="(key, index) in drafts[node.nodeId] || []" :key="key"><span>{{ userLabel(node, key) }}</span><template v-if="node.editable && node.assignmentMode === 'MULTI_INSTANCE'"><button :disabled="submitting || index === 0" @click="move(node, index, index - 1)">上移</button><button :disabled="submitting || index === drafts[node.nodeId].length - 1" @click="move(node, index, index + 1)">下移</button></template></li></ol>
      </section>
      <p v-if="error" class="preview-message blocked" role="alert">{{ error }}</p>
    </div>
    <div class="approval-submit"><VanButton block type="primary" :loading="submitting" :disabled="loading || preview.status === 'BLOCKED'" @click="$emit('submit')">确认提交</VanButton></div>
    <MobileUserPicker v-model:show="pickerOpen" :model-value="selectedUsers" :multiple="selectedNode?.multiple" :title="`选择${selectedNode?.nodeName || ''}审批人`" value-key="userKey" :identity="pickerIdentity" :load-options="loadCandidates" @confirm="selectUsers" />
  </VanPopup>
</template>
<script setup>
import { computed, ref, watch } from 'vue'
import { Popup as VanPopup, NavBar as VanNavBar, Field as VanField, Radio as VanRadio, RadioGroup as VanRadioGroup, Cell as VanCell, Loading as VanLoading, Button as VanButton } from 'vant'
import { normalizeNextApproverUser, reconcileNextApproverDraftState, buildChangedNextApproverSelections, validateNextApproverDraft, reorderNextApproverValues, createNextApproverPreviewRequestSignature } from '@flow/workflow-core/next-approver'
import MobileUserPicker from '../pickers/MobileUserPicker.vue'
const props = defineProps({ show: Boolean, config: { type: Object, required: true }, action: String, comment: String, preview: { type: Object, required: true }, loading: Boolean, submitting: Boolean, taskId: String, formData: Object, loadOptions: { type: Function, required: true } })
const emit = defineEmits(['update:show', 'update:action', 'update:comment', 'submit'])
const drafts = ref({}), touched = ref([]), labels = ref({}), pickerOpen = ref(false), selectedNode = ref(null), error = ref('')
const selectedOption = computed(() => props.config.options?.find(option => option.value === props.action))
const visibleNodes = computed(() => (props.preview.nextNodes || []).filter(node => node.visible))
const pickerIdentity = computed(() => `${props.preview.scopeKey}:${selectedNode.value?.nodeId}:${createNextApproverPreviewRequestSignature({ taskId: props.taskId, action: props.action, actionLabel: selectedOption.value?.label, formData: props.formData })}`)
const selectedUsers = computed(() => (drafts.value[selectedNode.value?.nodeId] || []).map(userKey => ({ userKey, displayName: userLabel(selectedNode.value, userKey) })))
watch(() => props.preview, (next, previous) => {
  const result = reconcileNextApproverDraftState(previous, next, drafts.value, touched.value)
  drafts.value = result.draftMap; touched.value = result.touchedNodeIds; error.value = ''
  if (previous?.scopeKey !== next.scopeKey) { labels.value = {}; pickerOpen.value = false }
  if (selectedNode.value) selectedNode.value = next.nextNodes?.find(node => node.nodeId === selectedNode.value.nodeId) || null
}, { immediate: true })
function userLabel(node, key) { return labels.value[key] || node?.assignees?.find(user => user.userKey === key)?.displayName || key }
function openNode(node) { if (!node.editable || props.loading || props.submitting) return; selectedNode.value = node; pickerOpen.value = true }
/** 候选查询携带当前表单、动作和服务端范围签名，不能降级为全员选择器。 */
async function loadCandidates(query) {
  if (!selectedNode.value || !props.preview.scopeKey) throw new Error('审批人范围已变化，请刷新预览')
  const result = await props.loadOptions(props.taskId, { ...query, targetNodeId: selectedNode.value.nodeId, scopeKey: props.preview.scopeKey, action: props.action, actionLabel: selectedOption.value?.label, comment: props.comment || '', formData: JSON.parse(JSON.stringify(props.formData || {})) })
  const rows = Array.isArray(result) ? result : result.records || result.list || result.options || []
  return { records: rows.map(normalizeNextApproverUser), total: result.total ?? rows.length }
}
function selectUsers(users) { if (!selectedNode.value) return; const id = selectedNode.value.nodeId; drafts.value[id] = users.map(user => user.userKey); touched.value = [...new Set([...touched.value, id])]; users.forEach(user => { labels.value[user.userKey] = user.displayName }) }
function move(node, from, to) { drafts.value[node.nodeId] = reorderNextApproverValues(drafts.value[node.nodeId], from, to); touched.value = [...new Set([...touched.value, node.nodeId])] }
function validate() { const result = validateNextApproverDraft(props.preview, drafts.value); error.value = result.message; return result }
defineExpose({ validate, getChangedSelections: () => buildChangedNextApproverSelections(props.preview, drafts.value) })
</script>
<style scoped>
.approval-popup { max-height: 88dvh; display: flex; flex-direction: column; }.approval-content { overflow: auto; padding: 0 16px 12px; }.approval-content :deep(.van-field) { padding: 14px 0; }.approval-content :deep(.van-radio) { margin: 6px 18px 6px 0; }.approval-submit { padding: 12px 16px; border-top: 1px solid var(--flow-mobile-border); }.next-loading { display: flex; gap: 8px; align-items: center; color: var(--flow-mobile-muted); font-size: 13px; padding: 16px 0; }.next-node { border-top: 1px solid var(--flow-mobile-border); }.next-node :deep(.van-cell) { padding: 12px 0; }.next-node ol { margin: 0; padding: 0 0 0 20px; font-size: 14px; }.next-node li span { display: inline-block; margin-right: 12px; }.next-node button { border: 0; background: none; color: var(--flow-mobile-accent-text); min-height: 44px; }.next-node button:disabled { opacity: .35; }.preview-message { font-size: 13px; line-height: 1.6; background: var(--flow-mobile-inset); padding: 12px; border-radius: 8px; }.blocked { background: #fff2ed; color: #b14c38; }
</style>
