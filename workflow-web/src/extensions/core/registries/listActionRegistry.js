const toolbarHandlers = new Map()
const rowHandlers = new Map()

/**
 * 注册列表工具栏自定义动作
 * @param {string} name 执行器名称
 * @param {Function} handler 执行函数，接收 context 参数
 */
export function registerListToolbarAction(name, handler) {
  toolbarHandlers.set(name, handler)
}

/**
 * 获取列表工具栏自定义动作
 */
export function getListToolbarAction(name) {
  return toolbarHandlers.get(name)
}

/**
 * 判断列表工具栏自定义动作是否已注册
 */
export function hasListToolbarAction(name) {
  return toolbarHandlers.has(name)
}

/**
 * 注册列表操作列自定义动作
 * @param {string} name 执行器名称
 * @param {Function} handler 执行函数，接收 { row, ...context }
 */
export function registerListRowAction(name, handler) {
  rowHandlers.set(name, handler)
}

/**
 * 获取列表操作列自定义动作
 */
export function getListRowAction(name) {
  return rowHandlers.get(name)
}

/**
 * 判断列表操作列自定义动作是否已注册
 */
export function hasListRowAction(name) {
  return rowHandlers.has(name)
}
