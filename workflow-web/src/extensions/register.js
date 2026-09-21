import entries from 'virtual:flow-extension-manifest'
import { extensionAdapters } from './core/adapters/index.js'
import { createExtensionInstaller } from './core/installer.js'
import { publishExtensionCatalog } from './core/catalog.js'

const install = createExtensionInstaller(extensionAdapters, publishExtensionCatalog)

/**
 * 主应用和 Embed 唯一安装入口，必须在宿主挂载前调用。
 * 新增实现只需代码及 JSON；清单变化由构建插件触发整页刷新。
 */
export function registerApplicationExtensions(options = {}) {
  install(entries, options)
}
