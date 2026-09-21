/** 构建模块图而非产物字符串检查，懒加载模块同样受平台依赖边界约束。 */
export function workflowBoundaryPlugin(platform) {
  const forbidden = platform === 'mobile'
    ? /(?:^|[/\\])(?:workflow-web|element-plus|@element-plus)(?:[/\\]|$)/
    : /(?:^|[/\\])(?:workflow-mobile|workflow-mobile-ui)(?:[/\\]|$)/
  return {
    name: `flow-${platform}-boundary`,
    resolveId(source, importer) {
      if (forbidden.test(source)) this.error(`${platform} 入口不允许导入 ${source}（来自 ${importer || 'entry'}）`)
    },
    generateBundle() {
      const modules = [...this.getModuleIds()]
      const invalid = modules.filter(id => forbidden.test(id))
      if (invalid.length) this.error(`平台依赖越界：\n${invalid.join('\n')}`)
      this.emitFile({ type: 'asset', fileName: 'module-boundary.json', source: JSON.stringify({ platform, modules: modules.map(id => id.replace(process.cwd(), '.')) }, null, 2) })
    }
  }
}
