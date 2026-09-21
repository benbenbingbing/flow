/**
 * 契约定义入口，无注册、网络或应用启动副作用，可供普通 JS 和 Vue 页面导入。
 * Vue 模板位于 templates/；实现统一通过 manifests JSON 注册，
 * 已有渲染/配置辅助能力从 runtime.js 导入。校验器同样使用 VALIDATOR 清单。
 */
export * from './component.js'
export * from './form.js'
export * from '@flow/workflow-core/extensions/contracts/form-field'
export * from './form-node.js'
export * from './list.js'
export * from './list-cell.js'
export * from './list-action.js'
export * from './action-rule.js'
export * from './related-content.js'
export * from './data-source.js'
export * from '@flow/workflow-core/extensions/contracts/validation'
