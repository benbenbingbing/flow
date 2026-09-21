import { normalizeExtensionDescriptor } from '../../../shared/config-runtime/index.js'

// 包含平台与业务单元格，均由 JSON 安装器填充；导入本文件不会自动注册。
const registry = new Map()

/**
 * 注册自定义列表单元格组件
 * @param {string} name 组件标识名
 * @param {Component} component Vue 组件
 */
export function registerCellComponent(name, component, metadata = {}) {
  const descriptor = normalizeExtensionDescriptor(name, component, metadata)
  registry.set(descriptor.name, descriptor)
}

/**
 * 获取列表单元格组件
 * @param {string} name 组件标识名
 * @returns {Component|undefined}
 */
export function getCellComponent(name) {
  return registry.get(name)?.component
}

/**
 * 判断组件是否已注册
 * @param {string} name 组件标识名
 * @returns {boolean}
 */
export function hasCellComponent(name) {
  return registry.has(name)
}

/**
 * 获取所有已注册的组件名称列表
 * @returns {string[]}
 */
export function getRegisteredCellNames() {
  return Array.from(registry.keys())
}

export function getCellDescriptor(name) {
  return registry.get(name)
}

export function getCellComponentOptions() {
  return Array.from(registry.values()).map(({ component, ...descriptor }) => descriptor)
}
