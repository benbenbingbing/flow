import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

function source(relativePath) {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8')
}

const panel = source('../ApprovalDecisionPanel.vue')
const dialog = source('../EntityApprovalDialog.vue')
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
