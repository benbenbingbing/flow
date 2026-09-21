import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'
import { discoverExtensions } from '../../../../build/extensions/discover.mjs'
import { createExtensionInstaller } from '../../core/installer.js'
import { extensionAdapters } from '../../core/adapters/index.js'

const root = fileURLToPath(new URL('../../../..', import.meta.url))
const install = createExtensionInstaller(extensionAdapters)

/** Node 校验测试加载正式 JSON 与实际 JS 实现；不复制金额注册配置，不伪造 Vue 实现。 */
export async function installTestValidators() {
  const entries = await Promise.all(discoverExtensions(root).filter(item => item.type === 'VALIDATOR' && item.enabled !== false).map(async descriptor => {
    const module = await import(pathToFileURL(path.join(root, descriptor.implementation.path)).href)
    return { descriptor, implementation: module[descriptor.implementation.export] }
  }))
  install(entries)
}
