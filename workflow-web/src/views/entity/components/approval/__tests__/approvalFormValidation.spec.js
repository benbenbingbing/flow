import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

function source(relativePath) {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8')
}

const dialog = source('../EntityApprovalDialog.vue')
const basicInfo = source('../EntityApprovalBasicInfo.vue')
const preview = source('../../../../../components/FormPreviewLinkage.vue')

assert.match(
  dialog,
  /validateApprovalForms[\s\S]*?name:\s*tab\.name[\s\S]*?label:\s*tab\.label/,
  '审批多页签校验必须保留页签位置和名称'
)
assert.match(
  dialog,
  /target\.formRef\.validate[\s\S]*?activeDialogTab\.value\s*=\s*target\.name/,
  '校验失败后必须切换到对应表单页签'
)
assert.match(
  dialog,
  /getValidationError\?\.\(\)/,
  '审批弹窗必须读取字段层的具体校验错误'
)
assert.match(
  dialog,
  /ElMessage\.warning\(approvalValidationMessage\(validation\)\)/,
  '审批提交和自定义动作必须展示页签内具体错误'
)
assert.doesNotMatch(
  dialog,
  /ElMessage\.warning\('请先完成表单必填项'\)/,
  '不得将唯一性失败统一误报为必填项'
)

assert.match(basicInfo, /ref="formSectionRef"/)
assert.match(
  basicInfo,
  /await nextTick\(\)[\s\S]*?getValidationError\?\.\(\)[\s\S]*?resolveRenderedValidationError\(\)/,
  '必须优先读取唯一预检语义错误，DOM 只作嵌套/普通校验兜底'
)
assert.match(basicInfo, /\.el-form-item__error/)
assert.match(basicInfo, /\.field-error/)
assert.ok(
  basicInfo.indexOf("'.field-error'")
    < basicInfo.indexOf("'.el-form-item__error'"),
  '嵌套 legacy 子表应优先显示子字段具体唯一错误，不能被父字段泛化错误覆盖'
)
assert.match(
  basicInfo,
  /getValidationError:\s*\(\)\s*=>\s*lastValidationError\.value/,
  '基本信息组件必须向弹窗暴露最新具体错误'
)
assert.match(
  preview,
  /getValidationError:\s*\(\)\s*=>\s*firstUniqueError\.value/,
  'FormPreview 必须暴露当前表单作用域的首个唯一错误'
)

console.log('approval form validation contract tests passed')
