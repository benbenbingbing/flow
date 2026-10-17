import { checkBusinessBoundaries, createBusinessBoundaryContext, inspectBusinessDependency } from './check-business-boundaries.mjs'
import path from 'node:path'
import { resolveEnabledModules } from './extensions/modules.mjs'

/** 构建模块图而非产物字符串检查，懒加载模块同样受平台依赖边界约束。 */
export function workflowBoundaryPlugin(platform, options = {}) {
  const context = createBusinessBoundaryContext(options.repository)
  const forbidden = platform === 'mobile'
    ? /(?:^|[/\\])(?:workflow-web|element-plus|@element-plus)(?:[/\\]|$)/
    : /(?:^|[/\\])(?:workflow-mobile|workflow-mobile-ui)(?:[/\\]|$)/
  return {
    name: `flow-${platform}-boundary`,
    buildStart() {
      const result = checkBusinessBoundaries(context.root)
      if (result.errors.length) this.error(`前端业务依赖边界检查失败：\n${result.errors.join('\n')}`)
    },
    async resolveId(source, importer) {
      if (forbidden.test(source)) this.error(`${platform} 入口不允许导入 ${source}（来自 ${importer || 'entry'}）`)
      if (!importer) return null
      const directError = inspectBusinessDependency(context, importer, source)
      if (directError) this.error(directError)
      // 使用构建器真实解析结果检查别名；仅检查源码字符串会漏过 alias 指向的私有文件。
      const from = context.classify(importer)
      if (!from.pkg && !from.app && !from.assembly) return null
      const resolved = await this.resolve(source, importer, { skipSelf: true })
      if (resolved) {
        const resolvedError = inspectBusinessDependency(context, importer, source, resolved.id)
        if (resolvedError) this.error(resolvedError)
      }
      return resolved
    },
    generateBundle() {
      const modules = [...this.getModuleIds()]
      const invalid = modules.filter(id => forbidden.test(id))
      if (invalid.length) this.error(`平台依赖越界：\n${invalid.join('\n')}`)
      const enabledModules = resolveEnabledModules({
        repositoryRoot: context.root, enabledModules: options.enabledModules
      })
      // 交付配置是构建边界；即使某个已启用业务间接导入另一个包，也不能带入未选业务。
      const excluded = modules.filter(id => {
        const pkg = context.classify(id).pkg
        return pkg?.business && !enabledModules.has(path.basename(pkg.directory))
      })
      if (excluded.length) this.error(`产物包含未启用的业务模块：\n${excluded.join('\n')}`)
      for (const importer of modules) {
        const info = this.getModuleInfo?.(importer)
        // 动态导入的边也必须检查；间接依赖不能经由合法的业务入口把应用私有实现带回来。
        for (const target of [...(info?.importedIds || []), ...(info?.dynamicallyImportedIds || [])]) {
          const error = inspectBusinessDependency(context, importer, null, target)
          if (error) this.error(error)
        }
      }
      this.emitFile({ type: 'asset', fileName: 'module-boundary.json', source: JSON.stringify({ platform, modules: modules.map(id => id.replace(process.cwd(), '.')) }, null, 2) })
    }
  }
}
