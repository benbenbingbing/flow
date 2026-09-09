import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { stripTypeScriptTypes } from 'node:module'
import {
  getTodoTaskMoreActions,
  isTaskClaimable,
  taskApprovalConflictMessage
} from '../workflow-task-actions.js'

const task = { taskId: 'task-1', canClaim: true }
assert.equal(isTaskClaimable(task), true)
assert.equal(isTaskClaimable({ taskId: 'old-task', claimRequired: true }), true)
for (const canClaim of [false, null, undefined, 'true', 1]) {
  assert.equal(isTaskClaimable({ ...task, canClaim, claimRequired: true }), false,
    '新版能力存在时，非法值或显式拒绝不能被旧字段重新放开')
}
assert.equal(isTaskClaimable({ canClaim: true }), false)
assert.equal(isTaskClaimable({ ...task, nodeType: 'ADD_SIGN' }), false)
assert.equal(isTaskClaimable(null), false)
assert.deepEqual(getTodoTaskMoreActions(task), [{ command: 'claim', label: '认领任务' }])
assert.deepEqual(getTodoTaskMoreActions({ ...task, slaStatus: 'NORMAL' }), [
  { command: 'claim', label: '认领任务' },
  { command: 'sla', label: 'SLA', divided: true }
])
assert.deepEqual(getTodoTaskMoreActions({
  taskId: 'assigned-task',
  canClaim: false,
  claimRequired: true,
  taskOperations: { transfer: true, addSign: true, addSignTypes: ['BEFORE'] }
}).map(action => action.command), ['transfer', 'addSign', 'cc'])
assert.deepEqual(getTodoTaskMoreActions({ ...task, nodeType: 'ADD_SIGN' }), [])

for (const errorCode of ['TASK_ALREADY_CLAIMED', 'TASK_ALREADY_COMPLETED', 'TASK_STATE_CHANGED']) {
  for (const error of [
    { errorCode },
    { source: { errorCode } },
    { response: { data: { errorCode } } }
  ]) {
    assert.match(taskApprovalConflictMessage(error), /仍保留在当前弹窗/)
  }
}
assert.equal(taskApprovalConflictMessage({ errorCode: 'FORBIDDEN', message: '无权操作' }), '')
assert.equal(taskApprovalConflictMessage({ status: 409, message: '表单校验失败' }), '')

const home = readFileSync(new URL('../../views/Home.vue', import.meta.url), 'utf8')
const dialog = readFileSync(new URL(
  '../../views/entity/components/approval/EntityApprovalDialog.vue', import.meta.url
), 'utf8')

/** 运行真实组件方法并注入接口替身，验证状态变化和调用结果，不发起网络请求。 */
function componentMethod(source, start, end, name, dependencies, typescript = false) {
  const startIndex = source.indexOf(start)
  const endIndex = source.indexOf(end, startIndex + start.length)
  assert.ok(startIndex >= 0 && endIndex > startIndex, `无法定位 ${name}`)
  let code = source.slice(startIndex, endIndex)
  if (typescript) code = stripTypeScriptTypes(code)
  return new Function(...Object.keys(dependencies), `${code}\nreturn ${name}`)(
    ...Object.values(dependencies)
  )
}

