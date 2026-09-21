import { readFileSync, realpathSync, statSync } from 'node:fs'
import path from 'node:path'

const schema = JSON.parse(readFileSync(new URL('../../src/extensions/schemas/extension.schema.json', import.meta.url), 'utf8'))
const multiVersionTypes = new Set(['FORM', 'LIST', 'NODE', 'VALIDATOR'])
const componentTypes = new Set(['FORM', 'LIST', 'FIELD', 'NODE', 'LIST_CELL', 'LIST_BUTTON', 'ACTION_CONDITION'])

/** 执行本项目清单 Schema 使用的校验关键字；错误携带 JSON 属性路径，供构建输出定位。 */
function validateValue(value, rule, location) {
  const fail = message => { throw new Error(`${location}: ${message}`) }
  if (rule.const !== undefined && value !== rule.const) fail(`必须为 ${rule.const}`)
  if (rule.enum && !rule.enum.includes(value)) fail(`必须为 ${rule.enum.join(' / ')}`)
  if (rule.type) {
    const valid = rule.type === 'array' ? Array.isArray(value)
      : rule.type === 'object' ? value !== null && typeof value === 'object' && !Array.isArray(value)
        : rule.type === 'integer' ? Number.isSafeInteger(value) : typeof value === rule.type
    if (!valid) fail(`必须为 ${rule.type}`)
  }
  if (typeof value === 'string') {
    if (rule.minLength && !value.trim()) fail('不能为空')
    if (rule.pattern && !new RegExp(rule.pattern).test(value)) fail('格式不合法')
  }
  if (typeof value === 'number' && rule.minimum !== undefined && value < rule.minimum) fail(`不能小于 ${rule.minimum}`)
  if (Array.isArray(value)) {
    if (rule.minItems && value.length < rule.minItems) fail('至少需要一个值')
    if (rule.uniqueItems && new Set(value.map(item => JSON.stringify(item))).size !== value.length) fail('值不能重复')
    value.forEach((item, i) => validateValue(item, rule.items || {}, `${location}[${i}]`))
  } else if (value && typeof value === 'object') {
    for (const key of rule.required || []) if (!Object.hasOwn(value, key)) fail(`缺少 ${key}`)
    for (const [key, item] of Object.entries(value)) {
      if (['__proto__', 'prototype', 'constructor'].includes(key)) fail(`禁止属性 ${key}`)
      if (rule.additionalProperties === false && !Object.hasOwn(rule.properties || {}, key)) fail(`未知属性 ${key}`)
      validateValue(item, rule.properties?.[key] || {}, `${location}.${key}`)
    }
  }
}

/** 将受控本地引用解析为真实文件；拒绝越界、符号链接逃逸和运行时注册模块。 */
export function resolveImplementation(root, reference) {
  const value = reference.path
  if (!/^src\/.+\.(vue|js|mjs)$/.test(value) || value.includes('\\') || value.split('/').includes('..')) {
    throw new Error(`实现路径必须是 src/ 下的本地 .vue/.js/.mjs 文件：${value}`)
  }
  const sourceRoot = realpathSync(path.join(root, 'src'))
  const filename = realpathSync(path.join(root, value))
  if (!filename.startsWith(sourceRoot + path.sep) || !statSync(filename).isFile()) throw new Error(`实现路径越界或不是文件：${value}`)
  if (/\/extensions\/(core|generated|manifests|schemas)\//.test(value) || /\/extensions\/(register|index|manifest)\.js$/.test(value)) {
    throw new Error(`扩展实现不能引用注册基础设施：${value}`)
  }
  return filename
}

/** 校验单份清单及类型专属属性；这里只读源码，不执行模块或业务工厂。 */
export function validateManifest(manifest, root, filename) {
  validateValue(manifest, schema, filename)
  const { type, implementation, metadata = {}, hooks = {} } = manifest
  const fail = message => { throw new Error(`${filename}: ${message}`) }
  if (componentTypes.has(type) && implementation.kind !== 'COMPONENT') fail(`${type} 需要 COMPONENT`)
  if (['LIST_ACTION', 'PERMISSION_PROVIDER'].includes(type) && implementation.kind !== 'FUNCTION') fail(`${type} 需要 FUNCTION`)
  if (type === 'VALIDATOR' && !['OBJECT', 'CLASS', 'FACTORY'].includes(implementation.kind)) fail('VALIDATOR 需要 OBJECT/CLASS/FACTORY')
  if ((type === 'LIST_ACTION') !== Boolean(manifest.targets)) fail('targets 只允许且必须出现在 LIST_ACTION')
  if (manifest.defaultConfig && type !== 'ACTION_CONDITION') fail('defaultConfig 仅用于 ACTION_CONDITION')
  if ((manifest.aliases || manifest.defaultForFieldTypes) && type !== 'FIELD') fail('别名和默认字段类型仅用于 FIELD')
  if (hooks.createDefault && type !== 'ACTION_CONDITION') fail('createDefault 仅用于 ACTION_CONDITION')
  if (hooks.createDefault && manifest.defaultConfig) fail('createDefault 和 defaultConfig 不能同时配置')
  if (hooks.migrateConfig && type !== 'NODE') fail('migrateConfig 仅用于 NODE')
  for (const ref of Object.values(hooks)) if (ref.kind !== 'FUNCTION') fail('钩子必须使用 FUNCTION')
  if (metadata.usageContexts && !['FORM', 'LIST'].includes(type)) fail('usageContexts 仅用于 FORM/LIST')
  if (metadata.artifactDigest && !['FORM', 'LIST'].includes(type)) fail('artifactDigest 仅用于 FORM/LIST')
  if ((metadata.nodeTypes || metadata.supportedBindings) && type !== 'NODE') fail('nodeTypes/supportedBindings 仅用于 NODE')
  const scope = metadata.supportedEntityCodes || []
  if (scope.includes('*') && scope.length !== 1) fail('全部实体范围只使用 ["*"]')
  const keys = (metadata.configSchema || []).map(item => item.key)
  if (new Set(keys).size !== keys.length) fail('configSchema 参数 key 重复')
  for (const ref of [implementation, ...Object.values(hooks)]) resolveImplementation(root, ref)
}

/** 按最终注册规则校验所有启用项，避免大小写、别名或单版本表的后写覆盖。 */
export function validateCollisions(entries) {
  const identities = new Map()
  const fields = new Map()
  const defaults = new Map()
  const reserve = (map, key, entry) => {
    if (map.has(key)) throw new Error(`${entry.sourceFile}: ${key} 与 ${map.get(key)} 冲突`)
    map.set(key, entry.sourceFile)
  }
  for (const entry of entries.filter(item => item.enabled !== false)) {
    const name = entry.type === 'FIELD' ? entry.name.toLowerCase()
      : entry.type === 'ACTION_CONDITION' ? entry.name.toUpperCase() : entry.name
    reserve(identities, `${entry.type}:${name}${multiVersionTypes.has(entry.type) ? `@${entry.version}` : ''}`, entry)
    if (entry.type === 'FIELD') {
      for (const alias of [name, ...(entry.aliases || [])]) reserve(fields, alias.toLowerCase(), entry)
      for (const fieldType of entry.defaultForFieldTypes || []) reserve(defaults, fieldType.toUpperCase(), entry)
    }
  }
}
