<template>
  <section class="detail-page" :class="{ 'has-actions': hasPrimaryActions || workflowMenuItems.length }">
    <VanNavBar title="流程详情" left-arrow @click-left="back">
      <template #right><div ref="moreActionsTarget" class="detail-more-target" /></template>
    </VanNavBar>
    <div v-if="loading" class="detail-loading"><VanLoading>正在加载</VanLoading></div>
    <div v-if="error" class="mobile-error" role="alert">{{ error }}<button v-if="!conflict" @click="load">重试</button><button v-else @click="refreshOperations">刷新任务状态</button></div>
    <template v-if="loaded">
      <div v-if="detail.progressLoading.value" class="detail-loading"><VanLoading>正在加载流程历史</VanLoading></div>
      <div v-if="detail.progressError.value" class="mobile-error" role="alert">{{ detail.progressError.value }}<button @click="detail.loadProcessDetail(route.params.instanceId)">重试</button></div>
      <header class="detail-summary"><div><h1>{{ record.name || snapshot.processName || '流程详情' }}</h1><VanTag plain type="primary" :aria-label="`实体状态：${entityStatusText}`">{{ entityStatusText }}</VanTag></div><p>{{ record.submitterName || snapshot.startUserName || '发起人' }} · {{ formatTime(record.processStartTime || snapshot.startTime) }}</p></header>
      <MobileFormRenderer v-if="form" ref="formRef" v-model="record" v-model:active-tab="activeTab" tabbed class="detail-tabs" :form="form" :entity-fields="entityFields" :readonly="formReadonly" :mode="mode" :context="runtimeContext" :services="formServices" :data-source-runtime="dataSourceRuntime" :actions="formActions" :action-loading-key="pending" @action="handleAction" @error="showFailToast">
        <template #after-tabs>
          <VanTab title="流程进度" name="progress"><main class="detail-content"><MobileProcessProgress :progress="detail.progressData.value" :nodes="diagramNodes" :has-diagram="Boolean(detail.bpmnXml.value)" @diagram="diagramOpen = true" /></main></VanTab>
          <VanTab title="审批历史" name="history"><main class="detail-content"><MobileApprovalHistory :items="detail.processHistory.value" /></main></VanTab>
        </template>
      </MobileFormRenderer>
      <VanTabs v-else v-model:active="activeTab" :lazy-render="false" shrink class="detail-tabs">
        <VanTab title="基本信息" name="basic"><main class="detail-content"><VanEmpty description="此流程未配置可展示的表单" /></main></VanTab>
        <VanTab title="流程进度" name="progress"><main class="detail-content"><MobileProcessProgress :progress="detail.progressData.value" :nodes="diagramNodes" :has-diagram="Boolean(detail.bpmnXml.value)" @diagram="diagramOpen = true" /></main></VanTab>
        <VanTab title="审批历史" name="history"><main class="detail-content"><MobileApprovalHistory :items="detail.processHistory.value" /></main></VanTab>
      </VanTabs>
      <div v-if="ccError" class="mobile-error">{{ ccError }}<button @click="markRead">重新标记已读</button></div>
      <MobileActionBar v-if="footerActions.length || workflowMenuItems.length" :actions="footerActions" :loading-key="pending" @action="handleAction">
        <template v-if="workflowMenuItems.length" #leading>
          <button class="detail-more-button" aria-label="更多操作" :aria-expanded="workflowMenuOpen" :disabled="Boolean(pending)" @click="openWorkflowMenu">
            <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="4" cy="12" r="2" /><circle cx="12" cy="12" r="2" /><circle cx="20" cy="12" r="2" /></svg>
          </button>
        </template>
        <template #more="{ open, disabled, expanded }">
          <Teleport v-if="moreActionsTarget" :to="moreActionsTarget">
            <button class="detail-more-button" aria-label="其他任务操作" :aria-expanded="expanded" :disabled="disabled" @click="open">
              <svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="4" cy="12" r="2" /><circle cx="12" cy="12" r="2" /><circle cx="20" cy="12" r="2" /></svg>
            </button>
          </Teleport>
        </template>
      </MobileActionBar>
      <VanActionSheet teleport="body" v-model:show="workflowMenuOpen"
        :actions="workflowMenuItems.map(item => ({ ...item, name: item.label }))"
        cancel-text="取消" close-on-click-action @select="handleAction" />
      <MobileApprovalPanel ref="approvalRef" v-model:show="approvalOpen" v-model:action="action" v-model:comment="comment" :config="approvalConfig" :preview="preview.preview.value" :loading="preview.loading.value || preview.dirty.value" :submitting="submitting" :task-id="taskId" :form-data="record" :load-options="tasks.getNextApproverOptions" @submit="submitApproval" />
    </template>
    <VanPopup v-model:show="diagramOpen" position="right" :style="{ width: '100%', height: '100%' }"><ProcessDiagram v-if="diagramOpen" :xml="detail.bpmnXml.value" :progress="detail.progressData.value" @close="diagramOpen = false" /></VanPopup>
    <VanPopup v-model:show="operationOpen" position="bottom" round safe-area-inset-bottom :close-on-click-overlay="!submitting">
      <h2 class="mobile-panel-title">{{ operation?.label }}</h2>
      <p v-if="operation?.key === 'return' && hasUnsavedChanges()" class="operation-note">当前未提交的表单修改不会随退回保存</p>
      <VanField v-if="operation?.key === 'return'" label="回退目标" :model-value="selectedReturnTarget?.nodeName || '请选择回退节点'" readonly is-link required @click="returnTargetsOpen = true" />
      <VanField v-if="needsUsers" label="办理人" :model-value="operationUsers.map(user => user.nickname || user.name || user.username).join('、')" readonly is-link @click="operationPicker = true" />
      <VanRadioGroup v-if="operation?.key === 'addSign'" v-model="addSignType" direction="horizontal" class="operation-types"><VanRadio v-for="type in allowedAddSignTypes" :key="type" :name="type">{{ { BEFORE: '前加签', PARALLEL: '并行加签', AFTER: '后加签' }[type] }}</VanRadio></VanRadioGroup>
      <VanField v-model="operationComment" label="说明" type="textarea" rows="3" maxlength="2000" :required="operation?.key === 'return'" placeholder="请填写操作说明" />
      <div class="mobile-panel-actions"><VanButton :disabled="submitting" @click="operationOpen = false">取消</VanButton><VanButton type="primary" :loading="submitting" @click="submitOperation">确认</VanButton></div>
    </VanPopup>
    <VanActionSheet teleport="body" v-model:show="returnTargetsOpen"
      :actions="returnTargets.map(target => ({ ...target, name: target.nodeName }))"
      cancel-text="取消" close-on-click-action @select="target => returnTargetId = target.activityInstanceId" />
    <MobileUserPicker v-model:show="operationPicker" :model-value="operationUsers" :multiple="operation?.key !== 'transfer'" :load-options="loadOperationUsers" :identity="`${taskId}:${operation?.key}`" @confirm="value => operationUsers = value" />
  </section>
