import assert from 'node:assert/strict'
import {
  DEFAULT_NODE_FORM_VALUE,
  buildNodeFormPersistencePlan,
  isNodeFormInitializationCurrent,
  resolveNodeFormSelection
} from '../nodeFormBindingModel.js'

const entityForms = [
  {
    id: 101,
    formKey: 'request-default',
    formName: '申请单',
    entityCode: 'REQ',
    isDefault: true
  },
  {
    id: '202',
    formKey: 'request-review',
    formName: '审核单',
    entityCode: 'REQ'
  }
]

const explicitBinding = resolveNodeFormSelection({
  entityFormId: '202',
  entityForms,
  entityFormReadonly: true,
  entityCode: 'REQ'
})
assert.equal(explicitBinding.selectionValue, '202')
assert.deepEqual(explicitBinding.entityFormIds, ['202'])
assert.equal(explicitBinding.isReadonly, true)
assert.equal(explicitBinding.unresolvedBinding, null)

const defaultBinding = resolveNodeFormSelection({ entityForms })
assert.equal(defaultBinding.selectionValue, DEFAULT_NODE_FORM_VALUE)
assert.equal(defaultBinding.entityFormId, '')
assert.equal(defaultBinding.isReadonly, false)

const persistedDefaultBinding = resolveNodeFormSelection({
  entityFormBindingMode: 'DEFAULT',
  entityFormId: '101',
  entityFormReadonly: true,
  entityForms
})
assert.equal(persistedDefaultBinding.selectionValue, DEFAULT_NODE_FORM_VALUE)
assert.equal(persistedDefaultBinding.isReadonly, true)
assert.deepEqual(
  buildNodeFormPersistencePlan({
    selectionValue: persistedDefaultBinding.selectionValue,
    entityForms,
    boundEntityCode: 'REQ'
  }),
  { mode: 'DEFAULT', entityFormId: '101', entityCode: 'REQ' }
)

const resolvedLegacyBinding = resolveNodeFormSelection({
  legacyFormKey: 'request-review',
  entityForms
})
assert.equal(resolvedLegacyBinding.selectionValue, '202')
assert.equal(resolvedLegacyBinding.resolvedFromLegacy, true)
assert.equal(resolvedLegacyBinding.unresolvedBinding, null)
assert.equal(
  resolveNodeFormSelection({ legacyFormKey: '101', entityForms }).selectionValue,
  '101',
  '历史 formKey 使用实体表单 ID 时也应回显对应表单'
)

const resolvedPortableBinding = resolveNodeFormSelection({
  legacyFormKey: 'wf-form://REQ/request-review',
  boundEntityCode: 'REQ',
  entityForms
})
assert.equal(resolvedPortableBinding.selectionValue, '202')
assert.equal(resolvedPortableBinding.resolvedFromLegacy, true)
assert.ok(
  resolveNodeFormSelection({
    legacyFormKey: 'wf-form://OTHER/request-review',
    boundEntityCode: 'REQ',
    entityForms
  }).unresolvedBinding,
  '可移植引用不得跨实体仅按路径尾段匹配'
)

const unresolvedLegacyBinding = resolveNodeFormSelection({
  legacyFormKey: 'external-form-no-longer-registered',
  entityForms
})
assert.equal(unresolvedLegacyBinding.entityFormId, '')
assert.equal(unresolvedLegacyBinding.unresolvedBinding?.kind, 'formKey')
assert.equal(unresolvedLegacyBinding.unresolvedBinding?.value, 'external-form-no-longer-registered')
assert.deepEqual(
  buildNodeFormPersistencePlan({
    selectionValue: unresolvedLegacyBinding.selectionValue,
    entityForms,
    unresolvedBinding: unresolvedLegacyBinding.unresolvedBinding,
    selectionDirty: false
  }).mode,
  'PRESERVE'
)
assert.deepEqual(
  buildNodeFormPersistencePlan({
    selectionValue: DEFAULT_NODE_FORM_VALUE,
    entityForms,
    unresolvedBinding: unresolvedLegacyBinding.unresolvedBinding,
    selectionDirty: true
  }),
  { mode: 'DEFAULT', entityFormId: '101', entityCode: 'REQ' }
)

assert.equal(
  isNodeFormInitializationCurrent({
    sequence: 3,
    activeSequence: 4,
    elementId: 'Task_A',
    currentElementId: 'Task_A',
    processId: 'P1',
    currentProcessId: 'P1'
  }),
  false,
  '过期异步响应不得覆盖当前节点'
)
assert.equal(
  isNodeFormInitializationCurrent({
    sequence: 4,
    activeSequence: 4,
    elementId: 'Task_A',
    currentElementId: 'Task_A',
    processId: 'P1',
    currentProcessId: 'P1'
  }),
  true
)

assert.equal(
  buildNodeFormPersistencePlan({
    selectionValue: DEFAULT_NODE_FORM_VALUE,
    entityForms: []
  }).mode,
  'MISSING_DEFAULT',
  '默认模式缺少实体默认表单时必须阻止保存'
)

console.log('node form binding model tests passed')
