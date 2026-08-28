import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import {
  canEvaluateFormUniquenessCondition,
  collectFormUniquenessConditionFields,
  createFormFieldUniquenessRuleId,
  createFormUniquePrecheckController,
  filterFormUniquenessFieldsForNodeScope,
  isFormUniquenessConditionActive,
  normalizeFormFieldUniqueness,
  resolveFormFieldUniqueness,
  supportsFormFieldUniqueness,
  validateFormFieldUniqueness
} from '../form-field-uniqueness.js'
import {
  createFormUniquePrecheckRuntime,
  resolveFormUniqueValidationTrigger
} from '../form-runtime/uniquePrecheckContext.js'

function uniqueField(overrides = {}) {
  return {
    fieldCode: 'name',
    fieldName: '项目名称',
    fieldType: 'STRING',
    validationRules: {
      uniqueness: {
        version: 1,
        ruleId: 'uq_name',
        mode: 'GLOBAL',
        ignoreBlank: true,
        precheck: {
          enabled: true,
          trigger: 'BLUR',
          debounceMs: 500,
          watchConditionFields: true
        }
      }
    },
    ...overrides
  }
}

const normalized = normalizeFormFieldUniqueness({
  mode: 'conditional',
  ruleId: 'r'.repeat(130),
  message: 'm'.repeat(260),
  precheck: {
    enabled: true,
    trigger: 'change',
    debounceMs: 50,
    watchConditionFields: false
  },
  condition: {
    version: 1,
    root: {
      type: 'GROUP',
      logic: 'OR',
      children: [
        { type: 'CONDITION', property: 'status', operator: '==', value: 'ACTIVE' }
      ]
    }
  }
}, 'name')

assert.equal(normalized.mode, 'CONDITIONAL')
assert.equal(normalized.ruleId.length, 100)
assert.equal(normalized.message.length, 200)
assert.equal(normalized.precheck.trigger, 'CHANGE')
assert.equal(normalized.precheck.debounceMs, 200)
assert.equal(normalized.precheck.watchConditionFields, false)
assert.deepEqual(collectFormUniquenessConditionFields(normalized), ['status'])
assert.equal(isFormUniquenessConditionActive(normalized, { status: 'ACTIVE' }), true)
assert.equal(isFormUniquenessConditionActive(normalized, { status: 'DRAFT' }), false)
assert.equal(canEvaluateFormUniquenessCondition(normalized, { name: '项目A' }), false)
assert.equal(canEvaluateFormUniquenessCondition(normalized, { status: 'DRAFT' }), true)

assert.equal(createFormFieldUniquenessRuleId('project-name').length <= 100, true)
assert.equal(createFormFieldUniquenessRuleId('project-name'), 'uq_project_name')
assert.equal(
  validateFormFieldUniqueness({ mode: 'CONDITIONAL' }, 'name').valid,
  false
)
assert.equal(
  validateFormFieldUniqueness({ mode: 'GLOBAL' }, 'name').valid,
  true
)
assert.equal(supportsFormFieldUniqueness(uniqueField()), true)
for (const fieldType of [
  'FILE',
  'IMAGE',
  'MULTI_SELECT',
  'MULTI_REFERENCE',
  'SUB_FORM',
  'SUBLIST',
  'SUB_LIST',
  'REPEATER'
]) {
  assert.equal(
    supportsFormFieldUniqueness(uniqueField({ fieldType })),
    false,
    `${fieldType} 不应开放单值唯一配置`
  )
}
assert.equal(
  supportsFormFieldUniqueness(uniqueField({ componentType: 'cascader' })),
  false
)
assert.equal(resolveFormUniqueValidationTrigger(uniqueField()), 'blur')
assert.equal(
  resolveFormUniqueValidationTrigger(uniqueField({ componentType: 'radio' })),
  'change'
)
assert.equal(
  resolveFormUniqueValidationTrigger(uniqueField({ fieldType: 'BOOLEAN' })),
  'change'
)
for (const fieldType of ['REFERENCE', 'USER', 'DEPT', 'ROLE', 'GROUP']) {
  assert.equal(
    resolveFormUniqueValidationTrigger(uniqueField({ fieldType })),
    'change',
    `${fieldType} 选择控件没有稳定 blur，必须改用 change 预检`
  )
}
assert.equal(
  resolveFormUniqueValidationTrigger(uniqueField({
    fieldType: 'STRING',
    componentType: 'entity_selector'
  })),
  'change'
)