</template>
<script setup>
import { computed, defineAsyncComponent, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ActionSheet as VanActionSheet, NavBar as VanNavBar, Tabs as VanTabs, Tab as VanTab, Tag as VanTag, Loading as VanLoading, Empty as VanEmpty, Popup as VanPopup, Button as VanButton, Field as VanField, RadioGroup as VanRadioGroup, Radio as VanRadio, showConfirmDialog, showFailToast, showSuccessToast } from 'vant'
import { MobileFormRenderer, MobileActionBar, MobileApprovalPanel, MobileProcessProgress, MobileApprovalHistory, MobileUserPicker } from '@flow/workflow-mobile-ui'
import { createBusinessTraceKey, BUSINESS_TRACE_HEADER, notifyRequestError } from '@flow/workflow-api'
import { useProcessDetail } from '@flow/workflow-core/vue/useProcessDetail'
import { useNextApproverPreview } from '@flow/workflow-core/vue/useNextApproverPreview'
import { createFormActionRuntime } from '@flow/workflow-core/form-action-runtime'
import { createFormDataSourceRuntime, isRuntimeFormReadonly } from '@flow/workflow-core/form-runtime'
import { footerFormActions } from '@flow/workflow-core/form-actions'
import { resolveEntityStatusLabel } from '@flow/workflow-core/entity-status-runtime'
import { applyRuntimeEventEffects } from '@flow/workflow-core/form-runtime/eventEffects'
import { getApprovalMenuActions, getTodoTaskMoreActions, isTaskClaimable, taskApprovalConflictMessage } from '@flow/workflow-core/workflow-task-actions'
import { isReservedApprovalActionCode, resolveAllowedAddSignTypes } from '@flow/workflow-core/workflow-operation-guards'
import { normalizeNextApproverPreview } from '@flow/workflow-core/next-approver'
import { tasks, forms, entities, request, ui, session } from '../adapters/services.js'
import { formServices } from '../adapters/formServices.js'
import { invalidateInboxes } from '../inbox.js'
const ProcessDiagram = defineAsyncComponent(() => import('../components/ProcessDiagram.vue'))
const router = useRouter(), route = useRoute()
const resubmitTaskId = ref('')
const taskId = computed(() => resubmitTaskId.value || String(route.query.taskId || ''))
const snapshot = ref(history.state.row || {}), detail = useProcessDetail({ request })
const loading = ref(false), loaded = ref(false), error = ref(''), ccError = ref(''), conflict = ref(false), record = ref({}), form = ref(null), entityFields = ref([]), task = ref(null), operations = ref({}), rejected = ref(null), formActions = ref([])
const activeTab = ref('basic'), formRef = ref(), approvalRef = ref(), approvalOpen = ref(false), diagramOpen = ref(false), action = ref('approve'), comment = ref(''), pending = ref(''), submitting = ref(false)
const processOperations = ref({})
const moreActionsTarget = ref(null)
const workflowMenuOpen = ref(false), returnTargetsOpen = ref(false), returnTargets = ref([]), returnTargetId = ref('')
const selectedReturnTarget = computed(() => returnTargets.value.find(target => target.activityInstanceId === returnTargetId.value))
const operation = ref(null), operationOpen = ref(false), operationUsers = ref([]), operationComment = ref(''), operationPicker = ref(false), addSignType = ref('')
let sequence = 0, savedFingerprint = '', completed = false, returnIntent = null
const canApprove = computed(() => Boolean(task.value && String(task.value.status).toLowerCase() === 'todo' && operations.value.approve === true && !conflict.value))
const isStarter = computed(() => [session.userInfo?.username, session.userInfo?.id].filter(Boolean).map(String).includes(String(record.value.submitterId || snapshot.value.startUserId || '')))
const historyResubmit = ref(false)
const canResubmit = computed(() => isStarter.value && rejected.value?.canResubmit === true && rejected.value?.historyReturn !== true && !historyResubmit.value)
const mode = computed(() => canApprove.value ? 'approve' : canResubmit.value ? 'edit' : 'view')
const formReadonly = computed(() => mode.value === 'view' || isRuntimeFormReadonly(form.value))
const entityCode = computed(() => record.value.entityCode || task.value?.entityCode || snapshot.value.entityCode || '')
// 详情标签读取最新实体状态及配置名称，不使用流程进度状态或列表中的旧状态。
const entityStatusText = computed(() => record.value._statusText || resolveEntityStatusLabel(record.value.status) || '—')
const runtimeContext = computed(() => ({ form: form.value, permissions: session.permissions, entityCode: entityCode.value, entityId: form.value?.entityId, recordId: record.value.id, record: record.value, mode: mode.value, taskId: taskId.value, processInstanceId: route.params.instanceId, configHash: form.value?.configHash, runtimeContext: form.value?.runtimeContext, initializationKey: `${route.params.instanceId}:${taskId.value}` }))
const approvalConfig = computed(() => historyResubmit.value
  ? { enabled: true, commentLabel: '重提说明', options: [{ value: 'resubmit', label: '重新提交' }] }
  : detail.approvalConfig.value || { enabled: true, commentLabel: '审批意见', options: [{ value: 'approve', label: '通过' }, { value: 'reject', label: '驳回' }] })
