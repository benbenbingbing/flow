import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { discoverExtensions, manifestDirectory } from './extensions/discover.mjs'
import { resolveImplementation } from './extensions/validate.mjs'

const repository = fileURLToPath(new URL('..', import.meta.url))
const virtualId = 'virtual:flow-mobile-extensions'

/** 构建时读取身份清单，只生成移动实现的静态导入；缺失实现绝不回退到 PC。 */
export function generateMobileExtensions() {
  const entries = discoverExtensions(path.join(repository, 'workflow-mobile'), { platform: 'mobile', implementationRoot: path.join(repository, 'workflow-mobile') }).filter(entry => entry.enabled !== false && entry.platforms?.mobile)
  const imports = [], registrations = []
  entries.forEach((entry, index) => {
    const { implementation, capabilities } = entry.platforms.mobile
    if (!['FIELD', 'FORM', 'VALIDATOR', 'FIELD_EVENT_HANDLER'].includes(entry.type)) throw new Error(`尚不支持的移动扩展类型：${entry.type}`)
    const kinds = entry.type === 'VALIDATOR' ? ['OBJECT', 'CLASS', 'FACTORY'] : entry.type === 'FIELD_EVENT_HANDLER' ? ['FUNCTION'] : ['COMPONENT']
    if (!kinds.includes(implementation.kind)) throw new Error(`移动扩展实现类型不符：${entry.name}`)
    const filename = resolveImplementation(path.join(repository, 'workflow-mobile'), implementation)
    const variable = `extension${index}`
    imports.push(`import ${implementation.export === 'default' ? variable : `{ ${implementation.export} as ${variable} }`} from ${JSON.stringify(filename)}`)
    if (entry.type === 'FIELD_EVENT_HANDLER') {
      registrations.push(`registerFieldEventHandler(${JSON.stringify(entry.name)}, ${variable}, ${JSON.stringify({ ...entry.metadata, version: entry.version, label: entry.label, description: entry.description })})`)
    } else if (entry.type === 'VALIDATOR') {
      const instance = implementation.kind === 'CLASS' ? `new ${variable}()` : implementation.kind === 'FACTORY' ? `${variable}()` : variable
      registrations.push(`registerCustomValidator(${JSON.stringify(entry.name)}, ${instance}, ${JSON.stringify({ ...entry.metadata, version: entry.version, label: entry.label, description: entry.description })})`)
    } else {
      registrations.push(`registerMobileExtension({ ...${JSON.stringify({ type: entry.type, name: entry.name, version: entry.version, ...entry.metadata, ...capabilities })}, component: ${variable} })`)
    }
  })
  return ["import { registerMobileExtension } from '@flow/workflow-mobile-ui'", "import { registerCustomValidator } from '@flow/workflow-core/extensions/core/registries/validatorRegistry'", "import { registerFieldEventHandler } from '@flow/workflow-core/extensions/core/registries/fieldEventHandlerRegistry'", ...imports, ...registrations].join('\n')
}

export function mobileExtensionsPlugin() {
  return {
    name: 'flow-mobile-extensions',
    resolveId(id) { if (id === virtualId) return '\0' + virtualId },
    load(id) { if (id === '\0' + virtualId) return generateMobileExtensions() },
    configureServer(server) {
      const manifests = manifestDirectory
      const implementationRoot = path.join(repository, 'workflow-mobile')
      const readFieldImplementations = () => new Set(discoverExtensions(implementationRoot, { platform: 'mobile', implementationRoot })
        .filter(entry => entry.type === 'FIELD' && entry.platforms?.mobile)
        .map(entry => path.resolve(implementationRoot, entry.platforms.mobile.implementation.path)))
      let fieldImplementations = readFieldImplementations()
      server.watcher.add(manifests)
      server.watcher.on('all', (_, filename) => {
        if (!filename.startsWith(manifests) && !fieldImplementations.has(filename)) return
        try { fieldImplementations = readFieldImplementations() } catch (error) {
          server.ws.send({ type: 'error', err: { message: error.message, stack: error.stack, plugin: 'flow-mobile-extensions' } })
          return
        }
        const module = server.moduleGraph.getModuleById('\0' + virtualId)
        if (module) server.moduleGraph.invalidateModule(module)
        // 事件能力由组件静态选项提取；完整重载同时刷新注册表和事件目录。
        server.ws.send({ type: 'full-reload' })
      })
    }
  }
}
