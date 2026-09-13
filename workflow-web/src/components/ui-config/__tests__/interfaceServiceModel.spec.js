import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

// 使用情况是接口服务的同一 CI 边界，确保新模型不会变成游离测试。
import './interfaceServiceUsageModel.spec.js'

import {
  configurableEntities,
  defaultInterfaceServiceDebugUsage,
  eventBindingOperationsForEvent,
  eventBindingReadOperations,
  interfaceServiceUsageOptions,
  isInterfaceServiceUsageCompatible,
  requiresProvider,
  sourceTypeOptions
} from '../interfaceServiceModel.js'
import {
  eventGroupsForScope,
  eventsForScope
} from '../uiEventScope.js'

const entities = [
  { id: 'system', storageMode: 'SYSTEM' },
  { id: 'dynamic', storageMode: 'DYNAMIC' },
  { id: 'legacy' }
]

assert.deepEqual(
  configurableEntities(entities).map(entity => entity.id),
  ['dynamic', 'legacy']
)

const eventBindingEditorSource = readFileSync(
  new URL('../EventBindingEditor.vue', import.meta.url),
  'utf8'
)
assert.match(
  eventBindingEditorSource,
  /表单自定义按钮本身没有平台默认动作/
)
assert.match(
  eventBindingEditorSource,
  /最终链必须且只能包含一个主处理/
)
assert.doesNotMatch(
  eventBindingEditorSource,
  /FORM_BUTTON_CLICK:\s*'执行该表单按钮原有的内置动作'/
)

const formButtonInheritanceOptionsSource = eventBindingEditorSource.match(
  /const formButtonInheritanceOptions = \[([\s\S]*?)\n\]/
)?.[1] || ''
assert.match(formButtonInheritanceOptionsSource, /仅使用当前层/)
assert.doesNotMatch(formButtonInheritanceOptionsSource, /DISABLE|禁用自定义/)
assert.match(
  eventBindingEditorSource,
  /formButtonExactTarget\.value[\s\S]*?formButtonInheritanceOptions[\s\S]*?defaultInheritanceOptions/
)
assert.match(
  eventBindingEditorSource,
  /formButtonExactTarget\.value && editor\.inheritanceMode === 'DISABLE'/
)