const selectedOption = computed(() => approvalConfig.value.options?.find(option => option.value === action.value))
const allowedAddSignTypes = computed(() => resolveAllowedAddSignTypes(operations.value))
const needsUsers = computed(() => ['transfer', 'addSign', 'cc'].includes(operation.value?.key))
const actionRuntime = createFormActionRuntime({ ...ui, createBusinessTraceKey })
const dataSourceRuntime = createFormDataSourceRuntime({ executeDataSource: ui.uiExtensionRuntimeApi.execute, getRecord: () => record.value, getRecordId: () => record.value.id, getMode: () => mode.value, getForm: () => form.value }).withContext(() => ({ context: runtimeContext.value }))
const preview = useNextApproverPreview({ previewNextApproval: tasks.previewNextApproval, createBusinessTraceKey, traceHeader: BUSINESS_TRACE_HEADER, isEnabled: () => canApprove.value && !isReservedApprovalActionCode(action.value), getTaskId: () => taskId.value, getAction: () => action.value, getActionLabel: () => selectedOption.value?.label, getComment: () => comment.value, getFormData: () => record.value })
watch(() => [action.value, record.value], () => { if (loaded.value) preview.schedule() }, { deep: true })
const footerActions = computed(() => {
  const items = footerFormActions(formActions.value)
    .filter(item => !['close', 'reset', 'save', 'saveAndStart'].includes(item.key) && (item.key !== 'submitApproval' || canApprove.value))
    .map(item => historyResubmit.value && item.key === 'submitApproval' ? { ...item, label: '重新提交' } : item)
  if (canApprove.value) {
    if (historyResubmit.value && !form.value && !items.some(item => item.key === 'submitApproval')) {
      items.push({ key: 'submitApproval', label: '重新提交', primary: true })
    }
    items.push(...getTodoTaskMoreActions({ ...snapshot.value, ...task.value, taskId: taskId.value, taskOperations: operations.value })
      .filter(item => ['claim', 'cancelAddSign'].includes(item.command))
      .map(item => ({ key: item.command, label: item.label })))
  }
  if (canResubmit.value) items.push({ key: 'resubmit', label: '重新提交', primary: true })
  return items
})
const workflowMenuItems = computed(() => getApprovalMenuActions({
  task: task.value ? operations.value : {},
  instance: detail.progressData.value?.status === 'RUNNING' ? processOperations.value : {},
  assigned: !isTaskClaimable({ ...task.value, taskId: taskId.value }) && task.value?.nodeType !== 'ADD_SIGN',
  allowReturn: !form.value || formActions.value.some(item =>
    item.key === 'submitApproval' && item.visible !== false && item.enabled !== false)
}))
const hasPrimaryActions = computed(() => footerActions.value.some(item => item.visible !== false && (item.primary || item.key === 'submitApproval')))
const diagramNodes = computed(() => {
  if (!detail.bpmnXml.value) return []
  const document = new DOMParser().parseFromString(detail.bpmnXml.value, 'text/xml')
  // 进度列表需要区分节点与连线；BPMN 图仍保留全部原始元素。
  const defaultNames = { startEvent: '开始', endEvent: '结束', exclusiveGateway: '条件分支', parallelGateway: '并行分支', inclusiveGateway: '条件分支' }
  return [...document.querySelectorAll('[id]')].map(node => ({ id: node.getAttribute('id'), type: node.localName, name: node.getAttribute('name') || defaultNames[node.localName] || node.getAttribute('id') }))
})
const formatTime = value => value ? String(value).replace('T', ' ').slice(0, 16) : '—'
function actionContext() { return { entityCode: entityCode.value, mode: mode.value, recordId: record.value.id, taskId: canApprove.value ? taskId.value : undefined, canApprove: canApprove.value, formData: record.value, hasProcessInstance: true, permissions: session.permissions } }
async function refreshOperations() {
  const current = sequence
  const result = task.value ? await tasks.getTaskOperations(taskId.value, { silentError: true }).catch(() => ({})) : {}
  if (current !== sequence) return
  operations.value = result || {}
  if (task.value) conflict.value = operations.value.approve !== true
}
async function refreshProcessOperations() {
  const current = sequence
  const result = await tasks.getProcessOperations(route.params.instanceId, { silentError: true }).catch(() => ({}))
  if (current === sequence) processOperations.value = result || {}
}
async function openWorkflowMenu() {
  const current = sequence
  await Promise.all([refreshOperations(), refreshProcessOperations()])
  if (current === sequence && workflowMenuItems.value.length) workflowMenuOpen.value = true
}
function hasUnsavedChanges() {
  return Boolean(savedFingerprint) && (JSON.stringify(record.value) !== savedFingerprint || Boolean(comment.value))
}
/**
 * 补齐代码表绑定等实体字段元数据，供移动端字段投影和选项加载使用。
 * 流程表单 DTO 不保证返回 entityId，因此需沿用 PC 按实体编码读取元数据的路径；
 * 这里只补充实体字段，不重新解析最新表单，保留流程固定的发布版本和字段权限。
 */
