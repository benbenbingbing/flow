/**
 * 契约定义入口，无注册、网络或应用启动副作用，可供普通 JS 和 Vue 页面导入。
 * Vue 模板位于 templates/；已有系统注册函数按需从 registration.js 导入，
 * 已有渲染/配置辅助能力从 runtime.js 导入。本次没有业务页面依赖这个新入口。
 */
export * from './component.js'
export * from './form.js'
export * from './form-field.js'
export * from './form-node.js'
export * from './list.js'
export * from './list-cell.js'
export * from './list-action.js'
export * from './action-rule.js'
export * from './related-content.js'
export * from './data-source.js'
export * from './validation.js'