const formButtonStepOptionsSource = eventBindingEditorSource.match(
  /const formButtonStepStrategyOptions = \[([\s\S]*?)\n\]/
)?.[1] || ''
assert.match(formButtonStepOptionsSource, /前置处理[\s\S]*?BEFORE/)
assert.match(formButtonStepOptionsSource, /主处理[\s\S]*?REPLACE/)
assert.match(formButtonStepOptionsSource, /后置处理[\s\S]*?AFTER/)
assert.match(
  eventBindingEditorSource,
  /formButtonEventSelected\.value \? '执行阶段' : '执行位置'/
)
assert.match(
  eventBindingEditorSource,
  /function defaultNewStepStrategy\(\)[\s\S]*?return 'BEFORE'[\s\S]*?=== 'REPLACE'\)[\s\S]*?\? 'AFTER'[\s\S]*?: 'REPLACE'/
)
assert.match(
  eventBindingEditorSource,
  /formButtonExactTarget\.value[\s\S]*?editor\.inheritanceMode === 'REPLACE' && mainStepCount !== 1[\s\S]*?必须且只能配置一个主处理步骤/
)
assert.match(
  eventBindingEditorSource,
  /editor\.inheritanceMode === 'INHERIT' && mainStepCount > 1[\s\S]*?最多只能配置一个主处理步骤/
)
assert.match(
  eventBindingEditorSource,
  /\$\{formButtonStageLabel\(strategy\)\}：\$\{label\(step\)\}/
)
assert.match(eventBindingEditorSource, /当前层缺少主处理（不可发布）/)
assert.match(eventBindingEditorSource, /当前层不提供公共步骤，由具体按钮补充主处理/)
assert.match(eventBindingEditorSource, /清空截至当前层的公共步骤/)
assert.match(eventBindingEditorSource, /help-key="uiEvent\.inheritanceMode"/)
assert.match(eventBindingEditorSource, /help-key="uiEvent\.stepStrategy"/)
assert.match(
  eventBindingEditorSource,
  /<el-collapse-item name="input">[\s\S]*?<template #title>[\s\S]*?label="输入参数映射"[\s\S]*?help-key="uiEvent\.inputMapping"/
)
assert.match(
  eventBindingEditorSource,
  /<el-collapse-item name="output">[\s\S]*?<template #title>[\s\S]*?label="结果回填"[\s\S]*?help-key="uiEvent\.outputMapping"/
)
assert.doesNotMatch(eventBindingEditorSource, /<el-collapse-item title="输入参数映射"/)
assert.doesNotMatch(eventBindingEditorSource, /<el-collapse-item title="结果回填"/)
assert.match(
  eventBindingEditorSource,
  /:disabled="isFormButtonMainStep\(step\) && !hasStepCondition\(step\)"/
)
assert.match(
  eventBindingEditorSource,
  /执行条件（主处理固定无条件执行）/
)
assert.match(
  eventBindingEditorSource,
  /执行条件（主处理需清空）[\s\S]*?主处理必须无条件执行，请清空当前执行条件后再保存。[\s\S]*?clearStepCondition\(step\)/
)
assert.match(
  eventBindingEditorSource,
  /function hasStepCondition\(step\)[\s\S]*?Boolean\(step\?\.conditionPath\)/
)
assert.match(
  eventBindingEditorSource,
  /function normalizeReplace\(current\)[\s\S]*?formButtonEventSelected\.value[\s\S]*?clearStepCondition\(current\)/
)
assert.match(
  eventBindingEditorSource,
  /function clearStepCondition\(step\)[\s\S]*?step\.conditionPath = ''[\s\S]*?step\.conditionBoolean = false/
)
assert.match(
  eventBindingEditorSource,
  /formButtonEventSelected\.value && steps\.some\(step =>[\s\S]*?step\.strategy === 'REPLACE'[\s\S]*?Object\.keys\(step\.condition \|\| \{\}\)\.length > 0[\s\S]*?主处理必须无条件执行/
)

const mixedEventOperations = [
  { operationCode: 'query', kind: 'READ' },
  { operationCode: 'mutate', kind: 'WRITE' }
]
assert.deepEqual(
  eventBindingOperationsForEvent(
    mixedEventOperations,
    'FORM_BUTTON_CLICK'
  ).map(operation => operation.operationCode),
  ['query']
)
assert.deepEqual(
  eventBindingOperationsForEvent(
    mixedEventOperations,
    'DATA_UPDATE'
  ).map(operation => operation.operationCode),
  ['query', 'mutate'],
  '普通数据与列表事件必须保留既有 WRITE 操作能力'
)
assert.equal(entities.length, 3)
assert.deepEqual(configurableEntities(null), [])

assert.deepEqual(
  sourceTypeOptions.map(option => option.value),
  [
    'DICTIONARY',
    'STATIC_OPTIONS',
    'REGISTERED_PROVIDER',
    'RUNTIME_CONTEXT',
    'STRUCTURED_COMPUTE'
  ]
)
assert.equal(requiresProvider('REGISTERED_PROVIDER'), true)
assert.equal(requiresProvider('DICTIONARY'), false)

assert.deepEqual(
  eventBindingReadOperations([
    { operationCode: 'query', kind: 'read' },
    { operationCode: 'mutate', kind: 'WRITE' },
    { operationCode: 'legacy-without-kind' },
    null
  ]).map(operation => operation.operationCode),
  ['query'],
  '事件编辑器必须隐藏 WRITE 和未知影响类型的操作'
)

assert.equal(
  interfaceServiceUsageOptions.some(option => option.value === 'FIELD_OPTIONS'),
  true
)
assert.equal(
  defaultInterfaceServiceDebugUsage({
    code: 'FIELD_OPTIONS',
    kind: 'READ',
    contextType: 'FORM'
  }),
  'FIELD_OPTIONS'
)
assert.equal(
  defaultInterfaceServiceDebugUsage({
    code: 'CUSTOM_LIST_READ',
    kind: 'READ',
    contextType: 'LIST'
  }),
  'LIST_LOAD'
)
assert.equal(
  defaultInterfaceServiceDebugUsage({
    code: 'CUSTOM_WRITE',
    kind: 'WRITE',
    contextType: 'ENTITY'
  }),
  'DATA_UPDATE'
)
assert.equal(
  isInterfaceServiceUsageCompatible({
    code: 'FIELD_OPTIONS',
    kind: 'WRITE',
    contextType: 'FORM'
  }, 'FIELD_OPTIONS'),
  false
)
assert.equal(
  defaultInterfaceServiceDebugUsage({
    code: 'FIELD_OPTIONS',
    kind: 'WRITE',
    contextType: 'FORM'
  }),
  'DATA_UPDATE'
)
assert.equal(
  defaultInterfaceServiceDebugUsage({
    code: 'DATA_UPDATE',
    kind: 'READ',
    contextType: 'FORM'
  }),
  'DETAIL_LOAD'
)
assert.equal(
  defaultInterfaceServiceDebugUsage({
    code: 'LIST_LOAD',
    kind: 'READ',
    contextType: 'FORM'
  }),
  'DETAIL_LOAD'
)

assert.deepEqual(
  eventGroupsForScope('FORM', 'OWNER').map(group => group.label),
  ['表单生命周期', '表单数据', '字段默认事件', '子表单默认事件', '表单按钮']
)
assert.equal(eventsForScope('FORM').includes('FORM_SAVE'), true)
assert.equal(eventsForScope('FORM').includes('FIELD_CHANGE'), true)
assert.equal(eventsForScope('FORM').includes('LIST_LOAD'), false)
assert.equal(eventsForScope('FORM').includes('LIST_EXPORT'), false)
assert.equal(eventsForScope('FORM').includes('DATA_DELETE'), false)
assert.equal(eventsForScope('FORM').includes('DATA_BATCH_DELETE'), false)
assert.deepEqual(
  eventsForScope('FORM', 'FIELD'),
  [
    'FIELD_CHANGE',
    'ENTITY_SELECTED',
    'FIELD_BUTTON_CLICK',
    'SUBFORM_LOAD',
    'SUBFORM_SAVE'
  ]
)
assert.deepEqual(
  eventsForScope('LIST', 'BUTTON'),
  ['TOOLBAR_BUTTON_CLICK', 'ROW_BUTTON_CLICK']
)
assert.equal(eventsForScope('LIST').includes('FORM_OPEN'), false)
assert.equal(eventsForScope('LIST').includes('ROW_BUTTON_CLICK'), true)
assert.equal(eventsForScope('ENTITY').includes('FORM_OPEN'), true)
assert.deepEqual(eventsForScope('ENTITY', 'FIELD'), [])

console.log('interface service entity selection tests passed')
