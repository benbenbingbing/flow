const handlers = new Map()
export const FIELD_HANDLER_NAME = /^[A-Za-z][A-Za-z0-9_.:-]{0,99}$/

/** 安装受构建管理的前端处理函数；发布配置锁定名称和版本，安装阶段不执行函数。 */
export function registerFieldEventHandler(name, handler, metadata = {}) {
  const version = metadata.version ?? 1
  const key = `${name}@${version}`
  if (typeof name !== 'string' || !FIELD_HANDLER_NAME.test(name) || !Number.isSafeInteger(version) || version < 1 || typeof handler !== 'function' || handlers.has(key)) throw new Error(`前端事件处理函数注册无效或重复：${key}`)
  const supportedFieldTypes = metadata.supportedFieldTypes || []
  const eventNames = metadata.capabilities?.eventNames || []
  if (![supportedFieldTypes, eventNames].every(items => Array.isArray(items) && items.every(item => typeof item === 'string'))) throw new Error(`${key} 的适用范围必须为字符串数组`)
  handlers.set(key, Object.freeze({ name, version, handler, label: metadata.label || name, description: metadata.description || '',
    supportedFieldTypes: [...supportedFieldTypes], eventNames: [...eventNames] }))
}

/** 精确版本查找；不回退最新版，避免历史发布表单执行语义发生变化。 */
export function getFieldEventHandler(name, version) { return handlers.get(`${name}@${version}`) }

export function fieldEventHandlerApplicable(descriptor, field, eventName) {
  return Boolean(descriptor)
    && (!descriptor.supportedFieldTypes.length || descriptor.supportedFieldTypes.map(value => value.toUpperCase()).includes(String(field?.fieldType || '').toUpperCase()))
    && (!descriptor.eventNames.length || descriptor.eventNames.includes(eventName))
}

/** 下拉框仅取元数据；函数对象不进入字段 JSON 或服务端目录。 */
export function getFieldEventHandlerOptions(field, eventName) {
  return [...handlers.values()].filter(item => fieldEventHandlerApplicable(item, field, eventName))
    .map(({ handler, ...item }) => ({ ...item, supportedFieldTypes: [...item.supportedFieldTypes], eventNames: [...item.eventNames] }))
}
