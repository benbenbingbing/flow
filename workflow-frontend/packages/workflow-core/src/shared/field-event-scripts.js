import { isEmbedPath } from '../runtime/app-mode.js'
import { FIELD_HANDLER_NAME, getFieldEventHandler, fieldEventHandlerApplicable } from '../extensions/core/registries/fieldEventHandlerRegistry.js'

export const FIELD_SCRIPT_CONTEXT = Symbol('field-script-context')
export const FIELD_SCRIPT_TIMEOUT = 5000

/** 保留非法对象供编辑器定位，不能在读取/保存时静默删除已有绑定。 */
export function hasFieldScript(binding) {
  if (typeof binding === 'string') return Boolean(binding.trim())
  if (binding == null) return false
  if (binding.mode === 'body') return typeof binding.code !== 'string' || Boolean(binding.code.trim())
  return true
}

/** 历史字符串是代码片段；函数模式只接受受控注册名和精确版本。 */
export function normalizeFieldScript(binding) {
  if (typeof binding === 'string') return { mode: 'body', code: binding }
  if (!binding || typeof binding !== 'object' || Array.isArray(binding)) throw new Error('事件处理配置必须为代码片段或处理函数引用')
  if (binding.mode === 'body' && typeof binding.code === 'string' && Object.keys(binding).every(key => ['mode', 'code'].includes(key))) return { mode: 'body', code: binding.code }
  if (binding.mode === 'function' && typeof binding.handler === 'string' && FIELD_HANDLER_NAME.test(binding.handler) && Number.isSafeInteger(binding.version) && binding.version > 0
    && Object.keys(binding).every(key => ['mode', 'handler', 'version'].includes(key))) return { mode: 'function', handler: binding.handler, version: binding.version }
  throw new Error('处理函数须选择注册名和版本，代码片段须填写 code')
}

/** 设计器保存/发布与运行时共享校验；只编译代码，绝不在校验时执行。 */
export function validateFieldScript(binding, field, eventName) {
  const normalized = normalizeFieldScript(binding)
  if (normalized.mode === 'body') compileFieldScript(normalized.code)
  else {
    const descriptor = getFieldEventHandler(normalized.handler, normalized.version)
    if (!descriptor) throw new Error(`处理函数未安装：${normalized.handler}@${normalized.version}`)
    if (!fieldEventHandlerApplicable(descriptor, field, eventName)) throw new Error(`处理函数不适用于当前字段或事件：${normalized.handler}`)
  }
  return normalized
}

/** 根属性是设计器草稿，优先于已序列化的 componentProps.events，包括显式清空。 */
export function readFieldScripts(field) {
  let props = field?.componentProps || {}
  if (typeof props === 'string') {
    try { props = JSON.parse(props) || {} } catch { props = {} }
  }
  const events = { ...props.events }
  Object.entries(field || {}).forEach(([key, value]) => {
    if (key.startsWith('eventOn')) events[`on${key.slice(7)}`] = value
  })
  return Object.fromEntries(Object.entries(events).filter(([, binding]) => hasFieldScript(binding)))
}

/** 同步草稿与序列化副本；清空/删除脚本后不能从旧 componentProps 再恢复出来。 */
export function writeFieldScripts(field, events) {
  let props = field.componentProps || {}
  if (typeof props === 'string') {
    try { props = JSON.parse(props) } catch { props = {} }
  }
  const componentProps = { ...props }
  const scripts = Object.fromEntries(Object.entries(events).filter(([, binding]) => hasFieldScript(binding)))
  Object.keys(field).forEach(key => {
    if (key.startsWith('eventOn')) delete field[key]
  })
  Object.entries(scripts).forEach(([event, code]) => {
    field[`eventOn${event.slice(2)}`] = code
  })
  if (Object.keys(scripts).length) componentProps.events = scripts
  else delete componentProps.events
  field.componentProps = JSON.stringify(componentProps)
}

export function fieldScriptEnvironmentError(location = globalThis.location) {
  return isEmbedPath(location?.pathname)
    ? '嵌入页面不支持前端脚本事件，请使用值联动或事件链'
    : ''
}

/** 仅编译，不执行；编辑器和运行时共享语法规则，支持顶层 await。不是隔离沙箱。 */
export function compileFieldScript(code) {
  const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor
  return new AsyncFunction('value', 'field', 'selection', 'event', 'setValue', 'getFieldValue', 'setFieldValue',
    `"use strict";\n${code}`)
}

export function fieldScriptEventSupported(field, event) {
  const type = String(field?.componentType || field?.fieldType || '').toLowerCase()
  if (event === 'onInput') return ['input', 'password', 'string', 'text', 'textarea'].includes(type)
  if (event === 'onFocus' || event === 'onBlur') {
    return ['input', 'password', 'string', 'text', 'textarea', 'number', 'integer', 'long', 'decimal', 'double',
      'date', 'datetime', 'select', 'multi_select', 'select_multiple', 'cascader',
      'reference', 'multi_reference', 'user', 'dept'].includes(type)
  }
  return !['section', 'sub_list'].includes(type)
}

/**
 * 执行管理员配置的浏览器脚本。返回值不用于回填，赋值必须使用显式 helper。
 * 同步/异步异常均交给调用方展示；等待超时或被新输入替代后，helper 不再写入。
 * 超时只能结束异步等待，无法抢占主线程死循环，也不限制脚本访问浏览器全局对象。
 */
export async function runFieldScript(code, context, { timeout = FIELD_SCRIPT_TIMEOUT } = {}) {
  if (!hasFieldScript(code) || !context.isCurrent()) return
  const environmentError = fieldScriptEnvironmentError()
  if (environmentError) throw new Error(environmentError)
  const binding = validateFieldScript(code, context.field, context.event?.name)
  const run = binding.mode === 'body' ? compileFieldScript(binding.code) : getFieldEventHandler(binding.handler, binding.version).handler
  let active = true
  let timer
  const guardedWrite = fn => (...args) => {
    if (active && context.isCurrent()) return fn(...args)
  }
  try {
    const ctx = Object.freeze({ value: context.value, field: context.field, selection: context.selection, event: context.event,
      setValue: guardedWrite(context.setValue), getFieldValue: context.getFieldValue, setFieldValue: guardedWrite(context.setFieldValue) })
    await Promise.race([
      binding.mode === 'body' ? run(ctx.value, ctx.field, ctx.selection, ctx.event, ctx.setValue, ctx.getFieldValue, ctx.setFieldValue) : run(ctx),
      new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error(`脚本等待超过 ${timeout} 毫秒`)), timeout)
      })
    ])
  } finally {
    active = false
    clearTimeout(timer)
  }
}
