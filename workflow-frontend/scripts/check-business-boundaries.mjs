import { existsSync, readFileSync, readdirSync, realpathSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse } from '@babel/parser'
import { parse as parseSfc } from '@vue/compiler-sfc'

const repository = fileURLToPath(new URL('..', import.meta.url))
const assemblyModules = new Set(['virtual:flow-extension-manifest', 'virtual:flow-mobile-extensions'])
const scriptExtension = /\.(?:[cm]?[jt]sx?|vue)$/
const testPath = /(?:^|[/\\])(?:__tests__|tests|fixtures)(?:[/\\]|$)|\.(?:test|spec)\./
const cleanId = id => String(id || '').replace(/^\0/, '').replace(/[?#].*$/, '')
const inside = (filename, directory) => filename === directory || filename.startsWith(directory + path.sep)
function normalize(filename) {
  let ancestor = path.resolve(filename)
  const suffix = []
  // 测试夹具、待生成 dist 可能尚不存在；仍需消除 /var → /private/var 等父目录符号链接。
  while (!existsSync(ancestor) && path.dirname(ancestor) !== ancestor) {
    suffix.unshift(path.basename(ancestor))
    ancestor = path.dirname(ancestor)
  }
  return path.join(realpathSync(ancestor), ...suffix)
}
const packageName = source => source.startsWith('@') ? source.split('/').slice(0, 2).join('/') : source.split('/')[0]
const isBare = source => !source.startsWith('.') && !source.startsWith('/') && !source.startsWith('\0')

function findFile(filename) {
  for (const candidate of [filename, ...['.js', '.mjs', '.ts', '.tsx', '.vue', '/index.js', '/index.ts'].map(extension => filename + extension)]) {
    if (existsSync(candidate) && statSync(candidate).isFile()) return normalize(candidate)
  }
  return filename
}

function exportTarget(value) {
  if (typeof value === 'string') return value
  if (!value || typeof value !== 'object') return null
  // 构建检查与浏览器 ESM 的入口保持一致；类型声明不属于运行时依赖。
  for (const condition of ['browser', 'import', 'default', 'node']) {
    const target = exportTarget(value[condition])
    if (target) return target
  }
  return null
}

/** 只解析 package.json 明确公开的入口；null 排除项不能被通配符重新开放。 */
function resolveExport(pkg, source) {
  const subpath = source === pkg.name ? '.' : '.' + source.slice(pkg.name.length)
  const exports = pkg.json.exports
  if (typeof exports === 'string') return subpath === '.' ? exports : null
  if (!exports || typeof exports !== 'object') return null
  if (!Object.keys(exports).some(key => key.startsWith('.'))) return subpath === '.' ? exportTarget(exports) : null
  if (Object.hasOwn(exports, subpath)) return exportTarget(exports[subpath])
  const patterns = Object.keys(exports).filter(key => key.includes('*')).sort((a, b) => b.indexOf('*') - a.indexOf('*') || b.length - a.length)
  for (const key of patterns) {
    const [prefix, suffix] = key.split('*')
    if (!subpath.startsWith(prefix) || !subpath.endsWith(suffix)) continue
    const target = exportTarget(exports[key])
    const replacement = subpath.slice(prefix.length, suffix ? -suffix.length : undefined)
    return target && target.replaceAll('*', replacement)
  }
  return null
}

/** 读取工作区包边界，供源码扫描和 Vite 解析后的模块图共同使用。 */
export function createBusinessBoundaryContext(root = repository) {
  root = normalize(root)
  const packages = new Map()
  const folder = path.join(root, 'packages')
  for (const entry of existsSync(folder) ? readdirSync(folder, { withFileTypes: true }) : []) {
    if (!entry.isDirectory()) continue
    const directory = path.join(folder, entry.name)
    const filename = path.join(directory, 'package.json')
    if (!existsSync(filename)) continue
    const json = JSON.parse(readFileSync(filename, 'utf8'))
    packages.set(json.name, { name: json.name, directory: normalize(directory), json, business: entry.name.startsWith('biz-') })
  }
  const applications = ['workflow-web', 'workflow-mobile'].map(name => ({ name, directory: path.join(root, name) }))
  function classify(id) {
    const value = cleanId(id)
    if (assemblyModules.has(value)) return { assembly: true }
    if (!path.isAbsolute(value)) return {}
    const filename = normalize(value)
    const pkg = [...packages.values()].find(candidate => inside(filename, candidate.directory))
    const app = applications.find(candidate => inside(filename, candidate.directory))
    const segments = pkg ? path.relative(pkg.directory, filename).split(path.sep) : []
    return { filename, pkg, app, platform: segments.find(segment => ['pc', 'mobile'].includes(segment)) }
  }
  function resolve(source, importer) {
    if (source.startsWith('.')) return findFile(path.resolve(path.dirname(cleanId(importer)), source))
    if (source.startsWith('@/')) {
      const app = classify(importer).app
      return app ? findFile(path.join(app.directory, 'src', source.slice(2))) : null
    }
    if (path.isAbsolute(source)) return findFile(cleanId(source))
    const pkg = packages.get(packageName(source))
    if (!pkg) return null
    const target = resolveExport(pkg, cleanId(source))
    if (!target) return null
    // 公共包的 dist 是 src 的复制产物，源码检查无需先构建，也不能读取过期 dist。
    const sourceTarget = target.replace(/^\.\/dist\//, './src/')
    return findFile(path.resolve(pkg.directory, sourceTarget))
  }
  return { root, packages, applications, classify, resolve }
}

/**
 * 检查一条实际导入边，返回错误文本；source 为 null 表示只掌握构建模块图的解析结果。
 * 声明检查负责公开入口和依赖清单，解析结果检查负责抵御 alias、相对路径及传递依赖绕过。
 */
export function inspectBusinessDependency(context, importer, source, resolvedId = null) {
  const from = context.classify(importer)
  const bare = source && isBare(source)
  const targetPackage = bare ? context.packages.get(packageName(source)) : null
  const resolved = resolvedId || (source ? context.resolve(source, importer) : null)
  const to = context.classify(resolved)
  const target = to.pkg || targetPackage
  const label = `${path.relative(context.root, cleanId(importer))} → ${source || resolvedId}`
  const fail = message => `${message}：${label}`

  if (target?.business && !from.pkg?.business && !from.assembly) return fail('产品代码不能依赖业务包；请由扩展清单装配')
  if (!from.pkg?.business) return null
  if (to.app || (source && /^(?:@\/|~\/|\/src\/|workflow-(?:web|mobile)(?:\/|$))/.test(source))) return fail('业务包不能引用应用私有实现；请使用 SDK 宿主能力')
  if (from.platform === 'pc' && (to.platform === 'mobile' || target?.name === '@flow/workflow-mobile-ui' || /^(?:vant)(?:\/|$)/.test(source || ''))) return fail('PC 业务不能依赖移动端实现')
  if (from.platform === 'mobile' && (to.platform === 'pc' || /^(?:element-plus|@element-plus\/)/.test(source || '') || /[/\\](?:element-plus|@element-plus)[/\\]/.test(resolved || ''))) return fail('移动业务不能依赖 PC 实现')
  if (!from.platform && (to.platform || /^(?:element-plus|@element-plus\/|vant(?:\/|$))/.test(source || '') || target?.name === '@flow/workflow-mobile-ui')) return fail('跨端业务逻辑不能依赖平台 UI；请放入 pc 或 mobile 入口')

  if (source && bare) {
    const name = packageName(source)
    if (name !== from.pkg.name && !Object.hasOwn({ ...from.pkg.json.dependencies, ...from.pkg.json.peerDependencies, ...from.pkg.json.optionalDependencies }, name)) return fail(`业务包必须显式声明运行依赖 ${name}`)
    if (target && !resolveExport(target, cleanId(source))) return fail('业务包必须使用 package.json exports 公开入口')
  } else if (source && target && target !== from.pkg) {
    return fail('业务包不得通过文件路径访问其他包；请声明依赖并使用公开包入口')
  }
  // 构建图不再保留原始 specifier，但仍能核对真实目标包是否被明确声明。
  if (!source && target && target !== from.pkg && !Object.hasOwn({ ...from.pkg.json.dependencies, ...from.pkg.json.peerDependencies, ...from.pkg.json.optionalDependencies }, target.name)) return fail(`业务包必须显式声明运行依赖 ${target.name}`)
  if (resolved && path.isAbsolute(cleanId(resolved)) && inside(cleanId(resolved), context.root) && !target && !to.app && !cleanId(resolved).includes(`${path.sep}node_modules${path.sep}`)) return fail('业务包不能引用工作区私有文件')
  return null
}

function literalValue(node) {
  if (node?.type === 'StringLiteral') return node.value
  if (node?.type === 'TemplateLiteral' && node.expressions.length === 0) return node.quasis[0].value.cooked
  return null
}

/** AST 收集覆盖 import/export/require/import()，注释和普通字符串不会被当成依赖。 */
export function readModuleDependencies(source, filename) {
  const imports = []
  const scripts = []
  if (filename.endsWith('.vue')) {
    const { descriptor, errors } = parseSfc(source, { filename })
    if (errors.length) throw new Error(errors.map(error => error.message || error).join('; '))
    for (const block of [descriptor.script, descriptor.scriptSetup, ...descriptor.styles]) {
      if (block?.src) imports.push({ source: block.src })
    }
    for (const block of [descriptor.script, descriptor.scriptSetup]) if (block) scripts.push(block.content)
  } else scripts.push(source)
  function visit(node) {
    if (!node || typeof node !== 'object') return
    if (['ImportDeclaration', 'ExportNamedDeclaration', 'ExportAllDeclaration'].includes(node.type) && node.source) imports.push({ source: node.source.value })
    if (node.type === 'ImportExpression') imports.push({ source: literalValue(node.source), dynamic: true })
    if (node.type === 'CallExpression') {
      if (node.callee.type === 'Import' || (node.callee.type === 'Identifier' && node.callee.name === 'require')) imports.push({ source: literalValue(node.arguments[0]), dynamic: true })
      if (node.callee.type === 'MemberExpression' && node.callee.object?.type === 'MetaProperty' && node.callee.object.meta.name === 'import' && ['glob', 'globEager'].includes(node.callee.property.name)) imports.push({ source: null, dynamic: true })
    }
    if (node.type === 'TSImportEqualsDeclaration' && node.moduleReference.type === 'TSExternalModuleReference') imports.push({ source: literalValue(node.moduleReference.expression) })
    for (const value of Object.values(node)) {
      if (Array.isArray(value)) value.forEach(visit)
      else if (value && typeof value === 'object') visit(value)
    }
  }
  for (const script of scripts) visit(parse(script, { sourceType: 'unambiguous', allowUndeclaredExports: true, createImportExpressions: true, plugins: ['jsx', 'typescript', 'decorators-legacy'] }))
  return imports
}

function sourceFiles(folder) {
  if (!existsSync(folder)) return []
  return readdirSync(folder, { withFileTypes: true }).flatMap(entry => {
    const filename = path.join(folder, entry.name)
    if (testPath.test(filename)) return []
    if (entry.isDirectory()) return sourceFiles(filename)
    return scriptExtension.test(filename) ? [filename] : []
  })
}

/** 扫描全部产品/业务源码（含未启用业务），失败时返回所有位置，方便一次修复依赖边界。 */
export function checkBusinessBoundaries(root = repository) {
  const context = createBusinessBoundaryContext(root)
  const files = [...context.packages.values(), ...context.applications].flatMap(entry => sourceFiles(path.join(entry.directory, 'src')))
  const errors = []
  for (const filename of files) {
    try {
      for (const dependency of readModuleDependencies(readFileSync(filename, 'utf8'), filename)) {
        if (dependency.source === null) {
          if (context.classify(filename).pkg?.business) errors.push(`业务包只允许可静态解析的显式导入，不允许计算式 import/require/glob：${path.relative(root, filename)}`)
          continue
        }
        const error = inspectBusinessDependency(context, filename, dependency.source)
        if (error) errors.push(error)
      }
    } catch (error) { errors.push(`${path.relative(root, filename)}：${error.message}`) }
  }
  return { errors, files: files.length }
}

if (process.argv[1] && normalize(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const result = checkBusinessBoundaries()
  if (result.errors.length) {
    console.error(`前端业务依赖边界检查失败：\n${result.errors.join('\n')}`)
    process.exitCode = 1
  } else console.log(`前端业务依赖边界检查通过（${result.files} 个源码文件）`)
}
