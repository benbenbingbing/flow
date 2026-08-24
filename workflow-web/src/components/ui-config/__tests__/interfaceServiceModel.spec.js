import assert from 'node:assert/strict'

import { configurableEntities } from '../interfaceServiceModel.js'
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
