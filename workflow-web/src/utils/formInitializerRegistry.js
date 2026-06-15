/**
 * 表单初始化器注册中心
 *
 * 二次开发者可通过 registerFormInitializer 注册自定义初始化器，
 * 在 form.initConfig.type === 'custom' 时被调用。
 *
 * 自定义初始化器签名：
 *   (config, context) => Promise<Record<string, any>>
 * 其中 context 包含：
 *   - entityCode: 实体编码
 *   - entityDefinition: 实体定义对象
 *   - routeQuery: 当前页面路由 query 参数
 *   - userStore: 当前用户 store
 */

const initializerRegistry = new Map()

/**
 * 注册自定义表单初始化器
 * @param {string} name 初始化器标识名
 * @param {Function} executor 执行函数
 */
export function registerFormInitializer(name, executor) {
  initializerRegistry.set(name, executor)
}

/**
 * 获取自定义表单初始化器
 * @param {string} name 初始化器标识名
 * @returns {Function|undefined}
 */
export function getFormInitializer(name) {
  return initializerRegistry.get(name)
}

/**
 * 判断自定义表单初始化器是否已注册
 * @param {string} name 初始化器标识名
 * @returns {boolean}
 */
export function hasFormInitializer(name) {
  return initializerRegistry.has(name)
}

/**
 * 获取所有已注册的自定义表单初始化器名
 * @returns {string[]}
 */
export function getRegisteredFormInitializerNames() {
  return Array.from(initializerRegistry.keys())
}
