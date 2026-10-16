import { fileURLToPath } from 'node:url'
import { discoverExtensions } from '../build/extensions/discover.mjs'
import { writeFieldDefinitions } from '../build/extensions/generate.mjs'

const root = fileURLToPath(new URL('..', import.meta.url))
const entries = discoverExtensions(root)
writeFieldDefinitions(entries, root, { check: !process.argv.includes('--generate') })
console.info(`扩展清单检查通过：${entries.length} 项，${entries.filter(item => item.enabled !== false).length} 项启用。`)
