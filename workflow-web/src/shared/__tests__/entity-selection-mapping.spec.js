import assert from 'node:assert/strict'

import {
  ENTITY_SELECTION_FILL_STEP_CODE,
  areEntitySelectionTypesCompatible,
  buildEntitySelectionSourceFields,
  buildEntitySelectionTargetFields,
  entitySelectionMappings,
  isPersistedEntitySelectionField,
  mergeEntitySelectionMappings,
  resolveEntitySelectionRefConfig,
  resolveRuntimeEntitySelectionReference
} from '../entity-selection-mapping.js'

assert.deepEqual(
  buildEntitySelectionSourceFields('CUSTOM').map(field => field.value),
  ['selection.id', 'selection.name', 'selection.code', 'selection.status'],
  '实体回填统一使用 name/code，不再提供退役字段'
)

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

const interfaceStep = {
  name: '检查客户状态',
  strategy: 'BEFORE',
  extensionId: 'customer-status-interface',
  outputMapping: []
}
const merged = mergeEntitySelectionMappings(
  [interfaceStep],
  entitySelectionMappings(firstFormBinding)
)
assert.equal(merged.length, 2)
assert.equal(merged[0].extensionId, 'customer-status-interface')
assert.equal(merged[1].stepCode, ENTITY_SELECTION_FILL_STEP_CODE)
assert.deepEqual(
  merged.map(step => step.order),
  [10, 20],
  '专用配置应合并进现有 ENTITY_SELECTED 执行链'
)

const afterInterface = { ...interfaceStep, strategy: 'AFTER', name: '接口补充信息' }
const updated = mergeEntitySelectionMappings([merged[1], afterInterface], [{
  sourcePath: 'selection.name', targetPath: 'form.name'
}])
assert.equal(updated[0].stepCode, ENTITY_SELECTION_FILL_STEP_CODE, '快捷保存不能把回填从接口之前移到之后')
assert.equal(updated[1].extensionId, afterInterface.extensionId)
assert.deepEqual(mergeEntitySelectionMappings(updated, []), [{ ...afterInterface, order: 10 }], '清空快捷映射只删除快捷步骤，保留接口步骤')

const convertedStep = { ...merged[1], extensionId: 'provider-fill', outputMapping: [{ sourcePath: 'data.userName', targetPath: 'form.name' }] }
assert.deepEqual(entitySelectionMappings({ steps: [convertedStep] }), [], '接口返回值映射不能当成实体选择快捷映射编辑')
const retained = mergeEntitySelectionMappings([convertedStep], [{ sourcePath: 'selection.code', targetPath: 'form.code' }])
const { stepCode: managedCode, ...customStep } = convertedStep
assert.deepEqual(retained[0], { ...customStep, order: 10 }, '转换为接口的旧快捷步骤必须完整保留，只解除快捷管理标记')
assert.equal(retained[1].stepCode, ENTITY_SELECTION_FILL_STEP_CODE)
assert.equal(mergeEntitySelectionMappings(retained, []).length, 1, '清空快捷映射不能删除接口回填')

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

assert.deepEqual(
  resolveRuntimeEntitySelectionReference({
    entityType: 'CUSTOM',
    entityCode: '',
    runtimeEntityCode: 'project',
    refEntityId: 'legacy-project-definition-id'
  }),
  {
    entityCode: 'project',
    refEntityId: ''
  },
  '运行时实体编码必须用于已选关联记录的名称回显'
)
assert.deepEqual(
  resolveRuntimeEntitySelectionReference({
    entityType: 'CUSTOM',
    refEntityId: 'project-definition-id'
  }),
  {
    entityCode: '',
    refEntityId: 'project-definition-id'
  },
  '没有实体编码时继续兼容实体定义 ID'
)

console.log('entity-selection-mapping tests passed')
