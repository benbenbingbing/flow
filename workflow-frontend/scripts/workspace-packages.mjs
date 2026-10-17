import path from 'node:path'
import { discoverBusinessModules, extensionLayout, resolveEnabledModules } from './extensions/modules.mjs'

/** 构建、开发和仓库启动脚本共用包名及真实目录，调用者无需猜测 packages/custom 位置。 */
export function selectedWorkspacePackageDescriptors(options = {}) {
  const { root } = extensionLayout(options)
  const businessModules = discoverBusinessModules(options)
  const modules = resolveEnabledModules({ ...options, businessModules })
  const products = ['workflow-core', 'workflow-extensions', 'workflow-api', 'workflow-mobile-ui']
  return [...products.map(id => ({ name: `@flow/${id}`, directory: path.join(root, 'packages', id) })),
    ...[...modules].map(id => {
      const { name, directory } = businessModules.get(id)
      return { name, directory }
    })]
}

/** 保留 npm workspace 使用的有序包名数组；顺序与描述符接口完全一致。 */
export function selectedWorkspacePackages(options = {}) {
  return selectedWorkspacePackageDescriptors(options).map(entry => entry.name)
}
