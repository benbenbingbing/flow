import assert from 'node:assert/strict'

import {
  applyRuntimeFieldDefaults,
  collectRuntimeFormFieldCodes,
  createFormDataSourceRuntime,
  filterRuntimeFormSubmissionData,
  getClientBeforeSubmitBindings,
  getFieldKey,
  getFieldModelPath,
  isClientPrevalidationBinding,
  isSystemField,
  isRuntimeFieldReadonly,
  isRuntimeFormReadonly,
  mergeRuntimeFormConfigs,
  normalizeEntityRecordForForm,
  normalizeRuntimeFormConfigs
} from '@/shared/form-runtime'
import {
  formatDateValue,
  formatListFieldValue,
  getCellValue,
  isReferenceListField,
  parseDataSourceConfig,
  parseJsonOptions,
  toRuntimeFieldKey
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
assert.equal(formatListFieldValue({ data: { owner: 'u1' } }, refField), '-')

const customRefField = {
  fieldCode: 'projectId',
  fieldType: 'REFERENCE',
  refEntityType: 'CUSTOM',
  refEntityId: 'project-entity'
}
assert.equal(isReferenceListField(customRefField), true)
assert.equal(
  formatListFieldValue(
    { data: { projectId: 'project-1' } },
    customRefField,
    { 'CUSTOM:project-entity:project-1': '统一客户运营平台' }
  ),
  '统一客户运营平台'
)
assert.equal(
  formatListFieldValue(
    { data: { projectId: 'project-1' } },
    customRefField
  ),
  '-'
)

const multiRefField = { fieldCode: 'reviewers', fieldType: 'MULTI_REFERENCE', refEntityType: 'USER', refEntityId: '' }
assert.equal(formatListFieldValue({ data: { reviewers: '["u1","u2"]' } }, multiRefField, { 'USER::u1': '张三', 'USER::u2': '李四' }), '张三, 李四')
assert.equal(formatListFieldValue({ data: { reviewers: '["u1","u2"]' } }, multiRefField), '-')
assert.equal(
  formatListFieldValue(
    {
      data: {
        project_id: 'project-1',
        project_id_display: '统一客户运营平台'
      }
    },
    {
      fieldCode: 'project_id',
      fieldType: 'REFERENCE',
      refEntityType: 'CUSTOM',
      refEntityId: 'project-entity'
    }
  ),
  '统一客户运营平台'
)

assert.equal(formatListFieldValue({ data: { lines: [{ id: 1 }, { id: 2 }] } }, { fieldCode: 'lines', fieldType: 'SUB_FORM' }), '2 行')
assert.equal(formatListFieldValue({ status: 'DRAFT' }, { fieldCode: 'status' }), '草稿')
assert.equal(formatListFieldValue({ data: { missing: null } }, { fieldCode: 'missing' }), '-')

assert.deepEqual(parseJsonOptions('[{"label":"是","value":true}]'), [{ label: '是', value: true }])
assert.deepEqual(parseJsonOptions('{bad json'), [])
assert.deepEqual(parseDataSourceConfig('{"url":"/api/demo"}'), { url: '/api/demo' })
assert.deepEqual(parseDataSourceConfig('{bad json'), {})

assert.equal(getCellValue({ extData: { a: 1 }, data: { a: 2 }, a: 3 }, { fieldCode: 'a' }), 1)
assert.equal(getCellValue({ data: { a: 2 }, a: 3 }, { fieldCode: 'a' }), 2)
assert.equal(getCellValue({ a: 3 }, { fieldCode: 'a' }), 3)
assert.equal(toRuntimeFieldKey('customer_name'), 'customerName')
assert.equal(
  getCellValue({ data: { customerName: '验收客户' } }, { fieldCode: 'customer_name' }),
  '验收客户'
)
assert.equal(
  formatListFieldValue(
    { data: { attachment: '["/srv/uploads/a.png"]' } },
    { fieldCode: 'attachment', fieldType: 'FILE' }
  ),
  'a.png'
)

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
assert.deepEqual(normalizeRuntimeFormConfigs({ formConfigs: [configA, configB] }), [configA])
assert.equal(mergeRuntimeFormConfigs([configA, configB]), configA)
assert.deepEqual(
  normalizeEntityRecordForForm({
    id: 'data-1',
    title: '费用申请',
    processInstanceId: 'pi-1',
    processStartTime: '2026-07-25T10:30:00',
    data: { amount: 100 }
  }),
  {
    amount: 100,
    id: 'data-1',
    title: '费用申请',
    processInstanceId: 'pi-1',
    processStartTime: '2026-07-25T10:30:00'
  }
)

const relationEntityFields = [
  { fieldCode: 'name', fieldType: 'STRING' },
  { fieldCode: 'reqItemForm', fieldType: 'SUB_FORM' },
  { fieldCode: 'subList', fieldType: 'SUB_LIST' }
]
const subListOnlyForm = {
  nodes: [
    {
      nodeType: 'FIELD',
      bindingType: 'NONE',
      propsDocument: JSON.stringify({
        fieldCode: 'subList',
        fieldType: 'SUB_LIST'
      })
    },
    {
      nodeType: 'FIELD',
      bindingType: 'ENTITY_FIELD',
      bindingRef: 'name',
      propsDocument: JSON.stringify({
        fieldCode: 'name'
      })
    }
  ]
}
assert.deepEqual(
  [...collectRuntimeFormFieldCodes(subListOnlyForm)].sort(),
  ['name', 'subList']
)
assert.deepEqual(
  filterRuntimeFormSubmissionData(
    {
      name: '4444',
      subList: null,
      reqItemForm: []
    },
    subListOnlyForm,
    relationEntityFields
  ),
  {
    name: '4444',
    subList: null
  }
)
assert.deepEqual(
  filterRuntimeFormSubmissionData(
    {
      name: '4444',
      reqItemForm: []
    },
    {
      nodes: [
        {
          nodeType: 'REPEATER',
          bindingType: 'RELATION',
          bindingRef: 'ZDWREQ_reqItemForm',
          propsDocument: JSON.stringify({
            fieldCode: 'reqItemForm'
          })
        }
      ]
    },
    relationEntityFields
  ),
  {
    name: '4444',
    reqItemForm: []
  }
)

const initializedFormData = applyRuntimeFieldDefaults(
  { quantity: '', urgent: '', preserved: 'keep' },
  {
    fields: [
      { fieldCode: 'quantity', defaultValue: '1' },
      { fieldCode: 'urgent', defaultValue: 'false' },
      { fieldCode: 'priority', defaultValue: 'normal' }
    ],
    nodes: [
      {
        nodeType: 'FIELD',
        propsDocument: JSON.stringify({
          fieldCode: 'tags',
          defaultValue: '["dev","qa"]'
        })
      },
      {
        nodeType: 'FIELD',
        props: {
          fieldCode: 'preserved',
          defaultValue: 'replace'
        }
      }
    ]
  },
  [{ fieldCode: 'amount', defaultValue: '12.5' }]
)
assert.deepEqual(initializedFormData, {
  quantity: 1,
  urgent: false,
  preserved: 'keep',
  amount: 12.5,
  priority: 'normal',
  tags: ['dev', 'qa']
})

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

const normalBeforeSubmitBinding = {
  serviceId: 'normal-source',
  operationCode: 'validate-normal'
}
const incompleteClientBinding = {
  serviceId: 'incomplete-source',
  operationCode: 'validate-incomplete',
  clientPrevalidate: true
}
const safeClientBinding = {
  serviceId: 'safe-source',
  operationCode: 'validate-safe',
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
  executeDataSource: async request => {
    browserExecutions.push(request)
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
assert.equal(browserExecutions[0].serviceId, 'safe-source')
assert.equal(browserExecutions[0].operationCode, 'validate-safe')
assert.equal(browserExecutions[0].bindingCode, 'BEFORE_SUBMIT')
assert.deepEqual(browserExecutions[0].input.formData, { amount: 88 })
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
  formId: 'form-1',
  dataSourceBindingsDocument: JSON.stringify({
    FORM_INIT: {
      serviceId: 'form-init-source',
      operationCode: 'initialize-form'
    },
    AFTER_LOAD: {
      serviceId: 'form-after-load-source',
      operationCode: 'load-form'
    }
  })
}
const initializationRuntime = createFormDataSourceRuntime({
  entityCode: 'expense',
  getForm: () => initializationForm,
  getRecord: () => initializedRecord,
  getMode: () => 'create',
  executeDataSource: async request => {
    initializationExecutions.push(request)
    return request.serviceId === 'form-init-source'
      ? { data: { initialized: true } }
      : { data: { afterLoaded: true } }
  }
})
await initializationRuntime.initialize({
  form: initializationForm
})
assert.deepEqual(
  initializationExecutions.map(request => request.serviceId),
  ['form-init-source', 'form-after-load-source']
)
assert.deepEqual(
  initializationExecutions.map(request => ({
    ownerType: request.ownerType,
    ownerId: request.ownerId
  })),
  [
    { ownerType: 'FORM', ownerId: 'form-1' },
    { ownerType: 'FORM', ownerId: 'form-1' }
  ]
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
      serviceId: 'child-form-init-source',
      operationCode: 'initialize-child'
    },
    AFTER_LOAD: {
      serviceId: 'child-form-after-load-source',
      operationCode: 'load-child'
    }
  }
}
const nestedRuntime = createFormDataSourceRuntime({
  entityCode: 'parent-entity',
  getForm: () => ({ id: 'parent-form-1', entityId: 'parent-entity-1' }),
  getRecord: () => parentRecord,
  getMode: () => 'edit',
  executeDataSource: async request => {
    nestedInitializationExecutions.push(request)
    return request.serviceId === 'child-form-init-source'
      ? { data: { initializedForChild: request.input.formData.rowKey } }
      : { data: { afterLoadedForChild: request.input.formData.rowKey } }
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
assert.equal(nestedInitializationExecutions[0].ownerId, 'child-form-1')
assert.equal(nestedInitializationExecutions[0].ownerType, 'FORM')
assert.equal(nestedInitializationExecutions[0].input.recordId, 'parent-1:lines:0')

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
assert.deepEqual(
  getFieldModeAccess({
    extensionConfig: JSON.stringify({
      modes: {
        view: { visible: true, editable: true }
      }
    })
  }, 'view'),
  { visible: true, editable: false }
)
assert.deepEqual(
  getFieldModeAccess({ extensionConfig: '' }, 'view'),
  { visible: true, editable: false }
)

const validationRules = buildRuntimeFieldRules(
  {
    validationRules: JSON.stringify({
      minLength: 2,
      maxLength: 50,
      format: 'EMAIL',
      pattern: '^[^@]+@example\\.com$'
    })
  },
  true,
  '邮箱'
)
assert.equal(validationRules.length, 4)
const entityValidationRules = buildRuntimeFieldRules(
  { validateRules: JSON.stringify({ min: 0, max: 100 }) },
  false,
  '完成比例'
)
assert.equal(entityValidationRules.length, 1)

console.log('runtime integration tests passed')
