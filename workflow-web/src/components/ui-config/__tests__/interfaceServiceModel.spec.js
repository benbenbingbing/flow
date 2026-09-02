import assert from 'node:assert/strict'

import {
  configurableEntities,
  defaultInterfaceServiceDebugUsage,
  interfaceServiceUsageOptions,
  isInterfaceServiceUsageCompatible
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
assert.equal(entities.length, 3)
assert.deepEqual(configurableEntities(null), [])

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
