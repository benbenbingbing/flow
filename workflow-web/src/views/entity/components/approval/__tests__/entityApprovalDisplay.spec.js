import assert from 'node:assert/strict'
import {
  fileName,
  formatReadonlyValue,
  hasRenderableApprovalForm,
  isFileUrl,
  isGroupedFileValue,
  resolveApprovalFieldLabel,
  resolveApprovalFormConfig
} from '../entityApprovalDisplay.js'

assert.equal(isFileUrl('/api/files/design.pdf'), true)
assert.equal(isFileUrl('https://example.test/design.pdf'), true)
assert.equal(isFileUrl('javascript:alert(1)'), false)
assert.equal(isFileUrl(false), false)

assert.equal(isGroupedFileValue({
  方案附件: ['/api/files/design.pdf'],
  评审记录: '/api/files/review.docx'
}), true)
assert.equal(isGroupedFileValue({
  access_review_required_flag: false
}), false)
assert.equal(isGroupedFileValue({
  项目: { id: 'PRJ-1' }
}), false)

assert.equal(
  fileName('/api/files/%E6%96%B9%E6%A1%88.pdf?download=1'),
  '方案.pdf'
)
assert.equal(formatReadonlyValue(true), '是')
assert.equal(formatReadonlyValue(false), '否')
assert.equal(
  formatReadonlyValue({ id: 'PRJ-1' }),
  '{"id":"PRJ-1"}'
)

const defaultForm = {
  customComponent: 'ProjectMemberChangeForm',
  fields: []
}
assert.equal(hasRenderableApprovalForm(defaultForm), true)
assert.equal(
  resolveApprovalFormConfig({ fields: [] }, defaultForm),
  defaultForm
)
const nodeForm = {
  formKey: 'pmo_review',
  fields: [{ fieldCode: 'change_reason' }]
}
assert.equal(resolveApprovalFormConfig(nodeForm, defaultForm), nodeForm)
assert.equal(
  resolveApprovalFieldLabel('target_user_id', [{
    fieldCode: 'target_user_id',
    fieldName: '目标人员'
  }]),
  '目标人员'
)
assert.equal(
  resolveApprovalFieldLabel('unknown_field', []),
  'unknown_field'
)

console.log('entity approval display tests passed')
