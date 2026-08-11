import assert from 'node:assert/strict'

import {
  buildUiEventExecutionPayload,
  sanitizeUiEventContext
} from '../ui-event-request.js'

const source = {
  listId: 'list-1',
  list_key: 'default',
  'ENTITY-CODE': 'expense',
  userId: 'forged-user',
  mode: 'edit',
  scene: 'PAGE',
  params: {
    listId: 'nested-business-value'
  }
}

assert.deepEqual(
  sanitizeUiEventContext(source),
  {
    mode: 'edit',
    scene: 'PAGE',
    params: {
      listId: 'nested-business-value'
    }
  }
)
assert.equal(source.listId, 'list-1')

assert.deepEqual(
  buildUiEventExecutionPayload({
    configType: 'LIST',
    configId: 'list-1',
    entityCode: 'expense',
    listKey: 'default',
    context: source
  }),
  {
    configType: 'LIST',
    configId: 'list-1',
    entityCode: 'expense',
    listKey: 'default',
    context: {
      mode: 'edit',
      scene: 'PAGE',
      params: {
        listId: 'nested-business-value'
      }
    }
  }
)

console.log('ui event request sanitization tests passed')
