import { computed, watch, inject, getCurrentInstance, onBeforeUnmount, nextTick, shallowRef, unref, toRaw } from 'vue'
import { FIELD_SCRIPT_CONTEXT, readFieldScripts, runFieldScript } from '../../../../shared/field-event-scripts.js'
import { FIELD_SCRIPT_EVENTS, readComponentScriptEvents, snapshotScriptEventData } from '../../../../shared/field-script-metadata.js'

export function normalizeFieldDefaultValue(field, value) {
  if (value == null) return value

  const type = String(field?.componentType || field?.fieldType || '').toLowerCase()
  if (type !== 'boolean' && type !== 'switch') return value
  if (typeof value === 'boolean') return value
  if (typeof value === 'number') return value !== 0

  const normalized = String(value).trim().toLowerCase()
  if (['true', '1', 'yes', 'on'].includes(normalized)) return true
  if (['false', '0', 'no', 'off', ''].includes(normalized)) return false
  return Boolean(value)
}

export function normalizeFieldCollectionValue(value) {
  if (Array.isArray(value)) return value
  if (value == null || value === '') return []
  return [value]
}

/**
 * 表单字段共享逻辑 Composable
 * 统一处理字段值转换、选项解析、组件属性解析、自定义事件脚本执行
 *
 * @param {Object} props - 组件 props，必须包含 field, modelValue, disabled, options
 * @param {Function} emit - Vue emit 函数
 * @returns {Object} 字段渲染所需的响应式状态和方法
 */
