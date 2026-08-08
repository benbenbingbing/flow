import assert from 'node:assert/strict'
import {
  buildRuntimeFieldRules,
  getFieldModeAccess,
  getRuntimeRegexPatternError,
  resolveRuntimeNodeFieldRules,
  resolveTextFieldMaxLength
} from '../config-runtime/index.js'

const resolved = resolveRuntimeNodeFieldRules(
  {
    validationRules: {
      minLength: 2,
      maxLength: 200
    },
    extensionConfig: {
      modes: {
        create: { visible: true, editable: true },
        approve: { visible: true, editable: false }
      },
      source: 'entity-field'
    }
  },
  {
    validation: {
      maxLength: 120,
      format: 'EMAIL'
    },
    extension: {
      modes: {
        approve: { visible: false },
        view: { visible: true }
      },
      source: 'form-node'
    }
  }
)

assert.deepEqual(resolved.validationRules, {
  minLength: 2,
  maxLength: 120,
  format: 'EMAIL'
})
assert.deepEqual(resolved.extensionConfig, {
  modes: {
    create: { visible: true, editable: true },
    approve: { visible: false, editable: false },
    view: { visible: true }
  },
  source: 'form-node'
})
assert.deepEqual(
  getFieldModeAccess(
    { extensionConfig: resolved.extensionConfig },
    'approve'
  ),
  {
    visible: false,
    editable: false
  }
)

assert.equal(
  resolveTextFieldMaxLength(
    {
      validationRules: { maxLength: 120 },
      fieldLength: 255
    },
    { maxlength: 64 }
  ),
  120,
  '校验规则最大长度应作为文本输入的唯一优先配置'
)
assert.equal(
  resolveTextFieldMaxLength(
    { validationRules: {}, fieldLength: 255 },
    { maxlength: 64 }
  ),
  64,
  '历史组件 maxlength 应继续作为兼容兜底'
)
assert.equal(
  resolveTextFieldMaxLength(
    { validationRules: {}, fieldLength: 255 },
    {}
  ),
  255,
  '未配置表单长度时应继续使用实体字段长度'
)

assert.equal(getRuntimeRegexPatternError('^REQ-\\d{4}$'), '')
assert.equal(
  getRuntimeRegexPatternError('[unclosed'),
  '正则表达式语法不正确'
)

const regexRules = buildRuntimeFieldRules(
  {
    validationRules: {
      pattern: '^REQ-\\d{4}$'
    }
  },
  false,
  '需求编码'
)
assert.equal(regexRules.length, 1)
let regexValidationError
regexRules[0].validator({}, 'REQ-2026', error => {
  regexValidationError = error
})
assert.equal(regexValidationError, undefined)
regexRules[0].validator({}, 'BAD-2026', error => {
  regexValidationError = error
})
assert.equal(regexValidationError?.message, '需求编码格式不符合正则要求')

console.log('runtime node field rules tests passed')
