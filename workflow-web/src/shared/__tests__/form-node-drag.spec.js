import assert from 'node:assert/strict'
import {
  buildFormNodeDropPlan,
  getFormNodeDepth,
  validateFormNodeDrop
} from '../form-node-drag.js'

function node(id, nodeType, parentId = '', orderKey = 1_000_000) {
  return { id, nodeType, parentId, orderKey }
}

const baseNodes = [
  node('section', 'SECTION'),
  node('field-a', 'FIELD', 'section', 1_000_000),
  node('field-b', 'FIELD', 'section', 2_000_000),
  node('tab-set', 'TAB_SET', '', 2_000_000),
  node('tab-a', 'TAB', 'tab-set', 1_000_000),
  node('grid', 'GRID', 'tab-a', 1_000_000)
]

const reorderPlan = buildFormNodeDropPlan(
  baseNodes,
  baseNodes[2],
  'section',
  0
)
assert.equal(reorderPlan.valid, true)
assert.deepEqual(
  reorderPlan.orderedSiblings.map(item => item.id),
  ['field-b', 'field-a']
)
assert.equal(reorderPlan.previousNodeId, null)
assert.equal(reorderPlan.nextNodeId, 'field-a')

const crossParentPlan = buildFormNodeDropPlan(
  baseNodes,
  baseNodes[1],
  'grid',
  0
)
assert.equal(crossParentPlan.valid, true)
assert.equal(crossParentPlan.parentId, 'grid')
assert.deepEqual(
  crossParentPlan.orderedSiblings.map(item => item.id),
  ['field-a']
)

assert.equal(
  validateFormNodeDrop(baseNodes, baseNodes[4], '').code,
  'ROOT_TYPE_FORBIDDEN'
)
assert.equal(
  validateFormNodeDrop(baseNodes, baseNodes[1], 'tab-set').code,
  'INCOMPATIBLE_PARENT'
)
assert.equal(
  validateFormNodeDrop(baseNodes, baseNodes[3], 'grid').code,
  'DESCENDANT_PARENT'
)

const deepNodes = [
  node('n1', 'SECTION'),
  node('n2', 'SECTION', 'n1'),
  node('n3', 'SECTION', 'n2'),
  node('n4', 'SECTION', 'n3'),
  node('n5', 'SECTION', 'n4'),
  node('n6', 'SECTION', 'n5'),
  node('n7', 'SECTION', 'n6'),
  node('n8', 'SECTION', 'n7'),
  node('field', 'FIELD')
]
assert.equal(getFormNodeDepth(deepNodes, 'n8'), 8)
assert.equal(
  validateFormNodeDrop(deepNodes, deepNodes[8], 'n8').code,
  'MAX_DEPTH_EXCEEDED'
)

assert.equal(
  validateFormNodeDrop(baseNodes, baseNodes[4], 'tab-set').valid,
  true
)

console.log('form-node-drag tests passed')