const customRuntimeCalls = []
const customRuntimeErrors = { name: '项目名称已存在' }
const customFormRuntime = createFormUniquePrecheckRuntime({
  controller: {
    check: async (runtimeField, record, options) => {
      customRuntimeCalls.push({ runtimeField, record, options })
      return { available: true, checked: true }
    }
  },
  getFields: () => [uniqueField()],
  getRecord: () => ({ name: '自定义表单项目' }),
  getErrors: () => customRuntimeErrors
})
assert.equal(customFormRuntime.errors, customRuntimeErrors)
await customFormRuntime.onFieldBlur('name')
assert.equal(customRuntimeCalls[0].runtimeField.fieldCode, 'name')
assert.equal(customRuntimeCalls[0].record.name, '自定义表单项目')
assert.equal(customRuntimeCalls[0].options.reason, 'BLUR')
await customFormRuntime.checkField(uniqueField(), 'SUBMIT')
assert.equal(customRuntimeCalls[1].options.reason, 'SUBMIT')
assert.equal(
  (await customFormRuntime.onFieldBlur('missing')).checked,
  false,
  '未知字段不得误发自定义整表单预检请求'
)
assert.equal(
  resolveFormUniqueValidationTrigger(uniqueField({
    fieldType: 'STRING',
    componentType: 'input',
    refEntityType: 'USER'
  })),
  'change'
)
assert.equal(
  resolveFormUniqueValidationTrigger(uniqueField({
    fieldType: 'STRING',
    componentType: 'input',
    refEntityId: 'entity-customer'
  })),
  'change'
)

assert.equal(resolveFormFieldUniqueness({
  fieldCode: 'name',
  validationRules: JSON.stringify({ uniqueness: { mode: 'NONE' } })
}), null)

const tabScopedFields = [
  uniqueField({ id: 'field-name', fieldCode: 'name' }),
  uniqueField({ id: 'field-code', fieldCode: 'code' })
]
const tabScopedNodes = [
  { id: 'tab-a', parentId: '', nodeType: 'TAB' },
  { id: 'tab-b', parentId: '', nodeType: 'TAB' },
  { id: 'node-name', parentId: 'tab-a', bindingRef: 'name', nodeType: 'FIELD' },
  { id: 'node-code', parentId: 'tab-b', bindingRef: 'field-code', nodeType: 'FIELD' }
]
assert.deepEqual(
  filterFormUniquenessFieldsForNodeScope(
    tabScopedFields,
    tabScopedNodes,
    { rootParentId: 'tab-b' }
  ).map(item => item.fieldCode),
  ['code']
)
assert.deepEqual(
  filterFormUniquenessFieldsForNodeScope(
    tabScopedFields,
    tabScopedNodes,
    { excludedNodeIds: ['tab-b'] }
  ).map(item => item.fieldCode),
  ['name']
)

const errors = []
const calls = []
const controller = createFormUniquePrecheckController({
  request: async (formId, payload) => {
    calls.push({ formId, payload })
    return {
      available: payload.formData.name !== '重复项目',
      checked: true,
      fieldCode: payload.fieldCode,
      ruleId: payload.ruleId,
      message: '项目名称在当前状态下已存在'
    }
  },
  getIdentity: () => ({
    formId: 'form-project',
    releaseId: 'release-v2',
    releaseVersion: 2,
    releaseResolutionToken: 'signed-token',
    recordId: 'record-1',
    published: true
  }),
  onErrorsChange: value => errors.push(value)
})

const field = uniqueField()
const conflict = await controller.check(field, { name: '重复项目' }, { reason: 'SUBMIT' })
assert.equal(conflict.available, false)
assert.equal(controller.errorFor('name'), '项目名称在当前状态下已存在')
assert.deepEqual(calls[0], {
  formId: 'form-project',
  payload: {
    releaseId: 'release-v2',
    releaseVersion: 2,
    releaseResolutionToken: 'signed-token',
    ruleId: 'uq_name',
    fieldCode: 'name',
    recordId: 'record-1',
    formData: { name: '重复项目' }
  }
})
await controller.check(field, { name: '可用项目' }, { reason: 'SUBMIT' })
assert.equal(controller.errorFor('name'), '')
assert.equal(errors.length > 0, true)
const callsBeforeSubmitRetry = calls.length
await controller.check(field, { name: '可用项目' }, { reason: 'SUBMIT' })
assert.equal(
  calls.length,
  callsBeforeSubmitRetry + 1,
  '提交前必须刷新预检结果，不能被旧的重复缓存永久阻塞'
)

