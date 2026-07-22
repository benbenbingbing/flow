import assert from 'node:assert/strict'

import {
  createFormDataSourceRuntime,
  getClientBeforeSubmitBindings,
  getFieldKey,
  getFieldModelPath,
  isClientPrevalidationBinding,
  isSystemField,
  isRuntimeFieldReadonly,
  isRuntimeFormReadonly,
  mergeRuntimeFormConfigs,
  normalizeRuntimeFormConfigs
} from '@/shared/form-runtime'
import {
  formatDateValue,
  formatListFieldValue,
  getCellValue,
  parseDataSourceConfig,
  parseJsonOptions
} from '@/shared/list-runtime'
import {
  canExecuteAction,
  getActionCapabilityReason,
  getSelectionActionState,
  hasButtonPermission,
  isActionVisible
} from '@/utils/listButtonPermission.js'
import {
  applySchemaDefaults,
  buildRuntimeFieldRules,
  getFieldModeAccess,
  isFieldReadonlyForMode,
  isFieldVisibleForMode,
  safeParseConfig,
  stringifyConfig
} from '@/shared/config-runtime'
import {
  normalizeApiResponse,
  normalizePageResult,
  toPageParams,
  API_SUCCESS_CODES,
  BUSINESS_TRACE_HEADER,
  ensureBusinessTraceHeader,
  getApiErrorMessage
} from '@/shared/request'

const optionField = {
  fieldCode: 'priority',
  fieldType: 'SELECT',
  optionsJson: JSON.stringify([
    { label: '高', value: 'HIGH' },
    { label: '低', value: 'LOW' }
  ])
}
assert.equal(formatListFieldValue({ data: { priority: 'HIGH' } }, optionField), '高')

const multiOptionField = {
  fieldCode: 'tags',
  fieldType: 'MULTI_SELECT',
  optionsJson: JSON.stringify([
    { label: '研发', value: 'dev' },
    { label: '测试', value: 'qa' }
  ])
}
assert.equal(formatListFieldValue({ data: { tags: ['dev', 'qa'] } }, multiOptionField), '研发, 测试')
assert.equal(formatListFieldValue({ data: { tags: '["dev","qa"]' } }, multiOptionField), '研发, 测试')
assert.equal(formatListFieldValue({ data: { tags: 'dev,qa' } }, multiOptionField), '研发, 测试')

const refField = { fieldCode: 'owner', fieldType: 'USER', refEntityType: 'USER', refEntityId: '' }
assert.equal(formatListFieldValue({ data: { owner: 'u1' } }, refField, { 'USER::u1': '张三' }), '张三')

const multiRefField = { fieldCode: 'reviewers', fieldType: 'MULTI_REFERENCE', refEntityType: 'USER', refEntityId: '' }
assert.equal(formatListFieldValue({ data: { reviewers: '["u1","u2"]' } }, multiRefField, { 'USER::u1': '张三', 'USER::u2': '李四' }), '张三, 李四')

assert.equal(formatListFieldValue({ data: { lines: [{ id: 1 }, { id: 2 }] } }, { fieldCode: 'lines', fieldType: 'SUB_FORM' }), '2 行')
assert.equal(formatListFieldValue({ status: 'DRAFT' }, { fieldCode: 'status' }), 'DRAFT')
assert.equal(formatListFieldValue({ data: { missing: null } }, { fieldCode: 'missing' }), '-')

assert.deepEqual(parseJsonOptions('[{"label":"是","value":true}]'), [{ label: '是', value: true }])
assert.deepEqual(parseJsonOptions('{bad json'), [])
assert.deepEqual(parseDataSourceConfig('{"url":"/api/demo"}'), { url: '/api/demo' })
assert.deepEqual(parseDataSourceConfig('{bad json'), {})

assert.equal(getCellValue({ extData: { a: 1 }, data: { a: 2 }, a: 3 }, { fieldCode: 'a' }), 1)
assert.equal(getCellValue({ data: { a: 2 }, a: 3 }, { fieldCode: 'a' }), 2)
assert.equal(getCellValue({ a: 3 }, { fieldCode: 'a' }), 3)

assert.equal(isSystemField('createdAt'), true)
assert.equal(isSystemField('customName'), false)
assert.equal(getFieldModelPath('createdAt'), 'createdAt')
assert.equal(getFieldModelPath('customName'), 'data.customName')
assert.equal(getFieldKey({ fieldKey: 'fallbackKey' }), 'fallbackKey')
assert.equal(isRuntimeFormReadonly({ isReadonly: true }), true)
assert.equal(isRuntimeFormReadonly({ isReadonly: 0 }), false)
assert.equal(isRuntimeFieldReadonly({ isReadonly: 1 }), true)
assert.equal(isRuntimeFieldReadonly({ isReadonly: 0 }, true), true)

