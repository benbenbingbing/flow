/**
 * 创建独立安装器。entries 为生成器提供的模块引用与可信描述，不接受页面上传清单。
 * adapters 由平台固定提供；测试可注入独立适配器验证错误隔离与幂等性。
 */
export function createExtensionInstaller(adapters, publish = () => {}) {
  let installedSignature = null
  let failed = false
  return (entries, { enableDemo = false, services = {} } = {}) => {
    if (failed) throw new Error('扩展安装曾失败，请修复后刷新页面')
    const selected = entries.filter(({ descriptor: item }) => item.enabled !== false && (item.origin !== 'EXAMPLE' || enableDemo))
    const signature = JSON.stringify(selected.map(item => item.descriptor))
    if (installedSignature === signature) return
    if (installedSignature !== null) throw new Error('扩展清单变化需要刷新页面，不能叠加注册')
    try {
      // 完整验证导出和实例后才写入注册表；业务函数在此阶段不可被当作工厂误执行。
      const plan = selected.map(({ descriptor: entry, implementation, hooks = {} }) => {
        const fail = message => { throw new Error(`${entry.sourceFile} [${entry.type}:${entry.name}]: ${message}`) }
        if (!adapters[entry.type]) fail('没有对应注册适配器')
        const kind = entry.implementation.kind
        let value = implementation
        if (['CLASS', 'FACTORY', 'FUNCTION'].includes(kind) && typeof value !== 'function') fail(`导出必须为 ${kind}`)
        try {
          if (kind === 'CLASS') value = new implementation()
          if (kind === 'FACTORY') value = implementation(Object.freeze({ services }))
        } catch (error) {
          fail(`实例创建失败：${error.message}`)
        }
        if (value?.then) {
          // 错误异步工厂仍可能随后 reject，吸收它并报告可定位的同步安装错误。
          Promise.resolve(value).catch(() => {})
          fail('实例工厂必须同步返回，不能在注册阶段请求接口')
        }
        if (kind === 'COMPONENT' && (!value || !['object', 'function'].includes(typeof value))) fail('缺少 Vue 组件导出')
        if (kind === 'OBJECT' && (!value || typeof value !== 'object')) fail('缺少对象导出')
        if (entry.type === 'VALIDATOR' && typeof value?.validate !== 'function') fail('校验器必须提供 validate(value, context)')
        for (const [key, hook] of Object.entries(hooks)) if (typeof hook !== 'function') fail(`${key} 钩子必须为函数`)
        return { entry, value, metadata: { ...entry.metadata, name: entry.name, label: entry.label, description: entry.description, version: entry.version, origin: entry.origin, ...hooks } }
      })
      for (const { entry, value, metadata } of plan) adapters[entry.type](entry, value, metadata)
      publish(plan.map(item => item.entry))
      installedSignature = signature
    } catch (error) {
      // 旧注册 API 无事务回滚；失败时禁止继续挂载或在同页重试覆盖。
      failed = true
      throw error
    }
  }
}