controller.reset({ name: '重复项目' })
await controller.check(field, { name: '重复项目' }, { reason: 'BLUR' })
assert.equal(controller.errorFor('name'), '项目名称在当前状态下已存在')
const callsBeforeBlurEdit = calls.length
controller.handleRecordChange([field], { name: '已修改项目' })
assert.equal(controller.errorFor('name'), '', '字段变化后必须立即清除旧的失焦重复提示')
assert.equal(calls.length, callsBeforeBlurEdit, 'BLUR 模式的输入变化不应主动发请求')

let unpublishedCalls = 0
const unpublishedController = createFormUniquePrecheckController({
  request: async () => {
    unpublishedCalls += 1
    return { available: false, checked: true }
  },
  getIdentity: () => ({ formId: 'draft-form', published: false })
})
assert.equal((await unpublishedController.check(
  field,
  { name: '重复项目' },
  { reason: 'SUBMIT' }
)).checked, false)
assert.equal(unpublishedCalls, 0, '设计器草稿不能发运行时预检')

const noRuleResult = await controller.check(
  { fieldCode: 'code', fieldType: 'STRING', validationRules: '{}' },
  { code: 'A' },
  { reason: 'SUBMIT' }
)
assert.equal(noRuleResult.checked, false)

const conditionalField = uniqueField({
  validationRules: {
    uniqueness: {
      ...field.validationRules.uniqueness,
      mode: 'CONDITIONAL',
      precheck: {
        enabled: true,
        trigger: 'CHANGE',
        debounceMs: 200,
        watchConditionFields: true
      },
      condition: normalized.condition
    }
  }
})
const callCountBeforeInactive = calls.length
await controller.check(
  conditionalField,
  { name: '重复项目', status: 'DRAFT' },
  { reason: 'SUBMIT' }
)
assert.equal(calls.length, callCountBeforeInactive, '条件不成立时不得请求')

const callCountBeforeMissingCondition = calls.length
await controller.check(
  conditionalField,
  { name: '重复项目' },
  { reason: 'SUBMIT' }
)
assert.equal(
  calls.length,
  callCountBeforeMissingCondition + 1,
  '编辑表单未展示条件字段时必须由服务端合并旧记录后判断'
)

controller.reset()
controller.handleRecordChange(
  [conditionalField],
  { name: '项目A', status: 'DRAFT' }
)
const scheduled = controller.handleRecordChange(
  [conditionalField],
  { name: '项目A', status: 'ACTIVE' }
)
assert.equal(scheduled.length, 1, '开启 watchConditionFields 后条件字段变化也应调度预检')
await Promise.all(scheduled)
assert.equal(calls.at(-1).payload.formData.status, 'ACTIVE')

const deferred = []
const staleErrors = []
const staleController = createFormUniquePrecheckController({
  request: () => new Promise(resolve => deferred.push(resolve)),
  getIdentity: () => ({ formId: 'form-project', releaseId: 'r1', published: true }),
  onErrorsChange: value => staleErrors.push(value)
})
const first = staleController.check(field, { name: '旧值' }, { reason: 'SUBMIT' })
const second = staleController.check(field, { name: '新值' }, { reason: 'SUBMIT' })
deferred[1]({ available: false, checked: true, message: '新值重复' })
await second
deferred[0]({ available: true, checked: true })
const staleResult = await first
assert.equal(staleResult.stale, true)
assert.equal(staleController.errorFor('name'), '新值重复', '过期响应不能覆盖最新错误')
assert.equal(staleErrors.at(-1).name, '新值重复')

const submitDeferred = []
const submitPayloads = []
const currentSubmitRecord = { name: '提交旧值' }
const freshSubmitController = createFormUniquePrecheckController({
  request: (_formId, payload) => {
    submitPayloads.push(payload)
    return new Promise(resolve => submitDeferred.push(resolve))
  },
  getIdentity: () => ({ formId: 'form-project', releaseId: 'r1', published: true })
})
freshSubmitController.reset({ name: '提交旧值' })
const freshSubmit = freshSubmitController.checkAll(
  [field],
  () => currentSubmitRecord
)
await Promise.resolve()
currentSubmitRecord.name = '提交新值'
freshSubmitController.handleRecordChange([field], currentSubmitRecord)
submitDeferred[0]({ available: true, checked: true })
await new Promise(resolve => setTimeout(resolve, 0))
assert.equal(submitDeferred.length, 2, 'SUBMIT 响应过期后必须按当前记录有界重试')
submitDeferred[1]({ available: true, checked: true })
assert.equal((await freshSubmit).valid, true)
assert.deepEqual(
  submitPayloads.map(payload => payload.formData.name),
  ['提交旧值', '提交新值']
)

