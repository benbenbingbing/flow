import { readFileSync, realpathSync, statSync } from 'node:fs'
import path from 'node:path'
import { frontendRoot, businessModulePattern } from './modules.mjs'

const schema = JSON.parse(readFileSync(new URL('../../extensions/schemas/extension.schema.json', import.meta.url), 'utf8'))
const multiVersionTypes = new Set(['FORM', 'LIST', 'FIELD', 'VALIDATOR', 'FIELD_EVENT_HANDLER'])
const componentTypes = new Set(['FORM', 'LIST', 'FIELD', 'LIST_CELL', 'LIST_BUTTON', 'ACTION_CONDITION'])

/** 执行本项目清单 Schema 使用的校验关键字；错误携带 JSON 属性路径，供构建输出定位。 */
function validateValue(value, rule, location) {
  const fail = message => { throw new Error(`${location}: ${message}`) }
  if (rule.oneOf) {
    const matches = rule.oneOf.filter(candidate => {
      try { validateValue(value, candidate, location); return true } catch { return false }
    })
    if (matches.length !== 1) fail('path 与 package 必须且只能填写一个')
  }
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

/** 校验所有平台的引用形状，不读组件源码，禁用模块也不能夹带任意导入。 */
function validateReference(reference, { moduleId, platform = 'pc' } = {}) {
  if (Boolean(reference.path) === Boolean(reference.package)) throw new Error('path 与 package 必须且只能填写一个')
  if (reference.package) {
    const match = /^@flow\/(biz-[a-z][a-z0-9]*(?:-[a-z0-9]+)*)\/(pc|mobile)\/([A-Za-z0-9_-]+(?:\/[A-Za-z0-9_-]+)*)$/.exec(reference.package)
    if (!match || match[1] !== moduleId || match[2] !== platform) {
      throw new Error(`业务实现只能引用本模块 ${moduleId || 'biz-*'} 的 ${platform} 公开包入口：${reference.package}`)
    }
    return
  }
  if (businessModulePattern.test(moduleId || '')) throw new Error(`业务模块 ${moduleId} 必须通过 package 引用公开入口`)
  const value = reference.path
  if (!/^src\/.+\.(vue|js|mjs)$/.test(value) || value.includes('\\') || value.split('/').includes('..')) {
    throw new Error(`实现路径必须是 src/ 下的本地 .vue/.js/.mjs 文件：${value}`)
  }
  if (/\/extensions\/(core|generated|manifests|schemas)\//.test(value) || /\/extensions\/(register|index|manifest)\.js$/.test(value)) {
    throw new Error(`扩展实现不能引用注册基础设施：${value}`)
  }
}

/**
 * 将公开包入口解析到源码用于校验和监听，不加载/执行业务，也不要求先生成 dist。
 * 只支持本仓库声明的简单 dist 导出；匹配 exports 后仍验证真实路径，防止符号链接绕过边界。
 */
export function resolveImplementation(root, reference, options = {}) {
  validateReference(reference, options)
  if (reference.package) {
    const workspace = realpathSync(options.repositoryRoot || frontendRoot)
    const packageRoot = realpathSync(path.join(workspace, 'packages', options.moduleId))
    if (packageRoot !== path.join(workspace, 'packages', options.moduleId)) throw new Error(`业务包不能通过符号链接越界：${reference.package}`)
    const descriptor = JSON.parse(readFileSync(path.join(packageRoot, 'package.json'), 'utf8'))
    if (descriptor.name !== `@flow/${options.moduleId}`) throw new Error(`业务包名称不匹配：${reference.package}`)
    const entry = '.' + reference.package.slice(descriptor.name.length)
    const exports = descriptor.exports || {}
    let target = exports[entry]
    if (target === undefined) {
      const patterns = Object.keys(exports).filter(key => key.split('*').length === 2)
        .sort((a, b) => b.indexOf('*') - a.indexOf('*') || b.length - a.length)
      for (const pattern of patterns) {
        const [prefix, suffix] = pattern.split('*')
        if (!entry.startsWith(prefix) || !entry.endsWith(suffix)) continue
        const wildcard = entry.slice(prefix.length, suffix ? -suffix.length : undefined)
        target = typeof exports[pattern] === 'string' ? exports[pattern].replaceAll('*', wildcard) : exports[pattern]
        break
      }
    }
    const platform = options.platform || 'pc'
    if (typeof target !== 'string' || !target.startsWith(`./dist/${platform}/`) || !/\.(vue|js|mjs)$/.test(target)
      || target.includes('\\') || target.split('/').includes('..')) {
      throw new Error(`未导出的业务入口或导出未指向本平台 dist：${reference.package}`)
    }
    const sourceRoot = realpathSync(path.join(packageRoot, 'src', platform))
    if (sourceRoot !== path.join(packageRoot, 'src', platform)) throw new Error(`业务源码目录越界：${reference.package}`)
    const filename = realpathSync(path.join(packageRoot, target.replace('./dist/', 'src/')))
    if (!filename.startsWith(sourceRoot + path.sep) || !statSync(filename).isFile()) throw new Error(`业务实现路径越界或不是文件：${reference.package}`)
    return filename
  }
  const sourceRoot = realpathSync(path.join(root, 'src'))
  const filename = realpathSync(path.join(root, reference.path))
  if (!filename.startsWith(sourceRoot + path.sep) || !statSync(filename).isFile()) throw new Error(`实现路径越界或不是文件：${reference.path}`)
  return filename
}

/** 生成器只保留公开包名；已校验的本地文件继续使用绝对路径供 Vite 加载。 */
export function implementationImport(root, reference) {
  return reference.package || path.join(root, reference.path).split(path.sep).join('/')
}

/** 包组件通过 dist 加载；监听源码和对应产物，确保复制构建完成后事件目录再次刷新。 */
export function implementationWatchFiles(root, reference, options = {}) {
  const filename = resolveImplementation(root, reference, options)
  if (!reference.package) return [filename]
  const segment = `${path.sep}${options.moduleId}${path.sep}src${path.sep}`
  return [filename, filename.replace(segment, `${path.sep}${options.moduleId}${path.sep}dist${path.sep}`)]
}

/** 校验单份清单及类型专属属性；这里只读源码，不执行模块或业务工厂。 */
export function validateManifest(manifest, root, filename, options = {}) {
  const { platform = 'pc', implementationRoot = root, structureOnly = false } = options
  validateValue(manifest, schema, filename)
  const { type, implementation, metadata = {}, hooks = {} } = manifest
  const fail = message => { throw new Error(`${filename}: ${message}`) }
  if (componentTypes.has(type) && implementation.kind !== 'COMPONENT') fail(`${type} 需要 COMPONENT`)
  if (['LIST_ACTION', 'PERMISSION_PROVIDER', 'FIELD_EVENT_HANDLER'].includes(type) && implementation.kind !== 'FUNCTION') fail(`${type} 需要 FUNCTION`)
  if (type === 'VALIDATOR' && !['OBJECT', 'CLASS', 'FACTORY'].includes(implementation.kind)) fail('VALIDATOR 需要 OBJECT/CLASS/FACTORY')
  if ((type === 'LIST_ACTION') !== Boolean(manifest.targets)) fail('targets 只允许且必须出现在 LIST_ACTION')
  if (manifest.defaultConfig && type !== 'ACTION_CONDITION') fail('defaultConfig 仅用于 ACTION_CONDITION')
  if ((manifest.aliases || manifest.defaultForFieldTypes) && type !== 'FIELD') fail('别名和默认字段类型仅用于 FIELD')
  if (hooks.createDefault && type !== 'ACTION_CONDITION') fail('createDefault 仅用于 ACTION_CONDITION')
  if (hooks.createDefault && manifest.defaultConfig) fail('createDefault 和 defaultConfig 不能同时配置')
  for (const ref of Object.values(hooks)) if (ref.kind !== 'FUNCTION') fail('钩子必须使用 FUNCTION')
  if (metadata.usageContexts && !['FORM', 'LIST'].includes(type)) fail('usageContexts 仅用于 FORM/LIST')
  if (metadata.artifactDigest && !['FORM', 'LIST'].includes(type)) fail('artifactDigest 仅用于 FORM/LIST')
  if ((metadata.nodeTypes || metadata.supportedBindings) && type !== 'FIELD') fail('nodeTypes/supportedBindings 仅用于 FIELD')
  const scope = metadata.supportedEntityCodes || []
  if (scope.includes('*') && scope.length !== 1) fail('全部实体范围只使用 ["*"]')
  const keys = (metadata.configSchema || []).map(item => item.key)
  if (new Set(keys).size !== keys.length) fail('configSchema 参数 key 重复')
  for (const ref of [implementation, ...Object.values(hooks)]) validateReference(ref, { ...options, platform: 'pc' })
  if (manifest.platforms?.mobile) {
    const mobile = manifest.platforms.mobile.implementation
    validateReference(mobile, { ...options, platform: 'mobile' })
    if (!['FIELD', 'FORM', 'VALIDATOR', 'FIELD_EVENT_HANDLER'].includes(type)) fail(`尚不支持的移动扩展类型：${type}`)
    const kinds = type === 'VALIDATOR' ? ['OBJECT', 'CLASS', 'FACTORY'] : type === 'FIELD_EVENT_HANDLER' ? ['FUNCTION'] : ['COMPONENT']
    if (!kinds.includes(mobile.kind)) fail(`移动扩展实现类型不符：${manifest.name}`)
  }
  // 共享描述始终校验，源码路径只解析目标平台，移动构建不依赖 PC 组件文件存在。
  // metadata 仅供生成平台无关字段策略；应用构建仍校验自己的实现路径。
  const references = structureOnly || platform === 'metadata' ? [] : platform === 'mobile'
    ? (manifest.platforms?.mobile ? [manifest.platforms.mobile.implementation] : [])
    : [implementation, ...Object.values(hooks)]
  for (const ref of references) resolveImplementation(implementationRoot, ref, options)
}

/** 按最终注册规则校验启用项；同名组件允许多个版本，别名不能跨名称抢占。 */
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
      for (const alias of [name, ...(entry.aliases || [])]) {
        const key = alias.toLowerCase()
        if (fields.has(key) && fields.get(key) !== name) throw new Error(`${entry.sourceFile}: 组件别名 ${key} 冲突，已由 ${fields.get(key)} 使用`)
        fields.set(key, name)
      }
      for (const fieldType of entry.defaultForFieldTypes || []) {
        const key = fieldType.toUpperCase()
        if (defaults.has(key) && defaults.get(key) !== name) throw new Error(`${entry.sourceFile}: 字段默认组件 ${key} 冲突`)
        defaults.set(key, name)
      }
    }
  }
}
