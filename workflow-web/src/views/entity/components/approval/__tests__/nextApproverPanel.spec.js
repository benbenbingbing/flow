import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

function source(relativePath) {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8')
}

const panel = source('../ApprovalDecisionPanel.vue')
const dialog = source('../EntityApprovalDialog.vue')
const entityDataList = source('../../../EntityDataList.vue')
const section = source('../../../../../components/NextApproverSection.vue')
const selector = source('../../../../../components/ControlledUserSelector.vue')
const previewComposable = source(
  '../../../../../composables/useNextApproverPreview.js'
)

assert.ok(
  panel.indexOf('class="approval-opinion-section"')
    < panel.indexOf('<NextApproverSection')
    && panel.indexOf('<NextApproverSection') < panel.indexOf('</el-form>'),
  '下一节点审批人必须位于审批意见的同一个表单区域内'
)
assert.ok(
  panel.indexOf('@update:model-value="emit(\'update:comment\', $event)"')
    < panel.indexOf('<NextApproverSection'),
  '下一节点审批人必须紧跟在审批意见字段之后'
)
assert.doesNotMatch(
  section,
  /next-approver-section__title/,
  '下一节点审批人不应再渲染独立区块标题'
)
assert.doesNotMatch(
  section,
  /next-approver-section__tip|人员来自节点配置的可选范围|人员来自节点配置的受控接口/,
  '下一节点审批人选择框下方不应显示来源说明文字'
)
assert.ok(
  dialog.indexOf('</el-tabs>') < dialog.indexOf('<ApprovalDecisionPanel'),
  '审批决策面板必须位于表单页签之后'
)
assert.match(
  dialog,
  /v-model:expanded="approvalDecisionExpanded"/,
  '审批弹窗必须持有审批信息折叠状态'
)
assert.match(
  dialog,
  /openApprove[\s\S]*?approvalDecisionExpanded\.value\s*=\s*true/,
  '每次打开审批弹窗时必须默认展开审批信息'
)
assert.match(
  dialog,
  /openApprove[\s\S]*?resolveActionableTaskId\(row, 'approve',[\s\S]*?requireActionCapability:\s*options\.requireActionCapability[\s\S]*?if \(!actionableTaskId\)[\s\S]*?刷新列表后重试[\s\S]*?return false[\s\S]*?loadProcessDetail/,
  '实体列表审批能力缺少服务端目标任务时必须在加载流程详情前失败关闭'
)
assert.match(
  entityDataList,
  /openApprove\(row,\s*\{[\s\S]*?requireActionCapability:\s*true[\s\S]*?\}\)/,
  '实体列表审批必须显式要求服务端动作能力，不能回退记录级任务 ID'
)
assert.match(
  dialog,
  /currentTask\.value\s*=\s*\{[\s\S]*?taskId:\s*actionableTaskId/,
  '审批弹窗必须使用服务端授权的 actionableTaskId'
)
assert.doesNotMatch(
  dialog,
  /taskId:\s*row\.currentTaskId\s*\|\|\s*row\.taskId/,
  '审批弹窗不得使用记录级 currentTaskId 猜测当前用户的会签任务'
)
;[
  ':aria-expanded="expanded"',
  'aria-controls="approval-decision-content"',
  "expanded ? '收起审批信息' : '展开审批信息'",
  '<ArrowUp v-if="expanded" />',
  '<ArrowDown v-else />'
].forEach(marker => {
  assert.ok(panel.includes(marker), `审批信息折叠按钮缺少交互或可访问性语义: ${marker}`)
})
assert.match(
  panel,
  /<el-collapse-transition>[\s\S]*?v-show="expanded"[\s\S]*?<NextApproverSection/,
  '折叠审批信息时必须保留审批意见和下一审批人选择状态'
)
assert.match(
  panel,
  /function validate\(\)[\s\S]*?!result\.valid\s*&&\s*!props\.expanded[\s\S]*?emit\('update:expanded', true\)/,
  '折叠状态下校验失败时必须自动展开审批信息'
)
assert.match(
  panel,
  /:comment="comment"/,
  '审批备注必须从决策面板传入下一审批人区域'
)
assert.match(
  section,
  /:comment="comment"/,
  '下一审批人区域必须继续向受控人员选择器传递备注'
)
assert.match(
  selector,
  /comment:\s*props\.comment\s*\?\?\s*''/,
  '候选人员 options 请求必须显式携带审批备注，包括空字符串'
)
assert.match(
  previewComposable,
  /createNextApproverPreviewRequestSignature\(/,
  '预览请求签名必须使用不含审批备注的稳定签名'
)
assert.doesNotMatch(
  previewComposable,
  /comment:\s*payload\.comment/,
  '审批备注不得进入预览请求签名，避免输入意见时反复请求'
)
assert.match(
  previewComposable,
  /createBusinessTraceKey\(\)[\s\S]*?previewNextApproval\([\s\S]*?BUSINESS_TRACE_HEADER[\s\S]*?lastTraceKey\s*=\s*requestTraceKey/,
  '当前预览必须保存与其输入绑定的业务追踪键'
)
assert.match(
  dialog,
  /getNextApproverPreviewTraceKey\(\)[\s\S]*?completeTask\([\s\S]*?BUSINESS_TRACE_HEADER/,
  '正式审批必须复用当前预览的业务追踪键，保证相同输入命中相同无副作用结果'
)
;[
  'entityCode: effectiveEntityCode.value',
  'recordId: entityData.value?.id',
  'formId: approvalActionFormContext.value.formId',
  'formReleaseId: approvalActionFormContext.value.releaseId',
  'formReleaseVersion: approvalActionFormContext.value.releaseVersion',
  'formReleaseResolutionToken:'
].forEach(marker => {
  assert.ok(
    dialog.includes(marker),
    `正式审批缺少表单按钮最终条件复核坐标: ${marker}`
  )
})
assert.match(
  dialog,
  /NEXT_APPROVER_DEFERRED_DEFAULT_REQUIRED[\s\S]*?status:\s*'BLOCKED'/,
  '延迟预览命中无默认人的可编辑节点后必须锁定为配置阻断，防止重复执行正式处理'
)
assert.match(
  section,
  /:ordered="node\.assignmentMode === 'MULTI_INSTANCE'"/,
  '多实例节点必须启用有序人员选择'
)
assert.match(
  selector,
  /多实例参与人顺序[\s\S]*?moveDraftUser\(index, -1\)[\s\S]*?moveDraftUser\(index, 1\)/,
  '多实例选择器必须提供独立于候选表格的显式顺序操作'
)
assert.match(
  selector,
  /createNextApproverOptionsRequestSignature[\s\S]*?optionsRequestGeneration/,
  '候选人员加载必须以请求签名和代次共同防止旧响应覆盖'
)

console.log('next approver panel contract tests passed')
