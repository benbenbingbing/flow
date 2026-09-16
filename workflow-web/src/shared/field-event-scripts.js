import { isEmbedPath } from '../runtime/app-mode.js'

export const FIELD_SCRIPT_CONTEXT = Symbol('field-script-context')
export const FIELD_SCRIPT_TIMEOUT = 5000

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
  return Object.fromEntries(Object.entries(events).filter(([, code]) => typeof code === 'string' && code.trim()))
}

/** 同步草稿与序列化副本；清空/删除脚本后不能从旧 componentProps 再恢复出来。 */
export function writeFieldScripts(field, events) {
  let props = field.componentProps || {}
  if (typeof props === 'string') {
    try { props = JSON.parse(props) } catch { props = {} }
  }
  const componentProps = { ...props }
  const scripts = Object.fromEntries(Object.entries(events).filter(([, code]) => typeof code === 'string' && code.trim()))
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
  if (event === 'onInput') return ['input', 'string', 'text', 'textarea'].includes(type)
  if (event === 'onFocus' || event === 'onBlur') {
    return ['input', 'string', 'text', 'textarea', 'number', 'integer', 'long', 'decimal', 'double',
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
  if (!code?.trim() || !context.isCurrent()) return
  const environmentError = fieldScriptEnvironmentError()
  if (environmentError) throw new Error(environmentError)
  const run = compileFieldScript(code)
  let active = true
  let timer
  const guardedWrite = fn => (...args) => {
    if (active && context.isCurrent()) return fn(...args)
  }
  try {
    await Promise.race([
      run(context.value, context.field, context.selection, context.event,
        guardedWrite(context.setValue), context.getFieldValue, guardedWrite(context.setFieldValue)),
      new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error(`脚本等待超过 ${timeout} 毫秒`)), timeout)
      })
    ])
  } finally {
    active = false
    clearTimeout(timer)
  }
}
