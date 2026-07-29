const TEXT_FIELD_TYPES = new Set(['STRING', 'TEXT'])
const NUMBER_FIELD_TYPES = new Set(['INTEGER', 'LONG', 'DECIMAL'])
const VALID_FORMATS = new Set(['EMAIL', 'PHONE', 'URL'])
const SUPPORTED_RULE_KEYS = new Set([
  'minLength',
  'maxLength',
  'min',
  'max',
  'format'
])

export const ENTITY_VALIDATION_RULE_GROUPS = [
  {
    key: 'TEXT',
    title: '文本、长文本',
    fieldTypes: ['STRING', 'TEXT'],
    rules: [
      { key: 'minLength', value: '0-20000 的整数', description: '最少字符数' },
      { key: 'maxLength', value: '0-20000 的整数', description: '最多字符数' },
      { key: 'format', value: 'EMAIL、PHONE 或 URL', description: '邮箱、手机号或网址格式' }
    ],
    example: '{"minLength":2,"maxLength":100,"format":"EMAIL"}'
  },
  {
    key: 'NUMBER',
    title: '整数、小数',
    fieldTypes: ['INTEGER', 'LONG', 'DECIMAL'],
    rules: [
      { key: 'min', value: '数字', description: '允许的最小值' },
      { key: 'max', value: '数字', description: '允许的最大值' }
    ],
    example: '{"min":0.01,"max":100}'
  },
  {
    key: 'OTHER',
    title: '其他字段类型',
    fieldTypes: [
      'RICH_TEXT',
      'DATE',
      'DATETIME',
      'BOOLEAN',
      'SELECT',
      'MULTI_SELECT',
      'RADIO',
      'CHECKBOX',
      'FILE',
      'IMAGE',
      'USER',
      'DEPT',
      'REFERENCE',
      'MULTI_REFERENCE',
      'SUB_FORM',
      'SUB_FORM_LIST'
    ],
    rules: [],
    description: '暂无内置单字段验证规则。必填、唯一、附件限制、选项来源和关联关系请使用对应的独立配置项。',
    example: ''
  }
]

export function getEntityValidationRuleGroup(fieldType) {
  const normalizedType = String(fieldType || '').toUpperCase()
  return ENTITY_VALIDATION_RULE_GROUPS.find(group =>
    group.fieldTypes.includes(normalizedType)
  ) || ENTITY_VALIDATION_RULE_GROUPS[2]
}

export function validateEntityValidationRules(fieldType, rawRules) {
  const text = String(rawRules || '').trim()
  if (!text) {
    return { valid: true, normalized: '', config: {} }
  }

  let config
  try {
    config = JSON.parse(text)
  } catch {
    return { valid: false, error: '验证规则必须是合法的 JSON 对象' }
  }
  if (!config || Array.isArray(config) || typeof config !== 'object') {
    return { valid: false, error: '验证规则必须是 JSON 对象' }
  }

  const fieldTypeName = String(fieldType || '').toUpperCase()
  const allowedKeys = TEXT_FIELD_TYPES.has(fieldTypeName)
    ? new Set(['minLength', 'maxLength', 'format'])
    : NUMBER_FIELD_TYPES.has(fieldTypeName)
      ? new Set(['min', 'max'])
      : new Set()

  for (const key of Object.keys(config)) {
    if (!SUPPORTED_RULE_KEYS.has(key)) {
      return { valid: false, error: `不支持的验证规则：${key}` }
    }
    if (!allowedKeys.has(key)) {
      return { valid: false, error: `当前字段类型不支持验证规则：${key}` }
    }
  }

  for (const key of ['minLength', 'maxLength']) {
    if (config[key] === undefined) continue
    if (!Number.isInteger(config[key]) || config[key] < 0 || config[key] > 20000) {
      return { valid: false, error: `${key} 必须是 0 到 20000 之间的整数` }
    }
  }
  for (const key of ['min', 'max']) {
    if (config[key] === undefined) continue
    if (typeof config[key] !== 'number' || !Number.isFinite(config[key])) {
      return { valid: false, error: `${key} 必须是数字` }
    }
  }

  if (config.minLength !== undefined
      && config.maxLength !== undefined
      && config.minLength > config.maxLength) {
    return { valid: false, error: 'minLength 不能大于 maxLength' }
  }
  if (config.min !== undefined
      && config.max !== undefined
      && config.min > config.max) {
    return { valid: false, error: 'min 不能大于 max' }
  }

  if (config.format !== undefined) {
    if (typeof config.format !== 'string') {
      return { valid: false, error: 'format 必须是字符串' }
    }
    const format = config.format.toUpperCase()
    if (!VALID_FORMATS.has(format)) {
      return { valid: false, error: 'format 仅支持 EMAIL、PHONE 或 URL' }
    }
    config.format = format
  }

  return {
    valid: true,
    normalized: Object.keys(config).length > 0 ? JSON.stringify(config) : '',
    config
  }
}
