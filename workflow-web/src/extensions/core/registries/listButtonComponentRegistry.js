const buttonComponents = new Map()

/**
 * 注册列表自定义按钮组件
 * @param {string} name 组件标识名
 * @param {Component} component Vue 组件；运行时接收 mode、row、disabled、reason
 *   和 context。组件应展示禁用态，宿主仍会在捕获阶段强制阻断禁用点击。
 */
export function registerListButtonComponent(name, component) {
  buttonComponents.set(name, component)
}

/**
 * 获取列表自定义按钮组件
 */
export function getListButtonComponent(name) {
  return buttonComponents.get(name)
}

/**
 * 判断列表自定义按钮组件是否已注册
 */
export function hasListButtonComponent(name) {
  return buttonComponents.has(name)
}