const configA = { fields: [{ fieldCode: 'name', formId: 'old' }, { fieldCode: 'amount' }], buttons: [{ key: 'save' }] }
const configB = { fields: [{ fieldCode: 'name', formId: 'new' }, { fieldCode: 'remark' }], buttons: [{ key: 'submit' }] }
assert.deepEqual(normalizeRuntimeFormConfigs({ formConfig: configA }), [configA])
assert.deepEqual(normalizeRuntimeFormConfigs({ formConfigs: [configA, configB] }), [configA, configB])
assert.deepEqual(mergeRuntimeFormConfigs([configA, configB]).fields.map((field) => field.fieldCode), ['name', 'amount', 'remark'])
assert.deepEqual(mergeRuntimeFormConfigs([configA, configB]).buttons.map((button) => button.key), ['save', 'submit'])

assert.equal(hasButtonPermission({ perm: 'entity:add' }, ['entity:add']), true)
assert.equal(hasButtonPermission({ perm: 'entity:add' }, ['entity:view']), false)
assert.equal(hasButtonPermission({}, []), true)

const allowedActionRow = {
  actionCapabilities: {
    delete: { visible: true, enabled: true, reason: '' }
  }
}
const hiddenActionRow = {
  actionCapabilities: {
    delete: { visible: false, enabled: false, reason: '仅本人草稿可以删除' }
  }
}
assert.equal(isActionVisible(allowedActionRow, 'delete'), true)
assert.equal(canExecuteAction(allowedActionRow, 'delete'), true)
assert.equal(isActionVisible(hiddenActionRow, 'delete'), false)
assert.equal(canExecuteAction(hiddenActionRow, 'delete'), false)
assert.equal(getActionCapabilityReason(hiddenActionRow, 'delete'), '仅本人草稿可以删除')
assert.deepEqual(getSelectionActionState([], 'batchDelete'), {
  enabled: false,
  reason: '请先选择数据'
})
assert.equal(getSelectionActionState([allowedActionRow], 'delete').enabled, true)
assert.equal(getSelectionActionState([allowedActionRow, hiddenActionRow], 'delete').enabled, false)

assert.deepEqual(toPageParams({ currentPage: 3, size: 20 }), { pageNum: 3, pageSize: 20 })
assert.deepEqual(normalizePageResult({ records: [{ id: 1 }], total: 1, current: 2, size: 10 }).list, [{ id: 1 }])
assert.deepEqual(normalizeApiResponse({ code: 0, data: { rows: [{ id: 2 }], count: 1 } }).list, [{ id: 2 }])
assert.equal(API_SUCCESS_CODES.has('200'), true)
assert.equal(getApiErrorMessage({ msg: '失败' }), '失败')
const mutationRequest = ensureBusinessTraceHeader({ method: 'post', headers: {} })
assert.match(mutationRequest.headers[BUSINESS_TRACE_HEADER], /^ui_/)
const existingTraceRequest = ensureBusinessTraceHeader({
  method: 'put',
  headers: { [BUSINESS_TRACE_HEADER]: 'ui_existing' }
})
assert.equal(existingTraceRequest.headers[BUSINESS_TRACE_HEADER], 'ui_existing')
assert.equal(
  ensureBusinessTraceHeader({ method: 'get', headers: {} }).headers[BUSINESS_TRACE_HEADER],
  undefined
)

const normalBeforeSubmitBinding = { sourceId: 'normal-source' }
const incompleteClientBinding = {
  sourceId: 'incomplete-source',
  clientPrevalidate: true
}
const safeClientBinding = {
  sourceId: 'safe-source',
  clientPrevalidate: true,
  sideEffectFree: true
}
assert.equal(isClientPrevalidationBinding(normalBeforeSubmitBinding), false)
assert.equal(isClientPrevalidationBinding(incompleteClientBinding), false)
assert.equal(isClientPrevalidationBinding(safeClientBinding), true)
assert.deepEqual(
  getClientBeforeSubmitBindings({
    dataSourceBindings: {
      BEFORE_SUBMIT: [
        'legacy-source',
        normalBeforeSubmitBinding,
        incompleteClientBinding,
        safeClientBinding
      ]
    }
  }),
  [{
    ...safeClientBinding,
    usage: 'BEFORE_SUBMIT'
  }]
)
const browserExecutions = []
const browserRecord = { amount: 88 }
const browserRuntime = createFormDataSourceRuntime({
  entityCode: 'expense',
  getRecord: () => browserRecord,
  getMode: () => 'create',
  executeDataSource: async (sourceId, request) => {
    browserExecutions.push({ sourceId, request })
    return { data: { browserMutated: true } }
  }
})
await browserRuntime.prevalidateBeforeSubmit({
  form: {
    dataSourceBindings: {
      BEFORE_SUBMIT: normalBeforeSubmitBinding
    }
  },
  fields: [{
    dataSourceBindings: {
      BEFORE_SUBMIT: safeClientBinding
    }
  }]
})
assert.equal(browserExecutions.length, 1)
assert.equal(browserExecutions[0].sourceId, 'safe-source')
assert.equal(browserExecutions[0].request.context.clientPrevalidate, true)
assert.deepEqual(browserRecord, { amount: 88 })
await assert.rejects(
  browserRuntime.execute(normalBeforeSubmitBinding, {
    usage: 'BEFORE_SUBMIT'
  }),
  /浏览器禁止执行普通 BEFORE_SUBMIT/
)
assert.equal(browserExecutions.length, 1)

