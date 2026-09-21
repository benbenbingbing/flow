import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { discoverExtensions } from '../workflow-web/build/extensions/discover.mjs'
import { resolveImplementation } from '../workflow-web/build/extensions/validate.mjs'

const repository = fileURLToPath(new URL('..', import.meta.url))
const virtualId = 'virtual:flow-mobile-extensions'

/** 构建时读取身份清单，只生成移动实现的静态导入；缺失实现绝不回退到 PC。 */
export function generateMobileExtensions() {
  const entries = discoverExtensions(path.join(repository, 'workflow-web'), { platform: 'mobile', implementationRoot: path.join(repository, 'workflow-mobile') }).filter(entry => entry.enabled !== false && entry.platforms?.mobile)
  const imports = [], registrations = []
  entries.forEach((entry, index) => {
    const { implementation, capabilities } = entry.platforms.mobile
    if (!['FIELD', 'FORM', 'NODE', 'VALIDATOR'].includes(entry.type)) throw new Error(`尚不支持的移动扩展类型：${entry.type}`)
    if (entry.type === 'VALIDATOR' ? !['OBJECT', 'CLASS', 'FACTORY'].includes(implementation.kind) : implementation.kind !== 'COMPONENT') throw new Error(`移动扩展实现类型不符：${entry.name}`)
    const filename = resolveImplementation(path.join(repository, 'workflow-mobile'), implementation)
    const variable = `extension${index}`
    imports.push(`import ${implementation.export === 'default' ? variable : `{ ${implementation.export} as ${variable} }`} from ${JSON.stringify(filename)}`)
    if (entry.type === 'VALIDATOR') {
      const instance = implementation.kind === 'CLASS' ? `new ${variable}()` : implementation.kind === 'FACTORY' ? `${variable}()` : variable
      registrations.push(`registerCustomValidator(${JSON.stringify(entry.name)}, ${instance}, ${JSON.stringify({ ...entry.metadata, version: entry.version, label: entry.label, description: entry.description })})`)
    } else {
      registrations.push(`registerMobileExtension({ ...${JSON.stringify({ type: entry.type, name: entry.name, version: entry.version, ...capabilities })}, component: ${variable} })`)
    }
  })
  return ["import { registerMobileExtension } from '@flow/workflow-mobile-ui'", "import { registerCustomValidator } from '@flow/workflow-core/extensions/core/registries/validatorRegistry'", ...imports, ...registrations].join('\n')
}

export function mobileExtensionsPlugin() {
  return {
    name: 'flow-mobile-extensions',
    resolveId(id) { if (id === virtualId) return '\0' + virtualId },
    load(id) { if (id === '\0' + virtualId) return generateMobileExtensions() },
    configureServer(server) {
      const manifests = path.join(repository, 'workflow-web/src/extensions/manifests')
      server.watcher.add(manifests)
      server.watcher.on('all', (_, filename) => {
        if (!filename.startsWith(manifests)) return
        const module = server.moduleGraph.getModuleById('\0' + virtualId)
        if (module) server.moduleGraph.invalidateModule(module)
        // 注册表为每个页面实例持有；完整重载避免热更新重复安装同版本。
        server.ws.send({ type: 'full-reload' })
      })
    }
  }
}
