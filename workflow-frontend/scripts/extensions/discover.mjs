import { readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { validateManifest, validateCollisions } from './validate.mjs'
import { discoverBusinessModules, extensionLayout, resolveEnabledModules } from './modules.mjs'

export const manifestDirectory = extensionLayout().manifestDirectory

const origins = { platform: 'PLATFORM', common: 'COMMON', examples: 'EXAMPLE' }

/**
 * 监听整个 custom 根才能发现新模块/新清单；过滤掉业务源码和 dist，避免复制构建重复触发装配。
 * 不枚举现有 JSON 作为监听范围，删除后重新创建的清单也能恢复。
 */
export function extensionWatchDirectories(options = {}) {
  const layout = extensionLayout(options)
  return [layout.manifestDirectory, layout.customDirectory]
}

export function isExtensionManifestChange(filename, options = {}) {
  const layout = extensionLayout(options)
  const relative = path.relative(layout.customDirectory, filename).split(path.sep).join('/')
  return (filename.startsWith(layout.manifestDirectory + path.sep) && filename.endsWith('.extension.json'))
    || /^[^/]+\/manifests\/.+\.extension\.json$/.test(relative)
    || /^[^/]+\/package\.json$/.test(relative)
}

/** 读取产品清单和各自包含业务模块；排序保证稳定构建，业务身份只取所属 custom 目录。 */
export function discoverExtensions(root, options = {}) {
  const directory = extensionLayout(options).manifestDirectory
  const businessModules = discoverBusinessModules(options)
  const resolvedOptions = { ...options, businessModules }
  const enabledModules = resolveEnabledModules(resolvedOptions)
  const files = []
  const visit = (folder, moduleId, origin) => {
    for (const entry of readdirSync(folder, { withFileTypes: true })) {
      const filename = path.join(folder, entry.name)
      if (entry.isSymbolicLink()) throw new Error(`清单目录不能使用符号链接：${filename}`)
      if (entry.isDirectory()) visit(filename, moduleId, origin)
      else if (entry.isFile() && entry.name.endsWith('.extension.json')) files.push({ filename, moduleId, origin })
    }
  }
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.isFile() && entry.name.endsWith('.extension.json')) throw new Error(`${directory}/${entry.name}: 产品清单必须位于 platform/common/examples 目录`)
    if (!entry.isDirectory() && !entry.isSymbolicLink()) continue
    if (!Object.hasOwn(origins, entry.name) || entry.isSymbolicLink()) throw new Error(`${directory}: 未知归属目录 ${entry.name}，产品清单只允许 platform/common/examples`)
    visit(path.join(directory, entry.name), entry.name, origins[entry.name])
  }
  for (const module of businessModules.values()) visit(module.manifestDirectory, module.id, 'BUSINESS')
  // 沿用按路径码点排序，避免系统 locale 改变大小写字段的注册和生成文件顺序。
  const entries = files.sort((a, b) => a.filename < b.filename ? -1 : a.filename > b.filename ? 1 : 0).map(({ filename, moduleId, origin }) => {
    let value
    try { value = JSON.parse(readFileSync(filename, 'utf8')) } catch (error) { throw new Error(`${filename}: ${error.message}`) }
    // 全量结构校验在选择之前执行，未交付业务不要求其组件源码和构建产物存在。
    validateManifest(value, root, filename, { ...resolvedOptions, moduleId, structureOnly: true })
    if (origin !== 'PLATFORM' && (value.aliases?.length || value.defaultForFieldTypes?.length)) throw new Error(`${filename}: 默认字段映射仅由 platform 定义`)
    return { ...value, origin, module: moduleId, sourceFile: path.relative(root, filename).split(path.sep).join('/') }
  })
  const selected = entries.filter(entry => entry.origin !== 'BUSINESS' || enabledModules.has(entry.module))
  // 描述附带的 origin/module/sourceFile 不属于 JSON 协议，实际引用校验使用原始清单。
  for (const { origin, module, sourceFile, ...manifest } of selected) validateManifest(manifest, root, sourceFile, { ...resolvedOptions, moduleId: module })
  validateCollisions(selected)
  return selected
}
