import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'

/** 为 Vite 生成静态导入。仅导入启用项，同一实现/钩子导出复用一个 import。 */
export function generateExtensionModule(entries, root) {
  const imports = new Map()
  const reference = ref => {
    const key = `${ref.path}#${ref.export}`
    if (!imports.has(key)) imports.set(key, { name: `implementation${imports.size}`, ref })
    return imports.get(key).name
  }
  const values = entries.filter(item => item.enabled !== false).map(entry => {
    const hooks = Object.entries(entry.hooks || {}).map(([name, ref]) => `${JSON.stringify(name)}: ${reference(ref)}`)
    return `{ descriptor: ${JSON.stringify(entry)}, implementation: ${reference(entry.implementation)}, hooks: {${hooks.join(',')}} }`
  })
  const lines = [...imports.values()].map(({ name, ref }) => {
    const binding = ref.export === 'default' ? name : `{ ${ref.export} as ${name} }`
    return `import ${binding} from ${JSON.stringify(path.join(root, ref.path).split(path.sep).join('/'))}`
  })
  return lines.join('\n') + `\nexport default [${values.join(',\n')}]\n`
}

/** 生成纯数据策略供无 Vue 的字段规则/Node 单测使用；源头仍只有平台 JSON 清单。 */
export function writeFieldDefinitions(entries, root, { check = false } = {}) {
  const definitions = entries.filter(item => item.origin === 'PLATFORM' && item.type === 'FIELD' && item.enabled !== false)
    .map(({ name, aliases = [], defaultForFieldTypes = [], metadata }) => ({ name, aliases, defaultForFieldTypes, supportedFieldTypes: metadata.supportedFieldTypes || [] }))
  const filename = path.join(root, 'src/extensions/generated/field-definitions.js')
  const content = '/** 自动生成：仅修改 manifests/platform/fields/*.extension.json，然后运行 npm run extensions:generate。 */\n'
    + `export default ${JSON.stringify(definitions, null, 2)}\n`
  let existing = ''
  try { existing = readFileSync(filename, 'utf8') } catch { /* 首次构建时创建目录与文件。 */ }
  if (check && existing !== content) throw new Error('字段策略已过期，请运行 npm run extensions:generate')
  if (!check && existing !== content) {
    mkdirSync(path.dirname(filename), { recursive: true })
    writeFileSync(filename, content)
  }
  return filename
}
