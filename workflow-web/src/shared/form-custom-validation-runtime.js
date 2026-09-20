import { cloneValidationValue, evaluateCustomValidators, getCustomValidationConfig, hasCustomValidationTrigger } from './form-custom-validation.js'

/** 即使实现没有使用 signal，也让取消后的校验及时结束；旧 Promise 的拒绝仍被消费。 */
function abortable(promise, signal) {
  return new Promise((resolve, reject) => {
    const abort = () => reject(signal.reason || new Error('校验已取消'))
    signal.addEventListener('abort', abort, { once: true })
    Promise.resolve(promise).then(resolve, reject).finally(() => signal.removeEventListener('abort', abort))
    if (signal.aborted) abort()
  })
}

/**
 * 每个表单/子表行独立维护异步校验状态。输入、发布身份或权限变化后旧结果不再生效。
 * getContext 提供该表单的实体身份与业务上下文；调用方不能用规则参数覆盖 entityCode。
 * 校验只处理可见且可编辑的字段，与已有跨字段规则保持同一适用策略。
 */
export function createCustomValidationController({ getFields, getRecord, getContext, getState, onErrorsChange = () => {} }) {
  const entries = new Map()
  const touched = new Map()
  let errors = {}
  let generation = 0
  let disposed = false

  function publish(code, error) {
    if (!error && !errors[code]) return
    const next = { ...errors }
    if (error) next[code] = { fieldCode: code, ...error }
    else delete next[code]
    errors = next
    onErrorsChange(next)
  }

  function snapshot() {
    const fields = getFields()
    const record = getRecord() || {}
    const context = getContext() || {}
    const states = Object.fromEntries(fields.map(field => [field.fieldCode, getState(field)]))
    return {
      fields, record, context, states,
      // 整记录也参与指纹：跨字段业务规则不能接受条件字段已变化的旧响应。
      fingerprint: JSON.stringify([fields, record, context, states], (_key, value) =>
        typeof value === 'number' && !Number.isFinite(value) ? String(value) : value)
    }
  }

  function configured(field) {
    try { return getCustomValidationConfig(field) !== undefined } catch { return true }
  }

  /** 校验某字段；同一输入的重复交互复用请求，提交始终重新执行，不缓存远端业务判断。 */
  function check(fieldOrCode, trigger = 'BLUR') {
    if (disposed) return Promise.resolve({ valid: false, stale: true })
    const code = typeof fieldOrCode === 'string' ? fieldOrCode : fieldOrCode?.fieldCode
    const data = snapshot()
    const field = data.fields.find(item => item.fieldCode === code)
    const previous = entries.get(code)
    if (!field || !configured(field) || !data.states[code]?.visible || !data.states[code]?.editable) {
      previous?.controller.abort()
      entries.delete(code)
      publish(code, null)
      return Promise.resolve({ valid: true })
    }
    // 文本框 change 的异步事件链可能晚于 blur 完成。没有匹配规则时不改变
    // 错误、最近校验时机和取消信号，避免把“未执行”当成“校验通过”。
    if (!hasCustomValidationTrigger(field, trigger, data.context.entityCode)) return Promise.resolve({ valid: true, skipped: true })
    if (trigger !== 'SUBMIT' && previous?.fingerprint === data.fingerprint && previous.trigger === trigger) return previous.promise
    // 保存已校验的字段定义，供 refresh 在规则移除/修改时清理旧提示。
    // 不保存可供重放的 BLUR/SUBMIT：数据变化并不代表再次失焦或提交。
    touched.set(code, JSON.stringify(field))
    previous?.controller.abort()
    const controller = new AbortController()
    const entry = { controller, fingerprint: data.fingerprint, trigger }
    entries.set(code, entry)
    const context = { ...cloneValidationValue(data.context), formData: cloneValidationValue(data.record) }
    entry.promise = (async () => {
      let result
      try {
        result = await abortable(evaluateCustomValidators(
          cloneValidationValue(field), data.record[code], context, trigger, controller.signal
        ), controller.signal)
      } catch (error) {
        result = { valid: false, message: `自定义校验执行失败：${error?.message || '请重试'}` }
      }
      if (disposed || controller.signal.aborted || entries.get(code) !== entry
          || snapshot().fingerprint !== data.fingerprint) {
        return { valid: false, stale: true, fieldCode: code }
      }
      publish(code, result.valid ? null : result)
      return { ...result, fieldCode: code }
    })()
    return entry.promise
  }

  return {
    check,
    /**
     * 数据变化只取消过期请求，并按 CHANGE 配置刷新已交互字段。
     * BLUR/仅提交字段保留上一次提示，直到下一次失焦/提交；不能因深度监听重跑它们。
     * 隐藏、只读、移除字段或修改规则时仍立即清除已不适用的错误。
     */
    refresh() {
      const data = snapshot()
      for (const [code, entry] of entries) {
        if (entry.fingerprint === data.fingerprint) continue
        entry.controller.abort()
        entries.delete(code)
      }
      for (const [code, previousField] of touched) {
        const field = data.fields.find(item => item.fieldCode === code)
        if (!field || !configured(field) || !data.states[code]?.visible || !data.states[code]?.editable) {
          entries.get(code)?.controller.abort()
          entries.delete(code)
          touched.delete(code)
          publish(code, null)
          continue
        }
        const currentField = JSON.stringify(field)
        if (currentField !== previousField) {
          publish(code, null)
          touched.set(code, currentField)
        }
        if (!entries.has(code) && hasCustomValidationTrigger(field, 'CHANGE', data.context.entityCode)) {
          void check(code, 'CHANGE')
        }
      }
    },
    /**
     * 必须等待所有字段完成再允许提交。校验期间数据变化、切换表单或卸载均返回失败，
     * 让调用方停留在当前页面重新提交，不把取消或旧结果视为成功。
     */
    async validate() {
      const data = snapshot()
      const epoch = generation
      const results = await Promise.all(data.fields.filter(configured).map(field => check(field, 'SUBMIT')))
      const stale = disposed || epoch !== generation || data.fingerprint !== snapshot().fingerprint || results.some(item => item.stale)
      return {
        valid: !stale && results.every(item => item.valid),
        errors: Object.values(errors),
        message: stale ? '校验期间表单数据已变化，请重新提交' : '',
        stale
      }
    },
    reset() {
      generation++
      for (const entry of entries.values()) entry.controller.abort()
      entries.clear()
      touched.clear()
      errors = {}
      onErrorsChange(errors)
    },
    dispose() { this.reset(); disposed = true },
    getErrors: () => errors
  }
}
