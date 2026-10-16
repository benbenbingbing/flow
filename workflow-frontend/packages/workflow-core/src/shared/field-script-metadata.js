/** 宿主提供实际解析到的组件事件目录；undefined 表示尚未迁移的旧组件，[] 表示明确不提供事件。 */
export const FIELD_SCRIPT_EVENTS = Symbol('field-script-events')
export const SCRIPT_EVENT_NAME = /^on[A-Z][A-Za-z0-9]*$/
const parameterTypes = new Set(['string', 'number', 'boolean', 'object', 'array', 'any'])

/** 从已加载的 Vue 导出读取静态声明，不挂载组件、不执行 setup，也不解析源码。 */
export function readComponentScriptEvents(component, variant) {
  const declarations = component?.scriptEvents
  if (declarations === undefined) return undefined
  if (!Array.isArray(declarations)) throw new Error('scriptEvents 必须为数组')
  const names = new Set()
  return declarations.map(item => {
    if (!item || !SCRIPT_EVENT_NAME.test(item.name) || names.has(item.name)) throw new Error(`scriptEvents 事件名称无效或重复：${item?.name}`)
    names.add(item.name)
    if (typeof item.label !== 'string' || !item.label.trim()) throw new Error(`${item.name} 缺少事件名称说明`)
    if (item.description !== undefined && typeof item.description !== 'string') throw new Error(`${item.name} description 必须为字符串`)
    if (item.variants !== undefined && (!Array.isArray(item.variants) || item.variants.some(value => typeof value !== 'string' || !value.trim()))) throw new Error(`${item.name} variants 必须为组件名称数组`)
    if (item.parameters !== undefined && !Array.isArray(item.parameters)) throw new Error(`${item.name} parameters 必须为数组`)
    const keys = new Set()
    const parameters = (item.parameters || []).map(parameter => {
      if (!parameter || !/^[A-Za-z][A-Za-z0-9]*$/.test(parameter.name) || keys.has(parameter.name) || !parameterTypes.has(parameter.type)) throw new Error(`${item.name} 参数声明无效`)
      if (parameter.required !== undefined && typeof parameter.required !== 'boolean') throw new Error(`${item.name}.${parameter.name} required 必须为布尔值`)
      if (parameter.description !== undefined && typeof parameter.description !== 'string') throw new Error(`${item.name}.${parameter.name} description 必须为字符串`)
      keys.add(parameter.name)
      return { name: parameter.name, type: parameter.type, description: parameter.description || '', required: parameter.required === true }
    })
    return { name: item.name, label: item.label, description: item.description || '', parameters,
      ...(item.variants ? { variants: [...item.variants] } : {}) }
  }).filter(item => !variant || !item.variants || item.variants.some(value => value.toLowerCase() === String(variant).toLowerCase()))
}

/** 只允许普通数据进入 event.data；声明参数负责说明和类型约束，不传递 DOM/组件对象。 */
export function snapshotScriptEventData(data = {}, declaration) {
  const copy = value => {
    if (value === null || ['string', 'boolean'].includes(typeof value)) return value
    if (typeof value === 'number' && Number.isFinite(value)) return value
    if (Array.isArray(value)) return value.map(copy)
    if (value && Object.getPrototypeOf(value) === Object.prototype) return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, copy(item)]))
    throw new Error('event.data 只支持普通 JSON 数据')
  }
  if (!data || Array.isArray(data) || typeof data !== 'object') throw new Error('event.data 必须为对象')
  const result = copy(data)
  for (const parameter of declaration?.parameters || []) {
    const value = result[parameter.name]
    if (value === undefined && !parameter.required) continue
    const type = Array.isArray(value) ? 'array' : value === null ? 'null' : typeof value
    if (value === undefined || (parameter.type !== 'any' && parameter.type !== type)) throw new Error(`event.data.${parameter.name} 必须为 ${parameter.type}`)
  }
  return result
}