let changingSubmitCalls = 0
const changingSubmitRecord = { name: '持续变化-0' }
const boundedSubmitController = createFormUniquePrecheckController({
  request: async () => {
    changingSubmitCalls += 1
    changingSubmitRecord.name = `持续变化-${changingSubmitCalls}`
    return { available: true, checked: true }
  },
  getIdentity: () => ({ formId: 'form-project', releaseId: 'r1', published: true })
})
const boundedSubmit = await boundedSubmitController.checkAll(
  [field],
  () => changingSubmitRecord,
  { maxStaleRetries: 1 }
)
assert.equal(boundedSubmit.valid, false, '持续 stale 不得按 available=true 放行')
assert.equal(boundedSubmit.stale, true)
assert.equal(changingSubmitCalls, 2, 'stale 重试必须有上限，避免无限请求')
assert.match(boundedSubmitController.errorFor('name'), /发生变化/)

const designerSource = readFileSync(
  new URL('../../views/EntityFormDesignByEntity.vue', import.meta.url),
  'utf8'
)
const runtimeSource = readFileSync(
  new URL('../../components/FormPreviewLinkage.vue', import.meta.url),
  'utf8'
)
const fieldRendererSource = readFileSync(
  new URL('../../components/FormFieldRendererLinkage.vue', import.meta.url),
  'utf8'
)
const entityDataFormFieldsSource = readFileSync(
  new URL('../../views/entity/components/EntityDataFormFields.vue', import.meta.url),
  'utf8'
)
const customComponentRegistrySource = readFileSync(
  new URL('../../utils/customComponentRegistry.js', import.meta.url),
  'utf8'
)
const apiSource = readFileSync(
  new URL('../../api/entityForm.ts', import.meta.url),
  'utf8'
)
assert.match(designerSource, /title="唯一性"/)
assert.match(designerSource, /value="GLOBAL"/)
assert.match(designerSource, /value="CONDITIONAL"/)
assert.match(designerSource, /value="CHANGE"/)
assert.match(designerSource, /watchConditionFields/)
assert.match(runtimeSource, /createFormUniquePrecheckController/)
assert.match(runtimeSource, /uniquePrecheckController\.handleRecordChange/)
assert.match(runtimeSource, /uniquePrecheckController\.checkAll/)
assert.match(runtimeSource, /formUniqueness:\s*formUniquenessRuntime/)
assert.match(
  entityDataFormFieldsSource,
  /formUniqueness:\s*formUniquenessRuntime/,
  '数据录入自定义整表单必须收到通用唯一预检运行时契约'
)
assert.match(
  entityDataFormFieldsSource,
  /provide\(FORM_UNIQUE_PRECHECK_CONTEXT_KEY, customUniquePrecheckContext\)/,
  '自定义整表单复用标准字段渲染器时也必须继承 BLUR provider'
)
assert.match(customComponentRegistrySource, /formUniqueness\.onFieldBlur/)
assert.match(customComponentRegistrySource, /formUniqueness\.checkField/)
assert.match(
  fieldRendererSource,
  /handleRuntimeChange[\s\S]*?checkChangeOnlyUniqueField\(\)/,
  '无可靠 blur 的复合控件必须在 change 链路显式预检'
)
assert.match(
  fieldRendererSource,
  /rule\.precheck\.trigger !== 'BLUR'[\s\S]*?uniquePrecheckContext\.check\(props\.field, 'BLUR'\)/,
  '映射到 change 的事件仍必须遵循用户配置的 BLUR 规则'
)
assert.equal(
  runtimeSource.indexOf('uniquePrecheckController.checkAll(')
    < runtimeSource.indexOf('customFormRef.value?.validate'),
  true,
  '提交级新鲜预检必须先于可能命中 BLUR 缓存的整表校验'
)
assert.match(apiSource, /\/entity-form\/\$\{formId\}\/unique-precheck/)
assert.match(apiSource, /silentError:\s*true/)

console.log('form field uniqueness tests passed')