const initializationExecutions = []
const initializedRecord = {}
const initializationForm = {
  id: 'form-1',
  dataSourceBindingsDocument: JSON.stringify({
    FORM_INIT: {
      sourceId: 'form-init-source'
    },
    AFTER_LOAD: {
      sourceId: 'form-after-load-source'
    }
  })
}
const initializationRuntime = createFormDataSourceRuntime({
  entityCode: 'expense',
  getForm: () => initializationForm,
  getRecord: () => initializedRecord,
  getMode: () => 'create',
  executeDataSource: async (sourceId) => {
    initializationExecutions.push(sourceId)
    return sourceId === 'form-init-source'
      ? { data: { initialized: true } }
      : { data: { afterLoaded: true } }
  }
})
await initializationRuntime.initialize({
  form: initializationForm
})
assert.deepEqual(
  initializationExecutions,
  ['form-init-source', 'form-after-load-source']
)
assert.deepEqual(
  initializedRecord,
  {
    initialized: true,
    afterLoaded: true
  }
)

const nestedInitializationExecutions = []
const parentRecord = { parentOnly: true }
const nestedForm = {
  id: 'child-form-1',
  entityId: 'child-entity-1',
  dataSourceBindings: {
    FORM_INIT: {
      sourceId: 'child-form-init-source'
    },
    AFTER_LOAD: {
      sourceId: 'child-form-after-load-source'
    }
  }
}
const nestedRuntime = createFormDataSourceRuntime({
  entityCode: 'parent-entity',
  getForm: () => ({ id: 'parent-form-1', entityId: 'parent-entity-1' }),
  getRecord: () => parentRecord,
  getMode: () => 'edit',
  executeDataSource: async (sourceId, request) => {
    nestedInitializationExecutions.push({ sourceId, request })
    return sourceId === 'child-form-init-source'
      ? { data: { initializedForChild: request.input.data.rowKey } }
      : { data: { afterLoadedForChild: request.input.data.rowKey } }
  }
})
const childRowOne = { rowKey: 'one' }
const childRowTwo = { rowKey: 'two' }
await nestedRuntime.initialize({
  form: nestedForm,
  record: childRowOne,
  recordId: 'parent-1:lines:0',
  initializationKey: 'nested:child-form-1:parent-1:lines:0'
})
await nestedRuntime.initialize({
  form: nestedForm,
  record: childRowTwo,
  recordId: 'parent-1:lines:1',
  initializationKey: 'nested:child-form-1:parent-1:lines:1'
})
assert.deepEqual(childRowOne, {
  rowKey: 'one',
  initializedForChild: 'one',
  afterLoadedForChild: 'one'
})
assert.deepEqual(childRowTwo, {
  rowKey: 'two',
  initializedForChild: 'two',
  afterLoadedForChild: 'two'
})
assert.deepEqual(parentRecord, { parentOnly: true })
assert.equal(nestedInitializationExecutions.length, 4)
assert.equal(nestedInitializationExecutions[0].request.context.formId, 'child-form-1')
assert.equal(nestedInitializationExecutions[0].request.context.entityId, 'child-entity-1')
assert.equal(nestedInitializationExecutions[0].request.input.recordId, 'parent-1:lines:0')

assert.equal(formatDateValue('not-a-date'), '-')
assert.notEqual(formatDateValue('2026-07-14T08:00:00Z'), '-')

assert.deepEqual(safeParseConfig('{"a":1}'), { a: 1 })
assert.deepEqual(safeParseConfig('{bad json'), {})
assert.equal(stringifyConfig({}), '')
assert.equal(stringifyConfig({ a: 1 }), '{"a":1}')
assert.deepEqual(
  applySchemaDefaults([{ key: 'size', defaultValue: 'small' }], {}),
  { size: 'small' }
)

const modeField = {
  extensionConfig: JSON.stringify({
    modes: {
      create: { visible: true, editable: true },
      view: { visible: true, editable: false }
    }
  })
}
assert.deepEqual(getFieldModeAccess(modeField, 'view'), { visible: true, editable: false })
assert.equal(isFieldVisibleForMode(modeField, 'create'), true)
assert.equal(isFieldReadonlyForMode(modeField, 'view'), true)

const validationRules = buildRuntimeFieldRules(
  { validationRules: JSON.stringify({ minLength: 2, maxLength: 5, format: 'EMAIL' }) },
  true,
  '邮箱'
)
assert.equal(validationRules.length, 3)

console.log('runtime integration tests passed')
