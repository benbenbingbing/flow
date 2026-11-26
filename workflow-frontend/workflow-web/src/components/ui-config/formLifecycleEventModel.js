import { assertUniqueFormDataSourceOutputTargets } from '@flow/workflow-core/form-runtime/formDataSourceBindings'
import { parseEventDocument, serializeEventStep } from './eventStepModel.js'

export const formLifecycleEvents = ['AFTER_LOAD', 'BEFORE_SUBMIT']

/** 表单事件统一编辑入口。生命周期步骤仍随表单元数据发布，供加载器和服务端提交链各自读取。 */
export function formLifecycleRows(form) {
  const bindings = readFormBindings(form)
  return formLifecycleEvents.flatMap(eventCode => {
    const configured = bindings[eventCode]
    const steps = (Array.isArray(configured) ? configured : [configured]).filter(Boolean)
    if (!steps.length) return []
    return [{
      id: eventCode, ownerType: 'FORM', targetType: 'OWNER', targetKey: '', eventCode,
      revision: form.revision, enabled: steps.some(step => step.enabled !== false), steps
    }]
  })
}

/** 只改当前时机的步骤，防止编辑或删除一个事件时覆盖同表单其他绑定。 */
export function updateFormLifecycleBindings(form, eventCode, steps, enabled) {
  const bindings = { ...readFormBindings(form) }
  if (!steps.length) {
    delete bindings[eventCode]
    return bindings
  }
  const configured = steps.map((step, index) => {
    const { name, extensionId, condition } = serializeEventStep(step, index)
    if (!extensionId) throw new Error('请选择扩展接口')
    if (eventCode === 'BEFORE_SUBMIT' && step.clientPrevalidate && !step.sideEffectFree) {
      throw new Error('浏览器预校验必须同时标记为无副作用')
    }
    return {
      name, extensionId, condition, enabled,
      inputMapping: toPathMapping(step.inputRows),
      outputMapping: toPathMapping(step.outputRows),
      ...(eventCode === 'BEFORE_SUBMIT' && {
        clientPrevalidate: step.clientPrevalidate === true,
        sideEffectFree: step.sideEffectFree === true
      })
    }
  })
  assertUniqueFormDataSourceOutputTargets(configured.map(step => ({ ...step, usage: eventCode })))
  bindings[eventCode] = configured.length === 1 ? configured[0] : configured
  return bindings
}

function readFormBindings(form) {
  return parseEventDocument(form?.dataSourceBindingsDocument || form?.dataSourceBindings, {}) || {}
}

function toPathMapping(rows) {
  const entries = []
  for (const { targetPath, sourcePath, ...row } of rows || []) {
    const target = String(targetPath || '').trim()
    const source = String(sourcePath || '').trim()
    const hasLiteral = Object.hasOwn(row, 'literal')
    if (!target && !source && !hasLiteral) continue
    if (!target || (!source && !hasLiteral)) throw new Error('请填写映射的来源和目标路径')
    if (target.split('.').some(part => !part || ['__proto__', 'prototype', 'constructor'].includes(part))) {
      throw new Error(`无效的映射目标路径“${target}”`)
    }
    if (entries.some(([path]) => path === target)) throw new Error(`重复的映射目标路径“${target}”`)
    entries.push([target, source || { literal: row.literal }])
  }
  return Object.fromEntries(entries)
}
