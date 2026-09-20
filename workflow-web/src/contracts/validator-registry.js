import { normalizeSupportedEntityCodes } from '../shared/extension-entity-scope.js'

const validators = new Map()
const NAME_PATTERN = /^[A-Za-z][A-Za-z0-9_.:-]{0,99}$/

/**
 * 注册可供表单字段选择的前端校验器；应在应用启动时调用一次。
 * @param {string} name 稳定名称，保存后不能随意更名。
 * @param {{validate: Function}} validator 实现 validate(value, context) 的对象或类实例。
 * @param {Object} [metadata] label/description/version/configSchema/supportedFieldTypes。
 * @param {string[]} [metadata.supportedEntityCodes=[]] [] 或 ['*'] 表示全部实体，
 *   其他编码列表表示仅这些实体；限制同时用于设计器和运行时，缺少实体身份时不放行受限规则。
 * @returns {Object} 注册描述。不同版本并存，修改实现、参数或适用范围时须提升 version。
 * @throws {TypeError|Error} 描述不完整或同名同版本重复注册。
 */
export function registerCustomValidator(name, validator, metadata = {}) {
  const version = metadata.version ?? 1
  if (typeof name !== 'string' || !NAME_PATTERN.test(name) || typeof validator?.validate !== 'function') {
    throw new TypeError('校验器需要合法名称和 validate(value, context) 方法')
  }
  if (!Number.isSafeInteger(version) || version < 1) throw new TypeError('校验器版本必须为正整数')
  const scope = metadata.supportedEntityCodes ?? []
  if (!Array.isArray(scope) || scope.some(code => typeof code !== 'string' || !code.trim())
      || (scope.some(code => code.trim() === '*') && scope.length !== 1)) {
    throw new TypeError('适用实体须为编码数组；全部实体使用 [] 或 ["*"]')
  }
  const fieldTypes = metadata.supportedFieldTypes ?? []
  if (!Array.isArray(fieldTypes) || fieldTypes.some(type => typeof type !== 'string' || !type.trim())) {
    throw new TypeError('支持的字段类型须为非空字符串数组')
  }
  const configSchema = metadata.configSchema ?? []
  if (!Array.isArray(configSchema) || configSchema.some(item => typeof item?.key !== 'string' || !NAME_PATTERN.test(item.key)
      || !['text', 'textarea', 'number', 'boolean', 'select', 'json'].includes(item.type))
      || new Set(configSchema.map(item => item.key)).size !== configSchema.length) {
    throw new TypeError('校验器参数 Schema 必须是 key 唯一的数组')
  }
  const key = `${name}@${version}`
  if (validators.has(key)) throw new Error(`校验器 ${key} 已注册，请提升版本后再注册`)
  const descriptor = Object.freeze({
    name, version, validator,
    label: metadata.label || name,
    description: metadata.description || '',
    supportedEntityCodes: Object.freeze(normalizeSupportedEntityCodes(scope)),
    supportedFieldTypes: Object.freeze(fieldTypes.map(type => type.trim().toUpperCase())),
    configSchema: Object.freeze(configSchema.map(item => Object.freeze({ ...item })))
  })
  validators.set(key, descriptor)
  return descriptor
}

/** 精确查找配置所固定的版本；未安装时返回 undefined，禁止自动换成最新版本。 */
export function getCustomValidator(name, version) {
  return validators.get(`${name}@${version}`)
}

/** 判断校验器是否适用于当前实体/字段；指定实体时缺少编码也视为不适用。 */
export function isCustomValidatorApplicable(descriptor, entityCode, fieldType) {
  if (!descriptor) return false
  const entity = String(entityCode || '').trim().toLowerCase()
  return (!descriptor.supportedEntityCodes.length
      || descriptor.supportedEntityCodes.some(code => code.toLowerCase() === entity))
    && (!descriptor.supportedFieldTypes.length
      || descriptor.supportedFieldTypes.includes(String(fieldType || '').toUpperCase()))
}

/** 返回适用的所有版本供设计器显式选择；不向配置暴露实现对象或函数。 */
export function getCustomValidatorOptions(entityCode, fieldType) {
  return [...validators.values()]
    .filter(item => isCustomValidatorApplicable(item, entityCode, fieldType))
    .map(({ validator, ...metadata }) => metadata)
    .sort((a, b) => a.name.localeCompare(b.name) || b.version - a.version)
}
