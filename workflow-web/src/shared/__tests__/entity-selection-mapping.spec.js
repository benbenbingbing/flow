import assert from 'node:assert/strict'

import {
  ENTITY_SELECTION_FILL_STEP_CODE,
  areEntitySelectionTypesCompatible,
  buildEntitySelectionSourceFields,
  buildEntitySelectionTargetFields,
  entitySelectionMappings,
  isPersistedEntitySelectionField,
  mergeEntitySelectionMappings,
  resolveEntitySelectionRefConfig
} from '../entity-selection-mapping.js'

const firstFormBinding = {
  eventCode: 'ENTITY_SELECTED',
  steps: mergeEntitySelectionMappings([], [{
    sourcePath: 'selection.data.phone',
    targetPath: 'form.contactPhone',
    sourceType: 'STRING',
    targetType: 'STRING',
    overwrite: 'ALWAYS',
    clearOnEmpty: true
  }])
}
const secondFormBinding = {
  eventCode: 'ENTITY_SELECTED',
  steps: mergeEntitySelectionMappings([], [{
    sourcePath: 'selection.data.phone',
    targetPath: 'form.backupPhone',
    sourceType: 'STRING',
    targetType: 'STRING',
    overwrite: 'IF_EMPTY',
    clearOnEmpty: false
  }])
}

assert.equal(
  entitySelectionMappings(firstFormBinding)[0].targetPath,
  'form.contactPhone',
  '同一引用实体的第一个表单应保留自己的回填目标'
)
assert.equal(
  entitySelectionMappings(secondFormBinding)[0].targetPath,
  'form.backupPhone',
  '同一引用实体的第二个表单应保留独立回填规则'
)
assert.equal(
  entitySelectionMappings(secondFormBinding)[0].clearOnEmpty,
  false,
  '空值保留策略应进入发布步骤'
)

const serviceStep = {
  name: '检查客户状态',
  strategy: 'BEFORE',
  serviceId: 'service-1',
  operationCode: 'check',
  outputMapping: []
}
const merged = mergeEntitySelectionMappings(
  [serviceStep],
  entitySelectionMappings(firstFormBinding)
)
assert.equal(merged.length, 2)
assert.equal(merged[0].serviceId, 'service-1')
assert.equal(merged[1].stepCode, ENTITY_SELECTION_FILL_STEP_CODE)
assert.deepEqual(
  merged.map(step => step.order),
  [10, 20],
  '专用配置应合并进现有 ENTITY_SELECTED 执行链'
)

assert.deepEqual(
  buildEntitySelectionSourceFields('CUSTOM', [{
    fieldCode: 'phone',
    fieldName: '联系电话',
    fieldType: 'STRING',
    isSystem: false
  }]).map(item => item.value).slice(-1),
  ['selection.data.phone']
)
assert.deepEqual(
  buildEntitySelectionTargetFields([
    { fieldCode: 'customerId', fieldType: 'REFERENCE' },
    {
      fieldId: 'phone-field',
      fieldCode: 'phone',
      fieldType: 'STRING'
    },
    {
      fieldId: 'reason-field',
      fieldCode: 'reason',
      fieldType: 'TEXT'
    },
    {
      fieldId: 'detail-field',
      fieldCode: 'detail',
      fieldType: 'SUB_FORM'
    },
    {
      fieldCode: 'staticText',
      componentType: 'TEXT'
    }
  ], 'customerId').map(item => item.value),
  ['form.phone', 'form.reason'],
  '引用字段自身和结构节点不能作为回填目标，业务长文本字段必须保留'
)

assert.equal(
  areEntitySelectionTypesCompatible('STRING', 'TEXT'),
  true
)
assert.equal(
  areEntitySelectionTypesCompatible('INTEGER', 'DECIMAL'),
  true
)
assert.equal(
  areEntitySelectionTypesCompatible('MULTI_SELECT', 'DECIMAL'),
  false
)

assert.equal(
  isPersistedEntitySelectionField({
    id: '2081971483508252675',
    revision: 0
  }),
  true,
  '兼容表中的已保存字段没有节点 revision，也必须允许配置回填'
)
assert.equal(
  isPersistedEntitySelectionField({
    id: 'node_unsaved',
    revision: 0
  }),
  false,
  '尚未保存的新节点不能提前配置回填'
)
assert.deepEqual(
  resolveEntitySelectionRefConfig({
    componentProps: JSON.stringify({
      refConfig: {
        refEntityType: 'custom',
        refEntityId: 'entity-1'
      }
    })
  }),
  {
    refEntityType: 'CUSTOM',
    refEntityId: 'entity-1'
  },
  '入口与编辑器应兼容 componentProps.refConfig'
)

console.log('entity-selection-mapping tests passed')