async function loadFormEntityFields() {
  if (!form.value) return []
  if (form.value.entityId) return forms.getEntityFields(form.value.entityId)
  if (!entityCode.value) return []
  const entity = await entities.entityApi.getByCode(entityCode.value)
  return entity.fields || []
}
/** 审批入口必须经过任务详情鉴权和服务端能力确认；URL 中的 todo 类型不能授予办理权。 */
async function load() {
  detail.resetProcessDetail()
  activeTab.value = 'basic'
  const current = ++sequence; loading.value = true; loaded.value = false; error.value = ''; conflict.value = false; task.value = null; operations.value = {}; preview.reset()
  resubmitTaskId.value = ''; historyResubmit.value = false; rejected.value = null; processOperations.value = {}
  workflowMenuOpen.value = false; returnTargetsOpen.value = false; operationOpen.value = false; approvalOpen.value = false
  returnIntent = null; comment.value = ''; savedFingerprint = ''; completed = false
  try {
    let authorizedTask = null
    let startedStatus = null
    // 我发起的列表没有待办 ID，使用服务端返回的发起人重提待办，再走任务详情授权。
    if (route.query.kind === 'started') {
      startedStatus = await tasks.checkRejectedStatus(route.params.instanceId, { silentError: true }).catch(() => null)
      if (current !== sequence) return
      if (startedStatus?.historyReturn === true && startedStatus.canResubmit === true) {
        authorizedTask = await tasks.getTaskDetail(startedStatus.taskId, { silentError: true }).catch(() => null)
        if (current !== sequence) return
        if (authorizedTask) resubmitTaskId.value = String(startedStatus.taskId)
      }
    }
    if (route.query.kind === 'todo' && taskId.value) authorizedTask = await tasks.getTaskDetail(taskId.value)
    if (current !== sequence) return
    const success = await detail.loadProcessFormRuntime(route.params.instanceId, {
      taskId: authorizedTask ? taskId.value : '',
      startUserName: snapshot.value.startUserName || '',
      onLoad: value => { snapshot.value = { ...snapshot.value, processName: value.processName } }
    })
    if (!success) throw new Error('流程详情加载失败，请重试')
    if (current !== sequence) return
    record.value = { ...(detail.entityData.value || authorizedTask?.entityData || {}) }
    const source = detail.formConfig.value || authorizedTask?.formConfig
    form.value = source ? { ...source, id: source.id || source.formId || source.entityFormId, configHash: source.configHash } : null
    task.value = authorizedTask?.processTask || null
    if (authorizedTask?.processInstance) snapshot.value = { ...snapshot.value, ...authorizedTask.processInstance }
    // 字段元数据和操作能力相互独立，并行读取以减少移动网络往返；切换记录后不再回填。
    const [taskOps, processOps, rejectedStatus, fields] = await Promise.all([
      task.value ? tasks.getTaskOperations(taskId.value, { silentError: true }).catch(() => ({})) : {},
      tasks.getProcessOperations(route.params.instanceId, { silentError: true }).catch(() => ({})),
      route.query.kind === 'started' ? startedStatus
        : isStarter.value ? tasks.checkRejectedStatus(route.params.instanceId, { silentError: true }).catch(() => null) : null,
      loadFormEntityFields()
    ])
    if (current !== sequence) return
    operations.value = taskOps || {}; processOperations.value = processOps || {}
    rejected.value = rejectedStatus; entityFields.value = fields
    historyResubmit.value = operations.value.historyResubmit === true
    action.value = approvalConfig.value.options?.[0]?.value || 'approve'
    const actions = await actionRuntime.resolveRuntimeFormActions(form.value || {}, actionContext())
    if (current !== sequence) return
    formActions.value = actions
    loaded.value = true; savedFingerprint = JSON.stringify(record.value)
    await nextTick(); await preview.refresh(); await markRead()
  } catch (cause) { if (current === sequence) error.value = cause.message || '加载失败' }
  finally { if (current === sequence) loading.value = false }
}
async function markRead() {
  if (!loaded.value || route.query.kind !== 'cc' || !route.query.ccId) return
  try { await tasks.markCcRead(route.query.ccId); ccError.value = ''; invalidateInboxes(['cc']) }
  catch { ccError.value = '未能标记已读，请重试' }
}
async function validateForm() {
  if (!form.value || !formRef.value) { showFailToast('表单尚未加载，无法提交'); return false }
  if (!(await formRef.value.validate())) { approvalOpen.value = false; await formRef.value.reveal(); showFailToast(formRef.value.getValidationError() || '请检查表单内容'); return false }
  await dataSourceRuntime.prevalidateBeforeSubmit({ form: form.value, fields: form.value.fields || [], nodes: form.value.nodes || [] }); return true
}
/** 校验、预览确认和提交共用一次锁；冲突后保留草稿，不自动重试审批写操作。 */
async function submitApproval() {
  if (submitting.value || !canApprove.value) return
  if (!selectedOption.value || isReservedApprovalActionCode(action.value)) { showFailToast('审批选项配置无效，请联系管理员'); return }
  submitting.value = true; pending.value = 'submitApproval'
  try {
    if ((form.value || !historyResubmit.value) && !(await validateForm())) return
    await preview.ensureCurrent()
    if (!approvalRef.value.validate().valid) return
    const selections = approvalRef.value.getChangedSelections(), current = form.value
    const payload = { taskId: taskId.value, action: action.value, actionLabel: selectedOption.value.label, comment: comment.value, formData: record.value, entityCode: entityCode.value, recordId: record.value.id, formId: current?.id, formConfigHash: current?.configHash }
    if (selections.length) { payload.nextApproverSelections = selections; payload.nextApprovalScopeKey = preview.preview.value.scopeKey }
    const trace = preview.getCurrentTraceKey()
    await tasks.completeTask(payload, { silentError: true, ...(trace ? { headers: { [BUSINESS_TRACE_HEADER]: trace } } : {}) })
    await finish(historyResubmit.value ? '重新提交成功' : '审批成功')
  } catch (cause) {
    if (await formRef.value?.applyServerValidationError(cause)) { approvalOpen.value = false }
    const message = taskApprovalConflictMessage(cause)
    if (message) { conflict.value = true; approvalOpen.value = false; error.value = message.replace('当前弹窗', '当前页面'); await refreshOperations() }
    else if ([cause.errorCode, cause.source?.errorCode, cause.response?.data?.errorCode].includes('NEXT_APPROVAL_SCOPE_CHANGED')) { await preview.refresh(); showFailToast('审批人范围已变化，请重新确认') }
    else if ([cause.errorCode, cause.source?.errorCode, cause.response?.data?.errorCode].includes('NEXT_APPROVER_DEFERRED_DEFAULT_REQUIRED')) { preview.preview.value = normalizeNextApproverPreview({ status: 'BLOCKED', message: cause.message || '下一审批节点未配置可用默认审批人，请联系管理员' }); showFailToast(preview.preview.value.message) }
    else { error.value = cause.message || '审批失败'; showFailToast(error.value) }
  } finally { submitting.value = false; pending.value = '' }
}
async function handleAction(item) {
  if (pending.value || submitting.value || item.enabled === false) return
  if (item.key === 'submitApproval') { approvalOpen.value = true; await preview.ensureCurrent(); return }
  if (item.type === 'custom') {
    const current = sequence
    const isCurrent = () => current === sequence
    try {
      await actionRuntime.runFormAction(item, {
        loadingState: pending,
        isCurrent,
        async confirm(action) {
          try { await showConfirmDialog({ title: action.label, message: action.confirm.message || `确认执行“${action.label}”？` }); return true }
          catch { return false }
        },
        validate: validateForm,
        execute: () => actionRuntime.executeCustomFormAction(item, form.value, actionContext()),
        async applyResult(result) {
          await applyRuntimeEventEffects(result, {
            getRecord: () => record.value,
            setField: (key, value) => { record.value = { ...record.value, [key]: value } },
            isCurrent,
            async confirmOverwrite() {
              try { await showConfirmDialog({ message: '是否覆盖已有字段？' }); return true }
              catch { return false }
            },
            message: effect => (['error', 'warning'].includes(effect.level) ? showFailToast : showSuccessToast)(effect.message),
            async navigate(effect) {
              // 移动路由未注册的 PC 页面不能被通配重定向静默吞掉。
              if (!router.resolve(effect.route).name) throw new Error('该跳转页面暂不支持移动端，请在 PC 端打开')
              await router.push(effect.route)
            },
            async close() { completed = true; invalidateInboxes(); await back() },
            refresh: () => invalidateInboxes(),
            download: effect => showSuccessToast(effect.message || '下载任务已创建')
          })
          if (!isCurrent()) return
          // 显式消息效果已提示时不立即用通用成功提示覆盖它。
          if (result?.message || !result?.effects?.some(effect => ['MESSAGE', 'DOWNLOAD_TASK'].includes(String(effect.type).toUpperCase()))) showSuccessToast(result?.message || '操作成功')
          invalidateInboxes()
          const actions = await actionRuntime.resolveRuntimeFormActions(form.value, actionContext())
          if (isCurrent()) formActions.value = actions
        }
      })
    } catch (cause) { notifyRequestError(cause, showFailToast, '按钮操作执行失败') }
    return
  }
  if (item.key === 'return') {
    const current = sequence
    pending.value = item.key
    try {
      const targets = await tasks.getTaskReturnTargets(taskId.value)
      if (current !== sequence) return
      returnTargets.value = targets || []
      if (!returnTargets.value.length) {
        await refreshOperations()
        showFailToast('当前任务已没有可选回退节点')
        return
      }
    } catch (cause) {
      if (current === sequence) notifyRequestError(cause, showFailToast, '加载回退节点失败')
      return
    } finally { pending.value = '' }
  }
  returnTargetId.value = ''; returnIntent = null
  operation.value = item; operationUsers.value = []; operationComment.value = ''; addSignType.value = allowedAddSignTypes.value[0] || ''; operationOpen.value = true
}
const loadOperationUsers = query => forms.getEntityOptions('USER', query)
async function submitOperation() {
  if (submitting.value || !operation.value) return
  const key = operation.value.key
  if (needsUsers.value && !operationUsers.value.length) { showFailToast('请选择办理人'); return }
  if (key === 'return' && (!selectedReturnTarget.value || !operationComment.value.trim())) {
    showFailToast('请选择回退目标并填写意见')
    return
  }
  const formReference = { formId: form.value?.id, formConfigHash: form.value?.configHash }
  const returnPayload = key === 'return' ? {
    ...formReference,
    targetActivityInstanceId: returnTargetId.value,
    comment: operationComment.value.trim()
  } : null
  const fingerprint = JSON.stringify(returnPayload)
  const replay = returnPayload && returnIntent?.fingerprint === fingerprint
  submitting.value = true; pending.value = key
  try {
    // 能力会随任务状态变化。断线重试保留请求键，以便原待办已完成时仍能读取原结果。
    if (!replay) {
      if (['withdraw', 'terminate'].includes(key)) await refreshProcessOperations()
      else if (key !== 'resubmit') await refreshOperations()
      if (['return', 'withdraw', 'terminate', 'transfer', 'addSign', 'cc'].includes(key)
          && !workflowMenuItems.value.some(item => item.key === key)) {
        throw new Error(key === 'withdraw' ? '当前流程已不允许撤回，请刷新后重试' : '当前已无法执行此操作，请刷新后重试')
      }
    }
    const ids = operationUsers.value.map(user => user.username || user.code || user.id)
    if (returnPayload) {
      if (!replay) returnIntent = { fingerprint, key: createBusinessTraceKey() }
      await tasks.returnTask(taskId.value, { ...returnPayload, requestId: returnIntent.key })
    }
    else if (key === 'claim') await tasks.claimTask(taskId.value)
    else if (key === 'transfer') await tasks.completeTask({ taskId: taskId.value, action: 'transfer', transferTo: ids[0], comment: operationComment.value, ...formReference })
    else if (key === 'cc') await tasks.ccTask(taskId.value, { userIds: ids, comment: operationComment.value })
    else if (key === 'addSign') { if (!resolveAllowedAddSignTypes(operations.value).includes(addSignType.value)) throw new Error('该加签类型已不可用'); await tasks.previewAddSign(taskId.value, ids, addSignType.value); await tasks.addSignTask(taskId.value, { userIds: ids, type: addSignType.value, comment: operationComment.value }) }
    else if (key === 'cancelAddSign') { if (!operations.value.activeAddSign?.id) throw new Error('加签任务已变化'); await tasks.cancelAddSign(operations.value.activeAddSign.id) }
    else if (key === 'withdraw') await tasks.withdrawProcess({ processInstanceId: route.params.instanceId, reason: operationComment.value })
    else if (key === 'terminate') await tasks.terminateProcess(route.params.instanceId, operationComment.value)
    else if (key === 'resubmit') { if (!(await validateForm())) return; await tasks.resubmitProcess(route.params.instanceId, { formData: record.value, comment: operationComment.value }) }
    else throw new Error('该操作暂不支持')
    if (['claim', 'cc', 'addSign', 'cancelAddSign'].includes(key)) { operationOpen.value = false; showSuccessToast('操作成功'); await refreshOperations(); invalidateInboxes() }
    else await finish('操作成功')
  } catch (cause) { showFailToast(cause.message || '操作失败') }
  finally { submitting.value = false; pending.value = '' }
}
async function finish(message) { completed = true; approvalOpen.value = false; operationOpen.value = false; invalidateInboxes(); showSuccessToast(message); await back() }
function back() { if (/^\/(?:m\/)?inbox\//.test(history.state.back || '')) router.back(); else return router.replace(`/inbox/${route.query.kind || 'todo'}`) }
onBeforeRouteLeave(async () => { if (submitting.value && !completed) return false; if (!completed && mode.value !== 'view' && hasUnsavedChanges()) { try { await showConfirmDialog({ title: '离开详情', message: '填写的内容尚未提交，确定离开？' }) } catch { return false } } })
// 表单初次可用后再按页签加载图和历史；查询失败保留表单并允许重试。
watch([activeTab, loaded], ([tab, ready]) => {
  if (ready && ['progress', 'history'].includes(tab)) void detail.loadProcessDetail(route.params.instanceId)
})
watch(() => [route.params.instanceId, route.query.taskId], load, { immediate: true })
onBeforeUnmount(() => { sequence++; detail.resetProcessDetail(); preview.reset() })
</script>
<style scoped>
.detail-more-target { display: flex; align-items: center; }
.detail-more-button { display: flex; align-items: center; justify-content: center; width: 32px; height: 44px; padding: 4px; border: 0; background: transparent; color: var(--flow-mobile-accent-text); cursor: pointer; }
.detail-more-button:disabled { opacity: .5; cursor: default; }
.detail-more-button svg { width: 24px; height: 24px; fill: currentColor; }
.operation-note { margin: 0; padding: 8px 16px; font-size: 13px; color: var(--flow-mobile-muted); }
.detail-page { min-height: 100dvh; background: var(--flow-mobile-surface); }.detail-page.has-actions { padding-bottom: calc(78px + env(safe-area-inset-bottom)); }.detail-loading { padding: 48px 16px; text-align: center; }.detail-summary { padding: 18px 18px 20px; background: var(--flow-mobile-surface); }.detail-summary > div { display: flex; align-items: flex-start; gap: 12px; justify-content: space-between; }.detail-summary h1 { font-size: 18px; line-height: 1.5; font-weight: 600; margin: 0; overflow-wrap: anywhere; }.detail-summary :deep(.van-tag) { flex-shrink: 0; margin-top: 3px; border: 0; background: var(--flow-mobile-accent-soft); color: var(--flow-mobile-accent-text); font-size: 11px; padding: 3px 7px; border-radius: 5px; }.detail-summary :deep(.van-tag:before) { border: 0; }.detail-summary p { font-size: 12px; line-height: 1.7; color: var(--flow-mobile-muted); margin: 8px 0 0; overflow-wrap: anywhere; }.detail-content { padding: 18px 18px 24px; }.detail-tabs :deep(.van-tabs__nav) { margin: 0; padding: 0 18px 12px; background: var(--flow-mobile-surface); border-bottom: 1px solid var(--flow-mobile-border); }.detail-tabs :deep(.van-tabs__wrap) { height: 44px; margin: 0; padding: 0; background: var(--flow-mobile-surface); }.detail-tabs :deep(.van-tab) { flex: 1 0 auto; padding: 0 12px; font-size: 13px; color: var(--flow-mobile-muted); }.detail-tabs :deep(.van-tab--active) { color: var(--flow-mobile-accent-text); font-weight: 600; }.detail-tabs :deep(.van-tabs__line) { width: 24px; height: 3px; bottom: 12px; background: var(--flow-mobile-accent); }.operation-types { padding: 12px 16px; gap: 16px; }
</style>
