import assert from 'node:assert/strict'
import {
  ENTITY_VALIDATION_RULE_GROUPS,
  getEntityValidationRuleGroup,
  validateEntityValidationRules
} from '../entity-validation-rules.js'

assert.equal(ENTITY_VALIDATION_RULE_GROUPS.length, 3)
assert.equal(getEntityValidationRuleGroup('STRING').key, 'TEXT')
assert.equal(getEntityValidationRuleGroup('DECIMAL').key, 'NUMBER')
assert.equal(getEntityValidationRuleGroup('DATE').key, 'OTHER')

const textRules = validateEntityValidationRules(
  'STRING',
  '{"minLength":2,"maxLength":100,"format":"email"}'
)
assert.equal(textRules.valid, true)
assert.equal(
  textRules.normalized,
  '{"minLength":2,"maxLength":100,"format":"EMAIL"}'
)

const numberRules = validateEntityValidationRules(
  'DECIMAL',
  '{"min":0.01,"max":100}'
)
assert.equal(numberRules.valid, true)
assert.equal(numberRules.normalized, '{"min":0.01,"max":100}')

assert.equal(
  validateEntityValidationRules('STRING', '{"min":0}').valid,
  false
)
assert.equal(
  validateEntityValidationRules('DATE', '{"minLength":1}').valid,
  false
)
assert.equal(
  validateEntityValidationRules('STRING', '{"pattern":"x"}').valid,
  false
)
assert.equal(
  validateEntityValidationRules('STRING', '{"minLength":5,"maxLength":2}').valid,
  false
)
assert.equal(
  validateEntityValidationRules('DECIMAL', '{"min":10,"max":2}').valid,
  false
)
assert.equal(
  validateEntityValidationRules('STRING', '{"format":"IP"}').valid,
  false
)
assert.equal(
  validateEntityValidationRules('STRING', '{bad json').valid,
  false
)

console.log('entity validation rule tests passed')
