import { hasInjectionContext, inject } from 'vue'

const extensionHostKey = Symbol('flow-extension-host')

/**
 * 为一个 Vue 应用安装扩展宿主能力，须在 mount 前调用。
 * entityDataApi、uiEventBindingApi 由宿主绑定当前会话与权限处理；业务模块只组合
 * 这些客户端，不持有应用 store 或自行建立登录态。components 提供公开的渲染能力。
 * 每个应用单独 provide，防止管理端、Embed 或测试之间共享可变服务单例。
 */
export function provideExtensionHost(app, host) {
  if (!app || typeof app.provide !== 'function' || !host || typeof host !== 'object') {
    throw new TypeError('扩展宿主需要 Vue 应用和能力对象')
  }
  const value = Object.freeze({ ...host, components: Object.freeze({ ...host.components }) })
  app.provide(extensionHostKey, value)
  return value
}

/**
 * 在 setup 或 app.runWithContext 中读取当前应用的公开能力。
 * 独立预览和测试也须安装宿主；缺失时明确失败，避免退回应用私有实现或错误会话。
 */
export function useExtensionHost() {
  const host = hasInjectionContext() ? inject(extensionHostKey, null) : null
  if (!host) throw new Error('扩展宿主能力未安装，请在挂载前调用 provideExtensionHost')
  return host
}