export function useFormField(props, emit, options = {}) {
  const instance = getCurrentInstance()
  const scriptContext = instance ? inject(FIELD_SCRIPT_CONTEXT, null) : null
  const hostEvents = instance ? inject(FIELD_SCRIPT_EVENTS, null) : null
  const scriptEvents = computed(() => {
    if (options.scriptEvents) return unref(options.scriptEvents)
    if (hostEvents) return unref(hostEvents)
    const type = String(props.field?.componentType || '').toLowerCase()
    return readComponentScriptEvents(instance?.type, ({ string: 'input', text: 'input' })[type] || type)
  })
  // 新输入使旧异步脚本失效；同一事件再次触发也会替代前次，避免迟到回填覆盖新值。
  let valueRevision = 0
  let disposed = false
  const latestValue = shallowRef(props.modelValue)
  let pendingTextBatch = null
  let activeTextBatch = null
  const eventRevisions = new Map()
  if (getCurrentInstance()) onBeforeUnmount(() => { disposed = true })
  watch(() => props.field, () => { valueRevision++ }, { flush: 'sync' })
  watch(() => props.modelValue, value => {
    // 本组件发出的模型回声不取消自己的脚本；其它字段回填或外部重置则使旧脚本失效。
    // 父表单的 reactive 会把刚发出的数组/对象包装为 Proxy，不能把同一原对象误判为外部重置。
    if (!Object.is(toRaw(value), toRaw(latestValue.value))) valueRevision++
    latestValue.value = value
  }, { flush: 'sync' })

  // 渲染类型：优先使用 componentType，其次使用 fieldType
  const renderType = computed(() => {
    const type = props.field?.componentType || props.field?.fieldType || ''
    return type.toLowerCase()
  })

  // 字段显示标签
  const fieldLabel = computed(() => {
    return props.field?.fieldLabel || props.field?.fieldName || ''
  })

  // 字段值（含类型转换）
  const fieldValue = computed({
    get() {
      const val = latestValue.value
      const type = renderType.value

      if (type === 'boolean' || type === 'switch') {
        return normalizeFieldDefaultValue(props.field, val)
      }

      if (['number', 'integer', 'long', 'decimal', 'double'].includes(type) && val === '') {
        return null
      }

      // 多选类字段强制数组化
      if ((type === 'checkbox' || type === 'select_multiple') && !Array.isArray(val)) {
        return normalizeFieldCollectionValue(val)
      }

      // 子表单按关系类型处理
      if (type === 'sub_form' && !Array.isArray(val)) {
        const isOneToOne = props.field?.relationType === 'ONE_TO_ONE'
          || props.field?.relation?.type === 'ONE_TO_ONE'
          || props.field?.componentType === 'sub_form'
        if (isOneToOne) {
          return val && typeof val === 'object' ? val : null
        }
        return val && typeof val === 'object' ? [val] : []
      }

      return val
    },
    set(val) {
      if (isDisabled.value) return
      if (options.eventAdapter === 'text') ensureTextBatch()
      else valueRevision++
      latestValue.value = val
      emit('update:modelValue', val)
    }
  })

  // 安全解析 componentProps JSON
  const parsedComponentProps = computed(() => {
    const cp = props.field?.componentProps
    if (!cp) return {}
    if (typeof cp === 'object') return cp
    try {
      return JSON.parse(cp)
    } catch (e) {
      return {}
    }
  })

  // 解析静态选项（多来源：componentProps.options -> optionsJson -> options）
  const staticOptions = computed(() => {
    const field = props.field
    if (!field) return []

    // 1. 从 componentProps 解析
    if (parsedComponentProps.value.options) {
      return parsedComponentProps.value.options
    }

    // 2. 从 optionsJson 解析
    if (field.optionsJson) {
      try {
        return JSON.parse(field.optionsJson)
      } catch (e) {
        return []
      }
    }

    // 3. 从 options 解析
    if (field.options) {
      if (typeof field.options === 'string') {
        try {
          return JSON.parse(field.options)
        } catch (e) {
          return []
        }
      }
      return field.options
    }

    return []
  })

  // 当前选项（外部传入的动态选项优先）
  const currentOptions = computed(() => {
    if (props.options && props.options.length > 0) {
      return props.options
    }
    return staticOptions.value
  })

  // 占位文本
  const placeholder = computed(() => {
    return props.field?.placeholder || `请输入${fieldLabel.value}`
  })

  // 是否禁用（props.disabled 已包含联动引擎的计算结果）
  const isDisabled = computed(() => {
    return props.disabled || props.readonly || props.field?.isReadonly === 1
  })

  // ========== 自定义事件脚本 ==========

  function getEventCode(eventType) {
    return readFieldScripts(props.field)[eventType] || ''
  }

  // 快照避免脚本直接修改响应式字段配置；业务赋值通过 helper 走原有表单更新通道。
  function snapshot(value) {
    if (value == null || typeof value !== 'object') return value
    if (value instanceof Date) return new Date(value)
    if (Array.isArray(value)) return value.map(snapshot)
    return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, snapshot(item)]))
  }

  /** 等待脚本（含 await）结束；错误只提示，不阻断正常 change 和后续事件链。 */
  async function executeEvent(code, value, eventName = 'onChange', selection = null, data = {}, revision = valueRevision, strict = false) {
    const eventRevision = (eventRevisions.get(eventName) || 0) + 1
    eventRevisions.set(eventName, eventRevision)
    const isCurrent = () => !disposed && !isDisabled.value && revision === valueRevision
      && eventRevisions.get(eventName) === eventRevision
    let finalValue = value
    const setValue = nextValue => {
      finalValue = nextValue
      latestValue.value = nextValue
      emit('update:modelValue', nextValue)
    }
    if ((code || strict) && isCurrent()) {
      try {
        const declaration = scriptEvents.value?.find(item => item.name === eventName)
        // 历史字符串绑定保留旧监听协议；新结构化绑定及组件主动触发必须匹配声明。
        if (scriptEvents.value !== undefined && !declaration && (strict || typeof code !== 'string')) throw new Error(`当前组件未声明事件 ${eventName}`)
        const eventData = snapshotScriptEventData(data, declaration)
        await runFieldScript(code, {
          value: snapshot(value), field: snapshot(props.field), selection: snapshot(selection),
          event: Object.freeze({ name: eventName, data: eventData }), isCurrent, setValue,
          getFieldValue: key => {
            if (!scriptContext) throw new Error('当前字段没有表单上下文，无法读取其它字段')
            return snapshot(scriptContext.getFieldValue(key))
          },
          setFieldValue: (key, nextValue) => {
            if (key === (props.field?.fieldCode || props.field?.fieldKey)) return setValue(nextValue)
            if (!scriptContext) throw new Error('当前字段没有表单上下文，请使用 setValue 修改当前值')
            scriptContext.setFieldValue(key, nextValue)
          }
        })
      } catch (error) {
        if (isCurrent()) {
          const message = `字段“${fieldLabel.value || props.field?.fieldCode || ''}”的 ${eventName} 脚本执行失败：${error?.message || String(error)}`
          if (scriptContext) scriptContext.reportError(message, error)
          else console.error(message, error)
        }
      }
    }
    // setValue 产生的 v-model 更新先落地，事件链读取到脚本处理后的表单值。
    await nextTick()
    return { current: isCurrent(), value: finalValue }
  }

  /** 组件业务代码主动触发已声明事件；value 只作为快照，不隐式修改模型或调用后台事件链。 */
  function triggerScriptEvent(name, payload = {}) {
    return executeEvent(getEventCode(name), Object.hasOwn(payload, 'value') ? payload.value : fieldValue.value,
      name, payload.selection ?? null, payload.data ?? {}, valueRevision, true)
  }

  /**
   * Element Plus 清空在同一栈发出 change/clear/input；微任务内合并后顺序执行，宿主只收到一次 change。
   * 新用户输入创建新修订号而不等待旧异步任务；setValue 只更新本批次值，不使自身失效。
   */
  function ensureTextBatch() {
    if (pendingTextBatch && pendingTextBatch.revision === valueRevision) return pendingTextBatch
    const batch = { revision: ++valueRevision, events: new Set() }
    pendingTextBatch = batch
    batch.promise = Promise.resolve().then(async () => {
      if (pendingTextBatch === batch) pendingTextBatch = null
      activeTextBatch = batch
      try {
        for (const name of ['onClear', 'onInput', 'onChange', 'onFocus', 'onBlur']) {
          if (!batch.events.has(name)) continue
          if (batch.revision !== valueRevision || disposed || isDisabled.value) break
          const result = await executeEvent(getEventCode(name), fieldValue.value, name, null, {}, batch.revision)
          if (!result.current) break
          if (name === 'onChange') emit('change', result.value)
          if (name === 'onFocus') emit('focus', result.value)
          if (name === 'onBlur') emit('blur', result.value)
        }
        return { current: batch.revision === valueRevision && !disposed && !isDisabled.value, value: fieldValue.value }
      } finally {
        if (activeTextBatch === batch) activeTextBatch = null
      }
    })
    return batch
  }

  function queueTextEvent(name, value) {
    // blur 可紧随异步 change 到达；并入同一批次，避免提前执行失焦脚本或取消值变化通知。
    const batch = ['onFocus', 'onBlur'].includes(name) && activeTextBatch?.revision === valueRevision
      ? activeTextBatch : ensureTextBatch()
    if (value !== undefined) latestValue.value = value
    batch.events.add(name)
    return batch.promise
  }

  async function handleChange(val) {
    if (isDisabled.value) return { current: false, value: fieldValue.value }
    fieldValue.value = val
    if (options.eventAdapter === 'text') return queueTextEvent('onChange')
    const result = await executeEvent(getEventCode('onChange'), val)
    if (result.current) emit('change', result.value)
  }

  /** 实体选择的模型保存 ID，事件携带完整记录；两者不能互相覆盖。 */
  async function handleSelectionChange(selection) {
    const revision = valueRevision
    await nextTick()
    if (revision !== valueRevision || disposed) return
    const result = await executeEvent(getEventCode('onChange'), fieldValue.value, 'onChange', selection)
    if (!result.current) return
    // 脚本允许清空或改选 ID，后续选择事件不能仍携带旧记录去执行回填。
    const records = Array.isArray(selection) ? selection : [selection]
    const recordFor = id => records.find(item => item && String(item.id) === String(id)) || { id }
    const nextSelection = Array.isArray(result.value)
      ? result.value.map(recordFor)
      : result.value == null || result.value === '' ? null : recordFor(result.value)
    emit('change', nextSelection)
  }

  // 文本 input 只执行输入脚本；提交变化时才执行 onChange 和服务端事件链。
  function handleInput(val) {
    if (isDisabled.value) return Promise.resolve({ current: false, value: fieldValue.value })
    if (options.eventAdapter === 'text') return queueTextEvent('onInput', val)
    valueRevision++
    return executeEvent(getEventCode('onInput'), val, 'onInput')
  }

  async function handleBlur() {
    if (options.eventAdapter === 'text') return queueTextEvent('onBlur')
    const result = await executeEvent(getEventCode('onBlur'), fieldValue.value, 'onBlur')
    if (result.current) emit('blur', result.value)
  }

  async function handleFocus() {
    if (options.eventAdapter === 'text') return queueTextEvent('onFocus')
    const result = await executeEvent(getEventCode('onFocus'), fieldValue.value, 'onFocus')
    if (result.current) emit('focus', result.value)
  }

  function handleClear() {
    return options.eventAdapter === 'text' ? queueTextEvent('onClear') : triggerScriptEvent('onClear')
  }

  const customEventListeners = computed(() => {
    const listeners = {}
    Object.entries(readFieldScripts(props.field)).forEach(([key, code]) => {
      if (['onChange', 'onBlur', 'onFocus', 'onInput'].includes(key)) return
      if (scriptEvents.value !== undefined && !scriptEvents.value.some(item => item.name === key) && typeof code !== 'string') return
      const name = key.startsWith('on') ? key.slice(2) : key
      const eventName = name.charAt(0).toLowerCase() + name.slice(1)
      listeners[eventName] = (...args) => executeEvent(code, fieldValue.value, key, null, options.eventPayloads?.[key]?.(...args) || {})
    })
    return listeners
  })

  // ========== 默认值注入 ==========

  watch(
    () => props.field?.defaultValue,
    (val) => {
      if (options.applyDefaults?.() !== false && val != null && (props.modelValue == null || props.modelValue === '')) {
        emit('update:modelValue', normalizeFieldDefaultValue(props.field, val))
      }
    },
    { immediate: true }
  )

  return {
    renderType,
    fieldLabel,
    fieldValue,
    parsedComponentProps,
    staticOptions,
    currentOptions,
    placeholder,
    isDisabled,
    handleChange,
    handleInput,
    handleSelectionChange,
    handleBlur,
    handleFocus,
    handleClear,
    triggerScriptEvent,
    scriptEvents,
    customEventListeners,
    getEventCode,
    executeEvent
  }
}
