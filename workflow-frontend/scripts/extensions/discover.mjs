import { readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

import { validateManifest, validateCollisions } from './validate.mjs'
import { businessModulePattern, resolveEnabledModules } from './modules.mjs'

export const manifestDirectory = fileURLToPath(new URL('../../extensions/manifests', import.meta.url))

const origins = { platform: 'PLATFORM', common: 'COMMON', examples: 'EXAMPLE' }

/** 递归读取集中目录；排序保证同一源码的构建结果稳定，非清单文件不会被安装。 */
export function discoverExtensions(root, options = {}) {
  const directory = options.manifestDirectory || manifestDirectory
  const enabledModules = resolveEnabledModules(directory, options)
  const files = []
  const visit = folder => {
    for (const entry of readdirSync(folder, { withFileTypes: true })) {
      const filename = path.join(folder, entry.name)
      if (entry.isDirectory()) visit(filename)
      else if (entry.isFile() && entry.name.endsWith('.extension.json')) files.push(filename)
    }
  }
  visit(directory)
  const entries = files.sort().map(filename => {
    const relative = path.relative(directory, filename).split(path.sep)
    const moduleId = relative[0]
    const origin = businessModulePattern.test(moduleId) ? 'BUSINESS' : Object.hasOwn(origins, moduleId) ? origins[moduleId] : undefined
    if (!origin) throw new Error(`${filename}: 未知归属目录 ${relative[0]}`)
    let value
    try { value = JSON.parse(readFileSync(filename, 'utf8')) } catch (error) { throw new Error(`${filename}: ${error.message}`) }
    // 全量结构校验在选择之前执行，未交付业务不要求其组件源码和构建产物存在。
    validateManifest(value, root, filename, { ...options, moduleId, structureOnly: true })
    if (origin !== 'PLATFORM' && (value.aliases?.length || value.defaultForFieldTypes?.length)) throw new Error(`${filename}: 默认字段映射仅由 platform 定义`)
    return { ...value, origin, module: moduleId, sourceFile: path.relative(root, filename).split(path.sep).join('/') }
  })
  const selected = entries.filter(entry => entry.origin !== 'BUSINESS' || enabledModules.has(entry.module))
  // 描述附带的 origin/module/sourceFile 不属于 JSON 协议，实际引用校验使用原始清单。
  for (const { origin, module, sourceFile, ...manifest } of selected) validateManifest(manifest, root, sourceFile, { ...options, moduleId: module })
  validateCollisions(selected)
  return selected
}
