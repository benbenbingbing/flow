<template>
  <el-dropdown v-if="items.length" trigger="click" placement="top-start"
    @visible-change="visible => { if (visible) refresh() }" @command="openOperation">
    <el-button aria-label="更多操作" :icon="MoreFilled" :disabled="submitting" />
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item v-for="item in items" :key="item.key" :command="item.key">
          {{ item.label }}
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>

  <el-dialog v-if="operation" v-model="dialogOpen" :title="operation.label" width="440px"
    append-to-body :close-on-click-modal="false" :close-on-press-escape="!submitting" :show-close="!submitting">
    <el-form label-position="top">
      <el-alert v-if="operation.key === 'return' && dirty" title="当前未提交的表单修改不会随退回保存"
        type="warning" :closable="false" show-icon class="operation-message" />
      <el-alert v-if="['withdraw', 'terminate'].includes(operation.key)"
        :title="`确认${operation.label}当前流程？`" type="warning" :closable="false"
        show-icon class="operation-message" />
      <el-form-item v-if="operation.key === 'return'" label="回退目标" required>
        <el-select v-model="targetId" filterable placeholder="请选择回退节点">
          <el-option v-for="target in targets" :key="target.activityInstanceId"
            :label="target.nodeName" :value="target.activityInstanceId" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="operation.key === 'transfer'" label="接手人" required>
        <UserSelector v-model="transferTo" value-key="code" title="选择接手人" />
      </el-form-item>
      <el-form-item v-if="['cc', 'addSign'].includes(operation.key)" label="人员" required>
        <UserSelector v-model="userIds" multiple value-key="code" />
      </el-form-item>
      <el-form-item v-if="operation.key === 'addSign'" label="加签方式" required>
        <el-radio-group v-model="addSignType">
          <el-radio v-for="type in addSignOptions" :key="type.value" :value="type.value">
            {{ type.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="意见" :required="operation.key === 'return'">
        <el-input v-model="comment" type="textarea" :rows="3" maxlength="2000" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="dialogOpen = false">取消</el-button>
      <el-button :type="operation.key === 'terminate' ? 'danger' : 'primary'"
        :loading="submitting" @click="submit">确认{{ operation.label }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { MoreFilled } from '@element-plus/icons-vue'
import { createBusinessTraceKey } from '@flow/workflow-api'
import { getApprovalMenuActions, isTaskClaimable } from '@flow/workflow-core/workflow-task-actions'
import { ADD_SIGN_TYPE_OPTIONS, resolveAllowedAddSignTypes } from '@flow/workflow-core/workflow-operation-guards'
import {
  addSignTask, ccTask, completeTask, getProcessOperations, getTaskOperations,
  getTaskReturnTargets, previewAddSign, returnTask, terminateProcess, withdrawProcess
} from '@/api/processTask'
import { showRequestError } from '@/shared/request'
import UserSelector from '@/components/UserSelector.vue'

const props = defineProps({
  task: { type: Object, default: null },
  instanceId: { type: String, default: '' },
  active: Boolean,
  allowReturn: Boolean,
  dirty: Boolean,
  formReference: { type: Object, default: () => ({}) }
})
const emit = defineEmits(['completed', 'history-resubmit'])
const taskOperations = ref({})
const instanceOperations = ref({})
const operation = ref(null)
const dialogOpen = ref(false)
const targets = ref([])
const targetId = ref('')
const transferTo = ref('')
const userIds = ref([])
const comment = ref('')
const addSignType = ref('')
const submitting = ref(false)
let revision = 0
let dialogSession = 0
let returnIntent = null

const items = computed(() => getApprovalMenuActions({
  task: taskOperations.value,
  instance: instanceOperations.value,
  allowReturn: props.allowReturn,
  assigned: !isTaskClaimable(props.task || {}) && props.task?.nodeType !== 'ADD_SIGN'
}))
const addSignOptions = computed(() => ADD_SIGN_TYPE_OPTIONS
  .filter(option => resolveAllowedAddSignTypes(taskOperations.value).includes(option.value)))

/** 两类能力独立读取。实例发起人无需对当前任务有读取权限。 */
async function refresh() {
  const current = ++revision
  const taskId = props.task?.taskId
  const options = { silentError: true }
  const [task, instance] = await Promise.all([
    props.active && taskId ? getTaskOperations(taskId, options).catch(() => ({})) : {},
    props.active && props.instanceId ? getProcessOperations(props.instanceId, options).catch(() => ({})) : {}
  ])
  if (current !== revision) return false
  taskOperations.value = task || {}
  instanceOperations.value = instance || {}
  emit('history-resubmit', taskOperations.value.historyResubmit === true)
  return true
}

watch(() => [props.active, props.task?.taskId, props.instanceId], () => {
  dialogSession++
  taskOperations.value = {}
  instanceOperations.value = {}
  dialogOpen.value = false
  emit('history-resubmit', false)
  void refresh()
}, { immediate: true })

async function openOperation(key) {
  if (!await refresh()) return
  const item = items.value.find(action => action.key === key)
  if (!item) return
  try {
    const current = revision
    targets.value = key === 'return' ? await getTaskReturnTargets(props.task.taskId) : []
    if (current !== revision) return
    if (key === 'return' && !targets.value.length) {
      await refresh()
      return
    }
    operation.value = item
    targetId.value = ''
    userIds.value = []
    transferTo.value = ''
    addSignType.value = addSignOptions.value[0]?.value || ''
    comment.value = ''
    returnIntent = null
    dialogOpen.value = true
  } catch (error) {
    showRequestError(error, '获取流程操作失败')
  }
}

async function submit() {
  if (submitting.value || !operation.value) return
  const { key, label } = operation.value
  if (key === 'return' && (!targetId.value || !comment.value.trim())) {
    ElMessage.warning('请选择回退目标并填写意见')
    return
  }
  if (key === 'transfer' && !transferTo.value || ['cc', 'addSign'].includes(key) && !userIds.value.length) {
    ElMessage.warning('请选择人员')
    return
  }
  const taskId = props.task?.taskId
  const instanceId = props.instanceId
  const currentSession = dialogSession
  const returnPayload = key === 'return' ? {
    targetActivityInstanceId: targetId.value,
    comment: comment.value.trim(),
    formId: props.formReference.formId,
    formConfigHash: props.formReference.configHash
  } : null
  const fingerprint = JSON.stringify(returnPayload)
  const replay = returnPayload && returnIntent?.fingerprint === fingerprint
  submitting.value = true
  try {
    // 断线重试可能发生在原待办已完成之后，使用同一请求键让服务端返回原结果。
    if (!replay) {
      if (!await refresh() || !dialogOpen.value) return
      if (!items.value.some(item => item.key === key)) throw new Error(`当前已无法${label}`)
    }
    if (returnPayload) {
      if (!replay) returnIntent = { fingerprint, key: createBusinessTraceKey() }
      await returnTask(taskId, { ...returnPayload, requestId: returnIntent.key })
    } else if (key === 'withdraw') {
      await withdrawProcess({ processInstanceId: instanceId, reason: comment.value })
    } else if (key === 'terminate') {
      await terminateProcess(instanceId, comment.value)
    } else if (key === 'cc') {
      await ccTask(taskId, { userIds: userIds.value, comment: comment.value })
    } else if (key === 'transfer') {
      await completeTask({ taskId, action: 'transfer', transferTo: transferTo.value, comment: comment.value,
        formId: props.formReference.formId, formConfigHash: props.formReference.configHash })
    } else if (key === 'addSign') {
      if (!addSignOptions.value.some(option => option.value === addSignType.value)) throw new Error('此加签类型已不可用')
      const preview = await previewAddSign(taskId, userIds.value, addSignType.value)
      if (!preview?.users?.length) throw new Error('没有可加签的人员')
      await addSignTask(taskId, { userIds: userIds.value, type: addSignType.value, comment: comment.value })
    }
    if (currentSession !== dialogSession) return
    dialogOpen.value = false
    ElMessage.success(`${label}成功`)
    emit('completed', key)
    if (key === 'cc' || key === 'addSign') void refresh()
  } catch (error) {
    if (currentSession !== dialogSession) return
    showRequestError(error, `${label}失败`)
    if (key === 'return') void refresh()
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.operation-message { margin-bottom: 12px; }
</style>
