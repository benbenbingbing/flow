import { fileURLToPath } from 'node:url'
import path from 'node:path'
import { discoverExtensions, manifestDirectory } from './discover.mjs'
import { generateExtensionModule, writeFieldDefinitions } from './generate.mjs'

const publicId = 'virtual:flow-extension-manifest'
const resolvedId = `\0${publicId}`

/** 主应用和 Embed 共用的构建插件；JSON 是编译期输入，不从业务配置动态 import。 */
export function flowExtensionsPlugin(options = {}) {
  let root = fileURLToPath(new URL('../..', import.meta.url))
  let fieldImplementations = new Set()
  const refresh = () => {
    const entries = discoverExtensions(root, options)
    fieldImplementations = new Set(entries.filter(entry => entry.type === 'FIELD').map(entry => path.resolve(root, entry.implementation.path)))
    writeFieldDefinitions(entries, root)
    return entries
  }
  return {
    name: 'flow-json-extensions',
    enforce: 'pre',
    configResolved(config) { root = config.root; refresh() },
    resolveId(id) { return id === publicId ? resolvedId : null },
    load(id) {
      if (id !== resolvedId) return null
      const entries = refresh()
      for (const entry of entries) this.addWatchFile(path.join(root, entry.sourceFile))
      return generateExtensionModule(entries, root)
    },
    configureServer(server) {
      const folder = options.manifestDirectory || manifestDirectory
      server.watcher.add(folder)
      const onChange = filename => {
        if (!fieldImplementations.has(filename) && (!filename.startsWith(folder + path.sep) || !filename.endsWith('.extension.json'))) return
        try {
          refresh()
          const module = server.moduleGraph.getModuleById(resolvedId)
          if (module) server.moduleGraph.invalidateModule(module)
          // 静态事件目录在安装时提取，组件源码或清单变化均重载，避免 HMR 只更新组件而留下旧目录。
          server.ws.send({ type: 'full-reload' })
        } catch (error) {
          server.ws.send({ type: 'error', err: { message: error.message, stack: error.stack, plugin: 'flow-json-extensions' } })
        }
      }
      for (const event of ['add', 'change', 'unlink']) server.watcher.on(event, onChange)
      server.httpServer?.once('close', () => {
        for (const event of ['add', 'change', 'unlink']) server.watcher.off(event, onChange)
      })
    }
  }
}
