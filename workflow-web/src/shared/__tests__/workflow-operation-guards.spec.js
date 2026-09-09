import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  isReservedApprovalActionCode,
  isTaskOperationExplicitlyAllowed,
  resolveAllowedAddSignTypes,
  selectAllowedAddSignType,
  validateApprovalOptionActionCodes
} from '../workflow-operation-guards.js'

const allAddSignTypes = ['BEFORE', 'PARALLEL', 'AFTER']

assert.equal(isTaskOperationExplicitlyAllowed({ transfer: true }, 'transfer'), true)
for (const value of [false, undefined, null, 1, 'true']) {
  assert.equal(
    isTaskOperationExplicitlyAllowed({ transfer: value }, 'transfer'),
    false,
    '能力值 ' + String(value) + ' 不得被放大解释为允许'
  )
}

assert.deepEqual(
  resolveAllowedAddSignTypes({
    addSign: true,
    allowedAddSignTypes: ['AFTER', 'before', 'PARALLEL', 'UNKNOWN', 'AFTER']
  }),
  allAddSignTypes,
  '应按稳定展示顺序保留服务端明确允许的三种加签类型'
)
assert.deepEqual(
  resolveAllowedAddSignTypes({
    addSign: true,
    addSignTypes: ['PARALLEL', 'AFTER']
  }),
  ['PARALLEL', 'AFTER'],
  '应兼容现有服务端 addSignTypes 字段'
)
assert.deepEqual(
  resolveAllowedAddSignTypes({
    addSign: true,
    allowedAddSignTypes: [],
    addSignTypes: allAddSignTypes
  }),
  [],
  '显式的新字段空白名单不能被旧字段重新放开'
)
assert.deepEqual(resolveAllowedAddSignTypes({ addSign: true }), [])
assert.deepEqual(resolveAllowedAddSignTypes({
  addSign: false,
  allowedAddSignTypes: allAddSignTypes
}), [])
assert.equal(
  selectAllowedAddSignType({
    addSign: true,
    allowedAddSignTypes: ['PARALLEL', 'AFTER']
  }, 'AFTER'),
  'AFTER'
)
assert.equal(
  selectAllowedAddSignType({
    addSign: true,
    allowedAddSignTypes: ['PARALLEL', 'AFTER']
  }, 'BEFORE'),
  'PARALLEL',
  '当前类型失效时只能回到白名单第一项，并由调用方要求用户重新确认'
)
assert.equal(selectAllowedAddSignType({ addSign: true }, 'BEFORE'), '')

for (const reserved of [
  'transfer',
  ' TRANSFERRED ',
  'addSign',
  'ADD_SIGN_BEFORE',
  'add-sign-parallel',
  'cancelAddSign',
  'terminate',
  'withdraw',
  'manual_cc',
  'cc'
]) {
  assert.equal(
    isReservedApprovalActionCode(reserved),
    true,
    reserved + ' 应视为系统操作保留编码'
  )
}
for (const businessAction of ['approve', 'reject', 'backlog', 'planned', 'return']) {
  assert.equal(
    isReservedApprovalActionCode(businessAction),
    false,
    businessAction + ' 应继续允许作为审批业务结果'
  )
}
assert.equal(validateApprovalOptionActionCodes([
  { value: 'approve' },
  { value: 'business_result' }
]).valid, true)
const reservedValidation = validateApprovalOptionActionCodes([
  { value: 'approve' },
  { value: ' Add-Sign ' },
  { value: 'terminate' }
])
assert.equal(reservedValidation.valid, false)
assert.match(reservedValidation.message, /Add-Sign/)
assert.match(reservedValidation.message, /terminate/)

function source(relativePath) {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8')
}

function functionBlock(content, startMarker, endMarker) {
  const start = content.indexOf(startMarker)
  const end = content.indexOf(endMarker, start + startMarker.length)
  assert.ok(start >= 0 && end > start, '无法定位源码区间 ' + startMarker)
  return content.slice(start, end)
}

const home = source('../../views/Home.vue')
assert.match(
  home,
  /v-for="option in availableAddSignTypeOptions"[\s\S]*?:value="option\.value"/,
  '加签方式必须由服务端白名单映射后的选项渲染'
)
const freshTaskGuard = functionBlock(
  home,
  'async function requireFreshTaskOperation',
  '// 加载已办'
)
assert.ok(freshTaskGuard.includes('await getTaskOperations(taskId)'))
assert.ok(freshTaskGuard.includes('isTaskOperationExplicitlyAllowed(operations, operation)'))

const addSignSubmit = functionBlock(
  home,
  'async function submitAddSign',
  'async function handleCancelAddSign'
)
assert.ok(
  addSignSubmit.indexOf('await requireFreshTaskOperation(')
    < addSignSubmit.indexOf('await addSignTask('),
  '加签提交必须先刷新并确认显式能力'
)
assert.ok(
  addSignSubmit.indexOf('refreshedSelection !== addSignForm.type')
    < addSignSubmit.indexOf('await addSignTask('),
  '加签类型变化时必须在调用接口前阻断'
)

const transferSubmit = functionBlock(
  home,
  'async function submitTransfer',
  '// 终止流程'
)
assert.ok(
  transferSubmit.indexOf('await requireFreshTaskOperation(')
    < transferSubmit.indexOf('await completeTask('),
  '转办提交必须先刷新并确认显式能力'
)

const terminateSubmit = functionBlock(
  home,
  'async function handleTerminate',
  '// 获取状态类型'
)
assert.ok(
  terminateSubmit.indexOf('await requireFreshTerminateOperation(row)')
    < terminateSubmit.indexOf('await terminateProcess('),
  '终止提交必须先刷新并确认服务端 canTerminate'
)

const nodeConfigPanel = source('../../components/NodeConfigPanel.vue')
const applyNodeConfiguration = functionBlock(
  nodeConfigPanel,
  'async function applyNodeConfiguration',
  'async function saveStatusConfig'
)
assert.ok(
  applyNodeConfiguration.indexOf('validateApprovalOptionActionCodes(')
    < applyNodeConfiguration.indexOf('for (const section of getConfigurationSections())'),
  '审批保留码必须在任何配置分区写入画布前预检'
)

const approvalDialog = source(
  '../../views/entity/components/approval/EntityApprovalDialog.vue'
)
assert.match(
  approvalDialog,
  /isEnabled:[\s\S]*?!isReservedApprovalActionCode\(approveForm\.action\)/,
  '历史保留码不得触发下一审批节点预览'
)
const approvalSubmit = functionBlock(
  approvalDialog,
  'const submitApprove = async () =>',
  'defineExpose({'
)
assert.ok(
  approvalSubmit.indexOf('isReservedApprovalActionCode(approveForm.action)')
    < approvalSubmit.indexOf('await completeTask('),
  '历史保留码不得作为普通审批结果提交'
)

console.log('workflow operation guard tests passed')
