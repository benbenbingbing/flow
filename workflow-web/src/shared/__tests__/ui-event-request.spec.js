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
  releaseResolutionToken: 'signed-release-token',
  viewCompositionTraversalToken: 'signed-traversal-token',
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
    viewCompositionTraversalToken: 'signed-traversal-token',
    context: source
  }),
  {
    configType: 'LIST',
    configId: 'list-1',
    entityCode: 'expense',
    listKey: 'default',
    viewCompositionTraversalToken: 'signed-traversal-token',
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
