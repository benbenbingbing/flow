import assert from 'node:assert/strict'

import {
  getFieldKey,
  getFieldModelPath,
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
  normalizeApiResponse,
  normalizePageResult,
  toPageParams,
  API_SUCCESS_CODES,
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

assert.equal(formatDateValue('not-a-date'), '-')
assert.notEqual(formatDateValue('2026-07-14T08:00:00Z'), '-')

console.log('runtime integration tests passed')