const operationsTemplate = home.slice(
  home.indexOf('<div class="todo-operation-cell">'),
  home.indexOf('</el-table>', home.indexOf('<div class="todo-operation-cell">'))
)
assert.match(operationsTemplate, /@click="handleApprove\(row\)"[\s\S]*?审批/)
assert.doesNotMatch(operationsTemplate, /v-if="row\.claimRequired"|@click="handleClaim/)
assert.match(home, /:selectable="isTaskClaimable"/)
assert.doesNotMatch(home, /row\.claimRequired/, '展示及批量选择都必须使用统一兼容规则')
assert.doesNotMatch(dialog, /\bclaimTask\s*\(/, '打开、关闭与提交弹窗不得单独调用认领接口')
assert.match(dialog.slice(dialog.indexOf('const openApprove = async'), dialog.indexOf('interface OpenViewOptions')),
  /approvalConflictMessage\.value = ''/, '打开另一个任务时必须清理前一任务的冲突提示')

let openedTask
const handleApprove = componentMethod(home,
  'function handleApprove(row)', 'async function handleClaim(row)', 'handleApprove', {
    approvalDialogRef: { value: { openApprove: row => { openedTask = row } } }
  })
handleApprove(task)
assert.equal(openedTask, task, '候选任务应直接进入审批，不因未认领被拦截')

const claimCalls = []
const messages = []
let listRefreshes = 0
let statisticsRefreshes = 0
const claimingTaskId = { value: '' }
const handleClaim = componentMethod(home,
  'async function handleClaim(row)', '// 查看进度', 'handleClaim', {
    isTaskClaimable,
    claimingTaskId,
    claimTask: async id => { claimCalls.push(id) },
    ElMessage: { success: message => messages.push(message) },
    loadTodoList: async () => { listRefreshes++ },
    loadStatistics: async () => { statisticsRefreshes++ },
    console
  })
await handleClaim(task)
assert.deepEqual(claimCalls, ['task-1'])
assert.match(messages[0], /已由你接手/)
assert.equal(listRefreshes, 1, '手动认领后刷新列表，继续停留在待办')
assert.equal(statisticsRefreshes, 1)
assert.equal(claimingTaskId.value, '')
await handleClaim({ ...task, canClaim: false, claimRequired: true })
assert.equal(claimCalls.length, 1, '服务端显式关闭认领后不能被旧字段绕过')

const batchCalls = []
const selectedTodoRows = { value: [
  task,
  { taskId: 'legacy-task', claimRequired: true },
  { taskId: 'assigned-task', canClaim: false, claimRequired: true }
] }
const bulkClaimLoading = { value: false }
const batchWarnings = []
const batchClaim = componentMethod(home,
  'async function handleBatchClaim()', 'function getTodoMoreActions(row)', 'handleBatchClaim', {
    isTaskClaimable,
    selectedTodoRows,
    bulkClaimLoading,
    loadedTabs: { todo: true },
    ElMessageBox: { confirm: async () => {} },
    ElMessage: { success: () => {}, warning: message => batchWarnings.push(message) },
    claimTask: async id => {
      batchCalls.push(id)
      if (id === 'legacy-task') throw new Error('已被其他人认领')
    },
    loadTodoList: async () => { listRefreshes++ },
    loadStatistics: async () => { statisticsRefreshes++ }
  })
await batchClaim()
assert.deepEqual(batchCalls, ['task-1', 'legacy-task'])
assert.match(batchWarnings[0], /已认领 1 个任务，1 个任务认领失败/)
assert.deepEqual(selectedTodoRows.value, [])
assert.equal(bulkClaimLoading.value, false)

/** 最终提交使用真实组件方法，覆盖成功以及并发/无权失败时保留用户输入的行为。 */
async function submitApproval(error) {
  const entityData = { value: { amount: 42, detail: { note: '未提交的修改' } } }
  const approveForm = { action: 'approve', comment: '已填写的审批意见' }
  const processDialogVisible = { value: true }
  const approveSubmitLoading = { value: false }
  const approvalConflictMessage = { value: '' }
  const calls = []
  const events = []
  const errors = []
  const dependencies = {
    currentTask: { value: task },
    approveSubmitLoading,
    approveForm,
    entityData,
    processDialogVisible,
    approvalConflictMessage,
    taskApprovalConflictMessage,
    isReservedApprovalActionCode: () => false,
    validateApprovalForms: async () => ({ valid: true }),
    dataSourceRuntime: { prevalidateBeforeSubmit: async () => {} },
    approvalNormalForm: { value: null },
    ensureNextApproverPreviewCurrent: async () => {},
    approvalDecisionRef: { value: null },
    selectedApprovalOption: { value: { label: '通过' } },
    getNextApproverPreviewTraceKey: () => '',
    completeTask: async payload => {
      calls.push(payload)
      if (error) throw error
    },
    ElMessage: { success: () => {}, warning: () => {}, error: message => errors.push(message) },
    emit: event => events.push(event),
    isDeferredDefaultRequired: () => false,
    isNextApprovalScopeChanged: () => false,
    console: { error: () => {} }
  }
  const submit = componentMethod(dialog,
    'const submitApprove = async () =>', 'defineExpose({', 'submitApprove', dependencies, true)
  await submit()
  assert.equal(calls.length, 1, '候选任务直接提交一次审批，认领由后端与审批原子处理')
  assert.equal(approveSubmitLoading.value, false)
  assert.equal(approveForm.comment, '已填写的审批意见')
  assert.deepEqual(entityData.value, { amount: 42, detail: { note: '未提交的修改' } })
  if (error) {
    assert.equal(processDialogVisible.value, true, '失败后不得关闭弹窗')
    assert.deepEqual(events, [], '失败后不得触发成功刷新覆盖当前表单')
    if (taskApprovalConflictMessage(error)) {
      assert.match(approvalConflictMessage.value, /仍保留在当前弹窗/)
    } else {
      assert.deepEqual(errors, [error.message], '无权及其他错误应保留服务端原消息')
    }
  } else {
    assert.equal(processDialogVisible.value, false)
    assert.deepEqual(events, ['success'])
  }
}

await submitApproval()
for (const errorCode of ['TASK_ALREADY_CLAIMED', 'TASK_ALREADY_COMPLETED', 'TASK_STATE_CHANGED']) {
  await submitApproval({ errorCode })
}
await submitApproval({ errorCode: 'FORBIDDEN', message: '当前用户无权审批' })

console.log('workflow task action behavior tests passed')
