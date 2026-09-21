import { readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { validateManifest, validateCollisions } from './validate.mjs'

const origins = { platform: 'PLATFORM', common: 'COMMON', business: 'BUSINESS', examples: 'EXAMPLE' }

/** 递归读取集中目录；排序保证同一源码的构建结果稳定，非清单文件不会被安装。 */
export function discoverExtensions(root) {
  const directory = path.join(root, 'src/extensions/manifests')
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
    const origin = origins[relative[0]]
    if (!origin) throw new Error(`${filename}: 未知归属目录 ${relative[0]}`)
    let value
    try { value = JSON.parse(readFileSync(filename, 'utf8')) } catch (error) { throw new Error(`${filename}: ${error.message}`) }
    validateManifest(value, root, filename)
    if (origin !== 'PLATFORM' && (value.aliases?.length || value.defaultForFieldTypes?.length)) throw new Error(`${filename}: 默认字段映射仅由 platform 定义`)
    return { ...value, origin, module: relative[0] === 'business' ? relative[1] : relative[0], sourceFile: path.relative(root, filename).split(path.sep).join('/') }
  })
  validateCollisions(entries)
  return entries
}
