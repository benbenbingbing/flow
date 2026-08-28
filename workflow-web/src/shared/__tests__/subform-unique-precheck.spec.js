import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

function source(path) {
  return readFileSync(new URL(path, import.meta.url), 'utf8')
}

const rowScope = source(
  '../../components/form-fields/components/SubFormRowRuntime.vue'
)
const subForm = source(
  '../../components/form-fields/components/SubFormField.vue'
)
const legacyRenderer = source('../../components/SubFormRenderer.vue')
const fieldRenderer = source('../../components/FormFieldRendererLinkage.vue')
const nodeRuntime = source('../../components/FormNodeRuntimeItem.vue')
const formPreview = source('../../components/FormPreviewLinkage.vue')

assert.match(
  rowScope,
  /provide\(FORM_UNIQUE_PRECHECK_CONTEXT_KEY/,
  '子表行必须覆盖父表唯一预检 provider'
)
assert.match(
  rowScope,
  /resolveFormUniqueRuntimeIdentity\(\s*props\.form/,
  '子表行必须使用子表 form 身份'
)
assert.match(
  rowScope,
  /recordId:\s*props\.row\?\.id/,
  '子表预检必须排除当前子记录而不是父记录'
)
assert.match(
  rowScope,
  /formData|props\.row/,
  '子表预检必须读取当前子行数据'
)
assert.match(
  rowScope,
  /handleRecordChange\(\s*props\.fields/,
  'CHANGE 和条件字段变化必须由子作用域监听'
)
assert.match(
  rowScope,
  /uniquePrecheckController\.checkAll[\s\S]*?nodeFormRef\.value\?\.validate/,
  '提交时应先强制刷新子表唯一预检，避免被旧 BLUR 结果拦住'
)

assert.match(subForm, /:form="childRuntimeForm"/)
assert.match(subForm, /:row="row"/)
assert.match(subForm, /runtimeReleaseId/)
assert.match(subForm, /runtimeReleaseVersion/)
assert.match(subForm, /releaseResolutionToken/)
assert.match(
  subForm,
  /validationRules:\s*JSON\.stringify\([\s\S]*?rules\.validation/,
  '子发布节点的唯一规则必须进入 controller 使用的 runtimeFields'
)
assert.match(
  subForm,
  /async function validate\(\)[\s\S]*?rowRuntimeRefs[\s\S]*?instance\.validate/,
  '每行校验结果必须向 SubFormField 冒泡'
)
assert.match(subForm, /defineExpose\(\{ validate \}\)/)

assert.match(
  subForm,
  /legacyRowControllers\s*=\s*new Map\(\)/,
  'legacy fields-only 必须为每行保留独立 controller'
)
assert.match(
  subForm,
  /createLegacyRowController[\s\S]*?createFormUniquePrecheckController/,
  'legacy 子行必须创建 headless 唯一预检 controller'
)
assert.match(
  subForm,
  /const form = childFormDefinition\.value \|\| \{\}[\s\S]*?resolveFormUniqueRuntimeIdentity\(\s*form/,
  'legacy 子行必须使用子表发布身份，不能回退父表 formId'
)
assert.match(
  subForm,
  /recordId:\s*currentRow\?\.id[\s\S]*?data:\s*currentRow/,
  'legacy 预检必须携带当前子行 recordId/data'
)
assert.match(
  subForm,
  /syncLegacyRowControllers[\s\S]*?handleRecordChange\(fields, entry\.row\)/,
  'legacy CHANGE 和条件字段必须深度同步到行 controller'
)
assert.match(subForm, /@field-change="handleLegacyFieldChange"/)
assert.match(subForm, /@field-blur="handleLegacyFieldBlur"/)
assert.match(subForm, /:external-field-error="resolveLegacyUniqueError"/)
const legacyValidate = subForm.match(
  /async function validate\(\)[\s\S]*?function mapFieldType/
)?.[0] || ''
assert.ok(
  legacyValidate.indexOf('entry.controller.checkAll')
    < legacyValidate.indexOf('subFormRendererRef.value?.validate'),
  'legacy SUBMIT 必须先强制预检，再执行常规子表校验'
)
assert.match(legacyRenderer, /@change="handleFieldChange/)
assert.match(legacyRenderer, /@blur="handleFieldBlur/)
assert.match(legacyRenderer, /emit\('field-change'/)
assert.match(legacyRenderer, /emit\('field-blur'/)
assert.match(
  legacyRenderer,
  /props\.externalFieldError\(index, field\)/,
  'legacy 预检错误必须回显在对应子行字段'
)

assert.match(fieldRenderer, /ref="fieldComponentRef"/)
assert.match(fieldRenderer, /fieldComponentRef\.value\.validate/)
assert.match(fieldRenderer, /defineExpose\(\{ validate \}\)/)
assert.match(nodeRuntime, /ref="fieldRendererRef"/)
assert.match(
  nodeRuntime,
  /fieldRendererRef\.value\?\.validate[\s\S]*?trigger:\s*'submit'/,
  '父表 Form.validate 必须等待子表校验并可阻止提交'
)
assert.match(
  formPreview,
  /:ref="instance => setFieldRendererRef\(field, instance\)"/,
  'fields-only legacy 父表必须持有复合字段渲染器引用'
)
assert.match(
  formPreview,
  /fieldRendererRefs\.value\[fieldKey\][\s\S]*?renderer\?\.validate\?\.\(\)[\s\S]*?trigger:\s*'submit'/,
  'fields-only legacy 父表提交必须等待子表 validate 并阻止提交'
)
assert.equal(
  formPreview.indexOf('uniquePrecheckController.checkAll(')
    < formPreview.indexOf('formRef.value.validate()'),
  true,
  'legacy 父表必须先刷新自身 SUBMIT 预检，再向子表冒泡'
)

console.log('subform unique precheck scope tests passed')
