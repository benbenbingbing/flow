import { fileURLToPath } from 'node:url'
import { discoverExtensions } from './discover.mjs'
import { writeFieldDefinitions } from './generate.mjs'

// 公共核心只消费清单的字段策略，不需要桌面或移动组件源码存在。
const coreRoot = fileURLToPath(new URL('../../packages/workflow-core', import.meta.url))
const entries = discoverExtensions(coreRoot, { platform: 'metadata' })
writeFieldDefinitions(entries, coreRoot, { check: process.argv.includes('--check') })
