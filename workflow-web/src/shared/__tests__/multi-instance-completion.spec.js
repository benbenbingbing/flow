import assert from 'node:assert/strict'
import {
  buildMultiInstanceCompletionCondition,
  buildNodeScopedMultiInstanceApprovedCountVariable,
  buildNodeScopedMultiInstanceCollection,
  buildNodeScopedMultiInstanceRejectedVariable,
  MIN_MULTI_INSTANCE_COMPLETION_RATE,
  MULTI_INSTANCE_DECISION_COUNTERSIGN,
  MULTI_INSTANCE_DECISION_ORSIGN,
  normalizeMultiInstanceCompletionRate,
  normalizeMultiInstanceDecision,
  sanitizeMultiInstanceNodeId
} from '../process-config/index.js'

assert.equal(
  sanitizeMultiInstanceNodeId('finance-review'),
  'finance_review'
)
assert.equal(
  sanitizeMultiInstanceNodeId('___'),
  'node'
)
assert.equal(
  buildNodeScopedMultiInstanceApprovedCountVariable('finance-review'),
  '${_wf_mi_approved_count_finance_review}'
)
assert.equal(
  buildNodeScopedMultiInstanceRejectedVariable('finance-review'),
  '${_wf_mi_rejected_finance_review}'
)
assert.equal(
  buildNodeScopedMultiInstanceCollection('finance-review', ''),
  '${_wfMultiInstanceUsers_finance_review}'
)

assert.equal(
  normalizeMultiInstanceDecision('or_sign'),
  MULTI_INSTANCE_DECISION_ORSIGN
)
assert.equal(
  normalizeMultiInstanceDecision(''),
  MULTI_INSTANCE_DECISION_COUNTERSIGN
)
assert.equal(normalizeMultiInstanceCompletionRate(0), MIN_MULTI_INSTANCE_COMPLETION_RATE)
assert.equal(normalizeMultiInstanceCompletionRate(-5), MIN_MULTI_INSTANCE_COMPLETION_RATE)

assert.equal(
  buildMultiInstanceCompletionCondition({
    decision: 'orsign',
    nodeId: 'joint-review'
  }),
  '${_wf_mi_rejected_joint_review || _wf_mi_approved_count_joint_review >= 1}'
)

assert.equal(
  buildMultiInstanceCompletionCondition({
    decision: 'countersign',
    completionRate: 67,
    needAllApprovers: false,
    nodeId: 'joint-review'
  }),
  '${_wf_mi_approved_count_joint_review * 100 >= nrOfInstances * 67 || (_wf_mi_approved_count_joint_review + nrOfInstances - nrOfCompletedInstances) * 100 < nrOfInstances * 67}'
)

assert.equal(
  buildMultiInstanceCompletionCondition({
    decision: 'countersign',
    completionRate: 100,
    needAllApprovers: true,
    nodeId: 'joint-review'
  }),
  '${nrOfCompletedInstances >= nrOfInstances}'
)

console.log('multi-instance completion contract passed')
